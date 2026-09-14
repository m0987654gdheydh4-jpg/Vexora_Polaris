package polaris.utils.proxy;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.Minecraft;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ProxyManager {
    private static ProxyManager instance;
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .excludeFieldsWithoutExposeAnnotation()
            .create();

    private final List<ProxyData> proxies = new ArrayList<>();
    private ProxyData activeProxy;
    private final ExecutorService pingExecutor = Executors.newCachedThreadPool();
    private final Path configFile;

    public ProxyManager() {
        this.configFile = Paths.get(Minecraft.getInstance().gameDirectory.getAbsolutePath(), "polaris", "proxies.json");
        loadProxies();
    }

    public static ProxyManager getInstance() {
        if (instance == null) {
            instance = new ProxyManager();
        }
        return instance;
    }

    public List<ProxyData> getProxies() {
        return proxies;
    }

    public ProxyData getActiveProxy() {
        return activeProxy;
    }

    public void setActiveProxy(ProxyData proxy) {
        if (proxy == null) {
            disconnect();
        } else {
            connect(proxy);
        }
    }

    public void connect(ProxyData proxy) {
        if (proxy == null) {
            disconnect();
            return;
        }
        this.activeProxy = proxy;

        // ===== JVM-глобальный прокси для всех HTTP/HTTPS запросов =====
        // Покрывает Mojang API (Profile Key Pair, скины, auth), текстуры и прочее,
        // что идёт через java.net.HttpURLConnection мимо Netty.
        if (proxy.getType() == ProxyData.ProxyType.HTTP) {
            System.setProperty("http.proxyHost", proxy.getIp());
            System.setProperty("http.proxyPort", String.valueOf(proxy.getPort()));
            System.setProperty("https.proxyHost", proxy.getIp());
            System.setProperty("https.proxyPort", String.valueOf(proxy.getPort()));
            System.clearProperty("socksProxyHost");
            System.clearProperty("socksProxyPort");
        } else {
            // SOCKS4 / SOCKS5 — JVM автоматически проксирует HTTP через SOCKS
            System.setProperty("socksProxyHost", proxy.getIp());
            System.setProperty("socksProxyPort", String.valueOf(proxy.getPort()));
            System.clearProperty("http.proxyHost");
            System.clearProperty("http.proxyPort");
            System.clearProperty("https.proxyHost");
            System.clearProperty("https.proxyPort");
        }

        // Authenticator — передаёт логин/пароль прокси при HTTP CONNECT и SOCKS5 auth
        if (proxy.getLogin() != null && !proxy.getLogin().isEmpty()) {
            java.net.Authenticator.setDefault(new java.net.Authenticator() {
                @Override
                protected java.net.PasswordAuthentication getPasswordAuthentication() {
                    return new java.net.PasswordAuthentication(
                            proxy.getLogin(),
                            proxy.getPassword() == null ? new char[0] : proxy.getPassword().toCharArray());
                }
            });
        } else {
            java.net.Authenticator.setDefault(null);
        }

        System.out.println("[Polaris] Proxy CONNECTED globally: " + proxy.getType()
                + " " + proxy.getIp() + ":" + proxy.getPort());
        saveProxies();
    }

    public void disconnect() {
        this.activeProxy = null;
        System.clearProperty("http.proxyHost");
        System.clearProperty("http.proxyPort");
        System.clearProperty("https.proxyHost");
        System.clearProperty("https.proxyPort");
        System.clearProperty("socksProxyHost");
        System.clearProperty("socksProxyPort");
        java.net.Authenticator.setDefault(null);
        System.out.println("[Polaris] Proxy DISCONNECTED");
        saveProxies();
    }

    public void addProxy(ProxyData proxy) {
        proxies.add(proxy);
        saveProxies();
    }

    public void removeProxy(ProxyData proxy) {
        if (activeProxy == proxy) {
            activeProxy = null;
        }
        proxies.remove(proxy);
        saveProxies();
    }

    private void loadProxies() {
        try {
            if (Files.exists(configFile)) {
                String json = Files.readString(configFile, StandardCharsets.UTF_8);
                Type listType = new TypeToken<List<ProxyData>>() {}.getType();
                List<ProxyData> loaded = GSON.fromJson(json, listType);
                if (loaded != null) {
                    proxies.clear();
                    proxies.addAll(loaded);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void saveProxies() {
        try {
            Files.createDirectories(configFile.getParent());
            String json = GSON.toJson(proxies);
            Files.writeString(configFile, json, StandardCharsets.UTF_8);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Пинг + авто-определение типа (SOCKS5 -> HTTP -> SOCKS4).
     * Определённый тип сохраняется в модель, чтобы ConnectionMixin
     * поставил правильный Netty-хэндлер.
     */
    public void checkPing(ProxyData proxy) {
        proxy.setPing(-2);
        pingExecutor.submit(() -> {
            long startTime = System.currentTimeMillis();
            ProxyData.ProxyType detected = null;

            try {
                if (trySocks5(proxy)) detected = ProxyData.ProxyType.SOCKS5;
            } catch (Exception ignored) {}

            if (detected == null) {
                try {
                    if (tryHttp(proxy)) detected = ProxyData.ProxyType.HTTP;
                } catch (Exception ignored) {}
            }

            if (detected == null) {
                try {
                    if (trySocks4(proxy)) detected = ProxyData.ProxyType.SOCKS4;
                } catch (Exception ignored) {}
            }

            if (detected != null) {
                proxy.setType(detected);
                proxy.setPing((int) (System.currentTimeMillis() - startTime));
            } else {
                proxy.setPing(-1);
            }
            saveProxies();
        });
    }

    private boolean trySocks5(ProxyData proxy) throws Exception {
        Socket socket = new Socket();
        socket.setSoTimeout(3000);
        socket.connect(new InetSocketAddress(proxy.getIp(), proxy.getPort()), 3000);
        try {
            OutputStream out = socket.getOutputStream();
            java.io.InputStream in = socket.getInputStream();

            boolean hasAuth = proxy.getLogin() != null && !proxy.getLogin().isEmpty();
            if (hasAuth) {
                out.write(new byte[]{0x05, 0x02, 0x00, 0x02});
            } else {
                out.write(new byte[]{0x05, 0x01, 0x00});
            }
            out.flush();

            byte[] resp = new byte[2];
            if (in.read(resp) != 2 || resp[0] != 0x05 || resp[1] == (byte) 0xFF) return false;

            if (resp[1] == 0x02) {
                byte[] login = proxy.getLogin().getBytes(StandardCharsets.UTF_8);
                byte[] pass = proxy.getPassword() == null ? new byte[0] : proxy.getPassword().getBytes(StandardCharsets.UTF_8);
                java.io.ByteArrayOutputStream auth = new java.io.ByteArrayOutputStream();
                auth.write(0x01);
                auth.write(login.length);
                auth.write(login);
                auth.write(pass.length);
                auth.write(pass);
                out.write(auth.toByteArray());
                out.flush();
                byte[] ar = new byte[2];
                if (in.read(ar) != 2 || ar[1] != 0x00) return false;
            } else if (resp[1] != 0x00) {
                return false;
            }

            out.write(new byte[]{0x05, 0x01, 0x00, 0x01, 127, 0, 0, 1, 0x00, 0x50});
            out.flush();
            byte[] cr = new byte[4];
            if (in.read(cr) != 4) return false;
            return cr[0] == 0x05 && cr[1] == 0x00;
        } finally {
            socket.close();
        }
    }

    private boolean tryHttp(ProxyData proxy) throws Exception {
        Socket socket = new Socket();
        socket.setSoTimeout(3000);
        socket.connect(new InetSocketAddress(proxy.getIp(), proxy.getPort()), 3000);
        try {
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();   // <-- ВОТ ЭТА СТРОКА БЫЛА ПРОПУЩЕНА

            String connect = "CONNECT 127.0.0.1:80 HTTP/1.1\r\nHost: 127.0.0.1\r\n";
            if (proxy.getLogin() != null && !proxy.getLogin().isEmpty()) {
                String auth = proxy.getLogin() + ":" + proxy.getPassword();
                connect += "Proxy-Authorization: Basic "
                        + java.util.Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8)) + "\r\n";
            }
            connect += "\r\n";

            out.write(connect.getBytes(StandardCharsets.UTF_8));
            out.flush();

            BufferedReader reader = new BufferedReader(new InputStreamReader(in));
            String line = reader.readLine();
            return line != null && line.contains("200");
        } finally {
            socket.close();
        }
    }
    private boolean trySocks4(ProxyData proxy) throws Exception {
        Socket socket = new Socket();
        socket.setSoTimeout(3000);
        socket.connect(new InetSocketAddress(proxy.getIp(), proxy.getPort()), 3000);
        try {
            OutputStream out = socket.getOutputStream();
            out.write(new byte[]{0x04, 0x01, 0x00, 0x50, 127, 0, 0, 1, 0x00});
            out.flush();
            byte[] resp = new byte[8];
            if (in_read(socket, resp) != 8) return false;
            return resp[1] == 0x5A;
        } finally {
            socket.close();
        }
    }

    private int in_read(Socket socket, byte[] buf) throws Exception {
        return socket.getInputStream().read(buf);
    }

    public Proxy getJavaProxy(ProxyData proxy) {
        if (proxy == null) return null;
        return switch (proxy.getType()) {
            case HTTP -> new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxy.getIp(), proxy.getPort()));
            case SOCKS4, SOCKS5 -> new Proxy(Proxy.Type.SOCKS, new InetSocketAddress(proxy.getIp(), proxy.getPort()));
        };
    }
}
package polaris.utils.proxy;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Локальный relay-туннель через прокси на чистом JDK (без io.netty.handler.proxy).
 * Netty подключается к 127.0.0.1:<port>, а relay вручную делает handshake
 * с прокси (SOCKS5 / SOCKS4 / HTTP CONNECT) и перекачивает байты обе стороны.
 */
public final class ProxyRelay {

    private ProxyRelay() {
    }

    /** Поднимает локальный relay и возвращает адрес, на который должен коннектиться Netty. */
    public static InetSocketAddress open(ProxyData proxy, String host, int port) throws Exception {
        ServerSocket server = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"));
        InetSocketAddress local = (InetSocketAddress) server.getLocalSocketAddress();

        Thread thread = new Thread(() -> {
            try (Socket client = server.accept()) {
                server.close();
                Socket upstream = connectThroughProxy(proxy, host, port);
                pump(client, upstream);
            } catch (Exception ignored) {
                try { server.close(); } catch (Exception ignored2) { }
            }
        }, "Polaris-Proxy-Relay");
        thread.setDaemon(true);
        thread.start();
        return local;
    }

    private static Socket connectThroughProxy(ProxyData proxy, String host, int port) throws Exception {
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(proxy.getIp(), proxy.getPort()), 5000);
        socket.setSoTimeout(0);

        switch (proxy.getType()) {
            case SOCKS5 -> socks5Handshake(socket, proxy, host, port);
            case SOCKS4 -> socks4Handshake(socket, proxy, host, port);
            case HTTP -> httpConnect(socket, proxy, host, port);
        }
        return socket;
    }

    // ===== SOCKS5 =====
    private static void socks5Handshake(Socket socket, ProxyData proxy, String host, int port) throws Exception {
        OutputStream out = socket.getOutputStream();
        InputStream in = socket.getInputStream();

        boolean hasAuth = proxy.getLogin() != null && !proxy.getLogin().isEmpty();
        out.write(hasAuth ? new byte[]{0x05, 0x02, 0x00, 0x02} : new byte[]{0x05, 0x01, 0x00});
        out.flush();

        byte[] resp = new byte[2];
        readFully(in, resp);
        if (resp[0] != 0x05 || resp[1] == (byte) 0xFF) throw new Exception("SOCKS5: no acceptable methods");

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
            readFully(in, ar);
            if (ar[1] != 0x00) throw new Exception("SOCKS5: auth failed");
        }

        // CONNECT с hostname (0x03), чтобы DNS резолвил прокси, а не мы
        java.io.ByteArrayOutputStream req = new java.io.ByteArrayOutputStream();
        req.write(0x05);
        req.write(0x01);
        req.write(0x00);
        req.write(0x03);
        byte[] hb = host.getBytes(StandardCharsets.UTF_8);
        req.write(hb.length);
        req.write(hb);
        req.write((port >> 8) & 0xFF);
        req.write(port & 0xFF);
        out.write(req.toByteArray());
        out.flush();

        byte[] head = new byte[4];
        readFully(in, head);
        if (head[1] != 0x00) throw new Exception("SOCKS5: connect failed rep=" + head[1]);
        skipBoundAddress(in, head[3]);
    }

    private static void skipBoundAddress(InputStream in, byte atyp) throws Exception {
        int len = switch (atyp) {
            case 0x01 -> 4;
            case 0x04 -> 16;
            case 0x03 -> {
                int l = in.read();
                yield l < 0 ? 0 : l;
            }
            default -> 0;
        };
        if (len > 0) readFully(in, new byte[len]);
        readFully(in, new byte[2]); // BND.PORT
    }

    // ===== SOCKS4a =====
    private static void socks4Handshake(Socket socket, ProxyData proxy, String host, int port) throws Exception {
        OutputStream out = socket.getOutputStream();
        InputStream in = socket.getInputStream();

        java.io.ByteArrayOutputStream req = new java.io.ByteArrayOutputStream();
        req.write(0x04);
        req.write(0x01);
        req.write((port >> 8) & 0xFF);
        req.write(port & 0xFF);
        req.write(new byte[]{0, 0, 0, 1}); // SOCKS4a: фейковый IP -> дальше hostname
        byte[] user = proxy.getLogin() == null ? new byte[0] : proxy.getLogin().getBytes(StandardCharsets.UTF_8);
        req.write(user);
        req.write(0);
        byte[] hb = host.getBytes(StandardCharsets.UTF_8);
        req.write(hb);
        req.write(0);
        out.write(req.toByteArray());
        out.flush();

        byte[] resp = new byte[8];
        readFully(in, resp);
        if (resp[1] != 0x5A) throw new Exception("SOCKS4: rejected cd=" + resp[1]);
    }

    // ===== HTTP CONNECT =====
    private static void httpConnect(Socket socket, ProxyData proxy, String host, int port) throws Exception {
        OutputStream out = socket.getOutputStream();
        InputStream in = socket.getInputStream();

        StringBuilder sb = new StringBuilder();
        sb.append("CONNECT ").append(host).append(':').append(port).append(" HTTP/1.1\r\n");
        sb.append("Host: ").append(host).append(':').append(port).append("\r\n");
        if (proxy.getLogin() != null && !proxy.getLogin().isEmpty()) {
            String auth = proxy.getLogin() + ":" + proxy.getPassword();
            sb.append("Proxy-Authorization: Basic ")
                    .append(java.util.Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8)))
                    .append("\r\n");
        }
        sb.append("\r\n");
        out.write(sb.toString().getBytes(StandardCharsets.UTF_8));
        out.flush();

        BufferedReader reader = new BufferedReader(new InputStreamReader(in));
        String status = reader.readLine();
        if (status == null || !status.contains("200")) {
            throw new Exception("HTTP CONNECT failed: " + status);
        }
        // дочитываем заголовки до пустой строки
        String line;
        while ((line = reader.readLine()) != null && !line.isEmpty()) { }
    }

    // ===== Перекачка =====
    private static void pump(Socket a, Socket b) {
        Thread t1 = copy(a, b);
        Thread t2 = copy(b, a);
        try { t1.join(); } catch (InterruptedException ignored) { }
        try { t2.join(); } catch (InterruptedException ignored) { }
        try { a.close(); } catch (Exception ignored) { }
        try { b.close(); } catch (Exception ignored) { }
    }

    private static Thread copy(Socket from, Socket to) {
        Thread t = new Thread(() -> {
            try {
                InputStream in = from.getInputStream();
                OutputStream out = to.getOutputStream();
                byte[] buf = new byte[16384];
                int n;
                while ((n = in.read(buf)) != -1) {
                    out.write(buf, 0, n);
                    out.flush();
                }
            } catch (Exception ignored) { }
            try { to.shutdownOutput(); } catch (Exception ignored) { }
        });
        t.setDaemon(true);
        t.start();
        return t;
    }

    private static void readFully(InputStream in, byte[] buf) throws Exception {
        int off = 0;
        while (off < buf.length) {
            int n = in.read(buf, off, buf.length - off);
            if (n < 0) throw new Exception("Unexpected EOF from proxy");
            off += n;
        }
    }
}
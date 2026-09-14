package polaris.screens.mainmenu;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.DirectJoinServerScreen;
import net.minecraft.client.gui.screens.FaviconTexture;
import net.minecraft.client.gui.screens.ManageServerScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import polaris.utils.compat.VfpHelper;
import polaris.utils.render.color.ColorUtil;
import polaris.utils.render.ui.Render2D;
import polaris.utils.render.ui.font.FontType;
import polaris.utils.render.ui.gif.GifRenderer;
import polaris.utils.render.ui.gif.MainMenuGifPreloader;
import polaris.screens.proxy.ProxyScreen;
import polaris.utils.proxy.ProxyManager;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class CustomMultiplayerScreen extends Screen {
    private static final float ROW_H = 38f;
    private static final float ROW_CONTENT = 34f;
    private static final float ROW_WIDTH = 305f;
    private static final float MOTD_MAX_W = 271f;
    private static final int MOTD_DEFAULT = ColorUtil.rgba(128, 128, 128, 255);

    private static final int PING_PROTOCOL = 47;
    private static final int CONNECT_TIMEOUT_MS = 2500;
    private static final int READ_TIMEOUT_MS = 2500;

    private static final ExecutorService PING_EXECUTOR = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "CustomServerPinger");
        t.setDaemon(true);
        return t;
    });

    private final Screen parent;
    private final Minecraft mc = Minecraft.getInstance();
    private ServerList servers;

    private final Map<String, IconSlot> icons = new HashMap<>();
    private final Map<String, PingInfo> pingInfo = new HashMap<>();
    private final List<Runnable> clientTasks = new ArrayList<>();

    private int selected = -1;
    private float scroll;
    private long lastClickMs;
    private GifRenderer backgroundGif;
    private final List<UiButton> buttons = new ArrayList<>();
    private RockstarMenuChrome.PanelGeom panel;
    private float listTop;
    private float listBottom;
    private float rowLeft;

    private volatile long pingSession = 0L;

    public CustomMultiplayerScreen(Screen parent) {
        super(Component.literal("Multiplayer"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        backgroundGif = MainMenuGifPreloader.background();
        if (backgroundGif != null && !backgroundGif.isReady() && !backgroundGif.isFailed()) {
            backgroundGif.ensureLoaded();
        }

        servers = new ServerList(mc);
        servers.load();
        selected = servers.size() > 0 ? 0 : -1;

        pingSession++;
        pingInfo.clear();
        synchronized (clientTasks) {
            clientTasks.clear();
        }

        rebuildLayout();
        pingAll();
    }

    @Override
    public void removed() {
        pingSession++;

        synchronized (clientTasks) {
            clientTasks.clear();
        }

        for (IconSlot slot : icons.values()) {
            try {
                slot.icon.close();
            } catch (Throwable ignored) {
            }
        }
        icons.clear();
        super.removed();
    }

    @Override
    public void tick() {
        List<Runnable> tasksToRun = null;

        synchronized (clientTasks) {
            if (!clientTasks.isEmpty()) {
                tasksToRun = new ArrayList<>(clientTasks);
                clientTasks.clear();
            }
        }

        if (tasksToRun != null) {
            for (Runnable task : tasksToRun) {
                try {
                    task.run();
                } catch (Throwable ignored) {
                }
            }
        }

        super.tick();
    }

    private void rebuildLayout() {
        buttons.clear();
        float dw = Render2D.getFixedScaledWidth();
        float dh = Render2D.getFixedScaledHeight();
        panel = RockstarMenuChrome.panelMulti(dw, dh);
        listTop = panel.y() + 10f;
        listBottom = panel.y() + panel.h() - 10f;
        rowLeft = dw * 0.5f - 150f;

        float by1 = dh - 52f;
        float by2 = dh - 28f;
        buttons.add(new UiButton("join", "Войти", dw * 0.5f - 154f, by1, 100f, 20f, true));
        buttons.add(new UiButton("direct", "Прямое подключение", dw * 0.5f - 50f, by1, 100f, 20f, false));
        buttons.add(new UiButton("add", "Добавить", dw * 0.5f + 54f, by1, 100f, 20f, false));
        buttons.add(new UiButton("edit", "Изменить", dw * 0.5f - 154f, by2, 70f, 20f, false));
        buttons.add(new UiButton("delete", "Удалить", dw * 0.5f - 74f, by2, 70f, 20f, false));
        buttons.add(new UiButton("refresh", "Обновить", dw * 0.5f + 4f, by2, 70f, 20f, false));
        buttons.add(new UiButton("back", "Отмена", dw * 0.5f + 80f, by2, 75f, 20f, false));

        if (VfpHelper.isAvailable()) {
            String ver = VfpHelper.getVersionName();
            String label = "Версия: " + (ver == null || ver.isBlank() ? "—" : ver);
            buttons.add(new UiButton("vfp", label, dw - 160f, 10f, 150f, 22f, false));
        }
        boolean proxyOn = ProxyManager.getInstance().getActiveProxy() != null;
        buttons.add(new UiButton("proxy", proxyOn ? "Прокси: вкл" : "Прокси", dw - 85f, dh - 28f, 75f, 20f, proxyOn));
    }
        private void pingAll() {
        if (servers == null) {
            return;
        }

        final long session = pingSession;

        for (int i = 0; i < servers.size(); i++) {
            ServerData data = servers.get(i);
            if (data == null || data.ip == null || data.ip.isBlank()) {
                continue;
            }

            String key = key(data);
            PING_EXECUTOR.submit(() -> pingOne(data, key, session));
        }
    }

    private void pingOne(ServerData data, String key, long session) {
        if (session != pingSession) {
            return;
        }

        PingInfo info = new PingInfo();
        Socket socket = new Socket();

        try {
            String ip = data.ip.trim();
            String host = ip;
            int port = 25565;

            if (host.startsWith("[")) {
                int end = host.indexOf(']');
                if (end > 1) {
                    String after = host.substring(end + 1);
                    host = host.substring(1, end);
                    if (after.startsWith(":")) {
                        port = parseInt(after.substring(1), 25565);
                    }
                }
            } else {
                int firstColon = host.indexOf(':');
                int lastColon = host.lastIndexOf(':');
                if (firstColon >= 0 && firstColon == lastColon) {
                    port = parseInt(host.substring(firstColon + 1), 25565);
                    host = host.substring(0, firstColon);
                }
            }

            if (host.isEmpty()) {
                info.success = false;
                finishPing(key, info, session);
                return;
            }

            socket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
            socket.setSoTimeout(READ_TIMEOUT_MS);

            DataOutputStream out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
            DataInputStream in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));

            sendHandshake(out, host, port);
            sendStatusRequest(out);

            String json = readStatusResponse(in);
            parseStatus(json, info);

            try {
                info.ping = sendPing(out, in);
            } catch (Throwable t) {
                info.ping = -1L;
            }

            info.success = true;
        } catch (Throwable t) {
            info.success = false;
            info.ping = -1L;
        } finally {
            try {
                socket.close();
            } catch (Throwable ignored) {
            }
        }

        finishPing(key, info, session);
    }

    private void finishPing(String key, PingInfo info, long session) {
        if (session != pingSession) {
            return;
        }

        runOnClient(() -> {
            if (session == pingSession) {
                pingInfo.put(key, info);
            }
        });
    }

    private void runOnClient(Runnable task) {
        synchronized (clientTasks) {
            clientTasks.add(task);
        }
    }

    private static void sendHandshake(DataOutputStream out, String host, int port) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        DataOutputStream packet = new DataOutputStream(buf);

        writeVarInt(packet, 0x00);
        writeVarInt(packet, PING_PROTOCOL);
        writeString(packet, host);
        packet.writeShort(port);
        writeVarInt(packet, 1);
        packet.flush();

        byte[] payload = buf.toByteArray();
        writeVarInt(out, payload.length);
        out.write(payload);
        out.flush();
    }

    private static void sendStatusRequest(DataOutputStream out) throws IOException {
        writeVarInt(out, 1);
        writeVarInt(out, 0x00);
        out.flush();
    }

    private static String readStatusResponse(DataInputStream in) throws IOException {
        int length = readVarInt(in);
        int packetId = readVarInt(in);

        if (packetId != 0x00) {
            throw new IOException("Bad status packet id: " + packetId);
        }

        int stringLength = readVarInt(in);
        byte[] bytes = readBytes(in, stringLength);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static long sendPing(DataOutputStream out, DataInputStream in) throws IOException {
        long payload = System.currentTimeMillis();

        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        DataOutputStream packet = new DataOutputStream(buf);

        writeVarInt(packet, 0x01);
        packet.writeLong(payload);
        packet.flush();

        byte[] bytes = buf.toByteArray();
        writeVarInt(out, bytes.length);
        out.write(bytes);
        out.flush();

        long start = System.currentTimeMillis();

        int length = readVarInt(in);
        int packetId = readVarInt(in);

        if (packetId != 0x01) {
            throw new IOException("Bad ping packet id: " + packetId);
        }

        long response = in.readLong();
        return System.currentTimeMillis() - start;
    }

    private static void parseStatus(String json, PingInfo info) {
        try {
            JsonElement root = JsonParser.parseString(json);
            if (!root.isJsonObject()) {
                return;
            }

            JsonObject obj = root.getAsJsonObject();

            if (obj.has("players") && obj.get("players").isJsonObject()) {
                JsonObject players = obj.getAsJsonObject("players");
                if (players.has("online")) {
                    info.online = players.get("online").getAsInt();
                }
                if (players.has("max")) {
                    info.max = players.get("max").getAsInt();
                }
            }

            if (obj.has("description")) {
                info.motd = parseDescription(obj.get("description"));
            }
        } catch (Throwable ignored) {
        }
    }

    private static Component parseDescription(JsonElement description) {
        if (description == null || description.isJsonNull()) {
            return null;
        }

        try {
            if (description.isJsonPrimitive()) {
                return Component.literal(description.getAsString());
            }

            String json = description.toString();

            Component parsed = tryParseComponentJson(json, description);
            if (parsed != null) {
                return parsed;
            }

            String flat = flattenDescription(description);
            if (!flat.isEmpty()) {
                return Component.literal(flat);
            }
        } catch (Throwable ignored) {
        }

        return null;
    }

    private static Component tryParseComponentJson(String json, JsonElement element) {
        try {
            Class<?> serializer = Class.forName("net.minecraft.network.chat.Component$Serializer");

            for (Method method : serializer.getMethods()) {
                if (!Modifier.isStatic(method.getModifiers())) {
                    continue;
                }

                if (!method.getName().equals("fromJson")) {
                    continue;
                }

                Class<?>[] params = method.getParameterTypes();

                try {
                    if (params.length == 1) {
                        Object arg = null;

                        if (params[0] == String.class) {
                            arg = json;
                        } else if (JsonElement.class.isAssignableFrom(params[0])) {
                            arg = element;
                        }

                        if (arg != null) {
                            Object result = method.invoke(null, arg);
                            if (result instanceof Component) {
                                return (Component) result;
                            }
                        }
                    } else if (params.length == 2) {
                        Object arg = null;

                        if (params[0] == String.class) {
                            arg = json;
                        } else if (JsonElement.class.isAssignableFrom(params[0])) {
                            arg = element;
                        }

                        if (arg != null) {
                            Object result = method.invoke(null, arg, null);
                            if (result instanceof Component) {
                                return (Component) result;
                            }
                        }
                    }
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }

        return null;
    }

    private static String flattenDescription(JsonElement element) {
        StringBuilder sb = new StringBuilder();
        flattenDescription(element, sb);
        return sb.toString();
    }

    private static void flattenDescription(JsonElement element, StringBuilder sb) {
        if (element == null || element.isJsonNull()) {
            return;
        }

        if (element.isJsonPrimitive()) {
            sb.append(element.getAsString());
            return;
        }

        if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            for (JsonElement child : array) {
                flattenDescription(child, sb);
            }
            return;
        }

        if (element.isJsonObject()) {
            JsonObject obj = element.getAsJsonObject();

            if (obj.has("text")) {
                flattenDescription(obj.get("text"), sb);
            }

            if (obj.has("extra")) {
                flattenDescription(obj.get("extra"), sb);
            }
        }
    }

    private static void writeVarInt(DataOutputStream out, int value) throws IOException {
        do {
            byte temp = (byte) (value & 0x7F);
            value >>>= 7;
            if (value != 0) {
                temp |= 0x80;
            }
            out.write(temp);
        } while (value != 0);
    }

    private static int readVarInt(DataInputStream in) throws IOException {
        int value = 0;
        int size = 0;

        while (true) {
            int b = in.read();
            if (b == -1) {
                throw new IOException("Unexpected end of stream while reading VarInt");
            }

            value |= (b & 0x7F) << (size * 7);

            if ((b & 0x80) == 0) {
                return value;
            }

            size++;
            if (size > 5) {
                throw new IOException("VarInt too big");
            }
        }
    }

    private static void writeString(DataOutputStream out, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        writeVarInt(out, bytes.length);
        out.write(bytes);
    }

    private static byte[] readBytes(DataInputStream in, int length) throws IOException {
        byte[] bytes = new byte[length];
        int offset = 0;

        while (offset < length) {
            int read = in.read(bytes, offset, length - offset);
            if (read < 0) {
                throw new IOException("Unexpected end of stream");
            }
            offset += read;
        }

        return bytes;
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Throwable t) {
            return fallback;
        }
    }

    private static String key(ServerData data) {
        return data.ip == null ? "" : data.ip;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        float dw = Render2D.getFixedScaledWidth();
        float dh = Render2D.getFixedScaledHeight();
        float mx = (float) Render2D.guiToFixed(mouseX);
        float my = (float) Render2D.guiToFixed(mouseY);

        if (buttons.isEmpty() || panel == null) {
            rebuildLayout();
        }

        float listH = Math.max(1f, listBottom - listTop);
        float maxScroll = Math.max(0f, servers.size() * ROW_H - listH + 4f);
        scroll = Math.max(0f, Math.min(scroll, maxScroll));

        Render2D.beginFrame(g);

        Render2D.rect(0, 0, dw, dh, 0f, ColorUtil.rgba(6, 8, 12, 255));
        if (backgroundGif != null) {
            backgroundGif.renderCover(0, 0, dw, dh, ColorUtil.rgba(255, 255, 255, 255));
        }

        Render2D.rect(0, 0, dw, dh, 0f, ColorUtil.rgba(0, 0, 0, 90));

        RockstarMenuChrome.drawPanel(panel, false);

        String title = "Многопользовательская игра";
        float titleSize = 10f;
        float tw = Render2D.textWidth(FontType.SEMIBOLD, title, titleSize);
        Render2D.text(FontType.SEMIBOLD, title, dw * 0.5f - tw * 0.5f, panel.y() - 18f, titleSize,
                RockstarMenuChrome.COL_TEXT);

        Render2D.pushScissor(g, panel.x() + 1f, panel.y() + 1f, panel.w() - 2f, panel.h() - 2f);
        if (servers.size() == 0) {
            String empty = "Список серверов пуст";
            float ew = Render2D.textWidth(FontType.SEMIBOLD, empty, 8.5f);
            Render2D.text(FontType.SEMIBOLD, empty, dw * 0.5f - ew * 0.5f, listTop + 24f, 8.5f,
                    RockstarMenuChrome.COL_TEXT_DIM);
        } else {
            for (int i = 0; i < servers.size(); i++) {
                float ry = listTop + 4f - scroll + i * ROW_H;
                if (ry + ROW_CONTENT < listTop || ry > listBottom) {
                    continue;
                }
                drawServerRow(i, ry, mx, my);
            }
        }
        Render2D.popScissor(g);

        for (UiButton b : buttons) {
            drawButton(b);
        }

        Render2D.flush();
    }

    private void drawServerRow(int index, float y, float mx, float my) {
        ServerData data = servers.get(index);
        boolean sel = index == selected;
        boolean hovIcon = mx >= rowLeft && mx <= rowLeft + 32f
                && my >= y && my <= y + 32f;

        if (sel) {
            Render2D.rect(rowLeft - 2f, y - 2f, ROW_WIDTH + 4f, ROW_CONTENT + 4f, 0f,
                    ColorUtil.rgba(128, 128, 128, 255));
            Render2D.rect(rowLeft - 1f, y - 1f, ROW_WIDTH + 2f, ROW_CONTENT + 2f, 0f,
                    ColorUtil.rgba(0, 0, 0, 255));
        }

        Render2D.rect(rowLeft, y, 32f, 32f, 0f, ColorUtil.rgba(40, 42, 50, 255));
        Identifier iconId = syncIcon(data);
        if (iconId != null) {
            Render2D.image(iconId.toString(), rowLeft, y, 32f, 32f, 0f,
                    ColorUtil.rgba(255, 255, 255, 255));
        }
        if (hovIcon) {
            Render2D.rect(rowLeft, y, 32f, 32f, 3f, ColorUtil.rgba(255, 255, 255, 128));
        }

        String name = data.name == null || data.name.isBlank() ? "Сервер" : data.name;
        Render2D.text(FontType.SEMIBOLD, name, rowLeft + 35f, y + 1f, 8.5f, RockstarMenuChrome.COL_TEXT);

        PingInfo info = pingInfo.get(key(data));
        Component motd = info != null && info.motd != null ? info.motd : data.motd;

        if (motd != null) {
            drawStyledMotd(motd, rowLeft + 35f, y + 12f, MOTD_MAX_W, 7.5f);
        }

        String online = onlineLabel(info);
        String ping = pingLabel(info);
        float right = rowLeft + 299f;

        if (!ping.isEmpty()) {
            float pingW = Render2D.textWidth(FontType.SEMIBOLD, ping, 7.5f);
            Render2D.text(FontType.SEMIBOLD, ping, right - pingW, y, 7.5f, pingColor(info));
            right -= pingW + 6f;
        }

        if (!online.isEmpty()) {
            float onlineW = Render2D.textWidth(FontType.SEMIBOLD, online, 7.5f);
            Render2D.text(FontType.SEMIBOLD, online, right - onlineW, y, 7.5f,
                    ColorUtil.rgba(180, 180, 180, 240));
        }
    }

    private Identifier syncIcon(ServerData data) {
        String key = data.ip == null ? "" : data.ip;
        IconSlot slot = icons.get(key);
        if (slot == null) {
            slot = new IconSlot(FaviconTexture.forServer(mc.getTextureManager(), key));
            icons.put(key, slot);
        }

        byte[] bytes = data.getIconBytes();
        if (!Arrays.equals(bytes, slot.lastBytes)) {
            try {
                if (bytes == null) {
                    slot.icon.clear();
                } else {
                    NativeImage image = NativeImage.read(bytes);
                    slot.icon.upload(image);
                    Render2D.invalidateImageTexture(slot.icon.textureLocation());
                }
                slot.lastBytes = bytes == null ? null : Arrays.copyOf(bytes, bytes.length);
            } catch (Throwable t) {
                try {
                    slot.icon.clear();
                } catch (Throwable ignored) {
                }
                slot.lastBytes = null;
            }
        }
        return slot.icon.textureLocation();
    }

    private void drawStyledMotd(Component motd, float x, float y, float maxW, float size) {
        float lineH = size + 1.5f;
        final float[] penX = {x};
        final float[] penY = {y};
        final float[] used = {0f};
        final int[] line = {0};

        motd.visit((style, text) -> {
            if (text == null || text.isEmpty() || line[0] >= 2) {
                return Optional.empty();
            }
            int color = styleToRgba(style, MOTD_DEFAULT);
            String[] parts = text.split("\n", -1);
            for (int p = 0; p < parts.length; p++) {
                if (p > 0) {
                    line[0]++;
                    if (line[0] >= 2) {
                        return Optional.of(Boolean.TRUE);
                    }
                    penX[0] = x;
                    penY[0] = y + line[0] * lineH;
                    used[0] = 0f;
                }
                String chunk = parts[p];
                if (chunk.isEmpty()) {
                    continue;
                }
                float remaining = maxW - used[0];
                if (remaining <= 1f) {
                    continue;
                }
                String draw = chunk;
                float w = Render2D.textWidth(FontType.REGULARNEW, draw, size);
                if (w > remaining) {
                    draw = ellipsize(draw, remaining, size);
                    w = Render2D.textWidth(FontType.REGULARNEW, draw, size);
                }
                if (!draw.isEmpty()) {
                    Render2D.text(FontType.REGULARNEW, draw, penX[0], penY[0], size, color);
                    penX[0] += w;
                    used[0] += w;
                }
            }
            return Optional.empty();
        }, Style.EMPTY);
    }

    private static int styleToRgba(Style style, int fallback) {
        if (style == null) {
            return fallback;
        }
        TextColor tc = style.getColor();
        if (tc == null) {
            return fallback;
        }
        int rgb = tc.getValue() & 0xFFFFFF;
        return 0xFF000000 | rgb;
    }

    private static String onlineLabel(PingInfo info) {
        if (info == null) {
            return "…";
        }
        if (!info.success) {
            return "–";
        }
        return info.online + "/" + info.max;
    }

    private static String pingLabel(PingInfo info) {
        if (info == null || !info.success || info.ping < 0L) {
            return "";
        }
        return info.ping + "ms";
    }

    private static int pingColor(PingInfo info) {
        if (info == null || info.ping < 0L) {
            return ColorUtil.rgba(128, 128, 128, 230);
        }

        long p = info.ping;
        if (p < 80L) {
            return ColorUtil.rgba(85, 255, 85, 255);
        }
        if (p < 150L) {
            return ColorUtil.rgba(255, 255, 85, 255);
        }
        if (p < 300L) {
            return ColorUtil.rgba(255, 170, 0, 255);
        }
        return ColorUtil.rgba(255, 85, 85, 255);
    }

    private static String ellipsize(String text, float maxW, float size) {
        return ellipsize(FontType.REGULARNEW, text, maxW, size);
    }

    private static String ellipsize(FontType type, String text, float maxW, float size) {
        if (Render2D.textWidth(type, text, size) <= maxW) {
            return text;
        }
        String ell = "…";
        int lo = 0;
        int hi = text.length();
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (Render2D.textWidth(type, text.substring(0, mid) + ell, size) <= maxW) {
                lo = mid;
            } else {
                hi = mid - 1;
            }
        }
        return lo <= 0 ? ell : text.substring(0, lo) + ell;
    }

    private void drawButton(UiButton b) {
        if (b.primary) {
            Render2D.rect(b.x, b.y, b.w, b.h, 3f, RockstarMenuChrome.COL_ACCENT);
        } else {
            Render2D.rect(b.x, b.y, b.w, b.h, 3f, RockstarMenuChrome.COL_SECOND);
        }
        Render2D.outline(b.x, b.y, b.w, b.h, 3f, 0.8f, RockstarMenuChrome.COL_FOURS_OUTLINE);
        float size = 8.5f;
        String label = b.label;
        float maxW = b.w - 8f;
        if (Render2D.textWidth(FontType.SEMIBOLD, label, size) > maxW) {
            label = ellipsize(FontType.SEMIBOLD, label, maxW, size);
        }
        float tw = Render2D.textWidth(FontType.SEMIBOLD, label, size);
        Render2D.text(FontType.SEMIBOLD, label, b.x + (b.w - tw) * 0.5f, b.y + (b.h - size) * 0.5f, size,
                RockstarMenuChrome.COL_TEXT);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        float mx = (float) Render2D.guiToFixed(event.x());
        float my = (float) Render2D.guiToFixed(event.y());

        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (my >= listTop && my <= listBottom
                    && mx >= rowLeft && mx <= rowLeft + ROW_WIDTH) {
                int idx = (int) ((my - listTop + scroll - 4f) / ROW_H);
                if (idx >= 0 && idx < servers.size()) {
                    long now = System.currentTimeMillis();
                    float localX = mx - rowLeft;
                    float localY = my - (listTop + 4f - scroll + idx * ROW_H);
                    if (localX >= 0f && localX <= 32f && localY >= 0f && localY <= 32f) {
                        selected = idx;
                        if (localX < 16f) {
                            swapSelected(localY < 16f ? -1 : 1);
                        } else {
                            joinSelected();
                        }
                        return true;
                    }
                    if (idx == selected && now - lastClickMs < 250L) {
                        joinSelected();
                        return true;
                    }
                    selected = idx;
                    lastClickMs = now;
                    return true;
                }
            }

            for (UiButton b : buttons) {
                if (b.contains(mx, my)) {
                    onAction(b.id);
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubled);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        float mx = (float) Render2D.guiToFixed(mouseX);
        float my = (float) Render2D.guiToFixed(mouseY);
        if (my >= listTop && my <= listBottom && mx >= rowLeft - 20f && mx <= rowLeft + ROW_WIDTH + 20f) {
            scroll = Math.max(0f, scroll - (float) verticalAmount * 19f);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            mc.setScreen(parent);
            return true;
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            joinSelected();
            return true;
        }
        if (key == GLFW.GLFW_KEY_DELETE) {
            deleteSelected();
            return true;
        }
        if (key == GLFW.GLFW_KEY_F5) {
            refresh();
            return true;
        }
        if (key == GLFW.GLFW_KEY_UP && selected > 0) {
            selected--;
            ensureVisible();
            return true;
        }
        if (key == GLFW.GLFW_KEY_DOWN && selected < servers.size() - 1) {
            selected++;
            ensureVisible();
            return true;
        }
        return super.keyPressed(event);
    }

    private void ensureVisible() {
        float y = selected * ROW_H;
        float listH = listBottom - listTop;
        if (y < scroll) {
            scroll = y;
        } else if (y + ROW_H > scroll + listH) {
            scroll = y + ROW_H - listH;
        }
    }

    private void swapSelected(int dir) {
        if (selected < 0 || selected >= servers.size()) {
            return;
        }
        int target = selected + dir;
        if (target < 0 || target >= servers.size()) {
            return;
        }
        try {
            servers.swap(selected, target);
            servers.save();
            selected = target;
        } catch (Throwable ignored) {
        }
    }

    private void onAction(String id) {
        switch (id) {
            case "join" -> joinSelected();
            case "direct" -> openDirect();
            case "add" -> openAdd();
            case "edit" -> openEdit();
            case "delete" -> deleteSelected();
            case "refresh" -> refresh();
            case "back" -> mc.setScreen(parent);
            case "proxy" -> mc.setScreen(new ProxyScreen(this));
            case "vfp" -> VfpHelper.openScreen(this);
            default -> {
            }
        }
    }

    private void joinSelected() {
        if (selected < 0 || selected >= servers.size()) {
            return;
        }
        join(servers.get(selected));
    }

    private void join(ServerData data) {
        if (data == null || data.ip == null || data.ip.isBlank()) {
            return;
        }

        ServerAddress address = ServerAddress.parseString(data.ip);
        ConnectScreen.startConnecting(this, mc, address, data, false, null);
    }

    private void openDirect() {
        ServerData data = new ServerData("Minecraft Server", "", ServerData.Type.OTHER);
        mc.setScreen(new DirectJoinServerScreen(this, accepted -> {
            if (accepted) {
                join(data);
            } else {
                mc.setScreen(this);
            }
        }, data));
    }

    private void openAdd() {
        ServerData data = new ServerData("Minecraft Server", "", ServerData.Type.OTHER);
        mc.setScreen(new ManageServerScreen(this, Component.literal("Добавить сервер"), accepted -> {
            if (accepted) {
                servers.add(data, false);
                servers.save();
                selected = servers.size() - 1;
                pingAll();
            }
            mc.setScreen(this);
        }, data));
    }

    private void openEdit() {
        if (selected < 0 || selected >= servers.size()) {
            return;
        }
        ServerData original = servers.get(selected);
        ServerData data = new ServerData(original.name, original.ip, original.type());
        data.copyFrom(original);
        final int idx = selected;
        mc.setScreen(new ManageServerScreen(this, Component.literal("Изменить сервер"), accepted -> {
            if (accepted) {
                servers.replace(idx, data);
                servers.save();
                pingAll();
            }
            mc.setScreen(this);
        }, data));
    }

    private void deleteSelected() {
        if (selected < 0 || selected >= servers.size()) {
            return;
        }
        ServerData data = servers.get(selected);
        String name = data.name == null ? data.ip : data.name;
        mc.setScreen(new ConfirmScreen(accepted -> {
            if (accepted) {
                String ip = data.ip == null ? "" : data.ip;

                IconSlot slot = icons.remove(ip);
                if (slot != null) {
                    try {
                        slot.icon.close();
                    } catch (Throwable ignored) {
                    }
                }

                pingInfo.remove(ip);

                servers.remove(data);
                servers.save();
                if (selected >= servers.size()) {
                    selected = servers.size() - 1;
                }
            }
            mc.setScreen(this);
        }, Component.literal("Удалить сервер?"), Component.literal("«" + name + "» будет удалён из списка.")));
    }

    private void refresh() {
        servers.load();
        if (selected >= servers.size()) {
            selected = servers.size() - 1;
        }

        Map<String, IconSlot> newIcons = new HashMap<>();
        Set<String> currentKeys = new HashSet<>();

        for (int i = 0; i < servers.size(); i++) {
            ServerData data = servers.get(i);
            String key = key(data);
            currentKeys.add(key);

            if (icons.containsKey(key)) {
                newIcons.put(key, icons.get(key));
            }
        }

        for (Map.Entry<String, IconSlot> entry : icons.entrySet()) {
            if (!newIcons.containsKey(entry.getKey())) {
                try {
                    entry.getValue().icon.close();
                } catch (Throwable ignored) {
                }
            }
        }

        icons.clear();
        icons.putAll(newIcons);

        pingInfo.keySet().retainAll(currentKeys);

        pingAll();
    }

    @Override
    public void onClose() {
        mc.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static final class PingInfo {
        boolean success;
        int online;
        int max;
        long ping = -1L;
        Component motd;
    }

    private static final class IconSlot {
        final FaviconTexture icon;
        byte[] lastBytes;

        IconSlot(FaviconTexture icon) {
            this.icon = icon;
        }
    }

    private static final class UiButton {
        final String id;
        final String label;
        final float x, y, w, h;
        final boolean primary;

        UiButton(String id, String label, float x, float y, float w, float h, boolean primary) {
            this.id = id;
            this.label = label;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.primary = primary;
        }

        boolean contains(float mx, float my) {
            return mx >= x && mx <= x + w && my >= y && my <= y + h;
        }
    }
}
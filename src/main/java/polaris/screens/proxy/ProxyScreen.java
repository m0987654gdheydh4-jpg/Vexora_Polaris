package polaris.screens.proxy;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import polaris.utils.proxy.ProxyData;
import polaris.utils.proxy.ProxyManager;
import polaris.utils.render.color.ColorUtil;
import polaris.utils.render.ui.Render2D;
import polaris.utils.render.ui.font.FontType;

import java.util.ArrayList;
import java.util.List;

public class ProxyScreen extends Screen {
    private final Screen parent;
    private final ProxyManager proxyManager = ProxyManager.getInstance();

    private final List<ProxyData> filteredProxies = new ArrayList<>();
    private String searchQuery = "";
    private boolean searchFocused = false;
    private boolean ctrlDown = false;

    private int selectedProxyIndex = -1;
    private int scrollOffset;

    private boolean addingNew = false;
    private String addName = "", addIp = "", addPort = "", addLogin = "", addPassword = "";
    private EditingField addFocus = EditingField.NONE;

    private enum EditingField { NONE, NAME, IP, PORT, LOGIN, PASSWORD }

    public ProxyScreen(Screen parent) {
        super(Component.literal("Proxy Manager"));
        this.parent = parent;
        refreshList();
    }

    // ================= Геометрия =================

    private float searchX() { return 20f; }
    private float searchY() { return 50f; }
    private float searchW() { return width - 40f; }
    private float searchH() { return 24f; }

    private float listX() { return 20f; }
    private float listY() { return 85f; }
    private float listW() { return width - 40f; }
    private float listH() { return height - 180f; }
    private float rowH() { return 32f; }

    // Кнопки в строке (справа налево): Delete | Connect/Disconnect | Ping
    private float delBtnW() { return 52f; }
    private float connBtnW() { return 84f; }
    private float pingBtnW() { return 44f; }
    private float rowBtnH() { return 20f; }
    private float rowBtnY(float rowY) { return rowY + 6f; }
    private float delBtnX() { return listX() + listW() - delBtnW(); }
    private float connBtnX() { return delBtnX() - 4f - connBtnW(); }
    private float pingBtnX() { return connBtnX() - 4f - pingBtnW(); }

    private float panelW() { return 260f; }
    private float panelH() { return 214f; }
    private float panelX() { return width / 2f - panelW() / 2f; }
    private float panelY() { return height / 2f - panelH() / 2f; }
    private float fieldX() { return panelX() + 12f; }
    private float fieldW() { return panelW() - 24f; }
    private float fieldY(int i) { return panelY() + 30f + i * 26f; }
    private float panelButtonsY() { return panelY() + 178f; }

    private boolean caretOn() { return (System.currentTimeMillis() / 400) % 2 == 0; }

    private void refreshList() {
        filteredProxies.clear();
        for (ProxyData proxy : proxyManager.getProxies()) {
            if (searchQuery.isEmpty()
                    || proxy.getName().toLowerCase().contains(searchQuery.toLowerCase())
                    || proxy.getIp().contains(searchQuery)) {
                filteredProxies.add(proxy);
            }
        }
    }

    // ================= Рендер =================

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        float dw = width;
        float dh = height;

        Render2D.beginFrame(g);

        Render2D.rect(0, 0, dw, dh, 0f, ColorUtil.rgba(10, 10, 15, 255));
        Render2D.rect(0, 0, dw, dh, 0f, ColorUtil.rgba(0, 0, 0, 180));

        String title = "Proxy Manager";
        float titleSize = 12f;
        float titleW = Render2D.textWidth(FontType.SEMIBOLD, title, titleSize);
        Render2D.text(FontType.SEMIBOLD, title, dw / 2 - titleW / 2, 20f, titleSize, ColorUtil.rgba(255, 255, 255, 255));

        // ===== Поиск =====
        Render2D.rect(searchX(), searchY(), searchW(), searchH(), 2f,
                searchFocused ? ColorUtil.rgba(50, 50, 65, 255) : ColorUtil.rgba(40, 40, 50, 255));
        if (searchFocused) {
            Render2D.outline(searchX(), searchY(), searchW(), searchH(), 2f, 0.8f, ColorUtil.rgba(120, 160, 255, 255));
        }
        if (searchQuery.isEmpty() && !searchFocused) {
            Render2D.text(FontType.REGULARNEW, "Search...", searchX() + 8f, searchY() + 7f, 8.5f, ColorUtil.rgba(128, 128, 128, 255));
        } else {
            Render2D.text(FontType.REGULARNEW, searchQuery, searchX() + 8f, searchY() + 7f, 8.5f, ColorUtil.rgba(255, 255, 255, 255));
        }
        if (searchFocused && caretOn()) {
            float cx = searchX() + 8f + Render2D.textWidth(FontType.REGULARNEW, searchQuery, 8.5f);
            Render2D.rect(cx + 1f, searchY() + 5f, 1f, searchH() - 10f, 0f, ColorUtil.rgba(255, 255, 255, 255));
        }

        // ===== Список =====
        Render2D.pushScissor(g, listX(), listY(), listW(), listH());

        int visibleCount = (int) (listH() / rowH()) + 1;
        int startIndex = scrollOffset;
        int endIndex = Math.min(startIndex + visibleCount, filteredProxies.size());

        for (int i = startIndex; i < endIndex; i++) {
            ProxyData proxy = filteredProxies.get(i);
            float y = listY() + (i - startIndex) * rowH();
            boolean selected = i == selectedProxyIndex;
            boolean isActive = proxyManager.getActiveProxy() == proxy;

            int bgColor = selected ? ColorUtil.rgba(60, 60, 80, 255)
                    : (i % 2 == 0 ? ColorUtil.rgba(35, 35, 45, 255) : ColorUtil.rgba(40, 40, 50, 255));
            Render2D.rect(listX(), y, listW(), rowH() - 2f, 2f, bgColor);

            if (isActive) {
                Render2D.rect(listX(), y, 4f, rowH() - 2f, 0f, ColorUtil.rgba(100, 255, 100, 255));
            }

            Render2D.text(FontType.SEMIBOLD, proxy.getName(), listX() + 10f, y + 5f, 9f, ColorUtil.rgba(255, 255, 255, 255));
            Render2D.text(FontType.REGULARNEW, proxy.getDisplayAddress(), listX() + listW() * 0.22f, y + 6f, 8f, ColorUtil.rgba(200, 200, 200, 255));

            String typeText = proxy.getPing() == -2 ? "detect..." : proxy.getType().name();
            Render2D.text(FontType.REGULARNEW, typeText, listX() + listW() * 0.45f, y + 6f, 8f, ColorUtil.rgba(150, 150, 200, 255));

            String pingText = proxy.getPing() == -2 ? "..." : proxy.getPing() < 0 ? "fail" : proxy.getPing() + "ms";
            int pingColor = proxy.getPing() == -2 ? ColorUtil.rgba(200, 200, 200, 255)
                    : proxy.getPing() < 0 ? ColorUtil.rgba(255, 100, 100, 255)
                      : proxy.getPing() < 100 ? ColorUtil.rgba(100, 255, 100, 255)
                        : proxy.getPing() < 300 ? ColorUtil.rgba(255, 255, 100, 255)
                          : ColorUtil.rgba(255, 150, 100, 255);
            Render2D.text(FontType.REGULARNEW, pingText, listX() + listW() * 0.62f, y + 6f, 8f, pingColor);

            // ===== Кнопки: Ping | Connect/Disconnect | Delete =====
            float by = rowBtnY(y);
            drawTextButton(pingBtnX(), by, pingBtnW(), "Ping", mouseX, mouseY,
                    ColorUtil.rgba(60, 60, 80, 255), ColorUtil.rgba(80, 80, 100, 255));
            drawTextButton(connBtnX(), by, connBtnW(), isActive ? "Disconnect" : "Connect", mouseX, mouseY,
                    isActive ? ColorUtil.rgba(100, 60, 60, 255) : ColorUtil.rgba(60, 90, 60, 255),
                    isActive ? ColorUtil.rgba(120, 70, 70, 255) : ColorUtil.rgba(70, 110, 70, 255));
            drawTextButton(delBtnX(), by, delBtnW(), "Delete", mouseX, mouseY,
                    ColorUtil.rgba(80, 60, 60, 255), ColorUtil.rgba(100, 60, 60, 255));
        }

        if (filteredProxies.isEmpty()) {
            String empty = searchQuery.isEmpty() ? "No proxies added" : "No proxies found";
            float emptyW = Render2D.textWidth(FontType.REGULARNEW, empty, 9f);
            Render2D.text(FontType.REGULARNEW, empty, listX() + listW() / 2 - emptyW / 2, listY() + listH() / 2, 9f, ColorUtil.rgba(128, 128, 128, 255));
        }

        Render2D.popScissor(g);

        // ===== Нижние кнопки =====
        float btnY = dh - 50f;
        float btnH = 30f;

        boolean hoverAdd = mouseX >= 20f && mouseX <= 120f && mouseY >= btnY && mouseY <= btnY + btnH;
        Render2D.rect(20f, btnY, 100f, btnH, 3f, hoverAdd ? ColorUtil.rgba(80, 120, 80, 255) : ColorUtil.rgba(60, 90, 60, 255));
        Render2D.text(FontType.SEMIBOLD, "Add Proxy", 35f, btnY + 9f, 10f, ColorUtil.rgba(255, 255, 255, 255));

        boolean hoverBack = mouseX >= dw - 120f && mouseX <= dw - 20f && mouseY >= btnY && mouseY <= btnY + btnH;
        Render2D.rect(dw - 120f, btnY, 100f, btnH, 3f, hoverBack ? ColorUtil.rgba(100, 80, 80, 255) : ColorUtil.rgba(80, 60, 60, 255));
        Render2D.text(FontType.SEMIBOLD, "Back", dw - 90f, btnY + 9f, 10f, ColorUtil.rgba(255, 255, 255, 255));

        if (addingNew) {
            renderAddPanel(mouseX, mouseY);
        }

        Render2D.flush();
    }

    private void drawTextButton(float x, float y, float w, String label, int mouseX, int mouseY, int normal, int hovered) {
        boolean hover = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + rowBtnH();
        Render2D.rect(x, y, w, rowBtnH(), 2f, hover ? hovered : normal);
        float tw = Render2D.textWidth(FontType.SEMIBOLD, label, 7.5f);
        Render2D.text(FontType.SEMIBOLD, label, x + w / 2 - tw / 2, y + 6f, 7.5f, ColorUtil.rgba(255, 255, 255, 255));
    }

    private void renderAddPanel(int mouseX, int mouseY) {
        Render2D.rect(0, 0, width, height, 0f, ColorUtil.rgba(0, 0, 0, 160));

        float px = panelX(), py = panelY(), pw = panelW(), ph = panelH();
        Render2D.rect(px, py, pw, ph, 4f, ColorUtil.rgba(30, 32, 40, 255));
        Render2D.outline(px, py, pw, ph, 4f, 0.8f, ColorUtil.rgba(70, 75, 90, 255));

        String t = "New proxy";
        float tw = Render2D.textWidth(FontType.SEMIBOLD, t, 10f);
        Render2D.text(FontType.SEMIBOLD, t, px + pw / 2 - tw / 2, py + 10f, 10f, ColorUtil.rgba(255, 255, 255, 255));

        drawField(0, EditingField.NAME, "Name", addName, mouseX, mouseY);
        drawField(1, EditingField.IP, "IP", addIp, mouseX, mouseY);
        drawField(2, EditingField.PORT, "Port", addPort, mouseX, mouseY);
        drawField(3, EditingField.LOGIN, "Login", addLogin, mouseX, mouseY);
        drawField(4, EditingField.PASSWORD, "Password", addPassword, mouseX, mouseY);

        Render2D.text(FontType.REGULARNEW, "Type: auto (HTTPS / SOCKS4 / SOCKS5 on ping)",
                fieldX(), panelY() + 162f, 7.5f, ColorUtil.rgba(110, 110, 110, 255));

        float by = panelButtonsY();
        boolean hoverOk = mouseX >= fieldX() && mouseX <= fieldX() + 110f && mouseY >= by && mouseY <= by + 24f;
        Render2D.rect(fieldX(), by, 110f, 24f, 3f, hoverOk ? ColorUtil.rgba(80, 120, 80, 255) : ColorUtil.rgba(60, 90, 60, 255));
        Render2D.text(FontType.SEMIBOLD, "Add", fieldX() + 45f, by + 7f, 9f, ColorUtil.rgba(255, 255, 255, 255));

        boolean hoverCancel = mouseX >= fieldX() + 126f && mouseX <= fieldX() + 236f && mouseY >= by && mouseY <= by + 24f;
        Render2D.rect(fieldX() + 126f, by, 110f, 24f, 3f, hoverCancel ? ColorUtil.rgba(100, 80, 80, 255) : ColorUtil.rgba(80, 60, 60, 255));
        Render2D.text(FontType.SEMIBOLD, "Cancel", fieldX() + 158f, by + 7f, 9f, ColorUtil.rgba(255, 255, 255, 255));
    }

    private void drawField(int index, EditingField field, String label, String value, int mouseX, int mouseY) {
        float y = fieldY(index);
        boolean focused = addFocus == field;

        Render2D.text(FontType.REGULARNEW, label, fieldX(), y + 5f, 8.5f, ColorUtil.rgba(160, 160, 160, 255));

        float bx = fieldX() + 60f;
        float bw = fieldW() - 60f;
        Render2D.rect(bx, y, bw, 20f, 2f, focused ? ColorUtil.rgba(50, 50, 65, 255) : ColorUtil.rgba(40, 40, 50, 255));
        if (focused) {
            Render2D.outline(bx, y, bw, 20f, 2f, 0.8f, ColorUtil.rgba(120, 160, 255, 255));
        }

        String shown = field == EditingField.PASSWORD && !focused && !value.isEmpty() ? "*******" : value;
        Render2D.text(FontType.REGULARNEW, shown, bx + 6f, y + 5f, 8.5f, ColorUtil.rgba(255, 255, 255, 255));

        if (focused && caretOn()) {
            float cx = bx + 6f + Render2D.textWidth(FontType.REGULARNEW, shown, 8.5f);
            Render2D.rect(cx + 1f, y + 4f, 1f, 12f, 0f, ColorUtil.rgba(255, 255, 255, 255));
        }
    }

    // ================= Ввод =================

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        float mx = (float) event.x();
        float my = (float) event.y();

        if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return super.mouseClicked(event, doubled);
        }

        if (addingNew) {
            handlePanelClick(mx, my);
            return true;
        }

        if (mx >= searchX() && mx <= searchX() + searchW() && my >= searchY() && my <= searchY() + searchH()) {
            searchFocused = true;
            return true;
        }
        searchFocused = false;

        if (mx >= listX() && mx <= listX() + listW() && my >= listY() && my <= listY() + listH()) {
            int index = scrollOffset + (int) ((my - listY()) / rowH());
            if (index >= 0 && index < filteredProxies.size()) {
                ProxyData proxy = filteredProxies.get(index);
                selectedProxyIndex = index;

                float y = listY() + (index - scrollOffset) * rowH();
                float by = rowBtnY(y);

                // Ping
                if (mx >= pingBtnX() && mx <= pingBtnX() + pingBtnW() && my >= by && my <= by + rowBtnH()) {
                    proxyManager.checkPing(proxy);
                    return true;
                }
                // Connect / Disconnect
                // Connect / Disconnect
                if (mx >= connBtnX() && mx <= connBtnX() + connBtnW() && my >= by && my <= by + rowBtnH()) {
                    if (proxyManager.getActiveProxy() == proxy) {
                        proxyManager.disconnect();
                    } else {
                        proxyManager.connect(proxy);
                    }
                    return true;
                }
                // Delete
                if (mx >= delBtnX() && mx <= delBtnX() + delBtnW() && my >= by && my <= by + rowBtnH()) {
                    proxyManager.removeProxy(proxy);
                    refreshList();
                    selectedProxyIndex = -1;
                    return true;
                }
                return true;
            }
        }

        float btnY = height - 50f;
        if (mx >= 20f && mx <= 120f && my >= btnY && my <= btnY + 30f) {
            openAddPanel();
            return true;
        }
        if (mx >= width - 120f && mx <= width - 20f && my >= btnY && my <= btnY + 30f) {
            minecraft.setScreen(parent);
            return true;
        }

        return super.mouseClicked(event, doubled);
    }

    private void handlePanelClick(float mx, float my) {
        EditingField[] fields = {EditingField.NAME, EditingField.IP, EditingField.PORT, EditingField.LOGIN, EditingField.PASSWORD};
        for (int i = 0; i < fields.length; i++) {
            float y = fieldY(i);
            float bx = fieldX() + 60f;
            float bw = fieldW() - 60f;
            if (mx >= bx && mx <= bx + bw && my >= y && my <= y + 20f) {
                addFocus = fields[i];
                return;
            }
        }

        float by = panelButtonsY();
        if (mx >= fieldX() && mx <= fieldX() + 110f && my >= by && my <= by + 24f) {
            confirmAdd();
            return;
        }
        if (mx >= fieldX() + 126f && mx <= fieldX() + 236f && my >= by && my <= by + 24f) {
            addingNew = false;
            addFocus = EditingField.NONE;
        }
    }

    private void openAddPanel() {
        addingNew = true;
        addFocus = EditingField.NAME;
        addName = ""; addIp = ""; addPort = ""; addLogin = ""; addPassword = "";
    }

    private void confirmAdd() {
        int port;
        try {
            port = Integer.parseInt(addPort.trim());
        } catch (Exception e) {
            return;
        }
        if (addIp.isBlank() || port <= 0 || port > 65535) {
            return;
        }
        String name = addName.isBlank() ? ("Proxy " + (proxyManager.getProxies().size() + 1)) : addName.trim();
        ProxyData proxy = new ProxyData(name, addIp.trim(), port, addLogin, addPassword, ProxyData.ProxyType.SOCKS5);
        proxyManager.addProxy(proxy);
        proxyManager.checkPing(proxy);
        addingNew = false;
        addFocus = EditingField.NONE;
        refreshList();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (addingNew) {
            return true;
        }
        if (mouseX >= listX() && mouseX <= listX() + listW() && mouseY >= listY() && mouseY <= listY() + listH()) {
            int maxScroll = Math.max(0, filteredProxies.size() - (int) (listH() / rowH()));
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) verticalAmount));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();

        if (key == GLFW.GLFW_KEY_LEFT_CONTROL || key == GLFW.GLFW_KEY_RIGHT_CONTROL) {
            ctrlDown = true;
            return true;
        }

        if (key == GLFW.GLFW_KEY_V && ctrlDown) {
            String paste = minecraft.keyboardHandler.getClipboard();
            if (paste != null && !paste.isEmpty()) {
                paste = paste.replaceAll("[\\r\\n\\t]", "");
                if (addingNew && addFocus != EditingField.NONE) {
                    appendToAddField(paste);
                } else if (searchFocused) {
                    searchQuery += paste;
                    refreshList();
                }
                return true;
            }
            return false;
        }

        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (addingNew) {
                addingNew = false;
                addFocus = EditingField.NONE;
                return true;
            }
            if (searchFocused) {
                searchFocused = false;
                return true;
            }
            minecraft.setScreen(parent);
            return true;
        }

        if (key == GLFW.GLFW_KEY_ENTER) {
            if (addingNew) {
                confirmAdd();
                return true;
            }
            if (searchFocused) {
                searchFocused = false;
                return true;
            }
            return false;
        }

        if (key == GLFW.GLFW_KEY_BACKSPACE) {
            if (addingNew && addFocus != EditingField.NONE) {
                removeFromAddField();
                return true;
            }
            if (searchFocused && !searchQuery.isEmpty()) {
                searchQuery = searchQuery.substring(0, searchQuery.length() - 1);
                refreshList();
                return true;
            }
            return false;
        }

        if (key == GLFW.GLFW_KEY_TAB && addingNew) {
            addFocus = nextField(addFocus);
            return true;
        }

        return super.keyPressed(event);
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        int key = event.key();
        if (key == GLFW.GLFW_KEY_LEFT_CONTROL || key == GLFW.GLFW_KEY_RIGHT_CONTROL) {
            ctrlDown = false;
            return true;
        }
        return super.keyReleased(event);
    }

    @Override
    public void removed() {
        ctrlDown = false;
        super.removed();
    }

    private EditingField nextField(EditingField current) {
        return switch (current) {
            case NONE, NAME -> EditingField.IP;
            case IP -> EditingField.PORT;
            case PORT -> EditingField.LOGIN;
            case LOGIN -> EditingField.PASSWORD;
            case PASSWORD -> EditingField.NAME;
        };
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (!event.isAllowedChatCharacter()) {
            return false;
        }
        char c = (char) event.codepoint();

        if (addingNew && addFocus != EditingField.NONE) {
            appendToAddField(String.valueOf(c));
            return true;
        }

        if (searchFocused) {
            searchQuery += c;
            refreshList();
            return true;
        }

        return false;
    }

    private void appendToAddField(String text) {
        switch (addFocus) {
            case NAME -> addName += text;
            case IP -> addIp += text;
            case PORT -> {
                String digits = text.replaceAll("\\D", "");
                addPort = (addPort + digits);
                if (addPort.length() > 5) addPort = addPort.substring(0, 5);
            }
            case LOGIN -> addLogin += text;
            case PASSWORD -> addPassword += text;
        }
    }

    private void removeFromAddField() {
        switch (addFocus) {
            case NAME -> addName = cutLast(addName);
            case IP -> addIp = cutLast(addIp);
            case PORT -> addPort = cutLast(addPort);
            case LOGIN -> addLogin = cutLast(addLogin);
            case PASSWORD -> addPassword = cutLast(addPassword);
        }
    }

    private static String cutLast(String s) {
        return s.isEmpty() ? s : s.substring(0, s.length() - 1);
    }
}
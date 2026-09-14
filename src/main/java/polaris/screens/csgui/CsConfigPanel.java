package polaris.screens.csgui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import polaris.api.config.ConfigManager;
import polaris.manager.Manager;
import polaris.screens.csgui.elements.TextInputField;
import polaris.utils.render.color.ColorUtil;
import polaris.utils.render.ui.Render2D;
import polaris.utils.render.ui.font.FontType;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;


public final class CsConfigPanel {
    private static final FontType FONT = FontType.INTER_SEMI;
    private static final FontType FONT_BOLD = FontType.INTER_BOLD;
    private static final float CARD_H_BASE = 40f;
    private static final float CARD_R_BASE = 8f;
    private static final float GAP_BASE = 4f;
    private static final float ICON_BOX_BASE = 24f;

    private final TextInputField nameInput;
    
    private float s = 1f;

    private float u(float v) {
        return v * s;
    }

    private float cardH() { return u(CARD_H_BASE); }
    private float cardR() { return u(CARD_R_BASE); }
    private float gap() { return u(GAP_BASE); }
    private float iconBox() { return u(ICON_BOX_BASE); }

    private float scroll, targetScroll;
    private float viewX, viewY, viewW, viewH;
    private final List<Row> rows = new ArrayList<>();

    private float createX, createY, createW, createH;
    private float fieldX, fieldY, fieldW, fieldH;
    private String status = "";
    private long statusUntil = 0L;
    private boolean statusOk = true;
    private String selectedName = "";

    private static final class Row {
        String name;
        String date;
        float x, y, w, h;
        float loadX, saveX, delX, iconBtn;
        float iconBoxX, iconBoxY;
    }

    public CsConfigPanel(TextInputField nameInput) {
        this.nameInput = nameInput;
        nameInput.setTextSize(9f);
    }

    public void reset() {
        scroll = targetScroll = 0f;
        status = "";
    }

    public float getScroll() {
        return targetScroll;
    }

    public void setScroll(float value) {
        scroll = targetScroll = Math.max(0f, value);
    }

    public boolean mouseScrolled(double amount) {
        targetScroll = Math.max(0f, targetScroll - (float) amount * u(28f));
        return true;
    }

    private ConfigManager cm() {
        try {
            Manager m = Manager.getInstance();
            return m != null ? m.getConfigManager() : null;
        } catch (Throwable t) {
            return null;
        }
    }

    private void setStatus(String msg, boolean ok) {
        status = msg;
        statusOk = ok;
        statusUntil = System.currentTimeMillis() + 2200L;
    }

    private String formatDate(String presetName) {
        try {
            Path p = ConfigManager.userConfigDirectory()
                    .resolve(presetName + ConfigManager.CONFIG_EXTENSION);
            if (!Files.exists(p)) return "—";
            FileTime ft = Files.getLastModifiedTime(p);
            return new SimpleDateFormat("dd.MM.yyyy").format(new Date(ft.toMillis()));
        } catch (Exception e) {
            return "—";
        }
    }

    public void render(float x, float y, float w, float h, float alpha, float scale, int accent, int mx, int my) {
        this.s = Math.max(0.5f, Math.min(2.0f, scale));
        viewX = x;
        viewY = y;
        viewW = w;
        viewH = h;

        int a = Math.round(255 * alpha);
        int text = ColorUtil.rgba(210, 214, 222, a);
        int dim = ColorUtil.rgba(120, 124, 136, a);
        int outline = ColorUtil.rgba(255, 255, 255, Math.round(14 * alpha));

        ConfigManager mgr = cm();
        List<String> names = mgr != null ? mgr.listUserConfigs() : List.of();

        
        float headerH = 56f;
        float hx = x;
        float hy = y;

        
        float badgeIcon = 10f;
        CsMenuAssets.icon(CsMenuAssets.FOLDER, hx + 3f, hy + 1f, badgeIcon,
                ColorUtil.withAlpha(accent, Math.round(220 * alpha)));
        Render2D.text(FONT, "Конфиги", hx + 3f + badgeIcon + 4f, hy + 2f, 9f, text);

        
        Render2D.text(FONT, "Доступные конфиги", hx + 3f, hy + 14f, 9f, dim);

        
        String createLbl = "Создать";
        float createIcon = 9f;
        float createTextW = Render2D.textWidth(FONT, createLbl, 8f);
        createW = 7f + createIcon + 4f + createTextW + 7f;
        createH = 16f;
        createX = x + w - createW;
        createY = hy + 11f;
        boolean createHov = hit(mx, my, createX, createY, createW, createH);
        drawPill(createX, createY, createW, createH, alpha, createHov, accent);
        CsMenuAssets.icon(CsMenuAssets.CLICK, createX + 6f, createY + (createH - createIcon) * 0.5f, createIcon,
                ColorUtil.rgba(230, 232, 238, a));
        Render2D.text(FONT, createLbl, createX + 6f + createIcon + 4f, createY + (createH - 8f) * 0.5f, 8f, text);

        
        fieldH = 17f;
        fieldY = hy + 30f;
        String saveLbl = "Сохранить";
        float saveIcon = 9f;
        float saveTextW = Render2D.textWidth(FONT, saveLbl, 8f);
        float saveW = 7f + saveIcon + 4f + saveTextW + 7f;
        float gap = 5f;
        fieldX = hx + 3f;
        fieldW = Math.max(36f, w - 6f - saveW - gap);
        float saveX = fieldX + fieldW + gap;
        float saveY = fieldY;
        float saveH = fieldH;

        boolean fieldFocus = nameInput.isFocused();
        Render2D.rect(fieldX, fieldY, fieldW, fieldH, 5f,
                ColorUtil.rgba(16, 17, 22, Math.round(230 * alpha)));
        Render2D.outline(fieldX, fieldY, fieldW, fieldH, 5f, 0.9f,
                fieldFocus ? ColorUtil.withAlpha(accent, Math.round(160 * alpha)) : outline);
        nameInput.setPosition(fieldX, fieldY);
        nameInput.setSize(fieldW, fieldH);
        nameInput.render(null);

        boolean saveHov = hit(mx, my, saveX, saveY, saveW, saveH);
        drawPill(saveX, saveY, saveW, saveH, alpha, saveHov, accent);
        CsMenuAssets.icon(CsMenuAssets.SAVE, saveX + 6f, saveY + (saveH - saveIcon) * 0.5f, saveIcon,
                ColorUtil.rgba(235, 236, 242, a));
        Render2D.text(FONT, saveLbl, saveX + 6f + saveIcon + 4f, saveY + (saveH - 8f) * 0.5f, 8f, text);

        if (!status.isEmpty() && System.currentTimeMillis() < statusUntil) {
            int sc = statusOk
                    ? ColorUtil.rgba(140, 210, 160, a)
                    : ColorUtil.rgba(220, 130, 130, a);
            Render2D.text(FONT, status, hx + 3f, hy + headerH - 9f, 7.5f, sc);
        }

        
        float listTop = y + headerH + 3f;
        float listH = Math.max(20f, h - (listTop - y));
        String count = names.isEmpty() ? "Нет конфигов" : (names.size() + " " + configsWord(names.size()));
        Render2D.text(FONT, count, x + 2f, listTop, 8f, dim);

        float listY = listTop + 12f;
        float listBodyH = Math.max(8f, h - (listY - y));

        rows.clear();
        if (names.isEmpty()) {
            float ey = listY + listBodyH * 0.3f;
            float box = 28f;
            Render2D.rect(x + w * 0.5f - box * 0.5f, ey, box, box, 8f,
                    ColorUtil.rgba(18, 19, 24, Math.round(230 * alpha)));
            Render2D.outline(x + w * 0.5f - box * 0.5f, ey, box, box, 8f, 0.9f, outline);
            CsMenuAssets.iconCentered(CsMenuAssets.FOLDER, x + w * 0.5f, ey + box * 0.5f, 12f,
                    ColorUtil.rgba(140, 144, 155, a));
            String empty = "Пусто";
            float ew = Render2D.textWidth(FONT_BOLD, empty, 10f);
            Render2D.text(FONT_BOLD, empty, x + (w - ew) * 0.5f, ey + box + 8f, 10f, dim);
            String tip = "Создайте конфиг выше";
            float tw = Render2D.textWidth(FONT, tip, 7.5f);
            Render2D.text(FONT, tip, x + (w - tw) * 0.5f, ey + box + 22f, 7.5f,
                    ColorUtil.rgba(100, 104, 116, a));
            return;
        }

        float content = names.size() * (cardH() + gap());
        float maxScroll = Math.max(0f, content - listBodyH);
        targetScroll = Math.max(0f, Math.min(targetScroll, maxScroll));
        if (Math.abs(targetScroll - scroll) < 0.15f) scroll = targetScroll;
        else scroll += (targetScroll - scroll) * 0.22f;

        Render2D.pushScissor(null, x, listY, w, listBodyH);

        float cy = listY - scroll;
        float iconBtn = 12f;
        float iconGap = 8f;

        for (String name : names) {
            Row row = new Row();
            row.name = name;
            row.date = formatDate(name);
            row.x = x;
            row.y = cy;
            row.w = w;
            row.h = cardH();
            row.iconBtn = iconBtn;
            row.iconBoxX = x + 6f;
            row.iconBoxY = cy + (cardH() - iconBox()) * 0.5f;
            
            float right = x + w - 8f;
            row.delX = right - iconBtn;
            row.saveX = row.delX - iconGap - iconBtn;
            row.loadX = row.saveX - iconGap - iconBtn;
            rows.add(row);

            boolean selected = name.equals(selectedName);
            boolean hov = hit(mx, my, row.x, row.y, row.w, row.h)
                    && my >= listY && my <= listY + listBodyH;

            
            if (selected || hov) {
                float alphaBoost = selected ? 0.55f : 0.28f;
                Render2D.rect(row.x, row.y, row.w, row.h, cardR(),
                        ColorUtil.rgba(16, 17, 22, Math.round(240 * alpha * alphaBoost)));
                Render2D.outline(row.x, row.y, row.w, row.h, cardR(), 1.0f,
                        selected
                                ? ColorUtil.withAlpha(accent, Math.round(120 * alpha))
                                : ColorUtil.rgba(255, 255, 255, Math.round(18 * alpha)));
            }

            
            Render2D.rect(row.iconBoxX, row.iconBoxY, iconBox(), iconBox(), 6f,
                    ColorUtil.rgba(22, 24, 30, Math.round(240 * alpha)));
            Render2D.outline(row.iconBoxX, row.iconBoxY, iconBox(), iconBox(), 6f, 0.9f,
                    selected ? ColorUtil.withAlpha(accent, Math.round(100 * alpha))
                            : ColorUtil.rgba(255, 255, 255, Math.round(12 * alpha)));
            CsMenuAssets.iconCentered(CsMenuAssets.FOLDER,
                    row.iconBoxX + iconBox() * 0.5f, row.iconBoxY + iconBox() * 0.5f, 11f,
                    selected ? ColorUtil.rgba(255, 210, 90, a) : ColorUtil.rgba(150, 154, 165, a));

            
            float textX = row.iconBoxX + iconBox() + 6f;
            float dateY = row.iconBoxY + 1f;
            CsMenuAssets.icon(CsMenuAssets.CALENDAR, textX, dateY, 8f, ColorUtil.rgba(100, 104, 116, a));
            Render2D.text(FONT_BOLD, row.date, textX + 11f, dateY, 8f, ColorUtil.rgba(100, 104, 116, a));

            float nameY = dateY + 11f;
            float nameMax = row.loadX - textX - 6f;
            String display = clip(name, nameMax, 10f);
            Render2D.text(FONT_BOLD, display, textX, nameY, 10f, text);

            
            float by = row.iconBoxY + (iconBox() - iconBtn) * 0.5f;
            drawIconBtn(CsMenuAssets.DISPLAY, row.loadX, by, iconBtn, hov && hit(mx, my, row.loadX, by, iconBtn, iconBtn), accent, a, alpha);
            drawIconBtn(CsMenuAssets.SAVE, row.saveX, by, iconBtn, hov && hit(mx, my, row.saveX, by, iconBtn, iconBtn), accent, a, alpha);
            drawIconBtn(CsMenuAssets.TRASH, row.delX, by, iconBtn, hov && hit(mx, my, row.delX, by, iconBtn, iconBtn), 0xFFC45A5A, a, alpha);

            cy += cardH() + gap();
        }

        Render2D.popScissor(null);

        if (maxScroll > 0.5f) {
            float thumbH = Math.max(24f, listBodyH * (listBodyH / (listBodyH + maxScroll)));
            float travel = Math.max(1f, listBodyH - thumbH);
            float thumbY = listY + (scroll / maxScroll) * travel;
            float tw = 3f;
            float tx = x + w + 5f;
            Render2D.rect(tx, listY, tw, listBodyH, tw * 0.5f,
                    ColorUtil.rgba(255, 255, 255, Math.round(10 * alpha)));
            Render2D.rect(tx, thumbY, tw, thumbH, tw * 0.5f,
                    ColorUtil.rgba(255, 255, 255, Math.round(42 * alpha)));
        }

        
        this.saveRectX = saveX;
        this.saveRectY = saveY;
        this.saveRectW = saveW;
        this.saveRectH = saveH;
    }

    private float saveRectX, saveRectY, saveRectW, saveRectH;

    private static void drawPill(float x, float y, float w, float h, float alpha, boolean hov, int accent) {
        float r = Math.min(5f, h * 0.4f);
        Render2D.rect(x, y, w, h, r, ColorUtil.rgba(18, 19, 24, Math.round((hov ? 250 : 220) * alpha)));
        Render2D.outline(x, y, w, h, r, 0.9f,
                hov ? ColorUtil.withAlpha(accent, Math.round(140 * alpha))
                        : ColorUtil.rgba(255, 255, 255, Math.round(14 * alpha)));
    }

    private static void drawIconBtn(String icon, float x, float y, float size, boolean hov, int accentOrRed, int a, float alpha) {
        if (hov) {
            Render2D.rect(x - 1.5f, y - 1.5f, size + 3f, size + 3f, 3.5f,
                    ColorUtil.rgba(255, 255, 255, Math.round(12 * alpha)));
        }
        CsMenuAssets.icon(icon, x, y, size,
                hov ? ColorUtil.withAlpha(accentOrRed, a) : ColorUtil.rgba(150, 154, 165, a));
    }

    private static String clip(String text, float maxW, float size) {
        if (Render2D.textWidth(FONT_BOLD, text, size) <= maxW) return text;
        String s = text;
        while (s.length() > 1 && Render2D.textWidth(FONT_BOLD, s + "…", size) > maxW) {
            s = s.substring(0, s.length() - 1);
        }
        return s + "…";
    }

    private static boolean hit(double mx, double my, float x, float y, float w, float h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return false;
        if (!hit(mx, my, viewX, viewY, viewW, viewH)) return false;

        if (hit(mx, my, fieldX, fieldY, fieldW, fieldH)) {
            nameInput.setFocused(true);
            return true;
        }
        if (hit(mx, my, createX, createY, createW, createH)) {
            createFromServer();
            return true;
        }
        if (hit(mx, my, saveRectX, saveRectY, saveRectW, saveRectH)) {
            savePreset();
            return true;
        }

        ConfigManager mgr = cm();
        if (mgr == null) return true;

        for (Row row : rows) {
            if (my < row.y || my > row.y + row.h) continue;
            float by = row.iconBoxY + (iconBox() - row.iconBtn) * 0.5f;
            if (hit(mx, my, row.loadX, by, row.iconBtn, row.iconBtn)) {
                if (mgr.loadPreset(row.name)) {
                    selectedName = row.name;
                    setStatus("Загружен · " + row.name, true);
                } else setStatus("Ошибка загрузки", false);
                return true;
            }
            if (hit(mx, my, row.saveX, by, row.iconBtn, row.iconBtn)) {
                if (mgr.savePreset(row.name)) {
                    setStatus("Сохранён · " + row.name, true);
                } else setStatus("Ошибка сохранения", false);
                return true;
            }
            if (hit(mx, my, row.delX, by, row.iconBtn, row.iconBtn)) {
                if (mgr.deletePreset(row.name)) {
                    if (row.name.equals(selectedName)) selectedName = "";
                    setStatus("Удалён · " + row.name, true);
                } else setStatus("Ошибка удаления", false);
                return true;
            }
            
            selectedName = row.name;
            return true;
        }
        return true;
    }

    public void savePreset() {
        ConfigManager mgr = cm();
        if (mgr == null) {
            setStatus("Менеджер конфигов недоступен", false);
            return;
        }
        String name = nameInput.getText().trim();
        if (name.isEmpty()) {
            setStatus("Введите имя", false);
            return;
        }
        if (mgr.savePreset(name)) {
            setStatus("Сохранён · " + name, true);
            selectedName = name;
            nameInput.setText("");
        } else {
            setStatus("Ошибка сохранения", false);
        }
    }

    
    public void createFromServer() {
        ConfigManager mgr = cm();
        if (mgr == null) {
            setStatus("Менеджер конфигов недоступен", false);
            return;
        }
        String name = resolveCreateName(mgr.listUserConfigs());
        if (name == null || name.isEmpty()) {
            setStatus("Не удалось выбрать имя", false);
            return;
        }
        if (mgr.savePreset(name)) {
            setStatus("Создан · " + name, true);
            selectedName = name;
            nameInput.setText(name);
        } else {
            setStatus("Ошибка создания", false);
        }
    }

    private static String resolveCreateName(List<String> existing) {
        String fromServer = serverHostName();
        if (fromServer != null && !fromServer.isEmpty()) {
            return fromServer;
        }
        return nextNumericName(existing);
    }

    
    private static String serverHostName() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.getConnection() == null) return null;
            ServerData data = mc.getConnection().getServerData();
            if (data == null) return null; 
            String ip = data.ip;
            if (ip == null || ip.isBlank()) return null;

            String host = ip.trim().toLowerCase(Locale.ROOT);
            if (host.startsWith("minecraft://")) host = host.substring("minecraft://".length());
            int colon = host.indexOf(':');
            if (colon > 0) host = host.substring(0, colon);
            if (host.endsWith("/")) host = host.substring(0, host.length() - 1);
            if (host.startsWith("www.")) host = host.substring(4);

            
            if (host.matches("\\d{1,3}(\\.\\d{1,3}){3}") || host.contains(":")) {
                return null;
            }

            String[] parts = host.split("\\.");
            if (parts.length == 0) return null;
            
            String label = parts.length >= 2 ? parts[parts.length - 2] : parts[0];
            label = label.replaceAll("[^a-zA-Z0-9_-]", "");
            if (label.isEmpty() || label.matches("\\d+")) return null;
            return label;
        } catch (Throwable t) {
            return null;
        }
    }

    private static String nextNumericName(List<String> existing) {
        Set<String> set = new HashSet<>();
        if (existing != null) {
            for (String n : existing) {
                if (n != null) set.add(n);
            }
        }
        int n = 1;
        while (set.contains(String.valueOf(n))) {
            n++;
            if (n > 9999) return String.valueOf(System.currentTimeMillis() % 100000);
        }
        return String.valueOf(n);
    }

    
    private static String configsWord(int n) {
        int mod10 = n % 10;
        int mod100 = n % 100;
        if (mod10 == 1 && mod100 != 11) return "конфиг";
        if (mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14)) return "конфига";
        return "конфигов";
    }
}

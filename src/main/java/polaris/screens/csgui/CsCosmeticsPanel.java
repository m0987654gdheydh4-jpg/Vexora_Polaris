package polaris.screens.csgui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.resources.Identifier;
import polaris.utils.cosmetics.CosmeticEntry;
import polaris.utils.cosmetics.CosmeticsRepository;
import polaris.utils.cosmetics.FiguraBridge;
import polaris.utils.render.color.ColorUtil;
import polaris.utils.render.ui.Render2D;
import polaris.utils.render.ui.font.FontType;

import java.util.List;


public final class CsCosmeticsPanel {
    private static final FontType FONT = FontType.INTER_SEMI;
    
    private static final int COLUMNS = 3;
    private static final float CARD_H = 42f;
    private static final float GAP = 4f;
    private static final float TAB_H = 17f;
    private static final float HEADER_H = 44f;
    private static final float PREVIEW_GAP = 6f;

    private final CosmeticEntry.Kind[] tabs = CosmeticEntry.Kind.values();
    private CosmeticEntry.Kind tab = CosmeticEntry.Kind.MODEL;

    private float gridW;
    private CosmeticEntry hovered;
    private CosmeticEntry selected;

    private float scroll;
    private float maxScroll;
    private float viewX;
    private float viewY;
    private float viewW;
    private float viewH;
    private float listTop;

    private final float[] tabX = new float[CosmeticEntry.Kind.values().length];
    private final float[] tabW = new float[CosmeticEntry.Kind.values().length];
    private float tabY;
    private float clearX;
    private float clearY;
    private float clearW;
    private float clearH;

    private String status = "";
    private long statusUntil;
    private boolean statusOk;

    public float getScroll() {
        return scroll;
    }

    public void setScroll(float value) {
        scroll = Math.max(0f, value);
    }

    public void reset() {
        scroll = 0f;
        hovered = null;
        selected = null;
        CosmeticsRepository.rescan();
    }

    public boolean mouseScrolled(double amount) {
        if (maxScroll <= 0f) {
            return false;
        }
        scroll = Math.max(0f, Math.min(maxScroll, scroll - (float) amount * 18f));
        return true;
    }

    public void render(GuiGraphics g, float x, float y, float w, float h, float alpha, float scale, int accent, int mx, int my) {
        viewX = x;
        viewY = y;
        viewW = w;
        viewH = h;

        int a = Math.round(255 * alpha);
        int text = ColorUtil.rgba(210, 214, 222, a);
        int dim = ColorUtil.rgba(120, 124, 136, a);

        Render2D.text(FONT, "Косметика", x + 3f, y + 2f, 9f, text);
        String subtitle = FiguraBridge.isAvailable()
                ? "Аватары из Polaris/cosmetics"
                : "Figura не установлена — аватары не применятся";
        Render2D.text(FONT, subtitle, x + 3f, y + 14f, 9f,
                FiguraBridge.isAvailable() ? dim : ColorUtil.rgba(220, 150, 120, a));

        
        tabY = y + 26f;
        float tx = x + 3f;
        for (int i = 0; i < tabs.length; i++) {
            String label = tabs[i].tabName();
            float labelW = Render2D.textWidth(FONT, label, 8f);
            tabW[i] = labelW + 16f;
            tabX[i] = tx;
            boolean active = tabs[i] == tab;
            boolean hover = hit(mx, my, tx, tabY, tabW[i], TAB_H);
            int fill = active
                    ? ColorUtil.withAlpha(accent, Math.round(60 * alpha))
                    : ColorUtil.rgba(255, 255, 255, Math.round((hover ? 16 : 8) * alpha));
            Render2D.rect(tx, tabY, tabW[i], TAB_H, 5f, fill);
            if (active) {
                Render2D.outline(tx, tabY, tabW[i], TAB_H, 5f, 0.9f,
                        ColorUtil.withAlpha(accent, Math.round(150 * alpha)));
            }
            Render2D.text(FONT, label, tx + 8f, tabY + (TAB_H - 8f) * 0.5f, 8f,
                    active ? text : dim);
            tx += tabW[i] + 4f;
        }

        String clearLabel = "Снять";
        clearW = Render2D.textWidth(FONT, clearLabel, 8f) + 16f;
        clearH = TAB_H;
        clearX = x + w - clearW;
        clearY = tabY;
        boolean clearHover = hit(mx, my, clearX, clearY, clearW, clearH);
        Render2D.rect(clearX, clearY, clearW, clearH, 5f,
                ColorUtil.rgba(255, 255, 255, Math.round((clearHover ? 18 : 9) * alpha)));
        Render2D.text(FONT, clearLabel, clearX + 8f, clearY + (clearH - 8f) * 0.5f, 8f, text);

        if (!status.isEmpty() && System.currentTimeMillis() < statusUntil) {
            int color = statusOk
                    ? ColorUtil.rgba(140, 210, 160, a)
                    : ColorUtil.rgba(220, 130, 130, a);
            Render2D.text(FONT, status, x + 3f, y + HEADER_H - 6f, 7.5f, color);
        }

        
        listTop = y + HEADER_H + 4f;
        float listH = Math.max(20f, h - (listTop - y));
        gridW = (w - PREVIEW_GAP) * 0.5f;
        float previewX = x + gridW + PREVIEW_GAP;
        float previewW = w - gridW - PREVIEW_GAP;
        List<CosmeticEntry> list = CosmeticsRepository.of(tab);
        hovered = null;

        if (list.isEmpty()) {
            Render2D.text(FONT, emptyText(), x + 3f, listTop + 6f, 8.5f, dim);
            maxScroll = 0f;
            renderPreview(g, previewX, listTop, previewW, listH, alpha, accent, mx, my);
            return;
        }

        float cardW = (gridW - GAP * (COLUMNS - 1)) / COLUMNS;
        int rows = (list.size() + COLUMNS - 1) / COLUMNS;
        float contentH = rows * CARD_H + Math.max(0, rows - 1) * GAP;
        maxScroll = Math.max(0f, contentH - listH);
        scroll = Math.min(scroll, maxScroll);

        Render2D.pushScissor(null, x, listTop, gridW, listH);
        for (int i = 0; i < list.size(); i++) {
            float cx = x + (i % COLUMNS) * (cardW + GAP);
            float cy = listTop + (i / COLUMNS) * (CARD_H + GAP) - scroll;
            if (cy + CARD_H < listTop || cy > listTop + listH) {
                continue;
            }
            drawCard(list.get(i), cx, cy, cardW, alpha, accent, mx, my);
        }
        Render2D.popScissor(null);

        
        renderPreview(g, previewX, listTop, previewW, listH, alpha, accent, mx, my);
    }

    
    private void renderPreview(GuiGraphics g, float x, float y, float w, float h, float alpha, int accent, int mx, int my) {
        int a = Math.round(255 * alpha);
        Render2D.rect(x, y, w, h, 8f, ColorUtil.rgba(14, 15, 20, Math.round(190 * alpha)));
        Render2D.outline(x, y, w, h, 8f, 0.9f, ColorUtil.rgba(255, 255, 255, Math.round(12 * alpha)));

        CosmeticEntry applied = CosmeticsRepository.byId(FiguraBridge.appliedId());
        CosmeticEntry shown = hovered != null ? hovered : (selected != null ? selected : applied);

        float pad = 8f;
        float captionH = 26f;
        float box = Math.min(w - pad * 2f, h - pad * 2f - captionH);
        float boxX = x + (w - box) * 0.5f;
        float boxY = y + pad;

        if (!renderPlayerModel(g, boxX, boxY, box, mx, my)) {
            
            drawThumb(shown, boxX, boxY, box, alpha, a);
        } else if (shown != null && shown != applied) {
            float inset = box * 0.30f;
            float insetX = boxX + box - inset - 4f;
            float insetY = boxY + 4f;
            drawThumb(shown, insetX, insetY, inset, alpha, a);
            Render2D.outline(insetX, insetY, inset, inset, 5f, 0.9f,
                    ColorUtil.rgba(255, 255, 255, Math.round(22 * alpha)));
        }

        if (shown == null) {
            String hint = applied == null ? "Аватар не надет" : "Наведи на модель";
            float hintW = Render2D.textWidth(FONT, hint, 8f);
            Render2D.text(FONT, hint, x + (w - hintW) * 0.5f, boxY + box + 7f, 8f,
                    ColorUtil.rgba(110, 114, 126, a));
            return;
        }

        String name = trim(shown.displayName(), w - pad * 2f, 8.5f);
        float nameW = Render2D.textWidth(FONT, name, 8.5f);
        Render2D.text(FONT, name, x + (w - nameW) * 0.5f, boxY + box + 7f, 8.5f,
                ColorUtil.rgba(215, 219, 228, a));

        boolean isApplied = FiguraBridge.isApplied(shown);
        String state = isApplied ? "надето" : shown.kind().tabName();
        float stateW = Render2D.textWidth(FONT, state, 7.5f);
        Render2D.text(FONT, state, x + (w - stateW) * 0.5f, boxY + box + 18f, 7.5f,
                isApplied ? ColorUtil.withAlpha(accent, a) : ColorUtil.rgba(110, 114, 126, a));
    }

    
    private boolean renderPlayerModel(GuiGraphics g, float x, float y, float box, int mx, int my) {
        Minecraft mc = Minecraft.getInstance();
        if (g == null || mc == null || mc.player == null || box < 8f) {
            return false;
        }
        int x1 = Math.round(x);
        int y1 = Math.round(y);
        int x2 = Math.round(x + box);
        int y2 = Math.round(y + box);
        
        int scale = Math.max(8, Math.round(box * 0.42f));
        try {
            InventoryScreen.renderEntityInInventoryFollowsMouse(
                    g, x1, y1, x2, y2, scale, 0.0625f, mx, my, mc.player);
            return true;
        } catch (Throwable throwable) {
            
            
            return false;
        }
    }

    private void drawThumb(CosmeticEntry entry, float x, float y, float size, float alpha, int a) {
        Identifier preview = entry == null ? null : CosmeticsRepository.preview(entry);
        if (preview != null) {
            Render2D.image(preview.toString(), x, y, size, size, 5f,
                    ColorUtil.rgba(255, 255, 255, a));
            return;
        }
        Render2D.rect(x, y, size, size, 5f, ColorUtil.rgba(255, 255, 255, Math.round(10 * alpha)));
        if (size > 40f) {
            String none = "нет превью";
            float noneW = Render2D.textWidth(FONT, none, 7.5f);
            Render2D.text(FONT, none, x + (size - noneW) * 0.5f, y + size * 0.5f - 4f, 7.5f,
                    ColorUtil.rgba(110, 114, 126, a));
        }
    }

    private void drawCard(CosmeticEntry entry, float x, float y, float w, float alpha, int accent, int mx, int my) {
        int a = Math.round(255 * alpha);
        boolean hover = hit(mx, my, x, y, w, CARD_H);
        if (hover) {
            hovered = entry;
        }
        boolean applied = FiguraBridge.isApplied(entry);
        boolean marked = applied || entry == selected;

        Render2D.rect(x, y, w, CARD_H, 6f,
                ColorUtil.rgba(18, 19, 25, Math.round((hover ? 235 : 210) * alpha)));
        Render2D.outline(x, y, w, CARD_H, 6f, 0.9f, marked
                ? ColorUtil.withAlpha(accent, Math.round((applied ? 175 : 95) * alpha))
                : ColorUtil.rgba(255, 255, 255, Math.round(14 * alpha)));

        float thumb = CARD_H - 14f;
        float thumbX = x + (w - thumb) * 0.5f;
        Identifier preview = CosmeticsRepository.preview(entry);
        if (preview != null) {
            Render2D.image(preview.toString(), thumbX, y + 3f, thumb, thumb, 4f,
                    ColorUtil.rgba(255, 255, 255, a));
        } else {
            Render2D.rect(thumbX, y + 3f, thumb, thumb, 4f,
                    ColorUtil.rgba(255, 255, 255, Math.round(10 * alpha)));
        }

        String name = trim(entry.displayName(), w - 4f, 6.5f);
        float nameW = Render2D.textWidth(FONT, name, 6.5f);
        Render2D.text(FONT, name, x + (w - nameW) * 0.5f, y + CARD_H - 9f, 6.5f,
                applied ? ColorUtil.withAlpha(accent, a) : ColorUtil.rgba(205, 209, 218, a));
    }

    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) {
            return false;
        }
        for (int i = 0; i < tabs.length; i++) {
            if (hit(mx, my, tabX[i], tabY, tabW[i], TAB_H)) {
                if (tab != tabs[i]) {
                    tab = tabs[i];
                    scroll = 0f;
                    selected = null;
                }
                return true;
            }
        }
        if (hit(mx, my, clearX, clearY, clearW, clearH)) {
            boolean ok = FiguraBridge.clear();
            setStatus(ok ? "Аватар снят" : "Figura недоступна", ok);
            return true;
        }

        List<CosmeticEntry> list = CosmeticsRepository.of(tab);
        if (list.isEmpty()) {
            return false;
        }
        
        float cardW = (gridW - GAP * (COLUMNS - 1)) / COLUMNS;
        float listH = Math.max(20f, viewH - (listTop - viewY));
        if (my < listTop || my > listTop + listH || mx > viewX + gridW) {
            return false;
        }
        for (int i = 0; i < list.size(); i++) {
            float cx = viewX + (i % COLUMNS) * (cardW + GAP);
            float cy = listTop + (i / COLUMNS) * (CARD_H + GAP) - scroll;
            if (hit(mx, my, cx, cy, cardW, CARD_H)) {
                CosmeticEntry entry = list.get(i);
                selected = entry;
                if (FiguraBridge.isApplied(entry)) {
                    boolean ok = FiguraBridge.clear();
                    setStatus(ok ? "Аватар снят" : "Figura недоступна", ok);
                } else {
                    boolean ok = FiguraBridge.apply(entry);
                    setStatus(ok ? entry.displayName() + " надет" : "Figura недоступна", ok);
                }
                return true;
            }
        }
        return false;
    }

    private String emptyText() {
        if (!CosmeticsRepository.directory().toFile().isDirectory()) {
            return "Папка Polaris/cosmetics не найдена";
        }
        return "Нет аватаров в этой вкладке";
    }

    private void setStatus(String message, boolean ok) {
        status = message;
        statusOk = ok;
        statusUntil = System.currentTimeMillis() + 2500L;
    }

    private String trim(String value, float maxWidth, float size) {
        if (Render2D.textWidth(FONT, value, size) <= maxWidth) {
            return value;
        }
        String cut = value;
        while (cut.length() > 1 && Render2D.textWidth(FONT, cut + "…", size) > maxWidth) {
            cut = cut.substring(0, cut.length() - 1);
        }
        return cut + "…";
    }

    private static boolean hit(double mx, double my, float x, float y, float w, float h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }
}

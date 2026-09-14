package polaris.screens.csgui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;
import polaris.api.module.impl.player.AutoBuy;
import polaris.manager.Manager;
import polaris.utils.modules.autobuy.AutoBuyItem;
import polaris.utils.modules.autobuy.AutoBuyManager;
import polaris.utils.modules.autobuy.catalog.AutoBuyItemCategory;
import polaris.utils.render.color.ColorUtil;
import polaris.utils.render.item.RenderItem;
import polaris.utils.render.ui.Render2D;
import polaris.utils.render.ui.font.FontType;

import java.util.ArrayList;
import java.util.List;


public final class CsAutoBuyPanel {
    private static final FontType FONT = FontType.INTER_SEMI;
    private static final FontType FONT_BOLD = FontType.INTER_BOLD;

    private static final String[] SERVERS = {"FunTime", "SpookyTime", "HolyWorld"};
    private static final String[] SERVER_SHORT = {"FT", "SP", "HW"};

    
    private static final AutoBuyItemCategory[] CATS_FT = {
            null,
            AutoBuyItemCategory.KRUSH,
            AutoBuyItemCategory.SPHERES,
            AutoBuyItemCategory.TALISMANS,
            AutoBuyItemCategory.POTIONS,
            AutoBuyItemCategory.MISC
    };
    private static final String[] CAT_LABELS_FT = {"Все", "Круш", "Сферы", "Талис", "Зелья", "Разн"};

    
    private static final AutoBuyItemCategory[] CATS_HW = {
            null,
            AutoBuyItemCategory.HOLYWORLD
    };
    private static final String[] CAT_LABELS_HW = {"Все", "HW"};

    private String lastServerMode = "";

    private static final float CELL_BASE = 30f;
    private static final float CELL_GAP_BASE = 5f;
    private static final float RULE_H_BASE = 42f;
    private static final float RULE_GAP_BASE = 5f;
    private static final float ICON_BOX_BASE = 30f;
    private static final float R_BASE = 7f;

    
    private float s = 1f;

    private float u(float v) {
        return v * s;
    }

    private float cell() { return u(CELL_BASE); }
    private float cellGap() { return u(CELL_GAP_BASE); }
    private float ruleH() { return u(RULE_H_BASE); }
    private float ruleGap() { return u(RULE_GAP_BASE); }
    private float iconBox() { return u(ICON_BOX_BASE); }
    private float r() { return u(R_BASE); }

    private float viewX, viewY, viewW, viewH;
    private float scrollCat, targetScrollCat;
    private float scrollRules, targetScrollRules;
    private float scrollParse, targetScrollParse;

    private float catX, catY, catW, catH;
    private float rulesX, rulesY, rulesW, rulesH;
    private float parseX, parseY, parseW, parseH;

    private final List<float[]> serverHits = new ArrayList<>();
    private final List<float[]> catTabHits = new ArrayList<>();

    
    private final List<AutoBuyItem> catalogItems = new ArrayList<>();
    private final List<float[]> catalogBoxes = new ArrayList<>();

    
    private final List<AutoBuyItem> ruleItems = new ArrayList<>();
    private final List<float[]> ruleBoxes = new ArrayList<>();

    private float[] parseNowHit;
    private float[] autoParseHit;
    private float[] clearHit;
    
    private final List<float[]> settingHits = new ArrayList<>();

    private int selectedCatIndex = 0;

    private AutoBuyItem editingPriceItem;
    private String priceDraft = "";
    private float lastMx, lastMy;

    
    private float parseContentH = 320f;

    
    private float[] catBar;
    private float[] rulesBar;
    private float[] parseBar;
    private static final int BAR_NONE = 0;
    private static final int BAR_CAT = 1;
    private static final int BAR_RULES = 2;
    private static final int BAR_PARSE = 3;
    private int draggingBar = BAR_NONE;
    private float barGrabOffset;

    
    private final List<float[]> stepperHits = new ArrayList<>();

    
    private static final int PRICE_MAX_DIGITS = 10;

    private List<AutoBuyItem> cachedCatalog;
    private String cachedCatalogKey = "";

    public void reset() {
        scrollCat = targetScrollCat = 0f;
        scrollRules = targetScrollRules = 0f;
        scrollParse = targetScrollParse = 0f;
        editingPriceItem = null;
        priceDraft = "";
        draggingBar = BAR_NONE;
        selectedCatIndex = 0;
    }

    
    public void onGuiClosed() {
        commitPriceEdit();
        draggingBar = BAR_NONE;
    }

    
    public float getScroll() {
        return targetScrollCat;
    }

    public void setScroll(float value) {
        targetScrollCat = Math.max(0f, value);
        scrollCat = targetScrollCat;
    }

    public float getRulesScroll() {
        return targetScrollRules;
    }

    public void setRulesScroll(float value) {
        targetScrollRules = Math.max(0f, value);
        scrollRules = targetScrollRules;
    }

    public boolean mouseScrolled(double mx, double my, double amount) {
        float step = (float) amount * u(30f);
        if (hit(mx, my, catX, catY, catW, catH)) {
            targetScrollCat = Math.max(0f, targetScrollCat - step);
            return true;
        }
        if (hit(mx, my, rulesX, rulesY, rulesW, rulesH)) {
            targetScrollRules = Math.max(0f, targetScrollRules - step);
            return true;
        }
        if (hit(mx, my, parseX, parseY, parseW, parseH)) {
            targetScrollParse = Math.max(0f, targetScrollParse - step);
            return true;
        }
        
        
        
        return true;
    }

    public boolean isSearchFocused() {
        return editingPriceItem != null;
    }

    public void render(GuiGraphics g, float x, float y, float w, float h, float alpha, float scale, int accent, int mx, int my) {
        this.s = Math.max(0.5f, Math.min(2.0f, scale));
        viewX = x;
        viewY = y;
        viewW = w;
        viewH = h;
        lastMx = mx;
        lastMy = my;
        AutoBuyManager.get().ensureLoaded();

        int a = Math.round(255 * alpha);
        int text = CsStyle.text(alpha);
        int dim = CsStyle.faint(alpha);
        int dim2 = CsStyle.muted(alpha);
        int iconBg = ColorUtil.rgba(0, 0, 0, Math.round(110 * alpha));
        int fieldBg = ColorUtil.rgba(0, 0, 0, Math.round(140 * alpha));
        int accentSoft = CsStyle.accentAlpha(accent, 55, alpha);
        int accentMid = CsStyle.accentAlpha(accent, 120, alpha);
        int accentHard = CsStyle.accentAlpha(accent, 200, alpha);

        AutoBuy mod = module();
        String currentServer = mod != null ? mod.getServerMode().getValue() : "FunTime";
        boolean parseOn = mod != null && mod.isAutoParseEnabled();
        boolean parseRun = mod != null && mod.isParseRunning();

        
        if (!currentServer.equalsIgnoreCase(lastServerMode)) {
            boolean wasHw = lastServerMode.equalsIgnoreCase("HolyWorld");
            boolean isHw = currentServer.equalsIgnoreCase("HolyWorld");
            
            
            if (!lastServerMode.isEmpty() && wasHw != isHw) {
                selectedCatIndex = 0;
                scrollCat = targetScrollCat = 0f;
                scrollRules = targetScrollRules = 0f;
            }
            
            editingPriceItem = null;
            priceDraft = "";
            lastServerMode = currentServer;
        }

        AutoBuyItemCategory[] cats = catsFor(currentServer);
        String[] catLabels = catLabelsFor(currentServer);
        if (selectedCatIndex >= cats.length) {
            selectedCatIndex = 0;
        }

        float pad = u(8f);
        float gap = u(7f);

        
        Render2D.rect(x, y, w, h, 10f,
                ColorUtil.rgba(13, 13, 18, Math.round(226 * alpha)),
                ColorUtil.rgba(13, 13, 18, Math.round(226 * alpha)),
                ColorUtil.rgba(6, 6, 9, Math.round(234 * alpha)),
                ColorUtil.rgba(6, 6, 9, Math.round(234 * alpha)));
        CsStyle.topHighlight(x, y, w, 10f, Math.round(26 * alpha));

        float topH = u(30f);
        float topY = y + pad;
        float topX = x + pad;
        float topW = w - pad * 2f;
        CsStyle.card(topX, topY, topW, topH, 8f, alpha, true);

        float tSz = u(10.5f);
        
        Render2D.rect(topX + u(10f), topY + (topH - u(12f)) * 0.5f, u(2.5f), u(12f), u(1.2f),
                accentHard, accentHard, accentSoft, accentSoft);
        Render2D.text(FONT_BOLD, "AutoBuy", topX + u(18f), topY + (topH - tSz) * 0.5f, tSz, text);

        serverHits.clear();
        float pillX = topX + u(18f) + Render2D.textWidth(FONT_BOLD, "AutoBuy", tSz) + u(14f);
        float pillH = u(19f);
        float pillY = topY + (topH - pillH) * 0.5f;
        for (int i = 0; i < SERVERS.length; i++) {
            float pw = Render2D.textWidth(FONT_BOLD, SERVER_SHORT[i], u(9.5f)) + u(16f);
            boolean sel = SERVERS[i].equalsIgnoreCase(currentServer);
            boolean hov = hit(mx, my, pillX, pillY, pw, pillH);
            CsStyle.pill(pillX, pillY, pw, pillH, sel, hov, accent, alpha);
            int pcol = sel ? 0xFFFFFFFF : (hov ? text : dim2);
            float tw = Render2D.textWidth(FONT_BOLD, SERVER_SHORT[i], u(9.5f));
            Render2D.text(FONT_BOLD, SERVER_SHORT[i], pillX + (pw - tw) * 0.5f, pillY + (pillH - u(9.5f)) * 0.5f, u(9.5f), pcol);
            serverHits.add(new float[]{pillX, pillY, pw, pillH, i});
            pillX += pw + u(5f);
        }

        String status = parseRun ? "Парсинг…" : (parseOn ? "AutoParse ON" : "Готов");
        int stCol = parseRun ? ColorUtil.rgba(240, 200, 90, a)
                : (parseOn ? ColorUtil.rgba(100, 220, 140, a) : dim2);
        float stW = Render2D.textWidth(FONT, status, u(9f)) + u(26f);
        float stX = topX + topW - stW - u(9f);
        Render2D.rect(stX, pillY, stW, pillH, pillH * 0.32f,
                ColorUtil.rgba(255, 255, 255, Math.round(14 * alpha)),
                ColorUtil.rgba(255, 255, 255, Math.round(14 * alpha)),
                ColorUtil.rgba(255, 255, 255, Math.round(6 * alpha)),
                ColorUtil.rgba(255, 255, 255, Math.round(6 * alpha)));
        CsStyle.statusDot(stX + u(10f), pillY + pillH * 0.5f, u(2.4f), stCol, alpha);
        Render2D.text(FONT, status, stX + u(17f), pillY + (pillH - u(9f)) * 0.5f, u(9f), stCol);

        float bodyY = topY + topH + u(7f);
        float bodyH = h - (bodyY - y) - pad;
        float bodyX = x + pad;
        float bodyW = w - pad * 2f;

        float col1 = bodyW * 0.34f;
        float col3 = bodyW * 0.28f;
        float col2 = bodyW - col1 - col3 - gap * 2f;

        CsStyle.inset(bodyX, bodyY, col1, bodyH, r(), alpha);
        CsStyle.inset(bodyX + col1 + gap, bodyY, col2, bodyH, r(), alpha);
        CsStyle.inset(bodyX + col1 + gap + col2 + gap, bodyY, col3, bodyH, r(), alpha);

        float hdrH = u(22f);
        int enCount = AutoBuyManager.get().enabledCount(currentServer);
        drawColHeader("Каталог", serverShort(currentServer), bodyX, bodyY, col1, hdrH, accent, alpha);
        drawColHeader("Список", String.valueOf(enCount), bodyX + col1 + gap, bodyY, col2, hdrH, accent, alpha);
        drawColHeader("Настройки", null, bodyX + col1 + gap + col2 + gap, bodyY, col3, hdrH, accent, alpha);

        float tabY = bodyY + hdrH + u(2f);
        float tabH = u(16f);
        catTabHits.clear();
        float tabX = bodyX + u(8f);
        boolean selectedTabVisible = false;
        for (int i = 0; i < catLabels.length; i++) {
            float tw = Render2D.textWidth(FONT, catLabels[i], u(8.5f)) + u(10f);
            if (tabX + tw > bodyX + col1 - u(8f)) {
                break;
            }
            if (selectedCatIndex == i) {
                selectedTabVisible = true;
            }
            boolean sel = selectedCatIndex == i;
            boolean hov = hit(mx, my, tabX, tabY, tw, tabH);
            Render2D.rect(tabX, tabY, tw, tabH, u(4f),
                    sel ? accentSoft : (hov ? ColorUtil.rgba(255, 255, 255, Math.round(12 * alpha))
                            : ColorUtil.rgba(0, 0, 0, 0)));
            if (sel) {
                Render2D.outline(tabX, tabY, tw, tabH, u(4f), 1f, accentMid);
            }
            Render2D.text(FONT, catLabels[i], tabX + u(5f), tabY + (tabH - u(8.5f)) * 0.5f, u(8.5f), sel ? text : dim);
            catTabHits.add(new float[]{tabX, tabY, tw, tabH, i});
            tabX += tw + u(3f);
        }
        
        
        if (!selectedTabVisible && selectedCatIndex != 0) {
            selectedCatIndex = 0;
            targetScrollCat = scrollCat = 0f;
        }

        catX = bodyX;
        catY = tabY + tabH + u(4f);
        catW = col1;
        catH = bodyY + bodyH - catY - u(4f);

        rulesX = bodyX + col1 + gap;
        rulesY = bodyY + hdrH + u(4f);
        rulesW = col2;
        rulesH = bodyY + bodyH - rulesY - u(4f);

        parseX = bodyX + col1 + gap + col2 + gap;
        parseY = bodyY + hdrH + u(4f);
        parseW = col3;
        parseH = bodyY + bodyH - parseY - u(4f);

        
        catalogItems.clear();
        catalogBoxes.clear();
        List<AutoBuyItem> catalog = filteredCatalog(currentServer);
        
        
        float barReserve = u(9f);
        float gridAvail = catW - u(8f) * 2f - barReserve;
        int cols = Math.max(1, (int) ((gridAvail + cellGap()) / (cell() + cellGap())));
        float cellW = Math.max(u(16f), (gridAvail - cellGap() * (cols - 1)) / cols);
        int rows = (catalog.size() + cols - 1) / Math.max(1, cols);
        float contentCat = rows <= 0 ? 0f : rows * cellW + (rows - 1) * cellGap() + u(4f);
        float maxCat = Math.max(0f, contentCat - catH);
        targetScrollCat = Math.max(0f, Math.min(targetScrollCat, maxCat));
        scrollCat += (targetScrollCat - scrollCat) * 0.28f;

        Render2D.pushScissor(g, catX + 1f, catY, catW - 2f, catH);
        float cellStartX = catX + u(8f);
        float cellStartY = catY + u(2f) - scrollCat;
        float iconSize = Math.max(8f, cellW * 0.55f);
        for (int i = 0; i < catalog.size(); i++) {
            AutoBuyItem item = catalog.get(i);
            int col = i % cols;
            int row = i / cols;
            float cx = cellStartX + col * (cellW + cellGap());
            float cy = cellStartY + row * (cellW + cellGap());
            if (cy + cellW < catY - 2f || cy > catY + catH + 2f) {
                continue;
            }
            boolean en = item.isEnabled();
            boolean hov = hit(mx, my, cx, cy, cellW, cellW);
            if (en) {
                CsStyle.glow(cx, cy, cellW, cellW, 6f, accent, hov ? 1.2f : 0.9f, alpha);
                Render2D.rect(cx, cy, cellW, cellW, 6f,
                        CsStyle.accentAlpha(accent, hov ? 110 : 82, alpha),
                        CsStyle.accentAlpha(accent, hov ? 110 : 82, alpha),
                        CsStyle.accentAlpha(accent, hov ? 52 : 34, alpha),
                        CsStyle.accentAlpha(accent, hov ? 52 : 34, alpha));
                Render2D.outline(cx, cy, cellW, cellW, 6f, 1.1f, accentHard);
            } else {
                int top = ColorUtil.rgba(255, 255, 255, Math.round((hov ? 20 : 9) * alpha));
                int bottom = ColorUtil.rgba(255, 255, 255, Math.round((hov ? 9 : 3) * alpha));
                Render2D.rect(cx, cy, cellW, cellW, 6f, top, top, bottom, bottom);
                if (hov) {
                    Render2D.outline(cx, cy, cellW, cellW, 6f, 1f,
                            ColorUtil.rgba(255, 255, 255, Math.round(40 * alpha)));
                }
            }
            CsStyle.topHighlight(cx, cy, cellW, 6f, Math.round((en ? 40 : 22) * alpha));
            drawIcon(g, item, cx + (cellW - iconSize) * 0.5f, cy + (cellW - iconSize) * 0.5f, iconSize, alpha);
            if (en) {
                
                float tick = u(7.5f);
                Render2D.rect(cx + cellW - tick - u(1.5f), cy + u(1.5f), tick, tick, tick * 0.5f, accentHard);
                CsMenuAssets.iconCentered(CsMenuAssets.CHECKMARK,
                        cx + cellW - tick * 0.5f - u(1.5f), cy + u(1.5f) + tick * 0.5f, tick * 0.73f,
                        ColorUtil.rgba(255, 255, 255, Math.round(240 * alpha)));
            }
            
            
            if (cy >= catY - 1f && cy + cellW <= catY + catH + 1f) {
                catalogItems.add(item);
                catalogBoxes.add(new float[]{cx, cy, cellW, cellW});
            }
        }
        Render2D.popScissor(g);
        catBar = CsStyle.scrollbar(catX, catY, catW, catH, contentCat, scrollCat, accent, alpha);

        if (catalog.isEmpty()) {
            CsStyle.emptyState(catX, catY, catW, catH, "Пусто", "Смени вкладку категории", alpha);
        }

        
        ruleItems.clear();
        ruleBoxes.clear();
        List<AutoBuyItem> enabled = AutoBuyManager.get().enabledItems(currentServer);
        float contentRules = enabled.isEmpty()
                ? 0f
                : enabled.size() * ruleH() + (enabled.size() - 1) * ruleGap() + u(4f);
        float maxRules = Math.max(0f, contentRules - rulesH);
        targetScrollRules = Math.max(0f, Math.min(targetScrollRules, maxRules));
        scrollRules += (targetScrollRules - scrollRules) * 0.28f;

        Render2D.pushScissor(g, rulesX + 1f, rulesY, rulesW - 2f, rulesH);
        float ry = rulesY + u(2f) - scrollRules;
        for (AutoBuyItem item : enabled) {
            if (ry + ruleH() >= rulesY - 2f && ry <= rulesY + rulesH + 2f) {
                float rx = rulesX + u(6f);
                float rw = rulesW - u(6f) * 2f - u(9f);
                boolean hov = hit(mx, my, rx, ry, rw, ruleH());
                boolean editing = editingPriceItem == item;
                if (editing) {
                    CsStyle.glow(rx, ry, rw, ruleH(), 7f, accent, 0.8f, alpha);
                }
                CsStyle.card(rx, ry, rw, ruleH(), 7f, alpha, hov || editing);
                
                Render2D.rect(rx + u(1.5f), ry + u(6f), u(2f), ruleH() - u(12f), u(1f),
                        CsStyle.accentAlpha(accent, editing ? 255 : (hov ? 190 : 110), alpha));

                float ibx = rx + u(8f);
                float iby = ry + (ruleH() - iconBox()) * 0.5f;
                CsStyle.inset(ibx, iby, iconBox(), iconBox(), 5f, alpha);
                float ruleIcon = Math.max(8f, iconBox() * 0.55f);
                drawIcon(g, item, ibx + (iconBox() - ruleIcon) * 0.5f, iby + (iconBox() - ruleIcon) * 0.5f,
                        ruleIcon, alpha);

                
                
                float tx = ibx + iconBox() + u(7f);
                Render2D.text(FONT_BOLD, clip(item.getName(), rx + rw - tx - u(8f), u(10f)),
                        tx, ry + u(7f), u(10f), text);

                String label = "Цена";
                float fieldH = u(15f);
                float fieldY = ry + u(21f);
                Render2D.text(FONT, label, tx, fieldY + (fieldH - u(8.5f)) * 0.5f, u(8.5f), dim);
                float labelW = Render2D.textWidth(FONT, label, u(8.5f)) + u(6f);
                float fieldX = tx + labelW;
                float fieldW = Math.min(u(76f), rx + rw - fieldX - u(8f));
                CsStyle.inset(fieldX, fieldY, fieldW, fieldH, 4f, alpha);
                if (editing) {
                    Render2D.outline(fieldX, fieldY, fieldW, fieldH, 4f, 1.1f, accentHard);
                    
                    float caretX = fieldX + u(5f) + Render2D.textWidth(FONT_BOLD, priceDraft, u(9.5f)) + u(1f);
                    if (System.currentTimeMillis() % 1000L > 500L && caretX < fieldX + fieldW - u(3f)) {
                        Render2D.rect(caretX, fieldY + u(3f), u(1f), fieldH - u(6f), 0.5f, accentHard);
                    }
                }
                String shown = editing ? priceDraft : formatPrice(item.getBuyPrice());
                Render2D.text(FONT_BOLD, shown, fieldX + u(5f), fieldY + (fieldH - u(9.5f)) * 0.5f, u(9.5f),
                        editing ? text : CsStyle.accentAlpha(accent, 235, alpha));

                
                
                if (ry >= rulesY - 1f && ry + ruleH() <= rulesY + rulesH + 1f) {
                    ruleItems.add(item);
                    ruleBoxes.add(new float[]{rx, ry, rw, ruleH(), fieldX, fieldY, fieldW, fieldH});
                }
            }
            ry += ruleH() + ruleGap();
        }
        Render2D.popScissor(g);
        rulesBar = CsStyle.scrollbar(rulesX, rulesY, rulesW, rulesH, contentRules, scrollRules, accent, alpha);

        if (enabled.isEmpty()) {
            CsStyle.emptyState(rulesX, rulesY, rulesW, rulesH,
                    "Список пуст", "ЛКМ по иконке слева — добавить · ПКМ — убрать", alpha);
        }

        
        settingHits.clear();
        stepperHits.clear();
        parseNowHit = null;
        autoParseHit = null;
        clearHit = null;

        
        float contentParseH = parseContentH;
        float maxParse = Math.max(0f, contentParseH - parseH);
        targetScrollParse = Math.max(0f, Math.min(targetScrollParse, maxParse));
        scrollParse += (targetScrollParse - scrollParse) * 0.28f;

        Render2D.pushScissor(g, parseX + 1f, parseY, parseW - 2f, parseH);
        float btnH = u(18f);
        float btnGap = u(4f);
        float btnX = parseX + u(8f);
        float btnW = parseW - u(8f) * 2f - u(9f);
        float by = parseY + u(2f) - scrollParse;

        boolean hovP = hit(mx, my, btnX, by, btnW, btnH);
        String pLabel = parseRun ? "Парсинг…" : "Парс сейчас";
        if (parseRun) {
            int warm = ColorUtil.rgba(210, 168, 50, Math.round(200 * alpha));
            drawGradientButton(btnX, by, btnW, btnH, warm, ColorUtil.rgba(140, 108, 26, Math.round(200 * alpha)), alpha);
        } else {
            if (hovP) {
                CsStyle.glow(btnX, by, btnW, btnH, 6f, accent, 1f, alpha);
            }
            drawGradientButton(btnX, by, btnW, btnH,
                    CsStyle.accentAlpha(accent, hovP ? 165 : 125, alpha),
                    CsStyle.accentAlpha(accent, hovP ? 90 : 62, alpha), alpha);
            Render2D.outline(btnX, by, btnW, btnH, 6f, 0.9f, accentHard);
        }
        float plw = Render2D.textWidth(FONT_BOLD, pLabel, u(9f));
        Render2D.text(FONT_BOLD, pLabel, btnX + (btnW - plw) * 0.5f, btnTextY(by, btnH), u(9f), 0xFFFFFFFF);
        parseNowHit = clipToParse(new float[]{btnX, by, btnW, btnH});

        by += btnH + btnGap;
        boolean hovA = hit(mx, my, btnX, by, btnW, btnH);
        String aLabel = "AutoParse";
        int okTop = ColorUtil.rgba(52, 168, 100, Math.round((hovA ? 235 : 205) * alpha));
        int okBottom = ColorUtil.rgba(28, 108, 64, Math.round((hovA ? 235 : 205) * alpha));
        int offTop = ColorUtil.rgba(255, 255, 255, Math.round((hovA ? 20 : 10) * alpha));
        int offBottom = ColorUtil.rgba(255, 255, 255, Math.round((hovA ? 10 : 4) * alpha));
        drawGradientButton(btnX, by, btnW, btnH,
                parseOn ? okTop : offTop, parseOn ? okBottom : offBottom, alpha);
        Render2D.text(FONT_BOLD, aLabel, btnX + u(9f), btnTextY(by, btnH), u(9f),
                parseOn ? 0xFFFFFFFF : dim2);
        CsStyle.switchTrack(btnX + btnW - u(29f), by + (btnH - u(11f)) * 0.5f, u(20f), u(11f),
                parseOn, hovA, accent, alpha);
        autoParseHit = clipToParse(new float[]{btnX, by, btnW, btnH});

        by += btnH + btnGap;
        boolean hovC = hit(mx, my, btnX, by, btnW, btnH);
        drawGradientButton(btnX, by, btnW, btnH,
                ColorUtil.rgba(255, 255, 255, Math.round((hovC ? 20 : 9) * alpha)),
                ColorUtil.rgba(255, 255, 255, Math.round((hovC ? 9 : 3) * alpha)), alpha);
        String cLabel = "Очистить список";
        float clw = Render2D.textWidth(FONT, cLabel, u(9f));
        float cIconX = btnX + (btnW - clw - u(12f)) * 0.5f;
        CsMenuAssets.icon(CsMenuAssets.TRASH, cIconX, by + (btnH - u(9f)) * 0.5f, u(9f),
                hovC ? ColorUtil.rgba(240, 120, 120, Math.round(240 * alpha)) : dim2);
        Render2D.text(FONT, cLabel, cIconX + u(12f), btnTextY(by, btnH), u(9f), hovC ? text : dim2);
        clearHit = clipToParse(new float[]{btnX, by, btnW, btnH});

        by += btnH + u(8f);
        CsStyle.divider(btnX, by, btnW, alpha);
        by += u(6f);

        if (mod != null) {
            
            by = drawSettingRow(btnX, by, btnW, "Обновление",
                    mod.getUpdateDelaySetting().getValue().intValue() + " мс",
                    "upd", frac(mod.getUpdateDelaySetting()), mx, my, alpha, text, dim, accent);
            by = drawSettingRow(btnX, by, btnW, "Скидка парса",
                    mod.getParseDiscountSetting().getValue().intValue() + "%",
                    "disc", frac(mod.getParseDiscountSetting()), mx, my, alpha, text, dim, accent);
            by = drawSettingRow(btnX, by, btnW, "Покупка кд",
                    mod.getBuyDelaySetting().getValue().intValue() + " мс",
                    "buy", frac(mod.getBuyDelaySetting()), mx, my, alpha, text, dim, accent);
            if (currentServer.equalsIgnoreCase("FunTime")) {
                by = drawSettingRow(btnX, by, btnW, "FT анархия",
                        mod.getAnarchyMinSecSetting().getValue().intValue() + "–"
                                + mod.getAnarchyMaxSecSetting().getValue().intValue() + "с",
                        "an", frac(mod.getAnarchyMinSecSetting()), mx, my, alpha, text, dim, accent);
                by = drawSettingRow(btnX, by, btnW, "Свап /an",
                        mod.getAnarchySwapSetting().getValue() ? "ON" : "OFF",
                        "answap", -1f, mx, my, alpha, text, dim, accent);
            }
            if (currentServer.equalsIgnoreCase("SpookyTime")) {
                by = drawSettingRow(btnX, by, btnW, "SP ходьба",
                        (mod.getSpWalkSetting().getValue() ? "ON " : "OFF ")
                                + mod.getSpWalkBlocksSetting().getValue().intValue() + "б / "
                                + mod.getSpWalkIntervalSetting().getValue().intValue() + "с",
                        "walk", frac(mod.getSpWalkBlocksSetting()), mx, my, alpha, text, dim, accent);
            }
            if (currentServer.equalsIgnoreCase("HolyWorld")) {
                by = drawSettingRow(btnX, by, btnW, "HW refresh",
                        Math.min(450, mod.getUpdateDelaySetting().getValue().intValue()) + " мс",
                        "upd", frac(mod.getUpdateDelaySetting()), mx, my, alpha, text, dim, accent);
            }
            by = drawSettingRow(btnX, by, btnW, "Авто /ah",
                    mod.getAutoOpenAhSetting().getValue() ? "ON" : "OFF",
                    "ah", -1f, mx, my, alpha, text, dim, accent);
            by = drawSettingRow(btnX, by, btnW, "ReParse",
                    mod.getReparseMinutesSetting().getValue().intValue() + " мин",
                    "rep", frac(mod.getReparseMinutesSetting()), mx, my, alpha, text, dim, accent);
        }

        if (parseRun && mod != null) {
            by += u(4f);
            String prog = "Парс " + (mod.getParseIndex() + 1) + "/" + Math.max(1, mod.getParseQueueSize());
            float pw = Render2D.textWidth(FONT_BOLD, prog, u(9f));
            Render2D.text(FONT_BOLD, prog, btnX + (btnW - pw) * 0.5f, by, u(9f),
                    ColorUtil.rgba(240, 210, 100, a));
            by += u(12f);
            String cur = clip(mod.getParseCurrentName(), btnW, u(8f));
            float cw = Render2D.textWidth(FONT, cur, u(8f));
            Render2D.text(FONT, cur, btnX + (btnW - cw) * 0.5f, by, u(8f), dim2);
            by += u(10f);
        }

        if (enabled.isEmpty()) {
            by += u(10f);
            
            String tip = clip("Включи предметы слева", btnW, u(8.5f));
            float tw = Render2D.textWidth(FONT, tip, u(8.5f));
            Render2D.text(FONT, tip, btnX + (btnW - tw) * 0.5f, by, u(8.5f), dim);
            by += u(10f);
        }
        
        parseContentH = (by + scrollParse) - parseY + u(4f);
        maxParse = Math.max(0f, parseContentH - parseH);
        targetScrollParse = Math.max(0f, Math.min(targetScrollParse, maxParse));
        Render2D.popScissor(g);
        parseBar = CsStyle.scrollbar(parseX, parseY, parseW, parseH, parseContentH, scrollParse, accent, alpha);
    }

    
    private float btnTextY(float y, float h) {
        return y + (h - u(9f)) * 0.5f + u(0.5f);
    }

    
    private float[] clipToParse(float[] box) {
        float top = Math.max(box[1], parseY);
        float bottom = Math.min(box[1] + box[3], parseY + parseH);
        if (bottom - top <= 1f) {
            return null;
        }
        box[1] = top;
        box[3] = bottom - top;
        return box;
    }

    private void drawColHeader(String title, String badge, float x, float y, float w, float h,
                               int accent, float alpha) {
        float tick = u(10f);
        Render2D.rect(x + u(8f), y + (h - tick) * 0.5f, u(2f), tick, u(1f),
                CsStyle.accentAlpha(accent, 235, alpha), CsStyle.accentAlpha(accent, 235, alpha),
                CsStyle.accentAlpha(accent, 80, alpha), CsStyle.accentAlpha(accent, 80, alpha));
        float tSize = u(9.5f);
        Render2D.text(FONT_BOLD, title, x + u(15f), y + (h - tSize) * 0.5f + u(0.5f), tSize, CsStyle.text(alpha));

        if (badge != null && !badge.isEmpty()) {
            float bSize = u(8f);
            float bt = Render2D.textWidth(FONT_BOLD, badge, bSize);
            float bw = bt + u(10f);
            float bh = u(13f);
            float bx = x + w - bw - u(8f);
            float by = y + (h - bh) * 0.5f;
            Render2D.rect(bx, by, bw, bh, bh * 0.5f, CsStyle.accentAlpha(accent, 34, alpha));
            Render2D.outline(bx, by, bw, bh, bh * 0.5f, 0.7f, CsStyle.accentAlpha(accent, 95, alpha));
            Render2D.text(FONT_BOLD, badge, bx + (bw - bt) * 0.5f, by + (bh - bSize) * 0.5f + u(0.5f), bSize,
                    CsStyle.accentAlpha(accent, 250, alpha));
        }

        
        CsStyle.divider(x + u(8f), y + h - 1f, w - u(16f), alpha);
    }

    
    
    private float drawSettingRow(float x, float y, float w, String label, String value, String id,
                                 float fraction, float mx, float my, float alpha,
                                 int text, int dim, int accent) {
        float h = u(30f);
        boolean hov = hit(mx, my, x, y, w, h) && my >= parseY && my <= parseY + parseH;
        int top = ColorUtil.rgba(255, 255, 255, Math.round((hov ? 17 : 8) * alpha));
        int bottom = ColorUtil.rgba(255, 255, 255, Math.round((hov ? 8 : 3) * alpha));
        Render2D.rect(x, y, w, h, 6f, top, top, bottom, bottom);
        if (hov) {
            Render2D.outline(x, y, w, h, 6f, 0.9f, CsStyle.accentAlpha(accent, 110, alpha));
        }
        CsStyle.topHighlight(x, y, w, 6f, Math.round((hov ? 30 : 18) * alpha));

        float labelSize = u(8.5f);
        float valueSize = u(9.5f);
        
        float textY = y + u(5f);
        Render2D.text(FONT, label, x + u(8f), textY + (valueSize - labelSize) * 0.5f, labelSize, dim);

        
        float vw = Render2D.textWidth(FONT_BOLD, value, valueSize);
        Render2D.text(FONT_BOLD, value, x + w - vw - u(8f), textY, valueSize,
                hov ? CsStyle.accentAlpha(accent, 250, alpha) : text);

        if (fraction >= 0f) {
            CsStyle.valueBar(x + u(8f), y + h - u(9f), w - u(16f), u(3f), fraction, accent, alpha);
        }

        
        
        if (hov) {
            float chip = u(9f);
            float chipY = y + u(3f);
            float minusX = x + w - u(8f) - vw - chip * 2f - u(6f);
            CsStyle.chip(minusX, chipY, chip, "−", true, accent, alpha);
            CsStyle.chip(minusX + chip + u(3f), chipY, chip, "+", true, accent, alpha);
            stepperHits.add(new float[]{minusX, chipY, chip, chip, settingId(id), -1f});
            stepperHits.add(new float[]{minusX + chip + u(3f), chipY, chip, chip, settingId(id), 1f});
        }

        float[] box = clipToParse(new float[]{x, y, w, h});
        if (box != null) {
            settingHits.add(new float[]{box[0], box[1], box[2], box[3], settingId(id)});
        }
        return y + h + u(4f);
    }

    
    private static float frac(polaris.api.settings.impl.NumberSetting setting) {
        if (setting == null) {
            return -1f;
        }
        double min = setting.getMin();
        double max = setting.getMax();
        if (max <= min) {
            return -1f;
        }
        return (float) Math.max(0.0, Math.min(1.0, (setting.getValue() - min) / (max - min)));
    }

    private static void drawGradientButton(float x, float y, float w, float h, int top, int bottom, float alpha) {
        Render2D.rect(x, y, w, h, 6f, top, top, bottom, bottom);
        CsStyle.topHighlight(x, y, w, 6f, Math.round(34 * alpha));
    }

    private static float settingId(String id) {
        return switch (id) {
            case "upd" -> 1f;
            case "disc" -> 2f;
            case "buy" -> 3f;
            case "an" -> 4f;
            case "walk" -> 5f;
            case "ah" -> 6f;
            case "rep" -> 7f;
            case "answap" -> 8f;
            default -> 0f;
        };
    }

    
    private List<AutoBuyItem> filteredCatalog(String serverMode) {
        AutoBuyItemCategory[] cats = catsFor(serverMode);
        int idx = Math.max(0, Math.min(selectedCatIndex, cats.length - 1));
        String key = serverMode + '#' + idx;
        if (cachedCatalog != null && key.equals(cachedCatalogKey)) {
            return cachedCatalog;
        }
        AutoBuyItemCategory cat = cats[idx];
        cachedCatalog = cat == null
                ? AutoBuyManager.get().itemsForServer(serverMode)
                : AutoBuyManager.get().byCategoryForServer(cat, serverMode);
        cachedCatalogKey = key;
        return cachedCatalog;
    }

    private static AutoBuyItemCategory[] catsFor(String serverMode) {
        return isHolyWorld(serverMode) ? CATS_HW : CATS_FT;
    }

    private static String[] catLabelsFor(String serverMode) {
        return isHolyWorld(serverMode) ? CAT_LABELS_HW : CAT_LABELS_FT;
    }

    private static boolean isHolyWorld(String serverMode) {
        return serverMode != null && serverMode.equalsIgnoreCase("HolyWorld");
    }

    private static String serverShort(String serverMode) {
        if (serverMode == null) {
            return "FT";
        }
        if (serverMode.equalsIgnoreCase("HolyWorld")) {
            return "HW";
        }
        if (serverMode.equalsIgnoreCase("SpookyTime")) {
            return "SP";
        }
        return "FT";
    }

    private static String formatPrice(int price) {
        if (price >= 1_000_000) {
            return String.format(java.util.Locale.ROOT, "%.1fM", price / 1_000_000.0);
        }
        if (price >= 10_000) {
            return String.format(java.util.Locale.ROOT, "%.1fK", price / 1000.0);
        }
        return String.valueOf(price);
    }

    private void drawIcon(GuiGraphics g, AutoBuyItem item, float x, float y, float size, float alpha) {
        try {
            ItemStack stack = item.createIcon();
            if (g != null) {
                try {
                    g.renderItem(stack, (int) x, (int) y);
                    return;
                } catch (Throwable ignored) {
                }
            }
            RenderItem.item(stack, x, y, size, alpha);
        } catch (Throwable ignored) {
        }
    }

    public boolean mouseClicked(double mx, double my, int btn) {
        if (mx < viewX || mx > viewX + viewW || my < viewY || my > viewY + viewH) {
            return false;
        }
        AutoBuy mod = module();

        
        if (btn == 0) {
            if (grabBar(mx, my, catBar, BAR_CAT) || grabBar(mx, my, rulesBar, BAR_RULES)
                    || grabBar(mx, my, parseBar, BAR_PARSE)) {
                return true;
            }
        }

        
        
        boolean inCat = hit(mx, my, catX, catY, catW, catH);
        boolean inRules = hit(mx, my, rulesX, rulesY, rulesW, rulesH);
        boolean inParse = hit(mx, my, parseX, parseY, parseW, parseH);

        for (float[] t : serverHits) {
            if (btn == 0 && hit(mx, my, t[0], t[1], t[2], t[3]) && mod != null) {
                String next = SERVERS[(int) t[4]];
                String prev = mod.getServerMode().getValue();
                if (!next.equalsIgnoreCase(prev)) {
                    mod.getServerMode().setValue(next);
                    
                    boolean familyChanged = isHolyWorld(prev) != isHolyWorld(next);
                    if (familyChanged) {
                        selectedCatIndex = 0;
                        scrollCat = targetScrollCat = 0f;
                        scrollRules = targetScrollRules = 0f;
                    }
                    lastServerMode = next;
                }
                return true;
            }
        }
        for (float[] t : catTabHits) {
            if (btn == 0 && hit(mx, my, t[0], t[1], t[2], t[3])) {
                selectedCatIndex = (int) t[4];
                targetScrollCat = scrollCat = 0f;
                return true;
            }
        }
        if (inParse && parseNowHit != null && btn == 0
                && hit(mx, my, parseNowHit[0], parseNowHit[1], parseNowHit[2], parseNowHit[3])) {
            
            
            if (mod != null && hasWorld()) {
                mod.startParseNow();
            }
            return true;
        }
        if (inParse && autoParseHit != null && btn == 0
                && hit(mx, my, autoParseHit[0], autoParseHit[1], autoParseHit[2], autoParseHit[3])) {
            if (mod != null) {
                mod.toggleAutoParse();
            }
            return true;
        }
        if (inParse && clearHit != null && btn == 0
                && hit(mx, my, clearHit[0], clearHit[1], clearHit[2], clearHit[3])) {
            String mode = mod != null ? mod.getServerMode().getValue() : "FunTime";
            AutoBuyManager.get().disableAll(mode);
            editingPriceItem = null;
            priceDraft = "";
            return true;
        }

        
        
        if (mod != null && inParse) {
            for (float[] t : stepperHits) {
                if (btn == 0 && hit(mx, my, t[0], t[1], t[2], t[3])) {
                    applySettingStep(mod, (int) t[4], (int) t[5]);
                    return true;
                }
            }
            for (float[] t : settingHits) {
                if (!hit(mx, my, t[0], t[1], t[2], t[3])) {
                    continue;
                }
                int dir = btn == 0 ? 1 : (btn == 1 ? -1 : 0);
                if (dir == 0) {
                    return true;
                }
                applySettingStep(mod, (int) t[4], dir);
                return true;
            }
        }

        if (inCat) {
            for (int i = 0; i < catalogItems.size(); i++) {
                float[] b = catalogBoxes.get(i);
                if (!hit(mx, my, b[0], b[1], b[2], b[3])) {
                    continue;
                }
                AutoBuyItem item = catalogItems.get(i);
                if (btn == 0) {
                    if (!item.isEnabled()) {
                        AutoBuyManager.get().setEnabled(item, true);
                    }
                    return true;
                }
                if (btn == 1 && item.isEnabled()) {
                    AutoBuyManager.get().setEnabled(item, false);
                    if (editingPriceItem == item) {
                        editingPriceItem = null;
                        priceDraft = "";
                    }
                }
                return true;
            }
        }

        if (inRules) {
            for (int i = 0; i < ruleItems.size(); i++) {
                float[] b = ruleBoxes.get(i);
                AutoBuyItem item = ruleItems.get(i);
                
                
                if (hit(mx, my, b[4], b[5], b[6], b[7])) {
                    if (btn == 0) {
                        commitPriceEdit();
                        editingPriceItem = item;
                        priceDraft = String.valueOf(item.getBuyPrice());
                    }
                    return true;
                }
                if (hit(mx, my, b[0], b[1], b[2], b[3])) {
                    if (btn == 1) {
                        AutoBuyManager.get().setEnabled(item, false);
                        if (editingPriceItem == item) {
                            editingPriceItem = null;
                            priceDraft = "";
                        }
                        return true;
                    }
                    if (btn == 0) {
                        commitPriceEdit();
                        editingPriceItem = item;
                        priceDraft = String.valueOf(item.getBuyPrice());
                        return true;
                    }
                }
            }
        }

        if (btn == 0 && editingPriceItem != null) {
            commitPriceEdit();
            return true;
        }
        return false;
    }

    
    private boolean grabBar(double mx, double my, float[] bar, int id) {
        if (bar == null || !hit(mx, my, bar[0] - 3f, bar[4], bar[2] + 6f, bar[5])) {
            return false;
        }
        draggingBar = id;
        barGrabOffset = (float) my - bar[4];
        return true;
    }

    
    public boolean mouseDragged(double mx, double my) {
        if (draggingBar == BAR_NONE) {
            return false;
        }
        float[] bar = switch (draggingBar) {
            case BAR_CAT -> catBar;
            case BAR_RULES -> rulesBar;
            case BAR_PARSE -> parseBar;
            default -> null;
        };
        if (bar == null) {
            draggingBar = BAR_NONE;
            return false;
        }
        float travel = Math.max(1f, bar[3] - bar[5]);
        float progress = Math.max(0f, Math.min(1f, ((float) my - barGrabOffset - bar[1]) / travel));
        switch (draggingBar) {
            case BAR_CAT -> {
                float max = Math.max(0f, contentFor(BAR_CAT) - catH);
                targetScrollCat = scrollCat = progress * max;
            }
            case BAR_RULES -> {
                float max = Math.max(0f, contentFor(BAR_RULES) - rulesH);
                targetScrollRules = scrollRules = progress * max;
            }
            case BAR_PARSE -> {
                float max = Math.max(0f, parseContentH - parseH);
                targetScrollParse = scrollParse = progress * max;
            }
            default -> {
            }
        }
        return true;
    }

    
    private float contentFor(int id) {
        float[] bar = id == BAR_CAT ? catBar : rulesBar;
        float view = id == BAR_CAT ? catH : rulesH;
        if (bar == null || bar[5] <= 0f) {
            return view;
        }
        return view * view / bar[5];
    }

    
    private void applySettingStep(AutoBuy mod, int sid, int dir) {
        switch (sid) {
            case 1 -> mod.adjustUpdateDelay(dir * 50);
            case 2 -> mod.adjustParseDiscount(dir);
            case 3 -> {
                int v = mod.getBuyDelaySetting().getValue().intValue() + dir * 10;
                mod.getBuyDelaySetting().setValue((double) Math.max(50, Math.min(5000, v)));
            }
            case 4 -> {
                int min = mod.getAnarchyMinSecSetting().getValue().intValue() + dir * 5;
                int max = mod.getAnarchyMaxSecSetting().getValue().intValue() + dir * 5;
                mod.getAnarchyMinSecSetting().setValue((double) Math.max(30, Math.min(600, min)));
                mod.getAnarchyMaxSecSetting().setValue((double) Math.max(
                        mod.getAnarchyMinSecSetting().getValue().intValue() + 5, Math.min(900, max)));
            }
            case 5 -> {
                if (dir > 0) {
                    mod.getSpWalkSetting().setValue(!mod.getSpWalkSetting().getValue());
                } else {
                    int b = mod.getSpWalkBlocksSetting().getValue().intValue() - 1;
                    if (b < 2) {
                        b = 15;
                    }
                    mod.getSpWalkBlocksSetting().setValue((double) b);
                }
            }
            case 6 -> mod.getAutoOpenAhSetting().setValue(!mod.getAutoOpenAhSetting().getValue());
            case 7 -> {
                int r = mod.getReparseMinutesSetting().getValue().intValue() + dir * 5;
                mod.getReparseMinutesSetting().setValue((double) Math.max(0, Math.min(240, r)));
            }
            case 8 -> mod.getAnarchySwapSetting().setValue(!mod.getAnarchySwapSetting().getValue());
            default -> {
            }
        }
    }

    public void mouseReleased(double mx, double my, int btn) {
        draggingBar = BAR_NONE;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (editingPriceItem == null) {
            return false;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            
            editingPriceItem = null;
            priceDraft = "";
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            commitPriceEdit();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            if (!priceDraft.isEmpty()) {
                priceDraft = priceDraft.substring(0, priceDraft.length() - 1);
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_V && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
            appendDigits(clipboard());
            return true;
        }
        return true;
    }

    private static String clipboard() {
        try {
            net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
            return client == null || client.keyboardHandler == null ? "" : client.keyboardHandler.getClipboard();
        } catch (Throwable ignored) {
            return "";
        }
    }

    private void appendDigits(String source) {
        if (source == null) {
            return;
        }
        for (int i = 0; i < source.length() && priceDraft.length() < PRICE_MAX_DIGITS; i++) {
            char c = source.charAt(i);
            if (c >= '0' && c <= '9') {
                priceDraft += c;
            }
        }
    }

    public boolean charTyped(char codePoint, int modifiers) {
        if (editingPriceItem == null) {
            return false;
        }
        if (codePoint >= '0' && codePoint <= '9' && priceDraft.length() < PRICE_MAX_DIGITS) {
            priceDraft += codePoint;
        }
        return true;
    }

    private void commitPriceEdit() {
        if (editingPriceItem == null) {
            return;
        }
        String draft = priceDraft.trim();
        if (!draft.isEmpty()) {
            try {
                
                
                long parsed = Long.parseLong(draft);
                AutoBuyManager.get().setBuyPrice(editingPriceItem,
                        (int) Math.max(0L, Math.min(Integer.MAX_VALUE, parsed)));
            } catch (NumberFormatException ignored) {
            }
        }
        editingPriceItem = null;
        priceDraft = "";
    }

    private static String clip(String text, float maxW, float size) {
        if (text == null) {
            return "";
        }
        if (Render2D.textWidth(FONT, text, size) <= maxW) {
            return text;
        }
        String s = text;
        while (s.length() > 1 && Render2D.textWidth(FONT, s + "…", size) > maxW) {
            s = s.substring(0, s.length() - 1);
        }
        return s + "…";
    }

    private static boolean hasWorld() {
        try {
            net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
            return client != null && client.level != null && client.player != null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean hit(double mx, double my, float x, float y, float w, float h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private static AutoBuy module() {
        try {
            AutoBuy direct = AutoBuy.getInstance();
            if (direct != null) {
                return direct;
            }
            if (Manager.getModules() == null) {
                return null;
            }
            return Manager.getModules().getByType(AutoBuy.class).orElse(null);
        } catch (Throwable t) {
            return null;
        }
    }
}

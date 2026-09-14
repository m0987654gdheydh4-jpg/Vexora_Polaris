package polaris.screens.csgui;

import net.minecraft.client.gui.GuiGraphics;
import polaris.api.drag.impl.HudTheme;
import polaris.theme.ThemeManager;
import polaris.theme.ThemePreset;
import polaris.theme.ThemeState;
import polaris.theme.ThemeStyle;
import polaris.utils.render.color.ColorUtil;
import polaris.utils.render.ui.Render2D;
import polaris.utils.render.ui.font.FontType;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;


public final class CsThemeSettings {
    private static final FontType FONT = FontType.INTER_SEMI;
    private static final FontType FONT_BOLD = FontType.INTER_BOLD;

    private static final Color[] BG_SWATCHES = {
            new Color(7, 7, 9), new Color(12, 12, 16),
            new Color(15, 15, 18), new Color(18, 20, 28),
            new Color(22, 24, 32), new Color(28, 28, 34)
    };

    private static final float CARD_R_BASE = 8f;
    private static final float GAP_BASE = 7f;
    private static final float PRESET_H_BASE = 34f;
    private static final float SWATCH_BASE = 15f;

    
    private float s = 1f;

    private float u(float v) {
        return v * s;
    }

    private float cardR() {
        return u(CARD_R_BASE);
    }

    private float gap() {
        return u(GAP_BASE);
    }

    private float presetH() {
        return u(PRESET_H_BASE);
    }

    private float swatch() {
        return u(SWATCH_BASE);
    }

    private float scroll, targetScroll, contentH;
    
    private float pendingScroll = -1f;
    private float viewX, viewY, viewW, viewH;

    
    private final List<HitPreset> presetHits = new ArrayList<>();
    private final List<HitRect> actionHits = new ArrayList<>();
    private final List<HitBg> bgHits = new ArrayList<>();
    private final float[] sliderX = new float[7];
    private final float[] sliderY = new float[7];
    private final float[] sliderW = new float[7];

    private float modeBlurX, modeGlassX, modeY, modeW, modeH;
    private float palX, palY, palW, palH, hueX, hueW;
    private float shineX, shineY, shineW, shineH;
    private float glowX, glowY, glowW, glowH;

    private int draggingSlider = -1;
    private boolean draggingPalette;
    private boolean draggingHue;

    private static final class HitPreset {
        float x, y, w, h;
        String id;
        boolean custom;
    }

    private static final class HitRect {
        float x, y, w, h;
        int action; 
    }

    private static final class HitBg {
        float x, y;
        Color color;
    }

    public void reset() {
        scroll = targetScroll = 0f;
        mouseReleased();
    }

    public float getScroll() {
        return targetScroll;
    }

    public void setScroll(float value) {
        scroll = targetScroll = Math.max(0f, value);
        
        
        pendingScroll = scroll > 0f ? scroll : -1f;
    }

    public void mouseReleased() {
        draggingSlider = -1;
        draggingPalette = false;
        draggingHue = false;
    }

    public boolean mouseScrolled(double amount) {
        pendingScroll = -1f;
        targetScroll = Math.max(0f, targetScroll - (float) amount * u(28f));
        return true;
    }

    public void render(GuiGraphics g, float x, float y, float w, float h, float alpha, float scale, int mouseX, int mouseY) {
        this.s = Math.max(0.5f, Math.min(2.0f, scale));
        ThemeManager tm = ThemeManager.get();
        ThemeState state = tm.active();

        viewX = x;
        viewY = y;
        viewW = w;
        viewH = h;

        if (contentH > 0f) {
            float maxScroll = Math.max(0f, contentH - h);
            if (pendingScroll >= 0f) {
                targetScroll = scroll = Math.min(pendingScroll, maxScroll);
                pendingScroll = -1f;
            }
            targetScroll = Math.max(0f, Math.min(targetScroll, maxScroll));
        }
        if (Math.abs(targetScroll - scroll) < 0.15f) {
            scroll = targetScroll;
        } else {
            scroll += (targetScroll - scroll) * 0.22f;
        }

        
        if (draggingPalette) {
            applyPalette(mouseX, mouseY);
            state = tm.active();
        } else if (draggingHue) {
            applyHue(mouseY);
            state = tm.active();
        } else if (draggingSlider >= 0) {
            applySlider(draggingSlider, mouseX);
            state = tm.active();
        }

        for (int i = 0; i < sliderX.length; i++) {
            sliderX[i] = -9999f;
            sliderY[i] = -9999f;
            sliderW[i] = 0f;
        }
        presetHits.clear();
        actionHits.clear();
        bgHits.clear();

        Render2D.pushScissor(g, x, y, Math.max(1f, w), Math.max(1f, h));

        float cy = y - scroll;
        float cardW = w;

        
        Render2D.rect(x + u(2f), cy + u(2f), u(2.5f), u(13f), u(1.2f),
                CsStyle.accentAlpha(accent(), 235, alpha), CsStyle.accentAlpha(accent(), 235, alpha),
                CsStyle.accentAlpha(accent(), 70, alpha), CsStyle.accentAlpha(accent(), 70, alpha));
        float badgeIcon = u(13f);
        CsMenuAssets.icon(CsMenuAssets.BRUSH, x + u(10f), cy + u(2f), badgeIcon,
                ColorUtil.withAlpha(accent(), Math.round(230 * alpha)));
        Render2D.text(FONT_BOLD, "Темы", x + u(10f) + badgeIcon + u(6f), cy + u(3f), u(11f), text(alpha));
        Render2D.text(FONT, "Пресеты и live-редактор", x + u(10f), cy + u(20f), u(9.5f), muted(alpha));
        CsStyle.divider(x + u(2f), cy + u(34f), cardW - u(4f), alpha);
        cy += u(40f);

        
        List<ThemePreset> builtins = tm.builtins();
        float presetsH = u(28f) + builtins.size() * (presetH() + u(4f)) + u(8f);
        drawCard(x, cy, cardW, presetsH, alpha, false);
        label(x + u(10f), cy + u(8f), "Пресеты", alpha);
        hint(x + u(10f), cy + u(20f), "ЛКМ — применить", alpha);
        float rowY = cy + u(34f);
        for (ThemePreset p : builtins) {
            drawPresetRow(x + u(8f), rowY, cardW - u(16f), p, tm.activePresetId(), alpha);
            rowY += presetH() + u(4f);
        }
        cy += presetsH + gap();

        
        List<ThemePreset> customs = tm.customs();
        float customBody = Math.max(1, customs.size()) * (presetH() + u(4f));
        float customH = u(28f) + customBody + u(8f);
        drawCard(x, cy, cardW, customH, alpha, false);
        label(x + u(10f), cy + u(8f), "Свои темы", alpha);
        hint(x + u(10f), cy + u(20f), customs.isEmpty()
                ? "Сохрани текущую в редакторе"
                : "ЛКМ — применить · ПКМ — удалить", alpha);
        rowY = cy + u(34f);
        if (customs.isEmpty()) {
            Render2D.text(FONT, "Пока пусто", x + u(12f), rowY + u(8f), u(9f), muted(alpha));
        } else {
            for (ThemePreset p : customs) {
                drawPresetRow(x + u(8f), rowY, cardW - u(16f), p, tm.activePresetId(), alpha);
                rowY += presetH() + u(4f);
            }
        }
        cy += customH + gap();

        
        float actH = u(40f);
        drawCard(x, cy, cardW, actH, alpha, false);
        float btnW = (cardW - u(28f)) * 0.5f;
        float btnY = cy + u(10f);
        float btnH = u(20f);
        float saveX = x + u(10f);
        float resetX = saveX + btnW + u(8f);
        drawAction(saveX, btnY, btnW, btnH, "Сохранить", true, alpha);
        drawAction(resetX, btnY, btnW, btnH, "Сброс", false, alpha);
        HitRect save = new HitRect();
        save.x = saveX; save.y = btnY; save.w = btnW; save.h = btnH; save.action = 0;
        HitRect reset = new HitRect();
        reset.x = resetX; reset.y = btnY; reset.w = btnW; reset.h = btnH; reset.action = 1;
        actionHits.add(save);
        actionHits.add(reset);
        cy += actH + gap();

        
        boolean glass = state.style == ThemeStyle.GLASS;
        float editorH = estimateEditorHeight(glass);
        drawCard(x, cy, cardW, editorH, alpha, true);
        float ey = cy + u(10f);
        float innerX = x + u(10f);
        float innerW = cardW - u(20f);

        label(innerX, ey, "Редактор", alpha);
        ey += u(16f);

        
        hint(innerX, ey, "Материал", alpha);
        ey += u(12f);
        modeH = u(18f);
        modeY = ey;
        modeW = (innerW - u(8f)) * 0.5f;
        modeBlurX = innerX;
        modeGlassX = modeBlurX + modeW + u(8f);
        drawSeg(modeBlurX, modeY, modeW, modeH, "Блюр", !glass, alpha);
        drawSeg(modeGlassX, modeY, modeW, modeH, "Стекло", glass, alpha);
        ey += modeH + u(12f);

        
        label(innerX, ey, "Акцент", alpha);
        ey += u(14f);
        float hueBar = u(12f);
        float gap = u(6f);
        palW = Math.max(u(40f), innerW - hueBar - gap);
        palH = u(72f);
        palX = innerX;
        palY = ey;
        hueX = palX + palW + gap;
        hueW = hueBar;
        drawPalette(state.accent, alpha);
        ey += palH + u(12f);

        
        label(innerX, ey, "Фон", alpha);
        ey += u(14f);
        for (int i = 0; i < BG_SWATCHES.length; i++) {
            float sx = innerX + i * (swatch() + u(5f));
            if (sx + swatch() > innerX + innerW) {
                break;
            }
            boolean sel = colorsClose(BG_SWATCHES[i], state.background);
            if (sel) {
                CsStyle.glow(sx, ey, swatch(), swatch(), 4f, accent(), 1f, alpha);
            }
            Render2D.rect(sx, ey, swatch(), swatch(), 4f, withAlpha(BG_SWATCHES[i].getRGB() | 0xFF000000, alpha));
            
            Render2D.rect(sx, ey, swatch(), swatch() * 0.5f, 4f, 4f, 0f, 0f,
                    ColorUtil.rgba(255, 255, 255, Math.round(30 * alpha)),
                    ColorUtil.rgba(255, 255, 255, Math.round(30 * alpha)),
                    ColorUtil.rgba(255, 255, 255, 0),
                    ColorUtil.rgba(255, 255, 255, 0));
            if (sel) {
                Render2D.outline(sx, ey, swatch(), swatch(), 4f, 1.3f, ColorUtil.withAlpha(accent(), Math.round(245 * alpha)));
                CsMenuAssets.iconCentered(CsMenuAssets.CHECKMARK, sx + swatch() * 0.5f, ey + swatch() * 0.5f, 8f,
                        ColorUtil.rgba(255, 255, 255, Math.round(235 * alpha)));
            } else {
                Render2D.outline(sx, ey, swatch(), swatch(), 4f, 0.7f, CsStyle.hairline(alpha));
            }
            HitBg hb = new HitBg();
            hb.x = sx;
            hb.y = ey;
            hb.color = BG_SWATCHES[i];
            bgHits.add(hb);
        }
        ey += swatch() + u(12f);

        
        if (glass) {
            ey = drawSlider(0, "Сила стекла", state.glassStrength, 1f, 100f, false, innerX, ey, innerW, alpha);
            ey = drawSlider(1, "Искажение", state.glassDistortion, -0.2f, 0.2f, false, innerX, ey, innerW, alpha);
        } else {
            ey = drawSlider(0, "Сила блюра", state.blurRadius, 0f, 60f, false, innerX, ey, innerW, alpha);
        }
        ey = drawSlider(2, "Прозрачность", state.opacity * 100f, 20f, 100f, true, innerX, ey, innerW, alpha);
        ey = drawSlider(3, "Скругление", state.rounding, 0f, 16f, false, innerX, ey, innerW, alpha);

        
        shineW = (innerW - u(8f)) * 0.5f;
        shineH = u(20f);
        shineX = innerX;
        shineY = ey;
        glowX = shineX + shineW + u(8f);
        glowY = ey;
        glowW = shineW;
        glowH = shineH;
        drawToggle(shineX, shineY, shineW, shineH, "Shine", state.shine, alpha);
        drawToggle(glowX, glowY, glowW, glowH, "Glow", state.glow, alpha);
        ey += shineH + u(10f);

        if (state.shine) {
            ey = drawSlider(4, "Shine %", state.shineIntensity * 100f, 0f, 100f, true, innerX, ey, innerW, alpha);
        }
        if (state.glow) {
            ey = drawSlider(5, "Glow size", state.glowSize, 0f, 20f, false, innerX, ey, innerW, alpha);
            ey = drawSlider(6, "Glow %", state.glowIntensity * 100f, 0f, 100f, true, innerX, ey, innerW, alpha);
        }

        contentH = (ey + u(8f)) - (y - scroll) + u(12f);
        Render2D.popScissor(g);
        CsStyle.scrollbar(x, y, w, h, contentH, scroll, accent(), alpha);
    }

    private float estimateEditorHeight(boolean glass) {
        
        float h = 10 + 16 + 12 + 18 + 12 + 14 + 72 + 12 + 14 + 15 + 12;
        h += glass ? 44 : 22; 
        h += 44; 
        h += 30; 
        h += 50; 
        return u(h + 16f);
    }

    private void drawPresetRow(float x, float y, float w, ThemePreset p, String activeId, float alpha) {
        boolean active = p.id().equals(activeId);
        if (active) {
            CsStyle.glow(x, y, w, presetH(), 6f, accent(), 0.8f, alpha);
            Render2D.rect(x, y, w, presetH(), 6f,
                    CsStyle.accentAlpha(accent(), 58, alpha), CsStyle.accentAlpha(accent(), 30, alpha),
                    CsStyle.accentAlpha(accent(), 16, alpha), CsStyle.accentAlpha(accent(), 34, alpha));
            Render2D.outline(x, y, w, presetH(), 6f, 0.8f, CsStyle.accentAlpha(accent(), 170, alpha));
            
            Render2D.rect(x + u(1.5f), y + u(6f), u(2f), presetH() - u(12f), u(1f), CsStyle.accentAlpha(accent(), 255, alpha));
        } else {
            int top = ColorUtil.rgba(255, 255, 255, Math.round(11 * alpha));
            int bottom = ColorUtil.rgba(255, 255, 255, Math.round(4 * alpha));
            Render2D.rect(x, y, w, presetH(), 6f, top, top, bottom, bottom);
        }

        Color ac = p.state().accent;
        int swatchColor = withAlpha(ac.getRGB() | 0xFF000000, alpha);
        CsStyle.glow(x + u(9f), y + u(8f), u(18f), u(18f), u(5f), ac.getRGB(), 0.7f, alpha);
        Render2D.rect(x + u(9f), y + u(8f), u(18f), u(18f), u(5f), swatchColor);
        
        Render2D.rect(x + u(9f), y + u(8f), u(18f), u(9f), u(5f), u(5f), 0f, 0f,
                ColorUtil.rgba(255, 255, 255, Math.round(46 * alpha)),
                ColorUtil.rgba(255, 255, 255, Math.round(46 * alpha)),
                ColorUtil.rgba(255, 255, 255, 0),
                ColorUtil.rgba(255, 255, 255, 0));
        Render2D.outline(x + u(9f), y + u(8f), u(18f), u(18f), u(5f), 0.7f, CsStyle.hairline(alpha));

        Render2D.text(FONT_BOLD, p.name(), x + u(35f), y + u(8f), u(9.5f), text(alpha));
        String sub = p.state().style == ThemeStyle.GLASS ? "Стекло" : "Блюр";
        Render2D.text(FONT, sub, x + u(35f), y + u(19f), u(8f), muted(alpha));

        if (active) {
            CsMenuAssets.icon(CsMenuAssets.CHECKMARK, x + w - u(20f), y + (presetH() - u(10f)) * 0.5f, u(10f),
                    CsStyle.accentAlpha(accent(), 255, alpha));
        }

        HitPreset hit = new HitPreset();
        hit.x = x;
        hit.y = y;
        hit.w = w;
        hit.h = presetH();
        hit.id = p.id();
        hit.custom = !p.builtIn();
        presetHits.add(hit);
    }

    private void drawAction(float x, float y, float w, float h, String label, boolean primary, float alpha) {
        if (primary) {
            CsStyle.glow(x, y, w, h, 5f, accent(), 0.85f, alpha);
            Render2D.rect(x, y, w, h, 5f,
                    CsStyle.accentAlpha(accent(), 135, alpha), CsStyle.accentAlpha(accent(), 135, alpha),
                    CsStyle.accentAlpha(accent(), 70, alpha), CsStyle.accentAlpha(accent(), 70, alpha));
            Render2D.outline(x, y, w, h, 5f, 0.8f, CsStyle.accentAlpha(accent(), 195, alpha));
        } else {
            int top = ColorUtil.rgba(255, 255, 255, Math.round(16 * alpha));
            int bottom = ColorUtil.rgba(255, 255, 255, Math.round(7 * alpha));
            Render2D.rect(x, y, w, h, 5f, top, top, bottom, bottom);
            Render2D.outline(x, y, w, h, 5f, 0.7f, outline(alpha));
        }
        CsStyle.topHighlight(x, y, w, 5f, Math.round((primary ? 44 : 26) * alpha));

        String icon = primary ? CsMenuAssets.SAVE : CsMenuAssets.ARROW;
        float tw = Render2D.textWidth(FONT_BOLD, label, u(8.5f));
        float total = tw + u(13f);
        float ix = x + (w - total) * 0.5f;
        CsMenuAssets.icon(icon, ix, y + (h - u(9f)) * 0.5f, u(9f),
                primary ? text(alpha) : muted(alpha));
        Render2D.text(FONT_BOLD, label, ix + u(13f), y + u(5.5f), u(8.5f), text(alpha));
    }

    private void drawCard(float x, float y, float w, float h, float alpha, boolean emphasis) {
        CsStyle.card(x, y, w, h, cardR(), alpha, emphasis);
    }

    private void label(float x, float y, String s, float alpha) {
        Render2D.text(FONT_BOLD, s, x, y, u(9.5f), text(alpha));
    }

    private void hint(float x, float y, String s, float alpha) {
        Render2D.text(FONT, s, x, y, u(8f), muted(alpha));
    }

    private void drawSeg(float x, float y, float w, float h, String label, boolean on, float alpha) {
        CsStyle.pill(x, y, w, h, on, false, accent(), alpha);
        float tw = Render2D.textWidth(FONT_BOLD, label, u(8f));
        Render2D.text(FONT_BOLD, label, x + (w - tw) * 0.5f, y + (h - u(8f)) * 0.5f, u(8f),
                on ? text(alpha) : muted(alpha));
    }

    
    private void drawToggle(float x, float y, float w, float h, String label, boolean on, float alpha) {
        int top = ColorUtil.rgba(255, 255, 255, Math.round((on ? 15 : 9) * alpha));
        int bottom = ColorUtil.rgba(255, 255, 255, Math.round((on ? 7 : 4) * alpha));
        Render2D.rect(x, y, w, h, 5f, top, top, bottom, bottom);
        Render2D.outline(x, y, w, h, 5f, 0.7f, on
                ? CsStyle.accentAlpha(accent(), 110, alpha)
                : outline(alpha));

        Render2D.text(FONT_BOLD, label, x + u(8f), y + (h - u(8f)) * 0.5f, u(8f), on ? text(alpha) : muted(alpha));

        float sw = u(20f);
        float sh = u(11f);
        CsStyle.switchTrack(x + w - sw - u(7f), y + (h - sh) * 0.5f, sw, sh, on, false, accent(), alpha);
    }

    private void drawPalette(Color accent, float alpha) {
        float[] hsb = Color.RGBtoHSB(accent.getRed(), accent.getGreen(), accent.getBlue(), null);
        int a255 = Math.round(255 * alpha);

        
        
        int hueCol = withAlpha(Color.getHSBColor(hsb[0], 1f, 1f).getRGB() | 0xFF000000, alpha);
        int white = ColorUtil.rgba(255, 255, 255, a255);
        Render2D.rect(palX, palY, palW, palH, 4f, white, hueCol, hueCol, white);
        int clear = ColorUtil.rgba(0, 0, 0, 0);
        int black = ColorUtil.rgba(0, 0, 0, a255);
        Render2D.rect(palX, palY, palW, palH, 4f, clear, clear, black, black);
        Render2D.outline(palX, palY, palW, palH, 4f, 0.8f, CsStyle.hairline(alpha));

        
        float cx = palX + hsb[1] * palW;
        float cy = palY + (1f - hsb[2]) * palH;
        Render2D.outline(cx - 4.5f, cy - 4.5f, 9f, 9f, 4.5f, 1.6f,
                ColorUtil.rgba(255, 255, 255, Math.round(245 * alpha)));
        Render2D.outline(cx - 5.6f, cy - 5.6f, 11.2f, 11.2f, 5.6f, 1f,
                ColorUtil.rgba(0, 0, 0, Math.round(90 * alpha)));

        
        int segments = 6;
        float segH = palH / segments;
        for (int i = 0; i < segments; i++) {
            int from = withAlpha(Color.getHSBColor(i / (float) segments, 1f, 1f).getRGB() | 0xFF000000, alpha);
            int to = withAlpha(Color.getHSBColor((i + 1) / (float) segments, 1f, 1f).getRGB() | 0xFF000000, alpha);
            float rTop = i == 0 ? 3f : 0f;
            float rBottom = i == segments - 1 ? 3f : 0f;
            Render2D.rect(hueX, palY + i * segH, hueW, segH + 0.5f,
                    rTop, rTop, rBottom, rBottom, from, from, to, to);
        }
        Render2D.outline(hueX, palY, hueW, palH, 3f, 0.8f, CsStyle.hairline(alpha));
        float hy = palY + hsb[0] * palH;
        Render2D.rect(hueX - 1.5f, hy - 1.8f, hueW + 3f, 3.6f, 1.8f,
                ColorUtil.rgba(255, 255, 255, Math.round(245 * alpha)));
        Render2D.outline(hueX - 1.5f, hy - 1.8f, hueW + 3f, 3.6f, 1.8f, 0.7f,
                ColorUtil.rgba(0, 0, 0, Math.round(80 * alpha)));
    }

    private float drawSlider(int index, String name, float value, float min, float max, boolean percent,
                             float x, float y, float w, float alpha) {
        float frac = max <= min ? 0f : (value - min) / (max - min);
        frac = Math.max(0f, Math.min(1f, frac));
        Render2D.text(FONT, name, x, y, u(8f), muted(alpha));
        String val = percent ? Math.round(value) + "%" : (Math.abs(max - min) <= 1f
                ? String.format("%.2f", value)
                : (value == Math.rint(value) ? Integer.toString(Math.round(value)) : String.format("%.1f", value)));
        
        float vw = Render2D.textWidth(FONT_BOLD, val, u(8f));
        float chipW = vw + u(10f);
        float chipX = x + w - chipW;
        Render2D.rect(chipX, y - u(1.5f), chipW, u(12f), u(6f), CsStyle.accentAlpha(accent(), 32, alpha));
        Render2D.text(FONT_BOLD, val, chipX + u(5f), y, u(8f), CsStyle.accentAlpha(accent(), 245, alpha));

        float trackY = y + u(13f);
        float trackH = u(4f);
        sliderX[index] = x;
        sliderY[index] = trackY;
        sliderW[index] = w;

        
        Render2D.rect(x, trackY, w, trackH, trackH * 0.5f,
                ColorUtil.rgba(0, 0, 0, Math.round(110 * alpha)));
        Render2D.rect(x, trackY, w, trackH, trackH * 0.5f,
                ColorUtil.rgba(255, 255, 255, Math.round(10 * alpha)));
        float fill = Math.max(trackH, w * frac);
        if (frac > 0.001f) {
            int left = ColorUtil.withAlpha(accent(), Math.round(240 * alpha));
            int right = ColorUtil.withAlpha(accent(), Math.round(165 * alpha));
            Render2D.rect(x, trackY, fill, trackH, trackH * 0.5f, left, right, right, left);
        }
        float hx = x + w * frac;
        float kr = u(4.2f);
        CsStyle.glow(hx - kr, trackY + trackH * 0.5f - kr, kr * 2f, kr * 2f, kr, accent(), 1f, alpha);
        Render2D.rect(hx - kr, trackY + trackH * 0.5f - kr, kr * 2f, kr * 2f, kr,
                ColorUtil.rgba(248, 249, 252, Math.round(255 * alpha)));
        return trackY + trackH + u(9f);
    }

    public boolean mouseClicked(double mx, double my, int button) {
        if (!hit(mx, my, viewX, viewY, viewW, viewH)) {
            return false;
        }
        ThemeManager tm = ThemeManager.get();

        
        for (HitPreset p : presetHits) {
            if (!inView(p.y, p.h) || !hit(mx, my, p.x, p.y, p.w, p.h)) {
                continue;
            }
            if (button == 1 && p.custom) {
                tm.deleteCustom(p.id);
                return true;
            }
            if (button == 0) {
                tm.applyPreset(p.id);
                return true;
            }
        }

        
        if (button == 0) {
            for (HitRect a : actionHits) {
                if (inView(a.y, a.h) && hit(mx, my, a.x, a.y, a.w, a.h)) {
                    if (a.action == 0) {
                        tm.saveCustom(null);
                    } else {
                        String id = tm.activePresetId();
                        if (id != null && !id.isEmpty()) {
                            tm.applyPreset(id);
                        } else if (!tm.builtins().isEmpty()) {
                            tm.applyPreset(tm.builtins().get(0).id());
                        }
                    }
                    return true;
                }
            }

            
            if (inView(modeY, modeH)) {
                if (hit(mx, my, modeBlurX, modeY, modeW, modeH)) {
                    tm.setActive(tm.active().withStyle(ThemeStyle.BLUR));
                    return true;
                }
                if (hit(mx, my, modeGlassX, modeY, modeW, modeH)) {
                    tm.setActive(tm.active().withStyle(ThemeStyle.GLASS));
                    return true;
                }
            }

            
            if (inView(palY, palH) && hit(mx, my, palX, palY, palW, palH)) {
                draggingPalette = true;
                applyPalette(mx, my);
                return true;
            }
            if (inView(palY, palH) && hit(mx, my, hueX, palY, hueW, palH)) {
                draggingHue = true;
                applyHue(my);
                return true;
            }

            
            for (HitBg bg : bgHits) {
                if (inView(bg.y, swatch()) && hit(mx, my, bg.x, bg.y, swatch(), swatch())) {
                    tm.setActive(tm.active().withBackground(bg.color));
                    return true;
                }
            }

            
            for (int i = 0; i < sliderX.length; i++) {
                if (sliderW[i] <= 0) {
                    continue;
                }
                if (inView(sliderY[i] - 4f, 14f) && hit(mx, my, sliderX[i] - 2f, sliderY[i] - 4f, sliderW[i] + 4f, 14f)) {
                    draggingSlider = i;
                    applySlider(i, mx);
                    return true;
                }
            }

            
            if (inView(shineY, shineH) && hit(mx, my, shineX, shineY, shineW, shineH)) {
                tm.setActive(tm.active().withShine(!tm.active().shine));
                return true;
            }
            if (inView(glowY, glowH) && hit(mx, my, glowX, glowY, glowW, glowH)) {
                tm.setActive(tm.active().withGlow(!tm.active().glow));
                return true;
            }
        }
        return true;
    }

    private void applySlider(int index, double mx) {
        ThemeManager tm = ThemeManager.get();
        ThemeState s = tm.active();
        boolean glass = s.style == ThemeStyle.GLASS;
        float frac = Math.max(0f, Math.min(1f, (float) ((mx - sliderX[index]) / Math.max(1f, sliderW[index]))));

        ThemeState next = switch (index) {
            case 0 -> glass
                    ? s.withGlassStrength(1f + frac * 99f)
                    : s.withBlurRadius(frac * 60f);
            case 1 -> glass
                    ? s.withGlassDistortion(-0.2f + frac * 0.4f)
                    : s;
            case 2 -> s.withOpacity((20f + frac * 80f) / 100f);
            case 3 -> s.withRounding(frac * 16f);
            case 4 -> s.withShineIntensity(frac);
            case 5 -> s.withGlowSize(frac * 20f);
            case 6 -> s.withGlowIntensity(frac);
            default -> s;
        };
        tm.setActive(next);
    }

    private void applyPalette(double mx, double my) {
        ThemeManager tm = ThemeManager.get();
        ThemeState s = tm.active();
        float sat = Math.max(0f, Math.min(1f, (float) ((mx - palX) / Math.max(1f, palW))));
        float bri = 1f - Math.max(0f, Math.min(1f, (float) ((my - palY) / Math.max(1f, palH))));
        float[] hsb = Color.RGBtoHSB(s.accent.getRed(), s.accent.getGreen(), s.accent.getBlue(), null);
        Color c = Color.getHSBColor(hsb[0], sat, bri);
        tm.setActive(s.withAccent(c));
    }

    private void applyHue(double my) {
        ThemeManager tm = ThemeManager.get();
        ThemeState s = tm.active();
        float hue = Math.max(0f, Math.min(1f, (float) ((my - palY) / Math.max(1f, palH))));
        float[] hsb = Color.RGBtoHSB(s.accent.getRed(), s.accent.getGreen(), s.accent.getBlue(), null);
        Color c = Color.getHSBColor(hue, hsb[1], hsb[2]);
        tm.setActive(s.withAccent(c));
    }

    private static int accent() {
        return HudTheme.current().accentColor;
    }

    private static int text(float a) {
        return ColorUtil.rgba(214, 216, 222, Math.round(255 * a));
    }

    private static int muted(float a) {
        return ColorUtil.rgba(130, 134, 146, Math.round(255 * a));
    }

    private static int outline(float a) {
        return ColorUtil.rgba(255, 255, 255, Math.round(12 * a));
    }

    private static int withAlpha(int rgb, float a) {
        int base = (rgb >>> 24) & 0xFF;
        if (base == 0) {
            base = 255;
        }
        return (rgb & 0x00FFFFFF) | (Math.round(base * Math.max(0f, Math.min(1f, a))) << 24);
    }

    private static boolean colorsClose(Color a, Color b) {
        int dr = a.getRed() - b.getRed();
        int dg = a.getGreen() - b.getGreen();
        int db = a.getBlue() - b.getBlue();
        return dr * dr + dg * dg + db * db < 180;
    }

    private static boolean hit(double mx, double my, float x, float y, float w, float h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private boolean inView(float y, float h) {
        return y + h > viewY && y < viewY + viewH;
    }
}

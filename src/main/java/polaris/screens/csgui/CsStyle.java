package polaris.screens.csgui;

import polaris.utils.render.color.ColorUtil;
import polaris.utils.render.ui.Render2D;
import polaris.utils.render.ui.font.FontType;


public final class CsStyle {
    public static final FontType FONT = FontType.INTER_SEMI;
    public static final FontType FONT_BOLD = FontType.INTER_BOLD;

    private CsStyle() {
    }

    

    public static int text(float alpha) {
        return ColorUtil.rgba(232, 234, 241, a(255, alpha));
    }

    public static int muted(float alpha) {
        return ColorUtil.rgba(146, 150, 162, a(255, alpha));
    }

    public static int faint(float alpha) {
        return ColorUtil.rgba(104, 108, 120, a(255, alpha));
    }

    public static int hairline(float alpha) {
        return ColorUtil.rgba(255, 255, 255, a(16, alpha));
    }

    public static int accentAlpha(int accent, int alpha255, float alpha) {
        return ColorUtil.withAlpha(accent, a(alpha255, alpha));
    }

    private static int a(int value, float alpha) {
        return Math.max(0, Math.min(255, Math.round(value * alpha)));
    }

    

    
    public static void card(float x, float y, float w, float h, float radius, float alpha) {
        card(x, y, w, h, radius, alpha, false);
    }

    public static void card(float x, float y, float w, float h, float radius, float alpha, boolean emphasis) {
        int top = emphasis
                ? ColorUtil.rgba(30, 31, 40, a(226, alpha))
                : ColorUtil.rgba(23, 24, 31, a(214, alpha));
        int bottom = emphasis
                ? ColorUtil.rgba(17, 17, 23, a(230, alpha))
                : ColorUtil.rgba(13, 13, 18, a(220, alpha));
        Render2D.rect(x, y, w, h, radius, top, top, bottom, bottom);
        Render2D.outline(x, y, w, h, radius, 0.8f, ColorUtil.rgba(255, 255, 255, a(emphasis ? 20 : 14, alpha)));
        topHighlight(x, y, w, radius, a(emphasis ? 34 : 24, alpha));
    }

    
    public static void inset(float x, float y, float w, float h, float radius, float alpha) {
        int top = ColorUtil.rgba(6, 6, 9, a(180, alpha));
        int bottom = ColorUtil.rgba(10, 10, 14, a(150, alpha));
        Render2D.rect(x, y, w, h, radius, top, top, bottom, bottom);
        Render2D.outline(x, y, w, h, radius, 0.8f, ColorUtil.rgba(0, 0, 0, a(90, alpha)));
        
        Render2D.rect(x + radius * 0.6f, y + 0.8f, w - radius * 1.2f, 1f, 0f,
                ColorUtil.rgba(0, 0, 0, a(70, alpha)));
    }

    
    public static void topHighlight(float x, float y, float w, float radius, int alpha255) {
        float inset = Math.max(1f, radius * 0.7f);
        if (w - inset * 2f <= 0f) {
            return;
        }
        int mid = ColorUtil.rgba(255, 255, 255, alpha255);
        int edge = ColorUtil.rgba(255, 255, 255, 0);
        float half = (w - inset * 2f) * 0.5f;
        Render2D.rect(x + inset, y + 0.4f, half, 1f, 0f, edge, mid, mid, edge);
        Render2D.rect(x + inset + half, y + 0.4f, half, 1f, 0f, mid, edge, edge, mid);
    }

    
    public static void divider(float x, float y, float w, float alpha) {
        if (w <= 2f) {
            return;
        }
        int mid = ColorUtil.rgba(255, 255, 255, a(26, alpha));
        int edge = ColorUtil.rgba(255, 255, 255, 0);
        float half = w * 0.5f;
        Render2D.rect(x, y, half, 1f, 0f, edge, mid, mid, edge);
        Render2D.rect(x + half, y, half, 1f, 0f, mid, edge, edge, mid);
    }

    
    public static void glow(float x, float y, float w, float h, float radius, int accent, float strength, float alpha) {
        if (strength <= 0.01f) {
            return;
        }
        for (int i = 3; i >= 1; i--) {
            float spread = i * 2.2f;
            int col = ColorUtil.withAlpha(accent, a(Math.round(18f * strength / i), alpha));
            Render2D.rect(x - spread, y - spread, w + spread * 2f, h + spread * 2f, radius + spread, col);
        }
    }

    

    
    public static float sectionHeader(float x, float y, float w, String title, String badge,
                                      int accent, float alpha) {
        float tickH = 9f;
        float ty = y + 5f;
        Render2D.rect(x, ty, 2f, tickH, 1f,
                accentAlpha(accent, 235, alpha), accentAlpha(accent, 235, alpha),
                accentAlpha(accent, 90, alpha), accentAlpha(accent, 90, alpha));
        Render2D.text(FONT_BOLD, title, x + 8f, ty - 0.5f, 9.5f, text(alpha));

        if (badge != null && !badge.isEmpty()) {
            float bw = Render2D.textWidth(FONT_BOLD, badge, 8f) + 10f;
            float bx = x + w - bw;
            Render2D.rect(bx, ty - 1.5f, bw, 13f, 6.5f, accentAlpha(accent, 34, alpha));
            Render2D.outline(bx, ty - 1.5f, bw, 13f, 6.5f, 0.7f, accentAlpha(accent, 90, alpha));
            float bt = Render2D.textWidth(FONT_BOLD, badge, 8f);
            Render2D.text(FONT_BOLD, badge, bx + (bw - bt) * 0.5f, ty + 1.5f, 8f,
                    accentAlpha(accent, 255, alpha));
        }

        divider(x, y + 19f, w, alpha);
        return y + 24f;
    }

    
    public static void pill(float x, float y, float w, float h, boolean selected, boolean hovered,
                            int accent, float alpha) {
        float r = h * 0.32f;
        if (selected) {
            glow(x, y, w, h, r, accent, 0.9f, alpha);
            Render2D.rect(x, y, w, h, r,
                    accentAlpha(accent, 120, alpha), accentAlpha(accent, 120, alpha),
                    accentAlpha(accent, 62, alpha), accentAlpha(accent, 62, alpha));
            Render2D.outline(x, y, w, h, r, 0.9f, accentAlpha(accent, 190, alpha));
            
            Render2D.rect(x + w * 0.24f, y + h - 1.6f, w * 0.52f, 1.6f, 0.8f,
                    accentAlpha(accent, 255, alpha));
        } else {
            int top = ColorUtil.rgba(255, 255, 255, a(hovered ? 20 : 9, alpha));
            int bottom = ColorUtil.rgba(255, 255, 255, a(hovered ? 10 : 4, alpha));
            Render2D.rect(x, y, w, h, r, top, top, bottom, bottom);
            if (hovered) {
                Render2D.outline(x, y, w, h, r, 0.7f, ColorUtil.rgba(255, 255, 255, a(34, alpha)));
            }
        }
    }

    
    public static void statusDot(float cx, float cy, float radius, int color, float alpha) {
        Render2D.rect(cx - radius * 2.2f, cy - radius * 2.2f, radius * 4.4f, radius * 4.4f, radius * 2.2f,
                ColorUtil.withAlpha(color, a(45, alpha)));
        Render2D.rect(cx - radius, cy - radius, radius * 2f, radius * 2f, radius,
                ColorUtil.withAlpha(color, a(255, alpha)));
    }

    
    public static void switchTrack(float x, float y, float w, float h, boolean on, boolean hovered,
                                   int accent, float alpha) {
        float r = h * 0.5f;
        if (on) {
            glow(x, y, w, h, r, accent, 0.7f, alpha);
            Render2D.rect(x, y, w, h, r,
                    accentAlpha(accent, 210, alpha), accentAlpha(accent, 210, alpha),
                    accentAlpha(accent, 140, alpha), accentAlpha(accent, 140, alpha));
        } else {
            int top = ColorUtil.rgba(255, 255, 255, a(hovered ? 22 : 14, alpha));
            int bottom = ColorUtil.rgba(255, 255, 255, a(hovered ? 12 : 7, alpha));
            Render2D.rect(x, y, w, h, r, top, top, bottom, bottom);
            Render2D.outline(x, y, w, h, r, 0.7f, ColorUtil.rgba(255, 255, 255, a(24, alpha)));
        }
        float knobR = h * 0.5f - 2f;
        float knobCx = on ? x + w - knobR - 2f : x + knobR + 2f;
        Render2D.rect(knobCx - knobR, y + 2f, knobR * 2f, knobR * 2f, knobR,
                ColorUtil.rgba(248, 249, 252, a(255, alpha)));
    }

    
    
    public static final float SCROLLBAR_W = 4f;
    public static final float SCROLLBAR_GAP = 5f;

    
    public static float[] scrollbar(float viewX, float viewY, float viewW, float viewH,
                                    float contentH, float scroll, int accent, float alpha) {
        if (contentH <= viewH + 1f || viewH <= 8f) {
            return null;
        }
        float trackW = SCROLLBAR_W;
        float trackX = viewX + viewW - trackW - 2f;
        float thumbH = Math.max(18f, viewH * (viewH / contentH));
        float travel = viewH - thumbH;
        float progress = Math.max(0f, Math.min(1f, scroll / Math.max(1f, contentH - viewH)));
        float thumbY = viewY + travel * progress;

        Render2D.rect(trackX, viewY, trackW, viewH, trackW * 0.5f,
                ColorUtil.rgba(255, 255, 255, a(8, alpha)));
        Render2D.rect(trackX, thumbY, trackW, thumbH, trackW * 0.5f,
                accentAlpha(accent, 190, alpha), accentAlpha(accent, 190, alpha),
                accentAlpha(accent, 110, alpha), accentAlpha(accent, 110, alpha));
        return new float[]{trackX, viewY, trackW, viewH, thumbY, thumbH};
    }

    
    public static void emptyState(float x, float y, float w, float h, String title, String hint, float alpha) {
        float tw = Render2D.textWidth(FONT_BOLD, title, 10f);
        float cy = y + h * 0.42f;
        Render2D.text(FONT_BOLD, title, x + (w - tw) * 0.5f, cy, 10f, muted(alpha));
        if (hint != null && !hint.isEmpty()) {
            float hw = Render2D.textWidth(FONT, hint, 8.5f);
            Render2D.text(FONT, hint, x + (w - hw) * 0.5f, cy + 14f, 8.5f, faint(alpha));
        }
    }

    
    public static void valueBar(float x, float y, float w, float h, float fraction, int accent, float alpha) {
        fraction = Math.max(0f, Math.min(1f, fraction));
        Render2D.rect(x, y, w, h, h * 0.5f, ColorUtil.rgba(255, 255, 255, a(11, alpha)));
        float fill = Math.max(h, w * fraction);
        if (fraction > 0.001f) {
            Render2D.rect(x, y, fill, h, h * 0.5f,
                    accentAlpha(accent, 230, alpha), accentAlpha(accent, 160, alpha),
                    accentAlpha(accent, 160, alpha), accentAlpha(accent, 230, alpha));
        }
    }

    
    public static void chip(float x, float y, float size, String glyph, boolean hot, int accent, float alpha) {
        int top = hot ? accentAlpha(accent, 120, alpha) : ColorUtil.rgba(255, 255, 255, a(12, alpha));
        int bottom = hot ? accentAlpha(accent, 70, alpha) : ColorUtil.rgba(255, 255, 255, a(6, alpha));
        Render2D.rect(x, y, size, size, size * 0.3f, top, top, bottom, bottom);
        if (hot) {
            Render2D.outline(x, y, size, size, size * 0.3f, 0.7f, accentAlpha(accent, 175, alpha));
        }
        float gw = Render2D.textWidth(FONT_BOLD, glyph, 8f);
        Render2D.text(FONT_BOLD, glyph, x + (size - gw) * 0.5f, y + size * 0.5f - 4f, 8f,
                hot ? text(alpha) : muted(alpha));
    }
}

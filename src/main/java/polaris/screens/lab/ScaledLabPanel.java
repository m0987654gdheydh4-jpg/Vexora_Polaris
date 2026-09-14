package polaris.screens.lab;

import polaris.api.drag.impl.HudTheme;
import polaris.api.module.impl.visual.Hud;
import polaris.utils.render.color.ColorUtil;
import polaris.utils.render.ui.Render2D;


public final class ScaledLabPanel {
    public static final float REF_W = 960F;
    public static final float REF_H = 540F;

    public float s;
    public float x, y, w, h;
    public float mouseX, mouseY;

    public void begin(float designW, float designH, float mx, float my) {
        float dw = Render2D.getFixedScaledWidth();
        float dh = Render2D.getFixedScaledHeight();
        float fit = Math.min(dw / REF_W, dh / REF_H);
        s = clamp(fit, 0.70F, 1.35F);
        w = Math.min(designW * s, dw - 28F * s);
        h = Math.min(designH * s, dh - 28F * s);
        
        float aspect = designW / designH;
        if (w / h > aspect) {
            w = h * aspect;
        } else {
            h = w / aspect;
        }
        x = (dw - w) * 0.5F;
        y = (dh - h) * 0.5F;
        mouseX = mx;
        mouseY = my;
    }

    public float u(float design) {
        return design * s;
    }

    public float px(float designX) {
        return x + designX * s;
    }

    public float py(float designY) {
        return y + designY * s;
    }

    public void drawShell(float anim) {
        float a = clamp(anim, 0F, 1F);
        Render2D.rect(0, 0, Render2D.getFixedScaledWidth(), Render2D.getFixedScaledHeight(), 0,
                ColorUtil.rgba(0, 0, 0, Math.round(150 * a)));
        float r = u(12F);
        float blurR = Math.max(22F, HudTheme.BLUR_RADIUS);
        int bg = ColorUtil.rgba(3, 3, 5, Math.round(248 * a));
        if (Hud.isGlassMode()) {
            Hud.renderHudBackground(x, y, w, h, r, blurR, HudTheme.BLUR_SMOOTHNESS, bg);
        } else {
            Render2D.blur(x, y, w, h, r, blurR, 1.25F, bg);
        }
        Render2D.rect(x, y, w, h, r, ColorUtil.rgba(4, 4, 7, Math.round(200 * a)));
        Render2D.outline(x, y, w, h, r, Math.max(1F, s), ColorUtil.rgba(255, 255, 255, Math.round(24 * a)));
        
        int accent = HudTheme.current().accentWithAlpha(Math.round(90 * a));
        Render2D.rect(x + u(12F), y + u(42F), w - u(24F), Math.max(1F, s), 0F, accent);
    }

    public int accent(int alpha) {
        return HudTheme.current().accentWithAlpha(alpha);
    }

    public int text(int alpha) {
        return HudTheme.current().textWithAlpha(alpha);
    }

    public static float clamp(float v, float a, float b) {
        return !Float.isFinite(v) ? a : Math.max(a, Math.min(b, v));
    }
}

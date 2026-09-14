package polaris.api.drag.impl;

import polaris.api.module.impl.visual.Hud;
import polaris.utils.render.ui.blur.BuiltBlur;

final class HudRenderCompat {

    private HudRenderCompat() {
    }

    static void background(float x, float y, float width, float height, float radius, float blurRadius, float smoothness, int color) {
        Hud.renderHudBackground(x, y, width, height, radius, blurRadius, smoothness, color);
    }

    static void background(BuiltBlur blur) {
        Hud.renderHudBackground(blur);
    }

    static void glow(String texture, float x, float y, float width, float height, float radius, int color) {
        Hud.renderHudGlow(texture, x, y, width, height, radius, color);
    }

    static boolean glowEnabled() {
        return Hud.isGlowEnabled();
    }
}


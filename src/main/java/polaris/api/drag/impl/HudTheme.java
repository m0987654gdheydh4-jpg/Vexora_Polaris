package polaris.api.drag.impl;

import polaris.theme.ThemeManager;
import polaris.theme.ThemeState;
import polaris.utils.render.color.ColorUtil;

import java.awt.Color;


public final class HudTheme {

    
    public static float BLUR_RADIUS = 26.0f;
    public static final float BLUR_SMOOTHNESS = 1.2f;

    private static final HudTheme INSTANCE = new HudTheme();

    public int accentColor;
    public int accentColorRgb;
    public int backgroundColor;
    public int additionalColor;
    public int textColor;
    public int outlineColor;
    public float hudRounding;
    public float blurStrength;
    public float hudOpacity;
    public boolean colorFromStatuses;

    private HudTheme() {
        apply(ThemeState.defaults());
    }

    public static HudTheme current() {
        INSTANCE.sync();
        return INSTANCE;
    }

    private void sync() {
        try {
            apply(ThemeManager.get().active());
        } catch (Throwable ignored) {
            
        }
    }

    private void apply(ThemeState s) {
        if (s == null) {
            s = ThemeState.defaults();
        }
        this.accentColor = rgba(s.accent, 255);
        this.accentColorRgb = rgb(s.accent);
        this.backgroundColor = rgba(s.background, 255);
        this.additionalColor = rgba(ThemeState.additionalFromBackground(s.background), 255);
        this.textColor = rgba(s.text, 255);
        this.outlineColor = rgba(s.outline, 255);
        this.hudRounding = s.rounding;
        this.blurStrength = s.blurRadius;
        this.hudOpacity = s.opacity;
        this.colorFromStatuses = false;
        BLUR_RADIUS = s.blurRadius;
    }

    public int accentWithAlpha(int alpha) {
        return withAlpha(accentColor, alpha);
    }

    public int backgroundWithAlpha(int alpha) {
        return withAlpha(backgroundColor, alpha);
    }

    public int additionalWithAlpha(int alpha) {
        return withAlpha(additionalColor, alpha);
    }

    public int textWithAlpha(int alpha) {
        return withAlpha(textColor, alpha);
    }

    public int outlineWithAlpha(int alpha) {
        return withAlpha(outlineColor, alpha);
    }

    public Color accentColor() {
        return new Color(accentColorRgb);
    }

    public Color backgroundColor() {
        return new Color(backgroundColor, true);
    }

    public Color additionalColor() {
        return new Color(additionalColor, true);
    }

    public Color textColor() {
        return new Color(textColor, true);
    }

    public Color outlineColor() {
        return new Color(outlineColor, true);
    }

    private static int rgba(Color color, int alpha) {
        Color safe = color == null ? Color.WHITE : color;
        return ColorUtil.rgba(safe.getRed(), safe.getGreen(), safe.getBlue(), Math.max(0, Math.min(255, alpha)));
    }

    private static int rgb(Color color) {
        Color safe = color == null ? Color.WHITE : color;
        return new Color(safe.getRed(), safe.getGreen(), safe.getBlue()).getRGB();
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (Math.max(0, Math.min(255, alpha)) << 24);
    }
}

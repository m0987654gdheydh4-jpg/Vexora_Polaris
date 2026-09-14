package polaris.theme;

import java.awt.Color;
import java.util.Objects;


public final class ThemeState {
    public final Color accent;
    public final Color background;
    public final Color text;
    public final Color outline;

    public final ThemeStyle style;
    public final float blurRadius;
    public final float opacity;
    public final float rounding;

    public final float glassStrength;
    public final float glassDistortion;

    public final boolean shine;
    public final float shineIntensity;
    public final boolean glow;
    public final float glowSize;
    public final float glowIntensity;

    public ThemeState(
            Color accent,
            Color background,
            Color text,
            Color outline,
            ThemeStyle style,
            float blurRadius,
            float opacity,
            float rounding,
            float glassStrength,
            float glassDistortion,
            boolean shine,
            float shineIntensity,
            boolean glow,
            float glowSize,
            float glowIntensity
    ) {
        this.accent = safeColor(accent, new Color(120, 180, 255));
        this.background = safeColor(background, new Color(7, 7, 9));
        this.text = safeColor(text, Color.WHITE);
        this.outline = safeColor(outline, deriveOutline(this.accent));
        this.style = style == null ? ThemeStyle.BLUR : style;
        this.blurRadius = clamp(blurRadius, 0f, 60f);
        this.opacity = clamp(opacity, 0.2f, 1f);
        this.rounding = clamp(rounding, 0f, 16f);
        this.glassStrength = clamp(glassStrength, 1f, 100f);
        this.glassDistortion = clamp(glassDistortion, -0.2f, 0.2f);
        this.shine = shine;
        this.shineIntensity = clamp(shineIntensity, 0f, 1f);
        this.glow = glow;
        this.glowSize = clamp(glowSize, 0f, 20f);
        this.glowIntensity = clamp(glowIntensity, 0f, 1f);
    }

    public static ThemeState defaults() {
        Color accent = new Color(120, 180, 255);
        return new ThemeState(
                accent,
                new Color(7, 7, 9),
                Color.WHITE,
                deriveOutline(accent),
                ThemeStyle.BLUR,
                26f,
                0.94f,
                7f,
                25f,
                0.08f,
                true,
                0.32f,
                true,
                6f,
                0.5f
        );
    }

    public ThemeState withAccent(Color c) {
        Color a = safeColor(c, accent);
        return copy(a, background, text, deriveOutline(a), style, blurRadius, opacity, rounding,
                glassStrength, glassDistortion, shine, shineIntensity, glow, glowSize, glowIntensity);
    }

    public ThemeState withBackground(Color c) {
        return copy(accent, safeColor(c, background), text, outline, style, blurRadius, opacity, rounding,
                glassStrength, glassDistortion, shine, shineIntensity, glow, glowSize, glowIntensity);
    }

    public ThemeState withStyle(ThemeStyle s) {
        return copy(accent, background, text, outline, s, blurRadius, opacity, rounding,
                glassStrength, glassDistortion, shine, shineIntensity, glow, glowSize, glowIntensity);
    }

    public ThemeState withBlurRadius(float v) {
        return copy(accent, background, text, outline, style, v, opacity, rounding,
                glassStrength, glassDistortion, shine, shineIntensity, glow, glowSize, glowIntensity);
    }

    public ThemeState withOpacity(float v) {
        return copy(accent, background, text, outline, style, blurRadius, v, rounding,
                glassStrength, glassDistortion, shine, shineIntensity, glow, glowSize, glowIntensity);
    }

    public ThemeState withRounding(float v) {
        return copy(accent, background, text, outline, style, blurRadius, opacity, v,
                glassStrength, glassDistortion, shine, shineIntensity, glow, glowSize, glowIntensity);
    }

    public ThemeState withGlassStrength(float v) {
        return copy(accent, background, text, outline, style, blurRadius, opacity, rounding,
                v, glassDistortion, shine, shineIntensity, glow, glowSize, glowIntensity);
    }

    public ThemeState withGlassDistortion(float v) {
        return copy(accent, background, text, outline, style, blurRadius, opacity, rounding,
                glassStrength, v, shine, shineIntensity, glow, glowSize, glowIntensity);
    }

    public ThemeState withShine(boolean on) {
        return copy(accent, background, text, outline, style, blurRadius, opacity, rounding,
                glassStrength, glassDistortion, on, shineIntensity, glow, glowSize, glowIntensity);
    }

    public ThemeState withShineIntensity(float v) {
        return copy(accent, background, text, outline, style, blurRadius, opacity, rounding,
                glassStrength, glassDistortion, shine, v, glow, glowSize, glowIntensity);
    }

    public ThemeState withGlow(boolean on) {
        return copy(accent, background, text, outline, style, blurRadius, opacity, rounding,
                glassStrength, glassDistortion, shine, shineIntensity, on, glowSize, glowIntensity);
    }

    public ThemeState withGlowSize(float v) {
        return copy(accent, background, text, outline, style, blurRadius, opacity, rounding,
                glassStrength, glassDistortion, shine, shineIntensity, glow, v, glowIntensity);
    }

    public ThemeState withGlowIntensity(float v) {
        return copy(accent, background, text, outline, style, blurRadius, opacity, rounding,
                glassStrength, glassDistortion, shine, shineIntensity, glow, glowSize, v);
    }

    public ThemeState copy() {
        return copy(accent, background, text, outline, style, blurRadius, opacity, rounding,
                glassStrength, glassDistortion, shine, shineIntensity, glow, glowSize, glowIntensity);
    }

    private ThemeState copy(
            Color accent, Color background, Color text, Color outline, ThemeStyle style,
            float blurRadius, float opacity, float rounding,
            float glassStrength, float glassDistortion,
            boolean shine, float shineIntensity,
            boolean glow, float glowSize, float glowIntensity
    ) {
        return new ThemeState(accent, background, text, outline, style, blurRadius, opacity, rounding,
                glassStrength, glassDistortion, shine, shineIntensity, glow, glowSize, glowIntensity);
    }

    public static Color deriveOutline(Color accent) {
        Color a = safeColor(accent, new Color(120, 180, 255));
        return new Color(
                Math.min(255, 40 + a.getRed() / 6),
                Math.min(255, 46 + a.getGreen() / 6),
                Math.min(255, 58 + a.getBlue() / 6)
        );
    }

    public static Color additionalFromBackground(Color background) {
        Color b = safeColor(background, new Color(7, 7, 9));
        return new Color(
                Math.min(255, b.getRed() + 10),
                Math.min(255, b.getGreen() + 10),
                Math.min(255, b.getBlue() + 12)
        );
    }

    private static Color safeColor(Color c, Color fallback) {
        return c == null ? fallback : c;
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ThemeState that)) return false;
        return Float.compare(that.blurRadius, blurRadius) == 0
                && Float.compare(that.opacity, opacity) == 0
                && Float.compare(that.rounding, rounding) == 0
                && Float.compare(that.glassStrength, glassStrength) == 0
                && Float.compare(that.glassDistortion, glassDistortion) == 0
                && shine == that.shine
                && Float.compare(that.shineIntensity, shineIntensity) == 0
                && glow == that.glow
                && Float.compare(that.glowSize, glowSize) == 0
                && Float.compare(that.glowIntensity, glowIntensity) == 0
                && Objects.equals(rgb(accent), rgb(that.accent))
                && Objects.equals(rgb(background), rgb(that.background))
                && style == that.style;
    }

    @Override
    public int hashCode() {
        return Objects.hash(rgb(accent), rgb(background), style, blurRadius, opacity, rounding);
    }

    private static int rgb(Color c) {
        return c.getRGB() & 0xFFFFFF;
    }
}

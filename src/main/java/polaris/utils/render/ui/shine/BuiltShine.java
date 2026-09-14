package polaris.utils.render.ui.shine;

/**
 * Parameters for a single animated "shine" light streak that sweeps across a
 * rounded rectangle. Rendered fully on the GPU by {@link ShineRenderer}.
 */
public record BuiltShine(
        float x,
        float y,
        float width,
        float height,
        float radiusTopLeft,
        float radiusTopRight,
        float radiusBottomRight,
        float radiusBottomLeft,
        float smoothness,
        float progress,
        float dirX,
        float dirY,
        float bandWidth,
        float intensity,
        int color
) {
    public BuiltShine(
            float x, float y, float width, float height,
            float radius, float smoothness,
            float progress, float dirX, float dirY, float bandWidth, float intensity,
            int color
    ) {
        this(x, y, width, height, radius, radius, radius, radius, smoothness, progress, dirX, dirY, bandWidth, intensity, color);
    }

    public boolean visible() {
        return width > 0 && height > 0 && intensity > 0.001f;
    }
}


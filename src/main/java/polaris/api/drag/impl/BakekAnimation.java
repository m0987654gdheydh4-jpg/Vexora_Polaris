package polaris.api.drag.impl;


public final class BakekAnimation {
    
    public static final Bezier SIZE = Bezier.of(0.27f, 1.09f, 0.49f, 1.06f);
    
    public static final Bezier BAKEK = Bezier.of(0.45f, 1.45f, 0.49f, 1.15f);
    
    public static final Bezier FIGMA = Bezier.of(0.42f, 0f, 0.58f, 1f);
    
    public static final Bezier LINEAR = Bezier.of(0f, 0f, 1f, 1f);

    private final long durationMs;
    private final Bezier easing;
    private float value;
    private float startValue;
    private float targetValue;
    private long startTime;
    private boolean done = true;

    public BakekAnimation(long durationMs, float initial, Bezier easing) {
        this.durationMs = Math.max(1L, durationMs);
        this.easing = easing == null ? LINEAR : easing;
        this.value = initial;
        this.startValue = initial;
        this.targetValue = initial;
        this.startTime = System.currentTimeMillis();
    }

    public BakekAnimation(long durationMs, Bezier easing) {
        this(durationMs, 0f, easing);
    }

    public float update(float newTarget) {
        long now = System.currentTimeMillis();
        if (newTarget != targetValue) {
            startValue = value;
            targetValue = newTarget;
            startTime = now;
            done = false;
        }
        long elapsed = now - startTime;
        if (elapsed >= durationMs) {
            value = targetValue;
            done = true;
            return value;
        }
        float progress = elapsed / (float) durationMs;
        float eased = easing.ease(progress);
        value = startValue + (targetValue - startValue) * eased;
        return value;
    }

    public float update(boolean toOne) {
        return update(toOne ? 1f : 0f);
    }

    public float get() {
        return value;
    }

    public void set(float v) {
        value = startValue = targetValue = v;
        done = true;
    }

    public boolean isDone() {
        return done;
    }

    public record Bezier(float x1, float y1, float x2, float y2) {
        public static Bezier of(float x1, float y1, float x2, float y2) {
            return new Bezier(x1, y1, x2, y2);
        }

        public float ease(float progress) {
            if (progress <= 0f) return 0f;
            if (progress >= 1f) return 1f;
            float t = solveT(x1, x2, progress);
            return bezierY(t, y1, y2);
        }

        private static float solveT(float x1, float x2, float progress) {
            float t = progress;
            for (int i = 0; i < 8; i++) {
                float x = bezierX(t, x1, x2);
                float dx = bezierDX(t, x1, x2);
                if (Math.abs(x - progress) < 1e-5f || Math.abs(dx) < 1e-6f) break;
                t -= (x - progress) / dx;
                if (t < 0f) t = 0f;
                if (t > 1f) t = 1f;
            }
            return t;
        }

        private static float bezierX(float t, float x1, float x2) {
            float u = 1f - t;
            return 3f * u * u * t * x1 + 3f * u * t * t * x2 + t * t * t;
        }

        private static float bezierDX(float t, float x1, float x2) {
            return 3f * ((1f - t) * (1f - 3f * t) * x1 + (2f * t - 3f * t * t) * x2) + 3f * t * t;
        }

        private static float bezierY(float t, float y1, float y2) {
            float u = 1f - t;
            return 3f * u * u * t * y1 + 3f * u * t * t * y2 + t * t * t;
        }
    }
}

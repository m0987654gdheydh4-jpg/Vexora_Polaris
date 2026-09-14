package polaris.screens.csgui.elements;

import java.util.function.Function;

public final class SimpleLinearAnimation {
    private static final long DEFAULT_DURATION_MS = 300;

    private long startTime;
    private long duration;
    private boolean forward = true;
    private boolean immediate = false;
    private boolean started = false;
    private Function<Float, Float> easing;

    public SimpleLinearAnimation() {
        this(DEFAULT_DURATION_MS);
    }

    public SimpleLinearAnimation(long durationMs) {
        this.duration = durationMs;
        this.easing = t -> t;
        this.forward = false;
    }

    public void setDuration(long durationMs) {
        this.duration = durationMs;
    }

    public void setEasing(Object easingObj) {
    }

    public void show() {
        if (!forward) {
            float current = getProgress();
            forward = true;
            immediate = false;
            
            startTime = System.currentTimeMillis() - (long) (current * duration);
            started = true;
        }
    }

    public void hide() {
        if (forward) {
            float current = getProgress();
            forward = false;
            immediate = false;
            startTime = System.currentTimeMillis() - (long) ((1f - current) * duration);
            started = true;
        }
    }

    public float getProgress() {
        if (immediate) return forward ? 1f : 0f;
        if (!started) return forward ? 1f : 0f;
        long elapsed = System.currentTimeMillis() - startTime;
        if (elapsed >= duration) return forward ? 1f : 0f;
        float t = Math.max(0f, Math.min(1f, elapsed / (float) duration));
        if (t >= 1f) return forward ? 1f : 0f;
        return forward ? easing.apply(t) : 1f - easing.apply(t);
    }

    public boolean isFinished() {
        if (immediate) return true;
        return !started || System.currentTimeMillis() - startTime >= duration;
    }

    public void setImmediate(boolean state) {
        this.immediate = true;
        this.forward = state;
        this.started = true;
        this.startTime = System.currentTimeMillis() - duration;
    }

    private float applyEasing(float t) {
        return easing.apply(t);
    }
}

package polaris.api.module.impl.visual.richdog.util;

public final class PetAnimation {

    private float current = 0f;
    private float target  = 0f;
    private float origin  = 0f;
    private long  startMs = 0L;
    private int   durationMs = 1;

    public PetAnimation() {
    }

    public float animate(float destination, int ms) {
        if (destination == target) {
            return get();
        }
        origin     = get();
        target     = destination;
        durationMs = Math.max(1, ms);
        startMs    = System.currentTimeMillis();
        return get();
    }

    public float get() {
        long elapsed = System.currentTimeMillis() - startMs;
        if (elapsed >= durationMs) {
            current = target;
            return current;
        }
        float t = (float) elapsed / durationMs;
        t = t * t * (3f - 2f * t);
        current = origin + (target - origin) * t;
        return current;
    }

    public boolean finished() {
        return System.currentTimeMillis() - startMs >= durationMs;
    }
}


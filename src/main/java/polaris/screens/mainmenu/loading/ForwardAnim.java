package polaris.screens.mainmenu.loading;


public final class ForwardAnim {
    public enum Ease {
        OUT_CIRC,
        BOTH_SINE
    }

    private long startMs = System.currentTimeMillis();
    private int durationMs = 300;
    private boolean forward;
    private Ease ease = Ease.OUT_CIRC;

    public ForwardAnim duration(int ms) {
        this.durationMs = Math.max(1, ms);
        return this;
    }

    public ForwardAnim ease(Ease ease) {
        this.ease = ease == null ? Ease.OUT_CIRC : ease;
        return this;
    }

    public ForwardAnim finishAt(boolean forward) {
        this.forward = forward;
        this.startMs = System.currentTimeMillis() - durationMs;
        return this;
    }

    public ForwardAnim setForward(boolean forward) {
        if (this.forward == forward) {
            return this;
        }
        float displayed = getLinear();
        this.forward = forward;
        float progress = forward ? displayed : 1.0f - displayed;
        progress = Math.max(0.0f, Math.min(1.0f, progress));
        this.startMs = System.currentTimeMillis() - (long) (progress * durationMs);
        return this;
    }

    public boolean isForward() {
        return forward;
    }

    public boolean finished() {
        return elapsed() >= durationMs && forward;
    }

    public boolean finished(boolean wantForward) {
        return elapsed() >= durationMs && this.forward == wantForward;
    }

    public float get() {
        float t = Math.min(1.0f, elapsed() / (float) durationMs);
        float e = applyEase(t);
        return forward ? e : 1.0f - e;
    }

    public float getLinear() {
        float t = Math.min(1.0f, elapsed() / (float) durationMs);
        return forward ? t : 1.0f - t;
    }

    private long elapsed() {
        return System.currentTimeMillis() - startMs;
    }

    private float applyEase(float x) {
        return switch (ease) {
            case BOTH_SINE -> (float) (-(Math.cos(Math.PI * x) - 1.0) / 2.0);
            case OUT_CIRC -> (float) Math.sqrt(1.0 - Math.pow(x - 1.0, 2));
        };
    }
}

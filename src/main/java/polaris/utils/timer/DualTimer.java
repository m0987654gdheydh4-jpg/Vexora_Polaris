package polaris.utils.timer;

/**
 * Dual-purpose delay timer (full Wild DualTimer API + polaris aliases).
 * Primary polaris API: {@link #reset()} / {@link #passed(long)}.
 * Wild aliases: {@link #invoke()} / {@link #check5(long)} / etc.
 */
public final class DualTimer {
    private long timestamp;
    public long timestamp2 = System.currentTimeMillis();

    

    public void reset() {
        timestamp2 = System.currentTimeMillis();
    }

    public boolean passed(long delayMs) {
        return System.currentTimeMillis() - timestamp2 > delayMs;
    }

    public boolean passedOrReset(long delayMs) {
        if (passed(delayMs)) {
            reset();
            return true;
        }
        return false;
    }

    public long elapsed() {
        return System.currentTimeMillis() - timestamp2;
    }

    public void setFuture(long delayMs) {
        timestamp2 = System.currentTimeMillis() + delayMs;
    }

    public long getLastMs() {
        return timestamp2;
    }

    public void setLastMs(long lastMs) {
        this.timestamp2 = lastMs;
    }

    

    
    public void invoke() {
        this.timestamp2 = System.currentTimeMillis();
    }

    public boolean check(long l) {
        return System.currentTimeMillis() - this.timestamp2 > l;
    }

    public void setTimestamp2(long l) {
        this.timestamp2 = System.currentTimeMillis() + l;
    }

    public void setTimestamp22(long l) {
        this.timestamp2 = l;
    }

    public boolean check2(double d) {
        return System.currentTimeMillis() - d >= this.timestamp;
    }

    public boolean check3(double d) {
        boolean flag = this.check2(d);
        if (flag) {
            this.invoke();
        }
        return flag;
    }

    public long compute() {
        return System.currentTimeMillis() - this.timestamp;
    }

    public void setTimestamp(long l) {
        this.timestamp = System.currentTimeMillis() - l;
    }

    public long compute2() {
        return System.currentTimeMillis() - this.timestamp2;
    }

    public boolean check4() {
        return System.currentTimeMillis() - this.timestamp2 <= 0L;
    }

    public boolean check5(long l) {
        return System.currentTimeMillis() - this.timestamp2 > l;
    }

    public boolean check6() {
        return this.timestamp2 < System.currentTimeMillis();
    }

    public boolean check7(long l, boolean bl) {
        if (System.currentTimeMillis() - this.timestamp2 > l) {
            if (bl) {
                this.invoke();
            }
            return true;
        }
        return false;
    }

    public boolean check8(double d) {
        return this.compute3() >= d;
    }

    public long compute3() {
        return System.currentTimeMillis() - this.timestamp2;
    }

    public long compute4(int i) {
        return System.currentTimeMillis() + i;
    }

    public long getTimestamp2() {
        return this.timestamp2;
    }

    public void invoke2() {
        this.timestamp2 = System.currentTimeMillis();
    }

    public boolean check9(long l) {
        return System.currentTimeMillis() - this.timestamp2 >= l;
    }

    public boolean check10(long l) {
        if (System.currentTimeMillis() - this.timestamp2 >= l) {
            this.invoke();
            return true;
        }
        return false;
    }

    public boolean check11(long l) {
        return System.currentTimeMillis() - this.timestamp2 >= l;
    }

    public long getTimestamp() {
        return this.timestamp;
    }
}

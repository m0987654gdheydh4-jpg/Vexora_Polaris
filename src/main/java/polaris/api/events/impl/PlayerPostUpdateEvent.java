package polaris.api.events.impl;

import polaris.api.events.Event;

/**
 * Событие вызывается после обновления игрока (пост-тик).
 * Используется для модулей, которым нужно реагировать на завершение тика игрока.
 */
public final class PlayerPostUpdateEvent implements Event {
    private int iterations = 1;

    public int getIterations() {
        return iterations;
    }

    public void setIterations(int iterations) {
        this.iterations = iterations;
    }
}

package polaris.api.events.impl;

import polaris.api.events.CancellableEvent;

public final class PushEvent extends CancellableEvent {
    private final Type type;

    public PushEvent(Type type) {
        this.type = type;
    }

    public Type getType() {
        return type;
    }

    public enum Type {
        COLLISION,
        BLOCK,
        WATER
    }
}


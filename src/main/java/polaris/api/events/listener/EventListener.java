package polaris.api.events.listener;

import polaris.api.events.Event;

@FunctionalInterface
public interface EventListener<T extends Event> {
    void invoke(T event) throws Exception;
}


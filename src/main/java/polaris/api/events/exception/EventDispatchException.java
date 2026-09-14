package polaris.api.events.exception;

import polaris.api.events.Event;

public class EventDispatchException extends EventException {
    public EventDispatchException(Event event, Throwable cause) {
        super("Failed to dispatch event: " + (event == null ? "null" : event.getClass().getName()), cause);
    }
}


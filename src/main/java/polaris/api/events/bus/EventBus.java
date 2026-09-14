package polaris.api.events.bus;

import polaris.api.events.Cancellable;
import polaris.api.events.Event;
import polaris.api.events.annotation.SubscribeEvent;
import polaris.api.events.exception.EventDispatchException;
import polaris.api.events.exception.EventRegistrationException;
import polaris.api.events.listener.EventListener;
import polaris.api.events.listener.RegisteredListener;
import polaris.api.events.types.EventPriority;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

public final class EventBus {
    private final Map<Class<? extends Event>, CopyOnWriteArrayList<RegisteredListener<?>>> listeners = new ConcurrentHashMap<>();
    private final Map<Class<? extends Event>, CachedListeners> listenerCache = new ConcurrentHashMap<>();
    private final Map<Object, List<EventSubscription>> ownerSubscriptions = Collections.synchronizedMap(new IdentityHashMap<>());
    private final AtomicLong orderCounter = new AtomicLong();
    
    
    
    private final AtomicLong registryVersion = new AtomicLong();
    private final Set<String> reportedFailures = ConcurrentHashMap.newKeySet();

    private record CachedListeners(long version, RegisteredListener<?>[] listeners) {
    }

    public <T extends Event> EventSubscription subscribe(Class<T> eventType, EventListener<? super T> listener) {
        return subscribe(eventType, EventPriority.NORMAL, false, listener, listener);
    }

    public <T extends Event> EventSubscription subscribe(Class<T> eventType,
                                                        EventPriority priority,
                                                        EventListener<? super T> listener) {
        return subscribe(eventType, priority, false, listener, listener);
    }

    public <T extends Event> EventSubscription subscribe(Class<T> eventType,
                                                        EventPriority priority,
                                                        boolean receiveCancelled,
                                                        EventListener<? super T> listener) {
        return subscribe(eventType, priority, receiveCancelled, listener, listener);
    }

    public void register(Object owner) {
        if (owner == null) {
            throw new EventRegistrationException("Event owner cannot be null");
        }

        
        
        unregister(owner);

        List<EventSubscription> subscriptions = new ArrayList<>();
        for (Method method : collectHandlers(owner.getClass())) {
            subscriptions.add(registerMethod(owner, method, method.getAnnotation(SubscribeEvent.class)));
        }

        if (!subscriptions.isEmpty()) {
            ownerSubscriptions.put(owner, subscriptions);
        }
    }

    public void unregister(Object owner) {
        if (owner == null) {
            return;
        }

        List<EventSubscription> subscriptions = ownerSubscriptions.remove(owner);
        if (subscriptions == null) {
            return;
        }
        for (EventSubscription subscription : subscriptions) {
            subscription.unsubscribe();
        }
    }

    public <T extends Event> T post(T event) {
        if (event == null) {
            throw new EventDispatchException(null, new NullPointerException("event"));
        }

        RegisteredListener<?>[] eventListeners = getListeners(event.getClass());
        for (RegisteredListener<?> listener : eventListeners) {
            if (event instanceof Cancellable cancellable && cancellable.isCancelled() && !listener.isReceiveCancelled()) {
                continue;
            }
            invoke(listener, event);
        }
        return event;
    }

    @SuppressWarnings("unchecked")
    private <T extends Event> EventSubscription subscribe(Class<T> eventType,
                                                         EventPriority priority,
                                                         boolean receiveCancelled,
                                                         EventListener<? super T> listener,
                                                         Object owner) {
        if (eventType == null) {
            throw new EventRegistrationException("Event type cannot be null");
        }
        if (listener == null) {
            throw new EventRegistrationException("Event listener cannot be null");
        }

        RegisteredListener<T> registered = new RegisteredListener<>(
                eventType,
                listener,
                priority == null ? EventPriority.NORMAL : priority,
                receiveCancelled,
                owner,
                orderCounter.incrementAndGet()
        );
        listeners.computeIfAbsent(eventType, ignored -> new CopyOnWriteArrayList<>()).add(registered);
        invalidateCache();
        return new EventSubscription(this, eventType, registered);
    }

    
    private List<Method> collectHandlers(Class<?> type) {
        List<Method> handlers = new ArrayList<>();
        Set<String> seenSignatures = new HashSet<>();
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            for (Method method : current.getDeclaredMethods()) {
                if (method.isSynthetic() || Modifier.isStatic(method.getModifiers())) {
                    continue;
                }
                
                
                if (!seenSignatures.add(method.getName() + Arrays.toString(method.getParameterTypes()))) {
                    continue;
                }
                if (method.getAnnotation(SubscribeEvent.class) != null) {
                    handlers.add(method);
                }
            }
        }
        return handlers;
    }

    void unsubscribe(Class<? extends Event> eventType, RegisteredListener<?> listener) {
        CopyOnWriteArrayList<RegisteredListener<?>> registeredListeners = listeners.get(eventType);
        if (registeredListeners == null) {
            return;
        }
        registeredListeners.remove(listener);
        if (registeredListeners.isEmpty()) {
            listeners.remove(eventType);
        }
        invalidateCache();
    }

    private void invalidateCache() {
        registryVersion.incrementAndGet();
        listenerCache.clear();
    }

    @SuppressWarnings("unchecked")
    private EventSubscription registerMethod(Object owner, Method method, SubscribeEvent annotation) {
        if (method.getParameterCount() != 1) {
            throw new EventRegistrationException("@SubscribeEvent method must have exactly one event parameter: " + method);
        }

        Class<?> parameterType = method.getParameterTypes()[0];
        if (!Event.class.isAssignableFrom(parameterType)) {
            throw new EventRegistrationException("@SubscribeEvent parameter must implement Event: " + method);
        }

        method.setAccessible(true);
        Class<? extends Event> eventType = (Class<? extends Event>) parameterType;
        MethodHandle handle;
        try {
            
            
            handle = MethodHandles.privateLookupIn(method.getDeclaringClass(), MethodHandles.lookup())
                    .unreflect(method)
                    .bindTo(owner);
        } catch (IllegalAccessException exception) {
            throw new EventRegistrationException("Cannot access @SubscribeEvent method: " + method, exception);
        }
        EventListener<Event> reflectedListener = event -> {
            try {
                handle.invoke(event);
            } catch (Throwable throwable) {
                if (throwable instanceof Exception exception) {
                    throw exception;
                }
                if (throwable instanceof Error error) {
                    throw error;
                }
                throw new EventDispatchException(event, throwable);
            }
        };

        return subscribe((Class<Event>) eventType, annotation.priority(), annotation.receiveCancelled(), reflectedListener, owner);
    }

    private RegisteredListener<?>[] getListeners(Class<? extends Event> eventType) {
        long version = registryVersion.get();
        CachedListeners cached = listenerCache.get(eventType);
        if (cached != null && cached.version() == version) {
            return cached.listeners();
        }

        RegisteredListener<?>[] resolved = resolveListeners(eventType);
        if (registryVersion.get() == version) {
            listenerCache.put(eventType, new CachedListeners(version, resolved));
        }
        return resolved;
    }

    private RegisteredListener<?>[] resolveListeners(Class<? extends Event> eventType) {
        List<RegisteredListener<?>> resolved = new ArrayList<>();
        for (Map.Entry<Class<? extends Event>, CopyOnWriteArrayList<RegisteredListener<?>>> entry : listeners.entrySet()) {
            if (entry.getKey().isAssignableFrom(eventType)) {
                resolved.addAll(entry.getValue());
            }
        }
        resolved.sort(Comparator
                .comparingInt((RegisteredListener<?> listener) -> listener.getPriority().getOrder())
                .thenComparingLong(RegisteredListener::getOrder));
        return resolved.toArray(RegisteredListener<?>[]::new);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void invoke(RegisteredListener<?> listener, Event event) {
        try {
            ((RegisteredListener) listener).getListener().invoke(event);
        } catch (Throwable throwable) {
            
            
            reportDispatchFailure(listener, event, throwable);
        }
    }

    private void reportDispatchFailure(RegisteredListener<?> listener, Event event, Throwable throwable) {
        Object owner = listener.getOwner();
        String key = (owner == null ? "?" : owner.getClass().getName())
                + "#" + (event == null ? "?" : event.getClass().getName())
                + "#" + throwable.getClass().getName();
        if (!reportedFailures.add(key)) {
            return;
        }
        System.err.println("[Polaris] Listener threw while handling "
                + (event == null ? "null" : event.getClass().getSimpleName())
                + " (owner: " + (owner == null ? "unknown" : owner.getClass().getSimpleName())
                + "). Further identical failures are suppressed.");
        new EventDispatchException(event, throwable).printStackTrace();
    }
}


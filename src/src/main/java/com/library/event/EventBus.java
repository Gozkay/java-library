
package com.library.event;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Distributes library events to registered listeners.
 */
public class EventBus {

    private final List<Consumer<LibraryEvent>> listeners =
            new CopyOnWriteArrayList<>();

    /**
     * Registers a listener to receive library events.
     */
    public void subscribe(Consumer<LibraryEvent> listener) {
        if (listener == null) {
            throw new IllegalArgumentException("Listener cannot be null");
        }

        listeners.add(listener);
    }

    /**
     * Removes a listener from the event bus.
     */
    public void unsubscribe(Consumer<LibraryEvent> listener) {
        listeners.remove(listener);
    }

    /**
     * Sends an event to all registered listeners.
     */
    public void publish(LibraryEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("Event cannot be null");
        }

        for (Consumer<LibraryEvent> listener : listeners) {
            listener.accept(event);
        }
    }
}

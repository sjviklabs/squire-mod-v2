package com.sjviklabs.squire.brain;

import com.sjviklabs.squire.entity.SquireEntity;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Lightweight in-process observer bus scoped to one squire instance.
 *
 * NOT NeoForge's IEventBus or MinecraftForge.EVENT_BUS. This bus is created
 * fresh for every SquireEntity and lives only as long as that entity does.
 * No synchronization is required — the server tick loop is single-threaded.
 *
 * Usage:
 *   bus.subscribe(SquireEvent.SIT_TOGGLE, s -> handler.onSitToggle(s));
 *   bus.publish(SquireEvent.SIT_TOGGLE, squire);
 */
public class SquireBrainEventBus {

    private final Map<SquireEvent, List<Consumer<SquireEntity>>> listeners =
            new EnumMap<>(SquireEvent.class);

    /**
     * Register a handler for the given event. Multiple handlers per event are
     * supported and invoked in registration order.
     */
    public void subscribe(SquireEvent event, Consumer<SquireEntity> handler) {
        listeners.computeIfAbsent(event, k -> new ArrayList<>()).add(handler);
    }

    /**
     * Dispatch the event to all registered handlers. Safe to call with zero
     * subscribers — returns immediately if no handlers are registered.
     */
    public void publish(SquireEvent event, SquireEntity squire) {
        List<Consumer<SquireEntity>> list = listeners.get(event);
        if (list != null) list.forEach(h -> h.accept(squire));
    }
}

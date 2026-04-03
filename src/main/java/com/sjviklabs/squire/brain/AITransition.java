package com.sjviklabs.squire.brain;

import com.sjviklabs.squire.entity.SquireEntity;

import javax.annotation.Nullable;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

/**
 * A state machine transition with tick-rate throttling.
 *
 * @param sourceState State this transition applies to, or null for global (any-state) transitions
 * @param condition   Predicate checked at the tick rate interval
 * @param action      Executes the transition and returns the next state
 * @param tickRate    How often (in ticks) to evaluate this transition. 1 = every tick.
 * @param priority    Lower value = higher priority. Global transitions evaluated before state-specific.
 */
public record AITransition(
        @Nullable SquireAIState sourceState,
        BooleanSupplier condition,
        Function<SquireEntity, SquireAIState> action,
        int tickRate,
        int priority
) {
    public AITransition {
        if (tickRate < 1) throw new IllegalArgumentException("tickRate must be >= 1");
        if (priority < 0) throw new IllegalArgumentException("priority must be >= 0");
    }

    /** True if this transition applies regardless of current state. */
    public boolean isGlobal() {
        return sourceState == null;
    }

    /** True if this transition applies to the given state (or is global). */
    public boolean appliesTo(SquireAIState currentState) {
        return sourceState == null || sourceState == currentState;
    }
}

package com.sjviklabs.squire.brain;

import java.util.Set;

/**
 * Contract for work-type behavior handlers (mining, placing, farming, fishing, chest).
 *
 * SquireBrain uses this interface to:
 *   1. Auto-register FSM transitions for all owned states (no boilerplate per handler)
 *   2. Suspend/resume work across combat and follow interruptions
 *   3. Check whether the current state is a work state (for auto-deposit)
 *
 * Adding a new work type = implement this interface + add to SquireBrain.workHandlers.
 * Zero changes to SquireBrain's transition registration, resume logic, or auto-deposit.
 *
 * Ousterhout Ch. 6.3: general-purpose API replaces N specialized registrations.
 */
public interface WorkHandler {

    /** All FSM states this handler owns and ticks. */
    Set<SquireAIState> ownedStates();

    /** State to re-enter when resuming after interruption (combat/follow). */
    SquireAIState resumeState();

    /** Whether this handler has active work to resume after interruption. */
    boolean hasActiveWork();

    /** Per-tick logic. Called by SquireBrain when FSM is in an owned state. */
    void tick(SquireAIState currentState);

    /** FSM priority for transitions (lower = higher priority). */
    int priority();
}

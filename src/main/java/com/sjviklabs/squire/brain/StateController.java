package com.sjviklabs.squire.brain;

/**
 * Narrow interface for work handlers to control FSM state transitions.
 *
 * Handlers use this to transition between their own sub-states (e.g.
 * MINING_APPROACH → MINING_BREAK) without depending on the full
 * TickRateStateMachine. Only exposes forceState() and getCurrentState() —
 * the two operations handlers actually need.
 *
 * Ousterhout Ch. 6.4: general-purpose interfaces reduce information leakage.
 */
public interface StateController {

    /** Force the FSM into a specific state immediately. */
    void forceState(SquireAIState state);

    /** Returns the current FSM state. */
    SquireAIState getCurrentState();
}

package com.sjviklabs.squire.ai.state;

import com.minecolonies.api.entity.ai.statemachine.states.IState;

/**
 * Typed state enum for all Squire job AIs.
 *
 * v4.0.0 replacement for the old 27-state flat {@code brain.SquireAIState}. Deliberately
 * smaller — one state per "what the squire is actively doing" — because job AIs own their
 * internal sub-states, not the global machine.
 *
 * Each enum value is shared across the job AIs that use it (e.g. FOLLOWING is both the
 * terminal state of IdleAI when the owner walks far away and the main state of FollowOwnerAI).
 * That's fine — the state enum is just the TickRateStateMachine's current position; job AIs
 * encode the real behavior in their AITarget transitions.
 *
 * IState is a marker interface (no methods). Implementing it lets {@code TickRateStateMachine<SquireAIState>}
 * parameterize cleanly.
 */
public enum SquireAIState implements IState {

    /** No active work; the squire is standing, looking around, minimally moving. */
    IDLE,

    /** Walking toward the owner because they're beyond the follow-distance threshold. */
    FOLLOWING,

    /** Engaged with a threat on the threat table — approach / attack / ranged covered internally. */
    FIGHTING,

    /** Patrolling a crest-selected area perimeter. */
    PATROLLING,

    /** Active work states — the job AI owns sub-transitions under these umbrellas. */
    MINING,
    FARMING,
    FISHING,
    PLACING,

    /** Survival states — eating to recover saturation, fleeing when HP is low. */
    EATING,
    FLEEING,

    /** Terminal state: ordered to sit via /squire mode stay. Preempts everything. */
    SITTING
}

package com.sjviklabs.squire.ai.job;

import com.sjviklabs.squire.ai.state.SquireAIState;

/**
 * Common contract for every Squire job AI (IdleAI, FollowOwnerAI, GuardAI, MinerAI, ...).
 *
 * Each implementation wraps one {@code TickRateStateMachine<SquireAIState>} and owns its
 * own state tree. The {@link com.sjviklabs.squire.ai.SquireAIController} picks which job
 * AI is active and routes ticks to it — only one runs at a time.
 *
 * Intentionally minimal — job AIs stay self-contained. Complexity lives inside the
 * state machine and the AITarget lambdas, not here (Ousterhout: pull complexity downward).
 */
public interface JobAI {

    /** Called once per server tick on the active job AI. Delegates to the internal state machine. */
    void tick();

    /** Current state of the underlying state machine. Used by SquireAIController for telemetry + switching decisions. */
    SquireAIState getCurrentState();
}

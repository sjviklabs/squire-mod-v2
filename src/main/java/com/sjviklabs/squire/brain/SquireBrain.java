package com.sjviklabs.squire.brain;

import com.sjviklabs.squire.brain.handler.FollowHandler;
import com.sjviklabs.squire.entity.SquireEntity;

/**
 * Central brain for a squire — owns the FSM, behavior handlers, and event bus.
 *
 * Construction order:
 *   1. Instantiate handlers (FollowHandler, etc.)
 *   2. Register transitions (handlers must exist before condition lambdas capture them)
 *   3. tick() delegates to machine.tick() every server aiStep()
 *
 * Phase 2: FollowHandler + FOLLOWING_OWNER transitions wired.
 * Phase 2 plan 02-02: SquireBrainEventBus + SquireEvent added; SIT_TOGGLE transitions added.
 * Phase 4+: CombatHandler, SurvivalHandler bolt on here without changing constructor shape.
 */
public class SquireBrain {

    private final SquireEntity squire;
    private final TickRateStateMachine machine;

    // ── Behavior handlers ────────────────────────────────────────────────────
    private final FollowHandler follow;

    public SquireBrain(SquireEntity squire) {
        this.squire = squire;
        this.machine = new TickRateStateMachine();

        // Initialize handlers before registerTransitions() — lambdas capture handler references
        this.follow = new FollowHandler(squire);

        registerTransitions();
    }

    /** Called every server-side aiStep() from SquireEntity. */
    public void tick() {
        machine.tick(squire);
    }

    /** Returns the current FSM state — used for debug rendering and test assertions. */
    public SquireAIState getCurrentState() {
        return machine.getCurrentState();
    }

    // ── Transition registration ──────────────────────────────────────────────

    /**
     * Register all FSM transitions in priority order.
     * Sitting transitions (plan 02-02) are registered in registerSittingTransitions().
     * Follow transitions are registered in registerFollowTransitions().
     */
    private void registerTransitions() {
        // Plan 02-02 adds registerSittingTransitions() call here
        registerFollowTransitions();
    }

    /**
     * Wire FOLLOWING_OWNER transitions:
     * - IDLE → FOLLOWING_OWNER when owner is far enough (priority 30, tickRate 10)
     * - FOLLOWING_OWNER → IDLE when owner is close or invalid (priority 30, tickRate 10)
     * - Per-tick FOLLOWING_OWNER action (priority 31, tickRate 1)
     *
     * Priority 29/30 for stop vs tick: stop fires first so squire doesn't move
     * on the same tick it decides to stop following.
     */
    private void registerFollowTransitions() {
        // IDLE → FOLLOWING_OWNER: owner walked away
        machine.addTransition(new AITransition(
                SquireAIState.IDLE,
                follow::shouldFollow,
                s -> { follow.start(); return SquireAIState.FOLLOWING_OWNER; },
                10, 30
        ));

        // FOLLOWING_OWNER → IDLE: owner close or invalid
        machine.addTransition(new AITransition(
                SquireAIState.FOLLOWING_OWNER,
                follow::shouldStop,
                s -> { follow.stop(); return SquireAIState.IDLE; },
                10, 30
        ));

        // FOLLOWING_OWNER tick: runs every tick, lower priority than stop check
        machine.addTransition(new AITransition(
                SquireAIState.FOLLOWING_OWNER,
                () -> !follow.shouldStop(),
                follow::tick,
                1, 31
        ));
    }
}

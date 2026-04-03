package com.sjviklabs.squire.brain;

import com.sjviklabs.squire.brain.handler.FollowHandler;
import com.sjviklabs.squire.entity.SquireEntity;
import net.minecraft.world.entity.Pose;

/**
 * Central brain for a squire — owns the FSM, event bus, and behavior handlers.
 *
 * Construction order (CRITICAL — do not reorder):
 *   1. Instantiate machine and bus
 *   2. Register bus subscriptions (before any transitions can fire)
 *   3. Instantiate handlers (handler constructors may call bus.subscribe())
 *   4. registerTransitions() — lambdas capture handler and bus references
 *
 * Phase 2 plan 02-02: SquireBrainEventBus + SquireEvent + SITTING transitions.
 * Phase 2 plan 02-03: FollowHandler + FOLLOWING_OWNER transitions.
 * Phase 4+: CombatHandler, SurvivalHandler bolt on here without changing constructor shape.
 */
public class SquireBrain {

    private final SquireEntity squire;
    private final TickRateStateMachine machine;
    private final SquireBrainEventBus bus;

    // ── Behavior handlers ────────────────────────────────────────────────────
    private final FollowHandler follow;

    // TODO (02-04): SurvivalHandler survival — added by Plan 02-04

    private int idleTicks;

    public SquireBrain(SquireEntity squire) {
        this.squire = squire;
        this.machine = new TickRateStateMachine();
        this.bus = new SquireBrainEventBus();

        // Subscriptions registered BEFORE registerTransitions() so no event fires
        // into an empty bus. Phase 2 has no subscribers yet — handlers added in
        // 02-04 (SurvivalHandler) will subscribe from their constructors.

        // Initialize handlers — their constructors may subscribe to the bus.
        this.follow = new FollowHandler(squire);

        registerTransitions();
    }

    // -------------------------------------------------------------------------
    // Public entry points
    // -------------------------------------------------------------------------

    /** Called every server-side aiStep() from SquireEntity. */
    public void tick() {
        if (machine.getCurrentState() != SquireAIState.IDLE) {
            idleTicks = 0;
        }
        machine.tick(squire);
    }

    /** Returns the current FSM state — used for debug rendering and test assertions. */
    public SquireAIState getCurrentState() {
        return machine.getCurrentState();
    }

    // -------------------------------------------------------------------------
    // Accessors (used by Plans 02-03, 02-04, and later phases)
    // -------------------------------------------------------------------------

    public TickRateStateMachine getMachine() {
        return machine;
    }

    public SquireBrainEventBus getBus() {
        return bus;
    }

    // -------------------------------------------------------------------------
    // Transitions
    // -------------------------------------------------------------------------

    /**
     * Register all FSM transitions. Called once during construction after all
     * handlers and subscriptions are set up.
     */
    private void registerTransitions() {
        registerSittingTransitions();
        registerFollowTransitions();
    }

    /**
     * SITTING entry/exit transitions (plan 02-02).
     * Both transitions publish SIT_TOGGLE so any subscriber (e.g. SurvivalHandler)
     * can react without coupling to SquireBrain directly.
     */
    private void registerSittingTransitions() {

        // Enter SITTING (global — fires from any state, priority 1, tickRate 1)
        machine.addTransition(new AITransition(
                null,   // sourceState null = global
                () -> machine.getCurrentState() != SquireAIState.SITTING
                        && squire.isOrderedToSit()
                        && !squire.isInWaterOrBubble(),
                s -> {
                    s.getNavigation().stop();
                    s.setPose(Pose.SITTING);
                    bus.publish(SquireEvent.SIT_TOGGLE, s);
                    return SquireAIState.SITTING;
                },
                1,  // tickRate
                1   // priority
        ));

        // Exit SITTING (source-locked to SITTING state, priority 1, tickRate 1)
        machine.addTransition(new AITransition(
                SquireAIState.SITTING,
                () -> !squire.isOrderedToSit(),
                s -> {
                    s.setPose(Pose.STANDING);
                    bus.publish(SquireEvent.SIT_TOGGLE, s);
                    return SquireAIState.IDLE;
                },
                1,  // tickRate
                1   // priority
        ));
    }

    /**
     * Wire FOLLOWING_OWNER transitions (plan 02-03):
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

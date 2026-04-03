package com.sjviklabs.squire.brain;

import com.sjviklabs.squire.brain.handler.FollowHandler;
import com.sjviklabs.squire.brain.handler.SurvivalHandler;
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
    private final SurvivalHandler survival;

    private int idleTicks;

    public SquireBrain(SquireEntity squire) {
        this.squire = squire;

        // Construction order is CRITICAL — do not reorder:
        // 1. machine and bus first (nothing can fire until transitions are registered)
        this.machine = new TickRateStateMachine();
        this.bus = new SquireBrainEventBus();

        // 2. Initialize handlers
        this.follow = new FollowHandler(squire);
        this.survival = new SurvivalHandler(squire);

        // 3. Subscribe BEFORE registerTransitions() so no event fires into an empty handler.
        //    SIT_TOGGLE resets eat cooldown so squire doesn't eat mid-sit-down animation.
        bus.subscribe(SquireEvent.SIT_TOGGLE, s -> survival.reset());

        // 4. Register transitions last — lambdas capture fully-initialized handler references.
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
     *
     * Priority order reflects behavioral priority:
     *   sitting (1) > eating (20) > following (30)
     */
    private void registerTransitions() {
        registerSittingTransitions();
        registerEatingTransitions();
        registerFollowTransitions();
    }

    /**
     * EATING transitions (plan 02-04):
     * - Global enter EATING at priority 20, tickRate 10
     * - EATING per-tick at priority 21, tickRate 1
     *
     * Pitfall 5 guard: the enter transition checks currentState != EATING to prevent
     * re-entering EATING every 10 ticks while already eating.
     */
    private void registerEatingTransitions() {

        // Enter EATING (global — fires from any state except EATING itself, priority 20, tickRate 10)
        machine.addTransition(new AITransition(
                null,   // sourceState null = global
                () -> machine.getCurrentState() != SquireAIState.EATING
                        && survival.shouldEat(),
                s -> {
                    survival.startEating();
                    return SquireAIState.EATING;
                },
                10, // tickRate
                20  // priority
        ));

        // EATING per-tick: decrements cooldown, then returns IDLE to re-evaluate state
        // (machine immediately re-enters FOLLOWING_OWNER if still following)
        machine.addTransition(new AITransition(
                SquireAIState.EATING,
                () -> true,
                s -> {
                    survival.tick();
                    return SquireAIState.IDLE;
                },
                1,  // tickRate — runs every tick
                21  // priority — fires after enter check would lose to priority 20
        ));
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

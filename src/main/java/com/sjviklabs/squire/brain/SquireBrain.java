package com.sjviklabs.squire.brain;

import com.sjviklabs.squire.brain.handler.ChestHandler;
import com.sjviklabs.squire.brain.handler.CombatHandler;
import com.sjviklabs.squire.brain.handler.DangerHandler;
import com.sjviklabs.squire.brain.handler.FarmingHandler;
import com.sjviklabs.squire.brain.handler.FishingHandler;
import com.sjviklabs.squire.brain.handler.FollowHandler;
import com.sjviklabs.squire.brain.handler.ItemHandler;
import com.sjviklabs.squire.brain.handler.MiningHandler;
import com.sjviklabs.squire.brain.handler.PlacingHandler;
import com.sjviklabs.squire.brain.handler.SurvivalHandler;
import com.sjviklabs.squire.brain.handler.TorchHandler;
import com.sjviklabs.squire.entity.SquireEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private static final Logger LOGGER = LoggerFactory.getLogger(SquireBrain.class);

    // ── Behavior handlers ────────────────────────────────────────────────────
    private final FollowHandler follow;
    private final SurvivalHandler survival;
    private final CombatHandler combat;
    private final DangerHandler danger;
    private final ItemHandler item;

    // ── Work handlers (Phase 6) ──────────────────────────────────────────────
    private final MiningHandler mining;
    private final PlacingHandler placing;
    private final FarmingHandler farming;
    private final FishingHandler fishing;
    private final ChestHandler chest;
    private final TorchHandler torch;

    // ── Task queue (Phase 6) ─────────────────────────────────────────────────
    private final TaskQueue taskQueue = new TaskQueue();

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
        this.combat = new CombatHandler(squire);
        this.danger = new DangerHandler(squire);
        this.item = new ItemHandler(squire);

        // Work handlers (Phase 6) — machine reference passed so handlers can forceState()
        this.mining  = new MiningHandler(squire, machine);
        this.placing = new PlacingHandler(squire, machine);
        this.farming = new FarmingHandler(squire, machine);
        this.fishing = new FishingHandler(squire, machine);
        this.chest   = new ChestHandler(squire, machine);
        this.torch   = new TorchHandler(squire);

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
        if (machine.getCurrentState() == SquireAIState.IDLE) {
            idleTicks++;
            // Dispatch next queued task when idle and queue is non-empty
            if (!taskQueue.isEmpty()) {
                SquireTask next = taskQueue.poll();
                if (next != null) {
                    dispatch(next);
                }
            }
        } else {
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

    public CombatHandler getCombatHandler() {
        return combat;
    }

    /** Returns the task queue for NBT persistence (SquireEntity save/load). */
    public TaskQueue getTaskQueue() {
        return taskQueue;
    }

    // -------------------------------------------------------------------------
    // Task dispatch
    // -------------------------------------------------------------------------

    /**
     * Route a dequeued task to the appropriate work handler.
     *
     * Each handler's setTarget / setArea / startFishing method internally calls
     * machine.forceState() to enter the correct approach state.
     *
     * commandName values: "mine", "mine_area", "place", "farm", "fish",
     *                     "chest_deposit", "chest_withdraw"
     */
    private void dispatch(SquireTask task) {
        CompoundTag p = task.params();
        switch (task.commandName()) {
            case "mine" -> {
                BlockPos pos = new BlockPos(p.getInt("x"), p.getInt("y"), p.getInt("z"));
                mining.setTarget(pos);
                // setTarget() calls machine.forceState(MINING_APPROACH) internally
            }
            case "mine_area" -> {
                BlockPos a = new BlockPos(p.getInt("ax"), p.getInt("ay"), p.getInt("az"));
                BlockPos b = new BlockPos(p.getInt("bx"), p.getInt("by"), p.getInt("bz"));
                mining.setAreaTarget(a, b);
            }
            case "place" -> {
                BlockPos pos = new BlockPos(p.getInt("x"), p.getInt("y"), p.getInt("z"));
                String itemId = p.getString("item");
                net.minecraft.world.item.Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM
                        .get(net.minecraft.resources.ResourceLocation.parse(itemId));
                placing.setTarget(pos, item);
            }
            case "farm" -> {
                BlockPos a = new BlockPos(p.getInt("ax"), p.getInt("ay"), p.getInt("az"));
                BlockPos b = new BlockPos(p.getInt("bx"), p.getInt("by"), p.getInt("bz"));
                farming.setArea(a, b);
            }
            case "fish" -> {
                if (p.contains("x")) {
                    BlockPos waterPos = new BlockPos(p.getInt("x"), p.getInt("y"), p.getInt("z"));
                    fishing.startFishing(waterPos);
                } else {
                    fishing.startFishing();
                }
            }
            case "chest_deposit" -> {
                BlockPos pos = new BlockPos(p.getInt("x"), p.getInt("y"), p.getInt("z"));
                chest.setDepositTarget(pos);
            }
            case "chest_withdraw" -> {
                BlockPos pos = new BlockPos(p.getInt("x"), p.getInt("y"), p.getInt("z"));
                String itemId = p.getString("item");
                int amount = p.contains("amount") ? p.getInt("amount") : 1;
                net.minecraft.world.item.Item requestedItem = itemId.isEmpty() ? null
                        : net.minecraft.core.registries.BuiltInRegistries.ITEM
                                .get(net.minecraft.resources.ResourceLocation.parse(itemId));
                chest.setWithdrawTarget(pos, requestedItem, amount);
            }
            default -> LOGGER.warn("[Squire] Unknown task command: {}", task.commandName());
        }
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
        registerCombatTransitions();
        registerFollowTransitions();
        registerItemPickupTransitions();
    }

    /**
     * COMBAT transitions (gap closure from Phase 4 verification):
     * Priority 10-19 (combat layer, between sitting/eating and follow).
     *
     * - Global enter COMBAT_APPROACH when squire has a target and isn't sitting (priority 10, tickRate 5)
     * - COMBAT_APPROACH per-tick: drives melee/tactic logic via CombatHandler.tick() (priority 11, tickRate 1)
     * - COMBAT_APPROACH → COMBAT_RANGED when ranged is preferred (priority 12, tickRate 5)
     * - COMBAT_RANGED per-tick: drives ranged logic via CombatHandler.tickRanged() (priority 13, tickRate 1)
     * - COMBAT_APPROACH/COMBAT_RANGED → FLEEING when CombatHandler triggers flee (priority 10, tickRate 1)
     * - FLEEING per-tick: keeps fleeing, returns to IDLE when safe (priority 11, tickRate 1)
     * - Combat exit → IDLE when target is null/dead (priority 10, tickRate 5)
     */
    private void registerCombatTransitions() {
        // Enter COMBAT_APPROACH (global — any non-sitting state when target exists)
        machine.addTransition(new AITransition(
                null,
                () -> {
                    if (squire.isOrderedToSit()) return false;
                    LivingEntity target = squire.getTarget();
                    return target != null && target.isAlive();
                },
                s -> {
                    combat.start();
                    bus.publish(SquireEvent.COMBAT_START, s);
                    return SquireAIState.COMBAT_APPROACH;
                },
                5, 10
        ));

        // COMBAT_APPROACH per-tick: delegate to CombatHandler which returns next state
        machine.addTransition(new AITransition(
                SquireAIState.COMBAT_APPROACH,
                () -> squire.getTarget() != null && squire.getTarget().isAlive(),
                s -> {
                    LivingEntity target = squire.getTarget();
                    danger.tick(target, combat);
                    SquireAIState next = combat.tick(s);
                    // CombatHandler may return COMBAT_RANGED or FLEEING
                    return next != null ? next : SquireAIState.COMBAT_APPROACH;
                },
                1, 11
        ));

        // COMBAT_RANGED per-tick
        machine.addTransition(new AITransition(
                SquireAIState.COMBAT_RANGED,
                () -> squire.getTarget() != null && squire.getTarget().isAlive(),
                s -> {
                    SquireAIState next = combat.tickRanged(s);
                    return next != null ? next : SquireAIState.COMBAT_RANGED;
                },
                1, 13
        ));

        // FLEEING per-tick: flee until safe distance reached, then exit combat
        machine.addTransition(new AITransition(
                SquireAIState.FLEEING,
                () -> true,
                s -> {
                    LivingEntity target = squire.getTarget();
                    if (target == null || !target.isAlive() || s.distanceToSqr(target) > 256) {
                        combat.stop();
                        squire.setTarget(null);
                        bus.publish(SquireEvent.COMBAT_END, s);
                        return SquireAIState.IDLE;
                    }
                    return SquireAIState.FLEEING;
                },
                1, 11
        ));

        // Exit combat when target dies or disappears (from any combat state)
        machine.addTransition(new AITransition(
                SquireAIState.COMBAT_APPROACH,
                () -> squire.getTarget() == null || !squire.getTarget().isAlive(),
                s -> {
                    combat.stop();
                    bus.publish(SquireEvent.COMBAT_END, s);
                    return SquireAIState.IDLE;
                },
                5, 10
        ));

        machine.addTransition(new AITransition(
                SquireAIState.COMBAT_RANGED,
                () -> squire.getTarget() == null || !squire.getTarget().isAlive(),
                s -> {
                    combat.stop();
                    bus.publish(SquireEvent.COMBAT_END, s);
                    return SquireAIState.IDLE;
                },
                5, 10
        ));
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
     * PICKING_UP_ITEM transitions (plan 05-04):
     * Priority 45 (work tier — below follow at 30 but above patrol/farming).
     *
     * Enter from IDLE or FOLLOWING_OWNER when items are nearby and inventory has space.
     * Per-tick: delegate to ItemHandler.tick() which returns PICKING_UP_ITEM while items remain.
     * Exit to IDLE when no items remain or inventory is full.
     */
    private void registerItemPickupTransitions() {
        // Enter PICKING_UP_ITEM from IDLE when items are nearby
        machine.addTransition(new AITransition(
                SquireAIState.IDLE,
                item::hasNearbyItems,
                s -> { item.start(); return SquireAIState.PICKING_UP_ITEM; },
                10, 45
        ));

        // Enter PICKING_UP_ITEM from FOLLOWING_OWNER when items are nearby
        machine.addTransition(new AITransition(
                SquireAIState.FOLLOWING_OWNER,
                item::hasNearbyItems,
                s -> { item.start(); return SquireAIState.PICKING_UP_ITEM; },
                10, 45
        ));

        // PICKING_UP_ITEM per-tick: delegate to ItemHandler which manages scan interval
        machine.addTransition(new AITransition(
                SquireAIState.PICKING_UP_ITEM,
                () -> true,
                s -> {
                    SquireAIState next = item.tick(s);
                    if (next != SquireAIState.PICKING_UP_ITEM) {
                        item.stop();
                    }
                    return next;
                },
                1, 46
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

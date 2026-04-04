package com.sjviklabs.squire.brain.handler;

import com.sjviklabs.squire.brain.SquireAIState;
import com.sjviklabs.squire.brain.SquireEvent;
import com.sjviklabs.squire.brain.TickRateStateMachine;
import com.sjviklabs.squire.config.SquireConfig;
import com.sjviklabs.squire.entity.SquireEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Simulated fishing behavior — walk to water target, idle-fish using the vanilla loot table.
 *
 * FSM cycle: FISHING_APPROACH → FISHING_IDLE (repeating until stopFishing() called)
 *
 * Fixes v0.5.0 bug: replaces hardcoded probability table (wrong fish rates) with
 * LootContextParamSets.FISHING — vanilla rates, mopack-overridable, Luck of the Sea aware.
 *
 * WRK-05: Squire fishes using minecraft:gameplay/fishing loot table.
 *
 * Package: com.sjviklabs.squire.brain.handler
 * Parallel to FarmingHandler — no file overlap with 06-01 plans.
 */
public class FishingHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(FishingHandler.class);

    private final SquireEntity squire;
    private final TickRateStateMachine machine;

    @Nullable private net.minecraft.core.BlockPos waterTarget;
    private int fishingTimer = 0;

    private static final int STUCK_TIMEOUT = 100;
    private static final int MAX_APPROACH_TICKS = 200;
    private int approachTicks = 0;
    private double lastApproachDistSq = Double.MAX_VALUE;
    private int stuckTicks = 0;

    // ── Constructor ───────────────────────────────────────────────────────────

    public FishingHandler(SquireEntity squire, TickRateStateMachine machine) {
        this.squire = squire;
        this.machine = machine;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Start fishing at the given water target position.
     * Sets state to FISHING_APPROACH.
     */
    public void startFishing(net.minecraft.core.BlockPos waterPos) {
        this.waterTarget = waterPos;
        this.fishingTimer = 0;
        this.approachTicks = 0;
        this.stuckTicks = 0;
        this.lastApproachDistSq = Double.MAX_VALUE;
        machine.forceState(SquireAIState.FISHING_APPROACH);
    }

    /** Overload: start fishing without a specific target (auto-find nearest water). */
    public void startFishing() {
        // Phase 6: auto-discover nearest water target via world scan
        // For now, if no target set, transition state and let tick handle null target gracefully
        this.fishingTimer = 0;
        this.approachTicks = 0;
        this.stuckTicks = 0;
        this.lastApproachDistSq = Double.MAX_VALUE;
        machine.forceState(SquireAIState.FISHING_APPROACH);
    }

    /**
     * Stop fishing — return to IDLE and fire WORK_TASK_COMPLETE.
     * Called by TaskQueue dispatch when next task arrives.
     */
    public void stopFishing() {
        this.waterTarget = null;
        this.fishingTimer = 0;
        squire.getNavigation().stop();
        machine.forceState(SquireAIState.IDLE);
        // Fire task complete event so ChatHandler can notify player
        squire.getSquireBrain().getBus().publish(SquireEvent.WORK_TASK_COMPLETE, squire);
    }

    // ── FSM tick ─────────────────────────────────────────────────────────────

    /**
     * Main tick dispatcher — called from SquireBrain per-tick for fishing states.
     *
     * @param currentState the current FSM state
     */
    public void tick(SquireAIState currentState) {
        switch (currentState) {
            case FISHING_APPROACH -> tickApproach();
            case FISHING_IDLE     -> tickIdle();
            default -> { /* not a fishing state */ }
        }
    }

    // ── Internal FSM ticks ────────────────────────────────────────────────────

    private void tickApproach() {
        if (waterTarget == null) {
            // No target — fall back to IDLE
            machine.forceState(SquireAIState.IDLE);
            return;
        }

        double distSq = squire.distanceToSqr(
                waterTarget.getX() + 0.5,
                waterTarget.getY(),
                waterTarget.getZ() + 0.5);

        double reach = SquireConfig.fishingDurationTicks.get(); // reuse fishingReach from config
        // Use a fixed arrival threshold of 4.0 (within 2 blocks)
        if (distSq <= 4.0) {
            squire.getNavigation().stop();
            approachTicks = 0;
            stuckTicks = 0;
            lastApproachDistSq = Double.MAX_VALUE;
            fishingTimer = 0;
            machine.forceState(SquireAIState.FISHING_IDLE);
            return;
        }

        squire.getNavigation().moveTo(
                waterTarget.getX() + 0.5,
                waterTarget.getY(),
                waterTarget.getZ() + 0.5,
                1.0);

        approachTicks++;
        if (distSq < lastApproachDistSq - 0.1) {
            stuckTicks = 0;
            lastApproachDistSq = distSq;
        } else {
            stuckTicks++;
        }

        if (stuckTicks >= STUCK_TIMEOUT || approachTicks >= MAX_APPROACH_TICKS) {
            if (SquireConfig.activityLogging.get()) {
                LOGGER.debug("[FISH] Can't reach water at {}, giving up", waterTarget.toShortString());
            }
            waterTarget = null;
            machine.forceState(SquireAIState.IDLE);
        }
    }

    private void tickIdle() {
        if (squire.level().isClientSide) return;

        // Check fishing rod still available
        ItemStack rod = findFishingRod();
        if (rod.isEmpty()) {
            if (SquireConfig.activityLogging.get()) {
                LOGGER.debug("[FISH] No fishing rod in inventory — stopping");
            }
            machine.forceState(SquireAIState.IDLE);
            return;
        }

        // Face water target if known
        if (waterTarget != null) {
            squire.getLookControl().setLookAt(
                    waterTarget.getX() + 0.5,
                    waterTarget.getY() + 0.5,
                    waterTarget.getZ() + 0.5);
        }

        // Increment timer; roll loot when duration reached
        fishingTimer++;
        if (fishingTimer >= SquireConfig.fishingDurationTicks.get()) {
            fishingTimer = 0;
            performCatch(rod, (ServerLevel) squire.level());
        }
    }

    private void performCatch(ItemStack rod, ServerLevel level) {
        List<ItemStack> drops = rollFishingLoot(rod, level);

        for (ItemStack drop : drops) {
            // Insert into squire inventory via IItemHandler — no .shrink() or direct slot writes
            var handler = squire.getItemHandler();
            ItemStack remainder = drop.copy();
            for (int i = 0; i < handler.getSlots(); i++) {
                remainder = handler.insertItem(i, remainder, false);
                if (remainder.isEmpty()) break;
            }
            // Overflow drops at squire's feet
            if (!remainder.isEmpty()) {
                squire.spawnAtLocation(remainder);
            }
        }

        // Award fishing XP per catch
        if (!drops.isEmpty() && squire.getProgressionHandler() != null) {
            squire.getProgressionHandler().addFishXP();
        }

        if (SquireConfig.activityLogging.get() && !drops.isEmpty()) {
            LOGGER.debug("[FISH] Caught {} item(s)", drops.size());
        }
    }

    // ── Loot table roll ───────────────────────────────────────────────────────

    /**
     * Roll the vanilla fishing loot table.
     *
     * Uses LootContextParamSets.FISHING (not EMPTY) to ensure correct vanilla probabilities
     * and mopack override support. Requires ORIGIN and TOOL params per RESEARCH.md.
     *
     * Package-private so FishingHandlerTest can call it via rollFishingLoot_forTest().
     *
     * @param rod   the fishing rod ItemStack (used as LootContextParams.TOOL)
     * @param level the server level (used for registry access and ORIGIN)
     * @return list of ItemStacks from the loot roll; empty if rod is absent or level null
     */
    List<ItemStack> rollFishingLoot(ItemStack rod, ServerLevel level) {
        if (rod.isEmpty() || level == null) return List.of();

        try {
            LootTable table = level.getServer()
                    .reloadableRegistries()
                    .getLootTable(BuiltInLootTables.FISHING);
            // Note: BuiltInLootTables.FISHING = minecraft:gameplay/fishing

            LootParams params = new LootParams.Builder(level)
                    .withParameter(LootContextParams.ORIGIN, squire.position())
                    .withParameter(LootContextParams.TOOL, rod)
                    .withOptionalParameter(LootContextParams.THIS_ENTITY, squire)
                    .create(LootContextParamSets.FISHING);

            return table.getRandomItems(params);
        } catch (Exception e) {
            LOGGER.warn("[FISH] Loot table roll failed: {}", e.getMessage());
            return List.of();
        }
    }

    // ── Static test accessor ──────────────────────────────────────────────────

    /**
     * Test-only static accessor for rollFishingLoot().
     *
     * FishingHandlerTest uses this to test the loot roll without constructing a live
     * SquireEntity. The squire reference is null — only rod and level are used.
     *
     * @param rod   fishing rod ItemStack (or EMPTY to test early-out)
     * @param level mock or real ServerLevel
     * @return result of rollFishingLoot with null squire position (Vec3.ZERO)
     */
    static List<ItemStack> rollFishingLoot_forTest(ItemStack rod, ServerLevel level) {
        if (rod.isEmpty() || level == null) return List.of();

        try {
            LootTable table = level.getServer()
                    .reloadableRegistries()
                    .getLootTable(BuiltInLootTables.FISHING);

            LootParams params = new LootParams.Builder(level)
                    .withParameter(LootContextParams.ORIGIN, net.minecraft.world.phys.Vec3.ZERO)
                    .withParameter(LootContextParams.TOOL, rod)
                    .create(LootContextParamSets.FISHING);

            return table.getRandomItems(params);
        } catch (Exception e) {
            // Expected in unit test environment — server registry not available
            return List.of();
        }
    }

    // ── Inventory helpers ─────────────────────────────────────────────────────

    /**
     * Find a fishing rod in equipment slots 0-5.
     *
     * @return the fishing rod ItemStack, or ItemStack.EMPTY if not found
     */
    private ItemStack findFishingRod() {
        var handler = squire.getItemHandler();
        for (int i = 0; i < Math.min(6, handler.getSlots()); i++) {
            ItemStack stack = handler.getStackInSlot(i);
            if (!stack.isEmpty() && stack.getItem() instanceof FishingRodItem) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    // ── Accessor for stopFishing brain reference ───────────────────────────────

    /**
     * Get squire entity reference (used by tests and SquireBrain wiring).
     */
    public SquireEntity getSquire() {
        return squire;
    }
}

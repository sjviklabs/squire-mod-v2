package com.sjviklabs.squire.brain.handler;

import com.sjviklabs.squire.brain.SquireAIState;
import com.sjviklabs.squire.brain.SquireEvent;
import com.sjviklabs.squire.brain.StateController;
import com.sjviklabs.squire.brain.WorkHandler;
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

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;

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
public class FishingHandler implements WorkHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(FishingHandler.class);

    private final SquireEntity squire;
    private final StateController stateController;

    @Nullable private BlockPos waterTarget;
    @Nullable private BlockPos standPos = null;
    private int fishingTimer = 0;
    private int lookTimer = 0;

    private static final int STUCK_TIMEOUT = 100;
    private static final int MAX_APPROACH_TICKS = 200;
    private int approachTicks = 0;
    private double lastApproachDistSq = Double.MAX_VALUE;
    private int stuckTicks = 0;

    // ── Constructor ───────────────────────────────────────────────────────────

    public FishingHandler(SquireEntity squire, StateController stateController) {
        this.squire = squire;
        this.stateController = stateController;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Start fishing at the given water target position.
     * Sets state to FISHING_APPROACH.
     */
    public void startFishing(BlockPos waterPos) {
        // Auto-craft fishing rod if missing (Wave 1.5)
        if (!hasFishingRod()) {
            CraftingHandler crafting = squire.getSquireBrain().getCraftingHandler();
            if (!crafting.tryCraftIfMissing(squire,
                    stack -> stack.getItem() instanceof FishingRodItem, "fishing rod")) {
                return; // No rod and can't craft one — stay IDLE
            }
        }
        this.waterTarget = waterPos;
        this.standPos = findShorePosition(waterPos);
        this.fishingTimer = 0;
        this.lookTimer = 0;
        this.approachTicks = 0;
        this.stuckTicks = 0;
        this.lastApproachDistSq = Double.MAX_VALUE;
        stateController.forceState(SquireAIState.FISHING_APPROACH);
    }

    /** Overload: start fishing without a specific target (auto-find nearest water). */
    public void startFishing() {
        // Auto-craft fishing rod if missing (Wave 1.5)
        if (!hasFishingRod()) {
            CraftingHandler crafting = squire.getSquireBrain().getCraftingHandler();
            if (!crafting.tryCraftIfMissing(squire,
                    stack -> stack.getItem() instanceof FishingRodItem, "fishing rod")) {
                return;
            }
        }
        // Auto-discover nearest water body
        BlockPos waterPos = findNearestWater();
        if (waterPos != null) {
            this.standPos = findShorePosition(waterPos);
            this.waterTarget = waterPos;
        } else {
            this.standPos = null;
            this.waterTarget = null;
        }
        this.fishingTimer = 0;
        this.lookTimer = 0;
        this.approachTicks = 0;
        this.stuckTicks = 0;
        this.lastApproachDistSq = Double.MAX_VALUE;
        stateController.forceState(SquireAIState.FISHING_APPROACH);
    }

    private boolean hasFishingRod() {
        return squire.getMainHandItem().getItem() instanceof FishingRodItem;
    }

    /**
     * Stop fishing — return to IDLE and fire WORK_TASK_COMPLETE.
     * Called by TaskQueue dispatch when next task arrives.
     */
    public void stopFishing() {
        this.waterTarget = null;
        this.standPos = null;
        this.fishingTimer = 0;
        this.lookTimer = 0;
        squire.getNavigation().stop();
        stateController.forceState(SquireAIState.IDLE);
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
            stateController.forceState(SquireAIState.IDLE);
            return;
        }

        // Navigate to shore position if available, otherwise directly toward water
        BlockPos navTarget = (standPos != null) ? standPos : waterTarget;

        double distSq = squire.distanceToSqr(
                navTarget.getX() + 0.5,
                navTarget.getY(),
                navTarget.getZ() + 0.5);

        // Use a fixed arrival threshold of 4.0 (within 2 blocks)
        if (distSq <= 4.0) {
            squire.getNavigation().stop();
            approachTicks = 0;
            stuckTicks = 0;
            lastApproachDistSq = Double.MAX_VALUE;
            fishingTimer = 0;
            lookTimer = 0;
            // Face the water before transitioning to idle
            squire.getLookControl().setLookAt(
                    waterTarget.getX() + 0.5,
                    waterTarget.getY() + 0.5,
                    waterTarget.getZ() + 0.5);
            stateController.forceState(SquireAIState.FISHING_IDLE);
            return;
        }

        squire.getNavigation().moveTo(
                navTarget.getX() + 0.5,
                navTarget.getY(),
                navTarget.getZ() + 0.5,
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
            standPos = null;
            stateController.forceState(SquireAIState.IDLE);
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
            stateController.forceState(SquireAIState.IDLE);
            return;
        }

        // Idle look behavior — mostly face water, occasionally glance around
        lookTimer++;
        int lookCycle = lookTimer % 80; // 4-second cycle
        if (lookCycle >= 60 && lookCycle < 80) {
            // Glance in a pseudo-random direction for 20 ticks (1 second)
            double angle = ((lookTimer / 80) * 137.5) % 360.0; // golden angle for varied directions
            double radians = Math.toRadians(angle);
            squire.getLookControl().setLookAt(
                    squire.getX() + Math.cos(radians) * 5.0,
                    squire.getEyeY(),
                    squire.getZ() + Math.sin(radians) * 5.0);
        } else if (waterTarget != null) {
            // Face water target
            squire.getLookControl().setLookAt(
                    waterTarget.getX() + 0.5,
                    waterTarget.getY() + 0.5,
                    waterTarget.getZ() + 0.5);
        }

        // Arm swing animation at ~80% of fishing duration (simulates casting/reeling)
        int castTick = (int) (SquireConfig.fishingDurationTicks.get() * 0.8);
        if (fishingTimer == castTick) {
            squire.swing(InteractionHand.MAIN_HAND);
        }

        // Increment timer; roll loot when duration reached
        fishingTimer++;
        if (fishingTimer >= SquireConfig.fishingDurationTicks.get()) {
            fishingTimer = 0;
            performCatch(rod, (ServerLevel) squire.level());
        }
    }

    // ── Water discovery & shore positioning ────────────────────────────────

    /**
     * Scan outward in a spiral for the nearest water body meeting minimum size.
     * Runs once on startFishing(), not per-tick.
     */
    @Nullable
    private BlockPos findNearestWater() {
        int radius = SquireConfig.waterScanRadius.get();
        int minSize = SquireConfig.minWaterSize.get();
        BlockPos center = squire.blockPosition();
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;

        // Spiral scan outward — check perimeter at each radius
        for (int r = 1; r <= radius; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.abs(dx) != r && Math.abs(dz) != r) continue; // perimeter only
                    for (int dy = -3; dy <= 3; dy++) {
                        BlockPos check = center.offset(dx, dy, dz);
                        if (squire.level().getFluidState(check).is(FluidTags.WATER)) {
                            int waterCount = countConnectedWater(check, minSize);
                            if (waterCount >= minSize) {
                                double dist = center.distSqr(check);
                                if (dist < bestDist) {
                                    bestDist = dist;
                                    best = check;
                                }
                            }
                        }
                    }
                }
            }
            if (best != null) break; // Found water at this radius, stop expanding
        }

        if (best != null && SquireConfig.activityLogging.get()) {
            LOGGER.debug("[FISH] Auto-discovered water at {}", best.toShortString());
        }
        return best;
    }

    /**
     * Flood-fill count of connected water blocks, capped at limit for performance.
     */
    private int countConnectedWater(BlockPos start, int limit) {
        Set<BlockPos> visited = new HashSet<>();
        Queue<BlockPos> queue = new ArrayDeque<>();
        queue.add(start);
        int count = 0;
        while (!queue.isEmpty() && count < limit) {
            BlockPos pos = queue.poll();
            if (visited.contains(pos)) continue;
            visited.add(pos);
            if (!squire.level().getFluidState(pos).is(FluidTags.WATER)) continue;
            count++;
            // Cardinal neighbors at same Y (surface check)
            queue.add(pos.north());
            queue.add(pos.south());
            queue.add(pos.east());
            queue.add(pos.west());
        }
        return count;
    }

    /**
     * Find a solid shore position adjacent to the water block — air above, solid below.
     * Checks cardinal directions and one block up (shore may be higher than water surface).
     */
    @Nullable
    private BlockPos findShorePosition(BlockPos waterPos) {
        Direction[] dirs = {
            Direction.NORTH, Direction.SOUTH,
            Direction.EAST, Direction.WEST
        };
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;

        for (Direction dir : dirs) {
            BlockPos shore = waterPos.relative(dir);
            // Need air at feet level and solid ground below
            if (squire.level().getBlockState(shore).isAir()
                    && squire.level().getBlockState(shore.below()).isSolid()) {
                double dist = squire.blockPosition().distSqr(shore);
                if (dist < bestDist) {
                    bestDist = dist;
                    best = shore;
                }
            }
            // Also check one block up (shore might be higher than water)
            BlockPos shoreUp = shore.above();
            if (squire.level().getBlockState(shoreUp).isAir()
                    && squire.level().getBlockState(shoreUp.below()).isSolid()) {
                double dist = squire.blockPosition().distSqr(shoreUp);
                if (dist < bestDist) {
                    bestDist = dist;
                    best = shoreUp;
                }
            }
        }

        if (best != null && SquireConfig.activityLogging.get()) {
            LOGGER.debug("[FISH] Shore position at {}", best.toShortString());
        }
        return best;
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
        } catch (RuntimeException e) {
            // LootTable internals can throw IllegalArgumentException, NPE, or registry-related
            // RuntimeExceptions if the loot table is malformed, missing, or parameters invalid.
            // Not a reason to crash the tick — log and return empty drops.
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
        } catch (RuntimeException e) {
            // Expected in unit test environment — server registry not available
            // (IllegalStateException when registries not bootstrapped, NPE on null registry lookup).
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

    // ── WorkHandler interface ────────────────────────────────────────────────

    @Override
    public Set<SquireAIState> ownedStates() {
        return Set.of(SquireAIState.FISHING_APPROACH, SquireAIState.FISHING_IDLE);
    }

    @Override
    public SquireAIState resumeState() {
        return SquireAIState.FISHING_APPROACH;
    }

    @Override
    public boolean hasActiveWork() {
        return waterTarget != null;
    }

    @Override
    public int priority() {
        return 43;
    }
}

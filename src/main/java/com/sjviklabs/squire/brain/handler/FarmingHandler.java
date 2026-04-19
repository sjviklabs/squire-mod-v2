package com.sjviklabs.squire.brain.handler;

import com.sjviklabs.squire.brain.SquireAIState;
import com.sjviklabs.squire.brain.SquireEvent;
import com.sjviklabs.squire.brain.StateController;
import com.sjviklabs.squire.brain.WorkHandler;
import com.sjviklabs.squire.config.SquireConfig;
import com.sjviklabs.squire.entity.SquireEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.Tags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Farming behavior: till dirt, plant seeds, harvest mature crops, replant automatically.
 *
 * FSM cycle: FARM_SCAN → FARM_APPROACH → FARM_WORK → FARM_SCAN (repeat)
 *
 * Key v2 changes from v0.5.0:
 * - Seed detection uses CropBlock instanceof check instead of hardcoded wheat/potato/carrot list
 * - plantable_seeds item tag provides vanilla seed whitelist; modded seeds detected via CropBlock
 * - Inventory access via squire.getItemHandler() (IItemHandler) — no .shrink(), no direct slot writes
 * - Package: brain.handler (not ai.handler)
 * - Ability gate: squire.getLevel() >= 0 (farming available from Servant tier)
 *
 * Y-level contract (see Pitfall 5 in RESEARCH.md):
 *   setArea() cornerA.Y is the farmland/ground level. The scan always reads the block AT that Y,
 *   and plants/tills one block above it (pos.above()). Commands must pass ground-level Y, not
 *   crop-level Y. Passing crop-level Y will cause the scan to check the crop layer as "ground"
 *   and planting will silently fail.
 *
 * WRK-04: Squire farms (tilling + harvesting).
 *
 * Package: com.sjviklabs.squire.brain.handler
 */
public class FarmingHandler implements WorkHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(FarmingHandler.class);

    // Stuck detection constants (proven from v0.5.0)
    private static final int STUCK_TIMEOUT = 100;        // 5 seconds
    private static final int MAX_APPROACH_TICKS = 200;   // 10 seconds

    private final SquireEntity squire;
    private final StateController stateController;

    // Farm area bounds (Y of cornerA = ground/farmland level per Y-level contract)
    @Nullable private BlockPos cornerA;
    @Nullable private BlockPos cornerB;

    // Current work target
    @Nullable private BlockPos currentTarget;
    private FarmAction currentAction;

    // Approach stuck detection
    private int approachTicks = 0;
    private double lastApproachDistSq = Double.MAX_VALUE;
    private int stuckTicks = 0;

    // Work tick countdown
    private int workTicksRemaining = 0;

    // Rescan cooldown: when area is fully grown/planted, wait before rescanning
    private static final int RESCAN_INTERVAL = 100; // 5 seconds
    private int rescanCooldown = 0;

    private enum FarmAction { TILL, PLANT, HARVEST }

    // Internal farm phases — runs WITHIN existing FSM states (FARM_SCAN / FARM_APPROACH)
    private enum FarmPhase {
        SCANNING,    // Looking for work (current FARM_SCAN logic)
        WORKING,     // Approaching and performing action
        PATROL_ROW,  // Walking along crop rows when nothing to do
        INSPECT      // Stopped at a crop, looking down at it
    }
    private FarmPhase farmPhase = FarmPhase.SCANNING;

    // Patrol fields
    private List<BlockPos> patrolWaypoints = new ArrayList<>();
    private int patrolIndex = 0;
    private int inspectTimer = 0;

    // ── Constructor ───────────────────────────────────────────────────────────

    public FarmingHandler(SquireEntity squire, StateController stateController) {
        this.squire = squire;
        this.stateController = stateController;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Set the farming area bounds and begin scanning.
     *
     * Y-level contract: cornerA.Y MUST be the farmland/ground level.
     * The scan uses this Y to find farmland blocks; crops are planted/harvested at Y+1.
     * Passing the crop level (Y+1) instead of ground level will cause silent planting failure.
     *
     * @param cornerA one corner of the farm area (ground level Y required)
     * @param cornerB opposite corner of the farm area (Y ignored, cornerA.Y is used for scan)
     */
    /** Returns true if the farm area corners are set (work can continue). */
    public boolean hasArea() {
        return cornerA != null && cornerB != null;
    }

    public void setArea(BlockPos cornerA, BlockPos cornerB) {
        // v3.1.1 — no auto-craft; squire asks for a hoe and stays IDLE until one is provided
        if (!hasHoe(squire)) {
            com.sjviklabs.squire.entity.ChatHandler.askForTool(squire, "hoe");
            return;
        }

        this.cornerA = cornerA;
        this.cornerB = cornerB;
        this.currentTarget = null;
        this.approachTicks = 0;
        this.stuckTicks = 0;
        this.lastApproachDistSq = Double.MAX_VALUE;
        this.rescanCooldown = 0;
        this.farmPhase = FarmPhase.SCANNING;
        this.patrolWaypoints.clear();
        this.patrolIndex = 0;
        stateController.forceState(SquireAIState.FARM_SCAN);
    }

    /**
     * Main tick dispatcher — called from SquireBrain per-tick for farming states.
     *
     * @param currentState the current FSM state
     */
    public void tick(SquireAIState currentState) {
        switch (currentState) {
            case FARM_SCAN     -> tickScan();
            case FARM_APPROACH -> tickApproach();
            case FARM_WORK     -> tickWork();
            default -> { /* not a farming state */ }
        }
    }

    // ── FSM ticks ─────────────────────────────────────────────────────────────

    /**
     * FARM_SCAN: scan all (x, cornerA.y, z) positions in area.
     * Priority: harvest mature crops first, then till/plant.
     *
     * When no work is found, stays in FARM_SCAN with a cooldown instead of
     * returning to IDLE — crops may still be growing and need harvest later.
     */
    private void tickScan() {
        if (cornerA == null || cornerB == null) {
            stateController.forceState(SquireAIState.IDLE);
            return;
        }
        if (squire.level().isClientSide) return;

        // Rescan cooldown: wait before scanning again after finding nothing
        if (rescanCooldown > 0) {
            rescanCooldown--;
            return;
        }

        BlockPos next = findNextTask();
        if (next != null) {
            farmPhase = FarmPhase.WORKING;
            currentTarget = next;
            approachTicks = 0;
            stuckTicks = 0;
            lastApproachDistSq = Double.MAX_VALUE;
            workTicksRemaining = SquireConfig.farmingTickRate.get();
            stateController.forceState(SquireAIState.FARM_APPROACH);
            return;
        }

        // Nothing to do right now — crops still growing.
        // If idle patrol is enabled, walk the rows instead of standing still.
        if (SquireConfig.idlePatrolEnabled.get()) {
            generatePatrolWaypoints();
            if (!patrolWaypoints.isEmpty()) {
                farmPhase = FarmPhase.PATROL_ROW;
                patrolIndex = 0;
                if (SquireConfig.activityLogging.get()) {
                    LOGGER.debug("[FARM] No work found — starting patrol ({} waypoints)", patrolWaypoints.size());
                }
                // Use FARM_APPROACH state to navigate to first waypoint
                stateController.forceState(SquireAIState.FARM_APPROACH);
                return;
            }
        }

        // Fallback: no patrol waypoints or patrol disabled — wait and rescan.
        rescanCooldown = RESCAN_INTERVAL;
        if (SquireConfig.activityLogging.get()) {
            LOGGER.debug("[FARM] Area scan complete — no work found, rescanning in {} ticks", RESCAN_INTERVAL);
        }
    }

    /**
     * FARM_APPROACH: pathfind toward currentTarget within farmingReach.
     * Also handles PATROL_ROW and INSPECT phases when no real work exists.
     * Stuck detection: if no movement improvement for STUCK_TIMEOUT ticks, skip to next task.
     */
    private void tickApproach() {
        // Handle patrol/inspect phases before normal approach logic
        if (farmPhase == FarmPhase.PATROL_ROW) {
            tickPatrol();
            return;
        }
        if (farmPhase == FarmPhase.INSPECT) {
            tickInspect();
            return;
        }

        if (currentTarget == null) {
            stateController.forceState(SquireAIState.FARM_SCAN);
            return;
        }

        double reach = SquireConfig.farmingReach.get();
        double distSq = squire.distanceToSqr(
                currentTarget.getX() + 0.5,
                currentTarget.getY(),
                currentTarget.getZ() + 0.5);

        if (distSq <= reach * reach) {
            // In range — begin work
            approachTicks = 0;
            stuckTicks = 0;
            lastApproachDistSq = Double.MAX_VALUE;
            workTicksRemaining = SquireConfig.farmingTickRate.get();
            stateController.forceState(SquireAIState.FARM_WORK);
            return;
        }

        squire.getNavigation().moveTo(
                currentTarget.getX() + 0.5,
                currentTarget.getY(),
                currentTarget.getZ() + 0.5,
                1.0);

        // Stuck detection (same pattern as MiningHandler)
        approachTicks++;
        if (distSq < lastApproachDistSq - 0.1) {
            stuckTicks = 0;
            lastApproachDistSq = distSq;
        } else {
            stuckTicks++;
        }

        if (stuckTicks >= STUCK_TIMEOUT || approachTicks >= MAX_APPROACH_TICKS) {
            if (SquireConfig.activityLogging.get()) {
                LOGGER.debug("[FARM] Can't reach {} — skipping to next task", currentTarget.toShortString());
            }
            currentTarget = null;
            approachTicks = 0;
            stuckTicks = 0;
            lastApproachDistSq = Double.MAX_VALUE;
            stateController.forceState(SquireAIState.FARM_SCAN);
        }
    }

    /**
     * FARM_WORK: perform the action (till, plant, or harvest) at currentTarget.
     * Waits farmingTickRate ticks before acting (windup animation time).
     */
    private void tickWork() {
        if (currentTarget == null) {
            stateController.forceState(SquireAIState.IDLE);
            return;
        }
        if (squire.level().isClientSide) return;

        // Face the target block
        squire.getLookControl().setLookAt(
                currentTarget.getX() + 0.5,
                currentTarget.getY() + 0.5,
                currentTarget.getZ() + 0.5);

        workTicksRemaining--;
        if (workTicksRemaining > 0) return;

        ServerLevel level = (ServerLevel) squire.level();
        boolean success = switch (currentAction) {
            case TILL    -> performTill(level, currentTarget);
            case PLANT   -> performPlant(level, currentTarget);
            case HARVEST -> performHarvest(level, currentTarget);
        };

        if (success) {
            squire.swing(InteractionHand.MAIN_HAND);
            if (SquireConfig.activityLogging.get()) {
                LOGGER.debug("[FARM] {} at {}", currentAction, currentTarget.toShortString());
            }
        }

        currentTarget = null;
        farmPhase = FarmPhase.SCANNING;
        stateController.forceState(SquireAIState.FARM_SCAN);
    }

    // ── Area scan ─────────────────────────────────────────────────────────────

    /**
     * Scan the farm area for the next block needing work.
     *
     * Scans at cornerA.Y (ground/farmland level) per the Y-level contract.
     * Priority: harvest mature crops > till dirt > plant seeds.
     *
     * @return target BlockPos, or null if nothing to do
     */
    @Nullable
    private BlockPos findNextTask() {
        int minX = Math.min(cornerA.getX(), cornerB.getX());
        int maxX = Math.max(cornerA.getX(), cornerB.getX());
        int minZ = Math.min(cornerA.getZ(), cornerB.getZ());
        int maxZ = Math.max(cornerA.getZ(), cornerB.getZ());
        int y = cornerA.getY();  // ground/farmland level (Y-level contract)

        List<BlockPos> tillable = new ArrayList<>();
        List<BlockPos> plantable = new ArrayList<>();
        BlockPos closestHarvest = null;
        double closestHarvestDist = Double.MAX_VALUE;
        boolean hasSeeds = hasSeedsInInventory(); // hoisted — one scan, not N

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                BlockPos groundPos = new BlockPos(x, y, z);
                BlockPos cropPos = groundPos.above();

                BlockState groundState = squire.level().getBlockState(groundPos);
                BlockState cropState = squire.level().getBlockState(cropPos);

                // Check crop layer (one above ground) for mature crops — highest priority
                if (cropState.getBlock() instanceof CropBlock crop && crop.isMaxAge(cropState)) {
                    double dist = squire.distanceToSqr(x + 0.5, y, z + 0.5);
                    if (dist < closestHarvestDist) {
                        closestHarvestDist = dist;
                        closestHarvest = cropPos;
                        currentAction = FarmAction.HARVEST;
                    }
                    continue;
                }

                // Ground level is tillable (dirt/grass → farmland)
                if (isTillable(groundState)) {
                    tillable.add(groundPos);
                    continue;
                }

                // Farmland with air above — plantable if squire has seeds
                if (groundState.getBlock() instanceof FarmBlock) {
                    if (cropState.isAir() && hasSeeds) {
                        plantable.add(groundPos);
                    }
                }
            }
        }

        if (closestHarvest != null) return closestHarvest;

        if (!tillable.isEmpty() && hasHoeInInventory()) {
            currentAction = FarmAction.TILL;
            return findClosest(tillable);
        }

        if (!plantable.isEmpty()) {
            currentAction = FarmAction.PLANT;
            return findClosest(plantable);
        }

        return null;
    }

    // ── Farm actions ──────────────────────────────────────────────────────────

    private boolean performTill(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!isTillable(state)) return false;

        level.setBlock(pos, Blocks.FARMLAND.defaultBlockState(), Block.UPDATE_ALL);
        level.playSound(null, pos, SoundEvents.HOE_TILL, SoundSource.BLOCKS, 1.0F, 1.0F);
        return true;
    }

    private boolean performPlant(ServerLevel level, BlockPos pos) {
        // pos is the farmland block; plant above it
        BlockState farmlandState = level.getBlockState(pos);
        if (!(farmlandState.getBlock() instanceof FarmBlock)) return false;

        BlockPos plantPos = pos.above();
        if (!level.getBlockState(plantPos).isAir()) return false;

        // Find a seed in inventory using CropBlock instanceof check (not hardcoded list)
        ItemStack seed = findPlantableSeed();
        if (seed.isEmpty()) return false;

        // Derive the crop block from the seed's BlockItem
        Block cropBlock = getCropBlockForSeed(seed);
        if (cropBlock == null) return false;

        level.setBlock(plantPos, cropBlock.defaultBlockState(), Block.UPDATE_ALL);
        // Consume one seed via IItemHandler extractItem — no .shrink()
        consumeOneSeed(seed);
        level.playSound(null, plantPos, SoundEvents.CROP_PLANTED, SoundSource.BLOCKS, 1.0F, 1.0F);
        return true;
    }

    private boolean performHarvest(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof CropBlock crop) || !crop.isMaxAge(state)) return false;

        // Break the crop — drops items naturally into the world; squire picks up via ItemHandler
        level.destroyBlock(pos, true, squire);

        // Award harvest XP
        if (squire.getProgressionHandler() != null) {
            squire.getProgressionHandler().addHarvestXP();
        }

        // Auto-replant: immediately plant on the farmland below if enabled
        if (SquireConfig.autoReplant.get()) {
            BlockPos farmlandPos = pos.below();
            performPlant(level, farmlandPos);
        }

        return true;
    }

    // ── Patrol / Inspect ───────────────────────────────────────────────────────

    /**
     * PATROL_ROW tick: walk along crop rows when no real farm work exists.
     * Uses FARM_APPROACH FSM state for movement. Every 3rd waypoint, pause to inspect.
     */
    private void tickPatrol() {
        if (patrolWaypoints.isEmpty() || patrolIndex >= patrolWaypoints.size()) {
            // Patrol loop complete — rescan for real work
            farmPhase = FarmPhase.SCANNING;
            rescanCooldown = 0; // immediate rescan after full patrol
            stateController.forceState(SquireAIState.FARM_SCAN);
            if (SquireConfig.activityLogging.get()) {
                LOGGER.debug("[FARM] Patrol complete — returning to scan");
            }
            return;
        }

        // Check if real work appeared mid-patrol (opportunistic rescan)
        BlockPos task = findNextTask();
        if (task != null) {
            farmPhase = FarmPhase.WORKING;
            currentTarget = task;
            approachTicks = 0;
            stuckTicks = 0;
            lastApproachDistSq = Double.MAX_VALUE;
            workTicksRemaining = SquireConfig.farmingTickRate.get();
            if (SquireConfig.activityLogging.get()) {
                LOGGER.debug("[FARM] Found work during patrol — switching to {}", currentAction);
            }
            // Stay in FARM_APPROACH; normal approach logic handles it next tick
            return;
        }

        BlockPos waypoint = patrolWaypoints.get(patrolIndex);
        double distSq = squire.distanceToSqr(
                waypoint.getX() + 0.5,
                waypoint.getY(),
                waypoint.getZ() + 0.5);

        if (distSq <= 4.0) { // within 2 blocks
            patrolIndex++;
            // Every 3rd waypoint, stop and inspect
            if (patrolIndex % 3 == 0 && patrolIndex < patrolWaypoints.size()) {
                farmPhase = FarmPhase.INSPECT;
                inspectTimer = SquireConfig.inspectDurationTicks.get();
                if (SquireConfig.activityLogging.get()) {
                    LOGGER.debug("[FARM] Inspecting crops at waypoint {}", patrolIndex);
                }
            }
            return;
        }

        // Navigate toward current waypoint
        squire.getNavigation().moveTo(
                waypoint.getX() + 0.5,
                waypoint.getY(),
                waypoint.getZ() + 0.5,
                0.8); // slower patrol speed — looks deliberate, not frantic
    }

    /**
     * INSPECT tick: squire stops and looks down at nearby crops.
     * When timer expires, returns to PATROL_ROW and continues walking.
     */
    private void tickInspect() {
        inspectTimer--;

        // Look down at the nearest crop block (ground level + 1)
        if (cornerA != null) {
            int y = cornerA.getY(); // ground level
            BlockPos feetPos = squire.blockPosition();
            BlockPos cropLookTarget = new BlockPos(feetPos.getX(), y + 1, feetPos.getZ());
            squire.getLookControl().setLookAt(
                    cropLookTarget.getX() + 0.5,
                    cropLookTarget.getY(),
                    cropLookTarget.getZ() + 0.5);
        }

        // Stop moving while inspecting
        squire.getNavigation().stop();

        if (inspectTimer <= 0) {
            farmPhase = FarmPhase.PATROL_ROW;
            if (SquireConfig.activityLogging.get()) {
                LOGGER.debug("[FARM] Inspect complete — resuming patrol at waypoint {}", patrolIndex);
            }
        }
    }

    /**
     * Generate patrol waypoints along the farm area rows.
     * Walks Z-rows at ground level, alternating between min-X and max-X edges.
     * Every other row to avoid overly dense waypoints.
     */
    private void generatePatrolWaypoints() {
        patrolWaypoints.clear();
        patrolIndex = 0;
        if (cornerA == null || cornerB == null) return;

        int minX = Math.min(cornerA.getX(), cornerB.getX());
        int maxX = Math.max(cornerA.getX(), cornerB.getX());
        int minZ = Math.min(cornerA.getZ(), cornerB.getZ());
        int maxZ = Math.max(cornerA.getZ(), cornerB.getZ());
        int y = cornerA.getY(); // ground level

        // Walk along each Z row (every other row to avoid dense waypoints)
        for (int z = minZ; z <= maxZ; z += 2) {
            patrolWaypoints.add(new BlockPos(minX, y, z));
            patrolWaypoints.add(new BlockPos(maxX, y, z));
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static boolean isTillable(BlockState state) {
        Block block = state.getBlock();
        return block == Blocks.DIRT
                || block == Blocks.GRASS_BLOCK
                || block == Blocks.DIRT_PATH
                || block == Blocks.COARSE_DIRT;
    }

    /**
     * Check if the squire has a hoe equipped in mainhand or anywhere in inventory.
     * Used by setArea() to decide whether auto-craft is needed.
     */
    private boolean hasHoe(SquireEntity squire) {
        if (squire.getMainHandItem().getItem() instanceof HoeItem) return true;
        var handler = squire.getItemHandler();
        for (int i = 0; i < handler.getSlots(); i++) {
            if (handler.getStackInSlot(i).getItem() instanceof HoeItem) return true;
        }
        return false;
    }

    private boolean hasHoeInInventory() {
        var handler = squire.getItemHandler();
        // Check main hand first (equipped hoe)
        if (squire.getMainHandItem().getItem() instanceof HoeItem) return true;
        for (int i = 0; i < handler.getSlots(); i++) {
            if (handler.getStackInSlot(i).getItem() instanceof HoeItem) return true;
        }
        return false;
    }

    private boolean hasSeedsInInventory() {
        return !findPlantableSeed().isEmpty();
    }

    /**
     * Find a plantable seed in squire's inventory.
     *
     * Seed detection uses CropBlock instanceof check — not a hardcoded item list.
     * This handles vanilla and modded crops uniformly (open question 3, RESEARCH.md).
     *
     * A seed is plantable if:
     *   1. It is a BlockItem, AND
     *   2. Its block is a CropBlock
     *
     * This covers: wheat seeds (Blocks.WHEAT), potatoes (Blocks.POTATOES),
     * carrots (Blocks.CARROTS), beetroot seeds (Blocks.BEETROOTS), melon seeds,
     * pumpkin seeds, and any modded seeds that extend CropBlock.
     *
     * @return the first plantable seed found, or ItemStack.EMPTY
     */
    private ItemStack findPlantableSeed() {
        var handler = squire.getItemHandler();
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack stack = handler.getStackInSlot(i);
            if (isPlantableSeed(stack)) return stack;
        }
        return ItemStack.EMPTY;
    }

    /**
     * Check if an ItemStack is a plantable seed.
     *
     * Uses CropBlock instanceof check (v2 improvement over v0.5.0 hardcoded list).
     * Handles all vanilla and modded crops that extend CropBlock without enumerating them.
     */
    private static boolean isPlantableSeed(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (!(stack.getItem() instanceof BlockItem blockItem)) return false;
        return blockItem.getBlock() instanceof CropBlock;
    }

    /**
     * Derive the CropBlock to place from a seed ItemStack.
     *
     * Uses BlockItem.getBlock() — no hardcoded item→block mapping needed.
     * Works for vanilla seeds and any modded CropBlock extension automatically.
     *
     * @param seed a plantable seed ItemStack (caller must ensure isPlantableSeed is true)
     * @return the Block to place, or null if seed is not a BlockItem with a CropBlock
     */
    @Nullable
    private static Block getCropBlockForSeed(ItemStack seed) {
        if (!(seed.getItem() instanceof BlockItem blockItem)) return null;
        Block block = blockItem.getBlock();
        return block instanceof CropBlock ? block : null;
    }

    /**
     * Consume one seed from the squire inventory via IItemHandler.
     *
     * Finds the slot containing the given seed stack and extracts 1 via extractItem.
     * Never calls .shrink() directly on the stack.
     */
    private void consumeOneSeed(ItemStack seed) {
        var handler = squire.getItemHandler();
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack slot = handler.getStackInSlot(i);
            if (!slot.isEmpty() && ItemStack.isSameItemSameComponents(slot, seed)) {
                handler.extractItem(i, 1, false);
                return;
            }
        }
    }

    /**
     * Find the closest BlockPos to the squire from a list of candidates.
     *
     * @param positions list of candidate positions
     * @return closest position, or null if list is empty
     */
    @Nullable
    private BlockPos findClosest(List<BlockPos> positions) {
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos pos : positions) {
            double dist = squire.distanceToSqr(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
            if (dist < bestDist) {
                bestDist = dist;
                best = pos;
            }
        }
        return best;
    }

    // ── WorkHandler interface ────────────────────────────────────────────────

    @Override
    public Set<SquireAIState> ownedStates() {
        return Set.of(SquireAIState.FARM_SCAN, SquireAIState.FARM_APPROACH, SquireAIState.FARM_WORK);
    }

    @Override
    public SquireAIState resumeState() {
        return SquireAIState.FARM_SCAN;
    }

    @Override
    public boolean hasActiveWork() {
        return hasArea();
    }

    @Override
    public int priority() {
        return 42;
    }
}

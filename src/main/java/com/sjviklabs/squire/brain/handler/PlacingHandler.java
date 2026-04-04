package com.sjviklabs.squire.brain.handler;

import com.sjviklabs.squire.brain.SquireAIState;
import com.sjviklabs.squire.brain.SquireEvent;
import com.sjviklabs.squire.brain.TickRateStateMachine;
import com.sjviklabs.squire.config.SquireConfig;
import com.sjviklabs.squire.entity.SquireEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.neoforged.neoforge.items.IItemHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

/**
 * Handles block placement from squire inventory at a target position.
 *
 * Flow: setTarget(pos, blockItem) → machine.forceState(PLACING_APPROACH) → PLACING_BLOCK → IDLE
 *
 * Key design: slot index is cached at setTarget() time via a single inventory scan.
 * No per-tick scan — the slot is re-validated in PLACING_BLOCK but only confirmed,
 * not re-searched (extractItem confirms emptiness on success).
 *
 * v0.5.0 adaptations:
 * - squire.getSquireInventory() → squire.getItemHandler() (IItemHandler)
 * - inventory.removeItem(slot, 1) → getItemHandler().extractItem(inventorySlot, 1, false)
 * - SquireAIState imported from brain package (not ai.statemachine)
 * - Constructor takes TickRateStateMachine for state transitions
 *
 * WRK-06: Squire places a targeted block from its inventory at a specified position on command.
 */
public class PlacingHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(PlacingHandler.class);

    private final SquireEntity squire;
    private final TickRateStateMachine machine;

    @Nullable private BlockPos targetPos;
    @Nullable private Item targetBlockItem;
    /** Cached slot index — set once in setTarget(), re-validated in PLACING_BLOCK. */
    private int inventorySlot = -1;

    // Stuck detection state
    private int approachTicks;
    private double lastApproachDistSq;
    private int stuckTicks;
    private int repositionAttempts;

    // Constants
    private static final int STUCK_TIMEOUT = 100;
    private static final int MAX_APPROACH_TICKS = 200;
    static final int MAX_REPOSITION_ATTEMPTS = 3;

    // ── Constructor ───────────────────────────────────────────────────────────

    public PlacingHandler(SquireEntity squire, TickRateStateMachine machine) {
        this.squire = squire;
        this.machine = machine;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Set the target position and block item to place.
     *
     * Scans squire inventory once and caches the slot index.
     * If the item is not found: logs a warning and returns without changing state.
     * If found: transitions to PLACING_APPROACH.
     *
     * @param pos       world position where the block should be placed
     * @param blockItem the block item to place (must be a BlockItem)
     */
    public void setTarget(BlockPos pos, Item blockItem) {
        if (pos == null || blockItem == null) return;
        if (!(blockItem instanceof BlockItem)) {
            LOGGER.warn("[Squire] setTarget: {} is not a BlockItem, ignoring", blockItem);
            return;
        }

        int slot = findItemInInventory(blockItem);
        if (slot < 0) {
            LOGGER.warn("[Squire] setTarget: {} not found in inventory, cannot place", blockItem);
            return;
        }

        this.targetPos = pos;
        this.targetBlockItem = blockItem;
        this.inventorySlot = slot;

        this.approachTicks = 0;
        this.stuckTicks = 0;
        this.repositionAttempts = 0;
        this.lastApproachDistSq = Double.MAX_VALUE;

        machine.forceState(SquireAIState.PLACING_APPROACH);
    }

    /**
     * Main FSM tick dispatcher — call from SquireBrain per-tick for placing states.
     */
    public void tick(SquireAIState currentState) {
        if (currentState == SquireAIState.PLACING_APPROACH) {
            tickApproach();
        } else if (currentState == SquireAIState.PLACING_BLOCK) {
            tickPlace();
        }
    }

    /** Clear target and reset all state. */
    public void clearTarget() {
        targetPos = null;
        targetBlockItem = null;
        inventorySlot = -1;
        approachTicks = 0;
        stuckTicks = 0;
        repositionAttempts = 0;
        lastApproachDistSq = Double.MAX_VALUE;
    }

    public boolean hasTarget() {
        return targetPos != null && targetBlockItem != null && inventorySlot >= 0;
    }

    @Nullable
    public BlockPos getTargetPos() {
        return targetPos;
    }

    // ── FSM tick implementations ──────────────────────────────────────────────

    /**
     * PLACING_APPROACH tick: pathfind toward target position.
     * Transitions to PLACING_BLOCK on arrival, IDLE on failure.
     */
    private void tickApproach() {
        if (targetPos == null) {
            machine.forceState(SquireAIState.IDLE);
            return;
        }

        // If block is already there, task is complete (someone else placed it)
        if (!squire.level().isEmptyBlock(targetPos)) {
            if (SquireConfig.activityLogging.get()) {
                LOGGER.debug("[Squire] PLACING_APPROACH: target {} occupied, task complete", targetPos.toShortString());
            }
            clearTarget();
            machine.forceState(SquireAIState.IDLE);
            fireTaskComplete();
            return;
        }

        // Look at and path toward target
        squire.getLookControl().setLookAt(
                targetPos.getX() + 0.5, targetPos.getY() + 0.5, targetPos.getZ() + 0.5);
        squire.getNavigation().moveTo(
                targetPos.getX() + 0.5, targetPos.getY(), targetPos.getZ() + 0.5, 1.0D);

        if (isInRange()) {
            squire.getNavigation().stop();
            machine.forceState(SquireAIState.PLACING_BLOCK);
            return;
        }

        // Stuck detection
        approachTicks++;
        double distSq = squire.distanceToSqr(
                targetPos.getX() + 0.5, targetPos.getY() + 0.5, targetPos.getZ() + 0.5);

        if (distSq < lastApproachDistSq - 0.1) {
            stuckTicks = 0;
            lastApproachDistSq = distSq;
        } else {
            stuckTicks++;
        }

        if (stuckTicks >= STUCK_TIMEOUT || approachTicks >= MAX_APPROACH_TICKS) {
            repositionAttempts++;

            if (repositionAttempts <= MAX_REPOSITION_ATTEMPTS) {
                BlockPos reposition = findApproachPosition(targetPos, repositionAttempts);
                if (reposition != null) {
                    if (SquireConfig.activityLogging.get()) {
                        LOGGER.debug("[Squire] Repositioning to reach {} (attempt {})",
                                targetPos.toShortString(), repositionAttempts);
                    }
                    approachTicks = 0;
                    stuckTicks = 0;
                    lastApproachDistSq = Double.MAX_VALUE;
                    squire.getNavigation().moveTo(
                            reposition.getX() + 0.5, reposition.getY(), reposition.getZ() + 0.5, 1.0D);
                    return;
                }
            }

            // All reposition attempts exhausted
            if (SquireConfig.activityLogging.get()) {
                LOGGER.debug("[Squire] Can't reach placing target {} after {} attempts, aborting",
                        targetPos.toShortString(), repositionAttempts);
            }
            clearTarget();
            machine.forceState(SquireAIState.IDLE);
        }
    }

    /**
     * PLACING_BLOCK tick: place the block and consume one item from inventory.
     *
     * Re-validates: target is still air, item is still in cached slot.
     * If target occupied: task complete (block placed, possibly by someone else).
     * If item missing from cached slot: re-scan once; if still missing, abort.
     */
    private void tickPlace() {
        if (targetPos == null) {
            machine.forceState(SquireAIState.IDLE);
            return;
        }
        if (!(squire.level() instanceof ServerLevel serverLevel)) {
            machine.forceState(SquireAIState.IDLE);
            return;
        }

        // If target is occupied, task complete — block is placed (even if not by squire)
        if (!serverLevel.isEmptyBlock(targetPos)) {
            if (SquireConfig.activityLogging.get()) {
                LOGGER.debug("[Squire] PLACING_BLOCK: target {} occupied, task complete", targetPos.toShortString());
            }
            clearTarget();
            machine.forceState(SquireAIState.IDLE);
            fireTaskComplete();
            return;
        }

        // Verify item still in cached slot
        IItemHandler inv = squire.getItemHandler();
        ItemStack stack = inv.getStackInSlot(inventorySlot);
        if (stack.isEmpty() || stack.getItem() != targetBlockItem) {
            // Cached slot changed — re-scan once
            int newSlot = findItemInInventory(targetBlockItem);
            if (newSlot < 0) {
                LOGGER.warn("[Squire] PLACING_BLOCK: {} no longer in inventory, aborting", targetBlockItem);
                clearTarget();
                machine.forceState(SquireAIState.IDLE);
                return;
            }
            inventorySlot = newSlot;
            stack = inv.getStackInSlot(inventorySlot);
        }

        // Place the block
        BlockItem blockItem = (BlockItem) targetBlockItem;
        Block block = blockItem.getBlock();
        serverLevel.setBlock(targetPos, block.defaultBlockState(), Block.UPDATE_ALL);

        // Play block place sound
        SoundType soundType = block.defaultBlockState().getSoundType();
        serverLevel.playSound(null, targetPos, soundType.getPlaceSound(),
                SoundSource.BLOCKS, 1.0F, soundType.getPitch());

        // Arm swing animation
        squire.swing(InteractionHand.MAIN_HAND);

        // Consume one item via IItemHandler (no .shrink())
        inv.extractItem(inventorySlot, 1, false);

        if (SquireConfig.activityLogging.get()) {
            LOGGER.debug("[Squire] Placed {} at {}", block.getName().getString(), targetPos.toShortString());
        }

        clearTarget();
        machine.forceState(SquireAIState.IDLE);
        fireTaskComplete();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Scan inventory for the first non-empty slot containing the given item. Returns -1 if not found. */
    private int findItemInInventory(Item item) {
        if (item == null) return -1;
        IItemHandler inv = squire.getItemHandler();
        for (int i = 0; i < inv.getSlots(); i++) {
            ItemStack stack = inv.getStackInSlot(i);
            if (!stack.isEmpty() && stack.getItem() == item) {
                return i;
            }
        }
        return -1;
    }

    /** Whether squire is within placingReach of the target position. */
    private boolean isInRange() {
        if (targetPos == null) return false;
        double reach = SquireConfig.placingReach.get();
        return squire.distanceToSqr(
                targetPos.getX() + 0.5,
                targetPos.getY() + 0.5,
                targetPos.getZ() + 0.5) <= reach * reach;
    }

    private void fireTaskComplete() {
        if (squire.getSquireBrain() != null) {
            squire.getSquireBrain().getBus().publish(SquireEvent.WORK_TASK_COMPLETE, squire);
        }
    }

    /**
     * Find an alternative approach position for a target block.
     * Tries cardinal directions and diagonals; rotates starting offset by attempt number.
     */
    @Nullable
    private BlockPos findApproachPosition(BlockPos target, int attempt) {
        int[][] offsets = {
                {2, 0}, {-2, 0}, {0, 2}, {0, -2},
                {2, 2}, {-2, 2}, {2, -2}, {-2, -2},
                {3, 0}, {0, 3}, {-3, 0}, {0, -3},
        };

        int startIdx = (attempt - 1) * 4;

        for (int i = 0; i < offsets.length; i++) {
            int idx = (startIdx + i) % offsets.length;
            int dx = offsets[idx][0];
            int dz = offsets[idx][1];

            for (int dy = 0; dy >= -2; dy--) {
                BlockPos candidate = target.offset(dx, dy, dz);
                net.minecraft.world.level.block.state.BlockState at    = squire.level().getBlockState(candidate);
                net.minecraft.world.level.block.state.BlockState above  = squire.level().getBlockState(candidate.above());
                net.minecraft.world.level.block.state.BlockState below  = squire.level().getBlockState(candidate.below());

                if (below.isSolid() && at.isAir() && above.isAir()) {
                    return candidate;
                }
            }
        }
        return null;
    }
}

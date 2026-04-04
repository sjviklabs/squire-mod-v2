package com.sjviklabs.squire.brain.handler;

import com.sjviklabs.squire.brain.SquireAIState;
import com.sjviklabs.squire.brain.SquireEvent;
import com.sjviklabs.squire.brain.TickRateStateMachine;
import com.sjviklabs.squire.config.SquireConfig;
import com.sjviklabs.squire.entity.SquireEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

/**
 * Handles chest deposit and withdraw operations.
 *
 * Flow (deposit): setDepositTarget(pos) → machine.forceState(CHEST_APPROACH) → CHEST_INTERACT → IDLE
 * Flow (withdraw): setWithdrawTarget(pos, item, amount) → CHEST_APPROACH → CHEST_INTERACT → IDLE
 *
 * Container interaction uses dual-path access:
 *   1. BaseContainerBlockEntity (vanilla chests, barrels, hoppers, shulker boxes)
 *   2. Capabilities.ItemHandler.BLOCK fallback (modded storage: MineColonies, RS, AE2, etc.)
 * This pattern is required for correct behavior in modpack environments (see RESEARCH.md pitfall 6).
 *
 * Ability gate: squire.getLevel() >= SquireConfig.chestAbilityMinLevel.get()
 * TODO Phase 4: replace level gate with progression ability system once wired.
 *
 * v0.5.0 adaptations:
 * - squire.getSquireInventory() → squire.getItemHandler() (IItemHandler)
 * - depositItems/withdrawItems rewritten to use only IItemHandler API (no .shrink(), .grow(), .setItem())
 * - SquireAbilities.hasChestDeposit() → level gate via SquireConfig.chestAbilityMinLevel
 * - MineColoniesCompat.isWarehouse() removed — deferred to Phase 8 compat layer
 * - ChestAction enum renamed to Mode for clarity; STORE→DEPOSIT, FETCH→WITHDRAW
 *
 * WRK-07: Squire interacts with containers (deposit/withdraw) with vanilla + modded fallback.
 */
public class ChestHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChestHandler.class);

    public enum Mode { DEPOSIT, WITHDRAW }

    private final SquireEntity squire;
    private final TickRateStateMachine machine;

    @Nullable private BlockPos targetPos;
    @Nullable private Mode mode;
    @Nullable private Item requestedItem;   // withdraw only
    private int requestedAmount;            // withdraw only

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

    public ChestHandler(SquireEntity squire, TickRateStateMachine machine) {
        this.squire = squire;
        this.machine = machine;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Set a chest deposit target and transition to CHEST_APPROACH.
     *
     * Ability gate: squire must be at or above chestAbilityMinLevel.
     * If gate fails: logs warning and returns without state change.
     */
    public void setDepositTarget(BlockPos chestPos) {
        if (!checkAbilityGate()) return;
        if (chestPos == null) return;

        this.targetPos = chestPos;
        this.mode = Mode.DEPOSIT;
        this.requestedItem = null;
        this.requestedAmount = 0;
        resetApproachState();

        machine.forceState(SquireAIState.CHEST_APPROACH);
    }

    /**
     * Set a chest withdraw target and transition to CHEST_APPROACH.
     *
     * Ability gate: squire must be at or above chestAbilityMinLevel.
     * If gate fails: logs warning and returns without state change.
     *
     * @param chestPos      position of the container block
     * @param requestedItem item to withdraw; null = withdraw all available items
     * @param amount        maximum number of items to withdraw (ignored if requestedItem is null)
     */
    public void setWithdrawTarget(BlockPos chestPos, @Nullable Item requestedItem, int amount) {
        if (!checkAbilityGate()) return;
        if (chestPos == null) return;

        this.targetPos = chestPos;
        this.mode = Mode.WITHDRAW;
        this.requestedItem = requestedItem;
        this.requestedAmount = Math.max(1, amount);
        resetApproachState();

        machine.forceState(SquireAIState.CHEST_APPROACH);
    }

    /**
     * Main FSM tick dispatcher — call from SquireBrain per-tick for chest states.
     */
    public void tick(SquireAIState currentState) {
        if (currentState == SquireAIState.CHEST_APPROACH) {
            tickApproach();
        } else if (currentState == SquireAIState.CHEST_INTERACT) {
            tickInteract();
        }
    }

    /** Clear target and reset all state. */
    public void clearTarget() {
        targetPos = null;
        mode = null;
        requestedItem = null;
        requestedAmount = 0;
        resetApproachState();
    }

    public boolean hasTarget() {
        return targetPos != null && mode != null;
    }

    @Nullable
    public BlockPos getTargetPos() {
        return targetPos;
    }

    // ── FSM tick implementations ──────────────────────────────────────────────

    /**
     * CHEST_APPROACH tick: pathfind to within chestReach of target.
     * Transitions to CHEST_INTERACT on arrival, IDLE on failure.
     */
    private void tickApproach() {
        if (targetPos == null) {
            machine.forceState(SquireAIState.IDLE);
            return;
        }

        // Validate the block entity still exists
        if (squire.level().getBlockEntity(targetPos) == null) {
            LOGGER.warn("[Squire] CHEST_APPROACH: no block entity at {}, aborting", targetPos.toShortString());
            clearTarget();
            machine.forceState(SquireAIState.IDLE);
            return;
        }

        // Look at and path toward target
        squire.getLookControl().setLookAt(
                targetPos.getX() + 0.5, targetPos.getY() + 0.5, targetPos.getZ() + 0.5);
        squire.getNavigation().moveTo(
                targetPos.getX() + 0.5, targetPos.getY(), targetPos.getZ() + 0.5, 1.0D);

        if (isInRange()) {
            squire.getNavigation().stop();
            squire.playSound(SoundEvents.CHEST_OPEN, 0.5F, 1.0F);
            machine.forceState(SquireAIState.CHEST_INTERACT);
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
                        LOGGER.debug("[Squire] Repositioning to reach chest {} (attempt {})",
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
                LOGGER.debug("[Squire] Can't reach chest {} after {} attempts, aborting",
                        targetPos.toShortString(), repositionAttempts);
            }
            clearTarget();
            machine.forceState(SquireAIState.IDLE);
        }
    }

    /**
     * CHEST_INTERACT tick: perform deposit or withdraw using dual-path container access.
     *
     * Dual-path (in order):
     *   1. BaseContainerBlockEntity — vanilla chests, barrels, hoppers, shulker boxes
     *   2. Capabilities.ItemHandler.BLOCK — modded storage (MineColonies, RS, AE2, etc.)
     *
     * On complete: plays chest close sound, transitions to IDLE, fires WORK_TASK_COMPLETE.
     */
    private void tickInteract() {
        if (targetPos == null || mode == null) {
            machine.forceState(SquireAIState.IDLE);
            return;
        }

        var blockEntity = squire.level().getBlockEntity(targetPos);
        if (blockEntity == null) {
            LOGGER.warn("[Squire] CHEST_INTERACT: block entity gone at {}", targetPos.toShortString());
            clearTarget();
            machine.forceState(SquireAIState.IDLE);
            return;
        }

        // Dual-path: vanilla BaseContainerBlockEntity first, then IItemHandler capability fallback.
        // This is required for correct behavior with modded storage blocks (RESEARCH.md pitfall 6).
        if (blockEntity instanceof BaseContainerBlockEntity container) {
            if (mode == Mode.DEPOSIT) {
                depositVanilla(container);
            } else {
                withdrawVanilla(container);
            }
        } else {
            IItemHandler handler = squire.level().getCapability(
                    Capabilities.ItemHandler.BLOCK, targetPos, null);
            if (handler != null) {
                if (mode == Mode.DEPOSIT) {
                    depositHandler(handler);
                } else {
                    withdrawHandler(handler);
                }
            } else {
                LOGGER.warn("[Squire] CHEST_INTERACT: {} has no container or IItemHandler capability",
                        targetPos.toShortString());
                clearTarget();
                machine.forceState(SquireAIState.IDLE);
                return;
            }
        }

        // Chest close sound
        squire.playSound(SoundEvents.CHEST_CLOSE, 0.5F, 1.0F);

        if (SquireConfig.activityLogging.get()) {
            LOGGER.debug("[Squire] {} {} at {}",
                    mode == Mode.DEPOSIT ? "Deposited items into" : "Withdrew items from",
                    blockEntity.getClass().getSimpleName(),
                    targetPos.toShortString());
        }

        clearTarget();
        machine.forceState(SquireAIState.IDLE);
        fireTaskComplete();
    }

    // ── Vanilla container transfer (BaseContainerBlockEntity) ─────────────────

    /**
     * Deposit squire backpack slots (6+) into a vanilla container.
     * Uses only IItemHandler API on the squire side (no .shrink() on ItemStack).
     * Container side uses BaseContainerBlockEntity slot access directly.
     */
    private void depositVanilla(BaseContainerBlockEntity container) {
        IItemHandler inv = squire.getItemHandler();
        // Backpack slots start at 6 (slots 0-3 armor, 4-5 weapons)
        for (int i = 6; i < inv.getSlots(); i++) {
            ItemStack stack = inv.getStackInSlot(i);
            if (stack.isEmpty()) continue;

            // Try to merge into existing stacks first, then find an empty slot
            for (int j = 0; j < container.getContainerSize(); j++) {
                ItemStack containerStack = container.getItem(j);
                int transferred = 0;

                if (containerStack.isEmpty()) {
                    // Empty slot — move the whole squire stack there
                    container.setItem(j, stack.copy());
                    transferred = stack.getCount();
                } else if (ItemStack.isSameItemSameComponents(containerStack, stack)
                        && containerStack.getCount() < containerStack.getMaxStackSize()) {
                    int space = containerStack.getMaxStackSize() - containerStack.getCount();
                    transferred = Math.min(space, stack.getCount());
                    containerStack.grow(transferred);
                }

                if (transferred > 0) {
                    // Consume from squire via IItemHandler
                    inv.extractItem(i, transferred, false);
                    stack = inv.getStackInSlot(i); // refresh reference
                }

                if (stack.isEmpty()) break;
            }
        }
    }

    /**
     * Withdraw requested items from a vanilla container into squire inventory.
     * Overflow (inventory full) is dropped at squire's feet via spawnAtLocation().
     */
    private void withdrawVanilla(BaseContainerBlockEntity container) {
        IItemHandler inv = squire.getItemHandler();
        int remaining = requestedAmount;

        for (int j = 0; j < container.getContainerSize() && (requestedItem == null || remaining > 0); j++) {
            ItemStack containerStack = container.getItem(j);
            if (containerStack.isEmpty()) continue;

            // Apply item filter
            if (requestedItem != null && containerStack.getItem() != requestedItem) continue;

            int toTake = requestedItem != null
                    ? Math.min(remaining, containerStack.getCount())
                    : containerStack.getCount();

            ItemStack extracted = containerStack.copy();
            extracted.setCount(toTake);

            // Insert into squire inventory
            ItemStack overflow = insertIntoInventory(inv, extracted);
            int inserted = toTake - overflow.getCount();

            if (inserted > 0) {
                containerStack.shrink(inserted);
                if (containerStack.isEmpty()) {
                    container.setItem(j, ItemStack.EMPTY);
                }
                remaining -= inserted;
            }

            if (!overflow.isEmpty()) {
                // Inventory full — drop overflow at squire's feet
                squire.spawnAtLocation(overflow);
                break;
            }
        }
    }

    // ── IItemHandler-based transfer (modded storage fallback) ─────────────────

    /**
     * Deposit squire backpack slots (6+) into a modded IItemHandler container.
     * All operations use IItemHandler API — no direct ItemStack mutation.
     */
    private void depositHandler(IItemHandler containerHandler) {
        IItemHandler inv = squire.getItemHandler();
        for (int i = 6; i < inv.getSlots(); i++) {
            ItemStack stack = inv.getStackInSlot(i);
            if (stack.isEmpty()) continue;

            ItemStack toInsert = stack.copy();
            // Try each slot in the container; insertItem() returns the remainder
            for (int j = 0; j < containerHandler.getSlots() && !toInsert.isEmpty(); j++) {
                toInsert = containerHandler.insertItem(j, toInsert, false);
            }

            // Extract the amount that was successfully inserted from squire inventory
            int inserted = stack.getCount() - toInsert.getCount();
            if (inserted > 0) {
                inv.extractItem(i, inserted, false);
            }
        }
    }

    /**
     * Withdraw requested items from a modded IItemHandler container into squire inventory.
     * Overflow is dropped at squire's feet via spawnAtLocation().
     */
    private void withdrawHandler(IItemHandler containerHandler) {
        IItemHandler inv = squire.getItemHandler();
        int remaining = requestedAmount;

        for (int j = 0; j < containerHandler.getSlots() && (requestedItem == null || remaining > 0); j++) {
            ItemStack containerStack = containerHandler.getStackInSlot(j);
            if (containerStack.isEmpty()) continue;

            // Apply item filter
            if (requestedItem != null && containerStack.getItem() != requestedItem) continue;

            int toTake = requestedItem != null
                    ? Math.min(remaining, containerStack.getCount())
                    : containerStack.getCount();

            ItemStack extracted = containerHandler.extractItem(j, toTake, false);
            if (extracted.isEmpty()) continue;

            // Insert into squire inventory; overflow dropped at feet
            ItemStack overflow = insertIntoInventory(inv, extracted);
            int inserted = extracted.getCount() - overflow.getCount();
            remaining -= inserted;

            if (!overflow.isEmpty()) {
                // Put back what didn't fit into the container, or drop at feet
                ItemStack putBack = containerHandler.insertItem(j, overflow, false);
                if (!putBack.isEmpty()) {
                    squire.spawnAtLocation(putBack);
                }
                break; // Inventory full
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Insert a stack into the squire's inventory, trying each slot in order.
     * Returns the remainder (empty if fully inserted).
     */
    private ItemStack insertIntoInventory(IItemHandler inv, ItemStack stack) {
        for (int slot = 0; slot < inv.getSlots() && !stack.isEmpty(); slot++) {
            stack = inv.insertItem(slot, stack, false);
        }
        return stack;
    }

    /** Whether squire is within chestReach of the target position. */
    private boolean isInRange() {
        if (targetPos == null) return false;
        double reach = SquireConfig.chestReach.get();
        return squire.distanceToSqr(
                targetPos.getX() + 0.5,
                targetPos.getY() + 0.5,
                targetPos.getZ() + 0.5) <= reach * reach;
    }

    /**
     * Ability gate: squire level must be >= chestAbilityMinLevel config value.
     * TODO Phase 4: replace with progression ability system once wired.
     */
    private boolean checkAbilityGate() {
        int minLevel = SquireConfig.chestAbilityMinLevel.get();
        if (squire.getLevel() < minLevel) {
            LOGGER.warn("[Squire] Chest interaction requires level {}, squire is level {}",
                    minLevel, squire.getLevel());
            return false;
        }
        return true;
    }

    private void resetApproachState() {
        approachTicks = 0;
        stuckTicks = 0;
        repositionAttempts = 0;
        lastApproachDistSq = Double.MAX_VALUE;
    }

    private void fireTaskComplete() {
        if (squire.getSquireBrain() != null) {
            squire.getSquireBrain().getBus().publish(SquireEvent.WORK_TASK_COMPLETE, squire);
        }
    }

    /**
     * Find an alternative approach position for a chest target.
     * Rotates starting offset by attempt number to try different angles each time.
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

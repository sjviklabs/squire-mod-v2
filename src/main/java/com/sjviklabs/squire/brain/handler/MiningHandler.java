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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.neoforge.items.IItemHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.LinkedList;
import java.util.List;

/**
 * Handles block breaking with proper break speed calculation, crack animation,
 * tool selection, arm swing, and drops deposited into squire's IItemHandler inventory.
 *
 * Single block: setTarget(BlockPos) → machine.forceState(MINING_APPROACH) → MINING_BREAK → drops → IDLE
 * Area clear:   setAreaTarget(cornerA, cornerB) → queues blocks top-down → chains through queue → IDLE when empty
 *
 * Break speed formula (port unchanged from v0.5.0 RESEARCH.md):
 *   progress_per_tick = (tool.getDestroySpeed(state) / (destroySpeed * divisor))
 *                       * breakSpeedMultiplier * (1 + level * miningSpeedPerLevel)
 *   divisor = 30 if correct tool, 100 if not
 *
 * v0.5.0 adaptations:
 * - squire.getSquireInventory() → squire.getItemHandler() (IItemHandler)
 * - inventory.removeItem(slot, 1) → getItemHandler().insertItem() + spawnAtLocation() for overflow
 * - squire.getSquireAI() → squire.getSquireBrain() (method name changed in v2)
 * - SquireAbilities gate removed — mining available from SERVANT tier (level >= 0)
 * - squire.getSquireLevel() → squire.getLevel() (renamed in v2 entity)
 * - mineReach config key (not miningReach)
 *
 * WRK-01: Squire mines single blocks on command.
 * WRK-02: Squire performs area clear (multi-block mining).
 */
public class MiningHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(MiningHandler.class);

    private final SquireEntity squire;
    private final TickRateStateMachine machine;

    @Nullable private BlockPos targetPos;
    private float breakProgress;      // 0.0 to 1.0
    private int lastCrackStage;       // 0-9 for destroyBlockProgress overlay (-1 = none)

    private final LinkedList<BlockPos> blockQueue = new LinkedList<>();
    private boolean areaClearing = false;

    // Stuck detection state
    private int approachTicks;
    private double lastApproachDistSq;
    private int stuckTicks;
    private int repositionAttempts;

    // Constants
    private static final int STUCK_TIMEOUT = 100;           // ticks with no approach progress
    private static final int MAX_APPROACH_TICKS = 200;      // absolute approach time limit
    static final int MAX_REPOSITION_ATTEMPTS = 3;           // skip target after this many failures

    // ── Constructor ───────────────────────────────────────────────────────────

    public MiningHandler(SquireEntity squire, TickRateStateMachine machine) {
        this.squire = squire;
        this.machine = machine;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Set a single block target and transition to MINING_APPROACH.
     * Passing null is a no-op (preserves IDLE state).
     */
    public void setTarget(@Nullable BlockPos pos) {
        if (pos == null) return;

        // Auto-craft pickaxe if missing (Wave 1.5)
        if (!hasPickaxe(squire)) {
            CraftingHandler crafting = squire.getSquireBrain().getCraftingHandler();
            if (!crafting.tryCraftIfMissing(squire,
                    stack -> stack.getItem() instanceof net.minecraft.world.item.PickaxeItem, "pickaxe")) {
                return; // No pickaxe and can't craft one — stay IDLE
            }
        }

        this.targetPos = pos;
        this.breakProgress = 0.0F;
        this.lastCrackStage = -1;
        this.approachTicks = 0;
        this.stuckTicks = 0;
        this.repositionAttempts = 0;
        this.lastApproachDistSq = Double.MAX_VALUE;

        // Equip best pickaxe from inventory before mining
        equipBestTool();

        machine.forceState(SquireAIState.MINING_APPROACH);
    }

    /**
     * Set an area to clear. Calls squire.ensureChunkLoaded() for each corner before
     * building the block queue. Queue is ordered top-down (Y descending) then nearest-first
     * (distance to squire ascending within same Y layer). Capped at areaMaxBlocks.
     *
     * @return total blocks queued (including the one set as immediate target)
     */
    public int setAreaTarget(BlockPos cornerA, BlockPos cornerB) {
        // Auto-craft pickaxe if missing (Wave 1.5)
        if (!hasPickaxe(squire)) {
            CraftingHandler crafting = squire.getSquireBrain().getCraftingHandler();
            if (!crafting.tryCraftIfMissing(squire,
                    stack -> stack.getItem() instanceof net.minecraft.world.item.PickaxeItem, "pickaxe")) {
                return 0; // No pickaxe and can't craft one — stay IDLE
            }
        }

        blockQueue.clear();
        areaClearing = true;

        // ARC-09: ensure chunks are loaded before scanning
        squire.ensureChunkLoaded();

        int minX = Math.min(cornerA.getX(), cornerB.getX());
        int maxX = Math.max(cornerA.getX(), cornerB.getX());
        int minY = Math.min(cornerA.getY(), cornerB.getY());
        int maxY = Math.max(cornerA.getY(), cornerB.getY());
        int minZ = Math.min(cornerA.getZ(), cornerB.getZ());
        int maxZ = Math.max(cornerA.getZ(), cornerB.getZ());

        int maxBlocks = SquireConfig.areaMaxBlocks.get();

        // Build layer by layer: top-down (Y desc), scanline order within each layer.
        // Scanline: sweep Z rows front-to-back, X columns left-to-right within each row.
        // Alternating X direction per Z row (boustrophedon) minimizes travel distance.
        outer:
        for (int y = maxY; y >= minY; y--) {
            boolean reverseX = false;
            for (int z = minZ; z <= maxZ; z++) {
                int xStart = reverseX ? maxX : minX;
                int xEnd   = reverseX ? minX : maxX;
                int xStep  = reverseX ? -1 : 1;

                for (int x = xStart; reverseX ? x >= xEnd : x <= xEnd; x += xStep) {
                    if (blockQueue.size() >= maxBlocks) break outer;

                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = squire.level().getBlockState(pos);

                    // Skip air and unbreakable blocks
                    if (state.isAir()) continue;
                    if (state.getDestroySpeed(squire.level(), pos) < 0) continue;

                    // Skip pure fluid (waterlogged blocks stay — they have a solid state)
                    FluidState fluid = state.getFluidState();
                    if (!fluid.isEmpty() && state.getBlock().defaultBlockState().isAir()) continue;

                    blockQueue.add(pos);
                }
                reverseX = !reverseX;
            }
        }

        // Pop the first block as the immediate target
        if (!blockQueue.isEmpty()) {
            BlockPos first = blockQueue.poll();
            this.targetPos = first;
            this.breakProgress = 0.0F;
            this.lastCrackStage = -1;
            this.approachTicks = 0;
            this.stuckTicks = 0;
            this.repositionAttempts = 0;
            this.lastApproachDistSq = Double.MAX_VALUE;
            machine.forceState(SquireAIState.MINING_APPROACH);
        } else {
            areaClearing = false;
        }

        return blockQueue.size() + (targetPos != null ? 1 : 0);
    }

    /**
     * Main FSM tick dispatcher — call from SquireBrain per-tick for mining states.
     */
    public void tick(SquireAIState currentState) {
        if (currentState == SquireAIState.MINING_APPROACH) {
            tickApproach();
        } else if (currentState == SquireAIState.MINING_BREAK) {
            tickBreak();
        }
    }

    /**
     * Clear target, reset progress, and wipe the area queue.
     * Removes any crack overlay from the current target block.
     */
    public void clearTarget() {
        if (targetPos != null && squire.level() instanceof ServerLevel serverLevel) {
            serverLevel.destroyBlockProgress(squire.getId(), targetPos, -1);
        }
        targetPos = null;
        breakProgress = 0.0F;
        lastCrackStage = -1;
        blockQueue.clear();
        areaClearing = false;
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public boolean hasTarget() {
        return targetPos != null;
    }

    @Nullable
    public BlockPos getTargetPos() {
        return targetPos;
    }

    public boolean isAreaClearing() {
        return areaClearing;
    }

    public int getQueueSize() {
        return blockQueue.size();
    }

    // ── FSM tick implementations ──────────────────────────────────────────────

    /**
     * MINING_APPROACH tick: pathfind toward target block.
     * Transitions to MINING_BREAK on arrival, IDLE on failure.
     */
    private void tickApproach() {
        if (targetPos == null) {
            machine.forceState(SquireAIState.IDLE);
            return;
        }

        BlockState state = squire.level().getBlockState(targetPos);
        if (state.isAir() || state.getDestroySpeed(squire.level(), targetPos) < 0) {
            // Block is gone or unbreakable — skip to next
            if (areaClearing) {
                BlockPos next = popNextValid();
                if (next != null) {
                    setTarget(next);
                    return;
                }
                finalizeAreaClear();
            }
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
            machine.forceState(SquireAIState.MINING_BREAK);
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

            // All reposition attempts exhausted — skip this block
            if (SquireConfig.activityLogging.get()) {
                LOGGER.debug("[Squire] Can't reach {} after {} attempts, skipping",
                        targetPos.toShortString(), repositionAttempts);
            }

            if (areaClearing) {
                BlockPos next = popNearestReachable();
                if (next != null) {
                    setTarget(next);
                    return;
                }
                finalizeAreaClear();
            }
            clearTarget();
            machine.forceState(SquireAIState.IDLE);
        }
    }

    /**
     * MINING_BREAK tick: accumulate break progress, show crack animation, break when done.
     * Deposits drops into squire's IItemHandler; overflow spawned at squire's feet.
     */
    private void tickBreak() {
        if (targetPos == null) {
            machine.forceState(SquireAIState.IDLE);
            return;
        }

        if (!(squire.level() instanceof ServerLevel serverLevel)) {
            machine.forceState(SquireAIState.IDLE);
            return;
        }

        BlockState state = serverLevel.getBlockState(targetPos);
        if (state.isAir()) {
            clearTarget();
            machine.forceState(SquireAIState.IDLE);
            return;
        }

        float destroySpeed = state.getDestroySpeed(serverLevel, targetPos);
        if (destroySpeed < 0) {
            // Unbreakable (bedrock, etc.)
            clearTarget();
            machine.forceState(SquireAIState.IDLE);
            return;
        }

        if (!isInRange()) {
            machine.forceState(SquireAIState.MINING_APPROACH);
            return;
        }

        // Look at target + arm swing animation
        squire.getLookControl().setLookAt(
                targetPos.getX() + 0.5, targetPos.getY() + 0.5, targetPos.getZ() + 0.5);
        squire.swing(InteractionHand.MAIN_HAND);

        // Level-based mining speed — ignores tool for speed, tool only matters for drops.
        // Base rate ~iron pickaxe on stone (6.0/1.5/30 ≈ 0.133), scaling with level.
        float progressPerTick;
        if (destroySpeed == 0) {
            progressPerTick = 1.0F; // instant break (tall grass, etc.)
        } else {
            float levelBonus = 1.0F + (squire.getLevel() * SquireConfig.miningSpeedPerLevel.get().floatValue());
            progressPerTick = (1.0F / (destroySpeed * 5.0F))
                    * SquireConfig.breakSpeedMultiplier.get().floatValue()
                    * levelBonus;
        }

        breakProgress += progressPerTick;

        // Update crack overlay (stages 0-9)
        int crackStage = Math.min((int) (breakProgress * 10.0F), 9);
        if (crackStage != lastCrackStage) {
            serverLevel.destroyBlockProgress(squire.getId(), targetPos, crackStage);
            lastCrackStage = crackStage;
        }

        if (breakProgress >= 1.0F) {
            // Play break sound
            SoundType soundType = state.getSoundType();
            serverLevel.playSound(null, targetPos, soundType.getBreakSound(),
                    SoundSource.BLOCKS, 1.0F, soundType.getPitch());

            BlockPos brokenPos = targetPos;
            String posStr = brokenPos.toShortString();

            // Clear overlay before destroy (destroyBlock fires events that may read progress)
            serverLevel.destroyBlockProgress(squire.getId(), brokenPos, -1);
            targetPos = null;
            breakProgress = 0.0F;
            lastCrackStage = -1;

            // Destroy block (drops are created as ItemEntities by the game)
            // We collect them by scanning nearby ItemEntities right after
            serverLevel.destroyBlock(brokenPos, true, squire);

            // Deposit any newly spawned item entities near the broken block into squire inventory
            collectDropsNearPos(serverLevel, brokenPos);

            // Award mining XP
            if (squire.getProgressionHandler() != null) {
                squire.getProgressionHandler().addMineXP();
            }

            if (SquireConfig.activityLogging.get()) {
                LOGGER.debug("[Squire] Broke block at {}", posStr);
            }

            // Area clearing: chain to next block
            if (areaClearing) {
                BlockPos next = popNextValid();
                if (next != null) {
                    setTarget(next);
                    return;
                }
                finalizeAreaClear();
            }

            machine.forceState(SquireAIState.IDLE);

            // Fire task complete event
            if (squire.getSquireBrain() != null) {
                squire.getSquireBrain().getBus().publish(SquireEvent.WORK_TASK_COMPLETE, squire);
            }
        }
    }

    // ── Drop collection ───────────────────────────────────────────────────────

    /**
     * Collect ItemEntity drops spawned near the broken block position and insert
     * them into the squire's IItemHandler. Overflow (rejected inserts) is left in
     * place or re-spawned at squire's feet via spawnAtLocation().
     *
     * Uses a 2-block radius scan to catch all drops from the break event.
     */
    private void collectDropsNearPos(ServerLevel level, BlockPos brokenPos) {
        IItemHandler inv = squire.getItemHandler();

        List<net.minecraft.world.entity.item.ItemEntity> nearbyItems = level.getEntitiesOfClass(
                net.minecraft.world.entity.item.ItemEntity.class,
                new net.minecraft.world.phys.AABB(brokenPos).inflate(2.0));

        for (net.minecraft.world.entity.item.ItemEntity itemEntity : nearbyItems) {
            if (itemEntity.isRemoved()) continue;

            ItemStack drop = itemEntity.getItem().copy();
            ItemStack remaining = insertIntoInventory(inv, drop);

            if (remaining.isEmpty()) {
                // Fully absorbed into inventory
                itemEntity.discard();
            } else {
                // Inventory full — leave overflow at squire's feet
                itemEntity.setItem(remaining);
            }
        }
    }

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

    // ── Range check ──────────────────────────────────────────────────────────

    private boolean isInRange() {
        if (targetPos == null) return false;
        double reach = SquireConfig.mineReach.get();
        return squire.distanceToSqr(
                targetPos.getX() + 0.5,
                targetPos.getY() + 0.5,
                targetPos.getZ() + 0.5) <= reach * reach;
    }

    // ── Queue helpers ─────────────────────────────────────────────────────────

    /**
     * Pop blocks from the queue until a valid (non-air, breakable) one is found.
     * Returns null if queue is exhausted.
     */
    @Nullable
    private BlockPos popNextValid() {
        while (!blockQueue.isEmpty()) {
            BlockPos candidate = blockQueue.poll();
            BlockState state = squire.level().getBlockState(candidate);
            if (state.isAir()) continue;
            if (state.getDestroySpeed(squire.level(), candidate) < 0) continue;
            return candidate;
        }
        return null;
    }

    /**
     * Find the nearest valid block in the queue by distance to the squire.
     * Removes it from the queue. Used after stuck-skip to avoid linear order bias.
     * Returns null if queue is exhausted.
     */
    @Nullable
    BlockPos popNearestReachable() {
        if (blockQueue.isEmpty()) return null;

        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        int squireY = squire.blockPosition().getY();

        for (BlockPos candidate : blockQueue) {
            BlockState state = squire.level().getBlockState(candidate);
            if (state.isAir()) continue;
            if (state.getDestroySpeed(squire.level(), candidate) < 0) continue;

            double dist = squire.distanceToSqr(
                    candidate.getX() + 0.5, candidate.getY() + 0.5, candidate.getZ() + 0.5);
            int heightAbove = candidate.getY() - squireY;
            double penalty = heightAbove > 0 ? heightAbove * 10.0 : 0;
            double score = dist + penalty;

            if (score < bestScore) {
                bestScore = score;
                best = candidate;
            }
        }

        if (best != null) {
            blockQueue.remove(best);
        }
        return best;
    }

    private void finalizeAreaClear() {
        areaClearing = false;
        if (SquireConfig.activityLogging.get()) {
            LOGGER.debug("[Squire] Area clear complete");
        }
    }

    // ── Tool helpers ──────────────────────────────────────────────────────────

    /**
     * Check if the squire has a pickaxe equipped in mainhand.
     * Used by setTarget/setAreaTarget to decide whether auto-craft is needed.
     */
    private boolean hasPickaxe(SquireEntity squire) {
        var mainHand = squire.getMainHandItem();
        return mainHand.getItem() instanceof net.minecraft.world.item.PickaxeItem;
    }

    // ── Repositioning ─────────────────────────────────────────────────────────

    /**
     * Find an alternative approach position for a target block.
     * Tries cardinal directions and diagonals at multiple distances.
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
                BlockState at    = squire.level().getBlockState(candidate);
                BlockState above  = squire.level().getBlockState(candidate.above());
                BlockState below  = squire.level().getBlockState(candidate.below());

                if (below.isSolid() && at.isAir() && above.isAir()) {
                    return candidate;
                }
            }
        }
        return null;
    }

    /**
     * Scan backpack for the best tool for the target block and equip it in mainhand.
     * Picks whichever tool has the highest destroy speed against the target block state.
     * Falls back to any tool that isCorrectToolForDrops so drops aren't lost.
     * Saves the previous mainhand item back to backpack.
     */
    private void equipBestTool() {
        var handler = squire.getItemHandler();
        if (handler == null || targetPos == null) return;

        BlockState targetState = squire.level().getBlockState(targetPos);

        // Check if current mainhand is already the right tool
        ItemStack currentMainhand = squire.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND);
        if (!currentMainhand.isEmpty() && currentMainhand.isCorrectToolForDrops(targetState)) {
            return; // already holding an appropriate tool
        }

        int bestSlot = -1;
        float bestSpeed = 0;
        int correctDropSlot = -1; // fallback: any tool that gets drops

        for (int i = com.sjviklabs.squire.inventory.SquireItemHandler.EQUIPMENT_SLOTS; i < handler.getSlots(); i++) {
            ItemStack candidate = handler.getStackInSlot(i);
            if (candidate.isEmpty()) continue;
            if (!(candidate.getItem() instanceof net.minecraft.world.item.DiggerItem)) continue;

            float speed = candidate.getDestroySpeed(targetState);
            if (speed > bestSpeed) {
                bestSpeed = speed;
                bestSlot = i;
            }
            if (correctDropSlot < 0 && candidate.isCorrectToolForDrops(targetState)) {
                correctDropSlot = i;
            }
        }

        // Prefer fastest tool; fall back to one that at least gets drops
        int slotToEquip = bestSpeed > 1.0F ? bestSlot : correctDropSlot;
        if (slotToEquip < 0) return; // no suitable tool found

        ItemStack tool = handler.extractItem(slotToEquip, handler.getStackInSlot(slotToEquip).getCount(), false);
        squire.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, tool);
        if (!currentMainhand.isEmpty()) {
            // Try to insert old mainhand back into backpack
            ItemStack remainder = currentMainhand;
            for (int i = com.sjviklabs.squire.inventory.SquireItemHandler.EQUIPMENT_SLOTS; i < handler.getSlots(); i++) {
                remainder = handler.insertItem(i, remainder, false);
                if (remainder.isEmpty()) break;
            }
            if (!remainder.isEmpty()) {
                squire.spawnAtLocation(remainder); // drop if backpack full
            }
        }
    }
}

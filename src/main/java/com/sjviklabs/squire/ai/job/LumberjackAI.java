package com.sjviklabs.squire.ai.job;

import com.minecolonies.api.entity.ai.statemachine.AITarget;
import com.minecolonies.api.entity.ai.statemachine.tickratestatemachine.TickRateStateMachine;
import com.sjviklabs.squire.ai.state.SquireAIState;
import com.sjviklabs.squire.ai.util.BlockBreakUtil;
import com.sjviklabs.squire.config.SquireConfig;
import com.sjviklabs.squire.entity.SquireEntity;
import com.sjviklabs.squire.inventory.SquireItemHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

/**
 * Lumberjack job AI — fell every tree in a crest-selected area, auto-replant saplings.
 *
 * Shares scaffolding with {@link MinerAI} (state tree shape, reach math, break-and-collect).
 * Differs in:
 *   - Only logs are queued, discovered via flood-fill from each seed log in the area
 *     (handles 2×2 dark oak, jungle giants, modded rainbow trees — anything BlockTags.LOGS
 *     plus face/diagonal adjacency fills correctly)
 *   - After a tree's bottom log is broken, plant a sapling on the stump if one is in inventory
 *     and the ground below is valid (grass/dirt/farmland)
 *   - Leaves are NOT chopped; vanilla decay handles them
 *
 * State tree: same IDLE / CHOPPING as MinerAI's IDLE / MINING. Single umbrella state with
 * per-tick logic.
 */
public final class LumberjackAI implements JobAI {

    private static final Logger LOGGER = LoggerFactory.getLogger(LumberjackAI.class);
    private static final double WALK_SPEED = 1.0;
    private static final double REACH_SQ = 25.0;
    private static final int SWING_COOLDOWN_TICKS = 15;
    private static final int MAX_TREE_BLOCKS = 200; // cap a single flood-fill

    private final SquireEntity squire;
    private final TickRateStateMachine<SquireAIState> machine;

    /** Global queue of logs to break across all discovered trees, ordered top-down per tree. */
    private final Deque<BlockPos> queue = new ArrayDeque<>();

    /** Track the lowest log of each tree so we can plant a sapling there after felling. */
    private final Deque<BlockPos> replantSites = new ArrayDeque<>();

    private int swingCooldown = 0;

    public LumberjackAI(SquireEntity squire) {
        this.squire = squire;
        this.machine = new TickRateStateMachine<>(
                SquireAIState.IDLE,
                ex -> LOGGER.error("[LumberjackAI] state machine error", ex)
        );

        machine.addTransition(new AITarget<>(
                SquireAIState.IDLE,
                () -> !queue.isEmpty() && BlockBreakUtil.hasAxe(squire),
                () -> {
                    BlockBreakUtil.equipToolFromBackpack(squire, AxeItem.class);
                    return SquireAIState.MINING; // reuse MINING state umbrella for chop
                },
                10
        ));

        machine.addTransition(new AITarget<>(
                SquireAIState.MINING,
                () -> queue.isEmpty(),
                () -> {
                    // Drain any pending replant sites before handing control back.
                    if (squire.level() instanceof ServerLevel level) {
                        while (!replantSites.isEmpty()) tryReplant(level, replantSites.pollFirst());
                    }
                    squire.getNavigation().stop();
                    return SquireAIState.IDLE;
                },
                5
        ));

        machine.addTransition(new AITarget<>(
                SquireAIState.MINING,
                () -> true,
                this::tickChopping,
                1
        ));
    }

    /**
     * Scan the area for trees and enqueue their logs. Each tree is flood-filled via BlockTags.LOGS
     * adjacency; trees larger than {@link #MAX_TREE_BLOCKS} are truncated (rare enough not to matter).
     */
    public void setArea(BlockPos cornerA, BlockPos cornerB) {
        queue.clear();
        replantSites.clear();
        if (!(squire.level() instanceof ServerLevel level)) return;

        int minX = Math.min(cornerA.getX(), cornerB.getX());
        int maxX = Math.max(cornerA.getX(), cornerB.getX());
        int minY = Math.min(cornerA.getY(), cornerB.getY());
        int maxY = Math.max(cornerA.getY(), cornerB.getY());
        int minZ = Math.min(cornerA.getZ(), cornerB.getZ());
        int maxZ = Math.max(cornerA.getZ(), cornerB.getZ());

        int cap = SquireConfig.areaMaxBlocks.get();
        Set<BlockPos> visited = new HashSet<>();

        for (int y = minY; y <= maxY && queue.size() < cap; y++) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (visited.contains(pos)) continue;
                    BlockState s = level.getBlockState(pos);
                    if (!BlockBreakUtil.isLog(s)) continue;
                    // Found a new tree seed — flood fill.
                    floodFillTree(level, pos, visited, cap);
                }
            }
        }
        LOGGER.debug("[LumberjackAI] queued {} log blocks across {} trees",
                queue.size(), replantSites.size());
    }

    /**
     * Flood-fill from {@code seed} via BlockTags.LOGS adjacency (6 face + 8 diagonal offsets),
     * adds all discovered logs to the queue top-down order, records the LOWEST seen log as a
     * replant site.
     */
    private void floodFillTree(ServerLevel level, BlockPos seed, Set<BlockPos> visited, int globalCap) {
        Set<BlockPos> treeLogs = new HashSet<>();
        Deque<BlockPos> frontier = new ArrayDeque<>();
        frontier.add(seed);

        while (!frontier.isEmpty() && treeLogs.size() < MAX_TREE_BLOCKS) {
            BlockPos p = frontier.poll();
            if (visited.contains(p)) continue;
            visited.add(p);

            BlockState s = level.getBlockState(p);
            if (!BlockBreakUtil.isLog(s)) continue;
            treeLogs.add(p);

            // 6-face + 8-horizontal-diagonal + up-diagonals ≈ the canopy shapes we care about.
            for (int dy = -1; dy <= 1; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        BlockPos n = p.offset(dx, dy, dz);
                        if (!visited.contains(n)) frontier.add(n);
                    }
                }
            }
        }

        if (treeLogs.isEmpty()) return;

        // Sort top-down so we don't leave floating logs.
        BlockPos[] sorted = treeLogs.toArray(new BlockPos[0]);
        java.util.Arrays.sort(sorted, (a, b) -> Integer.compare(b.getY(), a.getY())); // Y desc

        BlockPos lowest = null;
        for (BlockPos log : sorted) {
            if (queue.size() >= globalCap) break;
            queue.add(log);
            if (lowest == null || log.getY() < lowest.getY()) lowest = log;
        }
        if (lowest != null) replantSites.add(lowest);
    }

    public int getQueueSize() {
        return queue.size();
    }

    public boolean hasWork() {
        return !queue.isEmpty();
    }

    private SquireAIState tickChopping() {
        if (!(squire.level() instanceof ServerLevel level)) return SquireAIState.MINING;
        BlockPos target = queue.peekFirst();
        if (target == null) return SquireAIState.MINING;

        BlockState state = level.getBlockState(target);
        if (state.isAir() || !BlockBreakUtil.isLog(state)) {
            // Tree fell out from under us or block was wrong — skip.
            queue.pollFirst();
            return SquireAIState.MINING;
        }

        squire.getLookControl().setLookAt(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5);
        double distSq = squire.distanceToSqr(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5);
        if (distSq > REACH_SQ) {
            squire.getNavigation().moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, WALK_SPEED);
            return SquireAIState.MINING;
        }

        squire.getNavigation().stop();
        if (swingCooldown > 0) {
            swingCooldown--;
            return SquireAIState.MINING;
        }
        squire.swing(InteractionHand.MAIN_HAND);
        BlockBreakUtil.breakAndCollect(squire, level, target, state);
        queue.pollFirst();
        swingCooldown = SWING_COOLDOWN_TICKS;

        // If this was the last log in a tree we've been tracking, try replant immediately.
        // A tree's replant site is its lowest log — once we chop below that Y, the tree is done.
        // We try-replant after each break instead of a separate state to keep behavior simple.
        if (!replantSites.isEmpty()) {
            BlockPos site = replantSites.peekFirst();
            if (level.getBlockState(site).isAir()) {
                tryReplant(level, replantSites.pollFirst());
            }
        }

        return SquireAIState.MINING;
    }

    /**
     * Find a sapling in inventory and place it at {@code stump}. Requires a valid dirt/grass/farmland
     * block directly below (vanilla sapling placement rules). Silently no-op on any failure —
     * replant is a courtesy, not a contract.
     */
    private void tryReplant(ServerLevel level, BlockPos stump) {
        if (!SquireConfig.autoReplant.get()) return;
        if (!level.getBlockState(stump).isAir()) return;
        BlockState below = level.getBlockState(stump.below());
        if (!below.is(BlockTags.DIRT) && !(below.getBlock() instanceof net.minecraft.world.level.block.FarmBlock)) return;

        SquireItemHandler handler = squire.getItemHandler();
        if (handler == null) return;

        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack candidate = handler.getStackInSlot(i);
            if (candidate.isEmpty()) continue;
            if (!candidate.is(net.minecraft.tags.ItemTags.SAPLINGS)) continue;

            // Derive the sapling block from the BlockItem.
            if (!(candidate.getItem() instanceof BlockItem bi)) continue;
            Block saplingBlock = bi.getBlock();
            if (!(saplingBlock instanceof SaplingBlock)) continue;

            level.setBlock(stump, saplingBlock.defaultBlockState(), Block.UPDATE_ALL);
            handler.extractItem(i, 1, false);
            return;
        }
    }

    @Override
    public void tick() {
        machine.tick();
    }

    @Override
    public SquireAIState getCurrentState() {
        return machine.getState();
    }
}

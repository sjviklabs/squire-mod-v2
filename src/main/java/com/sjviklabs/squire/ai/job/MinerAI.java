package com.sjviklabs.squire.ai.job;

import com.minecolonies.api.entity.ai.statemachine.AITarget;
import com.minecolonies.api.entity.ai.statemachine.tickratestatemachine.TickRateStateMachine;
import com.sjviklabs.squire.ai.state.SquireAIState;
import com.sjviklabs.squire.ai.util.BlockBreakUtil;
import com.sjviklabs.squire.config.SquireConfig;
import com.sjviklabs.squire.entity.SquireEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Mining job AI — clears a crest-selected cuboid of breakable blocks.
 *
 * State tree:
 *   IDLE   — queue empty; controller will swap back to Idle/Follow
 *   MINING — per-tick: approach current target, break when in reach, advance queue
 *
 * Tool requirement: the squire must have a pickaxe accessible (mainhand or backpack).
 * If not, we stay in IDLE and broadcast the need — Phase 7's chat wiring surfaces this.
 *
 * Drop handling: {@link BlockBreakUtil#breakAndCollect} — drops computed, block destroyed
 * without natural drops, inserted into backpack with ground overflow fallback.
 *
 * Area iteration: top-down (Y descending) by Y-layer, then row sweep within a layer. This
 * prevents floating blocks from being left behind by a bottom-up dig.
 *
 * Limits: capped at SquireConfig.areaMaxBlocks. Larger selections get truncated with a chat
 * warning (Phase 7). Single breaks complete instantly once the squire is within reach —
 * Phase 4 doesn't implement progress-based break animation; add if playtest misses the feel.
 */
public final class MinerAI implements JobAI {

    private static final Logger LOGGER = LoggerFactory.getLogger(MinerAI.class);
    private static final double WALK_SPEED = 1.0;
    private static final double REACH_SQ = 25.0;                // 5 blocks
    private static final int SWING_COOLDOWN_TICKS = 15;         // 0.75s between breaks

    private final SquireEntity squire;
    private final TickRateStateMachine<SquireAIState> machine;

    private final Deque<BlockPos> queue = new ArrayDeque<>();
    private int swingCooldown = 0;

    public MinerAI(SquireEntity squire) {
        this.squire = squire;
        this.machine = new TickRateStateMachine<>(
                SquireAIState.IDLE,
                ex -> LOGGER.error("[MinerAI] state machine error", ex)
        );

        // IDLE → MINING when a queue exists
        machine.addTransition(new AITarget<>(
                SquireAIState.IDLE,
                () -> !queue.isEmpty() && BlockBreakUtil.hasPickaxe(squire),
                () -> {
                    BlockBreakUtil.equipToolFromBackpack(squire, PickaxeItem.class);
                    return SquireAIState.MINING;
                },
                10
        ));

        // MINING → IDLE when queue drains
        machine.addTransition(new AITarget<>(
                SquireAIState.MINING,
                queue::isEmpty,
                () -> {
                    squire.getNavigation().stop();
                    return SquireAIState.IDLE;
                },
                5
        ));

        // MINING per-tick
        machine.addTransition(new AITarget<>(
                SquireAIState.MINING,
                () -> true,
                this::tickMining,
                1
        ));
    }

    /**
     * Queue all breakable blocks in the cuboid between cornerA and cornerB (inclusive),
     * ordered top-down then nearest-first. Replaces any existing queue. Capped at
     * SquireConfig.areaMaxBlocks.
     */
    public void setArea(BlockPos cornerA, BlockPos cornerB) {
        queue.clear();
        if (!(squire.level() instanceof ServerLevel level)) return;

        int minX = Math.min(cornerA.getX(), cornerB.getX());
        int maxX = Math.max(cornerA.getX(), cornerB.getX());
        int minY = Math.min(cornerA.getY(), cornerB.getY());
        int maxY = Math.max(cornerA.getY(), cornerB.getY());
        int minZ = Math.min(cornerA.getZ(), cornerB.getZ());
        int maxZ = Math.max(cornerA.getZ(), cornerB.getZ());

        int cap = SquireConfig.areaMaxBlocks.get();

        // Top-down layer sweep. Within a layer, nearest-first to the squire's XZ.
        BlockPos squirePos = squire.blockPosition();
        outer:
        for (int y = maxY; y >= minY; y--) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int x = minX; x <= maxX; x++) {
                    if (queue.size() >= cap) break outer;
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (state.isAir()) continue;
                    if (state.getDestroySpeed(level, pos) < 0) continue; // unbreakable (bedrock)
                    queue.add(pos);
                }
            }
        }
        LOGGER.debug("[MinerAI] queued {} blocks ({},{},{}) → ({},{},{})",
                queue.size(), minX, minY, minZ, maxX, maxY, maxZ);
    }

    /** Number of blocks still queued. Controller reads this via hasWork(). */
    public int getQueueSize() {
        return queue.size();
    }

    /** True when the AI has pending work. Called by SquireAIController.chooseJob. */
    public boolean hasWork() {
        return !queue.isEmpty();
    }

    private SquireAIState tickMining() {
        if (!(squire.level() instanceof ServerLevel level)) return SquireAIState.MINING;
        BlockPos target = queue.peekFirst();
        if (target == null) return SquireAIState.MINING; // guard — next tick condition clears to IDLE

        // Skip already-broken blocks (someone else mined it, or water flowed, etc.)
        BlockState state = level.getBlockState(target);
        if (state.isAir()) {
            queue.pollFirst();
            return SquireAIState.MINING;
        }

        // Move toward target
        squire.getLookControl().setLookAt(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5);
        double distSq = squire.distanceToSqr(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5);
        if (distSq > REACH_SQ) {
            squire.getNavigation().moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, WALK_SPEED);
            return SquireAIState.MINING;
        }

        // In reach — break on cooldown
        squire.getNavigation().stop();
        if (swingCooldown > 0) {
            swingCooldown--;
            return SquireAIState.MINING;
        }
        squire.swing(InteractionHand.MAIN_HAND);
        boolean broke = BlockBreakUtil.breakAndCollect(squire, level, target, state);
        if (broke) {
            queue.pollFirst();
        } else {
            // Destruction refused — skip the block so we don't loop forever.
            queue.pollFirst();
        }
        swingCooldown = SWING_COOLDOWN_TICKS;
        return SquireAIState.MINING;
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

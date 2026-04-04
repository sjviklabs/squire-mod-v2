package com.sjviklabs.squire.brain.handler;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for MiningHandler queue logic — no live SquireEntity or server required.
 *
 * Uses TestableMiningHandler (inner class) that exposes the area queue and stuck-skip
 * logic without requiring a running Minecraft world.
 *
 * Covers WRK-02: area queue ordering (top-down, nearest-first), stuck skip after MAX_REPOSITION_ATTEMPTS.
 *
 * Run: ./gradlew test --tests "com.sjviklabs.squire.brain.handler.MiningHandlerTest"
 */
class MiningHandlerTest {

    // ── Test double ───────────────────────────────────────────────────────────

    /**
     * Testable variant that replicates MiningHandler's queue-building and stuck logic
     * without needing a live SquireEntity or ServerLevel.
     *
     * Squire position is injected as a BlockPos for distance calculations.
     */
    private static class TestableMiningHandler {

        static final int MAX_REPOSITION_ATTEMPTS = 3;

        private final LinkedList<BlockPos> blockQueue = new LinkedList<>();
        private BlockPos currentTarget = null;
        private int stuckCount = 0;

        /** Squire position used for distance calculations in nearest-first sort. */
        private final BlockPos squirePos;

        TestableMiningHandler(BlockPos squirePos) {
            this.squirePos = squirePos;
        }

        /**
         * Mirrors MiningHandler.setAreaTarget() queue-building logic.
         * Populates queue top-down (Y descending), within same Y sorted nearest-first
         * (ascending distance from squirePos). Does NOT filter air/unbreakable —
         * that requires a live level; unit tests supply all blocks explicitly.
         */
        void setAreaTarget(BlockPos cornerA, BlockPos cornerB, List<BlockPos> allBlocks) {
            blockQueue.clear();

            int minY = Math.min(cornerA.getY(), cornerB.getY());
            int maxY = Math.max(cornerA.getY(), cornerB.getY());

            // Build layer-by-layer (top down), within each layer sort nearest-first
            for (int y = maxY; y >= minY; y--) {
                final int finalY = y;
                List<BlockPos> layer = new ArrayList<>();
                for (BlockPos pos : allBlocks) {
                    if (pos.getY() == finalY) {
                        layer.add(pos);
                    }
                }
                // Sort within layer: nearest first (ascending distance from squirePos)
                layer.sort((a, b) -> {
                    double da = distanceSq(squirePos, a);
                    double db = distanceSq(squirePos, b);
                    return Double.compare(da, db);
                });
                blockQueue.addAll(layer);
            }

            // Pop first as current target
            if (!blockQueue.isEmpty()) {
                currentTarget = blockQueue.poll();
            }
        }

        /**
         * setTarget(null) must be a no-op.
         */
        void setTarget(BlockPos pos) {
            if (pos == null) return;
            this.currentTarget = pos;
            this.stuckCount = 0;
        }

        /**
         * Simulate incrementing stuck counter.
         * When stuckCount reaches MAX_REPOSITION_ATTEMPTS, popNearestReachable()
         * should skip the current target.
         */
        void incrementStuck() {
            stuckCount++;
        }

        /**
         * Returns next block from queue if stuck attempts are exhausted, otherwise null.
         * Mirrors the skip logic in MiningHandler.tickApproach() when all reposition attempts fail.
         */
        BlockPos popNearestReachable() {
            if (stuckCount < MAX_REPOSITION_ATTEMPTS) {
                return null; // not stuck enough to skip yet
            }
            // Skip current target, pop next from queue
            currentTarget = blockQueue.poll();
            stuckCount = 0;
            return currentTarget;
        }

        BlockPos getCurrentTarget() {
            return currentTarget;
        }

        LinkedList<BlockPos> getBlockQueue() {
            return blockQueue;
        }

        int getStuckCount() {
            return stuckCount;
        }

        private static double distanceSq(BlockPos from, BlockPos to) {
            double dx = from.getX() - to.getX();
            double dy = from.getY() - to.getY();
            double dz = from.getZ() - to.getZ();
            return dx * dx + dy * dy + dz * dz;
        }
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    void test_areaQueue_topDown() {
        // Area Y=60 to Y=62 → blocks enqueued top-down (Y=62 first, Y=60 last)
        BlockPos squirePos = new BlockPos(0, 60, 0);
        var handler = new TestableMiningHandler(squirePos);

        // Corners of a 1x3 column (single XZ position to isolate Y ordering)
        BlockPos cornerA = new BlockPos(0, 60, 0);
        BlockPos cornerB = new BlockPos(0, 62, 0);

        List<BlockPos> allBlocks = List.of(
                new BlockPos(0, 60, 0),
                new BlockPos(0, 61, 0),
                new BlockPos(0, 62, 0)
        );

        handler.setAreaTarget(cornerA, cornerB, allBlocks);

        // Current target is first block popped — should be Y=62
        assertEquals(62, handler.getCurrentTarget().getY(),
                "First block (current target) must be at Y=62 (top-down ordering)");

        // Remaining queue: Y=61, then Y=60
        LinkedList<BlockPos> queue = handler.getBlockQueue();
        assertEquals(2, queue.size(), "Queue should have 2 remaining blocks");
        assertEquals(61, queue.poll().getY(), "Second block must be Y=61");
        assertEquals(60, queue.poll().getY(), "Third block must be Y=60");
    }

    @Test
    void test_areaQueue_nearestFirst() {
        // Two blocks at the same Y with different XZ distances — nearer block must come first
        BlockPos squirePos = new BlockPos(5, 60, 5);
        var handler = new TestableMiningHandler(squirePos);

        BlockPos near  = new BlockPos(6, 61, 5); // distance ~1
        BlockPos far   = new BlockPos(0, 61, 0); // distance ~9

        BlockPos cornerA = new BlockPos(0, 61, 0);
        BlockPos cornerB = new BlockPos(10, 61, 10);

        List<BlockPos> allBlocks = List.of(far, near); // supply far first to verify sorting

        handler.setAreaTarget(cornerA, cornerB, allBlocks);

        // Current target is first popped: should be 'near'
        assertEquals(near, handler.getCurrentTarget(),
                "Nearer block should be popped first within the same Y layer");
        assertEquals(far, handler.getBlockQueue().peek(),
                "Farther block should remain in the queue");
    }

    @Test
    void test_stuckSkip_afterMaxAttempts() {
        // Stuck counter reaches MAX_REPOSITION_ATTEMPTS → popNearestReachable pops next target
        BlockPos squirePos = new BlockPos(0, 60, 0);
        var handler = new TestableMiningHandler(squirePos);

        BlockPos first  = new BlockPos(5, 60, 0);
        BlockPos second = new BlockPos(6, 60, 0);

        BlockPos cornerA = new BlockPos(5, 60, 0);
        BlockPos cornerB = new BlockPos(6, 60, 0);
        List<BlockPos> allBlocks = List.of(first, second);

        handler.setAreaTarget(cornerA, cornerB, allBlocks);
        // After setAreaTarget: currentTarget=first (nearer), queue=[second]

        // Simulate failing to reach first target MAX_REPOSITION_ATTEMPTS times
        for (int i = 0; i < TestableMiningHandler.MAX_REPOSITION_ATTEMPTS; i++) {
            handler.incrementStuck();
        }

        assertEquals(TestableMiningHandler.MAX_REPOSITION_ATTEMPTS, handler.getStuckCount(),
                "Stuck count must equal MAX_REPOSITION_ATTEMPTS before skip fires");

        BlockPos next = handler.popNearestReachable();

        assertNotNull(next, "popNearestReachable() must return the next block after skip");
        assertEquals(second, next,
                "Returned block must be 'second' (the next in queue after skipping 'first')");
        assertEquals(0, handler.getStuckCount(),
                "Stuck count must reset to 0 after skip");
    }

    @Test
    void test_setTarget_null_doesNothing() {
        // setTarget(null) must leave handler in IDLE (no crash, no state change)
        BlockPos squirePos = new BlockPos(0, 60, 0);
        var handler = new TestableMiningHandler(squirePos);

        // No area target set — currentTarget is null
        assertNull(handler.getCurrentTarget(), "Initial target should be null");

        // Calling setTarget(null) must be a no-op
        handler.setTarget(null);

        assertNull(handler.getCurrentTarget(),
                "After setTarget(null), currentTarget must still be null (no-op)");
    }
}

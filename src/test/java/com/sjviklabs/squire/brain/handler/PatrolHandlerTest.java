package com.sjviklabs.squire.brain.handler;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PatrolHandler route-building logic.
 *
 * Uses Map-backed test doubles for both the existence check and link resolver —
 * no BlockEntity instantiation required. Consistent with the writeTag/readTag
 * headless-test pattern from SignpostBlockEntityTest.
 *
 * Sentinel: map.containsKey(pos) = "signpost exists here"
 *           map.get(pos) = linked next pos (null = end of chain)
 *
 * Requirements: PTR-02
 * Run: ./gradlew test --tests "*.PatrolHandlerTest"
 */
class PatrolHandlerTest {

    /**
     * Runs buildRouteFromSignpost against a simple pos-to-next map.
     * map.containsKey(pos) = is a valid signpost; map.get(pos) = linked next (null = end).
     */
    private static List<BlockPos> buildRoute(Map<BlockPos, BlockPos> world, BlockPos start) {
        return PatrolHandler.buildRouteFromSignpost(
                world::containsKey,  // isSignpost predicate
                world::get,          // getNext resolver (returns null for end of chain)
                start
        );
    }

    // ── Tests ────────────────────────────────────────────────────────────────

    @Test
    void test_buildRoute_linear() {
        // Chain: A -> B -> C -> null (end of chain)
        BlockPos posA = new BlockPos(0, 64, 0);
        BlockPos posB = new BlockPos(10, 64, 0);
        BlockPos posC = new BlockPos(20, 64, 0);

        Map<BlockPos, BlockPos> world = new HashMap<>();
        world.put(posA, posB);
        world.put(posB, posC);
        world.put(posC, null); // terminal signpost, no link

        List<BlockPos> route = buildRoute(world, posA);

        assertEquals(3, route.size(), "Linear chain of 3 should produce 3 waypoints");
        assertEquals(posA, route.get(0));
        assertEquals(posB, route.get(1));
        assertEquals(posC, route.get(2));
    }

    @Test
    void test_buildRoute_looped() {
        // Circular chain: A -> B -> C -> A (loop detected via visited set)
        BlockPos posA = new BlockPos(0, 64, 0);
        BlockPos posB = new BlockPos(10, 64, 0);
        BlockPos posC = new BlockPos(20, 64, 0);

        Map<BlockPos, BlockPos> world = new HashMap<>();
        world.put(posA, posB);
        world.put(posB, posC);
        world.put(posC, posA); // back to A -- loop

        List<BlockPos> route = buildRoute(world, posA);

        assertEquals(3, route.size(), "Looped chain should produce 3 waypoints (terminates at revisit)");
        assertTrue(route.contains(posA));
        assertTrue(route.contains(posB));
        assertTrue(route.contains(posC));
    }

    @Test
    void test_buildRoute_empty() {
        // Start position has no signpost in the world
        BlockPos posA = new BlockPos(0, 64, 0);
        Map<BlockPos, BlockPos> world = new HashMap<>(); // empty

        List<BlockPos> route = buildRoute(world, posA);

        assertTrue(route.isEmpty(), "No signpost at start should return empty list");
    }

    @Test
    void test_buildRoute_brokenChain() {
        // A links to B, but B is not a signpost (broken link)
        BlockPos posA = new BlockPos(0, 64, 0);
        BlockPos posB = new BlockPos(10, 64, 0);

        Map<BlockPos, BlockPos> world = new HashMap<>();
        world.put(posA, posB); // A links to B
        // posB intentionally absent -- broken link

        List<BlockPos> route = buildRoute(world, posA);

        assertEquals(1, route.size(), "Broken chain should stop at A, returning [A] only");
        assertEquals(posA, route.get(0));
    }
}

package com.sjviklabs.squire.brain.handler;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PatrolHandler route-building logic.
 *
 * v3.1.0 — previously tested signpost-chain builder. Now tests crest-area
 * perimeter builder, which walks the four corners of a rectangular area
 * defined by two corner BlockPos values.
 *
 * Requirements: PTR-02 (patrol walks route in a loop, post-combat resume)
 * Run: ./gradlew test --tests "*.PatrolHandlerTest"
 */
class PatrolHandlerTest {

    // ── buildRouteFromCrestArea ─────────────────────────────────────────────

    @Test
    void test_area_standardCorners_produces4Waypoints() {
        // SW corner (0,64,0) and NE corner (10,64,10)
        BlockPos sw = new BlockPos(0, 64, 0);
        BlockPos ne = new BlockPos(10, 64, 10);

        List<BlockPos> route = PatrolHandler.buildRouteFromCrestArea(sw, ne);

        assertEquals(4, route.size(), "Crest area should produce 4 corner waypoints");
        assertEquals(new BlockPos(0, 64, 0), route.get(0), "First waypoint is SW corner");
        assertEquals(new BlockPos(10, 64, 0), route.get(1), "Second waypoint is SE corner");
        assertEquals(new BlockPos(10, 64, 10), route.get(2), "Third waypoint is NE corner");
        assertEquals(new BlockPos(0, 64, 10), route.get(3), "Fourth waypoint is NW corner");
    }

    @Test
    void test_area_reversedCorners_samePerimeter() {
        // Same area but corners given in reverse order (NE first, then SW)
        BlockPos ne = new BlockPos(10, 64, 10);
        BlockPos sw = new BlockPos(0, 64, 0);

        List<BlockPos> route = PatrolHandler.buildRouteFromCrestArea(ne, sw);

        assertEquals(4, route.size(), "Reversed corner order should still produce 4 waypoints");
        // The same 4 perimeter points should appear regardless of input order
        assertTrue(route.contains(new BlockPos(0, 64, 0)));
        assertTrue(route.contains(new BlockPos(10, 64, 0)));
        assertTrue(route.contains(new BlockPos(10, 64, 10)));
        assertTrue(route.contains(new BlockPos(0, 64, 10)));
    }

    @Test
    void test_area_usesFirstCornerY() {
        // When corners are at different Y levels, perimeter uses corner1's Y
        BlockPos c1 = new BlockPos(0, 70, 0);
        BlockPos c2 = new BlockPos(10, 80, 10);

        List<BlockPos> route = PatrolHandler.buildRouteFromCrestArea(c1, c2);

        assertEquals(4, route.size());
        for (BlockPos p : route) {
            assertEquals(70, p.getY(), "All waypoints should inherit corner1's Y");
        }
    }

    @Test
    void test_area_nullCorners_returnsEmpty() {
        assertTrue(PatrolHandler.buildRouteFromCrestArea(null, new BlockPos(0,64,0)).isEmpty());
        assertTrue(PatrolHandler.buildRouteFromCrestArea(new BlockPos(0,64,0), null).isEmpty());
        assertTrue(PatrolHandler.buildRouteFromCrestArea(null, null).isEmpty());
    }

    @Test
    void test_area_singlePoint_producesDegenerate4Waypoints() {
        // Both corners at the same pos — degenerate but should not crash
        BlockPos p = new BlockPos(5, 64, 5);

        List<BlockPos> route = PatrolHandler.buildRouteFromCrestArea(p, p);

        assertEquals(4, route.size(), "Degenerate area still emits 4 waypoints (all identical)");
        for (BlockPos wp : route) {
            assertEquals(p, wp, "All waypoints collapse to the single point");
        }
    }
}

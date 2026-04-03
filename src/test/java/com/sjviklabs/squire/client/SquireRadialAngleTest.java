package com.sjviklabs.squire.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for the radial menu wedge angle calculation in SquireRadialScreen.
 *
 * Tests call SquireRadialScreen.computeWedge(float dx, float dy) — a static utility method.
 * These tests are pure math: no client classes, no NeoForge bootstrap required.
 *
 * Wedge convention (angle 0 = top, increases clockwise):
 *   Wedge 0 — Follow    (top, directly above center)
 *   Wedge 1 — Stay      (right)
 *   Wedge 2 — Guard     (bottom)
 *   Wedge 3 — Inventory (left)
 */
class SquireRadialAngleTest {

    /**
     * Cursor directly above center: dx=0, dy=-1 → wedge 0 (Follow).
     * atan2(0, -(-1)) = atan2(0, 1) = 0 → index 0.
     */
    @Test
    void cursorAboveCenter_returnsWedge0_Follow() {
        assertEquals(0, SquireRadialScreen.computeWedge(0f, -1f));
    }

    /**
     * Cursor directly to the right: dx=1, dy=0 → wedge 1 (Stay).
     * atan2(1, -(0)) = atan2(1, 0) = π/2 → index 1.
     */
    @Test
    void cursorRightOfCenter_returnsWedge1_Stay() {
        assertEquals(1, SquireRadialScreen.computeWedge(1f, 0f));
    }

    /**
     * Cursor directly below center: dx=0, dy=1 → wedge 2 (Guard).
     * atan2(0, -(1)) = atan2(0, -1) = π → index 2.
     */
    @Test
    void cursorBelowCenter_returnsWedge2_Guard() {
        assertEquals(2, SquireRadialScreen.computeWedge(0f, 1f));
    }

    /**
     * Cursor directly to the left: dx=-1, dy=0 → wedge 3 (Inventory).
     * atan2(-1, -(0)) = atan2(-1, 0) = -π/2 → normalized to 3π/2 → index 3.
     */
    @Test
    void cursorLeftOfCenter_returnsWedge3_Inventory() {
        assertEquals(3, SquireRadialScreen.computeWedge(-1f, 0f));
    }

    /**
     * Negative angle from atan2 is normalized to positive before index calculation.
     * dx=-1, dy=0: atan2 returns -π/2 (negative). After += 2π: becomes 3π/2 → index 3.
     */
    @Test
    void negativeAngle_isNormalizedBeforeIndexing() {
        // atan2(-1, 0) = -π/2, normalized = 3π/2 → wedge 3
        int wedge = SquireRadialScreen.computeWedge(-1f, 0f);
        assertEquals(3, wedge, "Negative angle must be normalized to positive before wedge index calculation");
    }

    /**
     * Angle exactly at π/2 boundary maps to wedge 1.
     * dx=1, dy=0: atan2(1, 0) = π/2. (int)(π/2 / (π/2)) = 1.
     */
    @Test
    void angleAtRightBoundary_returnsWedge1() {
        // Exact right boundary: dx=1, dy=0 → atan2(1,0) = π/2 → wedge 1
        assertEquals(1, SquireRadialScreen.computeWedge(1f, 0f));
    }
}

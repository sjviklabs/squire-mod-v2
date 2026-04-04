package com.sjviklabs.squire.brain.handler;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for PatrolHandler route-building logic.
 *
 * All tests are @Disabled stubs until Wave 2 implements PatrolHandler.
 * Wave 2: remove @Disabled and implement assertions against
 * PatrolHandler.buildRouteFromSignpost() using a test-double Level.
 *
 * Requirements: PTR-02
 * Run: ./gradlew test --tests "*.PatrolHandlerTest"
 */
class PatrolHandlerTest {

    @Test
    @Disabled("requires SignpostBlockEntity chain — implemented in Task 2")
    void test_buildRoute_linear() {
        // Linear chain: A → B → C → null
        // Expected: [A, B, C]
    }

    @Test
    @Disabled("requires SignpostBlockEntity chain — implemented in Task 2")
    void test_buildRoute_looped() {
        // Circular chain: A → B → C → A (loop detected via visited set)
        // Expected: [A, B, C] — all unique nodes, loop terminates cleanly
    }

    @Test
    @Disabled("requires SignpostBlockEntity chain — implemented in Task 2")
    void test_buildRoute_empty() {
        // Start block is not a signpost, or signpost has no next
        // Expected: empty list or single-element list [A]
    }

    @Test
    @Disabled("requires SignpostBlockEntity chain — implemented in Task 2")
    void test_buildRoute_brokenChain() {
        // A → B (B exists) → C (C position has no BlockEntity)
        // Expected: [A, B] — stops gracefully at broken link
    }
}

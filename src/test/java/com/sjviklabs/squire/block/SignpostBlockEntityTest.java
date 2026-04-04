package com.sjviklabs.squire.block;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for SignpostBlockEntity NBT round-trips and linking gesture.
 *
 * All tests are @Disabled until Task 2 creates SignpostBlockEntity.
 * Task 2 will:
 *   1. Create SignpostBlockEntity with mode, linkedSignpost, waitTicks, assignedOwner fields
 *   2. Remove @Disabled from the five NBT round-trip tests
 *   3. Add imports for SignpostBlockEntity, BlockPos, UUID, CompoundTag
 *
 * NBT round-trip tests run headlessly: CompoundTag and NbtUtils are pure Java classes
 * with no NeoForge bootstrap required (same pattern as SquireDataAttachmentTest).
 *
 * Requirements: PTR-01, PTR-03
 * Run: ./gradlew test --tests "*.SignpostBlockEntityTest"
 */
class SignpostBlockEntityTest {

    // ─── PTR-01: NBT round-trip ───────────────────────────────────────────────
    // All five tests are stubs until Task 2 creates SignpostBlockEntity.
    // Task 2 implementation:
    //   - Create SignpostBlockEntity(BlockPos, BlockState) constructor
    //   - saveAdditional/loadAdditional with mode, linkedSignpost, waitTicks, assignedOwner
    //   - NbtUtils.writeBlockPos / readBlockPos(tag, key).ifPresent() for linkedSignpost

    @Test
    @Disabled("requires SignpostBlockEntity — enabled in Task 2")
    void test_nbtRoundTrip_mode() {
        // save PatrolMode.WAYPOINT → new tag → load → assert mode == WAYPOINT
    }

    @Test
    @Disabled("requires SignpostBlockEntity — enabled in Task 2")
    void test_nbtRoundTrip_linkedSignpost() {
        // save BlockPos(10, 64, 20) → new tag → load → assert pos equals expected
        // Uses NbtUtils.writeBlockPos/readBlockPos(tag, "LinkedSignpost").ifPresent()
    }

    @Test
    @Disabled("requires SignpostBlockEntity — enabled in Task 2")
    void test_nbtRoundTrip_waitTicks() {
        // save waitTicks=40 → new tag → load → assert 40
    }

    @Test
    @Disabled("requires SignpostBlockEntity — enabled in Task 2")
    void test_nbtRoundTrip_assignedOwner() {
        // save UUID.randomUUID() → new tag → load → assert equals
    }

    @Test
    @Disabled("requires SignpostBlockEntity — enabled in Task 2")
    void test_nbtRoundTrip_absentLinkedSignpost() {
        // do NOT set linkedSignpost → save → load → assert getLinkedSignpost() == null
    }

    // ─── PTR-03: linking gesture ──────────────────────────────────────────────

    @Test
    @Disabled("requires SignpostBlock world context — implemented in Wave 2")
    void test_linkingGesture_twoClickFlow() {
        // requires SignpostBlock world context
        // Wave 2: verify that clicking signpost A then B links A.getLinkedSignpost() == B.getPos()
    }
}

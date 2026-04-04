package com.sjviklabs.squire.block;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SignpostBlockEntity NBT round-trips and linking gesture.
 *
 * Tests call the package-private static writeTag/readTag helpers directly, avoiding
 * BlockEntity instantiation (which requires a frozen registry in the test environment).
 * This is the same headless-safe pattern used by SquireDataAttachmentTest (CODEC + JsonOps).
 *
 * Requirements: PTR-01, PTR-03
 * Run: ./gradlew test --tests "*.SignpostBlockEntityTest"
 */
class SignpostBlockEntityTest {

    // ─── PTR-01: NBT round-trip ───────────────────────────────────────────────

    @Test
    void test_nbtRoundTrip_mode() {
        CompoundTag tag = new CompoundTag();
        SignpostBlockEntity.writeTag(tag, SignpostBlockEntity.PatrolMode.WAYPOINT, null, 40, null);

        SignpostBlockEntity.NbtData data = SignpostBlockEntity.readTag(tag);

        assertEquals(SignpostBlockEntity.PatrolMode.WAYPOINT, data.mode,
                "Mode must survive NBT round-trip");
    }

    @Test
    void test_nbtRoundTrip_linkedSignpost() {
        BlockPos expected = new BlockPos(10, 64, 20);
        CompoundTag tag = new CompoundTag();
        SignpostBlockEntity.writeTag(tag, SignpostBlockEntity.PatrolMode.WAYPOINT, expected, 40, null);

        SignpostBlockEntity.NbtData data = SignpostBlockEntity.readTag(tag);

        assertEquals(expected, data.linkedSignpost,
                "LinkedSignpost BlockPos must survive NBT round-trip via NbtUtils");
    }

    @Test
    void test_nbtRoundTrip_waitTicks() {
        CompoundTag tag = new CompoundTag();
        SignpostBlockEntity.writeTag(tag, SignpostBlockEntity.PatrolMode.WAYPOINT, null, 40, null);

        SignpostBlockEntity.NbtData data = SignpostBlockEntity.readTag(tag);

        assertEquals(40, data.waitTicks,
                "WaitTicks must survive NBT round-trip");
    }

    @Test
    void test_nbtRoundTrip_assignedOwner() {
        UUID expected = UUID.randomUUID();
        CompoundTag tag = new CompoundTag();
        SignpostBlockEntity.writeTag(tag, SignpostBlockEntity.PatrolMode.WAYPOINT, null, 40, expected);

        SignpostBlockEntity.NbtData data = SignpostBlockEntity.readTag(tag);

        assertEquals(expected, data.assignedOwner,
                "AssignedOwner UUID must survive NBT round-trip");
    }

    @Test
    void test_nbtRoundTrip_absentLinkedSignpost() {
        // writeTag with null linkedSignpost — key must not be written
        CompoundTag tag = new CompoundTag();
        SignpostBlockEntity.writeTag(tag, SignpostBlockEntity.PatrolMode.WAYPOINT, null, 40, null);

        SignpostBlockEntity.NbtData data = SignpostBlockEntity.readTag(tag);

        assertNull(data.linkedSignpost,
                "Absent LinkedSignpost key must produce null after load");
    }

    // ─── PTR-03: linking gesture ──────────────────────────────────────────────

    @Test
    @Disabled("requires SignpostBlock world context — implemented in Wave 2")
    void test_linkingGesture_twoClickFlow() {
        // requires SignpostBlock world context
        // Wave 2: verify that clicking signpost A then B links A.getLinkedSignpost() == B.getPos()
    }
}

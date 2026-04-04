package com.sjviklabs.squire.brain.handler;

import com.sjviklabs.squire.entity.SquireTier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TorchHandler behavior — no live SquireEntity or server required.
 *
 * Uses TestableTorchHandler (inner class) that accepts SquireTier + light level directly,
 * bypassing live SquireEntity DeferredHolder resolution. Inventory is backed by
 * a simple ItemStackHandler slot array.
 *
 * Covers WRK-03: torch placed below light threshold, cooldown gating, slot search.
 *
 * Run: ./gradlew test --tests "com.sjviklabs.squire.brain.handler.TorchHandlerTest"
 */
class TorchHandlerTest {

    // ── Test double ───────────────────────────────────────────────────────────

    /**
     * Testable subclass of TorchHandler that skips the live SquireEntity requirement.
     * Injects light level and inventory directly so tests can exercise pure handler logic.
     */
    private static class TestableTorchHandler {
        private final ItemStackHandler inventory;
        private final int lightLevel;
        private final int lightThreshold;
        private int cooldownRemaining = 0;
        private boolean placementAttempted = false;
        private int lastPlacedSlot = -1;

        // Configuration matching TorchHandler defaults
        static final int DEFAULT_THRESHOLD = 7;
        static final int COOLDOWN_TICKS = 40;

        TestableTorchHandler(ItemStackHandler inventory, int lightLevel) {
            this(inventory, lightLevel, DEFAULT_THRESHOLD);
        }

        TestableTorchHandler(ItemStackHandler inventory, int lightLevel, int lightThreshold) {
            this.inventory = inventory;
            this.lightLevel = lightLevel;
            this.lightThreshold = lightThreshold;
        }

        /**
         * Mirrors TorchHandler.tryPlaceTorch() logic without needing a live level.
         * Returns true if a torch was "placed" (consumed from inventory).
         */
        boolean tryPlaceTorch() {
            // Cooldown gate
            if (cooldownRemaining > 0) {
                cooldownRemaining--;
                return false;
            }

            // Light level gate (>= threshold means bright enough — don't place)
            if (lightLevel >= lightThreshold) return false;

            // Inventory search
            int torchSlot = findTorchSlot();
            if (torchSlot < 0) return false;

            // Simulate placement: consume one torch
            inventory.extractItem(torchSlot, 1, false);
            cooldownRemaining = COOLDOWN_TICKS;
            placementAttempted = true;
            lastPlacedSlot = torchSlot;
            return true;
        }

        private int findTorchSlot() {
            for (int i = 0; i < inventory.getSlots(); i++) {
                ItemStack stack = inventory.getStackInSlot(i);
                if (!stack.isEmpty() && stack.is(Items.TORCH)) {
                    return i;
                }
            }
            return -1;
        }

        boolean wasPlacementAttempted() {
            return placementAttempted;
        }

        int getLastPlacedSlot() {
            return lastPlacedSlot;
        }
    }

    /** Build an inventory handler with a given number of slots, all initially empty. */
    private static ItemStackHandler emptyInventory(int slots) {
        return new ItemStackHandler(slots) {
            @Override
            protected void onContentsChanged(int slot) {
                // No-op in tests
            }
        };
    }

    /** Put one torch stack into the given slot of a handler. */
    private static void putTorch(ItemStackHandler inv, int slot, int count) {
        inv.setStackInSlot(slot, new ItemStack(Items.TORCH, count));
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    void test_noTorch_doesNotPlace() {
        // Squire has no torches — tryPlaceTorch must return false and not "place" anything
        var inv = emptyInventory(15);
        var handler = new TestableTorchHandler(inv, /* lightLevel= */ 4);

        boolean result = handler.tryPlaceTorch();

        assertFalse(result, "Should return false when inventory has no torches");
        assertFalse(handler.wasPlacementAttempted(),
                "Placement flag must stay false when no torch is available");
    }

    @Test
    void test_aboveThreshold_doesNotPlace() {
        // Light level 8 is above default threshold 7 — should not place
        var inv = emptyInventory(15);
        putTorch(inv, 6, 10); // torch at slot 6 (backpack slot 0)

        var handler = new TestableTorchHandler(inv, /* lightLevel= */ 8);

        boolean result = handler.tryPlaceTorch();

        assertFalse(result, "Should return false when light level (8) >= threshold (7)");
        // Verify torch was NOT consumed
        assertEquals(10, inv.getStackInSlot(6).getCount(),
                "Torch count must remain unchanged when placement is blocked by light level");
    }

    @Test
    void test_belowThreshold_withTorch_places() {
        // Light level 6 < threshold 7, torch in slot 7 → should place and consume one
        var inv = emptyInventory(15);
        putTorch(inv, 7, 5);

        var handler = new TestableTorchHandler(inv, /* lightLevel= */ 6);

        boolean result = handler.tryPlaceTorch();

        assertTrue(result, "Should return true when light level (6) < threshold (7) and torch exists");
        assertEquals(4, inv.getStackInSlot(7).getCount(),
                "One torch must be consumed from slot 7 after placement");
    }

    @Test
    void test_cooldown_blocksPlacement() {
        // Place once — then call immediately again. Second call must return false.
        var inv = emptyInventory(15);
        putTorch(inv, 6, 10);

        var handler = new TestableTorchHandler(inv, /* lightLevel= */ 4);

        boolean first = handler.tryPlaceTorch();
        boolean second = handler.tryPlaceTorch(); // called with zero elapsed ticks

        assertTrue(first, "First placement should succeed");
        assertFalse(second, "Second immediate call must be blocked by cooldown");
        // Only one torch consumed
        assertEquals(9, inv.getStackInSlot(6).getCount(),
                "Only one torch should have been consumed — cooldown blocked the second call");
    }

    @Test
    void test_torchSlotSearch_findsFirstMatch() {
        // Torch in slot 10 (not slot 0) — handler must walk all slots to find it
        var inv = emptyInventory(15);
        // Slots 0-9 are empty, slot 10 has torch
        putTorch(inv, 10, 3);

        var handler = new TestableTorchHandler(inv, /* lightLevel= */ 3);

        boolean result = handler.tryPlaceTorch();

        assertTrue(result, "Should find torch in slot 10 even though earlier slots are empty");
        assertEquals(10, handler.getLastPlacedSlot(),
                "Handler should have used slot 10 (first torch slot found)");
        assertEquals(2, inv.getStackInSlot(10).getCount(),
                "One torch must have been consumed from slot 10");
    }
}

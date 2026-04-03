package com.sjviklabs.squire.inventory;

import com.sjviklabs.squire.entity.SquireTier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JUnit 5 unit tests for SquireItemHandler inventory logic.
 *
 * Uses TestableItemHandler (inner class) to avoid instantiating SquireEntity —
 * NeoForge DeferredHolder resolution is not available in the JUnit test environment
 * without a full game launch. The handler logic (slot gating, insert/extract guards)
 * is what is under test, not entity construction.
 *
 * Run: ./gradlew test
 */
class SquireItemHandlerTest {

    // ── Test double ───────────────────────────────────────────────────────────

    /**
     * Mirrors SquireItemHandler logic without depending on a live SquireEntity.
     * Accepts SquireTier directly — tests the tier-gating arithmetic in isolation.
     */
    private static class TestableItemHandler extends ItemStackHandler {
        private final SquireTier tier;

        TestableItemHandler(SquireTier tier) {
            // Allocate at Champion max (42 slots) — same strategy as production handler
            super(SquireItemHandler.EQUIPMENT_SLOTS + 36);
            this.tier = tier;
        }

        @Override
        public int getSlots() {
            return SquireItemHandler.EQUIPMENT_SLOTS + tier.getBackpackSlots();
        }

        public boolean isEquipmentSlot(int slot) {
            return slot < SquireItemHandler.EQUIPMENT_SLOTS;
        }

        public boolean isBackpackSlot(int slot) {
            return slot >= SquireItemHandler.EQUIPMENT_SLOTS && slot < getSlots();
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (slot >= getSlots()) return stack;
            return super.insertItem(slot, stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot >= getSlots()) return ItemStack.EMPTY;
            return super.extractItem(slot, amount, simulate);
        }

        @Override
        protected void onContentsChanged(int slot) {
            // No-op in tests — entity dirty marking not applicable here
        }
    }

    // ── Slot count per tier (TST-01 coverage requirement: all 5 tiers) ───────

    @Test
    void servantTierSlotCount() {
        var handler = new TestableItemHandler(SquireTier.SERVANT);
        assertEquals(15, handler.getSlots(),
                "SERVANT: 6 equipment + 9 backpack = 15");
    }

    @Test
    void apprenticeTierSlotCount() {
        var handler = new TestableItemHandler(SquireTier.APPRENTICE);
        assertEquals(24, handler.getSlots(),
                "APPRENTICE: 6 equipment + 18 backpack = 24");
    }

    @Test
    void squireTierSlotCount() {
        var handler = new TestableItemHandler(SquireTier.SQUIRE);
        assertEquals(33, handler.getSlots(),
                "SQUIRE: 6 equipment + 27 backpack = 33");
    }

    @Test
    void knightTierSlotCount() {
        var handler = new TestableItemHandler(SquireTier.KNIGHT);
        assertEquals(38, handler.getSlots(),
                "KNIGHT: 6 equipment + 32 backpack = 38");
    }

    @Test
    void championTierSlotCount() {
        var handler = new TestableItemHandler(SquireTier.CHAMPION);
        assertEquals(42, handler.getSlots(),
                "CHAMPION: 6 equipment + 36 backpack = 42");
    }

    // ── Slot classification ───────────────────────────────────────────────────

    @Test
    void isEquipmentSlot_boundaries() {
        var handler = new TestableItemHandler(SquireTier.SERVANT);
        assertTrue(handler.isEquipmentSlot(0),  "Slot 0 is equipment (helmet)");
        assertTrue(handler.isEquipmentSlot(5),  "Slot 5 is equipment (offhand)");
        assertFalse(handler.isEquipmentSlot(6), "Slot 6 is NOT equipment (first backpack)");
    }

    @Test
    void isBackpackSlot_servant_boundaries() {
        var handler = new TestableItemHandler(SquireTier.SERVANT);
        assertTrue(handler.isBackpackSlot(6),   "Slot 6 is backpack (first) for SERVANT");
        assertTrue(handler.isBackpackSlot(14),  "Slot 14 is backpack (last) for SERVANT");
        assertFalse(handler.isBackpackSlot(15), "Slot 15 is beyond SERVANT capacity");
        assertFalse(handler.isBackpackSlot(5),  "Slot 5 is equipment, not backpack");
    }

    // ── Insert / extract guards ───────────────────────────────────────────────

    @Test
    void insertItem_intoEquipmentSlot_succeeds() {
        var handler = new TestableItemHandler(SquireTier.SERVANT);
        var sword = new ItemStack(Items.IRON_SWORD, 1);
        var remainder = handler.insertItem(0, sword, false);
        assertEquals(0, remainder.getCount(), "Equipment slot 0 should accept an item");
    }

    @Test
    void insertItem_intoFirstBackpackSlot_succeeds() {
        var handler = new TestableItemHandler(SquireTier.SERVANT);
        var cobble = new ItemStack(Items.COBBLESTONE, 10);
        var remainder = handler.insertItem(6, cobble, false);
        assertEquals(0, remainder.getCount(), "First backpack slot (6) should accept an item");
    }

    @Test
    void insertItem_intoLastServantSlot_succeeds() {
        var handler = new TestableItemHandler(SquireTier.SERVANT);
        var cobble = new ItemStack(Items.COBBLESTONE, 1);
        var remainder = handler.insertItem(14, cobble, false);
        assertEquals(0, remainder.getCount(), "Last SERVANT backpack slot (14) should accept an item");
    }

    @Test
    void insertItem_beyondServantCapacity_returnsStackUnchanged() {
        var handler = new TestableItemHandler(SquireTier.SERVANT);
        var cobble = new ItemStack(Items.COBBLESTONE, 5);
        var remainder = handler.insertItem(15, cobble, false);
        assertEquals(5, remainder.getCount(),
                "Slot 15 is beyond SERVANT capacity — insert must be rejected, stack returned unchanged");
    }

    @Test
    void extractItem_fromEmptySlot_returnsEmpty() {
        var handler = new TestableItemHandler(SquireTier.SERVANT);
        var extracted = handler.extractItem(0, 1, false);
        assertTrue(extracted.isEmpty(), "Extracting from an empty slot should return EMPTY");
    }

    @Test
    void extractItem_beyondCapacity_returnsEmpty() {
        var handler = new TestableItemHandler(SquireTier.SERVANT);
        var extracted = handler.extractItem(15, 1, false);
        assertTrue(extracted.isEmpty(), "Extracting beyond SERVANT capacity should return EMPTY");
    }

    // ── EQUIPMENT_SLOTS constant ──────────────────────────────────────────────

    @Test
    void equipmentSlotsConstant_is6() {
        assertEquals(6, SquireItemHandler.EQUIPMENT_SLOTS,
                "EQUIPMENT_SLOTS must be 6 (helmet, chest, legs, boots, mainhand, offhand)");
    }
}

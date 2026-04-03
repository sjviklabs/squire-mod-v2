package com.sjviklabs.squire.inventory;

import com.sjviklabs.squire.entity.SquireTier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JUnit 5 unit tests for SquireMenu slot layout and quickMoveStack logic.
 *
 * Uses TestableItemHandler (package-private inner class) to drive SquireMenu without
 * a live SquireEntity or NeoForge bootstrap.
 *
 * TDD contract (GUI-03):
 *   RED  — tests fail because SquireMenu stub (Phase 1) has no MENU_EQUIPMENT_SLOTS
 *           and no equipment slots registered in the constructor.
 *   GREEN — tests pass after Task 2 extends SquireMenu with equipment slots and
 *           adds the test-visible constructor SquireMenu(SquireTier, TestableItemHandler).
 *
 * Run: ./gradlew test --tests "*.SquireMenuTest"
 *
 * Note on compilation: The test-only constructor SquireMenu(SquireTier, IItemHandler)
 * and the MENU_EQUIPMENT_SLOTS constant are expected by this test class. Both are added
 * in Task 2. Until then, this file will fail to compile — that is an acceptable RED
 * state (compile failure is a valid TDD failure).
 */
class SquireMenuTest {

    // ── Test double for SquireItemHandler ─────────────────────────────────────

    /**
     * Mirrors SquireItemHandler without requiring a live SquireEntity.
     * Accepts SquireTier directly — same pattern as SquireItemHandlerTest.
     */
    static class TestableItemHandler extends ItemStackHandler {
        private final SquireTier tier;

        TestableItemHandler(SquireTier tier) {
            super(SquireItemHandler.EQUIPMENT_SLOTS + 36);
            this.tier = tier;
        }

        @Override
        public int getSlots() {
            return SquireItemHandler.EQUIPMENT_SLOTS + tier.getBackpackSlots();
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
        protected void onContentsChanged(int slot) { /* no-op in tests */ }
    }

    // ── Helper: build a TestableMenu from tier ────────────────────────────────

    private SquireMenu menu(SquireTier tier) {
        return new SquireMenu(tier, new TestableItemHandler(tier));
    }

    // ── MENU_EQUIPMENT_SLOTS constant ─────────────────────────────────────────

    /**
     * SquireMenu must expose MENU_EQUIPMENT_SLOTS = 6.
     * RED: constant does not exist in Phase 1 stub — compile failure.
     */
    @Test
    void menuEquipmentSlotsConstant_is6() {
        assertEquals(6, SquireMenu.MENU_EQUIPMENT_SLOTS,
                "MENU_EQUIPMENT_SLOTS must equal 6");
    }

    // ── Total slot counts ─────────────────────────────────────────────────────

    /**
     * SERVANT: 6 equipment + 9 backpack + 27 player main + 9 hotbar = 51.
     * RED: Phase 1 stub registers 0 equipment + 9 backpack + 36 player = 45 slots.
     */
    @Test
    void servantMenuTotalSlots_51() {
        assertEquals(51, menu(SquireTier.SERVANT).slots.size(),
                "SERVANT menu must have 51 total slots");
    }

    /**
     * CHAMPION: 6 equipment + 36 backpack + 27 player main + 9 hotbar = 78.
     * RED: Phase 1 stub registers 36 backpack + 36 player = 72 slots.
     */
    @Test
    void championMenuTotalSlots_78() {
        assertEquals(78, menu(SquireTier.CHAMPION).slots.size(),
                "CHAMPION menu must have 78 total slots");
    }

    // ── Equipment slot type validation ────────────────────────────────────────

    /**
     * Menu slot 0 (helmet) must reject a pickaxe.
     * RED: Phase 1 stub has no slot at index 0 from equipment logic.
     */
    @Test
    void equipmentSlot0_rejectsPickaxe() {
        var m = menu(SquireTier.SERVANT);
        assertFalse(m.slots.get(0).mayPlace(new ItemStack(Items.IRON_PICKAXE)),
                "Helmet slot (menu index 0) must not accept a pickaxe");
    }

    /**
     * Menu slot 0 (helmet) must accept a helmet.
     * RED: Phase 1 stub has no equipment slot validation at index 0.
     */
    @Test
    void equipmentSlot0_acceptsHelmet() {
        var m = menu(SquireTier.SERVANT);
        assertTrue(m.slots.get(0).mayPlace(new ItemStack(Items.IRON_HELMET)),
                "Helmet slot (menu index 0) must accept a helmet");
    }

    /**
     * Menu slot 4 (mainhand) must accept any item — pickaxe, sword, or dirt.
     * RED: Phase 1 stub has no mainhand slot at index 4.
     */
    @Test
    void equipmentSlot4_mainhand_acceptsAnyItem() {
        var m = menu(SquireTier.SERVANT);
        assertTrue(m.slots.get(4).mayPlace(new ItemStack(Items.IRON_PICKAXE)),
                "Mainhand slot must accept a pickaxe");
        assertTrue(m.slots.get(4).mayPlace(new ItemStack(Items.IRON_SWORD)),
                "Mainhand slot must accept a sword");
        assertTrue(m.slots.get(4).mayPlace(new ItemStack(Items.DIRT)),
                "Mainhand slot must accept dirt");
    }

    // ── quickMoveStack routing ────────────────────────────────────────────────

    /**
     * quickMoveStack from equipment slot (index 0-5) must return a non-empty copy
     * of the moved stack (indicating the move was attempted, not silently rejected).
     *
     * We put a sword in handler slot 4 (mainhand — no type restriction) and call
     * quickMoveStack at menu index 4. The player inventory slots are empty so the
     * move should succeed, returning a copy of the original stack.
     *
     * RED: Phase 1 has wrong slot layout so index 4 is a backpack slot, and the
     * range logic targets the wrong range — test fails with unexpected behavior.
     */
    @Test
    void quickMoveStack_fromEquipmentSlot_returnsStackCopy() {
        var handler = new TestableItemHandler(SquireTier.SERVANT);
        handler.insertItem(SquireItemHandler.SLOT_MAINHAND, new ItemStack(Items.IRON_SWORD, 1), false);

        var m = new SquireMenu(SquireTier.SERVANT, handler);
        // Menu index 4 = mainhand equipment slot
        ItemStack result = m.quickMoveStack(null, SquireItemHandler.SLOT_MAINHAND);
        assertFalse(result.isEmpty(),
                "quickMoveStack from mainhand slot must return a copy of the moved stack");
    }

    /**
     * quickMoveStack from player inventory must attempt to route toward squire slots.
     * First player-inventory slot is at: MENU_EQUIPMENT_SLOTS + backpackSlots
     * For SERVANT: 6 + 9 = 15.
     *
     * RED: Phase 1 index math omits MENU_EQUIPMENT_SLOTS — computed offset is wrong.
     */
    @Test
    void quickMoveStack_fromPlayerInventory_usesCorrectOffset() {
        var handler = new TestableItemHandler(SquireTier.SERVANT);
        var m = new SquireMenu(SquireTier.SERVANT, handler);

        int firstPlayerSlot = SquireMenu.MENU_EQUIPMENT_SLOTS + SquireTier.SERVANT.getBackpackSlots();
        // Place cobblestone in that player slot
        m.slots.get(firstPlayerSlot).set(new ItemStack(Items.COBBLESTONE, 1));

        ItemStack result = m.quickMoveStack(null, firstPlayerSlot);
        assertFalse(result.isEmpty(),
                "quickMoveStack from player inventory slot must return a non-empty result");
    }
}

package com.sjviklabs.squire.inventory;

import com.sjviklabs.squire.SquireRegistry;
import com.sjviklabs.squire.entity.SquireEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.SlotItemHandler;

/**
 * AbstractContainerMenu stub for the squire's inventory.
 *
 * This class is intentionally minimal — it registers the correct slot layout
 * so item movement (shift-click, hopper automation) works in Phase 1.
 * The full client-side Screen and GUI textures are added in Phase 5 without
 * touching this menu class.
 *
 * Slot layout (as registered in this menu):
 *   Indices 0 .. backpackSlots-1       — squire backpack (SlotItemHandler, handler slots 6+)
 *   Indices backpackSlots .. +26       — player main inventory (27 slots)
 *   Indices backpackSlots+27 .. +8     — player hotbar (9 slots)
 *
 * Equipment slots (handler indices 0-5) are intentionally excluded from the menu
 * slot list in Phase 1. Phase 5 adds dedicated equipment slot UI.
 */
public class SquireMenu extends AbstractContainerMenu {

    private final SquireEntity squire;

    /**
     * Server-side constructor (called from SquireEntity.mobInteract via SimpleMenuProvider).
     *
     * @param windowId        container ID assigned by the server
     * @param playerInventory the opening player's inventory
     * @param squire          the squire entity whose inventory is being accessed
     */
    public SquireMenu(int windowId, Inventory playerInventory, SquireEntity squire) {
        super(SquireRegistry.SQUIRE_MENU.get(), windowId);
        this.squire = squire;
        SquireItemHandler handler = squire.getItemHandler();

        // ---- Squire backpack slots (handler indices 6 .. 6+backpackSlots-1) ----
        // Arranged in a row-major grid, 9 columns wide, matching a standard chest layout.
        // Phase 5 maps these to actual screen coordinates in the Screen class.
        int backpackSlots = squire.getTier().getBackpackSlots();
        for (int i = 0; i < backpackSlots; i++) {
            int handlerSlot = SquireItemHandler.EQUIPMENT_SLOTS + i;
            this.addSlot(new SlotItemHandler(handler, handlerSlot,
                8 + (i % 9) * 18,      // x — 9 columns, left-aligned
                18 + (i / 9) * 18));   // y — rows stack downward
        }

        // ---- Player main inventory (27 slots) ----
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9,
                    8 + col * 18,
                    100 + row * 18));
            }
        }

        // ---- Player hotbar (9 slots) ----
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col, 8 + col * 18, 158));
        }
    }

    /**
     * Standard shift-click: moves items between squire backpack and player inventory.
     * Equipment slots (0-5 in the handler) are not in the menu slot list yet — Phase 5.
     */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) return result;

        ItemStack slotStack = slot.getItem();
        result = slotStack.copy();

        int backpackSlots = squire.getTier().getBackpackSlots();

        if (index < backpackSlots) {
            // Squire backpack → player inventory
            if (!this.moveItemStackTo(slotStack, backpackSlots, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            // Player inventory → squire backpack
            if (!this.moveItemStackTo(slotStack, 0, backpackSlots, false)) {
                return ItemStack.EMPTY;
            }
        }

        if (slotStack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }

        return result;
    }

    /**
     * Menu stays open while the squire is alive and within 8 blocks.
     * Matches the distance check in SquireInventory.stillValid() from v0.5.0.
     */
    @Override
    public boolean stillValid(Player player) {
        return squire.isAlive() && squire.distanceTo(player) < 8.0;
    }
}

package com.sjviklabs.squire.inventory;

import com.sjviklabs.squire.entity.SquireEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;

/**
 * IItemHandler implementation for a squire's inventory.
 *
 * Slot layout (locked in CONTEXT.md):
 *   Slots 0-5:  Equipment (helmet, chestplate, leggings, boots, mainhand, offhand)
 *   Slots 6+:   Backpack (tier-gated: 9 at Servant through 36 at Champion)
 *
 * Total slots per tier:
 *   Servant:    6 + 9  = 15
 *   Apprentice: 6 + 18 = 24
 *   Squire:     6 + 27 = 33
 *   Knight:     6 + 32 = 38
 *   Champion:   6 + 36 = 42
 *
 * The handler is allocated at Champion max (42 slots) to avoid resize issues
 * on tier-up. getSlots() gates access to only the current tier's slots.
 * IItemHandler ENTITY and ENTITY_AUTOMATION capabilities both return this handler,
 * making the backpack slots accessible to hoppers and modded pipes.
 */
public class SquireItemHandler extends ItemStackHandler {

    public static final int EQUIPMENT_SLOTS = 6;
    public static final int SLOT_HELMET    = 0;
    public static final int SLOT_CHEST     = 1;
    public static final int SLOT_LEGS      = 2;
    public static final int SLOT_BOOTS     = 3;
    public static final int SLOT_MAINHAND  = 4;
    public static final int SLOT_OFFHAND   = 5;

    private final SquireEntity squire;

    /**
     * @param squire the owning entity — used for tier-gated slot count and dirty marking
     */
    public SquireItemHandler(SquireEntity squire) {
        // Allocate at Champion max (42 slots) — getSlots() gates access to current tier
        super(EQUIPMENT_SLOTS + 36);
        this.squire = squire;
    }

    /**
     * Returns the number of slots visible at the squire's current tier.
     * Always: 6 equipment + tier.getBackpackSlots().
     */
    @Override
    public int getSlots() {
        return EQUIPMENT_SLOTS + squire.getTier().getBackpackSlots();
    }

    /** Returns true if slot is an equipment slot (indices 0-5). */
    public boolean isEquipmentSlot(int slot) {
        return slot < EQUIPMENT_SLOTS;
    }

    /** Returns true if slot is a backpack slot (index 6+) within current tier capacity. */
    public boolean isBackpackSlot(int slot) {
        return slot >= EQUIPMENT_SLOTS && slot < getSlots();
    }

    @Override
    public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
        // Reject inserts beyond current tier capacity
        if (slot >= getSlots()) return stack;
        return super.insertItem(slot, stack, simulate);
    }

    @Override
    public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (slot >= getSlots()) return ItemStack.EMPTY;
        return super.extractItem(slot, amount, simulate);
    }

    /**
     * Directly set the contents of a slot, bypassing insertItem validation.
     * Used by SquireEntity.setItemSlot() to bridge vanilla equipment to the handler.
     * This is the only method that should write equipment slots directly.
     */
    public void setSlotContents(int slot, ItemStack stack) {
        this.stacks.set(slot, stack);
        onContentsChanged(slot);
    }

    @Override
    protected void onContentsChanged(int slot) {
        // Entity NBT is saved automatically by the level on the next save cycle.
        // No explicit dirty flag needed on PathfinderMob (that's a BlockEntity pattern).
        // This hook is a future extension point for open-menu sync in Phase 5.
    }
}

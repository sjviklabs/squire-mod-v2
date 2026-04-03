package com.sjviklabs.squire.inventory;

import com.sjviklabs.squire.SquireRegistry;
import com.sjviklabs.squire.entity.SquireEntity;
import com.sjviklabs.squire.entity.SquireTier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

import javax.annotation.Nullable;

/**
 * AbstractContainerMenu for the squire's inventory.
 *
 * Slot layout (menu indices):
 *   0 .. 5                              — equipment slots (helmet, chest, legs, boots, mainhand, offhand)
 *   MENU_EQUIPMENT_SLOTS .. +N-1        — squire backpack (N = tier.getBackpackSlots())
 *   MENU_EQUIPMENT_SLOTS+N .. +26       — player main inventory (27 slots)
 *   MENU_EQUIPMENT_SLOTS+N+27 .. +8     — player hotbar (9 slots)
 *
 * where N = backpackSlots for the squire's current tier.
 *
 * Handler slot mapping:
 *   Menu index 0 → handler slot 0 (SLOT_HELMET)
 *   Menu index 1 → handler slot 1 (SLOT_CHEST)
 *   Menu index 2 → handler slot 2 (SLOT_LEGS)
 *   Menu index 3 → handler slot 3 (SLOT_BOOTS)
 *   Menu index 4 → handler slot 4 (SLOT_MAINHAND)
 *   Menu index 5 → handler slot 5 (SLOT_OFFHAND)
 *   Menu index 6+ → handler slots 6+ (backpack, tier-gated)
 *
 * Screen layout coordinates match the v0.5.0 layout — see SquireScreen for pixel math.
 */
public class SquireMenu extends AbstractContainerMenu {

    // ── Constants ─────────────────────────────────────────────────────────────

    /** Number of equipment slots at the front of the menu slot list. */
    public static final int MENU_EQUIPMENT_SLOTS = 6;

    // Layout constants shared with SquireScreen
    public static final int ENTITY_AREA_WIDTH = 51;
    public static final int ARMOR_COL_X       = ENTITY_AREA_WIDTH + 1;  // 52
    public static final int BACKPACK_X        = ARMOR_COL_X + 22;       // 74
    public static final int WEAPON_COL_X      = BACKPACK_X + 9 * 18 + 4; // 240
    public static final int BACKPACK_Y        = 18;
    public static final int PLAYER_INV_X      = 30;

    // ── Fields ────────────────────────────────────────────────────────────────

    /** Live squire entity — null when constructed via the headless test constructor. */
    @Nullable
    private final SquireEntity squire;

    /** Number of backpack slots for the squire's current tier (cached at open time). */
    private final int backpackSlots;

    /** Tier index (0-3) used by the Screen for locked-row visuals. */
    private final int backpackTier;

    // ── Constructors ──────────────────────────────────────────────────────────

    /**
     * Live server-side constructor — called from SquireEntity.mobInteract via SimpleMenuProvider,
     * and from the client-side IContainerFactory in SquireRegistry.
     *
     * @param windowId        container ID assigned by the server
     * @param playerInventory the opening player's inventory
     * @param squire          the squire entity whose inventory is being accessed
     */
    public SquireMenu(int windowId, Inventory playerInventory, SquireEntity squire) {
        super(SquireRegistry.SQUIRE_MENU.get(), windowId);
        this.squire = squire;
        this.backpackSlots = squire.getTier().getBackpackSlots();
        this.backpackTier = tierIndexFromTier(squire.getTier());

        SquireItemHandler handler = squire.getItemHandler();
        buildSlots(handler, playerInventory);
    }

    /**
     * Test-only constructor — creates a headless menu from a SquireTier and an IItemHandler.
     * No NeoForge registry access, no live SquireEntity required.
     *
     * Used exclusively by SquireMenuTest. Not for production use.
     *
     * @param tier    the squire tier to use for slot count
     * @param handler the item handler to back the slots
     */
    SquireMenu(SquireTier tier, IItemHandler handler) {
        // windowId=0, null MenuType — headless for testing only
        super(null, 0);
        this.squire = null;
        this.backpackSlots = tier.getBackpackSlots();
        this.backpackTier = tierIndexFromTier(tier);
        buildSlots(handler, null);
    }

    // ── Slot registration ─────────────────────────────────────────────────────

    private void buildSlots(IItemHandler handler, @Nullable Inventory playerInventory) {
        addEquipmentSlots(handler);
        addBackpackSlots(handler);
        if (playerInventory != null) {
            addPlayerInventorySlots(playerInventory);
        } else {
            // Headless test: add 36 placeholder player slots (no Inventory needed for slot-count tests)
            addHeadlessPlayerSlots();
        }
    }

    /**
     * Registers the 6 equipment slots at menu indices 0-5.
     * Each slot has a mayPlace() override that enforces item type restrictions.
     *
     * Screen coordinates:
     *   Helmet, chest, legs, boots — armor column at x=ARMOR_COL_X, rows from BACKPACK_Y.
     *   Mainhand, offhand          — weapon column at x=WEAPON_COL_X, rows from BACKPACK_Y.
     */
    private void addEquipmentSlots(IItemHandler handler) {
        // Armor column — 4 slots stacked vertically
        this.addSlot(new EquipmentSlotTyped(handler, SquireItemHandler.SLOT_HELMET,
                ARMOR_COL_X, BACKPACK_Y, EquipmentSlotTyped.Type.HELMET));
        this.addSlot(new EquipmentSlotTyped(handler, SquireItemHandler.SLOT_CHEST,
                ARMOR_COL_X, BACKPACK_Y + 18, EquipmentSlotTyped.Type.CHESTPLATE));
        this.addSlot(new EquipmentSlotTyped(handler, SquireItemHandler.SLOT_LEGS,
                ARMOR_COL_X, BACKPACK_Y + 36, EquipmentSlotTyped.Type.LEGGINGS));
        this.addSlot(new EquipmentSlotTyped(handler, SquireItemHandler.SLOT_BOOTS,
                ARMOR_COL_X, BACKPACK_Y + 54, EquipmentSlotTyped.Type.BOOTS));

        // Weapon column — mainhand on top, offhand below
        this.addSlot(new EquipmentSlotTyped(handler, SquireItemHandler.SLOT_MAINHAND,
                WEAPON_COL_X, BACKPACK_Y, EquipmentSlotTyped.Type.MAINHAND));
        this.addSlot(new EquipmentSlotTyped(handler, SquireItemHandler.SLOT_OFFHAND,
                WEAPON_COL_X, BACKPACK_Y + 18, EquipmentSlotTyped.Type.OFFHAND));
    }

    /**
     * Registers backpack slots starting at menu index MENU_EQUIPMENT_SLOTS.
     * Handler slots start at SquireItemHandler.EQUIPMENT_SLOTS (index 6).
     * Grid is 9 columns wide, rows stack downward from BACKPACK_Y.
     */
    private void addBackpackSlots(IItemHandler handler) {
        int rows = rowsForTier(backpackTier);
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < 9; col++) {
                int handlerSlot = SquireItemHandler.EQUIPMENT_SLOTS + col + row * 9;
                this.addSlot(new SlotItemHandler(handler, handlerSlot,
                        BACKPACK_X + col * 18,
                        BACKPACK_Y + row * 18));
            }
        }
    }

    /**
     * Registers the standard 27 + 9 player inventory slots.
     */
    private void addPlayerInventorySlots(Inventory playerInventory) {
        int playerInvY = getPlayerInvY();
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9,
                        PLAYER_INV_X + col * 18,
                        playerInvY + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col,
                    PLAYER_INV_X + col * 18,
                    playerInvY + 58));
        }
    }

    /**
     * Headless player slot registration for unit tests.
     * Adds 36 empty Slot objects backed by a SimpleContainer.
     */
    private void addHeadlessPlayerSlots() {
        net.minecraft.world.SimpleContainer dummy = new net.minecraft.world.SimpleContainer(36);
        // 27 main inventory slots
        for (int i = 0; i < 27; i++) {
            this.addSlot(new Slot(dummy, i, 0, 0));
        }
        // 9 hotbar slots
        for (int i = 27; i < 36; i++) {
            this.addSlot(new Slot(dummy, i, 0, 0));
        }
    }

    // ── quickMoveStack (shift-click) ──────────────────────────────────────────

    /**
     * Handles shift-click transfer across 4 directions:
     *
     *   Equipment slot (0..MENU_EQUIPMENT_SLOTS-1):
     *     → player inventory (backpackEnd..slots.size())
     *
     *   Backpack slot (MENU_EQUIPMENT_SLOTS..backpackEnd-1):
     *     → player inventory (backpackEnd..slots.size())
     *
     *   Player inventory / hotbar (backpackEnd..slots.size()-1):
     *     → try equipment first (type validation via mayPlace), then backpack
     *
     * Uses AbstractContainerMenu.moveItemStackTo — not a manual loop.
     * Constant MENU_EQUIPMENT_SLOTS is the single source of truth for index offsets.
     */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) return result;

        ItemStack slotStack = slot.getItem();
        result = slotStack.copy();

        int backpackEnd = MENU_EQUIPMENT_SLOTS + backpackSlots;
        int playerEnd   = backpackEnd + 36; // 27 main + 9 hotbar

        if (index < backpackEnd) {
            // Equipment or backpack slot → move to player inventory
            if (!this.moveItemStackTo(slotStack, backpackEnd, playerEnd, true)) {
                return ItemStack.EMPTY;
            }
        } else {
            // Player inventory or hotbar → try equipment first, then backpack
            boolean moved = this.moveItemStackTo(slotStack, 0, MENU_EQUIPMENT_SLOTS, false);
            if (!moved) {
                moved = this.moveItemStackTo(slotStack, MENU_EQUIPMENT_SLOTS, backpackEnd, false);
            }
            if (!moved) {
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
     * Returns true in headless test context (no squire entity).
     */
    @Override
    public boolean stillValid(Player player) {
        if (squire == null) return true; // headless test
        return squire.isAlive() && squire.distanceTo(player) < 8.0;
    }

    // ── Layout helpers ────────────────────────────────────────────────────────

    /** Y position where player inventory starts (constant — always below 4 backpack rows). */
    public int getPlayerInvY() {
        return BACKPACK_Y + 4 * 18 + 14;
    }

    /** Total GUI height. */
    public int getGuiHeight() {
        return getPlayerInvY() + 3 * 18 + 4 + 18 + 4;
    }

    /** Total GUI width. */
    public int getGuiWidth() {
        return WEAPON_COL_X + 18 + 8;
    }

    /**
     * Backpack tier index (0=Servant, 1=Apprentice, 2=Squire/Knight, 3=Champion).
     * Used by SquireScreen for locked-row visuals and tier labels.
     */
    public int getBackpackTier() {
        return backpackTier;
    }

    public int getBackpackSlots() {
        return backpackSlots;
    }

    /** Squire level — 0 if no live entity (headless/test). */
    public int getSquireLevel() {
        return squire != null ? squire.getLevel() : 0;
    }

    /** Squire total XP — 0 if no live entity. */
    public int getTotalXP() {
        return squire != null ? squire.getTotalXP() : 0;
    }

    /** Squire current health — 0 if no live entity. */
    public float getHealthCurrent() {
        return squire != null ? squire.getHealth() : 0f;
    }

    /** Squire max health — 20 if no live entity. */
    public float getHealthMax() {
        return squire != null ? squire.getMaxHealth() : 20f;
    }

    /** Squire mode byte (0=follow, 1=sit, 2=guard) — 0 if no live entity. */
    public byte getSquireMode() {
        return squire != null ? squire.getSquireMode() : 0;
    }

    /** Squire entity network ID — -1 if no live entity. */
    public int getSquireEntityId() {
        return squire != null ? squire.getId() : -1;
    }

    // ── Static helpers ────────────────────────────────────────────────────────

    /** Number of backpack rows for a given tier index (0-3). */
    public static int rowsForTier(int tierIndex) {
        return tierIndex + 1;
    }

    /**
     * Maps a SquireTier enum to a 0-based tier index (0=Servant..3=Champion).
     * Used for locked-row visual math in SquireScreen.
     */
    private static int tierIndexFromTier(SquireTier tier) {
        return switch (tier) {
            case SERVANT    -> 0;
            case APPRENTICE -> 1;
            case SQUIRE     -> 2;
            case KNIGHT     -> 2; // Knight uses 32 slots — treated as row 3 (4 rows unlocked)
            case CHAMPION   -> 3;
        };
    }

    // ── Inner class: equipment slot with type validation ──────────────────────

    /**
     * SlotItemHandler subclass that restricts which item types may be placed.
     *
     * Equipment type is one of HELMET, CHESTPLATE, LEGGINGS, BOOTS, MAINHAND, OFFHAND.
     *
     * Armor slots validate by checking ArmorItem.getType() — this avoids the NeoForge
     * canEquip(stack, slot, entity) overload which requires a non-null LivingEntity.
     * ArmorItem.Type.getSlot() gives the EquipmentSlot without entity context.
     *
     * Hand slots (MAINHAND, OFFHAND) accept any item — no restriction.
     */
    private static class EquipmentSlotTyped extends SlotItemHandler {

        enum Type { HELMET, CHESTPLATE, LEGGINGS, BOOTS, MAINHAND, OFFHAND }

        private final Type allowedType;

        EquipmentSlotTyped(IItemHandler handler, int index, int x, int y, Type type) {
            super(handler, index, x, y);
            this.allowedType = type;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            if (stack.isEmpty()) return false;
            return switch (allowedType) {
                case HELMET     -> isArmorOfType(stack, ArmorItem.Type.HELMET);
                case CHESTPLATE -> isArmorOfType(stack, ArmorItem.Type.CHESTPLATE);
                case LEGGINGS   -> isArmorOfType(stack, ArmorItem.Type.LEGGINGS);
                case BOOTS      -> isArmorOfType(stack, ArmorItem.Type.BOOTS);
                case MAINHAND, OFFHAND -> true;
            };
        }

        /**
         * Returns true if the stack is an ArmorItem of the specified ArmorItem.Type.
         * Uses direct ArmorItem.getType() comparison — no entity required.
         */
        private static boolean isArmorOfType(ItemStack stack, ArmorItem.Type expected) {
            if (stack.getItem() instanceof ArmorItem armorItem) {
                return armorItem.getType() == expected;
            }
            return false;
        }
    }
}

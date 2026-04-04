package com.sjviklabs.squire.inventory;

import com.sjviklabs.squire.entity.SquireEntity;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.EnchantmentTags;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;

import javax.annotation.Nullable;

/**
 * Static utility class for comparing equipment quality and auto-equipping
 * the best armor, weapon, and shield from a squire's IItemHandler inventory.
 *
 * CRITICAL: Never mutate the stack returned by getStackInSlot(). It is a live reference.
 * Always use extractItem(i, n, false) to remove and insertItem(i, stack, false) to add.
 *
 * Slot layout (SquireItemHandler):
 *   Slots 0-5:  Equipment (SLOT_HELMET, SLOT_CHEST, SLOT_LEGS, SLOT_BOOTS, SLOT_MAINHAND, SLOT_OFFHAND)
 *   Slots 6+:   Backpack (tier-gated)
 */
public final class SquireEquipmentHelper {

    private SquireEquipmentHelper() {}

    // ------------------------------------------------------------------
    // Public entry points
    // ------------------------------------------------------------------

    /**
     * Check whether {@code newItem} is an upgrade over the squire's current equipment
     * in the relevant slot. If yes, swap: old equipment goes back to backpack,
     * new item is equipped. Called when a squire picks up an item.
     */
    public static void tryAutoEquip(SquireEntity squire, ItemStack newItem) {
        if (newItem.isEmpty() || isCursed(newItem)) return;

        IItemHandler handler = squire.getCapability(Capabilities.ItemHandler.ENTITY);
        if (handler == null) return;

        // Armor
        EquipmentSlot armorSlot = getArmorSlot(newItem);
        if (armorSlot != null) {
            ItemStack current = squire.getItemBySlot(armorSlot);
            if (isBetterArmor(newItem, current, armorSlot)) {
                swapEquipment(squire, handler, armorSlot, newItem);
            }
            return;
        }

        // Shield
        if (isShield(newItem)) {
            ItemStack currentOffhand = squire.getItemBySlot(EquipmentSlot.OFFHAND);
            if (!isShield(currentOffhand) || isCursed(currentOffhand)) {
                swapEquipment(squire, handler, EquipmentSlot.OFFHAND, newItem);
            }
            return;
        }

        // Bow
        if (newItem.getItem() instanceof BowItem) {
            ItemStack currentMainhand = squire.getItemBySlot(EquipmentSlot.MAINHAND);
            if (!(currentMainhand.getItem() instanceof BowItem)) {
                swapEquipment(squire, handler, EquipmentSlot.MAINHAND, newItem);
            }
            return;
        }

        // Melee weapon (sword or axe)
        if (isMeleeWeapon(newItem)) {
            ItemStack currentMainhand = squire.getItemBySlot(EquipmentSlot.MAINHAND);
            if (isBetterWeapon(newItem, currentMainhand)) {
                swapEquipment(squire, handler, EquipmentSlot.MAINHAND, newItem);
            }
        }
    }

    /**
     * Full inventory scan: equip the best armor in each slot, best weapon in
     * mainhand, and a shield in offhand. Called periodically on a tick interval.
     */
    public static void runFullEquipCheck(SquireEntity squire) {
        IItemHandler handler = squire.getCapability(Capabilities.ItemHandler.ENTITY);
        if (handler == null) return;

        // --- Armor slots ---
        for (EquipmentSlot slot : new EquipmentSlot[]{
                EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            int bestIdx = -1;
            ItemStack bestStack = squire.getItemBySlot(slot);

            for (int i = SquireItemHandler.EQUIPMENT_SLOTS; i < handler.getSlots(); i++) {
                ItemStack candidate = handler.getStackInSlot(i); // READ ONLY
                if (candidate.isEmpty()) continue;
                EquipmentSlot candidateSlot = getArmorSlot(candidate);
                if (candidateSlot != slot) continue;
                if (isCursed(candidate)) continue;
                if (isBetterArmor(candidate, bestStack, slot)) {
                    bestStack = candidate;
                    bestIdx = i;
                }
            }

            if (bestIdx >= 0) {
                swapEquipmentFromSlot(squire, handler, slot, bestIdx);
            }
        }

        // --- Mainhand weapon ---
        // Prefer bow if one is in mainhand already; otherwise equip best melee weapon.
        {
            ItemStack currentMainhand = squire.getItemBySlot(EquipmentSlot.MAINHAND);
            boolean holdingBow = currentMainhand.getItem() instanceof BowItem;

            if (!holdingBow) {
                // Check backpack for a bow first (prefer ranged if available and not melee-locked)
                boolean foundBow = false;
                for (int i = SquireItemHandler.EQUIPMENT_SLOTS; i < handler.getSlots(); i++) {
                    ItemStack candidate = handler.getStackInSlot(i); // READ ONLY
                    if (!candidate.isEmpty() && candidate.getItem() instanceof BowItem && !isCursed(candidate)) {
                        // Only equip bow if no melee weapon already equipped (melee takes priority over empty hand)
                        if (currentMainhand.isEmpty()) {
                            swapEquipmentFromSlot(squire, handler, EquipmentSlot.MAINHAND, i);
                            foundBow = true;
                        }
                        break;
                    }
                }

                if (!foundBow) {
                    // Standard melee weapon selection
                    int bestIdx = -1;
                    ItemStack bestWeapon = currentMainhand;

                    for (int i = SquireItemHandler.EQUIPMENT_SLOTS; i < handler.getSlots(); i++) {
                        ItemStack candidate = handler.getStackInSlot(i); // READ ONLY
                        if (candidate.isEmpty()) continue;
                        if (!isMeleeWeapon(candidate)) continue;
                        if (isCursed(candidate)) continue;
                        if (isBetterWeapon(candidate, bestWeapon)) {
                            bestWeapon = candidate;
                            bestIdx = i;
                        }
                    }

                    if (bestIdx >= 0) {
                        swapEquipmentFromSlot(squire, handler, EquipmentSlot.MAINHAND, bestIdx);
                    }
                }
            }
        }

        // --- Offhand shield ---
        // Stow shield when bow is in mainhand (can't use both). Equip shield otherwise.
        {
            ItemStack finalMainhand = squire.getItemBySlot(EquipmentSlot.MAINHAND);
            boolean holdingBow = finalMainhand.getItem() instanceof BowItem;
            ItemStack currentOffhand = squire.getItemBySlot(EquipmentSlot.OFFHAND);

            if (holdingBow && isShield(currentOffhand)) {
                // Stow shield into backpack while using bow
                squire.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
                insertIntoBackpack(handler, currentOffhand);
            } else if (!holdingBow && !isShield(currentOffhand)) {
                // Not holding bow — equip a shield if available
                for (int i = SquireItemHandler.EQUIPMENT_SLOTS; i < handler.getSlots(); i++) {
                    ItemStack candidate = handler.getStackInSlot(i); // READ ONLY
                    if (isShield(candidate) && !isCursed(candidate)) {
                        swapEquipmentFromSlot(squire, handler, EquipmentSlot.OFFHAND, i);
                        break;
                    }
                }
            }
        }
    }

    /**
     * Force-switch from ranged to melee loadout: stow bow, equip best melee weapon,
     * equip shield. Called when transitioning out of COMBAT_RANGED.
     */
    public static void switchToMeleeLoadout(SquireEntity squire) {
        IItemHandler handler = squire.getCapability(Capabilities.ItemHandler.ENTITY);
        if (handler == null) return;

        // Stow bow, equip best melee weapon
        ItemStack currentMainhand = squire.getItemBySlot(EquipmentSlot.MAINHAND);
        if (currentMainhand.getItem() instanceof BowItem) {
            squire.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            insertIntoBackpack(handler, currentMainhand);
        }

        // Find best melee weapon in backpack
        ItemStack bestWeapon = squire.getItemBySlot(EquipmentSlot.MAINHAND);
        int bestIdx = -1;
        for (int i = SquireItemHandler.EQUIPMENT_SLOTS; i < handler.getSlots(); i++) {
            ItemStack candidate = handler.getStackInSlot(i); // READ ONLY
            if (candidate.isEmpty()) continue;
            if (!isMeleeWeapon(candidate)) continue;
            if (isCursed(candidate)) continue;
            if (isBetterWeapon(candidate, bestWeapon)) {
                bestWeapon = candidate;
                bestIdx = i;
            }
        }
        if (bestIdx >= 0) {
            swapEquipmentFromSlot(squire, handler, EquipmentSlot.MAINHAND, bestIdx);
        }

        // Equip shield from backpack
        ItemStack currentOffhand = squire.getItemBySlot(EquipmentSlot.OFFHAND);
        if (!isShield(currentOffhand)) {
            for (int i = SquireItemHandler.EQUIPMENT_SLOTS; i < handler.getSlots(); i++) {
                ItemStack candidate = handler.getStackInSlot(i); // READ ONLY
                if (isShield(candidate) && !isCursed(candidate)) {
                    swapEquipmentFromSlot(squire, handler, EquipmentSlot.OFFHAND, i);
                    break;
                }
            }
        }
    }

    /**
     * Force-equip a bow from backpack into mainhand, stowing shield.
     * Called when entering COMBAT_RANGED state.
     *
     * @return true if a bow was equipped (or already in mainhand)
     */
    public static boolean switchToRangedLoadout(SquireEntity squire) {
        IItemHandler handler = squire.getCapability(Capabilities.ItemHandler.ENTITY);
        if (handler == null) return false;

        ItemStack currentMainhand = squire.getItemBySlot(EquipmentSlot.MAINHAND);

        // Already holding a bow — just stow shield
        if (currentMainhand.getItem() instanceof BowItem) {
            stowShieldIfNeeded(squire, handler);
            return true;
        }

        // Search backpack for a bow
        for (int i = SquireItemHandler.EQUIPMENT_SLOTS; i < handler.getSlots(); i++) {
            ItemStack candidate = handler.getStackInSlot(i); // READ ONLY
            if (!candidate.isEmpty() && candidate.getItem() instanceof BowItem && !isCursed(candidate)) {
                swapEquipmentFromSlot(squire, handler, EquipmentSlot.MAINHAND, i);
                stowShieldIfNeeded(squire, handler);
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Comparison helpers
    // ------------------------------------------------------------------

    /**
     * Compare armor by combined defense + toughness. Cursed candidates are rejected.
     */
    public static boolean isBetterArmor(ItemStack candidate, ItemStack current, EquipmentSlot slot) {
        if (candidate.isEmpty() || isCursed(candidate)) return false;
        if (!(candidate.getItem() instanceof ArmorItem candidateArmor)) return false;
        if (candidateArmor.getEquipmentSlot() != slot) return false;

        double candidateScore = candidateArmor.getDefense() + candidateArmor.getToughness();

        if (current.isEmpty() || !(current.getItem() instanceof ArmorItem currentArmor)) {
            return true; // Anything beats empty
        }

        double currentScore = currentArmor.getDefense() + currentArmor.getToughness();
        return candidateScore > currentScore;
    }

    /**
     * Compare weapons by attack damage attribute. At equal damage, prefer SwordItem
     * over AxeItem (swords swing faster). Cursed candidates are rejected.
     */
    public static boolean isBetterWeapon(ItemStack candidate, ItemStack current) {
        if (candidate.isEmpty() || isCursed(candidate)) return false;

        double candidateDmg = getAttackDamage(candidate);
        double currentDmg = getAttackDamage(current);

        if (candidateDmg > currentDmg) return true;
        if (candidateDmg == currentDmg) {
            boolean candidateIsSword = candidate.getItem() instanceof SwordItem;
            boolean currentIsSword = current.getItem() instanceof SwordItem;
            return candidateIsSword && !currentIsSword;
        }
        return false;
    }

    /**
     * @return true if the item is a melee weapon (SwordItem or AxeItem)
     */
    public static boolean isMeleeWeapon(ItemStack stack) {
        if (stack.isEmpty()) return false;
        Item item = stack.getItem();
        // Direct weapon types
        if (item instanceof SwordItem || item instanceof AxeItem) return true;
        // Modded weapons (multi-tools, paxels, etc.) — check for attack damage attribute
        // but exclude armor, bows, shields, and block items to avoid equipping tools/blocks as weapons
        if (item instanceof ArmorItem || item instanceof BowItem || item instanceof ShieldItem) return false;
        if (item instanceof net.minecraft.world.item.BlockItem) return false;
        // Check if item has attack damage > 0 via attributes
        var attrs = stack.getAttributeModifiers();
        if (attrs == null) return false;
        return attrs.modifiers().stream().anyMatch(e ->
                e.attribute().is(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE));
    }

    /**
     * @return true if the item is a ShieldItem
     */
    public static boolean isShield(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof ShieldItem;
    }

    /**
     * @return the EquipmentSlot for an ArmorItem, or null if not armor
     */
    @Nullable
    public static EquipmentSlot getArmorSlot(ItemStack stack) {
        if (stack.isEmpty()) return null;
        if (stack.getItem() instanceof ArmorItem armorItem) {
            return armorItem.getEquipmentSlot();
        }
        return null;
    }

    /**
     * Check for Curse of Binding or Curse of Vanishing via the EnchantmentTags#CURSE tag.
     */
    public static boolean isCursed(ItemStack stack) {
        if (stack.isEmpty()) return false;
        ItemEnchantments enchantments = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        for (var entry : enchantments.entrySet()) {
            Holder<Enchantment> holder = entry.getKey();
            if (holder.is(EnchantmentTags.CURSE)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------

    /**
     * Extract the total attack damage from an item's attribute modifiers.
     * Sums all ADD_VALUE modifiers for ATTACK_DAMAGE on the MAINHAND slot.
     * Returns 0 if no attack damage modifier is found (or item is empty).
     */
    private static double getAttackDamage(ItemStack stack) {
        if (stack.isEmpty()) return 0.0;

        ItemAttributeModifiers modifiers = stack.getAttributeModifiers();
        double[] total = {0.0};
        modifiers.forEach(EquipmentSlot.MAINHAND, (Holder<Attribute> attr, AttributeModifier mod) -> {
            if (attr.equals(Attributes.ATTACK_DAMAGE)) {
                if (mod.operation() == AttributeModifier.Operation.ADD_VALUE) {
                    total[0] += mod.amount();
                }
            }
        });
        return total[0];
    }

    /**
     * Swap: unequip current item in slot (put it in backpack), equip newItem
     * by finding and extracting it from the handler.
     * Used by tryAutoEquip for items just picked up (already in handler).
     */
    private static void swapEquipment(SquireEntity squire, IItemHandler handler,
                                       EquipmentSlot slot, ItemStack newItem) {
        ItemStack old = squire.getItemBySlot(slot);

        // Find the new item in backpack by identity and extract it
        for (int i = SquireItemHandler.EQUIPMENT_SLOTS; i < handler.getSlots(); i++) {
            ItemStack candidate = handler.getStackInSlot(i); // READ ONLY — do not mutate
            if (ItemStack.isSameItemSameComponents(candidate, newItem) && candidate.getCount() > 0) {
                ItemStack extracted = handler.extractItem(i, 1, false);
                if (!extracted.isEmpty()) {
                    squire.setItemSlot(slot, extracted);
                    playEquipSound(squire, slot);
                    // Return old item to backpack
                    if (!old.isEmpty()) {
                        insertIntoBackpack(handler, old);
                    }
                    return;
                }
            }
        }
    }

    /**
     * Swap equipment using a known handler slot index. Used by runFullEquipCheck
     * where we already know which slot has the best item.
     */
    private static void swapEquipmentFromSlot(SquireEntity squire, IItemHandler handler,
                                               EquipmentSlot slot, int handlerIdx) {
        ItemStack old = squire.getItemBySlot(slot);
        // extractItem does the actual removal — READ ONLY constraint does not apply here
        ItemStack newItem = handler.extractItem(handlerIdx, handler.getStackInSlot(handlerIdx).getCount(), false);

        squire.setItemSlot(slot, newItem);
        playEquipSound(squire, slot);

        if (!old.isEmpty()) {
            insertIntoBackpack(handler, old);
        }
    }

    /**
     * Insert a stack into the first available backpack slot (slots EQUIPMENT_SLOTS+).
     * Loops until remainder is empty or no slot accepts it.
     */
    private static void insertIntoBackpack(IItemHandler handler, ItemStack stack) {
        ItemStack remainder = stack.copy();
        for (int i = SquireItemHandler.EQUIPMENT_SLOTS; i < handler.getSlots() && !remainder.isEmpty(); i++) {
            remainder = handler.insertItem(i, remainder, false);
        }
        // If remainder is not empty, the backpack is full — item is lost (acceptable edge case).
    }

    /**
     * Stow shield from offhand into backpack (bow needs both hands).
     */
    private static void stowShieldIfNeeded(SquireEntity squire, IItemHandler handler) {
        ItemStack offhand = squire.getItemBySlot(EquipmentSlot.OFFHAND);
        if (isShield(offhand)) {
            squire.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
            insertIntoBackpack(handler, offhand);
        }
    }

    /**
     * Play an appropriate equip sound based on the equipment slot type.
     */
    private static void playEquipSound(SquireEntity squire, EquipmentSlot slot) {
        switch (slot) {
            case HEAD, CHEST, LEGS, FEET ->
                    squire.playSound(SoundEvents.ARMOR_EQUIP_IRON.value(), 1.0F, 1.0F);
            case MAINHAND, OFFHAND ->
                    squire.playSound(SoundEvents.ARMOR_EQUIP_GENERIC.value(), 0.8F, 1.0F);
        }
    }
}

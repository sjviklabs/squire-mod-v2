package com.sjviklabs.squire.ai.job;

import com.sjviklabs.squire.entity.SquireEntity;
import com.sjviklabs.squire.inventory.SquireEquipmentHelper;
import com.sjviklabs.squire.inventory.SquireItemHandler;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

/**
 * Gear classification + scoring shared by ChestAI (deposit keep-best) and RestockAI
 * (pull-missing). Extracted to a separate class because both AIs need the exact same
 * category definitions and scoring function — if the two diverged we'd deposit an item
 * we'd never pull back, and vice versa.
 *
 * <p>One category per "thing the squire only needs one good copy of." Axes are their own
 * category rather than collapsed into MELEE_WEAPON because LumberjackAI needs an axe to
 * chop trees — a squire may legitimately hold both a diamond sword (MELEE) and iron axe
 * (AXE), and keep-best must preserve both.
 */
public final class GearCategorizer {

    private GearCategorizer() {}

    public enum Category {
        ARMOR_HEAD, ARMOR_CHEST, ARMOR_LEGS, ARMOR_FEET,
        MELEE_WEAPON,   // sword
        AXE,            // axe — weapon + tree-chopping
        PICKAXE, SHOVEL, HOE,
        BOW, CROSSBOW, TRIDENT,
        FISHING_ROD, SHIELD
    }

    /**
     * Map a stack to its category bucket. Returns {@code null} for non-gear (logs, stone,
     * seeds, fish, etc.) — those fall through to deposit unconditionally and are not
     * restock candidates.
     *
     * <p>Check order matters: SwordItem before AxeItem before PickaxeItem / ShovelItem /
     * HoeItem, all before the DiggerItem catch-all. Modded tools that extend the specific
     * vanilla subclass land in the right bucket; anything extending only DiggerItem is
     * treated as pickaxe-ish so we at least preserve one.
     */
    @Nullable
    public static Category classify(ItemStack stack) {
        Item item = stack.getItem();
        if (item instanceof ArmorItem ai) {
            return switch (ai.getEquipmentSlot()) {
                case HEAD -> Category.ARMOR_HEAD;
                case CHEST -> Category.ARMOR_CHEST;
                case LEGS -> Category.ARMOR_LEGS;
                case FEET -> Category.ARMOR_FEET;
                default -> null;
            };
        }
        if (item instanceof ShieldItem) return Category.SHIELD;
        if (item instanceof BowItem) return Category.BOW;
        if (item instanceof CrossbowItem) return Category.CROSSBOW;
        if (item instanceof TridentItem) return Category.TRIDENT;
        if (item instanceof FishingRodItem) return Category.FISHING_ROD;
        if (item instanceof SwordItem) return Category.MELEE_WEAPON;
        if (item instanceof AxeItem) return Category.AXE;
        if (item instanceof PickaxeItem) return Category.PICKAXE;
        if (item instanceof ShovelItem) return Category.SHOVEL;
        if (item instanceof HoeItem) return Category.HOE;
        if (item instanceof DiggerItem) return Category.PICKAXE;
        return null;
    }

    /**
     * Rank gear within a category. Higher score = better. Cursed items short-circuit to
     * {@link Integer#MIN_VALUE} so any non-cursed copy wins — but if every copy is cursed,
     * all share the same floor and the first one encountered "wins" by default.
     *
     * <p>Scoring weights:
     * <ul>
     *   <li>Tier level × 100,000 — netherite &gt; diamond &gt; iron &gt; stone &gt; wood dominates</li>
     *   <li>Armor defense × 10,000 + toughness × 1,000 — on the same scale as tier</li>
     *   <li>Enchantment count × 500 — enchanted beats unenchanted of same tier</li>
     *   <li>Durability remaining (1 per hp) — pristine beats used of otherwise equal gear</li>
     * </ul>
     */
    public static int scoreStack(ItemStack stack) {
        if (SquireEquipmentHelper.isCursed(stack)) return Integer.MIN_VALUE;

        int score = 0;
        Item item = stack.getItem();

        if (item instanceof DiggerItem di) {
            score += tierScore(di.getTier());
        } else if (item instanceof SwordItem sw) {
            score += tierScore(sw.getTier());
        } else if (item instanceof ArmorItem ai) {
            score += ai.getDefense() * 10_000;
            score += (int) (ai.getToughness() * 1_000);
        }

        int max = stack.getMaxDamage();
        if (max > 0) score += (max - stack.getDamageValue());

        ItemEnchantments ench = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        score += ench.size() * 500;

        return score;
    }

    /**
     * Find all gear categories the squire has zero coverage for — neither equipped nor in
     * backpack. RestockAI pulls one of each missing category from the last-deposit chest.
     *
     * <p>Equipped slots count as coverage: a pickaxe in mainhand covers PICKAXE even if
     * the backpack has none. If the equipped pickaxe breaks mid-work, its slot becomes
     * {@link ItemStack#EMPTY} and the category flips to missing on the next check.
     */
    public static EnumSet<Category> missingCategories(SquireEntity squire, SquireItemHandler backpack) {
        EnumSet<Category> covered = EnumSet.noneOf(Category.class);

        // Equipment slots (mainhand, offhand, 4 armor) — SquireItemHandler's first 6 slots.
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot == EquipmentSlot.BODY) continue;   // horse-armor slot, unused
            ItemStack s = squire.getItemBySlot(slot);
            if (s.isEmpty()) continue;
            Category cat = classify(s);
            if (cat != null) covered.add(cat);
        }

        // Backpack (slots ≥ EQUIPMENT_SLOTS).
        for (int i = SquireItemHandler.EQUIPMENT_SLOTS; i < backpack.getSlots(); i++) {
            ItemStack s = backpack.getStackInSlot(i);
            if (s.isEmpty()) continue;
            Category cat = classify(s);
            if (cat != null) covered.add(cat);
        }

        EnumSet<Category> missing = EnumSet.allOf(Category.class);
        missing.removeAll(covered);
        return missing;
    }

    /**
     * Rank a tier so netherite &gt; diamond &gt; iron &gt; stone &gt; gold &gt; wood. In 1.21.1 the
     * {@code Tier} interface no longer exposes {@code getLevel()}, so we read the enum
     * ordinal for vanilla {@link Tiers} (authoritative game-progression order) and fall
     * back to attack-damage bonus × 10,000 for modded tiers that don't use the vanilla enum.
     *
     * <p>Tiers.ordinal() gives WOOD=0, GOLD=1, STONE=2, IRON=3, DIAMOND=4, NETHERITE=5 —
     * which matches the player's intuition about tier strength (gold &gt; wood for mining
     * speed but weaker durability; in practice gold tools are transitional). Scaling by
     * 100,000 keeps tier dominant over durability / enchantments / armor subscores.
     */
    private static int tierScore(Tier tier) {
        if (tier instanceof Tiers t) {
            return t.ordinal() * 100_000;
        }
        return (int) (tier.getAttackDamageBonus() * 10_000);
    }
}

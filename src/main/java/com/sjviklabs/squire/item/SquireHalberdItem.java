package com.sjviklabs.squire.item;

import com.sjviklabs.squire.config.SquireConfig;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * The Squire's Halberd — a long-reach melee weapon with area sweep on every Nth hit.
 *
 * Stats:
 *   - Attack Damage: 7 total (3 bonus + Tiers.DIAMOND base 3.0 + player base 1.0)
 *   - Attack Speed:  -3.0 modifier (slow, deliberate)
 *   - Entity Interaction Range: +1.0 (extended reach)
 *
 * Sweep AoE trigger lives in CombatHandler (halberdHitCount field).
 * This class provides performSweep() which CombatHandler calls on threshold.
 *
 * Sweep predicate excludes:
 *   - The attacker itself
 *   - Player instances (protects owner and all other players)
 */
public class SquireHalberdItem extends SwordItem {

    /**
     * Durability matches a Diamond sword (1561 uses).
     * Stats override via createHalberdAttributes():
     *   total ATK = 1.0 (base) + 3.0 (DIAMOND tier) + 3 (bonus) = 7.0
     *   attack speed = -3.0 (very slow)
     *   entity reach = +1.0 block
     */
    public SquireHalberdItem() {
        super(Tiers.DIAMOND, new Item.Properties()
                .stacksTo(1)
                .attributes(createHalberdAttributes()));
    }

    /**
     * Builds the attribute modifier set for the Halberd:
     *   - ATTACK_DAMAGE: 3 bonus + DIAMOND tier bonus (3.0) = 6.0 modifier → 7 total ATK (with player base 1.0)
     *   - ATTACK_SPEED: -3.0 (very slow swing)
     *   - ENTITY_INTERACTION_RANGE: +1.0 (extended reach)
     */
    private static ItemAttributeModifiers createHalberdAttributes() {
        return ItemAttributeModifiers.builder()
                .add(
                        Attributes.ATTACK_DAMAGE,
                        new AttributeModifier(
                                BASE_ATTACK_DAMAGE_ID,
                                3.0 + Tiers.DIAMOND.getAttackDamageBonus(),
                                AttributeModifier.Operation.ADD_VALUE
                        ),
                        EquipmentSlotGroup.MAINHAND
                )
                .add(
                        Attributes.ATTACK_SPEED,
                        new AttributeModifier(
                                BASE_ATTACK_SPEED_ID,
                                -3.0,
                                AttributeModifier.Operation.ADD_VALUE
                        ),
                        EquipmentSlotGroup.MAINHAND
                )
                .add(
                        Attributes.ENTITY_INTERACTION_RANGE,
                        new AttributeModifier(
                                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                                        "squire", "halberd_reach"),
                                1.0,
                                AttributeModifier.Operation.ADD_VALUE
                        ),
                        EquipmentSlotGroup.MAINHAND
                )
                .build();
    }

    /**
     * Performs 360-degree sweep AoE around the attacker.
     * Called by CombatHandler when halberdHitCount reaches the configured threshold.
     *
     * Predicate excludes:
     *   - The attacker itself
     *   - Player instances (covers owner and all other players)
     *
     * @param attacker      the squire performing the sweep
     * @param sweepDamage   damage to deal (typically ATTACK_DAMAGE * 0.75)
     */
    public static void performSweep(LivingEntity attacker, float sweepDamage) {
        Level level = attacker.level();
        if (level.isClientSide()) return;

        AABB sweepArea = attacker.getBoundingBox().inflate(2.5);
        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, sweepArea,
                e -> e != attacker
                        && !(e instanceof Player));

        for (LivingEntity target : targets) {
            target.hurt(level.damageSources().mobAttack(attacker), sweepDamage);
        }
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        return true;
    }

    @Override
    public void postHurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        stack.hurtAndBreak(1, attacker, EquipmentSlot.MAINHAND);
    }
}

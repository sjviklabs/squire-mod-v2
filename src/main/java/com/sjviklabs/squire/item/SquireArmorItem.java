package com.sjviklabs.squire.item;

import net.minecraft.core.Holder;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;

/**
 * Marker class for the squire's 4-piece armor set.
 * SquireArmorLayer checks {@code stack.getItem() instanceof SquireArmorItem} to
 * select tier-based textures instead of vanilla material lookup.
 *
 * No custom behavior in Phase 3. Phase 4 may add durability modifiers.
 */
public class SquireArmorItem extends ArmorItem {

    public SquireArmorItem(Holder<ArmorMaterial> material, Type type, Item.Properties properties) {
        super(material, type, properties);
    }
}

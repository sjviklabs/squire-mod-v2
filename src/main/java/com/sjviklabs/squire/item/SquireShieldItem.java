package com.sjviklabs.squire.item;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ShieldItem;

/**
 * The Squire's Shield — a custom shield with 336 durability (matches vanilla wooden shield).
 *
 * Intended to be equipped by the squire in the OFFHAND equipment slot.
 * Registered in SquireRegistry as "squire_shield".
 */
public class SquireShieldItem extends ShieldItem {

    public SquireShieldItem() {
        super(new Item.Properties()
                .stacksTo(1)
                .durability(336));
    }
}

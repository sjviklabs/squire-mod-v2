package com.sjviklabs.squire.data;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;

public final class SquireTagKeys {
    public static final TagKey<EntityType<?>> MELEE_AGGRESSIVE =
        TagKey.create(net.minecraft.core.registries.Registries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath("squire", "melee_aggressive"));
    public static final TagKey<EntityType<?>> MELEE_CAUTIOUS =
        TagKey.create(net.minecraft.core.registries.Registries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath("squire", "melee_cautious"));
    public static final TagKey<EntityType<?>> RANGED_EVASIVE =
        TagKey.create(net.minecraft.core.registries.Registries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath("squire", "ranged_evasive"));
    public static final TagKey<EntityType<?>> EXPLOSIVE_THREAT =
        TagKey.create(net.minecraft.core.registries.Registries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath("squire", "explosive_threat"));
    public static final TagKey<EntityType<?>> DO_NOT_ATTACK =
        TagKey.create(net.minecraft.core.registries.Registries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath("squire", "do_not_attack"));

    private SquireTagKeys() {}
}

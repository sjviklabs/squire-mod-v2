package com.sjviklabs.squire;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Single registration hub for all Squire mod registrations.
 * RULE: No other class may create a DeferredRegister. All holders live here.
 *
 * Registration order in register() is load-order-sensitive:
 * 1. ENTITY_TYPES (entity type must exist before items reference it)
 * 2. ITEMS (Crest item may reference entity type for spawn egg)
 * 3. ATTACHMENT_TYPES (must exist before entity lifecycle events use SQUIRE_DATA)
 * 4. MENU_TYPES (menu type must be registered before opening menus)
 *
 * Capabilities are registered via @SubscribeEvent on the NeoForge bus — NOT here.
 * See SquireEntity.registerCapabilities() for capability event subscription.
 */
public final class SquireRegistry {

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, SquireMod.MODID);

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(Registries.ITEM, SquireMod.MODID);

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, SquireMod.MODID);

    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(Registries.MENU, SquireMod.MODID);

    private SquireRegistry() {}

    public static void register(IEventBus modEventBus) {
        ENTITY_TYPES.register(modEventBus);
        ITEMS.register(modEventBus);
        ATTACHMENT_TYPES.register(modEventBus);
        MENU_TYPES.register(modEventBus);
    }
}

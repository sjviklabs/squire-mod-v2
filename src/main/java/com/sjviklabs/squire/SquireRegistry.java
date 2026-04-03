package com.sjviklabs.squire;

import com.sjviklabs.squire.entity.SquireDataAttachment;
import com.sjviklabs.squire.entity.SquireEntity;
import com.sjviklabs.squire.item.SquireCrestItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

/**
 * Single registration hub for all Squire mod registrations.
 * RULE: No other class may create a DeferredRegister. All holders live here.
 *
 * Registration order in register() is load-order-sensitive:
 * 1. ENTITY_TYPES (entity type must exist before items reference it)
 * 2. ITEMS (Crest item may reference entity type for spawn egg)
 * 3. ATTACHMENT_TYPES (must exist before entity lifecycle events use SQUIRE_DATA)
 * 4. MENU_TYPES (menu type must be registered before opening menus)
 */
public final class SquireRegistry {

    // ---- DeferredRegisters ----

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, SquireMod.MODID);

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(Registries.ITEM, SquireMod.MODID);

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, SquireMod.MODID);

    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(Registries.MENU, SquireMod.MODID);

    // ---- Entity Types ----

    public static final DeferredHolder<EntityType<?>, EntityType<SquireEntity>> SQUIRE =
            ENTITY_TYPES.register("squire", () -> EntityType.Builder
                    .of(SquireEntity::new, MobCategory.MISC)
                    .sized(0.6F, 1.8F)
                    .ridingOffset(-0.7F)
                    .clientTrackingRange(10)
                    .build(SquireMod.MODID + ":squire"));

    // ---- Items ----

    public static final DeferredHolder<Item, SquireCrestItem> CREST =
            ITEMS.register("squire_crest", () -> new SquireCrestItem(new Item.Properties().stacksTo(1)));

    // ---- Attachment Types ----

    public static final Supplier<AttachmentType<SquireDataAttachment.SquireData>> SQUIRE_DATA =
            ATTACHMENT_TYPES.register("squire_data",
                    SquireDataAttachment::buildAttachmentType);

    // ---- Private constructor ----

    private SquireRegistry() {}

    // ================================================================
    // Registration entry point — called from SquireMod constructor
    // ================================================================

    public static void register(IEventBus modEventBus) {
        ENTITY_TYPES.register(modEventBus);
        ITEMS.register(modEventBus);
        ATTACHMENT_TYPES.register(modEventBus);
        MENU_TYPES.register(modEventBus);

        // Register mod-bus event handlers (attribute creation)
        modEventBus.register(SquireRegistry.class);
    }

    // ================================================================
    // Mod-bus event handlers
    // ================================================================

    @SubscribeEvent
    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(SquireRegistry.SQUIRE.get(), SquireEntity.createAttributes().build());
    }
}

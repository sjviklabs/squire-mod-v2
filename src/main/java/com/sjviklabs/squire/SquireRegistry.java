package com.sjviklabs.squire;

import com.sjviklabs.squire.entity.SquireDataAttachment;
import com.sjviklabs.squire.entity.SquireEntity;
import com.sjviklabs.squire.inventory.SquireMenu;
import com.sjviklabs.squire.item.SquireArmorItem;
import com.sjviklabs.squire.item.SquireCrestItem;
import com.sjviklabs.squire.network.SquireCommandPayload;
import com.sjviklabs.squire.network.SquireModePayload;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
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

    public static final DeferredHolder<Item, SquireArmorItem> SQUIRE_HELMET =
            ITEMS.register("squire_helmet",
                () -> new SquireArmorItem(ArmorMaterials.IRON, ArmorItem.Type.HELMET,
                    new Item.Properties().stacksTo(1)));

    public static final DeferredHolder<Item, SquireArmorItem> SQUIRE_CHESTPLATE =
            ITEMS.register("squire_chestplate",
                () -> new SquireArmorItem(ArmorMaterials.IRON, ArmorItem.Type.CHESTPLATE,
                    new Item.Properties().stacksTo(1)));

    public static final DeferredHolder<Item, SquireArmorItem> SQUIRE_LEGGINGS =
            ITEMS.register("squire_leggings",
                () -> new SquireArmorItem(ArmorMaterials.IRON, ArmorItem.Type.LEGGINGS,
                    new Item.Properties().stacksTo(1)));

    public static final DeferredHolder<Item, SquireArmorItem> SQUIRE_BOOTS =
            ITEMS.register("squire_boots",
                () -> new SquireArmorItem(ArmorMaterials.IRON, ArmorItem.Type.BOOTS,
                    new Item.Properties().stacksTo(1)));

    // ---- Attachment Types ----

    public static final Supplier<AttachmentType<SquireDataAttachment.SquireData>> SQUIRE_DATA =
            ATTACHMENT_TYPES.register("squire_data",
                    SquireDataAttachment::buildAttachmentType);

    // ---- Menu Types ----

    public static final DeferredHolder<MenuType<?>, MenuType<SquireMenu>> SQUIRE_MENU =
            MENU_TYPES.register("squire_menu", () ->
                IMenuTypeExtension.create((windowId, inv, data) -> {
                    // Client-side factory — locate squire entity by network ID sent from server
                    // Full client Screen registered in Phase 5; this stub handles entity lookup
                    int entityId = data.readInt();
                    if (inv.player.level().getEntity(entityId) instanceof SquireEntity squire) {
                        return new SquireMenu(windowId, inv, squire);
                    }
                    return null;
                }));

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

        // Register mod-bus event handlers (attribute creation, capabilities)
        modEventBus.register(SquireRegistry.class);
    }

    // ================================================================
    // Mod-bus event handlers
    // ================================================================

    @SubscribeEvent
    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(SquireRegistry.SQUIRE.get(), SquireEntity.createAttributes().build());
    }

    /**
     * Registers IItemHandler capabilities on the squire entity.
     *
     * ENTITY       — accessed by the GUI and direct capability queries.
     * ENTITY_AUTOMATION — accessed by hoppers, pipes, and other automation (INV-02).
     *
     * Both return the same SquireItemHandler so automation sees the same inventory as the GUI.
     */
    /**
     * Registers network payloads on the MOD event bus.
     * Both payloads use StreamCodec.composite() — no raw FriendlyByteBuf writes (ARC-08).
     *
     * Phase 2 payloads:
     * - SquireCommandPayload: CMD_STAY and CMD_FOLLOW (client → server)
     * - SquireModePayload:    mode query stub, no-op in Phase 2 (client → server)
     */
    @SubscribeEvent
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(
                SquireCommandPayload.TYPE,
                SquireCommandPayload.STREAM_CODEC,
                SquireCommandPayload::handle
        );
        registrar.playToServer(
                SquireModePayload.TYPE,
                SquireModePayload.STREAM_CODEC,
                SquireModePayload::handle
        );
    }

    @SubscribeEvent
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerEntity(
            Capabilities.ItemHandler.ENTITY,
            SquireRegistry.SQUIRE.get(),
            (squire, context) -> squire.getItemHandler()
        );
        event.registerEntity(
            Capabilities.ItemHandler.ENTITY_AUTOMATION,
            SquireRegistry.SQUIRE.get(),
            (squire, context) -> squire.getItemHandler()
        );
    }
}

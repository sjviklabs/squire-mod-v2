package com.sjviklabs.squire;

import com.sjviklabs.squire.entity.SquireDataAttachment;
import com.sjviklabs.squire.entity.SquireEntity;
import com.sjviklabs.squire.inventory.SquireMenu;
import com.sjviklabs.squire.item.SquireCrestItem;
import com.sjviklabs.squire.network.SquireCommandPayload;
import com.sjviklabs.squire.network.SquireModePayload;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
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
 *
 * v3.1.0 — mod simplified to vanilla equipment only. The squire accepts any
 * vanilla or modded armor/weapon via its equipment slots. Custom armor items
 * (helmet/chest/leggings/boots), custom weapons (halberd/shield), and the
 * signpost patrol block have been removed. Patrol now uses the Crest's
 * area-selection mechanic — the squire walks the perimeter of a marked zone.
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

    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, SquireMod.MODID);

    // ---- Entity Types ----

    public static final DeferredHolder<EntityType<?>, EntityType<SquireEntity>> SQUIRE =
            ENTITY_TYPES.register("squire", () -> EntityType.Builder
                    .of(SquireEntity::new, MobCategory.MISC)
                    .sized(0.6F, 1.8F)
                    .ridingOffset(-0.7F)
                    .clientTrackingRange(10)
                    .build(SquireMod.MODID + ":squire"));

    // ---- Items ----
    // Two custom items remain: the Crest (mandatory — summon + area selection)
    // and the Guidebook (Patchouli onboarding tome).

    public static final DeferredHolder<Item, SquireCrestItem> CREST =
            ITEMS.register("squire_crest", () -> new SquireCrestItem(new Item.Properties().stacksTo(1)));

    // ---- Attachment Types ----

    public static final Supplier<AttachmentType<SquireDataAttachment.SquireData>> SQUIRE_DATA =
            ATTACHMENT_TYPES.register("squire_data",
                    SquireDataAttachment::buildAttachmentType);

    // ---- Creative Tab ----

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> SQUIRE_TAB =
            CREATIVE_TABS.register("squire_tab", () -> CreativeModeTab.builder()
                    .title(Component.literal("Squire"))
                    .icon(() -> CREST.get().getDefaultInstance())
                    .displayItems((params, output) -> {
                        output.accept(CREST.get());
                    })
                    .build());

    // ---- Menu Types ----

    public static final DeferredHolder<MenuType<?>, MenuType<SquireMenu>> SQUIRE_MENU =
            MENU_TYPES.register("squire_menu", () ->
                IMenuTypeExtension.create((windowId, inv, data) -> {
                    // Client-side factory — locate squire entity by network ID sent from server
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
        CREATIVE_TABS.register(modEventBus);

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
     * Registers network payloads on the MOD event bus.
     * Both payloads use StreamCodec.composite() — no raw FriendlyByteBuf writes (ARC-08).
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

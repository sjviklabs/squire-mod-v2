package com.sjviklabs.squire.client;

import com.sjviklabs.squire.SquireMod;
import com.sjviklabs.squire.SquireRegistry;
import com.sjviklabs.squire.entity.SquireEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.fml.common.EventBusSubscriber.Bus;
import net.neoforged.neoforge.client.event.InputEvent;

/**
 * Client-only event handler for Squire client setup, keybind registration,
 * renderer registration, and radial menu open logic.
 *
 * Uses two inner static classes to subscribe to two different event buses:
 *   ModEvents  — NeoForge MOD bus: renderers, keybinds, client setup
 *   GameEvents — NeoForge GAME bus: input handling, radial open trigger
 *
 * Both inner classes are annotated with Dist.CLIENT — neither is ever loaded
 * on a dedicated server. This enforces the client-only boundary (ARC-08, Pitfall 1).
 */
public class SquireClientEvents {

    /**
     * Mod-bus subscribers: renderer registration, keybind registration, client setup.
     *
     * RegisterKeyMappingsEvent and EntityRenderersEvent both fire on the MOD bus.
     * FMLClientSetupEvent also fires on the MOD bus — used here for SquireScreen registration.
     */
    @EventBusSubscriber(modid = SquireMod.MODID, bus = Bus.MOD, value = Dist.CLIENT)
    public static class ModEvents {

        @SubscribeEvent
        public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(SquireRegistry.SQUIRE.get(), SquireRenderer::new);
        }

        @SubscribeEvent
        public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
            event.register(SquireKeybinds.RADIAL_MENU);
        }

        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            // TODO(05-01): Register SquireScreen once Plan 05-01 is complete.
            // Uncomment the line below after SquireScreen is created:
            //   MenuScreens.register(SquireRegistry.SQUIRE_MENU.get(), SquireScreen::new);
            //
            // SquireScreen.register() is defined in Plan 05-01. This handler is wired
            // and ready — just needs the SquireScreen class to exist.
        }
    }

    /**
     * Game-bus subscriber: key input handling for radial menu open trigger.
     *
     * InputEvent.Key fires on the NeoForge GAME bus on every key press/release.
     * We check consumeClick() which returns true once per press and clears the flag.
     */
    @EventBusSubscriber(modid = SquireMod.MODID, bus = Bus.GAME, value = Dist.CLIENT)
    public static class GameEvents {

        @SubscribeEvent
        public static void onKeyInput(InputEvent.Key event) {
            Minecraft mc = Minecraft.getInstance();

            // Only open if no screen is currently showing and the key was consumed
            if (!SquireKeybinds.RADIAL_MENU.consumeClick() || mc.screen != null) {
                return;
            }

            if (mc.player == null) return;

            // Raycast to find a squire entity under the player's crosshair
            // blockInteractionRange() is the correct 1.21.1 API (getBlockReach removed)
            double range = mc.player.blockInteractionRange();
            HitResult hit = mc.player.pick(range, 0.0F, false);

            if (!(hit instanceof EntityHitResult entityHit)) return;
            Entity target = entityHit.getEntity();

            if (target instanceof SquireEntity squire) {
                // Only open if this player is the squire's owner
                if (squire.isOwnedBy(mc.player)) {
                    mc.setScreen(new SquireRadialScreen(squire));
                }
            }
        }
    }
}

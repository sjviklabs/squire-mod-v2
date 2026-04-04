package com.sjviklabs.squire.client;

import com.sjviklabs.squire.SquireMod;
import com.sjviklabs.squire.SquireRegistry;
import com.sjviklabs.squire.entity.SquireEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.fml.common.EventBusSubscriber.Bus;

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
        public static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
            // Register the squire inventory screen against its menu type.
            // RegisterMenuScreensEvent is the NeoForge 1.21.1 replacement for
            // MenuScreens.register() which has private access in vanilla.
            SquireScreen.register(event);
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
        public static void onRenderLevel(RenderLevelStageEvent event) {
            SquireAreaRenderer.onRenderLevel(event);
        }

        @SubscribeEvent
        public static void onKeyInput(InputEvent.Key event) {
            Minecraft mc = Minecraft.getInstance();

            // Only open if no screen is currently showing and the key was consumed
            if (!SquireKeybinds.RADIAL_MENU.consumeClick() || mc.screen != null) {
                return;
            }

            if (mc.player == null || mc.level == null) return;

            // Find the nearest owned squire within 32 blocks (no need to aim at it)
            SquireEntity nearest = null;
            double nearestDist = Double.MAX_VALUE;
            for (Entity e : mc.level.entitiesForRendering()) {
                if (e instanceof SquireEntity squire && squire.isOwnedBy(mc.player)) {
                    double dist = squire.distanceToSqr(mc.player);
                    if (dist < 32 * 32 && dist < nearestDist) {
                        nearest = squire;
                        nearestDist = dist;
                    }
                }
            }

            if (nearest != null) {
                mc.setScreen(new SquireRadialScreen(nearest));
            }
        }
    }
}

package com.sjviklabs.squire.client;

import com.sjviklabs.squire.SquireMod;
import com.sjviklabs.squire.SquireRegistry;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * Client-only event handler for renderer registration.
 *
 * @EventBusSubscriber with Dist.CLIENT ensures this class is never loaded on a dedicated server.
 * All registrations fire on the MOD event bus during client setup.
 */
@EventBusSubscriber(modid = SquireMod.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class SquireClientEvents {

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(SquireRegistry.SQUIRE.get(), SquireRenderer::new);
    }
}

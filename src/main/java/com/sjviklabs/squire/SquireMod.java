package com.sjviklabs.squire;

import com.mojang.logging.LogUtils;
import com.sjviklabs.squire.compat.CuriosCompat;
import com.sjviklabs.squire.compat.MineColoniesCompat;
import com.sjviklabs.squire.config.SquireConfig;
import com.sjviklabs.squire.progression.ProgressionDataLoader;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import org.slf4j.Logger;

@Mod(SquireMod.MODID)
public final class SquireMod {
    public static final String MODID = "squire";
    private static final Logger LOGGER = LogUtils.getLogger();

    public SquireMod(IEventBus modEventBus, ModContainer container) {
        LOGGER.info("[Squire] Initializing Squire Mod v2");
        SquireRegistry.register(modEventBus);
        container.registerConfig(ModConfig.Type.COMMON, SquireConfig.SPEC, "squire-common.toml");
        NeoForge.EVENT_BUS.addListener(SquireMod::onAddReloadListeners);

        // Compat startup logging
        if (MineColoniesCompat.isActive()) {
            LOGGER.info("[Squire] MineColonies detected — citizen friendly-fire prevention active");
        }
        if (CuriosCompat.isActive()) {
            LOGGER.info("[Squire] Curios detected — accessory slots registered via datapack");
        }
        // Jade is auto-discovered via @WailaPlugin — no logging needed here
    }

    private static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new ProgressionDataLoader());
    }
}

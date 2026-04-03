package com.sjviklabs.squire;

import com.sjviklabs.squire.config.SquireConfig;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

@Mod(SquireMod.MODID)
public final class SquireMod {
    public static final String MODID = "squire";
    private static final Logger LOGGER = LogUtils.getLogger();

    public SquireMod(IEventBus modEventBus, ModContainer container) {
        LOGGER.info("[Squire] Initializing Squire Mod v2");
        SquireRegistry.register(modEventBus);
        container.registerConfig(ModConfig.Type.COMMON, SquireConfig.SPEC, "squire-common.toml");
    }
}

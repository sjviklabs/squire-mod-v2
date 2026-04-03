package com.sjviklabs.squire;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

@Mod(SquireMod.MODID)
public final class SquireMod {
    public static final String MODID = "squire";
    private static final Logger LOGGER = LogUtils.getLogger();

    public SquireMod(IEventBus modEventBus) {
        LOGGER.info("[Squire] Initializing Squire Mod v2");
        SquireRegistry.register(modEventBus);
    }
}

package com.sjviklabs.squire.compat;

import com.sjviklabs.squire.entity.SquireEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IEntityComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.config.IPluginConfig;

/**
 * Jade/WAILA plugin that registers a custom tooltip provider for squire entities.
 *
 * This class is ONLY loaded by Jade's @WailaPlugin service loader, which only runs
 * when Jade is present. As long as no other class references JadeCompat in a static
 * initializer or field, this class will never be loaded without Jade present —
 * preventing ClassNotFoundException on startup.
 *
 * Do NOT reference this class from SquireMod.java or SquireRegistry.java.
 */
@WailaPlugin
public class JadeCompat implements IWailaPlugin {

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerEntityComponent(SquireTooltipProvider.INSTANCE, SquireEntity.class);
    }
}

/**
 * IEntityComponentProvider that appends name, tier, and color-coded HP to the
 * Jade HUD overlay when looking at a squire entity.
 *
 * Reads SynchedEntityData-backed values (health, maxHealth) which are safe to
 * access on the client thread during appendTooltip. No IServerDataProvider is
 * needed because tier and health are both synced via SynchedEntityData.
 */
class SquireTooltipProvider implements IEntityComponentProvider {

    static final SquireTooltipProvider INSTANCE = new SquireTooltipProvider();

    private SquireTooltipProvider() {}

    @Override
    public void appendTooltip(ITooltip tooltip, EntityAccessor accessor, IPluginConfig config) {
        if (!(accessor.getEntity() instanceof SquireEntity squire)) return;

        // Line 1: squire name
        tooltip.add(Component.literal(squire.getName().getString())
                .withStyle(ChatFormatting.YELLOW));

        // Line 2: tier
        tooltip.add(Component.literal("Tier: " + squire.getTier().name())
                .withStyle(ChatFormatting.AQUA));

        // Line 3: HP with color coding — green above 50%, red at or below
        float hp = squire.getHealth();
        float maxHp = squire.getMaxHealth();
        ChatFormatting hpColor = (maxHp > 0 && hp / maxHp > 0.5f)
                ? ChatFormatting.GREEN
                : ChatFormatting.RED;
        tooltip.add(Component.literal(String.format("HP: %.0f/%.0f", hp, maxHp))
                .withStyle(hpColor));
    }

    @Override
    public ResourceLocation getUid() {
        return ResourceLocation.fromNamespaceAndPath("squire", "squire_entity");
    }
}

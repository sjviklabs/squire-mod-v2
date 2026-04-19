package com.sjviklabs.squire.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sjviklabs.squire.entity.SquireEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.network.chat.Component;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.ItemArmorGeoLayer;

/**
 * Geckolib entity renderer for the squire.
 *
 * All renderer-readable state comes from SynchedEntityData on SquireEntity — never from SquireBrain.
 * SquireModel handles male/female routing via entity.isSlimModel().
 *
 * Render layers:
 *   addRenderLayer(new ItemArmorGeoLayer<>(this)) — vanilla/modded armor rendering
 *   addRenderLayer(new SquireBackpackLayer(this))
 *   addRenderLayer(new SquireHeldItemLayer(this))
 *
 * v3.1.0 — SquireArmorLayer (tier-specific textures for removed SquireArmorItem) was
 * replaced with plain ItemArmorGeoLayer. Squire now renders any equipped armor
 * exactly like a vanilla mob wearing it.
 */
public class SquireRenderer extends GeoEntityRenderer<SquireEntity> {

    public SquireRenderer(EntityRendererProvider.Context context) {
        super(context, new SquireModel());
        addRenderLayer(new ItemArmorGeoLayer<>(this));
        addRenderLayer(new SquireBackpackLayer(this));
        addRenderLayer(new SquireHeldItemLayer(this));
    }

    @Override
    protected void renderNameTag(SquireEntity entity, Component displayName,
            PoseStack poseStack, MultiBufferSource bufferSource,
            int packedLight, float partialTick) {
        super.renderNameTag(entity, displayName, poseStack, bufferSource, packedLight, partialTick);
        // Health bar: port from v0.5.0 SquireRenderer.renderHealthBar() in Phase 3-04
        // Stub is intentionally empty — name tag renders via super call above
    }
}

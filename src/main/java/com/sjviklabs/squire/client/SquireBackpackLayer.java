package com.sjviklabs.squire.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.sjviklabs.squire.entity.SquireEntity;
import com.sjviklabs.squire.entity.SquireTier;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * Controls backpack bone visibility based on squire tier.
 *
 * Tier to visibility mapping:
 *   SERVANT, APPRENTICE  -> backpack_small visible, backpack_large hidden
 *   SQUIRE, KNIGHT       -> backpack_large visible, backpack_small hidden
 *   CHAMPION             -> backpack_large visible (same as SQUIRE and above)
 *
 * Uses GeoBone.setHidden() — the geometry lives in the .geo.json.
 * preRender() sets visibility before the main render pass resolves the bones.
 * render() is intentionally empty: no additional draw call needed.
 */
public class SquireBackpackLayer extends GeoRenderLayer<SquireEntity> {

    public SquireBackpackLayer(GeoEntityRenderer<SquireEntity> renderer) {
        super(renderer);
    }

    @Override
    public void preRender(PoseStack poseStack, SquireEntity animatable, BakedGeoModel bakedModel,
            RenderType renderType, MultiBufferSource bufferSource,
            VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
        if (animatable.isInvisible()) return;

        SquireTier tier = SquireTier.fromLevel(animatable.getLevel());
        boolean useLarge = tier.ordinal() >= SquireTier.SQUIRE.ordinal();

        bakedModel.getBone("backpack_small").ifPresent(bone -> bone.setHidden(useLarge));
        bakedModel.getBone("backpack_large").ifPresent(bone -> bone.setHidden(!useLarge));
    }

    @Override
    public void render(PoseStack poseStack, SquireEntity animatable, BakedGeoModel bakedModel,
            RenderType renderType, MultiBufferSource bufferSource,
            VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
        // No additional draw call — backpack geometry is in the .geo.json bones.
        // preRender() controls visibility; GeoEntityRenderer draws the bones automatically.
    }
}

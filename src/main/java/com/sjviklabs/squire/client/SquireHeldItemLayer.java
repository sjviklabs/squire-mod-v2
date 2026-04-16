package com.sjviklabs.squire.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.sjviklabs.squire.entity.SquireEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.BlockAndItemGeoLayer;

/**
 * Renders held items (weapon in mainhand, shield in offhand) on the squire's
 * hand bones using Geckolib's BlockAndItemGeoLayer.
 *
 * BlockAndItemGeoLayer handles bone-space positioning automatically via
 * RenderUtil.translateAndRotateMatrixForBone(). We override renderStackForBone()
 * to apply the standard -90 X rotation that converts from Geckolib bone space
 * (Y-up along arm) to vanilla item rendering space.
 *
 * Bone mapping (Geckolib mirrors model-space X axis):
 *   left_hand  bone -> mainhand weapon  (THIRD_PERSON_RIGHT_HAND)
 *   right_hand bone -> offhand shield   (THIRD_PERSON_LEFT_HAND)
 */
public class SquireHeldItemLayer extends BlockAndItemGeoLayer<SquireEntity> {

    public SquireHeldItemLayer(GeoRenderer<SquireEntity> renderer) {
        super(renderer);
    }

    @Override
    protected ItemStack getStackForBone(GeoBone bone, SquireEntity entity) {
        return switch (bone.getName()) {
            case "left_hand" -> entity.getMainHandItem();
            case "right_hand" -> entity.getOffhandItem();
            default -> null;
        };
    }

    @Override
    protected ItemDisplayContext getTransformTypeForStack(GeoBone bone, ItemStack stack, SquireEntity entity) {
        return switch (bone.getName()) {
            case "left_hand" -> ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
            case "right_hand" -> ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
            default -> ItemDisplayContext.NONE;
        };
    }

    @Override
    protected void renderStackForBone(PoseStack poseStack, GeoBone bone, ItemStack stack,
                                       SquireEntity entity, MultiBufferSource bufferSource,
                                       float partialTick, int packedLight, int packedOverlay) {
        // Small position adjustments to center item in hand
        poseStack.translate(0, 0, -0.0625);
        poseStack.translate(0, -0.0625, 0);

        // Convert from Geckolib bone space (Y along arm) to vanilla item space
        poseStack.mulPose(Axis.XP.rotationDegrees(-90f));

        if (stack.getItem() instanceof ShieldItem) {
            // 90° Y turns shield from sideways to forward-facing (plate out).
            // Vanilla's BlockEntityWithoutLevelRenderer already applies scale(1,-1,-1)
            // internally, so 90° here — not 180° — gets the plate facing enemies.
            poseStack.mulPose(Axis.YP.rotationDegrees(90f));
            poseStack.translate(0, 0.125, -0.1875);
        }

        super.renderStackForBone(poseStack, bone, stack, entity, bufferSource,
                partialTick, packedLight, packedOverlay);
    }
}

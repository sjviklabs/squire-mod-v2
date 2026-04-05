package com.sjviklabs.squire.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.sjviklabs.squire.entity.SquireEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * Custom held-item render layer for the squire.
 *
 * Renders mainhand and offhand items at the correct position/scale on arm bones.
 * Cannot use BlockAndItemGeoLayer directly because arm bones are full-size —
 * items render at shoulder pivot at bone scale, producing oversized floating items.
 *
 * This layer manually renders items with proper translation to the hand tip,
 * correct scale, and appropriate rotation for weapons vs shields.
 */
public class SquireHeldItemLayer extends GeoRenderLayer<SquireEntity> {

    public SquireHeldItemLayer(GeoRenderer<SquireEntity> renderer) {
        super(renderer);
    }

    @Override
    public void renderForBone(PoseStack poseStack, SquireEntity entity, GeoBone bone,
                               RenderType renderType, MultiBufferSource bufferSource,
                               VertexConsumer buffer, float partialTick,
                               int packedLight, int packedOverlay) {
        ItemStack stack;
        boolean isLeft;

        switch (bone.getName()) {
            case "left_arm" -> { stack = entity.getMainHandItem(); isLeft = true; }
            case "right_arm" -> { stack = entity.getOffhandItem(); isLeft = false; }
            default -> { return; }
        }

        if (stack.isEmpty()) return;

        poseStack.pushPose();

        // Translate down the arm to the hand position (arm is ~10px / 0.625 blocks long)
        poseStack.translate(0.0F, 0.5F, 0.0F);

        // Scale down — items are rendered at 1:1 block scale by default
        float scale = 0.5F;
        poseStack.scale(scale, scale, scale);

        if (stack.getItem() instanceof ShieldItem) {
            // Shield: rotate to face forward, offset to side
            poseStack.mulPose(Axis.XP.rotationDegrees(90));
            poseStack.mulPose(Axis.ZP.rotationDegrees(isLeft ? -10 : 10));
            poseStack.translate(0.0F, -0.2F, -0.5F);
        } else {
            // Weapon/tool: point downward, slight angle
            poseStack.mulPose(Axis.XP.rotationDegrees(180));
            poseStack.mulPose(Axis.ZP.rotationDegrees(isLeft ? 10 : -10));
            poseStack.translate(0.0F, -0.2F, 0.0F);
        }

        Minecraft.getInstance().getItemRenderer().renderStatic(
                stack,
                isLeft ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,
                packedLight, packedOverlay,
                poseStack, bufferSource, entity.level(), entity.getId());

        poseStack.popPose();
    }
}

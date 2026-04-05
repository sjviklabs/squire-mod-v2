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
 * Renders held items (weapon in mainhand, shield in offhand) on the squire's
 * hand bones with correct orientation.
 *
 * Uses the cubeless right_hand/left_hand bones added to the .geo.json models
 * as attachment points. These bones are positioned at the arm tips so items
 * render at the correct location. This layer adds rotation so:
 * - Weapons/tools point downward perpendicular to the body (like a player holding a sword)
 * - Shields face outward away from the body (blocking stance)
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
        boolean isMainHand;

        switch (bone.getName()) {
            case "left_hand"  -> { stack = entity.getMainHandItem(); isMainHand = true; }
            case "right_hand" -> { stack = entity.getOffhandItem(); isMainHand = false; }
            default -> { return; }
        }

        if (stack.isEmpty()) return;

        poseStack.pushPose();

        if (stack.getItem() instanceof ShieldItem) {
            // Shield: face outward (away from body), positioned at arm's side
            poseStack.mulPose(Axis.XP.rotationDegrees(-90));  // flip to face outward
            poseStack.mulPose(Axis.YP.rotationDegrees(180));  // face away from body
            poseStack.translate(0.0F, 0.1F, -0.25F);
        } else {
            // Weapon/tool: hang downward, blade/head pointing to ground
            poseStack.mulPose(Axis.XP.rotationDegrees(-90));  // rotate from arm-aligned to pointing down
            poseStack.translate(0.0F, 0.1F, 0.0F);
        }

        Minecraft.getInstance().getItemRenderer().renderStatic(
                stack,
                isMainHand ? ItemDisplayContext.THIRD_PERSON_RIGHT_HAND : ItemDisplayContext.THIRD_PERSON_LEFT_HAND,
                packedLight, packedOverlay,
                poseStack, bufferSource, entity.level(), entity.getId());

        poseStack.popPose();
    }
}

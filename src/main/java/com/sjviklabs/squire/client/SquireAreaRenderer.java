package com.sjviklabs.squire.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Client-side wireframe renderer for the Crest area selection.
 *
 * Draws a colored bounding box when the player holds a Crest with corner(s) set:
 *   - One corner set:  single-block highlight (yellow)
 *   - Two corners set: full area wireframe (cyan)
 *
 * Registered on the GAME bus from SquireClientEvents.
 * Uses RenderLevelStageEvent at AFTER_TRANSLUCENT_BLOCKS stage.
 *
 * CLIENT ONLY — no server-side imports.
 */
public class SquireAreaRenderer {

    private SquireAreaRenderer() {}

    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // Check both hands for a Crest with area data
        ItemStack mainHand = mc.player.getMainHandItem();
        ItemStack offHand = mc.player.getOffhandItem();

        ItemStack crest = null;
        if (isCrestWithCorners(mainHand)) {
            crest = mainHand;
        } else if (isCrestWithCorners(offHand)) {
            crest = offHand;
        }
        if (crest == null) return;

        CompoundTag tag = crest.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();

        boolean hasCorner1 = tag.contains("Corner1X");
        boolean hasCorner2 = tag.contains("Corner2X");
        if (!hasCorner1) return;

        PoseStack poseStack = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();

        poseStack.pushPose();
        poseStack.translate(-cam.x, -cam.y, -cam.z);

        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        VertexConsumer lines = bufferSource.getBuffer(RenderType.lines());

        if (hasCorner2) {
            // Full area — cyan wireframe
            int x1 = tag.getInt("Corner1X");
            int y1 = tag.getInt("Corner1Y");
            int z1 = tag.getInt("Corner1Z");
            int x2 = tag.getInt("Corner2X");
            int y2 = tag.getInt("Corner2Y");
            int z2 = tag.getInt("Corner2Z");

            AABB box = new AABB(
                    Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2),
                    Math.max(x1, x2) + 1.0, Math.max(y1, y2) + 1.0, Math.max(z1, z2) + 1.0);

            LevelRenderer.renderLineBox(poseStack, lines, box,
                    0.0F, 0.9F, 0.9F, 0.8F); // cyan, 80% alpha
        } else {
            // Single corner — yellow single-block highlight
            int x1 = tag.getInt("Corner1X");
            int y1 = tag.getInt("Corner1Y");
            int z1 = tag.getInt("Corner1Z");

            AABB box = new AABB(x1, y1, z1, x1 + 1.0, y1 + 1.0, z1 + 1.0);
            LevelRenderer.renderLineBox(poseStack, lines, box,
                    1.0F, 1.0F, 0.0F, 0.8F); // yellow, 80% alpha
        }

        bufferSource.endBatch(RenderType.lines());
        poseStack.popPose();
    }

    private static boolean isCrestWithCorners(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (!(stack.getItem() instanceof com.sjviklabs.squire.item.SquireCrestItem)) return false;
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return tag.contains("Corner1X");
    }
}

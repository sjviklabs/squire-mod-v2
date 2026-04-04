package com.sjviklabs.squire.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/**
 * Client-side wireframe renderer for the Crest area selection.
 *
 * Draws a colored bounding box when the player holds a Crest with corner(s) set:
 *   - One corner set:  single-block highlight (yellow)
 *   - Two corners set: full area wireframe (cyan)
 *
 * Uses direct RenderSystem line drawing (not LevelRenderer.renderLineBox)
 * for reliable rendering across NeoForge versions.
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

        Vec3 cam = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();

        poseStack.pushPose();
        poseStack.translate(-cam.x, -cam.y, -cam.z);

        if (hasCorner2) {
            int x1 = tag.getInt("Corner1X");
            int y1 = tag.getInt("Corner1Y");
            int z1 = tag.getInt("Corner1Z");
            int x2 = tag.getInt("Corner2X");
            int y2 = tag.getInt("Corner2Y");
            int z2 = tag.getInt("Corner2Z");

            float minX = Math.min(x1, x2);
            float minY = Math.min(y1, y2);
            float minZ = Math.min(z1, z2);
            float maxX = Math.max(x1, x2) + 1.0F;
            float maxY = Math.max(y1, y2) + 1.0F;
            float maxZ = Math.max(z1, z2) + 1.0F;

            drawBox(poseStack, minX, minY, minZ, maxX, maxY, maxZ,
                    0.0F, 0.9F, 0.9F, 0.9F);
        } else {
            int x1 = tag.getInt("Corner1X");
            int y1 = tag.getInt("Corner1Y");
            int z1 = tag.getInt("Corner1Z");

            drawBox(poseStack, x1, y1, z1, x1 + 1.0F, y1 + 1.0F, z1 + 1.0F,
                    1.0F, 1.0F, 0.0F, 0.9F);
        }

        poseStack.popPose();
    }

    /**
     * Draw a wireframe box using DEBUG_LINES mode with RenderSystem.
     * This bypasses LevelRenderer.renderLineBox which has normals issues
     * in some NeoForge versions.
     */
    private static void drawBox(PoseStack poseStack,
                                 float x1, float y1, float z1,
                                 float x2, float y2, float z2,
                                 float r, float g, float b, float a) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.disableDepthTest();
        RenderSystem.lineWidth(2.0F);

        Matrix4f matrix = poseStack.last().pose();
        BufferBuilder buf = Tesselator.getInstance().begin(
                VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);

        // Bottom face edges
        line(buf, matrix, x1, y1, z1, x2, y1, z1, r, g, b, a);
        line(buf, matrix, x2, y1, z1, x2, y1, z2, r, g, b, a);
        line(buf, matrix, x2, y1, z2, x1, y1, z2, r, g, b, a);
        line(buf, matrix, x1, y1, z2, x1, y1, z1, r, g, b, a);

        // Top face edges
        line(buf, matrix, x1, y2, z1, x2, y2, z1, r, g, b, a);
        line(buf, matrix, x2, y2, z1, x2, y2, z2, r, g, b, a);
        line(buf, matrix, x2, y2, z2, x1, y2, z2, r, g, b, a);
        line(buf, matrix, x1, y2, z2, x1, y2, z1, r, g, b, a);

        // Vertical edges
        line(buf, matrix, x1, y1, z1, x1, y2, z1, r, g, b, a);
        line(buf, matrix, x2, y1, z1, x2, y2, z1, r, g, b, a);
        line(buf, matrix, x2, y1, z2, x2, y2, z2, r, g, b, a);
        line(buf, matrix, x1, y1, z2, x1, y2, z2, r, g, b, a);

        BufferUploader.drawWithShader(buf.buildOrThrow());

        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
        RenderSystem.lineWidth(1.0F);
    }

    private static void line(BufferBuilder buf, Matrix4f matrix,
                              float x1, float y1, float z1,
                              float x2, float y2, float z2,
                              float r, float g, float b, float a) {
        buf.addVertex(matrix, x1, y1, z1).setColor(r, g, b, a);
        buf.addVertex(matrix, x2, y2, z2).setColor(r, g, b, a);
    }

    private static boolean isCrestWithCorners(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (!(stack.getItem() instanceof com.sjviklabs.squire.item.SquireCrestItem)) return false;
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return tag.contains("Corner1X");
    }
}

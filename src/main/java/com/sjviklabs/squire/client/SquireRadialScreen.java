package com.sjviklabs.squire.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.sjviklabs.squire.entity.SquireEntity;
import com.sjviklabs.squire.network.SquireCommandPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

/**
 * Radial command overlay for squire interaction (GUI-01).
 *
 * Opens when the player presses the R keybind while looking at a squire.
 * Shows 4 wedges: Follow, Stay, Guard, Inventory.
 * Hovering a wedge highlights it; left-clicking or releasing R fires the command.
 * Pressing Escape or R again closes without sending a command.
 *
 * CRITICAL: isPauseScreen() returns false — the server must keep ticking during
 * radial selection, especially in multiplayer. Never change this to true.
 *
 * CLIENT ONLY — this class must never be referenced from server-side code.
 * It lives in the client/ package and is loaded exclusively via
 * @EventBusSubscriber(value = Dist.CLIENT).
 *
 * Wedge layout (clockwise from top, angle 0 = top):
 *   Wedge 0 — Follow    (CMD_FOLLOW)
 *   Wedge 1 — Stay      (CMD_STAY)
 *   Wedge 2 — Guard     (CMD_GUARD)
 *   Wedge 3 — Inventory (CMD_INVENTORY)
 */
public class SquireRadialScreen extends Screen {

    // ---- Wedge definitions (Phase 5 scope — 4 commands only) ----

    private static final String[] WEDGE_LABELS = {"Follow", "Stay", "Guard", "Inventory"};
    private static final int[]    WEDGE_CMDS   = {
            SquireCommandPayload.CMD_FOLLOW,
            SquireCommandPayload.CMD_STAY,
            SquireCommandPayload.CMD_GUARD,
            SquireCommandPayload.CMD_INVENTORY
    };

    private static final int WEDGE_COUNT = 4;

    /**
     * Angle span of each wedge in radians. For 4 wedges: π/2 per wedge.
     * Used by computeWedge() and by render() for label placement.
     */
    static final float WEDGE_ANGLE = (float) (Math.PI / 2);

    // ---- Ring geometry ----

    /** Inner ring radius in pixels from screen center. */
    private static final float RING_INNER = 30.0F;
    /** Outer ring radius in pixels from screen center. */
    private static final float RING_OUTER = 80.0F;
    /** Dead zone: cursor within this distance ignores wedge selection. */
    private static final float DEAD_ZONE = 20.0F;
    /** Tessellation segments per wedge arc. */
    private static final int SEGMENTS = 16;

    // ---- Colors (ARGB) ----
    private static final int COLOR_NORMAL  = 0xB01A1A2E;
    private static final int COLOR_HOVER   = 0xC0D4AF37;
    private static final int COLOR_TEXT    = 0xFFFFFFFF;
    private static final int COLOR_TEXT_HL = 0xFFFFFF00;
    private static final int COLOR_BORDER  = 0xA0FFFFFF;

    // ---- State ----

    private final SquireEntity squire;
    private int hoveredWedge = -1;
    /**
     * Set to true by mouseClicked() to indicate an explicit selection was confirmed.
     * onClose() checks this before firing the packet — avoids double-send.
     */
    private boolean confirmed = false;

    public SquireRadialScreen(SquireEntity squire) {
        super(Component.translatable("squire.radial.title"));
        this.squire = squire;
    }

    /**
     * Returns false — the server must continue ticking while this screen is open.
     * This is critical for multiplayer correctness. Never change this to true.
     */
    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /**
     * No-op: skip the vanilla blurred background. The radial stays transparent
     * over the world view so the player can see their squire while selecting.
     */
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Intentionally empty — radial is a transparent overlay
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int cx = this.width / 2;
        int cy = this.height / 2;

        // Update hovered wedge from current mouse position
        float dx = mouseX - cx;
        float dy = mouseY - cy;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        hoveredWedge = (dist >= DEAD_ZONE && dist <= RING_OUTER) ? computeWedge(dx, dy) : -1;

        // Draw 4 wedges
        for (int i = 0; i < WEDGE_COUNT; i++) {
            boolean hovered = (i == hoveredWedge);
            float startAngle = i * WEDGE_ANGLE - WEDGE_ANGLE / 2.0F;
            float endAngle   = startAngle + WEDGE_ANGLE;
            drawWedge(graphics, cx, cy, startAngle, endAngle,
                    RING_INNER, RING_OUTER, hovered ? COLOR_HOVER : COLOR_NORMAL);
        }

        // Draw separator lines between wedges
        for (int i = 0; i < WEDGE_COUNT; i++) {
            float angle = i * WEDGE_ANGLE - WEDGE_ANGLE / 2.0F;
            float sinA = (float) Math.sin(angle);
            float cosA = (float) Math.cos(angle);
            drawLine(graphics, cx, cy,
                    (int) (cx + sinA * RING_OUTER), (int) (cy - cosA * RING_OUTER),
                    COLOR_BORDER);
        }

        // Draw wedge labels at the midpoint of each wedge arc
        for (int i = 0; i < WEDGE_COUNT; i++) {
            boolean hovered = (i == hoveredWedge);
            float midAngle = i * WEDGE_ANGLE;
            float labelR   = (RING_INNER + RING_OUTER) / 2.0F;
            float lx = cx + (float) Math.sin(midAngle) * labelR;
            float ly = cy - (float) Math.cos(midAngle) * labelR;

            String label = WEDGE_LABELS[i];
            int textW = this.font.width(label);
            graphics.drawString(this.font, label,
                    (int) (lx - textW / 2.0F),
                    (int) (ly - this.font.lineHeight / 2.0F),
                    hovered ? COLOR_TEXT_HL : COLOR_TEXT,
                    true);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && hoveredWedge >= 0 && hoveredWedge < WEDGE_COUNT) {
            confirmed = true;
            PacketDistributor.sendToServer(
                    new SquireCommandPayload(WEDGE_CMDS[hoveredWedge], squire.getId()));
            this.onClose();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // R key again or Escape — close without firing a command
        if (SquireKeybinds.RADIAL_MENU.matches(keyCode, scanCode)
                || keyCode == GLFW.GLFW_KEY_ESCAPE) {
            this.onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /**
     * Called when the screen is closed for any reason.
     * Only fires the packet if mouseClicked() already confirmed a selection.
     * This prevents double-send and ensures Escape/R-close is always a no-op.
     */
    @Override
    public void onClose() {
        // Packet was already sent in mouseClicked if confirmed=true — do not send again
        super.onClose();
    }

    // ---- Static utility — extracted for unit testing ----

    /**
     * Computes the wedge index (0-3) for the given cursor offset from screen center.
     *
     * Angle convention: 0 = top (negative Y), increases clockwise.
     *   atan2(dx, -dy) maps screen coordinates to this convention.
     * Negative angles from atan2 are normalized to [0, 2π) before indexing.
     *
     * @param dx cursor X offset from center (positive = right)
     * @param dy cursor Y offset from center (positive = down)
     * @return wedge index in range [0, 3]
     */
    static int computeWedge(float dx, float dy) {
        float angle = (float) Math.atan2(dx, -dy);
        if (angle < 0) angle += (float) (2.0 * Math.PI);
        int index = (int) (angle / WEDGE_ANGLE);
        // Guard against floating-point rounding producing index == WEDGE_COUNT
        return Math.min(index, WEDGE_COUNT - 1);
    }

    // ---- Rendering helpers ----

    /**
     * Draws a filled wedge arc segment between startAngle and endAngle at two radii.
     * Angle convention: 0 = top (negative Y), positive = clockwise.
     * Vertex positions: x = cx + sin(angle)*r, y = cy - cos(angle)*r.
     */
    private void drawWedge(GuiGraphics graphics, int cx, int cy,
                           float startAngle, float endAngle,
                           float innerR, float outerR, int color) {
        float a = ((color >> 24) & 0xFF) / 255.0F;
        float r = ((color >> 16) & 0xFF) / 255.0F;
        float g = ((color >> 8) & 0xFF) / 255.0F;
        float b = (color & 0xFF) / 255.0F;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Matrix4f matrix = graphics.pose().last().pose();
        BufferBuilder buf = Tesselator.getInstance().begin(
                VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);

        for (int i = 0; i <= SEGMENTS; i++) {
            float angle = startAngle + (endAngle - startAngle) * i / SEGMENTS;
            float sinA = (float) Math.sin(angle);
            float cosA = (float) Math.cos(angle);
            buf.addVertex(matrix, cx + sinA * outerR, cy - cosA * outerR, 0.0F)
                    .setColor(r, g, b, a);
            buf.addVertex(matrix, cx + sinA * innerR, cy - cosA * innerR, 0.0F)
                    .setColor(r, g, b, a);
        }

        BufferUploader.drawWithShader(buf.buildOrThrow());
        RenderSystem.disableBlend();
    }

    /**
     * Draws a line between two points using DEBUG_LINES vertex mode.
     */
    private void drawLine(GuiGraphics graphics, int x1, int y1, int x2, int y2, int color) {
        float a = ((color >> 24) & 0xFF) / 255.0F;
        float r = ((color >> 16) & 0xFF) / 255.0F;
        float g = ((color >> 8) & 0xFF) / 255.0F;
        float b = (color & 0xFF) / 255.0F;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Matrix4f matrix = graphics.pose().last().pose();
        BufferBuilder buf = Tesselator.getInstance().begin(
                VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
        buf.addVertex(matrix, x1, y1, 0.0F).setColor(r, g, b, a);
        buf.addVertex(matrix, x2, y2, 0.0F).setColor(r, g, b, a);

        BufferUploader.drawWithShader(buf.buildOrThrow());
        RenderSystem.disableBlend();
    }
}

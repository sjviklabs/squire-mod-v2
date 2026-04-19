package com.sjviklabs.squire.client;

// CLIENT ONLY — no server-side imports permitted in this file.
// See ARC-08 in ARCHITECTURE.md.

import com.sjviklabs.squire.SquireRegistry;
import com.sjviklabs.squire.inventory.SquirePostMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/**
 * Client renderer for the Squire Post GUI (v3.1.3).
 *
 * Three tabs: Status / Queue / Settings. Phase C scaffolds all three with empty
 * panels and a "sync pending" placeholder. Phase D wires Status via the server-
 * push sync packet. Phase E adds Queue interaction. Phase F adds Settings.
 *
 * Color palette mirrors SquireScreen's Ink/Brass/Vellum heraldic tokens so both
 * mod screens feel like they belong to the same family.
 */
public class SquirePostScreen extends AbstractContainerScreen<SquirePostMenu> {

    // ── Colors (mirrors SquireScreen) ─────────────────────────────────────────
    private static final int BG_COLOR       = 0xE60F0E0B;
    private static final int BG_HEADER      = 0xFF252119;
    private static final int BORDER_COLOR   = 0xFFB89558;
    private static final int LABEL_COLOR    = 0xFFE8DFC9;
    private static final int LABEL_DIM      = 0xFF8F8573;
    private static final int TAB_ACTIVE_BG  = 0xFF3C372E;
    private static final int TAB_INACTIVE_BG = 0xFF1A1712;
    private static final int ACCENT         = 0xFF4EDEA3; // Emerald

    private static final int PANEL_W = 220;
    private static final int PANEL_H = 180;

    public enum Tab {
        STATUS("Status"),
        QUEUE("Queue"),
        SETTINGS("Settings");
        final String label;
        Tab(String label) { this.label = label; }
    }

    private Tab activeTab = Tab.STATUS;
    private final Button[] tabButtons = new Button[Tab.values().length];

    public SquirePostScreen(SquirePostMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
        this.imageWidth = PANEL_W;
        this.imageHeight = PANEL_H;
        // Suppress default inventory label (no slots)
        this.inventoryLabelY = PANEL_H + 999;
        this.titleLabelY = 7;
    }

    public static void register(RegisterMenuScreensEvent event) {
        event.register(SquireRegistry.SQUIRE_POST_MENU.get(), SquirePostScreen::new);
    }

    @Override
    protected void init() {
        super.init();
        int x = leftPos + 8;
        int y = topPos + 20;
        for (int i = 0; i < Tab.values().length; i++) {
            Tab tab = Tab.values()[i];
            final Tab captured = tab;
            Button b = Button.builder(Component.literal(tab.label), btn -> setActiveTab(captured))
                    .bounds(x + i * 70, y, 68, 14)
                    .build();
            tabButtons[i] = b;
            addRenderableWidget(b);
        }
        refreshTabStates();
    }

    private void setActiveTab(Tab tab) {
        this.activeTab = tab;
        refreshTabStates();
    }

    private void refreshTabStates() {
        for (int i = 0; i < tabButtons.length; i++) {
            tabButtons[i].active = (Tab.values()[i] != activeTab);
        }
    }

    // ── Rendering ────────────────────────────────────────────────────────────

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        // Panel background
        g.fill(leftPos, topPos, leftPos + PANEL_W, topPos + PANEL_H, BG_COLOR);
        // Header strip
        g.fill(leftPos, topPos, leftPos + PANEL_W, topPos + 16, BG_HEADER);
        // Border
        drawRectBorder(g, leftPos, topPos, PANEL_W, PANEL_H, BORDER_COLOR);
        // Separator under tabs
        int sepY = topPos + 38;
        g.fill(leftPos + 4, sepY, leftPos + PANEL_W - 4, sepY + 1, ACCENT);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        // Title (top-left)
        g.drawString(font, this.title, 8, 4, LABEL_COLOR, false);

        // Bound squire name in header (right-aligned)
        String nameCache = menu.getBoundSquireNameCache();
        String headerRight = nameCache != null ? ("Bound: " + nameCache) : "Bound: —";
        int w = font.width(headerRight);
        g.drawString(font, headerRight, PANEL_W - 8 - w, 4, ACCENT, false);

        // Panel content per tab
        int cx = 8;
        int cy = 44;
        switch (activeTab) {
            case STATUS -> {
                g.drawString(font, "Status", cx, cy, LABEL_COLOR, false);
                g.drawString(font, "Live data arrives here (Phase D).", cx, cy + 14, LABEL_DIM, false);
                g.drawString(font, "HP / state / level / current task.", cx, cy + 26, LABEL_DIM, false);
            }
            case QUEUE -> {
                g.drawString(font, "Queue", cx, cy, LABEL_COLOR, false);
                g.drawString(font, "Task list arrives here (Phase E).", cx, cy + 14, LABEL_DIM, false);
                g.drawString(font, "Add / remove / reorder / clear.", cx, cy + 26, LABEL_DIM, false);
            }
            case SETTINGS -> {
                g.drawString(font, "Settings", cx, cy, LABEL_COLOR, false);
                g.drawString(font, "Role dropdown arrives here (Phase F).", cx, cy + 14, LABEL_DIM, false);
                g.drawString(font, "Home chest current + clear.", cx, cy + 26, LABEL_DIM, false);
            }
        }
    }

    private static void drawRectBorder(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }
}

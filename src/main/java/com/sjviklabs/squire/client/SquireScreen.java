package com.sjviklabs.squire.client;

// CLIENT ONLY — no server-side imports permitted in this file.
// This class is never loaded on a dedicated server. See ARC-08 in ARCHITECTURE.md.

import com.sjviklabs.squire.SquireRegistry;
import com.sjviklabs.squire.inventory.SquireMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

import javax.annotation.Nullable;

/**
 * Client-side renderer for the squire's inventory GUI (GUI-03).
 *
 * Extends AbstractContainerScreen<SquireMenu> — the menu handles all slot logic,
 * server validation, and shift-click; this class handles only visual rendering.
 *
 * Layout (mirrors v0.5.0 SquireScreen):
 *   - Left panel:   Entity preview (squire rendered in 3D, follows mouse)
 *   - Left column:  Armor slots — helmet, chest, legs, boots stacked vertically
 *   - Center grid:  Backpack slots (tier-gated rows; locked rows shown greyed out)
 *   - Right column: Weapon slots — mainhand + offhand
 *   - Bottom:       Player inventory (3x9) + hotbar (1x9)
 *   - Header:       HP bar, XP bar, mode indicator
 *
 * Registration: call SquireScreen.register() from SquireClientEvents.onClientSetup().
 * Plan 05-02 wires this — do NOT call register() from server paths.
 *
 * CLIENT ONLY — imports net.minecraft.client.*. Never referenced from server code.
 */
public class SquireScreen extends AbstractContainerScreen<SquireMenu> {

    // ── Color palette ─────────────────────────────────────────────────────────

    private static final int BG_COLOR         = 0xC0101010;
    private static final int BG_HEADER        = 0xC0181818;
    private static final int BORDER_COLOR     = 0xFF404040;
    private static final int SEPARATOR_COLOR  = 0xFF606060;
    private static final int SLOT_OUTER       = 0xFF373737;
    private static final int SLOT_INNER       = 0xFF8B8B8B;
    private static final int SLOT_LOCKED_OUTER = 0xFF2A2A2A;
    private static final int SLOT_LOCKED_INNER = 0xFF4A4A4A;
    private static final int LABEL_COLOR      = 0xFFE0E0E0;
    private static final int LABEL_DIM        = 0xFF808080;
    private static final int HP_BAR_BG        = 0xFF3A1010;
    private static final int HP_BAR_FG        = 0xFFCC3333;
    private static final int XP_BAR_BG        = 0xFF103A10;
    private static final int XP_BAR_FG        = 0xFF33CC33;
    private static final int LOCK_X_COLOR     = 0xFF5A5A5A;

    private static final String[] TIER_NAMES = {"Satchel", "Pack", "Knapsack", "War Chest"};

    // ── Layout references (derived from SquireMenu constants) ─────────────────

    private static final int ENTITY_AREA_WIDTH = SquireMenu.ENTITY_AREA_WIDTH;
    private static final int ARMOR_COL_X       = SquireMenu.ARMOR_COL_X;
    private static final int BACKPACK_X        = SquireMenu.BACKPACK_X;
    private static final int WEAPON_COL_X      = SquireMenu.WEAPON_COL_X;
    private static final int BACKPACK_Y        = SquireMenu.BACKPACK_Y;
    private static final int PLAYER_INV_X      = SquireMenu.PLAYER_INV_X;

    // ── Constructor ───────────────────────────────────────────────────────────

    public SquireScreen(SquireMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth  = menu.getGuiWidth();
        this.imageHeight = menu.getGuiHeight();
        this.inventoryLabelY = menu.getPlayerInvY() - 11;
        this.inventoryLabelX = PLAYER_INV_X;
        this.titleLabelX = ENTITY_AREA_WIDTH + 2;
        this.titleLabelY = 5;
    }

    // ── Registration ──────────────────────────────────────────────────────────

    /**
     * Registers this screen against SquireRegistry.SQUIRE_MENU via the NeoForge event.
     *
     * In NeoForge 1.21.1, MenuScreens.register() has private access and must be called
     * through RegisterMenuScreensEvent. This method is wired from
     * SquireClientEvents.ModEvents.onRegisterMenuScreens (Plan 05-02).
     *
     * Must only be called on the MOD event bus, client-side only.
     */
    public static void register(RegisterMenuScreensEvent event) {
        event.register(SquireRegistry.SQUIRE_MENU.get(), SquireScreen::new);
    }

    // ── Rendering ─────────────────────────────────────────────────────────────

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;

        // Main background
        g.fill(x, y, x + imageWidth, y + imageHeight, BG_COLOR);

        // Header background (stats area above backpack rows)
        g.fill(x, y, x + imageWidth, y + BACKPACK_Y, BG_HEADER);

        // Outer border
        drawBorder(g, x, y, imageWidth, imageHeight);

        // Header separator
        g.fill(x + 2, y + BACKPACK_Y - 1, x + imageWidth - 2, y + BACKPACK_Y, SEPARATOR_COLOR);

        // Entity preview area background (dark tint)
        g.fill(x + 2, y + BACKPACK_Y, x + ENTITY_AREA_WIDTH - 1, y + BACKPACK_Y + 4 * 18, 0x40000000);

        // Entity preview vertical separator
        g.fill(x + ENTITY_AREA_WIDTH - 1, y + BACKPACK_Y, x + ENTITY_AREA_WIDTH, y + BACKPACK_Y + 4 * 18, SEPARATOR_COLOR);

        // Entity preview
        renderEntityPreview(g, x, y, mouseX, mouseY);

        // Armor slots (left column, 4 slots)
        for (int i = 0; i < 4; i++) {
            drawSlotBg(g, x + ARMOR_COL_X, y + BACKPACK_Y + i * 18);
        }

        // Weapon slots (right column — mainhand + offhand)
        drawSlotBg(g, x + WEAPON_COL_X, y + BACKPACK_Y);
        drawSlotBg(g, x + WEAPON_COL_X, y + BACKPACK_Y + 18);

        // Backpack slots — active rows (unlocked for current tier)
        int activeRows = SquireMenu.rowsForTier(menu.getBackpackTier());
        for (int row = 0; row < activeRows; row++) {
            for (int col = 0; col < 9; col++) {
                drawSlotBg(g, x + BACKPACK_X + col * 18, y + BACKPACK_Y + row * 18);
            }
        }

        // Backpack slots — locked rows (greyed out, show tier requirement)
        int[] tierLevels = {0, 5, 10, 20};
        for (int row = activeRows; row < 4; row++) {
            for (int col = 0; col < 9; col++) {
                drawLockedSlot(g, x + BACKPACK_X + col * 18, y + BACKPACK_Y + row * 18);
            }
            String lockLabel = "Lv." + tierLevels[row];
            int labelX = x + BACKPACK_X + (9 * 18) / 2 - font.width(lockLabel) / 2;
            int labelY = y + BACKPACK_Y + row * 18 + 5;
            g.drawString(font, lockLabel, labelX, labelY, LOCK_X_COLOR, false);
        }

        // Section separator between squire inv and player inv
        int sepY = y + menu.getPlayerInvY() - 12;
        g.fill(x + 4, sepY, x + imageWidth - 4, sepY + 1, SEPARATOR_COLOR);

        // Player inventory (3x9)
        int playerInvY = y + menu.getPlayerInvY();
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                drawSlotBg(g, x + PLAYER_INV_X + col * 18, playerInvY + row * 18);
            }
        }

        // Player hotbar
        for (int col = 0; col < 9; col++) {
            drawSlotBg(g, x + PLAYER_INV_X + col * 18, playerInvY + 58);
        }

        // Stats header
        renderStats(g, x, y);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        // Player inventory label
        g.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, LABEL_COLOR, false);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        this.renderTooltip(g, mouseX, mouseY);
    }

    // ── Entity preview ────────────────────────────────────────────────────────

    private void renderEntityPreview(GuiGraphics g, int guiX, int guiY, int mouseX, int mouseY) {
        LivingEntity entity = getSquireEntity();
        if (entity == null) return;

        int centerX = guiX + ENTITY_AREA_WIDTH / 2;
        int entityY  = guiY + BACKPACK_Y + 4 * 18 - 4;
        int scale    = 28;

        float lookX = (float)(centerX - mouseX);
        float lookY = (float)(entityY - 40 - mouseY);

        InventoryScreen.renderEntityInInventoryFollowsMouse(
                g,
                centerX - 20, guiY + BACKPACK_Y + 2,
                centerX + 20, entityY,
                scale, 0.0625F,
                lookX, lookY,
                entity);
    }

    /**
     * Resolves the live squire entity from the client world using the entity ID
     * stored in the menu. Returns null if the entity is not loaded.
     */
    @Nullable
    private LivingEntity getSquireEntity() {
        if (this.minecraft == null || this.minecraft.level == null) return null;
        var entity = this.minecraft.level.getEntity(menu.getSquireEntityId());
        return (entity instanceof LivingEntity living) ? living : null;
    }

    // ── Stats header ──────────────────────────────────────────────────────────

    private void renderStats(GuiGraphics g, int guiX, int guiY) {
        int statsX    = guiX + ENTITY_AREA_WIDTH + 2;
        int row1Y     = guiY + 3;
        int row2Y     = guiY + 12;
        int barWidth  = 60;
        int barHeight = 4;
        int rightEdge = guiX + imageWidth - 4;

        int level = menu.getSquireLevel();
        String tierName = TIER_NAMES[Math.min(menu.getBackpackTier(), TIER_NAMES.length - 1)];

        // ── Row 1: "Lv.X Squire" ── Mode (right-aligned) ──
        String nameStr = "Lv." + level + " " + tierName;
        g.drawString(font, nameStr, statsX, row1Y, LABEL_COLOR, false);

        String modeStr = modeLabel(menu.getSquireMode());
        g.drawString(font, modeStr, rightEdge - font.width(modeStr), row1Y, LABEL_DIM, false);

        // ── Row 2: HP bar + XP bar ──
        // HP bar
        float hpFrac = menu.getHealthMax() > 0
                ? menu.getHealthCurrent() / menu.getHealthMax() : 0f;
        g.fill(statsX, row2Y, statsX + barWidth, row2Y + barHeight, HP_BAR_BG);
        g.fill(statsX, row2Y, statsX + (int)(barWidth * hpFrac), row2Y + barHeight, HP_BAR_FG);
        String hpStr = String.format("%.0f", menu.getHealthCurrent());
        g.drawString(font, hpStr, statsX + barWidth + 2, row2Y - 1, LABEL_COLOR, false);

        // XP bar (after HP)
        int xpBarX = statsX + barWidth + font.width(hpStr) + 8;
        float xpFrac = computeXpFraction(level, menu.getTotalXP());
        g.fill(xpBarX, row2Y, xpBarX + barWidth, row2Y + barHeight, XP_BAR_BG);
        g.fill(xpBarX, row2Y, xpBarX + (int)(barWidth * xpFrac), row2Y + barHeight, XP_BAR_FG);
        g.drawString(font, menu.getTotalXP() + "xp", xpBarX + barWidth + 2, row2Y - 1, LABEL_DIM, false);
    }

    // ── Drawing helpers ───────────────────────────────────────────────────────

    private void drawSlotBg(GuiGraphics g, int x, int y) {
        g.fill(x,     y,     x + 18, y + 18, SLOT_OUTER);
        g.fill(x + 1, y + 1, x + 17, y + 17, SLOT_INNER);
    }

    private void drawLockedSlot(GuiGraphics g, int x, int y) {
        g.fill(x,     y,     x + 18, y + 18, SLOT_LOCKED_OUTER);
        g.fill(x + 1, y + 1, x + 17, y + 17, SLOT_LOCKED_INNER);
    }

    private void drawBorder(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x,         y,         x + w, y + 1,     BORDER_COLOR); // top
        g.fill(x,         y + h - 1, x + w, y + h,     BORDER_COLOR); // bottom
        g.fill(x,         y,         x + 1, y + h,     BORDER_COLOR); // left
        g.fill(x + w - 1, y,         x + w, y + h,     BORDER_COLOR); // right
    }

    // ── Static utilities ──────────────────────────────────────────────────────

    /**
     * Very rough XP fraction within the current level.
     * Uses a simple linear xp-per-level model — proper values come from ProgressionHandler
     * when Phase 4 config is wired. Clamped to [0, 1].
     */
    private static float computeXpFraction(int level, int totalXP) {
        // Simple approximation: 100 XP per level (config-driven in a later plan)
        int xpPerLevel = 100;
        int currentLevelXP = level * xpPerLevel;
        int nextLevelXP    = (level + 1) * xpPerLevel;
        if (nextLevelXP <= currentLevelXP) return 1f;
        float frac = (float)(totalXP - currentLevelXP) / (nextLevelXP - currentLevelXP);
        return Math.max(0f, Math.min(1f, frac));
    }

    private static String modeLabel(byte mode) {
        return switch (mode) {
            case 0  -> "Follow";
            case 1  -> "Stay";
            case 2  -> "Guard";
            default -> "?";
        };
    }
}

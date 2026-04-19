package com.sjviklabs.squire.inventory;

import com.sjviklabs.squire.SquireRegistry;
import com.sjviklabs.squire.block.entity.SquirePostBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;

/**
 * Slotless menu backing the Squire Post GUI.
 *
 * No inventory slots — this menu is a pure data vessel. The Screen's tabs (Status /
 * Queue / Settings) operate over the bound squire's live state, which arrives via
 * SquirePostSyncPayload (Phase D). Mutations go out via SquirePostActionPayload
 * (Phase E+).
 *
 * Ownership is enforced at menu-open time in SquirePostBlock.useWithoutItem; the
 * server won't open this menu for a non-owner. A subsequent stillValid() check
 * guards against the post being broken / owner losing access mid-session.
 */
public class SquirePostMenu extends AbstractContainerMenu {

    private final ContainerLevelAccess access;
    private final Level level;
    private final BlockPos postPos;
    /** Cached bound squire display name for client-side rendering before sync arrives. */
    @Nullable private String boundSquireNameCache;

    /**
     * Server-side constructor — called by SquirePostBlock on menu open.
     */
    public SquirePostMenu(int windowId, Inventory playerInventory, ContainerLevelAccess access, BlockPos postPos, @Nullable String boundSquireName) {
        super(SquireRegistry.SQUIRE_POST_MENU.get(), windowId);
        this.access = access;
        this.level = playerInventory.player.level();
        this.postPos = postPos;
        this.boundSquireNameCache = boundSquireName;
    }

    /**
     * Client-side factory constructor — called when server sends the open-menu packet.
     * Extra data: BlockPos of the post + display name string.
     */
    public SquirePostMenu(int windowId, Inventory playerInventory, FriendlyByteBuf extra) {
        this(windowId, playerInventory,
                ContainerLevelAccess.NULL,
                extra.readBlockPos(),
                readNullableUtf(extra));
    }

    private static @Nullable String readNullableUtf(FriendlyByteBuf buf) {
        return buf.readBoolean() ? buf.readUtf() : null;
    }

    // ── Accessors (used by Screen) ────────────────────────────────────────────

    public BlockPos getPostPos() {
        return postPos;
    }

    @Nullable
    public String getBoundSquireNameCache() {
        return boundSquireNameCache;
    }

    public void setBoundSquireNameCache(@Nullable String name) {
        this.boundSquireNameCache = name;
    }

    // ── AbstractContainerMenu overrides ───────────────────────────────────────

    @Override
    public boolean stillValid(Player player) {
        // Delegate to access — returns false if the block at postPos is no longer the Post.
        return access.evaluate((lvl, pos) -> {
            if (!(lvl.getBlockState(pos).getBlock() instanceof com.sjviklabs.squire.block.SquirePostBlock)) {
                return false;
            }
            if (!(lvl.getBlockEntity(pos) instanceof SquirePostBlockEntity post)) {
                return false;
            }
            // Re-check ownership on every frame — player's UUID must still match.
            return post.getOwnerUUID() != null && post.getOwnerUUID().equals(player.getUUID());
        }, true);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        // No slots — shift-click has nowhere to send items. Return empty to stop vanilla.
        return ItemStack.EMPTY;
    }
}

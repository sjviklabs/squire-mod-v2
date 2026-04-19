package com.sjviklabs.squire.item;

import com.sjviklabs.squire.block.entity.SquirePostBlockEntity;
import com.sjviklabs.squire.entity.SquireEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.List;
import java.util.UUID;

/**
 * Squire Post item — placed as a block, but also serves as a "binding tool."
 *
 * Interaction flow:
 *   1. Sneak-right-click an owned squire → item records the squire's UUID/name in CUSTOM_DATA.
 *   2. Place the block → the BE reads the CUSTOM_DATA and stores owner (= placer) + binding.
 *   3. Break the block → loot table + BE.collectImplicitComponents preserve the binding
 *      back onto the dropped item. Picking up + re-placing keeps the binding AND the owner.
 *
 * The owner is preserved across pickup/re-place. A different player picking up another
 * player's dropped Post does NOT become the owner — the Post stays bound to the original
 * owner (prevents griefing via TNT-pop-and-grab).
 */
public class SquirePostItem extends BlockItem {

    public static final String TAG_OWNER_UUID = "OwnerUUID";
    public static final String TAG_SQUIRE_UUID = "BoundSquireUUID";
    public static final String TAG_SQUIRE_NAME = "BoundSquireName";

    public SquirePostItem(Block block, Properties properties) {
        super(block, properties);
    }

    // ── Bind flow ─────────────────────────────────────────────────────────────

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        // Only on server side to avoid double-binding messages
        if (player.level().isClientSide) return InteractionResult.PASS;

        if (!(target instanceof SquireEntity squire)) return InteractionResult.PASS;
        if (!squire.isOwnedBy(player)) {
            player.sendSystemMessage(Component.literal("That squire does not serve you.").withStyle(ChatFormatting.RED));
            return InteractionResult.FAIL;
        }
        if (!player.isShiftKeyDown()) {
            // Not sneaking — don't bind, let vanilla interaction (if any) handle it.
            return InteractionResult.PASS;
        }

        CustomData existing = stack.get(DataComponents.CUSTOM_DATA);
        CompoundTag tag = existing != null ? existing.copyTag() : new CompoundTag();
        tag.putUUID(TAG_SQUIRE_UUID, squire.getUUID());
        tag.putString(TAG_SQUIRE_NAME, squire.getName().getString());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));

        player.sendSystemMessage(Component.literal("Squire Post bound to " + squire.getName().getString() + ".")
                .withStyle(ChatFormatting.GREEN));
        return InteractionResult.SUCCESS;
    }

    // ── Place flow ────────────────────────────────────────────────────────────

    /**
     * Called by vanilla after the block is placed but before redstone/physics update.
     * We use this to wire: owner = placer (unless item already has an OwnerUUID), and
     * to copy the bound squire identity from item CUSTOM_DATA into the new BlockEntity.
     */
    @Override
    protected boolean updateCustomBlockEntityTag(BlockPos pos, Level level, Player player, ItemStack stack, net.minecraft.world.level.block.state.BlockState state) {
        boolean handled = super.updateCustomBlockEntityTag(pos, level, player, stack, state);
        if (level.isClientSide) return handled;

        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof SquirePostBlockEntity post)) return handled;

        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        UUID preservedOwner = null;
        UUID preservedSquire = null;
        String preservedName = null;
        if (data != null && !data.isEmpty()) {
            CompoundTag tag = data.copyTag();
            if (tag.hasUUID(TAG_OWNER_UUID)) preservedOwner = tag.getUUID(TAG_OWNER_UUID);
            if (tag.hasUUID(TAG_SQUIRE_UUID)) preservedSquire = tag.getUUID(TAG_SQUIRE_UUID);
            if (tag.contains(TAG_SQUIRE_NAME)) preservedName = tag.getString(TAG_SQUIRE_NAME);
        }

        // Preserve prior owner if the dropped item retained one; otherwise fresh owner = placer.
        UUID owner = preservedOwner != null ? preservedOwner : (player != null ? player.getUUID() : null);
        post.setOwnerUUID(owner);
        if (preservedSquire != null) {
            post.setBoundSquire(preservedSquire, preservedName);
        }
        return handled;
    }

    // ── Tooltip ──────────────────────────────────────────────────────────────

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null || data.isEmpty()) {
            tooltip.add(Component.literal("Unbound — sneak-right-click your squire to bind.").withStyle(ChatFormatting.GRAY));
            return;
        }
        CompoundTag tag = data.copyTag();
        if (tag.contains(TAG_SQUIRE_NAME)) {
            tooltip.add(Component.literal("Bound: " + tag.getString(TAG_SQUIRE_NAME)).withStyle(ChatFormatting.GREEN));
        }
        if (tag.hasUUID(TAG_OWNER_UUID)) {
            tooltip.add(Component.literal("Owner-locked").withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}

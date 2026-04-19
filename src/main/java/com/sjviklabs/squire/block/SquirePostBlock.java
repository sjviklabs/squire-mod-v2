package com.sjviklabs.squire.block;

import com.sjviklabs.squire.block.entity.SquirePostBlockEntity;
import com.sjviklabs.squire.inventory.SquirePostMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Squire Post — placed block that serves as a persistent in-world anchor for a bound squire.
 *
 * v3.1.3 (Phase A): block + block-entity scaffolding only. No interaction yet.
 * v3.1.3 (Phase B–F): item binding, GUI, packets — see plan for full scope.
 *
 * Design: one squire per post, owner-only interaction, binding survives pickup/replace.
 */
public class SquirePostBlock extends Block implements EntityBlock {

    public SquirePostBlock(Properties properties) {
        super(properties);
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SquirePostBlockEntity(pos, state);
    }

    @Override
    public boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    public int getAnalogOutputSignal(BlockState state, net.minecraft.world.level.Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof SquirePostBlockEntity post) {
            // Comparator reads queue fill: 0 (empty) → 15 (full, 16 cap).
            int queueSize = post.getLastKnownQueueSize();
            return Math.min(15, queueSize);
        }
        return 0;
    }

    // ── Right-click: open GUI (owner only) ────────────────────────────────────

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, net.minecraft.world.phys.BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer sp)) return InteractionResult.PASS;

        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof SquirePostBlockEntity post)) return InteractionResult.PASS;

        // Ownership enforcement. If unowned, any player can claim by first interaction
        // (matches the place flow where the placer becomes owner). This only matters
        // for blocks that predate the bind flow; freshly placed posts always have an owner.
        UUID owner = post.getOwnerUUID();
        if (owner == null) {
            post.setOwnerUUID(player.getUUID());
            sp.sendSystemMessage(Component.literal("You now own this Squire Post.").withStyle(ChatFormatting.GREEN));
            owner = player.getUUID();
        }
        if (!owner.equals(player.getUUID())) {
            sp.sendSystemMessage(Component.literal("This Post belongs to someone else.").withStyle(ChatFormatting.RED));
            return InteractionResult.FAIL;
        }
        if (post.getBoundSquireUUID() == null) {
            sp.sendSystemMessage(Component.literal("This Post is not bound to a squire. Break it, sneak-right-click your squire with the item, then replace it.")
                    .withStyle(ChatFormatting.YELLOW));
            return InteractionResult.FAIL;
        }

        // Open the menu. Extra data: post BlockPos + bound squire name for the client's initial render.
        final String nameCache = post.getBoundSquireName();
        final BlockPos postPos = pos;
        MenuProvider provider = new SimpleMenuProvider(
                (windowId, inv, p) -> new SquirePostMenu(windowId, inv,
                        ContainerLevelAccess.create(level, postPos), postPos, nameCache),
                Component.translatable("block.squire.squire_post")
        );
        sp.openMenu(provider, buf -> {
            buf.writeBlockPos(postPos);
            buf.writeBoolean(nameCache != null);
            if (nameCache != null) buf.writeUtf(nameCache);
        });
        return InteractionResult.CONSUME;
    }
}

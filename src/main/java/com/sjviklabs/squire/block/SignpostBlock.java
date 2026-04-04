package com.sjviklabs.squire.block;

import com.mojang.serialization.MapCodec;
import com.sjviklabs.squire.SquireRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Patrol signpost block. Places facing the player; right-click to configure.
 *
 * Crest-item linking gesture (PTR-03):
 *   1. Hold Squire's Crest, right-click signpost A → stored as pending entry
 *   2. Right-click signpost B → A.linkedSignpost = B, entry cleared
 *   Same-pos click replaces pending entry (no self-link).
 *   PlayerLoggedOutEvent clears stale entries on disconnect.
 *
 * Plain right-click (no crest): shows current mode, linked pos, wait ticks.
 */
public class SignpostBlock extends HorizontalDirectionalBlock implements EntityBlock {

    public static final MapCodec<SignpostBlock> CODEC = simpleCodec(SignpostBlock::new);

    /** Tracks which signpost each player last clicked with the Crest (pending link). */
    private static final Map<UUID, BlockPos> PENDING_LINKS = new HashMap<>();

    /** Called by SquireRegistry.onPlayerLogout to clear stale entries on disconnect. */
    public static void removePendingLink(UUID playerId) {
        PENDING_LINKS.remove(playerId);
    }

    public SignpostBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SignpostBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;

        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof SignpostBlockEntity signpost)) return InteractionResult.CONSUME;

        // Right-click with Crest: two-click linking gesture
        if (player.getMainHandItem().is(SquireRegistry.CREST.get())) {
            return handleCrestLink(player, pos, level);
        }

        // Plain right-click: show current signpost info
        String mode = signpost.getMode().name();
        BlockPos linked = signpost.getLinkedSignpost();
        String linkStr = linked != null ? linked.toShortString() : "none";
        int waitSec = signpost.getWaitTicks() / 20;
        player.sendSystemMessage(Component.literal(
                "Signpost — Mode: " + mode + " | Next: " + linkStr + " | Wait: " + waitSec + "s"));

        return InteractionResult.CONSUME;
    }

    /**
     * Two-click Crest linking gesture.
     *
     * First click: store pos as pending for this player.
     * Second click (different pos): link pending → current, clear entry.
     * Same-pos click: replace pending (no self-link).
     */
    private static InteractionResult handleCrestLink(Player player, BlockPos pos, Level level) {
        UUID playerId = player.getUUID();
        BlockPos pending = PENDING_LINKS.get(playerId);

        if (pending == null || pending.equals(pos)) {
            // First click, or clicked the same post again — update pending
            PENDING_LINKS.put(playerId, pos);
            player.sendSystemMessage(Component.literal(
                    "Signpost selected at " + pos.toShortString() + ". Right-click another signpost with Crest to link."));
            return InteractionResult.CONSUME;
        }

        // Second click on a different post — link pending → current
        BlockEntity fromBE = level.getBlockEntity(pending);
        if (fromBE instanceof SignpostBlockEntity fromSignpost) {
            fromSignpost.setLinkedSignpost(pos);
            level.sendBlockUpdated(pending, level.getBlockState(pending), level.getBlockState(pending), 3);
            player.sendSystemMessage(Component.literal(
                    "Linked: " + pending.toShortString() + " \u2192 " + pos.toShortString()));
        } else {
            player.sendSystemMessage(Component.literal("Previous signpost no longer exists."));
        }

        PENDING_LINKS.remove(playerId);
        return InteractionResult.CONSUME;
    }
}

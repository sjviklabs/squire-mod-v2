package com.sjviklabs.squire.block;

import com.sjviklabs.squire.block.entity.SquirePostBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

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
}

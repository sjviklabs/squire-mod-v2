package com.sjviklabs.squire.ai.job;

import com.minecolonies.api.entity.ai.statemachine.AITarget;
import com.minecolonies.api.entity.ai.statemachine.tickratestatemachine.TickRateStateMachine;
import com.sjviklabs.squire.ai.state.SquireAIState;
import com.sjviklabs.squire.entity.SquireEntity;
import com.sjviklabs.squire.inventory.SquireItemHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Single-block placement job AI.
 *
 * Given a target position and a block type (captured at assignment time), the squire
 * walks to within reach and places one instance of that block. Inventory is consumed.
 * Returns to IDLE when placement succeeds or the inventory no longer has the block.
 *
 * Simpler than MinerAI/LumberjackAI — no queue, no area scan. Just one block.
 * Used by {@code /squire place <pos>} to delegate a single build action.
 */
public final class PlacingAI implements JobAI {

    private static final Logger LOGGER = LoggerFactory.getLogger(PlacingAI.class);
    private static final double WALK_SPEED = 1.0;
    private static final double REACH_SQ = 25.0;
    private static final int PLACE_COOLDOWN_TICKS = 10;

    private final SquireEntity squire;
    private final TickRateStateMachine<SquireAIState> machine;

    private BlockPos targetPos = null;
    private Block targetBlock = null;
    private int cooldown = 0;

    public PlacingAI(SquireEntity squire) {
        this.squire = squire;
        this.machine = new TickRateStateMachine<>(
                SquireAIState.IDLE,
                ex -> LOGGER.error("[PlacingAI] state machine error", ex)
        );

        machine.addTransition(new AITarget<>(
                SquireAIState.IDLE,
                this::hasAssignment,
                () -> SquireAIState.PLACING,
                10
        ));

        machine.addTransition(new AITarget<>(
                SquireAIState.PLACING,
                () -> !hasAssignment(),
                () -> {
                    squire.getNavigation().stop();
                    return SquireAIState.IDLE;
                },
                5
        ));

        machine.addTransition(new AITarget<>(
                SquireAIState.PLACING,
                () -> true,
                this::tickPlacing,
                1
        ));
    }

    /** Set a single-block placement assignment. Block type captured now from squire's mainhand. */
    public void setTarget(BlockPos pos, Block block) {
        this.targetPos = pos;
        this.targetBlock = block;
        this.cooldown = 0;
    }

    /** True when there's a pending assignment we can still fulfill. */
    public boolean hasWork() {
        return hasAssignment();
    }

    /**
     * Cancel the placement assignment. Called by {@code /squire stop}. Clears target state
     * so {@link #hasAssignment} returns false on the next controller swap check.
     */
    public void clearQueue() {
        this.targetPos = null;
        this.targetBlock = null;
        squire.getNavigation().stop();
    }

    private boolean hasAssignment() {
        return targetPos != null && targetBlock != null && inventoryHasBlock(targetBlock);
    }

    private boolean inventoryHasBlock(Block block) {
        // Main hand first.
        if (squire.getMainHandItem().getItem() instanceof BlockItem bi && bi.getBlock() == block) return true;
        SquireItemHandler handler = squire.getItemHandler();
        if (handler == null) return false;
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack s = handler.getStackInSlot(i);
            if (s.getItem() instanceof BlockItem bi && bi.getBlock() == block) return true;
        }
        return false;
    }

    private SquireAIState tickPlacing() {
        if (!(squire.level() instanceof ServerLevel level)) return SquireAIState.PLACING;
        if (targetPos == null || targetBlock == null) return SquireAIState.PLACING;

        // Bail if the target is already filled with the right block.
        if (level.getBlockState(targetPos).getBlock() == targetBlock) {
            clearAssignment();
            return SquireAIState.PLACING;
        }
        // Bail if target is not air — we don't replace existing blocks.
        if (!level.getBlockState(targetPos).isAir()) {
            clearAssignment();
            return SquireAIState.PLACING;
        }

        squire.getLookControl().setLookAt(targetPos.getX() + 0.5, targetPos.getY() + 0.5, targetPos.getZ() + 0.5);
        double distSq = squire.distanceToSqr(targetPos.getX() + 0.5, targetPos.getY() + 0.5, targetPos.getZ() + 0.5);
        if (distSq > REACH_SQ) {
            squire.getNavigation().moveTo(targetPos.getX() + 0.5, targetPos.getY(), targetPos.getZ() + 0.5, WALK_SPEED);
            return SquireAIState.PLACING;
        }

        squire.getNavigation().stop();
        if (cooldown > 0) { cooldown--; return SquireAIState.PLACING; }

        if (level.setBlock(targetPos, targetBlock.defaultBlockState(), Block.UPDATE_ALL)) {
            squire.swing(InteractionHand.MAIN_HAND);
            consumeOne(targetBlock);
            clearAssignment();
        }
        cooldown = PLACE_COOLDOWN_TICKS;
        return SquireAIState.PLACING;
    }

    private void consumeOne(Block block) {
        // Main hand first.
        ItemStack main = squire.getMainHandItem();
        if (main.getItem() instanceof BlockItem bi && bi.getBlock() == block) {
            main.shrink(1);
            return;
        }
        SquireItemHandler handler = squire.getItemHandler();
        if (handler == null) return;
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack s = handler.getStackInSlot(i);
            if (s.getItem() instanceof BlockItem bi && bi.getBlock() == block) {
                handler.extractItem(i, 1, false);
                return;
            }
        }
    }

    private void clearAssignment() {
        this.targetPos = null;
        this.targetBlock = null;
    }

    @Override
    public void tick() {
        machine.tick();
    }

    @Override
    public SquireAIState getCurrentState() {
        return machine.getState();
    }
}

package com.sjviklabs.squire.ai.job;

import com.minecolonies.api.entity.ai.statemachine.AITarget;
import com.minecolonies.api.entity.ai.statemachine.tickratestatemachine.TickRateStateMachine;
import com.sjviklabs.squire.ai.state.SquireAIState;
import com.sjviklabs.squire.ai.util.BlockBreakUtil;
import com.sjviklabs.squire.config.SquireConfig;
import com.sjviklabs.squire.entity.SquireEntity;
import com.sjviklabs.squire.inventory.SquireItemHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Farming job AI — tend a crest-selected area of farmland.
 *
 * Every ~1 second the AI scans the assigned area and chooses ONE action to perform,
 * priority order:
 *   1. Harvest a mature crop (BlockTags.CROPS at max age)
 *   2. Plant a seed on empty farmland (if squire has seeds)
 *   3. Till dirt/grass into farmland (if squire has a hoe)
 *
 * Rather than queuing every tile up front (MinerAI style), the Farmer rescans after each
 * completed action. This handles growth over long sessions naturally — the squire loops
 * back to harvest whatever's newly ripe without needing a re-issued command.
 *
 * Port of v3.x FarmingHandler logic (till/plant/harvest, autoReplant on harvest) into the
 * v4.0.0 single-umbrella-state pattern. v3.1.4's direct-insert-drops fix is preserved via
 * BlockBreakUtil so seeds go straight into inventory and replant works on the NEXT tick.
 */
public final class FarmerAI implements JobAI {

    private static final Logger LOGGER = LoggerFactory.getLogger(FarmerAI.class);
    private static final double WALK_SPEED = 1.0;
    private static final double REACH_SQ = 16.0;
    private static final int ACTION_COOLDOWN_TICKS = 10;
    private static final int RESCAN_INTERVAL_TICKS = 40;

    private enum Action { HARVEST, PLANT, TILL }

    private final SquireEntity squire;
    private final TickRateStateMachine<SquireAIState> machine;

    // Assigned area (crest corners)
    private BlockPos cornerA, cornerB;

    // Current pending action + its target block
    private BlockPos actionPos;
    private Action actionKind;
    private ItemStack plantSeed = ItemStack.EMPTY;  // captured at scan time for PLANT

    private int cooldown = 0;
    private int rescanTimer = 0;

    public FarmerAI(SquireEntity squire) {
        this.squire = squire;
        this.machine = new TickRateStateMachine<>(
                SquireAIState.IDLE,
                ex -> LOGGER.error("[FarmerAI] state machine error", ex)
        );

        machine.addTransition(new AITarget<>(
                SquireAIState.IDLE,
                () -> cornerA != null && cornerB != null,
                () -> SquireAIState.FARMING,
                10
        ));

        machine.addTransition(new AITarget<>(
                SquireAIState.FARMING,
                () -> cornerA == null,   // only exits when the assignment is explicitly cleared
                () -> {
                    squire.getNavigation().stop();
                    return SquireAIState.IDLE;
                },
                10
        ));

        machine.addTransition(new AITarget<>(
                SquireAIState.FARMING,
                () -> true,
                this::tickFarming,
                1
        ));
    }

    public void setArea(BlockPos a, BlockPos b) {
        this.cornerA = a;
        this.cornerB = b;
        this.actionPos = null;
        this.actionKind = null;
        this.rescanTimer = 0;
    }

    public void stop() {
        this.cornerA = null;
        this.cornerB = null;
        this.actionPos = null;
        this.actionKind = null;
    }

    public boolean hasWork() {
        return cornerA != null && cornerB != null;
    }

    private SquireAIState tickFarming() {
        if (!(squire.level() instanceof ServerLevel level)) return SquireAIState.FARMING;

        // No pending action? Scan for one.
        if (actionPos == null) {
            if (rescanTimer > 0) { rescanTimer--; return SquireAIState.FARMING; }
            scanForTask(level);
            if (actionPos == null) {
                // Nothing to do right now — wait, don't abandon assignment.
                rescanTimer = RESCAN_INTERVAL_TICKS;
                squire.getNavigation().stop();
                return SquireAIState.FARMING;
            }
        }

        // Approach target
        squire.getLookControl().setLookAt(actionPos.getX() + 0.5, actionPos.getY() + 0.5, actionPos.getZ() + 0.5);
        double distSq = squire.distanceToSqr(actionPos.getX() + 0.5, actionPos.getY() + 0.5, actionPos.getZ() + 0.5);
        if (distSq > REACH_SQ) {
            squire.getNavigation().moveTo(actionPos.getX() + 0.5, actionPos.getY(), actionPos.getZ() + 0.5, WALK_SPEED);
            return SquireAIState.FARMING;
        }
        squire.getNavigation().stop();

        if (cooldown > 0) { cooldown--; return SquireAIState.FARMING; }

        switch (actionKind) {
            case HARVEST -> performHarvest(level);
            case PLANT   -> performPlant(level);
            case TILL    -> performTill(level);
        }

        actionPos = null;
        actionKind = null;
        plantSeed = ItemStack.EMPTY;
        cooldown = ACTION_COOLDOWN_TICKS;
        return SquireAIState.FARMING;
    }

    /**
     * Scan the assigned area for the next highest-priority task. Harvest beats plant
     * beats till; within a category, closest wins.
     */
    private void scanForTask(ServerLevel level) {
        int minX = Math.min(cornerA.getX(), cornerB.getX());
        int maxX = Math.max(cornerA.getX(), cornerB.getX());
        int minY = Math.min(cornerA.getY(), cornerB.getY());
        int maxY = Math.max(cornerA.getY(), cornerB.getY());
        int minZ = Math.min(cornerA.getZ(), cornerB.getZ());
        int maxZ = Math.max(cornerA.getZ(), cornerB.getZ());

        BlockPos closestHarvest = null, closestPlant = null, closestTill = null;
        ItemStack closestSeed = ItemStack.EMPTY;
        double closestHarvestDSq = Double.MAX_VALUE;
        double closestPlantDSq   = Double.MAX_VALUE;
        double closestTillDSq    = Double.MAX_VALUE;

        boolean hasSeeds = firstSeedStack() != null;   // short-circuit plant scan if empty
        boolean hasHoe   = BlockBreakUtil.hasToolClass(squire, HoeItem.class);

        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos ground = new BlockPos(x, y, z);
                    BlockPos crop = ground.above();
                    BlockState gs = level.getBlockState(ground);
                    BlockState cs = level.getBlockState(crop);
                    double d = squire.distanceToSqr(ground.getX() + 0.5, ground.getY() + 0.5, ground.getZ() + 0.5);

                    // Harvest has highest priority
                    if (cs.getBlock() instanceof CropBlock cb && cb.isMaxAge(cs)) {
                        if (d < closestHarvestDSq) { closestHarvest = crop; closestHarvestDSq = d; }
                        continue;
                    }
                    // Plant: farmland with air above and we have seeds
                    if (gs.getBlock() instanceof FarmBlock && cs.isAir() && hasSeeds) {
                        if (d < closestPlantDSq) {
                            ItemStack seed = firstSeedStack();
                            if (seed != null) { closestPlant = ground; closestPlantDSq = d; closestSeed = seed.copy(); }
                        }
                        continue;
                    }
                    // Till: dirt/grass with air above and we have a hoe
                    if (isTillable(gs) && cs.isAir() && hasHoe) {
                        if (d < closestTillDSq) { closestTill = ground; closestTillDSq = d; }
                    }
                }
            }
        }

        if (closestHarvest != null) {
            actionPos = closestHarvest;
            actionKind = Action.HARVEST;
            return;
        }
        if (closestPlant != null) {
            actionPos = closestPlant;
            actionKind = Action.PLANT;
            plantSeed = closestSeed;
            return;
        }
        if (closestTill != null) {
            actionPos = closestTill;
            actionKind = Action.TILL;
            BlockBreakUtil.equipToolFromBackpack(squire, HoeItem.class);
        }
    }

    private boolean isTillable(BlockState s) {
        return s.is(Blocks.DIRT) || s.is(Blocks.GRASS_BLOCK) || s.is(Blocks.COARSE_DIRT)
                || s.is(Blocks.ROOTED_DIRT) || s.is(Blocks.DIRT_PATH);
    }

    private void performHarvest(ServerLevel level) {
        BlockState state = level.getBlockState(actionPos);
        if (!(state.getBlock() instanceof CropBlock crop) || !crop.isMaxAge(state)) return;

        squire.swing(InteractionHand.MAIN_HAND);
        BlockBreakUtil.breakAndCollect(squire, level, actionPos, state);

        // Auto-replant: same tile, same seed family, on the next tick via scanForTask.
        // Direct-insert pattern from v3.1.4 means the seed drop is already in our inventory,
        // so the next scan will find the empty farmland + seeds available and plant it.
        if (squire.getProgressionHandler() != null) {
            squire.getProgressionHandler().addHarvestXP();
        }
    }

    private void performPlant(ServerLevel level) {
        BlockPos farmlandPos = actionPos;
        BlockPos plantPos = farmlandPos.above();
        if (!(level.getBlockState(farmlandPos).getBlock() instanceof FarmBlock)) return;
        if (!level.getBlockState(plantPos).isAir()) return;

        // Find a fresh seed stack at action time (the one captured at scan may have been consumed).
        ItemStack seedStack = firstSeedStack();
        if (seedStack == null) return;
        if (!(seedStack.getItem() instanceof BlockItem bi)) return;
        Block cropBlock = bi.getBlock();
        if (!(cropBlock instanceof CropBlock)) return;

        level.setBlock(plantPos, cropBlock.defaultBlockState(), Block.UPDATE_ALL);
        level.playSound(null, plantPos, SoundEvents.CROP_PLANTED, SoundSource.BLOCKS, 1.0F, 1.0F);
        squire.swing(InteractionHand.MAIN_HAND);
        consumeOneSeed(seedStack.getItem());
    }

    private void performTill(ServerLevel level) {
        BlockState state = level.getBlockState(actionPos);
        if (!isTillable(state)) return;
        if (!BlockBreakUtil.hasToolClass(squire, HoeItem.class)) return;
        level.setBlock(actionPos, Blocks.FARMLAND.defaultBlockState(), Block.UPDATE_ALL);
        level.playSound(null, actionPos, SoundEvents.HOE_TILL, SoundSource.BLOCKS, 1.0F, 1.0F);
        squire.swing(InteractionHand.MAIN_HAND);
    }

    /** First ItemStack in inventory tagged seed (via {@link ItemTags#CROP_SEEDS}), or null. */
    private ItemStack firstSeedStack() {
        SquireItemHandler handler = squire.getItemHandler();
        if (handler == null) return null;
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack s = handler.getStackInSlot(i);
            if (!s.isEmpty() && s.is(ItemTags.VILLAGER_PLANTABLE_SEEDS)) return s;
        }
        // Also accept mainhand seeds (edge case — squire might pick up seeds as drop).
        ItemStack main = squire.getMainHandItem();
        if (!main.isEmpty() && main.is(ItemTags.VILLAGER_PLANTABLE_SEEDS)) return main;
        return null;
    }

    private void consumeOneSeed(net.minecraft.world.item.Item seedItem) {
        SquireItemHandler handler = squire.getItemHandler();
        if (handler == null) return;
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack s = handler.getStackInSlot(i);
            if (!s.isEmpty() && s.getItem() == seedItem) {
                handler.extractItem(i, 1, false);
                return;
            }
        }
        ItemStack main = squire.getMainHandItem();
        if (!main.isEmpty() && main.getItem() == seedItem) main.shrink(1);
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

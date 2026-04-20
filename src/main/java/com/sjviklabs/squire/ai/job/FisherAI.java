package com.sjviklabs.squire.ai.job;

import com.minecolonies.api.entity.ai.statemachine.AITarget;
import com.minecolonies.api.entity.ai.statemachine.tickratestatemachine.TickRateStateMachine;
import com.sjviklabs.squire.ai.state.SquireAIState;
import com.sjviklabs.squire.ai.util.BlockBreakUtil;
import com.sjviklabs.squire.entity.SquireEntity;
import com.sjviklabs.squire.inventory.SquireItemHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Fishing job AI — stand near water, roll the vanilla fishing_loot table at intervals.
 *
 * Simpler than spawning a real {@link net.minecraft.world.entity.projectile.FishingHook}:
 * we sample the {@code fishing} loot table directly with the squire's held rod and enchants
 * as context. Players don't care whether a hook sprite is visible — they care that the squire
 * pulls fish out of water on a cadence. The loot table does all the real work.
 *
 * State tree: IDLE → FISHING (when assigned a water position + has rod) → back to IDLE
 * when {@code /squire fish stop} clears the assignment.
 *
 * Cast cycle: 5–15 seconds randomized per catch (vanilla range), swing rod on cast, swing
 * again on catch + particle burst. No actual projectile entity.
 */
public final class FisherAI implements JobAI {

    private static final Logger LOGGER = LoggerFactory.getLogger(FisherAI.class);
    private static final double WALK_SPEED = 1.0;
    private static final double REACH_SQ = 16.0;  // 4 blocks — rod reach is short
    private static final int MIN_CATCH_TICKS = 100;  // 5 s
    private static final int MAX_CATCH_TICKS = 300;  // 15 s

    private final SquireEntity squire;
    private final TickRateStateMachine<SquireAIState> machine;

    private BlockPos waterPos = null;   // assigned target
    private int catchTimer = 0;
    private boolean cast = false;

    public FisherAI(SquireEntity squire) {
        this.squire = squire;
        this.machine = new TickRateStateMachine<>(
                SquireAIState.IDLE,
                ex -> LOGGER.error("[FisherAI] state machine error", ex)
        );

        machine.addTransition(new AITarget<>(
                SquireAIState.IDLE,
                this::canFish,
                () -> {
                    BlockBreakUtil.equipToolFromBackpack(squire, FishingRodItem.class);
                    return SquireAIState.FISHING;
                },
                10
        ));

        machine.addTransition(new AITarget<>(
                SquireAIState.FISHING,
                () -> !canFish(),
                () -> {
                    squire.getNavigation().stop();
                    cast = false;
                    return SquireAIState.IDLE;
                },
                10
        ));

        machine.addTransition(new AITarget<>(
                SquireAIState.FISHING,
                () -> true,
                this::tickFishing,
                1
        ));
    }

    /** Start fishing at the given water position (squire walks to edge, casts into it). */
    public void start(BlockPos waterPos) {
        this.waterPos = waterPos;
        this.cast = false;
        this.catchTimer = 0;
    }

    /** Stop fishing — squire returns to idle. */
    public void stop() {
        this.waterPos = null;
        this.cast = false;
    }

    /** True when we've been told to fish and have the required tool. */
    public boolean hasWork() {
        return canFish();
    }

    private boolean canFish() {
        if (waterPos == null) return false;
        // Rod check: same mainhand-or-backpack pattern as pickaxe/axe.
        ItemStack main = squire.getMainHandItem();
        if (main.getItem() instanceof FishingRodItem) return true;
        SquireItemHandler handler = squire.getItemHandler();
        if (handler == null) return false;
        for (int i = 0; i < handler.getSlots(); i++) {
            if (handler.getStackInSlot(i).getItem() instanceof FishingRodItem) return true;
        }
        return false;
    }

    private SquireAIState tickFishing() {
        if (!(squire.level() instanceof ServerLevel level) || waterPos == null) return SquireAIState.FISHING;

        // If the assigned spot is no longer water (river drained, player built over it),
        // clear the assignment so we return to IDLE.
        BlockState ws = level.getBlockState(waterPos);
        if (!ws.getFluidState().is(net.minecraft.tags.FluidTags.WATER)) {
            waterPos = null;
            return SquireAIState.FISHING;
        }

        // Approach: stand next to water on dry ground. Squire paths to the water pos — vanilla
        // navigation will refuse to walk INTO water past wading depth, so we end up on the edge.
        squire.getLookControl().setLookAt(waterPos.getX() + 0.5, waterPos.getY() + 0.5, waterPos.getZ() + 0.5);
        double distSq = squire.distanceToSqr(waterPos.getX() + 0.5, waterPos.getY() + 0.5, waterPos.getZ() + 0.5);
        if (distSq > REACH_SQ) {
            squire.getNavigation().moveTo(waterPos.getX() + 0.5, waterPos.getY(), waterPos.getZ() + 0.5, WALK_SPEED);
            cast = false;
            return SquireAIState.FISHING;
        }

        squire.getNavigation().stop();

        if (!cast) {
            // Begin a cast — swing rod, kick off catch timer.
            squire.swing(InteractionHand.MAIN_HAND);
            level.playSound(null, squire.blockPosition(),
                    SoundEvents.FISHING_BOBBER_THROW, SoundSource.NEUTRAL, 0.6F, 0.8F);
            catchTimer = MIN_CATCH_TICKS + squire.getRandom().nextInt(MAX_CATCH_TICKS - MIN_CATCH_TICKS);
            cast = true;
            return SquireAIState.FISHING;
        }

        // During cast: emit small splash particles on the water surface every second.
        if (catchTimer % 20 == 0 && catchTimer > 0) {
            Vec3 surface = Vec3.atCenterOf(waterPos.above());
            level.sendParticles(ParticleTypes.SPLASH,
                    surface.x, surface.y, surface.z, 3,
                    0.25, 0.0, 0.25, 0.05);
        }

        catchTimer--;
        if (catchTimer > 0) return SquireAIState.FISHING;

        // Time's up — catch.
        squire.swing(InteractionHand.MAIN_HAND);
        level.playSound(null, squire.blockPosition(),
                SoundEvents.FISHING_BOBBER_RETRIEVE, SoundSource.NEUTRAL, 0.6F, 1.2F);

        LootTable table = level.getServer().reloadableRegistries()
                .getLootTable(ResourceKey.create(
                        net.minecraft.core.registries.Registries.LOOT_TABLE,
                        BuiltInLootTables.FISHING.location()));
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, squire.position())
                .withParameter(LootContextParams.TOOL, squire.getMainHandItem())
                .withParameter(LootContextParams.THIS_ENTITY, squire)
                .create(LootContextParamSets.FISHING);
        List<ItemStack> loot = table.getRandomItems(params);

        SquireItemHandler handler = squire.getItemHandler();
        for (ItemStack stack : loot) {
            ItemStack remaining = stack;
            if (handler != null) {
                for (int i = SquireItemHandler.EQUIPMENT_SLOTS; i < handler.getSlots(); i++) {
                    if (remaining.isEmpty()) break;
                    remaining = handler.insertItem(i, remaining, false);
                }
            }
            if (!remaining.isEmpty()) squire.spawnAtLocation(remaining);
        }
        // Optional: damage the rod by 1. Skip for Phase 5 MVP — add if playtest calls it out.

        cast = false;
        return SquireAIState.FISHING;
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

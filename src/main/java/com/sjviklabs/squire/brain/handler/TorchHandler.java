package com.sjviklabs.squire.brain.handler;

import com.sjviklabs.squire.config.SquireConfig;
import com.sjviklabs.squire.entity.SquireEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Side-effect torch placement handler — NOT a FSM state.
 *
 * Checks light level at the squire's position and auto-places a torch when it drops
 * below the configured threshold. Runs as a side-effect of FollowHandler.tick().
 *
 * Wire: FollowHandler calls torchHandler.tryPlaceTorch() at end of its tick method
 * (Phase 3 wiring plan).
 *
 * v0.5.0 adaptations:
 * - getSquireInventory() → getItemHandler() (IItemHandler, not SimpleContainer)
 * - removeItem(slot, 1) → extractItem(slot, 1, false) (IItemHandler API)
 * - SquireAbilities.hasAutoTorch() → squire.getLevel() >= 1 (tier ordinal gate;
 *   TODO: replace with brain.getProgression().hasAbility(SquireAbility.AUTO_TORCH) in Phase 6 progression)
 * - Activity logging via SquireConfig.activityLogging.get() guard, not ActivityLog object
 *
 * WRK-03: Squire auto-places torches in darkness.
 */
public class TorchHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(TorchHandler.class);

    private final SquireEntity squire;

    /** Ticks since last successful torch placement. Counts up; placement allowed when >= cooldown. */
    private int ticksSinceLastPlace;

    // ── Constructor ───────────────────────────────────────────────────────────

    public TorchHandler(SquireEntity squire) {
        this.squire = squire;
        // Initialize to cooldown so squire doesn't place a torch the instant it spawns
        this.ticksSinceLastPlace = SquireConfig.torchPlaceCooldown.get();
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Increments the internal tick counter and calls tryPlaceTorch() every
     * SquireConfig.torchCheckInterval.get() ticks. Call once per server tick
     * from FollowHandler.tick().
     */
    public void tick() {
        ticksSinceLastPlace++;
        if (ticksSinceLastPlace >= SquireConfig.torchCheckInterval.get()) {
            tryPlaceTorch();
        }
    }

    /**
     * Attempt to place a torch at the squire's current position.
     *
     * Returns true if a torch was placed this call (one was consumed from inventory).
     * Returns false for any of: no torches in inventory, light level at or above threshold,
     * cooldown not expired, no valid placement position, or not on server side.
     */
    public boolean tryPlaceTorch() {
        // Cooldown gate — must have waited long enough since last placement
        if (ticksSinceLastPlace < SquireConfig.torchPlaceCooldown.get()) {
            return false;
        }

        // Server-side only
        if (!(squire.level() instanceof ServerLevel serverLevel)) return false;

        // Ability gate — SERVANT tier (level 1+) can place torches
        // TODO: replace with brain.getProgression().hasAbility(SquireAbility.AUTO_TORCH) in Phase 6 progression
        if (squire.getLevel() < 1) return false;

        // Light level gate — only place in darkness
        BlockPos feetPos = squire.blockPosition();
        int blockLight = serverLevel.getBrightness(LightLayer.BLOCK, feetPos);
        if (blockLight >= SquireConfig.torchLightThreshold.get()) return false;

        // Inventory search — find a torch in any slot
        int torchSlot = findTorchSlot();
        if (torchSlot < 0) return false;

        // Find a valid position (feet first, then adjacent N/S/E/W)
        BlockPos placePos = findPlaceablePos(serverLevel, feetPos);
        if (placePos == null) return false;

        // Place the torch and consume one from inventory
        serverLevel.setBlock(placePos, Blocks.TORCH.defaultBlockState(), 3);
        squire.getItemHandler().extractItem(torchSlot, 1, false);

        // Torch placement sound
        serverLevel.playSound(null, placePos,
                SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);

        // Reset tick counter so cooldown starts from now
        ticksSinceLastPlace = 0;

        if (SquireConfig.activityLogging.get()) {
            LOGGER.debug("[Squire] Placed torch at {} (block light was {})",
                    placePos.toShortString(), blockLight);
        }

        return true;
    }

    // ── Inventory helpers ─────────────────────────────────────────────────────

    /**
     * Scan all slots in the squire's IItemHandler for a torch stack.
     * Returns the slot index of the first torch found, or -1 if none.
     */
    private int findTorchSlot() {
        IItemHandler inv = squire.getItemHandler();
        for (int i = 0; i < inv.getSlots(); i++) {
            ItemStack stack = inv.getStackInSlot(i);
            if (!stack.isEmpty() && stack.is(Items.TORCH)) {
                return i;
            }
        }
        return -1;
    }

    // ── Placement helpers ─────────────────────────────────────────────────────

    /**
     * Find the best position for torch placement near the squire's feet.
     * Tries squire's feet block position first, then N/S/E/W adjacent.
     */
    private BlockPos findPlaceablePos(ServerLevel level, BlockPos feetPos) {
        if (canPlaceTorch(level, feetPos)) return feetPos;

        for (BlockPos adjacent : new BlockPos[]{
                feetPos.north(), feetPos.south(),
                feetPos.east(),  feetPos.west()}) {
            if (canPlaceTorch(level, adjacent)) return adjacent;
        }

        return null;
    }

    /**
     * Validate that a torch can be placed at the given position:
     * - Position must be air (replaceable)
     * - Block below must be solid (torch needs a support surface)
     * - No existing torch immediately adjacent (prevents cluster spawning)
     */
    private boolean canPlaceTorch(ServerLevel level, BlockPos pos) {
        BlockState atPos = level.getBlockState(pos);
        if (!atPos.isAir()) return false;

        BlockState below = level.getBlockState(pos.below());
        if (!below.isSolid()) return false;

        // Prevent clusters — skip if any neighbor already has a torch
        for (BlockPos neighbor : new BlockPos[]{
                pos.north(), pos.south(), pos.east(), pos.west(),
                pos.above(), pos.below()}) {
            if (level.getBlockState(neighbor).getBlock() instanceof TorchBlock) {
                return false;
            }
        }

        return true;
    }
}

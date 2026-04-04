package com.sjviklabs.squire.block;

import com.sjviklabs.squire.SquireRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Stores patrol signpost configuration: mode, linked signpost, wait time, assigned owner.
 *
 * NBT keys:
 *   "Mode"           — PatrolMode enum name (String)
 *   "WaitTicks"      — int, default 40 (2 seconds)
 *   "AssignedOwner"  — UUID (int array via putUUID/getUUID)
 *   "LinkedSignpost" — BlockPos via NbtUtils.writeBlockPos / readBlockPos(tag, key)
 *
 * NbtUtils note (MC 1.21.1):
 *   writeBlockPos(pos) returns Tag (IntArrayTag) — store with tag.put(key, ...)
 *   readBlockPos(tag, key) returns Optional<BlockPos> — use .ifPresent()
 *   The zero-arg readBlockPos(CompoundTag) overload was removed in 1.20.5.
 *
 * Testing note:
 *   The static writeTag/readTag helpers are package-private so SignpostBlockEntityTest
 *   can test NBT round-trips without instantiating BlockEntity (which requires a live
 *   registry). Instance save/loadAdditional delegate to these helpers.
 */
public class SignpostBlockEntity extends BlockEntity {

    public enum PatrolMode { WAYPOINT, PATROL_START }

    private PatrolMode mode = PatrolMode.WAYPOINT;
    @Nullable private BlockPos linkedSignpost;
    private int waitTicks = 40; // 2 seconds default
    @Nullable private UUID assignedOwner;

    public SignpostBlockEntity(BlockPos pos, BlockState state) {
        super(SquireRegistry.SIGNPOST_BLOCK_ENTITY.get(), pos, state);
    }

    /**
     * Test-only factory — bypasses BlockEntityType registry for headless JUnit tests.
     * Uses a null BlockEntityType which is fine because tests never call
     * getType() or interact with the world.
     */
    @SuppressWarnings("ConstantConditions")
    public static SignpostBlockEntity forTest() {
        return new SignpostBlockEntity(null, BlockPos.ZERO, null);
    }

    /**
     * Headless factory with a pre-set linked signpost. Sets the field directly
     * (no setChanged()) so it is safe to call when level is null.
     */
    @SuppressWarnings("ConstantConditions")
    public static SignpostBlockEntity forTest(@Nullable BlockPos linkedTo) {
        SignpostBlockEntity be = new SignpostBlockEntity(null, BlockPos.ZERO, null);
        be.linkedSignpost = linkedTo; // direct field write — no setChanged(), level is null
        return be;
    }

    private SignpostBlockEntity(@Nullable net.minecraft.world.level.block.entity.BlockEntityType<?> type, BlockPos pos, @Nullable BlockState state) {
        super(type, pos, state);
    }

    // ---- Getters / Setters ----

    public PatrolMode getMode() { return mode; }
    public void setMode(PatrolMode mode) { this.mode = mode; setChanged(); }

    @Nullable
    public BlockPos getLinkedSignpost() { return linkedSignpost; }
    public void setLinkedSignpost(@Nullable BlockPos pos) { this.linkedSignpost = pos; setChanged(); }

    public int getWaitTicks() { return waitTicks; }
    public void setWaitTicks(int ticks) { this.waitTicks = ticks; setChanged(); }

    @Nullable
    public UUID getAssignedOwner() { return assignedOwner; }
    public void setAssignedOwner(@Nullable UUID owner) { this.assignedOwner = owner; setChanged(); }

    // ---- NBT (instance methods delegate to static helpers for testability) ----

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        writeTag(tag, mode, linkedSignpost, waitTicks, assignedOwner);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        NbtData data = readTag(tag);
        this.mode = data.mode;
        this.linkedSignpost = data.linkedSignpost;
        this.waitTicks = data.waitTicks;
        this.assignedOwner = data.assignedOwner;
    }

    // ---- Static NBT helpers (package-private for unit tests) ----

    // forTest() factories defined above (lines 55-68)

    /**
     * Writes signpost fields into an existing CompoundTag.
     * Called by saveAdditional and directly by tests.
     */
    static void writeTag(CompoundTag tag, PatrolMode mode, @Nullable BlockPos linkedSignpost,
                         int waitTicks, @Nullable UUID assignedOwner) {
        tag.putString("Mode", mode.name());
        tag.putInt("WaitTicks", waitTicks);
        if (assignedOwner != null) {
            tag.putUUID("AssignedOwner", assignedOwner);
        }
        if (linkedSignpost != null) {
            tag.put("LinkedSignpost", NbtUtils.writeBlockPos(linkedSignpost));
        }
    }

    /**
     * Reads signpost fields from a CompoundTag. Returns a plain data record.
     * Called by loadAdditional and directly by tests.
     */
    static NbtData readTag(CompoundTag tag) {
        PatrolMode mode = PatrolMode.WAYPOINT;
        if (tag.contains("Mode")) {
            try {
                mode = PatrolMode.valueOf(tag.getString("Mode"));
            } catch (IllegalArgumentException ignored) {
                // Unknown mode name — fall back to WAYPOINT
            }
        }

        int waitTicks = 40;
        if (tag.contains("WaitTicks")) {
            waitTicks = tag.getInt("WaitTicks");
        }

        UUID assignedOwner = null;
        if (tag.hasUUID("AssignedOwner")) {
            assignedOwner = tag.getUUID("AssignedOwner");
        }

        // NbtUtils.readBlockPos(CompoundTag, String) returns Optional<BlockPos> in 1.21.1
        BlockPos[] linkedSignpost = {null};
        NbtUtils.readBlockPos(tag, "LinkedSignpost")
                .ifPresent(pos -> linkedSignpost[0] = pos);

        return new NbtData(mode, linkedSignpost[0], waitTicks, assignedOwner);
    }

    /** Plain data holder returned by readTag — no BlockEntity lifecycle. */
    static final class NbtData {
        final PatrolMode mode;
        @Nullable final BlockPos linkedSignpost;
        final int waitTicks;
        @Nullable final UUID assignedOwner;

        NbtData(PatrolMode mode, @Nullable BlockPos linkedSignpost,
                int waitTicks, @Nullable UUID assignedOwner) {
            this.mode = mode;
            this.linkedSignpost = linkedSignpost;
            this.waitTicks = waitTicks;
            this.assignedOwner = assignedOwner;
        }
    }

    // ---- Client sync ----

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}

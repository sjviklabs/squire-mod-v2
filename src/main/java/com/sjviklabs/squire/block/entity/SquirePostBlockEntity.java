package com.sjviklabs.squire.block.entity;

import com.sjviklabs.squire.SquireRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Squire Post block entity — persists owner + bound squire identity.
 *
 * v3.1.3 scaffolding: holds identity data + last-known queue size for comparator output.
 * Phase B wires bind-on-place / drop-preserving-binding.
 * Phase C adds MenuProvider.
 * Phase D adds the 20-tick sync loop.
 */
public class SquirePostBlockEntity extends BlockEntity {

    private static final String TAG_OWNER_UUID = "OwnerUUID";
    private static final String TAG_SQUIRE_UUID = "BoundSquireUUID";
    private static final String TAG_SQUIRE_NAME = "BoundSquireName";
    private static final String TAG_LAST_QUEUE_SIZE = "LastQueueSize";

    @Nullable private UUID ownerUUID;
    @Nullable private UUID boundSquireUUID;
    @Nullable private String boundSquireNameCache;

    /** Snapshot of the bound squire's queue size at the last sync. Drives comparator output. */
    private int lastKnownQueueSize = 0;

    public SquirePostBlockEntity(BlockPos pos, BlockState state) {
        super(SquireRegistry.SQUIRE_POST_BE.get(), pos, state);
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    @Nullable
    public UUID getOwnerUUID() {
        return ownerUUID;
    }

    public void setOwnerUUID(@Nullable UUID uuid) {
        this.ownerUUID = uuid;
        setChanged();
    }

    @Nullable
    public UUID getBoundSquireUUID() {
        return boundSquireUUID;
    }

    @Nullable
    public String getBoundSquireName() {
        return boundSquireNameCache;
    }

    public void setBoundSquire(@Nullable UUID uuid, @Nullable String nameCache) {
        this.boundSquireUUID = uuid;
        this.boundSquireNameCache = nameCache;
        setChanged();
    }

    public int getLastKnownQueueSize() {
        return lastKnownQueueSize;
    }

    public void setLastKnownQueueSize(int size) {
        if (this.lastKnownQueueSize != size) {
            this.lastKnownQueueSize = size;
            setChanged();
            // Trigger comparator re-read
            if (level != null) {
                level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
            }
        }
    }

    // ── NBT persistence ───────────────────────────────────────────────────────

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (ownerUUID != null) tag.putUUID(TAG_OWNER_UUID, ownerUUID);
        if (boundSquireUUID != null) tag.putUUID(TAG_SQUIRE_UUID, boundSquireUUID);
        if (boundSquireNameCache != null) tag.putString(TAG_SQUIRE_NAME, boundSquireNameCache);
        tag.putInt(TAG_LAST_QUEUE_SIZE, lastKnownQueueSize);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.ownerUUID = tag.hasUUID(TAG_OWNER_UUID) ? tag.getUUID(TAG_OWNER_UUID) : null;
        this.boundSquireUUID = tag.hasUUID(TAG_SQUIRE_UUID) ? tag.getUUID(TAG_SQUIRE_UUID) : null;
        this.boundSquireNameCache = tag.contains(TAG_SQUIRE_NAME) ? tag.getString(TAG_SQUIRE_NAME) : null;
        this.lastKnownQueueSize = tag.getInt(TAG_LAST_QUEUE_SIZE);
    }
}

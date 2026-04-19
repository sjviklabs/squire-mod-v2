package com.sjviklabs.squire.block.entity;

import com.sjviklabs.squire.SquireRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.component.CustomData;
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

    // ── Data component bridge: BE ↔ item stack ────────────────────────────────
    //
    // Vanilla 1.21 flow: loot table copies CUSTOM_DATA from block_entity onto the
    // dropped item via copy_components. On place, applyImplicitComponents reads
    // CUSTOM_DATA back from the item. Keeps binding across break/pickup/replace
    // WITHOUT losing owner to a random picker-upper.

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        CompoundTag tag = new CompoundTag();
        if (ownerUUID != null) tag.putUUID(TAG_OWNER_UUID, ownerUUID);
        if (boundSquireUUID != null) tag.putUUID(TAG_SQUIRE_UUID, boundSquireUUID);
        if (boundSquireNameCache != null) tag.putString(TAG_SQUIRE_NAME, boundSquireNameCache);
        if (!tag.isEmpty()) {
            components.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }
    }

    @Override
    protected void applyImplicitComponents(BlockEntity.DataComponentInput input) {
        super.applyImplicitComponents(input);
        CustomData data = input.get(DataComponents.CUSTOM_DATA);
        if (data == null || data.isEmpty()) return;
        CompoundTag tag = data.copyTag();
        if (tag.hasUUID(TAG_OWNER_UUID)) this.ownerUUID = tag.getUUID(TAG_OWNER_UUID);
        if (tag.hasUUID(TAG_SQUIRE_UUID)) this.boundSquireUUID = tag.getUUID(TAG_SQUIRE_UUID);
        if (tag.contains(TAG_SQUIRE_NAME)) this.boundSquireNameCache = tag.getString(TAG_SQUIRE_NAME);
    }

    @Override
    public void removeComponentsFromTag(CompoundTag tag) {
        // These fields are read from CUSTOM_DATA via applyImplicitComponents on placement;
        // clearing them from world-save tag prevents double-storage bloat.
        tag.remove(TAG_OWNER_UUID);
        tag.remove(TAG_SQUIRE_UUID);
        tag.remove(TAG_SQUIRE_NAME);
    }
}

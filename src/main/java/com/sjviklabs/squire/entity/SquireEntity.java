package com.sjviklabs.squire.entity;

import com.sjviklabs.squire.SquireRegistry;
import com.sjviklabs.squire.brain.SquireBrain;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.util.FakePlayer;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.UUID;

/**
 * Core squire entity. Extends PathfinderMob with custom owner UUID tracking
 * (no TamableAnimal — avoids vanilla goal conflicts and taming state machine).
 *
 * IMPORTANT: This entity NEVER teleports to its owner. tryToTeleportToOwner() is
 * overridden as a no-op. Squires walk or get left behind — always.
 *
 * Architecture:
 * - Lifecycle/NBT/SynchedEntityData: this class
 * - AI/FSM/behavior: SquireBrain (stub in Phase 1, populated in Phase 2)
 * - Inventory capability: SquireItemHandler (Phase 1-03)
 *
 * SynchedEntityData fields (exactly 3, all passed SquireEntity.class):
 * - SQUIRE_MODE  Byte    0=follow, 1=sit, 2=guard
 * - SQUIRE_LEVEL Int     0-30
 * - SLIM_MODEL   Boolean false=wide arms (Steve), true=slim arms (Alex)
 */
public class SquireEntity extends PathfinderMob {

    // ---- SynchedEntityData keys (MUST pass SquireEntity.class — not any superclass) ----
    private static final EntityDataAccessor<Byte> SQUIRE_MODE =
            SynchedEntityData.defineId(SquireEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> SQUIRE_LEVEL =
            SynchedEntityData.defineId(SquireEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> SLIM_MODEL =
            SynchedEntityData.defineId(SquireEntity.class, EntityDataSerializers.BOOLEAN);

    public static final byte MODE_FOLLOW = 0;
    public static final byte MODE_SIT    = 1;
    public static final byte MODE_GUARD  = 2;

    // ---- Owner tracking (UUID-based, no TamableAnimal dependency) ----
    @Nullable
    private UUID ownerUUID;

    // ---- Progression fields (synched via SynchedEntityData, stored in NBT) ----
    private int totalXP = 0;

    // ---- Inventory capability (null until Plan 01-03 initializes it) ----
    // Declared as Object to avoid forward-reference compile errors.
    // Plan 01-03 will cast and initialize this properly.
    @Nullable
    Object itemHandler = null;

    // ---- AI (lazy-initialized in aiStep — constructor fires before registerGoals) ----
    @Nullable
    private SquireBrain squireBrain;

    // ================================================================
    // Constructor
    // ================================================================

    public SquireEntity(EntityType<? extends SquireEntity> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
        this.setCanPickUpLoot(false); // Phase 5 handles item pickup
        this.getNavigation().setCanFloat(true);
    }

    // ================================================================
    // Attributes
    // ================================================================

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.3D)
                .add(Attributes.ATTACK_DAMAGE, 3.0D)
                .add(Attributes.ARMOR, 0.0D)
                .add(Attributes.FOLLOW_RANGE, 32.0D);
    }

    // ================================================================
    // SynchedEntityData
    // ================================================================

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(SQUIRE_MODE, MODE_FOLLOW);
        builder.define(SQUIRE_LEVEL, 0);
        builder.define(SLIM_MODEL, false);
    }

    public byte getSquireMode() {
        return this.entityData.get(SQUIRE_MODE);
    }

    public void setSquireMode(byte mode) {
        this.entityData.set(SQUIRE_MODE, mode);
    }

    public int getLevel() {
        return this.entityData.get(SQUIRE_LEVEL);
    }

    public void setLevel(int level) {
        this.entityData.set(SQUIRE_LEVEL, level);
    }

    public boolean isSlimModel() {
        return this.entityData.get(SLIM_MODEL);
    }

    public void setSlimModel(boolean slim) {
        this.entityData.set(SLIM_MODEL, slim);
    }

    // ================================================================
    // Owner tracking
    // ================================================================

    @Nullable
    public UUID getOwnerUUID() {
        return this.ownerUUID;
    }

    public void setOwnerId(@Nullable UUID uuid) {
        this.ownerUUID = uuid;
    }

    /**
     * True if the given player is this squire's owner.
     * Never returns true for null UUID or fake players.
     */
    public boolean isOwnedBy(Player player) {
        if (player == null || player instanceof FakePlayer) return false;
        return player.getUUID().equals(this.ownerUUID);
    }

    // ================================================================
    // Progression accessors
    // ================================================================

    public int getTotalXP() {
        return this.totalXP;
    }

    public void setTotalXP(int xp) {
        this.totalXP = xp;
    }

    /** Convenience: current tier based on level. */
    public SquireTier getTier() {
        return SquireTier.fromLevel(getLevel());
    }

    // ================================================================
    // Goal registration
    // ================================================================

    @Override
    protected void registerGoals() {
        // FloatGoal: squire swims and doesn't drown
        this.goalSelector.addGoal(0, new FloatGoal(this));
        // OpenDoorGoal: squire can follow player through doors
        this.goalSelector.addGoal(1, new OpenDoorGoal(this, true));
        // All follow/combat/work behavior is Phase 2 (SquireBrain FSM).
    }

    // ================================================================
    // Teleport prevention — squires ALWAYS walk
    // ================================================================

    /**
     * No-op — squires never teleport to their owner.
     * They walk, sprint, or get left behind. Keeps pathfinding deterministic.
     * Not an @Override — PathfinderMob does not have this method (it's on TamableAnimal).
     */
    public void tryToTeleportToOwner() {
        // Intentional no-op — declared for documentation clarity only
    }

    // ================================================================
    // Despawn / leash / food prevention
    // ================================================================

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    // isFood() is a TamableAnimal method — not present on PathfinderMob.
    // Squires cannot be bred or fed. This comment documents the intentional omission.

    // ================================================================
    // AI tick — lazy SquireBrain init
    // ================================================================

    @Override
    public void aiStep() {
        if (!this.level().isClientSide && this.squireBrain == null) {
            this.squireBrain = new SquireBrain(this);
        }
        if (this.squireBrain != null) {
            this.squireBrain.tick();
        }
        super.aiStep();
    }

    // ================================================================
    // NBT persistence
    // ================================================================

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (this.ownerUUID != null) {
            tag.putUUID("OwnerUUID", this.ownerUUID);
        }
        tag.putByte("SquireMode", getSquireMode());
        tag.putInt("SquireLevel", getLevel());
        tag.putBoolean("SlimModel", isSlimModel());
        tag.putInt("TotalXP", this.totalXP);
        if (this.hasCustomName() && this.getCustomName() != null) {
            tag.putString("SquireName", this.getCustomName().getString());
        }
        // Inventory hook — Plan 01-03 sets itemHandler; safe if still null
        if (this.itemHandler != null) {
            // SquireItemHandler.serializeNBT() will be called by Plan 01-03 override
            // Left as hook: tag.put("Inventory", this.itemHandler.serializeNBT(provider));
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("OwnerUUID")) {
            this.ownerUUID = tag.getUUID("OwnerUUID");
        }
        if (tag.contains("SquireMode")) {
            setSquireMode(tag.getByte("SquireMode"));
        }
        if (tag.contains("SquireLevel")) {
            setLevel(tag.getInt("SquireLevel"));
        }
        if (tag.contains("SlimModel")) {
            setSlimModel(tag.getBoolean("SlimModel"));
        }
        if (tag.contains("TotalXP")) {
            this.totalXP = tag.getInt("TotalXP");
        }
        if (tag.contains("SquireName")) {
            this.setCustomName(Component.literal(tag.getString("SquireName")));
        }
        // Inventory hook — Plan 01-03 will handle "Inventory" tag deserialization
        if (tag.contains("Inventory") && this.itemHandler != null) {
            // Left as hook for Plan 01-03
        }
    }

    // ================================================================
    // Death — drops gear, retains attachment
    // ================================================================

    @Override
    public void die(DamageSource source) {
        if (!this.level().isClientSide && this.level() instanceof ServerLevel serverLevel) {
            // Find owner and persist progression to attachment before super.die() removes entity
            if (this.ownerUUID != null) {
                ServerPlayer owner = serverLevel.getServer().getPlayerList().getPlayer(this.ownerUUID);
                if (owner != null) {
                    // Write current XP/level/name back to player attachment (survives death)
                    SquireDataAttachment.SquireData data =
                            owner.getData(SquireRegistry.SQUIRE_DATA.get());
                    String name = this.hasCustomName() && this.getCustomName() != null
                            ? this.getCustomName().getString()
                            : "Squire";
                    owner.setData(SquireRegistry.SQUIRE_DATA.get(),
                            data.withXP(this.totalXP, getLevel())
                                .withName(name)
                                .withAppearance(isSlimModel())
                                .clearSquireUUID());

                    // Notify owner with death coordinates
                    int x = this.blockPosition().getX();
                    int y = this.blockPosition().getY();
                    int z = this.blockPosition().getZ();
                    owner.sendSystemMessage(Component.literal(
                            "Your squire fell at [" + x + ", " + y + ", " + z + "]"));
                }
            }

            // Drop equipped slots 0-5: armor×4 (HEAD, CHEST, LEGS, FEET) + mainhand + offhand
            // Backpack slots (if any) are lost on death — per locked design decision.
            EquipmentSlot[] equipSlots = {
                EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                EquipmentSlot.LEGS, EquipmentSlot.FEET,
                EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND
            };
            for (EquipmentSlot slot : equipSlots) {
                ItemStack stack = this.getItemBySlot(slot);
                if (!stack.isEmpty()) {
                    ItemEntity itemEntity = new ItemEntity(serverLevel,
                            this.getX(), this.getY(), this.getZ(), stack.copy());
                    serverLevel.addFreshEntity(itemEntity);
                    this.setItemSlot(slot, ItemStack.EMPTY);
                }
            }
        }

        super.die(source);
    }

    // ================================================================
    // Sounds — human-feeling squire
    // ================================================================

    @Nullable
    @Override
    protected SoundEvent getAmbientSound() {
        // ~25% chance — subtle villager hum feels more human than mob sounds
        if (this.getRandom().nextFloat() < 0.25F) {
            return SoundEvents.VILLAGER_AMBIENT;
        }
        return null;
    }

    @Override
    protected float getSoundVolume() {
        return 0.6F; // Quieter than default — squire shouldn't dominate the soundscape
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.PLAYER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.PLAYER_DEATH;
    }
}

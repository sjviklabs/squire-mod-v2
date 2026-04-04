package com.sjviklabs.squire.entity;

import com.sjviklabs.squire.SquireRegistry;
import com.sjviklabs.squire.brain.SquireActivityLog;
import com.sjviklabs.squire.brain.SquireBrain;
import com.sjviklabs.squire.inventory.SquireItemHandler;
import com.sjviklabs.squire.progression.ProgressionHandler;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.DamageTypeTags;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.Animation;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
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

import net.minecraft.world.item.NameTagItem;
import net.minecraft.core.component.DataComponents;

import javax.annotation.Nullable;
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
public class SquireEntity extends PathfinderMob implements GeoEntity {

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

    // ---- Geckolib animation references ----
    private static final RawAnimation IDLE_ANIM =
            RawAnimation.begin().thenLoop("animation.squire.idle");
    private static final RawAnimation WALK_ANIM =
            RawAnimation.begin().thenLoop("animation.squire.walk");
    private static final RawAnimation SPRINT_ANIM =
            RawAnimation.begin().thenLoop("animation.squire.sprint");
    private static final RawAnimation ATTACK_ANIM =
            RawAnimation.begin().then("animation.squire.attack", Animation.LoopType.PLAY_ONCE);

    // ---- Geckolib instance cache (MUST be private final, initialized at declaration) ----
    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    // ---- Owner tracking (UUID-based, no TamableAnimal dependency) ----
    @Nullable
    private UUID ownerUUID;

    // ---- Progression fields (synched via SynchedEntityData, stored in NBT) ----
    private int totalXP = 0;

    // ---- Inventory capability (initialized in constructor) ----
    private SquireItemHandler itemHandler;

    // ---- AI (lazy-initialized in aiStep — constructor fires before registerGoals) ----
    @Nullable
    private SquireBrain squireBrain;

    // ---- Activity log (lazy-initialized alongside brain) ----
    @Nullable
    private SquireActivityLog activityLog;

    // ---- Progression handler (lazy-initialized in aiStep — requires entity registered) ----
    @Nullable
    private ProgressionHandler progressionHandler;

    // ---- Pending horse UUID (loaded from NBT, injected into MountHandler on first brain tick) ----
    @Nullable
    public UUID pendingHorseUUID = null;

    // ================================================================
    // Constructor
    // ================================================================

    public SquireEntity(EntityType<? extends SquireEntity> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
        this.setCanPickUpLoot(false); // Phase 5 handles item pickup
        this.getNavigation().setCanFloat(true);
        this.itemHandler = new SquireItemHandler(this);
    }

    /** Returns this squire's IItemHandler (used by capability registration and SquireMenu). */
    public SquireItemHandler getItemHandler() {
        return this.itemHandler;
    }

    /** Returns the progression handler (may be null before first server-side tick). */
    @Nullable
    public ProgressionHandler getProgressionHandler() {
        return this.progressionHandler;
    }

    // ================================================================
    // Attributes
    // ================================================================

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.3D)
                .add(Attributes.ATTACK_DAMAGE, 3.0D)
                .add(Attributes.ATTACK_SPEED, 4.0D)
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
    // FSM helper methods (required by Phase 2 behavior handlers)
    // ================================================================

    /** True when the squire is in SIT mode (prevents follow/work behavior). */
    public boolean isOrderedToSit() {
        return getSquireMode() == MODE_SIT;
    }

    /** True when the squire is in FOLLOW mode. */
    public boolean shouldFollowOwner() {
        return getSquireMode() == MODE_FOLLOW;
    }

    /** Delegates to PathfinderMob.setSprinting() for use by FSM handlers. */
    public void setSquireSprinting(boolean sprint) {
        this.setSprinting(sprint);
    }

    /**
     * Returns the owning player, or null if unowned or offline.
     * OwnerHurtByTargetGoal is TamableAnimal-specific and cannot be used with
     * PathfinderMob — combat retaliation handled by SquireBrain FSM transitions.
     */
    @Nullable
    public Player getOwner() {
        if (ownerUUID == null) return null;
        return level().getPlayerByUUID(ownerUUID);
    }

    /**
     * Returns the squire's brain (FSM + event bus). May be null before first server-side tick.
     * Used by work handlers that need to fire events (e.g. WORK_TASK_COMPLETE) or force state.
     */
    @Nullable
    public SquireBrain getSquireBrain() {
        return this.squireBrain;
    }

    /**
     * Returns the activity log for this squire, creating it lazily on first call.
     * Used by TickRateStateMachine to log state transitions when activityLogging is enabled.
     */
    @Nullable
    public SquireActivityLog getActivityLog() {
        if (!this.level().isClientSide && activityLog == null) {
            activityLog = new SquireActivityLog(this);
        }
        return activityLog;
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
        // HurtByTargetGoal: squire retaliates when attacked (PathfinderMob-compatible).
        // Note: OwnerHurtByTargetGoal requires TamableAnimal — not applicable here.
        // Owner-hurt retaliation is handled by SquireBrain FSM combat transitions in Phase 2.
        this.targetSelector.addGoal(1, new net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal(this));
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
    // Inventory GUI interaction (INV-06)
    // ================================================================

    /**
     * Opens the squire's inventory screen when the owner right-clicks.
     * Only the squire's owner can open the menu. Non-owners get the default interaction.
     * Full GUI screen is registered in Phase 5; this stub opens server-side correctly.
     */
    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        // RND-03: Name tag interaction — rename the squire
        if (!this.level().isClientSide()
                && player.getItemInHand(hand).getItem() instanceof NameTagItem) {
            Component newName = player.getItemInHand(hand).get(DataComponents.CUSTOM_NAME);
            if (newName != null) {
                this.setCustomName(newName);
                this.setCustomNameVisible(true);
                if (!player.getAbilities().instabuild) {
                    player.getItemInHand(hand).shrink(1);
                }
                return InteractionResult.SUCCESS;
            }
        }

        if (!this.level().isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (this.isOwnedBy(player)) {
                final int entityId = this.getId();
                // Use NeoForge openMenu overload that accepts an extraDataWriter so the
                // client-side IContainerFactory (SQUIRE_MENU) can locate this entity by ID.
                serverPlayer.openMenu(
                    new SimpleMenuProvider(
                        (id, inv, p) -> new com.sjviklabs.squire.inventory.SquireMenu(id, inv, this),
                        Component.literal(this.hasCustomName() && this.getCustomName() != null
                            ? this.getCustomName().getString() : "Squire")
                    ),
                    buf -> buf.writeInt(entityId)
                );
                return InteractionResult.CONSUME;
            }
        }
        return super.mobInteract(player, hand);
    }

    // ================================================================
    // Chunk loading stub (ARC-09)
    // ================================================================

    /**
     * Stub: ensures the chunk at this squire's position is loaded during area clear operations.
     * Full implementation added in Phase 6 (MiningHandler) using ForceChunkManager.
     */
    public void ensureChunkLoaded() {
        // Phase 6: ForceChunkManager.forceChunk((ServerLevel)this.level(), this.chunkPosition(), this, true);
    }

    /**
     * Called by ProgressionHandler when the squire advances to a new tier.
     * In NeoForge 21.1, entity capability providers (lambdas registered via registerCapabilities)
     * are resolved fresh on each query and are not cached — no true invalidation is required.
     * SquireItemHandler.getSlots() already gates tier-based slot access dynamically.
     * This method exists as a documented hook for future capability caching if NeoForge adds it.
     */
    public void invalidateCapabilities() {
        // No-op: entity capability providers in NeoForge 21.1 are not cached per-entity.
        // SquireItemHandler slots are gated by getTier() dynamically — no invalidation needed.
    }

    // ================================================================
    // Name display (RND-03)
    // ================================================================

    /**
     * Show the name tag above the squire whenever a custom name is set,
     * regardless of look angle or distance. Matches v0.5.0 behavior.
     */
    @Override
    public boolean shouldShowName() {
        return this.hasCustomName();
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
        if (!this.level().isClientSide) {
            if (this.squireBrain == null) {
                this.squireBrain = new SquireBrain(this);
            }
            if (this.progressionHandler == null) {
                this.progressionHandler = new ProgressionHandler(this);
                // Apply attribute modifiers from saved NBT level (already set via readAdditionalSaveData)
                this.progressionHandler.setFromAttachment(this.totalXP, getLevel());
            }
        }
        if (this.squireBrain != null) {
            this.squireBrain.tick();
        }
        if (this.progressionHandler != null) {
            this.progressionHandler.tick(); // ticks down undying cooldown
        }
        // Auto-equip check every 40 ticks (~2 seconds)
        if (!this.level().isClientSide && this.tickCount % 40 == 0) {
            com.sjviklabs.squire.inventory.SquireEquipmentHelper.runFullEquipCheck(this);
        }
        super.aiStep();
        // PathfinderMob does not call updateSwingTime() automatically (only Monster does).
        // Required for weapon swing animations to complete at the correct duration.
        // See: 04-RESEARCH.md Pitfall F — attack animation duration.
        this.updateSwingTime();
    }

    /**
     * Extend swing duration to 10 ticks to match the halberd animation length.
     * PathfinderMob default is 6 ticks — too short for a two-handed weapon swing.
     * See: 04-RESEARCH.md Pitfall F — attack animation duration.
     */
    @Override
    public int getCurrentSwingDuration() {
        return 10;
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
        // Inventory — ItemStackHandler provides NBT serialization
        tag.put("Inventory", this.itemHandler.serializeNBT(this.registryAccess()));
        // Progression — save XP and level (attribute modifiers auto-saved by NeoForge)
        if (progressionHandler != null) progressionHandler.save(tag);
        // Mount — persist assigned horse UUID so squire reconnects after restart (MNT-04)
        if (squireBrain != null && squireBrain.getMountHandler().getHorseUUID() != null) {
            tag.putUUID("HorseUUID", squireBrain.getMountHandler().getHorseUUID());
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
        // Inventory — restore from NBT (ItemStackHandler handles the format)
        if (tag.contains("Inventory")) {
            this.itemHandler.deserializeNBT(this.registryAccess(), tag.getCompound("Inventory"));
        }
        // Progression — restore XP and level; handler lazy-init in aiStep() will apply modifiers
        if (progressionHandler == null) progressionHandler = new ProgressionHandler(this);
        progressionHandler.load(tag);
        // Mount — stash horse UUID for MountHandler injection on first brain tick (MNT-04)
        if (tag.hasUUID("HorseUUID")) {
            this.pendingHorseUUID = tag.getUUID("HorseUUID");
        }
    }

    // ================================================================
    // Death — drops gear, retains attachment
    // ================================================================

    @Override
    public void die(DamageSource source) {
        // PRG-06: Champion undying — survive lethal damage at 1 HP once per life (with cooldown).
        // Skip for void damage, /kill, and other insta-kill sources (Pitfall G).
        if (progressionHandler != null
                && progressionHandler.canUndying()
                && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            setHealth(1.0F);
            progressionHandler.triggerUndying();
            // Broadcast totem-of-undying particle burst to nearby clients
            if (level() instanceof ServerLevel serverLevelUndying) {
                serverLevelUndying.broadcastEntityEvent(this, (byte) 35); // vanilla totem effect ID
            }
            return; // squire lives — do NOT call super.die()
        }

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

    // ================================================================
    // GeoEntity — Geckolib animation interface
    // ================================================================

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "locomotion", 5, this::locomotionController));
        controllers.add(new AnimationController<>(this, "combat", 3, this::combatController));
    }

    private <E extends SquireEntity> PlayState locomotionController(AnimationState<E> state) {
        if (state.isMoving()) {
            return state.setAndContinue(this.isSprinting() ? SPRINT_ANIM : WALK_ANIM);
        }
        return state.setAndContinue(IDLE_ANIM);
    }

    private <E extends SquireEntity> PlayState combatController(AnimationState<E> state) {
        // Triggered animations managed via triggerAnim() from CombatHandler (Phase 4)
        return PlayState.STOP;
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.geoCache;
    }
}

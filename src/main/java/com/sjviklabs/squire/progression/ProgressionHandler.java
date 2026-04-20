package com.sjviklabs.squire.progression;

import com.sjviklabs.squire.config.SquireConfig;
import com.sjviklabs.squire.entity.SquireEntity;
import com.sjviklabs.squire.entity.SquireTier;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

import java.util.Optional;

/**
 * Tracks XP, computes tier-based levels, and applies per-tier attribute bonuses.
 *
 * XP sources: kills ({@link #addKillXP()}), mining ({@link #addMineXP()}),
 *             harvesting ({@link #addHarvestXP()}), and fishing ({@link #addFishXP()}).
 *
 * Tier stats are read from {@link ProgressionDataLoader} (datapack JSON) with fallback
 * to config-based scaling if the loader hasn't populated yet (e.g. unit-test context).
 *
 * Attribute modifiers use stable {@link ResourceLocation} IDs so they survive save/load.
 * ALWAYS call {@code instance.removeModifier(id)} before {@code instance.addPermanentModifier()}
 * to prevent double-apply on world reload (Pitfall B).
 *
 * Champion undying state is tracked here; {@link SquireEntity#die()} consults
 * {@link #canUndying()} and calls {@link #triggerUndying()} when the ability fires.
 */
public class ProgressionHandler {

    // Stable ResourceLocation IDs — must NOT change between versions (attribute modifier identity)
    private static final ResourceLocation LEVEL_HEALTH_ID =
            ResourceLocation.fromNamespaceAndPath("squire", "level_health_bonus");
    private static final ResourceLocation LEVEL_DAMAGE_ID =
            ResourceLocation.fromNamespaceAndPath("squire", "level_damage_bonus");
    private static final ResourceLocation LEVEL_SPEED_ID =
            ResourceLocation.fromNamespaceAndPath("squire", "level_speed_bonus");

    private final SquireEntity squire;
    private int totalXP;
    private int currentLevel;

    /** Server-side only. Counts down each tick; ability is ready when <= 0. */
    private int undyingCooldown = 0;

    public ProgressionHandler(SquireEntity squire) {
        this.squire = squire;
    }

    // ================================================================
    // Accessors
    // ================================================================

    public int getTotalXP() { return totalXP; }
    public int getCurrentLevel() { return currentLevel; }

    /**
     * Restore progression from player attachment data (used on resummon or server reload path).
     * Applies attribute modifiers immediately so stats are correct before first tick.
     */
    public void setFromAttachment(int xp, int level) {
        this.totalXP = xp;
        this.currentLevel = level;
        applyModifiers();
        squire.setLevel(currentLevel);
    }

    // ================================================================
    // XP sources
    // ================================================================

    /** Award XP for a mob kill. */
    public void addKillXP() {
        addXP(SquireConfig.xpPerKill.get());
    }

    /** Award XP for mining a block. */
    public void addMineXP() {
        addXP(SquireConfig.xpPerBlock.get());
    }

    /** Award XP for harvesting a mature crop. */
    public void addHarvestXP() {
        addXP(SquireConfig.xpPerHarvest.get());
    }

    /** Award XP for catching a fish. */
    public void addFishXP() {
        addXP(SquireConfig.xpPerFish.get());
    }

    // ================================================================
    // XP → level
    // ================================================================

    private void addXP(int amount) {
        this.totalXP += amount;
        // Sync back to entity so GUI, NBT, and attachment all see the updated value
        squire.setTotalXP(this.totalXP);
        recalculateLevel();
    }

    /**
     * Walk tier thresholds from the datapack loader.
     * Each tier's xpToNext is the cumulative XP required to advance past that tier.
     * Falls back to SquireTier enum minLevel boundaries if loader hasn't populated yet.
     */
    // Hardcoded fallback XP thresholds per tier (used when datapack loader hasn't populated)
    private static final int[] FALLBACK_XP = {500, 1000, 2000, 3000, 0};

    private void recalculateLevel() {
        int xpRemaining = totalXP;
        int newLevel = 0;

        SquireTier[] tiers = SquireTier.values();
        for (int i = 0; i < tiers.length; i++) {
            SquireTier tier = tiers[i];
            Optional<TierDefinition> def = ProgressionDataLoader.getTierDefinition(tier);
            int xpToNext = def.map(TierDefinition::xpToNext).orElse(FALLBACK_XP[i]);
            if (xpToNext > 0 && xpRemaining >= xpToNext) {
                xpRemaining -= xpToNext;
                if (i + 1 < tiers.length) {
                    newLevel = tiers[i + 1].getMinLevel();
                } else {
                    newLevel = SquireTier.CHAMPION.getMinLevel();
                }
            }
        }

        newLevel = Math.min(newLevel, SquireTier.CHAMPION.getMinLevel());

        if (newLevel != currentLevel) {
            currentLevel = newLevel;
            squire.setLevel(currentLevel);
            onLevelUp();
        }
    }

    // ================================================================
    // Level-up effects
    // ================================================================

    private void onLevelUp() {
        SquireTier oldTier = SquireTier.fromLevel(currentLevel - 1);
        SquireTier newTier = SquireTier.fromLevel(currentLevel);

        applyModifiers();

        // v4.0.0 Phase 1 — LEVEL_UP event removed with SquireBrain.
        // Phase 7 rewires chat personality against SquireAIController state transitions.

        // Level-up particle effect (small burst)
        if (squire.level() instanceof net.minecraft.server.level.ServerLevel sl) {
            sl.sendParticles(net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER,
                    squire.getX(), squire.getY() + 1.5, squire.getZ(),
                    15, 0.5, 0.5, 0.5, 0.1);
            squire.playSound(net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP, 1.0F, 1.0F);
        }

        if (newTier != oldTier) {
            // Refresh capability references held by pipes/hoppers — slot count expanded
            squire.invalidateCapabilities();

            // v4.0.0 Phase 1 — TIER_ADVANCE event removed with SquireBrain.
            // Phase 7 rewires chat personality against SquireAIController state transitions.

            // Tier advance: firework explosion effect + fanfare
            if (squire.level() instanceof net.minecraft.server.level.ServerLevel sl2) {
                sl2.sendParticles(net.minecraft.core.particles.ParticleTypes.FIREWORK,
                        squire.getX(), squire.getY() + 2.0, squire.getZ(),
                        50, 0.8, 1.0, 0.8, 0.2);
                sl2.sendParticles(net.minecraft.core.particles.ParticleTypes.TOTEM_OF_UNDYING,
                        squire.getX(), squire.getY() + 1.0, squire.getZ(),
                        30, 0.5, 0.8, 0.5, 0.3);
                squire.playSound(net.minecraft.sounds.SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.5F, 1.0F);
            }

            // Notify owner of tier promotion
            if (squire.getOwner() instanceof Player player) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        squire.getName().getString() + " advanced to " + newTier.name() + "!"));
            }
        }

        // Clamp HP to new max — never exceed the new ceiling
        squire.setHealth(Math.min(squire.getHealth(), squire.getMaxHealth()));

        // Play level-up sound for owner
        if (!squire.level().isClientSide && squire.level() instanceof ServerLevel serverLevel) {
            serverLevel.playSound(null, squire.blockPosition(),
                    net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP,
                    net.minecraft.sounds.SoundSource.NEUTRAL, 0.8F, 1.0F);
        }
    }

    // ================================================================
    // Attribute modifiers
    // ================================================================

    /**
     * Apply (or update) attribute modifiers from datapack tier definitions.
     * Uses remove-then-add pattern to prevent double-apply on world reload (Pitfall B).
     * Modifiers stored by NeoForge automatically — do NOT save values in progression NBT.
     */
    private void applyModifiers() {
        SquireTier currentTier = SquireTier.fromLevel(currentLevel);

        // Read stats from datapack; fall back to config-based scaling if loader isn't ready
        double maxHealth = ProgressionDataLoader.getTierDefinition(currentTier)
                .map(TierDefinition::maxHealth)
                .orElse(20.0 + currentLevel * SquireConfig.healthPerLevel.get());
        double attackDamage = ProgressionDataLoader.getTierDefinition(currentTier)
                .map(TierDefinition::attackDamage)
                .orElse(3.0 + currentLevel * SquireConfig.damagePerLevel.get());
        double movementSpeed = ProgressionDataLoader.getTierDefinition(currentTier)
                .map(TierDefinition::movementSpeed)
                .orElse(0.30 + currentLevel * SquireConfig.speedPerLevel.get());

        // Modifiers express the delta from the base attribute value
        double baseHealth = squire.getAttribute(Attributes.MAX_HEALTH) != null
                ? squire.getAttribute(Attributes.MAX_HEALTH).getBaseValue() : 20.0;
        double baseSpeed = 0.3D; // SquireEntity base movement speed

        setModifier(Attributes.MAX_HEALTH, LEVEL_HEALTH_ID, maxHealth - baseHealth);
        setModifier(Attributes.ATTACK_DAMAGE, LEVEL_DAMAGE_ID, attackDamage - 3.0);
        setModifier(Attributes.MOVEMENT_SPEED, LEVEL_SPEED_ID, movementSpeed - baseSpeed);
    }

    private void setModifier(Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute,
                             ResourceLocation id, double amount) {
        AttributeInstance instance = squire.getAttribute(attribute);
        if (instance == null) return;

        // Always remove first — prevents double-apply on world reload (Pitfall B)
        instance.removeModifier(id);

        if (amount > 0) {
            instance.addPermanentModifier(new AttributeModifier(
                    id, amount, AttributeModifier.Operation.ADD_VALUE));
        }
    }

    // ================================================================
    // Ability gates (PRG-05)
    // ================================================================

    /**
     * Returns true if the squire's current tier meets or exceeds the ability's unlock tier.
     * Uses {@link ProgressionDataLoader} ability definitions — returns false if the loader
     * hasn't run yet or the ability ID is unknown.
     *
     * Example: {@code hasAbility("COMBAT")} returns false for SERVANT, true for APPRENTICE+.
     */
    public boolean hasAbility(String abilityId) {
        return ProgressionDataLoader.getAbilityDefinition(abilityId)
                .map(def -> {
                    try {
                        SquireTier unlockTier = SquireTier.valueOf(def.unlockTier().toUpperCase());
                        return squire.getTier().ordinal() >= unlockTier.ordinal();
                    } catch (IllegalArgumentException e) {
                        return false; // unknown tier name in JSON
                    }
                })
                .orElse(false);
    }

    // ================================================================
    // Champion undying (PRG-06)
    // ================================================================

    /**
     * Returns true if the squire can trigger the undying ability.
     * Requires: CHAMPION tier and cooldown fully elapsed.
     */
    public boolean canUndying() {
        return squire.getTier() == SquireTier.CHAMPION && undyingCooldown <= 0;
    }

    /**
     * Arm the cooldown after undying fires. Called by {@link SquireEntity#die()}.
     */
    public void triggerUndying() {
        undyingCooldown = SquireConfig.undyingCooldownTicks.get();
    }

    /**
     * Called every tick from {@link SquireEntity#aiStep()}.
     * Decrements the undying cooldown.
     */
    public void tick() {
        if (undyingCooldown > 0) undyingCooldown--;
    }

    // ================================================================
    // NBT persistence
    // ================================================================

    /**
     * Save totalXP and currentLevel. Do NOT save attribute modifier values —
     * NeoForge persists permanent modifiers automatically; saving them manually
     * causes double-application on load (Pitfall B).
     */
    public void save(CompoundTag tag) {
        CompoundTag prog = new CompoundTag();
        prog.putInt("TotalXP", totalXP);
        prog.putInt("Level", currentLevel);
        // undyingCooldown is intentionally not saved — resets on entity respawn
        tag.put("SquireProgression", prog);
    }

    /**
     * Restore XP and level from NBT, then re-apply attribute modifiers.
     * Called from {@link SquireEntity#readAdditionalSaveData(CompoundTag)}.
     */
    public void load(CompoundTag tag) {
        if (tag.contains("SquireProgression")) {
            CompoundTag prog = tag.getCompound("SquireProgression");
            this.totalXP = prog.getInt("TotalXP");
            this.currentLevel = prog.getInt("Level");
            applyModifiers();
            squire.setLevel(currentLevel);
        }
    }
}

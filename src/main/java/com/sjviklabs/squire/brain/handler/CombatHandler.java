package com.sjviklabs.squire.brain.handler;

import com.sjviklabs.squire.brain.SquireAIState;
import com.sjviklabs.squire.config.SquireConfig;
import com.sjviklabs.squire.data.SquireTagKeys;
import com.sjviklabs.squire.entity.SquireEntity;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ShieldItem;

/**
 * Handles combat approach, attack cooldown, leash distance, and reach calculation.
 * Selects a tactic based on mob type (via entity tags — never instanceof) and drives
 * behavior per-tick accordingly.
 *
 * Architecture:
 * - start()  called when entering COMBAT_APPROACH state
 * - tick()   drives melee combat, dispatches to tactic methods
 * - stop()   called when leaving combat (clears target, stops nav)
 * - tickRanged() separate entry from SquireBrain for ranged state
 *
 * Tactic selection uses SquireTagKeys exclusively — no instanceof checks.
 * Tags are resolved in start() during live gameplay, never at class init.
 *
 * Guard mode (CMB-10): when squire is in MODE_GUARD, leash-breach disengage is skipped.
 * Flee (CMB-06): fires when HP fraction < fleeHealthThreshold and target is non-null.
 */
public class CombatHandler {

    private static final double RANGED_MIN_DIST = 4.0; // blocks; closer than this = switch to melee

    // ---- Tactic enum ----

    public enum CombatTactic {
        AGGRESSIVE,  // zombies, spiders, husks, hoglin -- close fast, circle-strafe, reactive shield
        CAUTIOUS,    // skeletons, strays, pillagers -- shield up while approaching, sprint to close gap
        EVASIVE,     // witches, evokers, blazes -- prefer ranged, hit-and-run if melee, dodge
        EXPLOSIVE,   // creepers -- ranged only if possible, flee if swelling
        PASSIVE,     // endermen -- don't engage unless provoked
        DEFAULT      // unknown/modded mobs -- basic melee behavior
    }

    private final SquireEntity squire;
    private int ticksUntilNextAttack;
    private int attackCooldown;

    // ---- Tactic state ----
    private CombatTactic currentTactic = CombatTactic.DEFAULT;
    private int shieldUpTicks;
    private int strafeDirection;    // 1 or -1
    private int strafeTicks;        // countdown for current strafe movement
    private int hitAndRunCooldown;  // ticks before re-engaging after hit-and-run
    private int dodgeCooldown;      // ticks until next evasive sidestep
    private int swingWindowTicks;   // ticks remaining in cautious swing window
    private boolean lastHitLanded;

    // ---- Flee state ----
    private boolean isFleeing;
    private int fleeEndTick;
    private int currentTick;

    // ---- Ranged state ----
    private int rangedChargeTicks;  // ticks bow has been drawn

    public CombatHandler(SquireEntity squire) {
        this.squire = squire;
    }

    // ================================================================
    // Lifecycle
    // ================================================================

    /**
     * Called when entering combat. Resets attack cooldown and selects tactic.
     * Combat is available from level 1 (SERVANT tier) — no tier gate.
     */
    public void start() {
        squire.setSquireSprinting(false);
        ticksUntilNextAttack = 0;
        isFleeing = false;
        fleeEndTick = 0;
        recalculateAttackCooldown();
        resetTacticState();

        LivingEntity target = squire.getTarget();
        if (target != null) {
            this.currentTactic = selectTactic(target);
        }

        var log = squire.getActivityLog();
        if (log != null) {
            String targetName = target != null ? target.getType().toShortString() : "unknown";
            log.log("COMBAT", "Engaging " + targetName + " [" + currentTactic + "]");
        }
    }

    /**
     * Called when leaving combat. Clears target, stops navigation, lowers shield.
     */
    public void stop() {
        disengageCombat(squire);
        isFleeing = false;
    }

    // ================================================================
    // Tactic selection — tag-based dispatch, no instanceof
    // ================================================================

    /**
     * Selects combat tactic based on target entity type tags.
     * Called only from start() during live gameplay, never at class init.
     * Tags are populated by the server's datapack loading — safe to call here.
     */
    private CombatTactic selectTactic(LivingEntity target) {
        if (target.getType().is(SquireTagKeys.EXPLOSIVE_THREAT)) return CombatTactic.EXPLOSIVE;
        if (target.getType().is(SquireTagKeys.DO_NOT_ATTACK))    return CombatTactic.PASSIVE;
        if (target.getType().is(SquireTagKeys.RANGED_EVASIVE))   return CombatTactic.EVASIVE;
        if (target.getType().is(SquireTagKeys.MELEE_CAUTIOUS))   return CombatTactic.CAUTIOUS;
        if (target.getType().is(SquireTagKeys.MELEE_AGGRESSIVE)) return CombatTactic.AGGRESSIVE;
        return CombatTactic.DEFAULT;
    }

    private void resetTacticState() {
        shieldUpTicks = 0;
        strafeDirection = 0;
        strafeTicks = 0;
        hitAndRunCooldown = 0;
        dodgeCooldown = 0;
        swingWindowTicks = 0;
        lastHitLanded = false;
        rangedChargeTicks = 0;
    }

    // ================================================================
    // Shield management (called every tick during combat)
    // ================================================================

    /**
     * Updates shield state: raise when target recently attacked, lower when attacking.
     * Only activates when shieldBlocking config is true and offhand item is a shield.
     */
    private void updateShield(SquireEntity s) {
        if (!SquireConfig.shieldBlocking.get()) return;
        if (!(s.getItemBySlot(EquipmentSlot.OFFHAND).getItem() instanceof ShieldItem)) return;

        if (shieldUpTicks > 0) {
            if (shieldUpTicks < Integer.MAX_VALUE) shieldUpTicks--;
            if (!s.isUsingItem()) {
                s.startUsingItem(InteractionHand.OFF_HAND);
            }
        } else {
            if (s.isUsingItem() && s.getUsedItemHand() == InteractionHand.OFF_HAND) {
                s.stopUsingItem();
            }
        }
    }

    /**
     * Called when the squire takes damage. Triggers reactive shield raise for
     * AGGRESSIVE and DEFAULT tactics.
     */
    public void onDamageTaken() {
        if (currentTactic == CombatTactic.AGGRESSIVE || currentTactic == CombatTactic.DEFAULT) {
            shieldUpTicks = SquireConfig.shieldCooldownTicks.get();
        }
    }

    /** Current tactic, exposed for debug/logging. */
    public CombatTactic getCurrentTactic() {
        return currentTactic;
    }

    /** Ticks until the next melee swing is ready. Exposed for /squire info diagnostics. */
    public int getTicksUntilNextAttack() {
        return ticksUntilNextAttack;
    }

    /** Configured attack cooldown (ticks) after each swing. Exposed for /squire info diagnostics. */
    public int getAttackCooldown() {
        return attackCooldown;
    }

    // ================================================================
    // Flee logic (CMB-06)
    // ================================================================

    /**
     * Initiates flee: clears target, paths toward owner at speed 1.4,
     * schedules flee end after 60 ticks (3 seconds).
     * Public so DangerHandler can trigger it directly.
     */
    public void startFlee() {
        if (isFleeing) return;
        isFleeing = true;
        fleeEndTick = currentTick + 60;
        squire.setTarget(null);
        shieldUpTicks = 0;

        Player owner = squire.getOwner();
        if (owner != null) {
            squire.getNavigation().moveTo(owner, 1.4D);
        }

        var log = squire.getActivityLog();
        if (log != null) {
            log.log("COMBAT", "Fleeing! HP: "
                    + String.format("%.1f", squire.getHealth()) + "/" + squire.getMaxHealth());
        }
    }

    /** True while squire is in flee mode. */
    public boolean isFleeing() {
        return isFleeing;
    }

    // ================================================================
    // Per-tick melee combat — dispatches to tactic-specific behavior
    // ================================================================

    /**
     * Per-tick combat logic: flee check, guard mode, tactic dispatch.
     * Returns IDLE if combat should end, COMBAT_APPROACH to continue,
     * COMBAT_RANGED if a tactic switches to ranged.
     */
    public SquireAIState tick(SquireEntity s) {
        currentTick++;

        LivingEntity target = s.getTarget();

        // ---- Global flee check (CMB-06) ----
        if (target != null
                && s.getHealth() / s.getMaxHealth() < SquireConfig.fleeHealthThreshold.get().floatValue()
                && !isFleeing) {
            startFlee();
        }

        // ---- Handle active flee ----
        if (isFleeing) {
            if (currentTick >= fleeEndTick) {
                isFleeing = false;
            } else {
                // Keep moving toward owner
                Player owner = s.getOwner();
                if (owner != null) {
                    s.getNavigation().moveTo(owner, 1.4D);
                }
                return SquireAIState.FLEEING;
            }
        }

        if (target == null || !target.isAlive()) {
            s.getNavigation().stop();
            shieldUpTicks = 0;
            return SquireAIState.IDLE;
        }
        if (s.isOrderedToSit()) {
            s.getNavigation().stop();
            shieldUpTicks = 0;
            return SquireAIState.IDLE;
        }

        // ---- Owner leash — disengage if too far from owner (guard mode bypasses this) ----
        if (isLeashBreached(s)) {
            if (s.getSquireMode() != SquireEntity.MODE_GUARD) {
                // Only disengage if not in guard mode
                disengageCombat(s);
                return SquireAIState.IDLE;
            }
            // Guard mode: hold position and keep fighting regardless of leash
        }

        // ---- Follow range — stop pathing if target too far, but DON'T clear the target. ----
        // v3.1.5 fix: clearing the target here caused a race with vanilla
        // NearestAttackableTargetGoal (which re-targets on its own cadence), producing
        // a "scream every 5 ticks, never swing" loop. Target lifecycle is now owned by
        // the combat-exit transition and by the eventual v3.2.0 SquireTargetSuggestionGoal.
        // This check just stops the chase when the target is unreachably far; if the
        // target moves back into range next tick, we resume.
        double followRange = s.getAttributeValue(Attributes.FOLLOW_RANGE);
        if (s.distanceToSqr(target) > followRange * followRange) {
            s.getNavigation().stop();
            shieldUpTicks = 0;
            return SquireAIState.COMBAT_APPROACH;
        }

        // ---- Update shield state every tick ----
        updateShield(s);

        // ---- Attack cooldown ----
        ticksUntilNextAttack = Math.max(ticksUntilNextAttack - 1, 0);

        // ---- Dispatch to tactic ----
        return switch (currentTactic) {
            case AGGRESSIVE -> tickAggressive(s, target);
            case CAUTIOUS   -> tickCautious(s, target);
            case EVASIVE    -> tickEvasive(s, target);
            case EXPLOSIVE  -> tickExplosive(s, target);
            case PASSIVE    -> tickPassive(s, target);
            default         -> tickDefault(s, target);
        };
    }

    // ---- AGGRESSIVE: sprint in, swing, circle-strafe after hit, reactive shield ----

    private SquireAIState tickAggressive(SquireEntity s, LivingEntity target) {
        // Circle-strafe if active
        if (strafeTicks > 0) {
            strafeTicks--;
            movePerpendicularToTarget(s, target);
            // Still try to attack during strafe if in range
            tryMeleeAttack(s, target);
            return SquireAIState.COMBAT_APPROACH;
        }

        // Look + sprint toward target
        s.getLookControl().setLookAt(target, 30.0F, 30.0F);
        s.getNavigation().moveTo(target, 1.2D);

        if (tryMeleeAttack(s, target) && lastHitLanded) {
            // Start circle-strafe after landing a hit (base 8 ticks +0-5 random)
            strafeDirection = s.getRandom().nextBoolean() ? 1 : -1;
            strafeTicks = 8 + s.getRandom().nextInt(6);
        }

        return SquireAIState.COMBAT_APPROACH;
    }

    // ---- CAUTIOUS: shield up while approaching, lower to swing, sprint to close ----

    private SquireAIState tickCautious(SquireEntity s, LivingEntity target) {
        s.getLookControl().setLookAt(target, 30.0F, 30.0F);

        double distSq = s.distanceToSqr(target);
        double reach = getAttackReachSq(target);

        // Sprint to close gap
        s.getNavigation().moveTo(target, 1.3D);

        // If in swing window, try to attack then re-raise shield
        if (swingWindowTicks > 0) {
            swingWindowTicks--;
            tryMeleeAttack(s, target);
            if (swingWindowTicks <= 0) {
                // Swing window closed -- re-raise shield
                shieldUpTicks = Integer.MAX_VALUE;
            }
            return SquireAIState.COMBAT_APPROACH;
        }

        // Shield up while approaching (if shield equipped and config allows)
        if (SquireConfig.shieldBlocking.get()
                && s.getItemBySlot(EquipmentSlot.OFFHAND).getItem() instanceof ShieldItem) {
            shieldUpTicks = Integer.MAX_VALUE;
        }

        // In melee range and attack ready -- lower shield to swing
        if (distSq <= reach && ticksUntilNextAttack <= 0) {
            shieldUpTicks = 0; // Lower shield
            swingWindowTicks = 5; // 5 ticks to land the swing
            tryMeleeAttack(s, target);
        }

        // If has ranged capability (bow in mainhand) and target is beyond rangedRange: switch
        double rangedThresh = SquireConfig.rangedRange.get();
        if (s.distanceToSqr(target) > rangedThresh * rangedThresh
                && s.getItemBySlot(EquipmentSlot.MAINHAND).getItem() instanceof BowItem
                && !s.getProjectile(s.getItemBySlot(EquipmentSlot.MAINHAND)).isEmpty()) {
            shieldUpTicks = 0;
            return SquireAIState.COMBAT_RANGED;
        }

        return SquireAIState.COMBAT_APPROACH;
    }

    // ---- EVASIVE: prefer ranged, hit-and-run if melee, periodic dodge ----

    private SquireAIState tickEvasive(SquireEntity s, LivingEntity target) {
        s.getLookControl().setLookAt(target, 30.0F, 30.0F);

        // Prefer ranged if bow equipped with arrows
        if (s.getItemBySlot(EquipmentSlot.MAINHAND).getItem() instanceof BowItem
                && !s.getProjectile(s.getItemBySlot(EquipmentSlot.MAINHAND)).isEmpty()) {
            shieldUpTicks = 0;
            return SquireAIState.COMBAT_RANGED;
        }

        // No ranged -- hit-and-run melee
        double distSq = s.distanceToSqr(target);
        double reach = getAttackReachSq(target);

        // Dodge: sidestep every N ticks (default 20)
        if (dodgeCooldown > 0) {
            dodgeCooldown--;
        } else {
            dodgeCooldown = 20;
            strafeDirection = s.getRandom().nextBoolean() ? 1 : -1;
            movePerpendicularToTarget(s, target);
        }

        // Hit-and-run cooldown: retreat phase
        if (hitAndRunCooldown > 0) {
            hitAndRunCooldown--;
            // Shield up while retreating
            if (SquireConfig.shieldBlocking.get()
                    && s.getItemBySlot(EquipmentSlot.OFFHAND).getItem() instanceof ShieldItem) {
                shieldUpTicks = SquireConfig.shieldCooldownTicks.get();
            }
            retreatFromTarget(s, target, 5.0);
            return SquireAIState.COMBAT_APPROACH;
        }

        // Sprint in to attack
        s.getNavigation().moveTo(target, 1.3D);

        if (distSq <= reach && ticksUntilNextAttack <= 0) {
            tryMeleeAttack(s, target);
            // After 1-2 hits, sprint away (15 ticks retreat)
            hitAndRunCooldown = 15;
        }

        return SquireAIState.COMBAT_APPROACH;
    }

    // ---- EXPLOSIVE: ranged if possible, hit-and-run if not, flee if swelling ----

    private SquireAIState tickExplosive(SquireEntity s, LivingEntity target) {
        s.getLookControl().setLookAt(target, 30.0F, 30.0F);

        // DangerHandler handles the actual flee trigger for explosive entities
        // This tactic keeps squire at range; if swelling detected, flee is triggered externally

        // Force ranged if bow available
        if (s.getItemBySlot(EquipmentSlot.MAINHAND).getItem() instanceof BowItem
                && !s.getProjectile(s.getItemBySlot(EquipmentSlot.MAINHAND)).isEmpty()) {
            shieldUpTicks = 0;
            return SquireAIState.COMBAT_RANGED;
        }

        // No ranged -- hit-and-run: sprint in, hit once, sprint away immediately
        double distSq = s.distanceToSqr(target);
        double reach = getAttackReachSq(target);

        if (hitAndRunCooldown > 0) {
            hitAndRunCooldown--;
            retreatFromTarget(s, target, 8.0);
            return SquireAIState.COMBAT_APPROACH;
        }

        // Sprint in
        s.getNavigation().moveTo(target, 1.4D);

        if (distSq <= reach && ticksUntilNextAttack <= 0) {
            tryMeleeAttack(s, target);
            // Immediately retreat after one hit (20 ticks retreat)
            hitAndRunCooldown = 20;
        }

        return SquireAIState.COMBAT_APPROACH;
    }

    // ---- PASSIVE: don't engage enderman unless provoked ----

    private SquireAIState tickPassive(SquireEntity s, LivingEntity target) {
        // Only fight if the entity is actively attacking us or our owner
        boolean provoked = false;
        if (s.getLastHurtByMob() == target) provoked = true;
        if (s.getOwner() instanceof Player owner) {
            if (target.getLastHurtByMob() == owner || owner.getLastHurtByMob() == target) {
                provoked = true;
            }
        }

        if (!provoked) {
            // Don't engage -- avoid eye contact, stand still
            s.getNavigation().stop();
            return SquireAIState.COMBAT_APPROACH;
        }

        // Provoked: fight like AGGRESSIVE but look at feet (avoids enraging enderman further)
        s.getLookControl().setLookAt(target.getX(), target.getY() - 1.5, target.getZ(), 30.0F, 30.0F);
        s.getNavigation().moveTo(target, 1.2D);

        // Circle-strafe if active
        if (strafeTicks > 0) {
            strafeTicks--;
            movePerpendicularToTarget(s, target);
            tryMeleeAttack(s, target);
            return SquireAIState.COMBAT_APPROACH;
        }

        if (tryMeleeAttack(s, target) && lastHitLanded) {
            strafeDirection = s.getRandom().nextBoolean() ? 1 : -1;
            strafeTicks = 8 + s.getRandom().nextInt(6);
        }

        return SquireAIState.COMBAT_APPROACH;
    }

    // ---- DEFAULT: basic approach + swing + reactive shield ----

    private SquireAIState tickDefault(SquireEntity s, LivingEntity target) {
        s.getLookControl().setLookAt(target, 30.0F, 30.0F);
        s.getNavigation().moveTo(target, 1.2D);
        tryMeleeAttack(s, target);
        return SquireAIState.COMBAT_APPROACH;
    }

    // ================================================================
    // Melee attack helper -- shared by all tactics
    // ================================================================

    /**
     * Attempts a melee attack if in range and cooldown is ready.
     * Returns true if an attack was attempted; sets lastHitLanded.
     * Lowers shield before swinging (shield up prevents own attack).
     */
    private boolean tryMeleeAttack(SquireEntity s, LivingEntity target) {
        double distSq = s.distanceToSqr(
                target.getX(), target.getBoundingBox().minY, target.getZ());
        double reach = getAttackReachSq(target);

        if (distSq <= reach && ticksUntilNextAttack <= 0) {
            // Lower shield for attack (CMB-05: shield down when squire attacks)
            shieldUpTicks = 0;
            if (s.isUsingItem() && s.getUsedItemHand() == InteractionHand.OFF_HAND) {
                s.stopUsingItem();
            }

            s.swing(InteractionHand.MAIN_HAND);
            boolean hitLanded = s.doHurtTarget(target);
            this.lastHitLanded = hitLanded;
            recalculateAttackCooldown();

            if (hitLanded) {
                s.playSound(SoundEvents.PLAYER_ATTACK_STRONG, 1.0F,
                        s.level().getRandom().nextFloat() * 0.1F + 0.9F);

                // Halberd-specific sweep AoE removed in v3.1.0 (custom halberd retired;
                // squire now wields any vanilla/modded weapon).

                var log = s.getActivityLog();
                if (!target.isAlive()) {
                    s.playSound(SoundEvents.PLAYER_ATTACK_CRIT, 1.0F, 1.0F);
                    if (log != null) {
                        log.log("COMBAT", "Killed " + target.getType().toShortString()
                                + " at " + target.blockPosition().toShortString());
                    }
                } else if (log != null) {
                    log.log("COMBAT", "Hit " + target.getType().toShortString()
                            + " (HP: " + String.format("%.1f", target.getHealth()) + ")");
                }
            }
            return true;
        }
        return false;
    }

    // ================================================================
    // Movement helpers
    // ================================================================

    /** Move perpendicular to the target (circle-strafe). */
    private void movePerpendicularToTarget(SquireEntity s, LivingEntity target) {
        double dx = target.getX() - s.getX();
        double dz = target.getZ() - s.getZ();
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len > 0.01) {
            double perpX = -dz / len * strafeDirection;
            double perpZ = dx / len * strafeDirection;
            double strafeX = s.getX() + perpX * 2.0;
            double strafeZ = s.getZ() + perpZ * 2.0;
            s.getNavigation().moveTo(strafeX, s.getY(), strafeZ, 1.0D);
        }
    }

    /** Move away from target by retreatDist blocks. */
    private void retreatFromTarget(SquireEntity s, LivingEntity target, double retreatDist) {
        double dx = s.getX() - target.getX();
        double dz = s.getZ() - target.getZ();
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len > 0.01) {
            double rx = s.getX() + (dx / len) * retreatDist;
            double rz = s.getZ() + (dz / len) * retreatDist;
            s.getNavigation().moveTo(rx, s.getY(), rz, 1.3D);
        }
    }

    // ================================================================
    // Ranged combat
    // ================================================================

    /**
     * Per-tick ranged combat: maintain optimal distance, shoot on cooldown.
     * Returns COMBAT_APPROACH if target closes to melee range, IDLE if combat ends.
     * Called from SquireBrain when in COMBAT_RANGED state.
     */
    public SquireAIState tickRanged(SquireEntity s) {
        LivingEntity target = s.getTarget();
        if (target == null || !target.isAlive()) {
            s.getNavigation().stop();
            return SquireAIState.IDLE;
        }
        if (s.isOrderedToSit()) {
            s.getNavigation().stop();
            return SquireAIState.IDLE;
        }

        // No bow in mainhand -- fall back to melee
        if (!(s.getItemBySlot(EquipmentSlot.MAINHAND).getItem() instanceof BowItem)
                || s.getProjectile(s.getItemBySlot(EquipmentSlot.MAINHAND)).isEmpty()) {
            return SquireAIState.COMBAT_APPROACH;
        }

        // Owner leash (guard mode bypass applies here too)
        if (isLeashBreached(s) && s.getSquireMode() != SquireEntity.MODE_GUARD) {
            disengageCombat(s);
            return SquireAIState.IDLE;
        }

        double distSq = s.distanceToSqr(target);
        double optimalRange = SquireConfig.rangedRange.get();

        // EXPLOSIVE tactic: maintain extra distance (>8 blocks minimum)
        double minRangedDist = RANGED_MIN_DIST;
        if (currentTactic == CombatTactic.EXPLOSIVE) {
            minRangedDist = 8.0;
        }

        // Too close -- back off (or retreat if explosive)
        if (distSq < minRangedDist * minRangedDist) {
            if (currentTactic == CombatTactic.EXPLOSIVE) {
                retreatFromTarget(s, target, 8.0);
            } else {
                return SquireAIState.COMBAT_APPROACH;
            }
        }

        // Too far -- approach
        double maxRange = optimalRange + 5.0;
        if (distSq > maxRange * maxRange) {
            s.getNavigation().moveTo(target, 1.0D);
        } else if (distSq < (optimalRange - 3.0) * (optimalRange - 3.0)) {
            retreatFromTarget(s, target, 5.0);
        } else {
            s.getNavigation().stop();
        }

        s.getLookControl().setLookAt(target, 30.0F, 30.0F);

        // Shoot on cooldown if line of sight
        ticksUntilNextAttack = Math.max(ticksUntilNextAttack - 1, 0);
        if (ticksUntilNextAttack <= 0 && s.getSensing().hasLineOfSight(target)) {
            float distanceFactor = (float) Math.sqrt(distSq) / 20.0F;
            performRangedAttack(s, target, Math.min(distanceFactor, 1.0F));
            ticksUntilNextAttack = SquireConfig.combatTickRate.get() * 5; // ~20 tick cooldown at default

            var log = s.getActivityLog();
            if (log != null) {
                log.log("COMBAT", "Shot arrow at " + target.getType().toShortString()
                        + " (range: " + String.format("%.1f", Math.sqrt(distSq)) + ")");
            }
        }

        return SquireAIState.COMBAT_RANGED;
    }

    /**
     * Performs a ranged attack using the bow in the mainhand.
     * Charges and releases via startUsingItem/stopUsingItem.
     */
    private void performRangedAttack(SquireEntity s, LivingEntity target, float power) {
        // Start drawing if not already
        if (!s.isUsingItem() || s.getUsedItemHand() != InteractionHand.MAIN_HAND) {
            s.startUsingItem(InteractionHand.MAIN_HAND);
            rangedChargeTicks = 0;
        }
        rangedChargeTicks++;
        // Release after 20 ticks (full charge)
        if (rangedChargeTicks >= 20) {
            s.stopUsingItem();
            rangedChargeTicks = 0;
        }
    }

    // ================================================================
    // Ranged decision
    // ================================================================

    /**
     * Determine if we should use ranged combat mode.
     * Requires bow in mainhand with arrows, and target beyond RANGED_MIN_DIST.
     */
    public boolean shouldUseRanged() {
        if (!(squire.getItemBySlot(EquipmentSlot.MAINHAND).getItem() instanceof BowItem)) {
            return false;
        }
        if (squire.getProjectile(squire.getItemBySlot(EquipmentSlot.MAINHAND)).isEmpty()) {
            return false;
        }
        LivingEntity target = squire.getTarget();
        if (target != null) {
            // EVASIVE and EXPLOSIVE tactics prefer ranged when available
            if (currentTactic == CombatTactic.EVASIVE || currentTactic == CombatTactic.EXPLOSIVE) {
                return true;
            }
            double distSq = squire.distanceToSqr(target);
            if (distSq < RANGED_MIN_DIST * RANGED_MIN_DIST) {
                return false;
            }
        }
        return true;
    }

    /**
     * Public melee attack entry point for mounted combat.
     * MountHandler.tickMountedCombat() calls this when the horse is within reach of the target.
     * Delegates to tryMeleeAttack() which handles cooldown, halberd sweep, and logging.
     */
    public void performMeleeAttack(SquireEntity s, LivingEntity target) {
        tryMeleeAttack(s, target);
    }

    /** Whether the squire has a valid combat target. */
    public boolean hasTarget() {
        LivingEntity target = squire.getTarget();
        return target != null && target.isAlive();
    }

    // ================================================================
    // Internal helpers
    // ================================================================

    private void recalculateAttackCooldown() {
        double speed = squire.getAttributeValue(Attributes.ATTACK_SPEED);
        speed = Math.max(0.5D, Math.min(4.0D, speed));
        attackCooldown = (int) Math.ceil(20.0D / speed);
        ticksUntilNextAttack = attackCooldown;
    }

    private boolean isLeashBreached(SquireEntity s) {
        Player owner = s.getOwner() instanceof Player p ? p : null;
        if (owner == null) return false;
        double leash = SquireConfig.combatLeashDistance.get();
        return s.distanceToSqr(owner) > leash * leash;
    }

    private void disengageCombat(SquireEntity s) {
        s.setTarget(null);
        s.getNavigation().stop();
        shieldUpTicks = 0;
    }

    private double getAttackReachSq(LivingEntity target) {
        double baseReach = 1.5;
        double width = squire.getBbWidth();
        double vanillaReach = Math.sqrt(width * 2.0D * width * 2.0D + target.getBbWidth());
        double reach = Math.max(baseReach, vanillaReach);
        return reach * reach;
    }
}

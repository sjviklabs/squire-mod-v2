package com.sjviklabs.squire.brain.handler;

import com.sjviklabs.squire.brain.SquireAIState;
import com.sjviklabs.squire.config.SquireConfig;
import com.sjviklabs.squire.entity.SquireEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/**
 * Handles horse finding, mounting, dismounting, and mounted navigation.
 *
 * NeoForge 1.21.1 horse movement with NPC riders:
 * - AbstractHorse.getControllingPassenger() only returns Player instances.
 * - LivingEntity.aiStep() gates travelRidden() on getControllingPassenger() instanceof Player (line ~2816).
 * - When squire (PathfinderMob) is passenger, vanilla travel() receives zero input — horse sits still.
 * - horse.getNavigation().moveTo() also has no effect when the horse has a passenger.
 * - Solution: call horse.move(MoverType.SELF, vec) directly each tick to physically push
 *   the horse with collision detection. This bypasses travel() and the goal system entirely.
 *   Confirmed working in v0.5.0.
 *
 * Requirements: MNT-01, MNT-02, MNT-04
 */
public class MountHandler {

    @Nullable
    private UUID horseUUID;
    private boolean mounted = false;

    // No-arg constructor — supports headless unit tests and standalone instantiation.
    // SquireEntity is passed per-tick via method parameters.
    public MountHandler() {}

    // ---- Public API ----

    public @Nullable UUID getHorseUUID() { return horseUUID; }
    public void setHorseUUID(@Nullable UUID uuid) { this.horseUUID = uuid; }

    public boolean isMounted() { return mounted; }

    /** Secondary guard: also check whether squire is actually on a horse. */
    public boolean isMounted(SquireEntity s) {
        return mounted && s.getVehicle() instanceof AbstractHorse;
    }

    public void orderDismount(SquireEntity s) {
        s.stopRiding();
        mounted = false;
    }

    // ---- Per-tick FSM methods ----

    /**
     * MNT-01: Approach the assigned horse and mount it when within range.
     * Uses inflate(horseSearchRange) AABB search — Pitfall 6.
     */
    public SquireAIState tickApproach(SquireEntity s) {
        AbstractHorse horse = findAssignedHorse(s);
        if (horse == null) {
            s.getNavigation().stop();
            return SquireAIState.IDLE;
        }

        double distSq = s.distanceToSqr(horse);
        if (distSq <= 4.0) {
            s.startRiding(horse, true);
            s.getNavigation().stop();
            mounted = true;
            return SquireAIState.MOUNTED_IDLE;
        }

        s.getNavigation().moveTo(horse, 1.2);
        return SquireAIState.MOUNTING;
    }

    /**
     * MNT-02: Follow player while mounted, driving the horse directly via
     * horse.move(MoverType.SELF, vec) — the only viable NPC horse driving approach.
     */
    public SquireAIState tickMountedFollow(SquireEntity s) {
        if (!(s.getVehicle() instanceof AbstractHorse horse)) {
            mounted = false;
            return SquireAIState.IDLE;
        }

        Player owner = s.getOwner() instanceof Player p ? p : null;
        if (owner == null) return SquireAIState.MOUNTED_IDLE;

        double distSq = s.distanceToSqr(owner);
        double stopDist = SquireConfig.followStopDistance.get();
        double startDist = SquireConfig.followStartDistance.get();

        if (distSq < stopDist * stopDist) {
            return SquireAIState.MOUNTED_IDLE;
        }

        // Sprint if far, trot if close
        double speedMult = distSq > startDist * startDist * 4 ? SquireConfig.mountedFollowSpeed.get() : SquireConfig.mountedFollowSpeed.get() * 0.5;
        driveHorseToward(s, horse, owner.getX(), owner.getY(), owner.getZ(), (float) speedMult);

        return SquireAIState.MOUNTED_FOLLOW;
    }

    /**
     * Mounted idle — squire stays on horse, no movement.
     */
    public SquireAIState tickMountedIdle(SquireEntity s) {
        if (!(s.getVehicle() instanceof AbstractHorse)) {
            mounted = false;
            return SquireAIState.IDLE;
        }
        return SquireAIState.MOUNTED_IDLE;
    }

    // ---- Horse driving ----

    /**
     * Drive the horse toward a target position using horse.move(MoverType.SELF, vec).
     * This directly changes the horse's position with collision detection, bypassing
     * the travel() system and goal-based movement entirely.
     *
     * Ported verbatim from v0.5.0 MountHandler.driveHorseToward() — proven working.
     * Includes: heading wrap-around, step-up, gravity, animation sync.
     */
    private void driveHorseToward(SquireEntity s, AbstractHorse horse,
                                   double targetX, double targetY, double targetZ,
                                   float speedMult) {
        double dx = targetX - horse.getX();
        double dz = targetZ - horse.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);

        if (dist < 1.5) return;

        // Rotate horse to face target — smooth turn
        float targetYRot = (float) (Math.atan2(-dx, dz) * (180.0 / Math.PI));
        float currentYRot = horse.getYRot();
        float diff = targetYRot - currentYRot;
        while (diff > 180.0F) diff -= 360.0F;
        while (diff < -180.0F) diff += 360.0F;
        float maxTurn = 12.0F;
        float smoothed = currentYRot + Math.max(-maxTurn, Math.min(maxTurn, diff));

        horse.setYRot(smoothed);
        horse.yBodyRot = smoothed;
        horse.yHeadRot = smoothed;
        s.setYRot(smoothed);
        s.yBodyRot = smoothed;

        // Move speed: horse attribute * multiplier. Typical horse speed is 0.1125-0.3375.
        // At multiplier 3.5 with speedMult 1.0: 0.39-1.18 blocks/tick = good gallop.
        double horseSpeed = horse.getAttributeValue(Attributes.MOVEMENT_SPEED);
        double moveSpeed = horseSpeed * speedMult * 3.5;

        // Direction unit vector
        double nx = dx / dist;
        double nz = dz / dist;

        // Directly move the horse with collision handling
        Vec3 movement = new Vec3(nx * moveSpeed, 0, nz * moveSpeed);
        horse.move(MoverType.SELF, movement);

        // Step-up: if horse hit a wall and is on ground, hop up one block
        if (horse.horizontalCollision && horse.onGround()) {
            horse.move(MoverType.SELF, new Vec3(0, 0.6, 0));
            // Try forward again after stepping up
            horse.move(MoverType.SELF, movement);
        }

        // Apply gravity if not on ground — vanilla travel() is bypassed so we must do this
        if (!horse.onGround()) {
            horse.move(MoverType.SELF, new Vec3(0, -0.08, 0));
        }

        // Sync to clients
        horse.hasImpulse = true;

        // Animate legs
        horse.walkAnimation.setSpeed(Math.min((float) moveSpeed * 8.0F, 1.5F));
    }

    // ---- Private helpers ----

    /**
     * Find the assigned horse by UUID within an inflate(horseSearchRange) AABB.
     * Uses inflate() not raw bounding box — Pitfall 6 from RESEARCH.md.
     */
    @Nullable
    private AbstractHorse findAssignedHorse(SquireEntity s) {
        if (horseUUID == null) return null;
        double range = SquireConfig.horseSearchRange.get();
        AABB searchBox = s.getBoundingBox().inflate(range);
        List<AbstractHorse> horses = s.level().getEntitiesOfClass(
                AbstractHorse.class, searchBox,
                h -> h.getUUID().equals(horseUUID) && h.isSaddled() && h.isAlive());
        return horses.isEmpty() ? null : horses.get(0);
    }
}

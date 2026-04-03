package com.sjviklabs.squire.brain.handler;

import com.sjviklabs.squire.brain.SquireAIState;
import com.sjviklabs.squire.config.SquireConfig;
import com.sjviklabs.squire.entity.SquireEntity;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Handles following the owner via pathfinding — no teleportation ever.
 *
 * NAV-01: squire paths toward player when beyond followStartDistance.
 * NAV-02: sprint scaling — sprints when distSq > sprintDistance^2 or owner is sprinting.
 * NAV-03: door navigation enabled on follow start, disabled on stop.
 * NAV-05: stuck recovery via jump boost + path replan (no teleport fallback).
 *
 * One instance per squire, owns path recalc timer and stuck tracking state.
 */
public class FollowHandler {

    private final SquireEntity squire;

    private int pathRecalcTimer = 0;
    private Vec3 lastPos = null;
    private int stuckTicks = 0;

    /** Ticks of minimal movement before stuck recovery fires (3 s at 20 TPS). */
    private static final int STUCK_THRESHOLD = 60;

    public FollowHandler(SquireEntity squire) {
        this.squire = squire;
    }

    // ── Condition methods (called by FSM transitions) ────────────────────────

    /** True when squire should start following — owner is beyond followStartDistance. */
    public boolean shouldFollow() {
        if (!squire.shouldFollowOwner()) return false;
        Player owner = squire.getOwner();
        if (owner == null || !owner.isAlive() || owner.isSpectator()) return false;
        double dist = SquireConfig.followStartDistance.get();
        return squire.distanceToSqr(owner) >= dist * dist;
    }

    /** True when squire should stop following — owner is within followStopDistance or invalid. */
    public boolean shouldStop() {
        if (!squire.shouldFollowOwner()) return true;
        Player owner = squire.getOwner();
        if (owner == null || !owner.isAlive() || owner.isSpectator()) return true;
        double dist = SquireConfig.followStopDistance.get();
        return squire.distanceToSqr(owner) <= dist * dist;
    }

    // ── Lifecycle methods ────────────────────────────────────────────────────

    /** Called when entering FOLLOWING_OWNER state. Resets timers, enables door nav. */
    public void start() {
        pathRecalcTimer = 0;
        lastPos = null;
        stuckTicks = 0;
        if (squire.getNavigation() instanceof GroundPathNavigation nav) {
            nav.setCanOpenDoors(true);
            nav.setCanPassDoors(true);
        }
    }

    /** Called when exiting FOLLOWING_OWNER state. Stops movement, disables door nav. */
    public void stop() {
        squire.setSquireSprinting(false);
        squire.getNavigation().stop();
        if (squire.getNavigation() instanceof GroundPathNavigation nav) {
            nav.setCanOpenDoors(false);
            nav.setCanPassDoors(false);
        }
    }

    // ── Per-tick logic ───────────────────────────────────────────────────────

    /**
     * Per-tick follow logic. Called every tick while in FOLLOWING_OWNER state.
     * Path recalculation is throttled by pathRecalcInterval; stuck detection runs every tick.
     */
    public SquireAIState tick(SquireEntity s) {
        Player owner = s.getOwner();
        if (owner == null) return SquireAIState.IDLE;

        // Smooth head tracking every tick
        s.getLookControl().setLookAt(owner, 10.0F, (float) s.getMaxHeadXRot());

        // Stuck detection — runs every tick regardless of pathRecalcTimer
        Vec3 currentPos = s.position();
        if (lastPos != null && currentPos.distanceTo(lastPos) < 0.1) {
            stuckTicks++;
            if (stuckTicks >= STUCK_THRESHOLD) {
                handleStuck(s, owner);
                stuckTicks = 0;
            }
        } else {
            stuckTicks = 0;
        }
        lastPos = currentPos;

        // Throttle path recalculation to pathRecalcInterval ticks
        if (--pathRecalcTimer > 0) return SquireAIState.FOLLOWING_OWNER;
        pathRecalcTimer = SquireConfig.followTickRate.get();

        double distSq = s.distanceToSqr(owner);

        // Jump boost for elevation — helps with hills, cliffs (not stuck recovery)
        double heightDiff = owner.getY() - s.getY();
        if (heightDiff > 1.5 && distSq < 20.0 * 20.0) {
            if (!s.hasEffect(MobEffects.JUMP)) {
                s.addEffect(new MobEffectInstance(MobEffects.JUMP, 40, 1, false, false));
            }
        }

        // Sprint scaling — sprint when far or when owner is sprinting
        double sprintThresh = SquireConfig.sprintDistance.get();
        boolean shouldSprint = distSq > sprintThresh * sprintThresh || owner.isSprinting();
        s.setSquireSprinting(shouldSprint);
        double speed = shouldSprint ? 1.3D : 1.0D;

        // Pathfinding navigation — the ONLY locomotion mechanism, no teleportation
        s.getNavigation().moveTo(owner, speed);

        return SquireAIState.FOLLOWING_OWNER;
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    /**
     * Stuck recovery: apply jump boost and force a path replan.
     * Called when squire hasn't moved 0.1 blocks for STUCK_THRESHOLD ticks.
     * No teleportation — squire must path its way out.
     */
    private void handleStuck(SquireEntity s, Player owner) {
        // Jump boost helps with stairs, 1-block lips, and step-up transitions
        s.addEffect(new MobEffectInstance(MobEffects.JUMP, 40, 2, false, false));
        // Force immediate path replan regardless of throttle timer
        s.getNavigation().stop();
        s.getNavigation().moveTo(owner, 1.3D);
        // Activate swimming if stuck in water
        if (s.isInWater()) s.setSwimming(true);
        // Debug log
        var log = s.getActivityLog();
        if (log != null) {
            log.log("FOLLOW", "Stuck detected — applying jump boost and replanning path");
        }
    }
}

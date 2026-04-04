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
    private int recoveryCooldown = 0;

    // Breadcrumb ring buffer — records owner positions as intermediate waypoints
    private static final int BREADCRUMB_SIZE = 10;
    private static final int BREADCRUMB_INTERVAL = 20; // record every 20 ticks (1 second)
    private final Vec3[] breadcrumbs = new Vec3[BREADCRUMB_SIZE];
    private int breadcrumbHead = 0;
    private int breadcrumbTimer = 0;

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
        recoveryCooldown = 0;
        breadcrumbHead = 0;
        breadcrumbTimer = 0;
        java.util.Arrays.fill(breadcrumbs, null);
        squire.ensureChunkLoaded();
        if (squire.getNavigation() instanceof GroundPathNavigation nav) {
            nav.setCanOpenDoors(true);
            nav.setCanPassDoors(true);
        }
    }

    /** Called when exiting FOLLOWING_OWNER state. Stops movement, disables door nav. */
    public void stop() {
        squire.setSquireSprinting(false);
        squire.getNavigation().stop();
        squire.releaseChunkLoading();
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

        // Record owner breadcrumbs every BREADCRUMB_INTERVAL ticks
        if (++breadcrumbTimer >= BREADCRUMB_INTERVAL) {
            breadcrumbTimer = 0;
            breadcrumbs[breadcrumbHead] = owner.position();
            breadcrumbHead = (breadcrumbHead + 1) % breadcrumbs.length;
        }

        // Refresh chunk loading as squire moves (~every 5 seconds)
        if (s.tickCount % 100 == 0) {
            s.releaseChunkLoading();
            s.ensureChunkLoaded();
        }

        // Stuck detection — runs every tick, uses config threshold + recovery cooldown
        Vec3 currentPos = s.position();
        if (recoveryCooldown > 0) {
            recoveryCooldown--;
        } else if (lastPos != null && currentPos.distanceTo(lastPos) < 0.1) {
            stuckTicks++;
            if (stuckTicks >= SquireConfig.stuckDetectionTicks.get()) {
                handleStuck(s, owner);
                stuckTicks = 0;
                recoveryCooldown = SquireConfig.stuckRecoveryTicks.get();
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

        // If direct path failed, try breadcrumb waypoints (most recent first)
        if (s.getNavigation().isDone()) {
            for (int i = 0; i < breadcrumbs.length; i++) {
                int idx = (breadcrumbHead - 1 - i + breadcrumbs.length) % breadcrumbs.length;
                Vec3 crumb = breadcrumbs[idx];
                if (crumb == null) continue;
                if (s.getNavigation().moveTo(crumb.x, crumb.y, crumb.z, 1.3D)) {
                    var log = s.getActivityLog();
                    if (log != null) log.log("FOLLOW", "Using breadcrumb waypoint " + i + " steps back");
                    break;
                }
            }
        }

        // Activate swimming if stuck in water
        if (s.isInWater()) s.setSwimming(true);
        var log = s.getActivityLog();
        if (log != null) {
            log.log("FOLLOW", "Stuck detected — applying jump boost and replanning path");
        }
    }
}

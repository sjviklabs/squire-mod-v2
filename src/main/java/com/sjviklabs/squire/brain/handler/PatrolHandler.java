package com.sjviklabs.squire.brain.handler;

import com.sjviklabs.squire.brain.SquireAIState;
import com.sjviklabs.squire.config.SquireConfig;
import com.sjviklabs.squire.entity.SquireEntity;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles patrol behavior — walking between waypoints in a defined loop,
 * pausing at each waypoint, and resuming after combat.
 *
 * Implements PTR-02: named route walking, wait-at-waypoint, post-combat resume,
 * stuck recovery.
 *
 * Design notes:
 * - Patrol is foot-only. startPatrol() should be preceded by dismount orders.
 * - currentIndex is saved on COMBAT_START (savedIndex) and restored on COMBAT_END
 *   so patrol resumes from where it was interrupted, not from the top.
 *
 * v3.1.0 — Route source changed from signpost-chain (removed block) to
 * crest-area perimeter. The squire walks the four corners of the area the
 * player marked with their Squire's Crest. Same tick/wait/resume logic,
 * different route builder.
 */
public class PatrolHandler {

    // ── State ─────────────────────────────────────────────────────────────────

    private List<BlockPos> route = new ArrayList<>();
    private int currentIndex = 0;
    private int savedIndex = 0;   // saved on COMBAT_START, restored on COMBAT_END
    private int waitTimer = 0;
    private boolean patrolling = false;

    // ── Public API ────────────────────────────────────────────────────────────

    public boolean isPatrolling() { return patrolling; }

    /**
     * Begin patrolling the given route.
     * Guard: if route is empty, returns immediately.
     * Foot-only: caller is responsible for dismount orders.
     */
    public void startPatrol(List<BlockPos> newRoute) {
        if (newRoute == null || newRoute.isEmpty()) return;
        this.route = new ArrayList<>(newRoute);
        this.currentIndex = 0;
        this.savedIndex = 0;
        this.waitTimer = 0;
        this.patrolling = true;
    }

    /** Stop patrolling and reset all state. */
    public void stopPatrol() {
        patrolling = false;
        route = new ArrayList<>();
        currentIndex = 0;
        savedIndex = 0;
        waitTimer = 0;
    }

    // ── FSM tick methods ──────────────────────────────────────────────────────

    /**
     * Called each tick while in PATROL_WALK state.
     *
     * Returns:
     *   PATROL_WAIT  — squire arrived within 2 blocks of waypoint
     *   PATROL_WALK  — still walking (or stuck recovery applied)
     *   IDLE         — patrol ended or route empty
     */
    public SquireAIState tickWalk(SquireEntity s) {
        if (!patrolling || route == null || route.isEmpty()) {
            patrolling = false;
            return SquireAIState.IDLE;
        }

        BlockPos target = route.get(currentIndex);
        double distSq = s.distanceToSqr(
                target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5);

        // Arrived within 2 blocks — transition to wait state
        if (distSq <= 4.0) {
            waitTimer = SquireConfig.patrolDefaultWait.get();
            return SquireAIState.PATROL_WAIT;
        }

        // Stuck recovery: replan and apply a jump boost (same pattern as FollowHandler)
        if (s.getNavigation().isStuck()) {
            s.getNavigation().stop();
            s.getNavigation().moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, 0.8);
            s.setDeltaMovement(s.getDeltaMovement().add(0, 0.42, 0));
            return SquireAIState.PATROL_WALK;
        }

        // Normal walk: issue navigation command each tick
        s.getNavigation().moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, 0.8);
        return SquireAIState.PATROL_WALK;
    }

    /**
     * Called each tick while in PATROL_WAIT state.
     *
     * Returns:
     *   PATROL_WALK — wait expired, advance to next waypoint
     *   PATROL_WAIT — still waiting
     *   IDLE        — patrol ended or route empty
     */
    public SquireAIState tickWait(SquireEntity s) {
        if (!patrolling || route == null || route.isEmpty()) {
            patrolling = false;
            return SquireAIState.IDLE;
        }

        waitTimer--;
        if (waitTimer <= 0) {
            // Advance to next waypoint (loop)
            currentIndex = (currentIndex + 1) % route.size();
            return SquireAIState.PATROL_WALK;
        }

        // Look around idly while waiting (10% chance per tick)
        if (s.getRandom().nextFloat() < 0.1F) {
            double rx = s.getX() + (s.getRandom().nextDouble() - 0.5) * 16.0;
            double ry = s.getEyeY() + (s.getRandom().nextDouble() - 0.5) * 4.0;
            double rz = s.getZ() + (s.getRandom().nextDouble() - 0.5) * 16.0;
            s.getLookControl().setLookAt(rx, ry, rz);
        }

        return SquireAIState.PATROL_WAIT;
    }

    // ── Combat event handlers ─────────────────────────────────────────────────

    /**
     * Called when COMBAT_START fires.
     * Saves the current waypoint index so patrol can resume at the same position
     * after combat ends, rather than restarting from the beginning.
     */
    public void onCombatStart() {
        savedIndex = currentIndex;
    }

    /**
     * Called when COMBAT_END fires.
     * Restores currentIndex to the saved value so patrol resumes from the same
     * waypoint it was at when combat began.
     */
    public void onCombatEnd() {
        currentIndex = savedIndex;
    }

    // ── Route building ────────────────────────────────────────────────────────

    /**
     * Build a patrol route from a crest-area selection — the squire will walk
     * the four corners of the rectangular zone defined by the two corner blocks.
     *
     * Clockwise order from the SW corner:
     *   (minX, minZ) → (maxX, minZ) → (maxX, maxZ) → (minX, maxZ) → loop.
     *
     * Y coordinate is taken from corner1. If the two corners are at different
     * Y levels the perimeter is flat at corner1's Y; the squire's pathfinding
     * will handle small vertical offsets naturally.
     *
     * @param corner1 one corner of the area (from Crest selection)
     * @param corner2 the opposite corner
     * @return 4 waypoints, or empty list if corners are null
     */
    public static List<BlockPos> buildRouteFromCrestArea(BlockPos corner1, BlockPos corner2) {
        List<BlockPos> waypoints = new ArrayList<>();
        if (corner1 == null || corner2 == null) return waypoints;

        int minX = Math.min(corner1.getX(), corner2.getX());
        int maxX = Math.max(corner1.getX(), corner2.getX());
        int minZ = Math.min(corner1.getZ(), corner2.getZ());
        int maxZ = Math.max(corner1.getZ(), corner2.getZ());
        int y = corner1.getY();

        waypoints.add(new BlockPos(minX, y, minZ));  // SW
        waypoints.add(new BlockPos(maxX, y, minZ));  // SE
        waypoints.add(new BlockPos(maxX, y, maxZ));  // NE
        waypoints.add(new BlockPos(minX, y, maxZ));  // NW

        return waypoints;
    }
}

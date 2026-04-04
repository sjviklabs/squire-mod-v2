package com.sjviklabs.squire.brain.handler;

import com.sjviklabs.squire.block.SignpostBlockEntity;
import com.sjviklabs.squire.brain.SquireAIState;
import com.sjviklabs.squire.config.SquireConfig;
import com.sjviklabs.squire.entity.SquireEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * Handles patrol behavior — walking between signpost waypoints in a defined loop,
 * pausing at each waypoint, and resuming after combat.
 *
 * Implements PTR-02: named route walking, wait-at-waypoint, post-combat resume,
 * stuck recovery.
 *
 * Design notes:
 * - Patrol is foot-only. startPatrol() calls MountHandler.orderDismount() if mounted.
 * - currentIndex is saved on COMBAT_START (savedIndex) and restored on COMBAT_END
 *   so patrol resumes from where it was interrupted, not from the top.
 * - buildRouteFromSignpost() has a package-private overload that accepts a
 *   Function<BlockPos, BlockEntity> resolver for headless unit testing.
 *
 * Phase 7 plan 07-02.
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
     * Foot-only: if squire is mounted, dismount is ordered via MountHandler (Phase 7-03).
     * Since MountHandler may not exist yet in this wave, the dismount call is guarded
     * by a null check and left as a TODO comment for MountHandler wiring.
     */
    public void startPatrol(List<BlockPos> newRoute) {
        if (newRoute == null || newRoute.isEmpty()) return;
        this.route = new ArrayList<>(newRoute);
        this.currentIndex = 0;
        this.savedIndex = 0;
        this.waitTimer = 0;
        this.patrolling = true;

        // Patrol is foot-only — Phase 7-03 MountHandler wires the dismount call here.
        // When MountHandler is present: if (mountHandler.isMounted()) mountHandler.orderDismount();
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
     * Build a patrol route by following the linked signpost chain starting at
     * the given position in the world.
     *
     * Uses loop detection via a visited HashSet so circular routes terminate
     * cleanly (A→B→C→A produces [A, B, C] and stops when A is revisited).
     *
     * Delegates to the package-private two-arg overload for testability.
     */
    public static List<BlockPos> buildRouteFromSignpost(Level level, BlockPos start) {
        return buildRouteFromSignpost(
                pos -> level.getBlockEntity(pos) instanceof SignpostBlockEntity,
                pos -> {
                    BlockEntity be = level.getBlockEntity(pos);
                    return be instanceof SignpostBlockEntity sp ? sp.getLinkedSignpost() : null;
                },
                start
        );
    }

    /**
     * Package-private overload for unit testing.
     *
     * Two-predicate design cleanly separates "is there a signpost here?" from
     * "what does it link to?" — avoiding the ambiguity of null meaning both
     * "no signpost" and "end of chain" in a single-function design.
     *
     * Tests supply both from a simple Map&lt;BlockPos, BlockPos&gt; without
     * instantiating any BlockEntity — consistent with the writeTag/readTag
     * headless-test pattern established in 07-01.
     *
     * Visited HashSet provides O(1) loop detection; max route length is capped
     * at SquireConfig.patrolMaxRouteLength.
     *
     * @param isSignpost  returns true if pos has a valid signpost block entity
     * @param getNext     returns the linked BlockPos, or null if end of chain
     * @param start       starting position
     */
    static List<BlockPos> buildRouteFromSignpost(
            java.util.function.Predicate<BlockPos> isSignpost,
            Function<BlockPos, BlockPos> getNext,
            BlockPos start) {

        List<BlockPos> waypoints = new ArrayList<>();
        Set<BlockPos> visited = new HashSet<>();
        BlockPos current = start;
        int maxRoute = SquireConfig.patrolMaxRouteLength.get();

        while (current != null && waypoints.size() < maxRoute) {
            if (visited.contains(current)) {
                // Loop detected — route is complete
                break;
            }
            if (!isSignpost.test(current)) {
                // No signpost at this position — broken chain or non-signpost start
                break;
            }
            visited.add(current);
            waypoints.add(current);
            current = getNext.apply(current); // null = end of linear chain
        }

        return waypoints;
    }
}

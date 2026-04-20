package com.sjviklabs.squire.ai.job;

import com.minecolonies.api.entity.ai.statemachine.AITarget;
import com.minecolonies.api.entity.ai.statemachine.tickratestatemachine.TickRateStateMachine;
import com.sjviklabs.squire.ai.state.SquireAIState;
import com.sjviklabs.squire.entity.SquireEntity;
import net.minecraft.core.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Patrol job AI — walk the perimeter of a crest-selected area.
 *
 * Waypoints: the four corners of the selected rectangle projected to the squire's
 * current Y layer, inset one block so the squire walks along the inside edge instead
 * of standing exactly on corner blocks. At each waypoint, pause briefly (a second or
 * two) before moving to the next. Loops forever until {@code /squire patrol stop}.
 *
 * Combat preempts patrol via {@link com.sjviklabs.squire.ai.SquireAIController} — when
 * GuardAI takes over, PatrolAI's waypoint index freezes in place. When combat resolves,
 * the controller picks PatrolAI again and we resume from whichever waypoint we were
 * heading toward. No explicit "save index across combat" hack needed; the state simply
 * persists on the AI instance.
 */
public final class PatrolAI implements JobAI {

    private static final Logger LOGGER = LoggerFactory.getLogger(PatrolAI.class);
    private static final double WALK_SPEED = 0.9;
    private static final double ARRIVAL_DSQ = 4.0;          // 2 blocks
    private static final int WAYPOINT_DWELL_TICKS = 40;     // 2s pause per corner

    private final SquireEntity squire;
    private final TickRateStateMachine<SquireAIState> machine;

    private final List<BlockPos> waypoints = new ArrayList<>();
    private int currentIndex = 0;
    private int dwellTimer = 0;

    public PatrolAI(SquireEntity squire) {
        this.squire = squire;
        this.machine = new TickRateStateMachine<>(
                SquireAIState.IDLE,
                ex -> LOGGER.error("[PatrolAI] state machine error", ex)
        );

        machine.addTransition(new AITarget<>(
                SquireAIState.IDLE,
                () -> !waypoints.isEmpty(),
                () -> SquireAIState.PATROLLING,
                10
        ));

        machine.addTransition(new AITarget<>(
                SquireAIState.PATROLLING,
                waypoints::isEmpty,
                () -> {
                    squire.getNavigation().stop();
                    return SquireAIState.IDLE;
                },
                10
        ));

        machine.addTransition(new AITarget<>(
                SquireAIState.PATROLLING,
                () -> true,
                this::tickPatrol,
                1
        ));
    }

    /**
     * Set the patrol bounds. Waypoints are the four corners of the rectangle projected
     * to the squire's current Y, inset by 1. Resets progress to the first corner.
     */
    public void setArea(BlockPos a, BlockPos b) {
        waypoints.clear();
        int minX = Math.min(a.getX(), b.getX()) + 1;
        int maxX = Math.max(a.getX(), b.getX()) - 1;
        int minZ = Math.min(a.getZ(), b.getZ()) + 1;
        int maxZ = Math.max(a.getZ(), b.getZ()) - 1;
        if (minX > maxX) { minX = Math.min(a.getX(), b.getX()); maxX = minX; }
        if (minZ > maxZ) { minZ = Math.min(a.getZ(), b.getZ()); maxZ = minZ; }

        int y = squire.blockPosition().getY();
        // Four corners of the inset rectangle, clockwise from NW.
        waypoints.add(new BlockPos(minX, y, minZ));
        waypoints.add(new BlockPos(maxX, y, minZ));
        waypoints.add(new BlockPos(maxX, y, maxZ));
        waypoints.add(new BlockPos(minX, y, maxZ));
        currentIndex = 0;
        dwellTimer = 0;
    }

    public void stop() {
        waypoints.clear();
        currentIndex = 0;
        dwellTimer = 0;
        // v4.0.1 — cancel live path (see FarmerAI.stop for rationale).
        squire.getNavigation().stop();
    }

    public boolean hasWork() {
        return !waypoints.isEmpty();
    }

    private SquireAIState tickPatrol() {
        if (waypoints.isEmpty()) return SquireAIState.PATROLLING;
        BlockPos target = waypoints.get(currentIndex);

        squire.getLookControl().setLookAt(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5);
        double distSq = squire.distanceToSqr(target.getX() + 0.5, target.getY(), target.getZ() + 0.5);

        if (distSq > ARRIVAL_DSQ) {
            // Still walking — refresh path periodically; vanilla handles completion.
            if (squire.getNavigation().isDone() || squire.tickCount % 20 == 0) {
                squire.getNavigation().moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, WALK_SPEED);
            }
            return SquireAIState.PATROLLING;
        }

        // Arrived — dwell briefly, then advance.
        squire.getNavigation().stop();
        if (dwellTimer < WAYPOINT_DWELL_TICKS) {
            dwellTimer++;
            return SquireAIState.PATROLLING;
        }
        dwellTimer = 0;
        currentIndex = (currentIndex + 1) % waypoints.size();
        return SquireAIState.PATROLLING;
    }

    @Override
    public void tick() {
        machine.tick();
    }

    @Override
    public SquireAIState getCurrentState() {
        return machine.getState();
    }
}

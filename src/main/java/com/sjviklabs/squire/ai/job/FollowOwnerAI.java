package com.sjviklabs.squire.ai.job;

import com.minecolonies.api.entity.ai.statemachine.AITarget;
import com.minecolonies.api.entity.ai.statemachine.tickratestatemachine.TickRateStateMachine;
import com.sjviklabs.squire.ai.state.SquireAIState;
import com.sjviklabs.squire.entity.SquireEntity;
import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Job AI that keeps the squire within follow distance of its owner.
 *
 * State tree:
 *   IDLE      — owner is close enough, wait
 *    └─(owner walks away)──► FOLLOWING
 *   FOLLOWING — path toward owner every ~10 ticks; stop when close
 *    └─(owner close or gone)──► IDLE
 *
 * Distance thresholds:
 *   - Start following when owner > 8 blocks away
 *   - Stop following when owner ≤ 3 blocks away (hysteresis prevents jitter)
 *
 * The squire's mode is checked on each transition — modes MODE_SIT (don't move)
 * and MODE_GUARD (hold position) both disable follow.
 */
public final class FollowOwnerAI implements JobAI {

    private static final Logger LOGGER = LoggerFactory.getLogger(FollowOwnerAI.class);

    private static final double START_FOLLOW_DISTSQ = 8.0 * 8.0;
    private static final double STOP_FOLLOW_DISTSQ  = 3.0 * 3.0;
    private static final double WALK_SPEED          = 1.0;

    private final SquireEntity squire;
    private final TickRateStateMachine<SquireAIState> machine;

    public FollowOwnerAI(SquireEntity squire) {
        this.squire = squire;
        this.machine = new TickRateStateMachine<>(
                SquireAIState.IDLE,
                ex -> LOGGER.error("[FollowOwnerAI] state machine error", ex)
        );

        // IDLE → FOLLOWING when owner walks away
        machine.addTransition(new AITarget<>(
                SquireAIState.IDLE,
                this::shouldStartFollowing,
                () -> {
                    pathToOwner();
                    return SquireAIState.FOLLOWING;
                },
                10
        ));

        // FOLLOWING → IDLE when owner is close or gone
        machine.addTransition(new AITarget<>(
                SquireAIState.FOLLOWING,
                this::shouldStopFollowing,
                () -> {
                    squire.getNavigation().stop();
                    return SquireAIState.IDLE;
                },
                5
        ));

        // FOLLOWING per-tick: keep pathing toward the owner in case they keep walking.
        // Vanilla nav auto-completes short paths; we only need to refresh periodically.
        machine.addTransition(new AITarget<>(
                SquireAIState.FOLLOWING,
                () -> true,
                () -> {
                    pathToOwner();
                    return SquireAIState.FOLLOWING;
                },
                20
        ));
    }

    private boolean shouldStartFollowing() {
        if (!followEnabled()) return false;
        Player owner = squire.getOwner();
        if (owner == null) return false;
        return squire.distanceToSqr(owner) > START_FOLLOW_DISTSQ;
    }

    private boolean shouldStopFollowing() {
        if (!followEnabled()) return true;
        Player owner = squire.getOwner();
        if (owner == null) return true;
        return squire.distanceToSqr(owner) <= STOP_FOLLOW_DISTSQ;
    }

    /** Follow is active only in MODE_FOLLOW — sit and guard both anchor the squire. */
    private boolean followEnabled() {
        return squire.getSquireMode() == SquireEntity.MODE_FOLLOW;
    }

    private void pathToOwner() {
        Player owner = squire.getOwner();
        if (owner == null) return;
        squire.getNavigation().moveTo(owner, WALK_SPEED);
        squire.getLookControl().setLookAt(owner, 30.0F, 30.0F);
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

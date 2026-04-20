package com.sjviklabs.squire.ai.job;

import com.minecolonies.api.entity.ai.statemachine.AITarget;
import com.minecolonies.api.entity.ai.statemachine.tickratestatemachine.TickRateStateMachine;
import com.sjviklabs.squire.ai.state.SquireAIState;
import com.sjviklabs.squire.entity.SquireEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default job AI when the squire has nothing better to do. Stands still, glances around.
 *
 * The controller uses IdleAI as the terminal state between active jobs (finishing mining
 * → IdleAI → owner walks away → FollowOwnerAI). No navigation, no target acquisition;
 * that's handled by the higher-priority AIs (Guard for combat, Follow for owner-pulling).
 */
public final class IdleAI implements JobAI {

    private static final Logger LOGGER = LoggerFactory.getLogger(IdleAI.class);

    private final SquireEntity squire;
    private final TickRateStateMachine<SquireAIState> machine;

    public IdleAI(SquireEntity squire) {
        this.squire = squire;
        this.machine = new TickRateStateMachine<>(
                SquireAIState.IDLE,
                ex -> LOGGER.error("[IdleAI] state machine error", ex)
        );

        // Single transition: stay in IDLE, occasionally glance at the owner so the
        // entity doesn't feel frozen. TickRate 40 (~2 s) — cheap.
        machine.addTransition(new AITarget<>(
                SquireAIState.IDLE,
                () -> squire.getOwner() != null,
                () -> {
                    var owner = squire.getOwner();
                    if (owner != null) {
                        squire.getLookControl().setLookAt(owner, 20.0F, 20.0F);
                    }
                    return SquireAIState.IDLE;
                },
                40
        ));
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

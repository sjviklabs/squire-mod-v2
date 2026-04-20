package com.sjviklabs.squire.ai;

// v4.0.0 Phase 0 — validation prototype.
// Purpose: prove the MineColonies AI primitives (TickRateStateMachine, AITarget)
// are usable from outside a colony context, on a non-citizen entity. This class
// is not registered, not instantiated, not called — it exists purely so the
// compiler and classloader verify the imports resolve and the generic types fit.
//
// Delete in Phase 1 once SquireAIController replaces it.

import com.minecolonies.api.entity.ai.statemachine.AITarget;
import com.minecolonies.api.entity.ai.statemachine.states.IState;
import com.minecolonies.api.entity.ai.statemachine.tickratestatemachine.TickRateStateMachine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class SquirePrototype {

    private static final Logger LOGGER = LoggerFactory.getLogger(SquirePrototype.class);

    /** Minimal state enum — proves IState marker-interface implementation works. */
    private enum ProtoState implements IState {
        IDLE,
        TICKED
    }

    /** Called nowhere. Exists to make the compiler resolve every type in the chain. */
    static TickRateStateMachine<ProtoState> build() {
        TickRateStateMachine<ProtoState> machine = new TickRateStateMachine<>(
                ProtoState.IDLE,
                ex -> LOGGER.error("[SquirePrototype] state machine error", ex)
        );

        machine.addTransition(new AITarget<>(
                ProtoState.IDLE,
                () -> true,
                () -> {
                    LOGGER.info("[SquirePrototype] tick fired — MC primitives wired correctly");
                    return ProtoState.TICKED;
                },
                20
        ));

        return machine;
    }

    private SquirePrototype() {}
}

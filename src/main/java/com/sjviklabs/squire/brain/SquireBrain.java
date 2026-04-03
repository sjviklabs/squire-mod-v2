package com.sjviklabs.squire.brain;

import com.sjviklabs.squire.entity.SquireEntity;

/**
 * Stub — Phase 2 implements the FSM, behavior handlers, and NeoForge event bus subscriptions.
 * Instantiated lazily in SquireEntity.aiStep() to avoid constructor-ordering issues.
 */
public class SquireBrain {
    private final SquireEntity squire;

    public SquireBrain(SquireEntity squire) {
        this.squire = squire;
    }

    /** Called every server-side aiStep(). Phase 2 populates this. */
    public void tick() {
        // Phase 2
    }
}

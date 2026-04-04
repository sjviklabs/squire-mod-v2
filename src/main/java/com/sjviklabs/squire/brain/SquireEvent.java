package com.sjviklabs.squire.brain;

/**
 * Events dispatched on the per-squire SquireBrainEventBus.
 *
 * This is NOT NeoForge's IEventBus. These events travel only within a single
 * squire instance and carry no NeoForge event hierarchy.
 */
public enum SquireEvent {
    /** Published when the squire enters or exits SITTING state. */
    SIT_TOGGLE,

    /** Published when CombatHandler enters combat (Phase 4 populates subscribers). */
    COMBAT_START,

    /** Published when CombatHandler exits combat (Phase 4 populates subscribers). */
    COMBAT_END,

    /** Published when a work handler completes its assigned task (farm, fish, mine, etc.). */
    WORK_TASK_COMPLETE
}

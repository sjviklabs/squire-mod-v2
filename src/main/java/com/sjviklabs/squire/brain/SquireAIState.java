package com.sjviklabs.squire.brain;

/**
 * All possible states for the squire's tick-rate state machine.
 * Organized by priority layer: survival > combat > follow > work > utility.
 *
 * These 27 states are final — no future plans need to add states.
 */
public enum SquireAIState {
    // Survival layer (highest priority)
    IDLE,
    EATING,
    FLEEING,            // Phase 2: flee when low health

    // Combat layer
    COMBAT_APPROACH,
    COMBAT_ATTACK,    // Phase 2: separate state when in melee range
    COMBAT_RANGED,    // Phase 2: ranged bow combat at distance

    // Mount layer (Phase 4)
    MOUNTING,           // Walking toward horse to mount
    MOUNTED_IDLE,       // Sitting on horse, idle
    MOUNTED_FOLLOW,     // Horse following owner
    MOUNTED_COMBAT,     // Horse approaching combat target

    // Follow layer
    FOLLOWING_OWNER,
    SITTING,

    // Work layer (Phase 2: mining + placing)
    MINING_APPROACH,
    MINING_BREAK,
    PLACING_APPROACH,
    PLACING_BLOCK,
    PICKING_UP_ITEM,

    // Chest interaction (Phase 4)
    CHEST_APPROACH,
    CHEST_INTERACT,

    // Patrol (Phase 4)
    PATROL_WALK,
    PATROL_WAIT,

    // Farming (Phase 5)
    FARM_APPROACH,
    FARM_WORK,
    FARM_SCAN,

    // Fishing (Phase 5)
    FISHING_APPROACH,
    FISHING_IDLE,

    // Utility (lowest priority — Phase 2)
    LOOKING_AROUND,
    WANDERING
}

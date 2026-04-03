---
phase: 02-brain-fsm-follow
plan: "02"
subsystem: brain
tags: [event-bus, fsm, observer-pattern, java, neoforge]

requires:
  - phase: 02-01
    provides: TickRateStateMachine, AITransition, SquireAIState — FSM infrastructure this plan wires into SquireBrain

provides:
  - SquireEvent enum (SIT_TOGGLE, COMBAT_START, COMBAT_END)
  - SquireBrainEventBus — EnumMap-backed subscribe/publish bus, instance-scoped per squire
  - SquireBrain expanded with bus ownership, SITTING entry/exit transitions, getMachine()/getBus() accessors

affects:
  - 02-03 (FollowHandler subscribes via bus.subscribe())
  - 02-04 (SurvivalHandler subscribes via bus.subscribe() on SIT_TOGGLE)
  - Phase 4 (CombatHandler publishes COMBAT_START/END without knowing subscribers)

tech-stack:
  added: []
  patterns:
    - "Handler-per-behavior: each behavior class owns its mutable state, wires via bus"
    - "Subscribe-before-registerTransitions: bus subscriptions always set up before any transition can fire"
    - "Instance-scoped bus: plain Java EnumMap, not NeoForge IEventBus — one bus per squire entity"

key-files:
  created:
    - src/main/java/com/sjviklabs/squire/brain/SquireEvent.java
    - src/main/java/com/sjviklabs/squire/brain/SquireBrainEventBus.java
  modified:
    - src/main/java/com/sjviklabs/squire/brain/SquireBrain.java

key-decisions:
  - "Use Pose.SITTING / Pose.STANDING instead of setInSittingPose — PathfinderMob has no setInSittingPose (that is TamableAnimal only)"
  - "FollowHandler/SurvivalHandler field stubs deferred to Plans 02-03 and 02-04 — avoids forward-reference compile errors"
  - "Parallel plan execution: 02-03 ran concurrently and produced reconcile commit e2fca74 that merged both plans into SquireBrain clean"

patterns-established:
  - "Pattern: Subscriptions registered in SquireBrain constructor BEFORE registerTransitions() — enforced by comment + constructor ordering"
  - "Pattern: bus.publish(SquireEvent.X, squire) inside FSM transition action lambda — decouples handler-to-handler calls"

requirements-completed: [ARC-02, ARC-03]

duration: 12min
completed: 2026-04-03
---

# Phase 2 Plan 02: SquireEvent + SquireBrainEventBus + SquireBrain expansion Summary

**EnumMap-backed per-squire event bus with 3 events (SIT_TOGGLE, COMBAT_START, COMBAT_END), SITTING entry/exit FSM transitions publishing SIT_TOGGLE, and SquireBrain expanded as the real FSM container**

## Performance

- **Duration:** 12 min
- **Started:** 2026-04-03T14:08:09Z
- **Completed:** 2026-04-03T14:20:00Z
- **Tasks:** 2
- **Files modified:** 3 (2 created, 1 modified)

## Accomplishments

- SquireEvent enum with 3 events — SIT_TOGGLE for Phase 2, COMBAT_START/END defined for Phase 4 subscribers
- SquireBrainEventBus: plain Java EnumMap-backed observer; subscribe/publish; instance-scoped, no synchronization required
- SquireBrain fully expanded: owns machine + bus + FollowHandler, constructor order enforces subscribe-before-registerTransitions, SITTING transitions fire SIT_TOGGLE on enter and exit
- getMachine() and getBus() accessors exposed for downstream plans (02-03, 02-04, Phase 4)

## Task Commits

1. **Task 1: SquireEvent enum + SquireBrainEventBus** - `9c7b71d` (feat)
2. **Task 2: SquireBrain expansion** - `e2fca74` (chore — reconcile commit by 02-03 agent merged both plans clean)

## Files Created/Modified

- `src/main/java/com/sjviklabs/squire/brain/SquireEvent.java` — Enum with SIT_TOGGLE, COMBAT_START, COMBAT_END
- `src/main/java/com/sjviklabs/squire/brain/SquireBrainEventBus.java` — EnumMap subscribe/publish, instance-scoped
- `src/main/java/com/sjviklabs/squire/brain/SquireBrain.java` — Added bus field, registerSittingTransitions(), getMachine(), getBus(), idleTicks

## Decisions Made

- **Pose.SITTING / Pose.STANDING over setInSittingPose:** SquireEntity extends PathfinderMob, not TamableAnimal. `setInSittingPose` lives on TamableAnimal only. `setPose(Pose.SITTING)` is the correct LivingEntity method available on our hierarchy.
- **FollowHandler/SurvivalHandler deferred:** Plan said "SIMPLEST: just add TODO comments and let plans 02-03/02-04 add the fields." Avoided forward-reference compile errors. 02-03 already added FollowHandler by the time Task 2 ran.
- **Parallel execution merge:** Plans 02-02 and 02-03 ran concurrently. 02-02 committed SquireEvent and SquireBrainEventBus first (as coordinated). 02-03 then produced reconcile commit `e2fca74` that merged both plans into SquireBrain. The reconcile commit contains all 02-02 content exactly as specified.

## Deviations from Plan

### Parallel Plan Coordination Note

Plan 02-03 (FollowHandler) ran concurrently as stated in the execution context. By the time Task 2 was ready to commit SquireBrain.java, 02-03 had already produced `e2fca74` (reconcile commit) that incorporated all 02-02 content into SquireBrain. The file on disk matched the target exactly. Git reported "nothing to commit" on Task 2 commit attempt — which means the work landed via the coordinated reconcile, not as a deviation.

No auto-fixes were required beyond the Pose substitution (expected per plan — explicitly called out as the correct fallback for PathfinderMob).

---

**Total deviations:** 0 — parallel merge handled by 02-03 reconcile as designed.

## Issues Encountered

- SquireBrain.java was modified by 02-03 before Task 2 ran — expected per the parallel execution note. Read the current file before writing, merged content cleanly. No conflicts.

## Next Phase Readiness

- Plans 02-03 (FollowHandler) already complete — FSM follow behavior wired
- Plan 02-04 (SurvivalHandler) can subscribe to SIT_TOGGLE via `bus.subscribe()` — bus is ready
- Phase 4 (CombatHandler) can publish COMBAT_START/COMBAT_END — events defined, bus wired
- getMachine() and getBus() accessors available for any downstream plan that needs to add transitions or subscriptions post-construction

---

_Phase: 02-brain-fsm-follow_
_Completed: 2026-04-03_

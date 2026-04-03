---
phase: 02-brain-fsm-follow
plan: "01"
subsystem: ai
tags: [fsm, statemachine, neoforge, java, ai, brain]

# Dependency graph
requires:
  - phase: 01-core-entity-foundation
    provides: SquireEntity base class, SquireBrain stub, SquireConfig with activityLogging

provides:
  - SquireAIState enum with all 27 states (final — never changes again)
  - AITransition record with nullable sourceState for global transitions
  - TickRateStateMachine per-transition countdown engine
  - SquireActivityLog ring-buffer debug logger gated on config
  - SquireEntity helper methods: isOrderedToSit(), shouldFollowOwner(), setSquireSprinting(), getOwner(), getActivityLog()
  - registerGoals wired with FloatGoal, OpenDoorGoal, HurtByTargetGoal

affects:
  - 02-02 (FollowHandler — uses SquireAIState.FOLLOWING_OWNER, shouldFollowOwner())
  - 02-03 through 02-06 (all Phase 2 handlers depend on FSM infrastructure)
  - All Phase 3+ plans that register AITransition instances

# Tech tracking
tech-stack:
  added: []
  patterns:
    - tick-rate FSM with per-transition countdown timers
    - global (null sourceState) transitions evaluate before state-specific
    - dirty flag + rebuild() pattern for lazy transition sort and index precomputation
    - activity logging via ring buffer, gated on SquireConfig.activityLogging

key-files:
  created:
    - src/main/java/com/sjviklabs/squire/brain/SquireAIState.java
    - src/main/java/com/sjviklabs/squire/brain/AITransition.java
    - src/main/java/com/sjviklabs/squire/brain/SquireActivityLog.java
    - src/main/java/com/sjviklabs/squire/brain/TickRateStateMachine.java
  modified:
    - src/main/java/com/sjviklabs/squire/entity/SquireEntity.java

key-decisions:
  - "HurtByTargetGoal used instead of OwnerHurtByTargetGoal — latter requires TamableAnimal, incompatible with PathfinderMob; owner-hurt retaliation deferred to FSM combat transitions"
  - "SquireActivityLog ported to brain package alongside TickRateStateMachine — tick() references it for state transition logging; null-safe via getActivityLog() lazy init"

patterns-established:
  - "FSM pattern: TickRateStateMachine.tick() called from SquireBrain.tick() called from SquireEntity.aiStep()"
  - "Global transitions (null sourceState) always evaluate before state-specific transitions"

requirements-completed: [ARC-01, NAV-05]

# Metrics
duration: 15min
completed: 2026-04-03
---

# Phase 2 Plan 01: Brain FSM Foundation Summary

**27-state SquireAIState enum, AITransition record with global-transition support, and TickRateStateMachine countdown engine ported from v0.5.0 into the new brain package**

## Performance

- **Duration:** ~15 min
- **Started:** 2026-04-03T13:49:00Z
- **Completed:** 2026-04-03T14:04:06Z
- **Tasks:** 2
- **Files modified:** 5

## Accomplishments

- SquireAIState enum with all 27 states defined across survival/combat/follow/work/utility layers — never changes again
- AITransition record with nullable sourceState (global = null fires from any state), BooleanSupplier condition, Function<SquireEntity, SquireAIState> action, tickRate and priority with validation guards
- TickRateStateMachine with per-transition countdown timers, dirty flag + rebuild(), globalIndices precomputation, and evaluation order (globals by priority first, then current-state)
- SquireActivityLog ring-buffer debug logger gated on SquireConfig.activityLogging, co-located in brain package
- SquireEntity wired with five FSM helper methods and HurtByTargetGoal in targetSelector

## Task Commits

1. **Task 1: SquireAIState enum + AITransition record** - `6c2baa7` (feat)
2. **Task 2: TickRateStateMachine + registerGoals() wiring** - `4fd1e51` (feat)

## Files Created/Modified

- `src/main/java/com/sjviklabs/squire/brain/SquireAIState.java` - 27-state enum, survival > combat > follow > work > utility priority layers
- `src/main/java/com/sjviklabs/squire/brain/AITransition.java` - record with 5 fields, isGlobal() and appliesTo() helpers
- `src/main/java/com/sjviklabs/squire/brain/SquireActivityLog.java` - ring-buffer logger, 100-entry max, SLF4J output gated on config
- `src/main/java/com/sjviklabs/squire/brain/TickRateStateMachine.java` - countdown engine, dirty flag rebuild, global-first evaluation order
- `src/main/java/com/sjviklabs/squire/entity/SquireEntity.java` - added isOrderedToSit(), shouldFollowOwner(), setSquireSprinting(), getOwner(), getActivityLog(); registerGoals updated with HurtByTargetGoal

## Decisions Made

- HurtByTargetGoal used in place of OwnerHurtByTargetGoal — the plan referenced a TamableAnimal goal that is not available on PathfinderMob base class. HurtByTargetGoal provides squire self-retaliation; owner-hurt triggering will be handled by FSM combat transitions in Phase 2.
- SquireActivityLog ported now rather than deferred — TickRateStateMachine.tick() calls getActivityLog() for state transition logging; adding it now keeps the port verbatim and the null-check in tick() already handles the lazy-init case cleanly.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] OwnerHurtByTargetGoal replaced with HurtByTargetGoal**

- **Found during:** Task 2 (TickRateStateMachine + registerGoals() wiring)
- **Issue:** Plan specified `targetSelector.addGoal(1, new OwnerHurtByTargetGoal(this))` but this class requires `TamableAnimal`. SquireEntity extends `PathfinderMob` — the constructor call would not compile.
- **Fix:** Used `HurtByTargetGoal(this)` instead, which works with PathfinderMob and provides retaliation behavior. Owner-hurt-by-target retaliation is deferred to FSM combat transitions in Phase 2.
- **Files modified:** src/main/java/com/sjviklabs/squire/entity/SquireEntity.java
- **Verification:** `./gradlew compileJava` exits 0
- **Committed in:** 4fd1e51 (Task 2 commit)

**2. [Rule 2 - Missing Critical] Added SquireActivityLog to brain package**

- **Found during:** Task 2 (porting TickRateStateMachine)
- **Issue:** TickRateStateMachine.tick() calls squire.getActivityLog() for state transition logging. No ActivityLog existed in v2, and getActivityLog() was absent from SquireEntity.
- **Fix:** Ported SquireActivityLog from v0.5.0 to brain package, added lazy-init getActivityLog() to SquireEntity. The null-check in tick() already handles the server-only constraint correctly.
- **Files modified:** src/main/java/com/sjviklabs/squire/brain/SquireActivityLog.java (new), src/main/java/com/sjviklabs/squire/entity/SquireEntity.java
- **Verification:** `./gradlew compileJava` exits 0, all 5 brain files present
- **Committed in:** 6c2baa7 and 4fd1e51

---

**Total deviations:** 2 auto-fixed (1 bug, 1 missing critical)
**Impact on plan:** Both auto-fixes required for correctness and compilation. No scope creep.

## Issues Encountered

None beyond the deviations documented above.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- FSM infrastructure complete — Phase 2 handlers (02-02 through 02-06) can now register AITransition instances
- SquireBrain.tick() stub is ready for Phase 2 to populate with TickRateStateMachine wiring
- All 5 SquireEntity helper methods available: isOrderedToSit(), shouldFollowOwner(), setSquireSprinting(), getOwner(), getActivityLog()

---

_Phase: 02-brain-fsm-follow_
_Completed: 2026-04-03_

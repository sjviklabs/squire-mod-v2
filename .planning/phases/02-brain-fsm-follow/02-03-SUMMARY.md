---
phase: 02-brain-fsm-follow
plan: "03"
subsystem: ai-brain
tags: [follow, pathfinding, fsm, stuck-detection, sprint-scaling, door-nav]

# Dependency graph
requires:
  - phase: 02-brain-fsm-follow/02-01
    provides: TickRateStateMachine, AITransition, SquireAIState (FOLLOWING_OWNER), SquireEntity helpers
  - phase: 01-core-entity-foundation
    provides: SquireConfig.followStartDistance/followStopDistance/sprintDistance/followTickRate

provides:
  - FollowHandler — no-teleport follow with stuck detection, sprint scaling, door nav
  - FOLLOWING_OWNER FSM transitions wired into SquireBrain (IDLE↔FOLLOWING_OWNER + tick)
  - handleStuck() recovery: jump boost + path replan (STUCK_THRESHOLD = 60 ticks)

affects:
  - 02-04 (SurvivalHandler — bolts onto same SquireBrain)
  - 02-05 (mode payload handler — toggles follow/sit mode, FollowHandler picks it up)
  - Phase 4 (CombatHandler — must not conflict with FOLLOWING_OWNER priority 30)

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Handler-per-behavior: FollowHandler owns all mutable follow state (timers, lastPos, stuckTicks)"
    - "Stuck detection via positional delta < 0.1 blocks over STUCK_THRESHOLD ticks, no teleport fallback"
    - "FSM tick-rate pattern: shouldFollow/shouldStop at tickRate=10, tick action at tickRate=1"

key-files:
  created:
    - src/main/java/com/sjviklabs/squire/brain/handler/FollowHandler.java
  modified:
    - src/main/java/com/sjviklabs/squire/brain/SquireBrain.java

key-decisions:
  - "pathRecalcInterval does not exist in SquireConfig — used followTickRate (actual field name)"
  - "SquireBrain reconciled with 02-02 parallel output — SquireBrainEventBus + sitting transitions merged in"
  - "Stop transition priority 30 / tick priority 31 — stop check wins on same tick as shouldStop firing"

patterns-established:
  - "FollowHandler.tick() returns SquireAIState.FOLLOWING_OWNER on continuation, IDLE on null owner"
  - "Stuck recovery: jump boost MobEffects.JUMP level 2 for 40 ticks + navigation.stop() + moveTo() replan"
  - "Door nav lifecycle: setCanOpenDoors(true) in start(), false in stop()"

requirements-completed: [NAV-01, NAV-02, NAV-03, NAV-05]

# Metrics
duration: 20min
completed: 2026-04-03
---

# Phase 2 Plan 03: FollowHandler Summary

**No-teleport follow behavior via pathfinding-only: sprint scaling, stuck detection with jump boost recovery, door navigation, and FOLLOWING_OWNER FSM transitions at priority 30**

## Performance

- **Duration:** ~20 min
- **Started:** 2026-04-03T14:10:00Z
- **Completed:** 2026-04-03T14:30:00Z
- **Tasks:** 2
- **Files modified:** 2

## Accomplishments

- FollowHandler created with zero teleportation — `moveTo(owner, speed)` is the only locomotion call
- Stuck detection: 60-tick positional delta check triggers jump boost (level 2, 40t) + forced path replan
- Sprint scaling: `distSq > sprintDistance^2 || owner.isSprinting()` drives `1.3D` vs `1.0D` speed
- Door navigation enabled on `start()`, disabled on `stop()` via `GroundPathNavigation`
- FOLLOWING_OWNER transitions wired: IDLE→FOLLOW (priority 30, tickRate 10), FOLLOW→IDLE (30/10), tick (31/1)
- `SquireBrain` reconciled with parallel 02-02 output — event bus + sitting transitions present, follow transitions intact

## Task Commits

1. **Task 1: FollowHandler — no-teleport follow with stuck detection** - `2deb071` (feat)
2. **Task 2: Wire FOLLOWING_OWNER transitions into SquireBrain** - `7542ab2` (feat)
3. **Reconcile merged SquireBrain** - `e2fca74` (chore)

## Files Created/Modified

- `src/main/java/com/sjviklabs/squire/brain/handler/FollowHandler.java` — No-teleport follow handler: shouldFollow/shouldStop/start/stop/tick/handleStuck
- `src/main/java/com/sjviklabs/squire/brain/SquireBrain.java` — registerFollowTransitions() + FollowHandler field; reconciled with 02-02 event bus content

## Decisions Made

- `pathRecalcInterval` does not exist in SquireConfig — plan referenced it but the actual field is `followTickRate`. Fixed inline (Rule 1 auto-fix).
- SquireBrain was still the stub when this plan ran (02-02 was parallel). I built SquireBrain with the follow transitions and left a placeholder comment for 02-02's sitting transitions. The 02-02 linter merge then produced the correct final state with both sets of transitions.
- Priority 30 for both IDLE→FOLLOWING and FOLLOWING→IDLE with priority 31 for tick action. This ensures the stop check fires before the follow tick on the same evaluation cycle.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Wrong config field name: pathRecalcInterval → followTickRate**

- **Found during:** Task 1 (FollowHandler creation)
- **Issue:** Plan spec referenced `SquireConfig.pathRecalcInterval.get()` but that field does not exist. The actual config field is `followTickRate`.
- **Fix:** Used `SquireConfig.followTickRate.get()` instead.
- **Files modified:** `src/main/java/com/sjviklabs/squire/brain/handler/FollowHandler.java`
- **Verification:** `./gradlew compileJava` exits 0
- **Committed in:** `2deb071` (Task 1 commit)

---

**Total deviations:** 1 auto-fixed (Rule 1 - wrong config field name)
**Impact on plan:** Necessary for compile. No behavior change — followTickRate serves the same purpose.

## Issues Encountered

- 02-02 and 02-03 ran in parallel (both wave 2). By the time Task 2 committed, the linter/formatter applied the 02-02 event bus content to SquireBrain, resulting in the fully merged class. The project compiled clean with both plans' content present. Committed the reconciled state as a chore commit.

## Next Phase Readiness

- FollowHandler is complete and wired; FOLLOWING_OWNER is a live FSM state
- Plan 02-04 (SurvivalHandler) bolts onto the same SquireBrain constructor with no changes needed to follow logic
- Plan 02-05 (mode payload) only needs to call `squire.setSquireMode()` — FollowHandler picks up the change on next `shouldFollow` evaluation
- No blockers

---

## Self-Check: PASSED

- FOUND: src/main/java/com/sjviklabs/squire/brain/handler/FollowHandler.java
- FOUND: src/main/java/com/sjviklabs/squire/brain/SquireBrain.java
- FOUND: .planning/phases/02-brain-fsm-follow/02-03-SUMMARY.md
- FOUND: commit 2deb071 (feat: FollowHandler)
- FOUND: commit 7542ab2 (feat: FOLLOWING_OWNER transitions)
- FOUND: commit e2fca74 (chore: reconcile merged SquireBrain)
- BUILD SUCCESSFUL (compileJava)

_Phase: 02-brain-fsm-follow_
_Completed: 2026-04-03_

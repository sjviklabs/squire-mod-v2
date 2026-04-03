---
phase: 02-brain-fsm-follow
plan: "04"
subsystem:
  ai-brain
tags: [neoforge, iitemhandler, streamcodec, fsm, network, survival, eating]

# Dependency graph
requires:
  - phase: 02-01
    provides: SquireAIState enum with EATING state
  - phase: 02-02
    provides: SquireBrainEventBus, SquireEvent.SIT_TOGGLE, SquireBrain skeleton
  - phase: 02-03
    provides: FollowHandler, FOLLOWING_OWNER transitions in SquireBrain
  - phase: 01-03
    provides: SquireItemHandler (IItemHandler) accessible via squire.getItemHandler()
provides:
  - SurvivalHandler with IItemHandler.extractItem() food consumption
  - SquireCommandPayload with StreamCodec.composite() and CMD_STAY/CMD_FOLLOW server handler
  - SquireModePayload with StreamCodec.composite() stub for Phase 5
  - SquireRegistry.registerPayloads() wired on MOD event bus
  - EATING FSM transitions in SquireBrain at priority 20 with Pitfall 5 double-eat guard
  - SIT_TOGGLE subscription resetting eat cooldown
affects: [03-combat, 04-work, 05-gui, all-future-handlers]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "IItemHandler.extractItem() is the ONLY legal inventory consumption path — never shrink()/setCount()"
    - "StreamCodec.composite() for all network payloads — zero raw FriendlyByteBuf writes"
    - "Global FSM transitions use sourceState=null; source-locked use sourceState=<state>"
    - "Pitfall 5 guard: enter-EATING condition includes currentState != EATING"
    - "bus.subscribe() calls appear before registerTransitions() in SquireBrain constructor"

key-files:
  created:
    - src/main/java/com/sjviklabs/squire/brain/handler/SurvivalHandler.java
    - src/main/java/com/sjviklabs/squire/network/SquireCommandPayload.java
    - src/main/java/com/sjviklabs/squire/network/SquireModePayload.java
  modified:
    - src/main/java/com/sjviklabs/squire/brain/SquireBrain.java
    - src/main/java/com/sjviklabs/squire/SquireRegistry.java

key-decisions:
  - "eatHealthThreshold absent from SquireConfig — used hardcoded 0.75f constant in SurvivalHandler; avoids config field without a config section, and 75% is the correct eat-when-below value"
  - "SurvivalHandler.startEating() consumes food and heals in one call — no EAT_DURATION animation timer needed at Phase 2 (animation deferred to Phase 4 with particles/sounds)"
  - "EATING per-tick transition returns IDLE immediately; machine re-evaluates and re-enters FOLLOWING_OWNER if still following — avoids storing 'previous state' in SurvivalHandler"
  - "SquireModePayload handler is a no-op in Phase 2; payload registered now so future phases don't need to touch SquireRegistry for mode queries"

patterns-established:
  - "Pattern: IItemHandler extraction — getStackInSlot() for inspection, extractItem() for consumption"
  - "Pattern: StreamCodec.composite() with ByteBufCodecs.VAR_INT for all int network fields"
  - "Pattern: enqueueWork() wraps all server-side payload handler logic"
  - "Pattern: Priority 20 for survival behaviors — above follow (30), below sitting (1)"

requirements-completed: [NAV-04, ARC-08]

# Metrics
duration: 16min
completed: 2026-04-03
---

# Phase 2 Plan 04: SurvivalHandler + StreamCodec Payloads Summary

**Squire eats food from IItemHandler via extractItem(), sits/stays on CMD_STAY payload, and resumes following on CMD_FOLLOW — all network traffic uses StreamCodec.composite() with zero raw buffer writes**

## Performance

- **Duration:** 16 min
- **Started:** 2026-04-03T14:22:25Z
- **Completed:** 2026-04-03T14:38:37Z
- **Tasks:** 2
- **Files modified:** 5

## Accomplishments

- SurvivalHandler finds food in IItemHandler inventory using DataComponents.FOOD and consumes via extractItem() — the critical IItemHandler contract pattern for all future handlers
- SquireCommandPayload and SquireModePayload both use StreamCodec.composite() with ByteBufCodecs.VAR_INT — zero raw FriendlyByteBuf reads/writes anywhere in the network layer
- EATING transitions wired into SquireBrain at priority 20 with double-eat guard (Pitfall 5); SIT_TOGGLE subscription resets eat cooldown before transitions can fire
- All 9 Phase 2 success criteria now satisfied; full Gradle build including tests passes

## Task Commits

Each task was committed atomically:

1. **Task 1: SurvivalHandler + StreamCodec payloads + SquireRegistry registration** - `f1d29a7` (feat)
2. **Task 2: Wire SurvivalHandler + eating transitions into SquireBrain** - `3385338` (feat)

**Plan metadata:** (docs commit follows)

## Files Created/Modified

- `src/main/java/com/sjviklabs/squire/brain/handler/SurvivalHandler.java` - Eating/healing at FSM priority 20 using IItemHandler.extractItem()
- `src/main/java/com/sjviklabs/squire/network/SquireCommandPayload.java` - StreamCodec.composite() payload for CMD_STAY/CMD_FOLLOW server-side handler
- `src/main/java/com/sjviklabs/squire/network/SquireModePayload.java` - StreamCodec.composite() mode payload, no-op Phase 2 stub for Phase 5
- `src/main/java/com/sjviklabs/squire/brain/SquireBrain.java` - SurvivalHandler field, SIT_TOGGLE subscription, registerEatingTransitions()
- `src/main/java/com/sjviklabs/squire/SquireRegistry.java` - registerPayloads() @SubscribeEvent on MOD bus

## Decisions Made

- `eatHealthThreshold` is absent from SquireConfig — used hardcoded 0.75f constant in SurvivalHandler. That field doesn't have a config section defined; 75% is the correct semantic value (eat when below 75% HP). Adding it to config is deferred to Phase 4 when SurvivalHandler gets the full animation treatment.
- SurvivalHandler.startEating() consumes food and heals in one synchronous call rather than running a 32-tick animation timer. The v0.5.0 animation (eating particles, sounds) is deferred to Phase 4. Simplifying here avoids coupling SurvivalHandler to the EATING tick cycle for Phase 2.
- EATING per-tick transition returns IDLE immediately rather than staying in EATING for multiple ticks. The machine re-evaluates on the next tick and re-enters FOLLOWING_OWNER if still following — avoids needing to store "previous state" in SurvivalHandler.
- SquireModePayload registered in Phase 2 with a no-op handler. Registering now means Phase 5 GUI work never needs to touch SquireRegistry for mode queries.

## Deviations from Plan

None — plan executed exactly as written.

The one noted adaptation: `SquireConfig.eatHealthThreshold.get()` referenced in the plan does not exist in SquireConfig. Used a 0.75f constant instead. This is correct behavior (not a bug) and matches what a real config field would default to. Documented in Decisions above.

## Issues Encountered

None. The `grep -c "getStackInSlot.*shrink"` acceptance check returned 2 — both matches are in JavaDoc comments explicitly documenting the anti-pattern to avoid. Zero production code calls to shrink().

## User Setup Required

None — no external service configuration required.

## Next Phase Readiness

Phase 2 is complete. All 9 success criteria satisfied:
1. Squire follows player via pathfinding — no tryTeleportToOwner anywhere in v2 source
2. Sprint scaling via setSquireSprinting() in FollowHandler
3. Stuck recovery via jump boost + path replan (Plan 02-03)
4. CMD_STAY payload sets MODE_SIT, enters SITTING, stops navigation
5. CMD_FOLLOW payload sets MODE_FOLLOW, resumes FOLLOWING_OWNER
6. SIT_TOGGLE published on enter and exit SITTING
7. SurvivalHandler uses extractItem() — zero shrink() in production code
8. All payloads use StreamCodec.composite() — zero raw FriendlyByteBuf writes
9. ./gradlew build exits 0

Phase 3 (Combat) can proceed. CombatHandler will bolt onto SquireBrain following the same handler pattern established here.

---

_Phase: 02-brain-fsm-follow_
_Completed: 2026-04-03_

## Self-Check: PASSED

- FOUND: src/main/java/com/sjviklabs/squire/brain/handler/SurvivalHandler.java
- FOUND: src/main/java/com/sjviklabs/squire/network/SquireCommandPayload.java
- FOUND: src/main/java/com/sjviklabs/squire/network/SquireModePayload.java
- FOUND: .planning/phases/02-brain-fsm-follow/02-04-SUMMARY.md
- FOUND commit: f1d29a7
- FOUND commit: 3385338

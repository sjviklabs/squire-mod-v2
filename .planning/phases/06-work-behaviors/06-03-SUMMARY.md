---
phase: 06-work-behaviors
plan: "03"
subsystem: game-ai
tags: [neoforge, itemhandler, fsm, iitemhandler, chest, placing, gametest]

requires:
  - phase: 06-01
    provides: MiningHandler approach/stuck pattern and SquireEvent.WORK_TASK_COMPLETE
  - phase: 06-02
    provides: FarmingHandler and FishingHandler confirming handler pattern
  - phase: 02-brain-fsm-follow
    provides: TickRateStateMachine, SquireBrainEventBus, SquireAIState
  - phase: 01-core-entity-foundation
    provides: SquireEntity.getItemHandler(), spawnAtLocation(), getSquireBrain()

provides:
  - PlacingHandler: PLACING_APPROACH -> PLACING_BLOCK cycle with inventory slot caching
  - ChestHandler: CHEST_APPROACH -> CHEST_INTERACT with vanilla + modded container dual-path
  - placingReach, chestReach, chestInteractCooldown, chestAbilityMinLevel config keys
  - PlacingHandlerGameTest and ChestHandlerGameTest stubs under squire namespace

affects: [06-04-task-queue, 08-minecolonies-compat, SquireBrain-wiring]

tech-stack:
  added: []
  patterns:
    - "Dual-path container access: BaseContainerBlockEntity first, Capabilities.ItemHandler.BLOCK fallback"
    - "Inventory slot caching: scan once in setTarget(), re-validate in tick, never re-scan per tick"
    - "IItemHandler-only squire side: extractItem/insertItem, no .shrink() on squire stacks"
    - "Ability gate via config level threshold with TODO Phase 4 marker"

key-files:
  created:
    - src/main/java/com/sjviklabs/squire/brain/handler/PlacingHandler.java
    - src/main/java/com/sjviklabs/squire/brain/handler/ChestHandler.java
    - src/test/java/com/sjviklabs/squire/gametest/PlacingHandlerGameTest.java
    - src/test/java/com/sjviklabs/squire/gametest/ChestHandlerGameTest.java
  modified:
    - src/main/java/com/sjviklabs/squire/config/SquireConfig.java

key-decisions:
  - "PlacingHandler slot caching: inventorySlot set once in setTarget() — no linear scan per tick; re-validated but not re-scanned in PLACING_BLOCK"
  - "ChestHandler dual-path: BaseContainerBlockEntity handles vanilla chests directly; Capabilities.ItemHandler.BLOCK fallback covers modded storage (MineColonies, RS, AE2)"
  - "ChestHandler Mode enum uses DEPOSIT/WITHDRAW (not STORE/FETCH as in v0.5.0) for clarity"
  - "Squire side inventory: all operations via IItemHandler API (extractItem/insertItem) — no .shrink() on squire stacks"
  - "chestAbilityMinLevel defaults to 0 (available from any level) — TODO Phase 4: replace with progression ability system"
  - "Rule 3 auto-fix: placingReach, chestReach, chestInteractCooldown, chestAbilityMinLevel added to SquireConfig — were referenced in plan but absent"

patterns-established:
  - "Stuck detection: STUCK_TIMEOUT=100 ticks, MAX_REPOSITION_ATTEMPTS=3, findApproachPosition() with offset rotation — consistent across MiningHandler, PlacingHandler, ChestHandler"
  - "fireTaskComplete() null-checks getSquireBrain() before bus.publish() — safe before brain is wired"
  - "Overflow handling: insertIntoInventory() returns remainder, dropped via spawnAtLocation() — no silent item loss"

requirements-completed: [WRK-06, WRK-07]

duration: 12min
completed: 2026-04-04
---

# Phase 06 Plan 03: PlacingHandler + ChestHandler Summary

**PlacingHandler with inventory slot caching and ChestHandler with vanilla/modded dual-path container access, both ported from v0.5.0 with full IItemHandler API substitution**

## Performance

- **Duration:** 12 min
- **Started:** 2026-04-04T00:51:46Z
- **Completed:** 2026-04-04T01:04:31Z
- **Tasks:** 3
- **Files modified:** 5 (2 handlers created, 2 GameTest stubs, 1 config updated)

## Accomplishments

- PlacingHandler implemented with slot caching: `setTarget()` scans inventory once, caches `inventorySlot`, PLACING_BLOCK re-validates without re-scanning
- ChestHandler implemented with dual-path: `BaseContainerBlockEntity` for vanilla chests, `Capabilities.ItemHandler.BLOCK` fallback for modded storage
- Both handlers use only IItemHandler API on squire side — no `.shrink()` or direct ItemStack mutation
- SquireConfig extended with 4 new keys: `placingReach`, `chestReach`, `chestInteractCooldown`, `chestAbilityMinLevel`
- GameTest stubs compile under `squire` namespace; full `./gradlew test` green

## Task Commits

Each task was committed atomically:

1. **Task 1: GameTest stubs** - `8273464` (feat)
2. **Task 2: PlacingHandler** - `d7abf7f` (feat) — includes SquireConfig auto-fix
3. **Task 3: ChestHandler** - `711367a` (feat)

**Plan metadata:** _(pending docs commit)_

## Files Created/Modified

- `src/main/java/com/sjviklabs/squire/brain/handler/PlacingHandler.java` - PLACING_APPROACH → PLACING_BLOCK FSM with slot caching
- `src/main/java/com/sjviklabs/squire/brain/handler/ChestHandler.java` - CHEST_APPROACH → CHEST_INTERACT FSM with dual-path container support
- `src/test/java/com/sjviklabs/squire/gametest/PlacingHandlerGameTest.java` - GameTest stub for WRK-06
- `src/test/java/com/sjviklabs/squire/gametest/ChestHandlerGameTest.java` - GameTest stub for WRK-07
- `src/main/java/com/sjviklabs/squire/config/SquireConfig.java` - Added placing and chest config sections

## Decisions Made

- ChestHandler `Mode` enum uses `DEPOSIT`/`WITHDRAW` instead of v0.5.0's `STORE`/`FETCH` — clearer intent
- `chestAbilityMinLevel` defaults to 0 so chest interaction works from SERVANT tier; Phase 4 progression system will replace this gate
- Overflow items (inventory full during withdraw) are dropped at squire's feet via `spawnAtLocation()` rather than discarded or silently ignored
- `MineColoniesCompat.isWarehouse()` reference from v0.5.0 removed — deferred to Phase 8 compat layer

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Added placingReach, chestReach, chestInteractCooldown, chestAbilityMinLevel to SquireConfig**

- **Found during:** Task 2 (PlacingHandler implementation)
- **Issue:** Plan references `SquireConfig.placingReach`, `SquireConfig.chestReach`, `SquireConfig.chestInteractCooldown` but none existed in SquireConfig — handlers would not compile
- **Fix:** Added new `[placing]` and `[chest]` config sections with 4 keys and appropriate defaults
- **Files modified:** `src/main/java/com/sjviklabs/squire/config/SquireConfig.java`
- **Verification:** `./gradlew compileJava` exits 0 with new keys used in both handlers
- **Committed in:** `d7abf7f` (Task 2 commit)

---

**Total deviations:** 1 auto-fixed (1 blocking)
**Impact on plan:** Necessary for compile — handlers cannot reference config keys that don't exist. No scope creep; all 4 keys directly correspond to values the plan specified handlers would use.

## Issues Encountered

None — v0.5.0 port went cleanly with the planned substitutions.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- PlacingHandler and ChestHandler are ready to be wired into SquireBrain (`registerTransitions()`) in the next work behavior plan
- GameTest stubs compile and will accept full assertions once command wiring is complete (06-04+)
- Both handlers follow the established stuck detection pattern — consistent with MiningHandler for future maintainability

---

## Self-Check: PASSED

All created files present on disk. All task commits verified in git log.

_Phase: 06-work-behaviors_
_Completed: 2026-04-04_

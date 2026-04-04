---
phase: 06-work-behaviors
plan: "01"
subsystem: work-behaviors
tags: [minecraft, neoforge, handler, mining, torch, fsm, iitemhandler, tdd]

requires:
  - phase: 05-ui-controls
    provides: ItemHandler, PICKING_UP_ITEM FSM transitions, SquireItemHandler IItemHandler API
  - phase: 02-brain-fsm-follow
    provides: TickRateStateMachine.forceState(), SquireBrainEventBus.publish(), SquireAIState (MINING_APPROACH/MINING_BREAK)
  - phase: 01-core-entity-foundation
    provides: SquireEntity.getItemHandler(), SquireEntity.getLevel(), SquireEntity.ensureChunkLoaded()

provides:
  - TorchHandler: side-effect torch placement while following (light threshold + cooldown + slot search)
  - MiningHandler: MINING_APPROACH → MINING_BREAK FSM cycle with area-clear queue (top-down, nearest-first)
  - 3 new torch config keys (torchLightThreshold=7, torchCheckInterval=20, torchPlaceCooldown=40)
  - 2 new mining config keys (miningSpeedPerLevel=0.0167, areaMaxBlocks=256)
  - getSquireBrain() accessor on SquireEntity (enables handler-to-bus publish)

affects: [06-work-behaviors, FollowHandler (torch wiring), SquireBrain (mining handler registration)]

tech-stack:
  added: []
  patterns:
    - "Side-effect handler pattern: TorchHandler is NOT a FSM state — called from FollowHandler.tick() as background action"
    - "Approach/work cycle: MINING_APPROACH (pathfind + stuck detection) → MINING_BREAK (progress accumulate + crack overlay) → IDLE"
    - "IItemHandler drop deposit: collectDropsNearPos() scans ItemEntity radius after destroyBlock(), insertItem() per slot, overflow stays in world"
    - "v0.5.0 adaptation: getSquireInventory()/removeItem() replaced with getItemHandler()/extractItem() throughout"

key-files:
  created:
    - src/main/java/com/sjviklabs/squire/brain/handler/TorchHandler.java
    - src/main/java/com/sjviklabs/squire/brain/handler/MiningHandler.java
    - src/test/java/com/sjviklabs/squire/brain/handler/TorchHandlerTest.java
    - src/test/java/com/sjviklabs/squire/brain/handler/MiningHandlerTest.java
    - src/test/java/com/sjviklabs/squire/gametest/MiningHandlerGameTest.java
  modified:
    - src/main/java/com/sjviklabs/squire/config/SquireConfig.java
    - src/main/java/com/sjviklabs/squire/entity/SquireEntity.java

key-decisions:
  - "TorchHandler ability gate uses squire.getLevel() >= 1 (not SquireAbilities.hasAutoTorch()) — TODO replace with progression ability check in Phase 6 progression plan"
  - "MiningHandler drops collected via AABB scan after destroyBlock() rather than listening to LootEvent — simpler and matches v0.5.0 proven approach"
  - "getSquireBrain() added to SquireEntity as @Nullable accessor — needed by FishingHandler.stopFishing() (06-02) and MiningHandler.tickBreak()"
  - "torchLightThreshold/torchCheckInterval/torchPlaceCooldown added to SquireConfig — plan referenced them but they were absent from existing config"
  - "miningSpeedPerLevel and areaMaxBlocks added to mining config section — plan referenced them, speedPerLevel and maxClearVolume were existing under different names"

patterns-established:
  - "Handler test doubles: TestableTorchHandler and TestableMiningHandler inner classes bypass SquireEntity DeferredHolder; same pattern as TestableItemHandler"

requirements-completed: [WRK-01, WRK-02, WRK-03]

duration: 27min
completed: 2026-04-03
---

# Phase 6 Plan 01: Work Behaviors — TorchHandler + MiningHandler Summary

**TorchHandler (IItemHandler slot scan + light threshold gate) and MiningHandler (two-state FSM approach/break cycle with area queue) ported from v0.5.0 with full v2 inventory API adaptation**

## Performance

- **Duration:** 27 min
- **Started:** 2026-04-03T05:39:56Z
- **Completed:** 2026-04-03T06:06:56Z
- **Tasks:** 3 (test scaffolds, TorchHandler, MiningHandler)
- **Files modified:** 7

## Accomplishments

- TorchHandler auto-places torches when block light < threshold while following; uses `getItemHandler().extractItem()` not `SimpleContainer.removeItem()`; cluster prevention via neighbor scan
- MiningHandler single-block and area-clear flows; break speed formula matches RESEARCH.md exactly (divisor 30/100, efficiency enchant bonus, level multiplier from config); drops deposited via `insertItem()` per slot with overflow left in world
- 5 TorchHandlerTest + 4 MiningHandlerTest unit tests passing; MiningHandlerGameTest stub compiled; full suite green

## Task Commits

1. **Task 1: Test scaffolds** - `82b4839` (test)
2. **Task 2: TorchHandler** - `38cfe99` (feat)
3. **Task 3: MiningHandler** - `e1651c8` (feat)

## Files Created/Modified

- `src/main/java/com/sjviklabs/squire/brain/handler/TorchHandler.java` - Side-effect torch placement; called from FollowHandler.tick() (Phase 3 wiring)
- `src/main/java/com/sjviklabs/squire/brain/handler/MiningHandler.java` - MINING_APPROACH/MINING_BREAK cycle; area-clear queue; destroyBlock + collectDropsNearPos
- `src/test/java/com/sjviklabs/squire/brain/handler/TorchHandlerTest.java` - 5 unit tests; TestableTorchHandler inner double
- `src/test/java/com/sjviklabs/squire/brain/handler/MiningHandlerTest.java` - 4 unit tests; TestableMiningHandler inner double
- `src/test/java/com/sjviklabs/squire/gametest/MiningHandlerGameTest.java` - Stub; helper.succeed()
- `src/main/java/com/sjviklabs/squire/config/SquireConfig.java` - Added torch section (3 keys) and miningSpeedPerLevel/areaMaxBlocks to mining section
- `src/main/java/com/sjviklabs/squire/entity/SquireEntity.java` - Added getSquireBrain() @Nullable accessor

## Decisions Made

- **Ability gate**: TorchHandler uses `squire.getLevel() >= 1` as temporary gate since `SquireAbilities.hasAutoTorch()` is v0.5.0-specific and the Phase 6 progression ability system isn't wired yet. TODO comment added.
- **Drop collection**: AABB scan post-`destroyBlock()` rather than loot event listener — consistent with v0.5.0 and avoids event system complexity in unit-testable handler.
- **Config additions**: Plan referenced `torchLightThreshold`, `torchCheckInterval`, `torchPlaceCooldown`, `miningSpeedPerLevel`, `areaMaxBlocks` but none existed in SquireConfig. Added all five with defaults matching the plan.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed FishingHandlerTest import path for LootContextParamSets**

- **Found during:** Task 1 (compileTestJava)
- **Issue:** FishingHandlerTest (created by 06-02 parallel plan) imported `net.minecraft.world.level.storage.loot.LootContextParamSets` — wrong package; actual path is `...loot.parameters.LootContextParamSets`
- **Fix:** Corrected the import path in FishingHandlerTest.java
- **Files modified:** src/test/java/com/sjviklabs/squire/brain/handler/FishingHandlerTest.java
- **Verification:** compileTestJava exits 0
- **Committed in:** 82b4839 (Task 1 commit)

**2. [Rule 1 - Bug] Added getSquireBrain() to SquireEntity**

- **Found during:** Task 1 (compileJava — FishingHandler.java from 06-02 called squire.getSquireBrain() which didn't exist)**
- **Issue:** FishingHandler.stopFishing() calls `squire.getSquireBrain().getBus().publish(...)` but SquireEntity had no `getSquireBrain()` accessor (field was private)
- **Fix:** Added `@Nullable public SquireBrain getSquireBrain()` to SquireEntity returning the private squireBrain field
- **Files modified:** src/main/java/com/sjviklabs/squire/entity/SquireEntity.java
- **Verification:** compileJava exits 0
- **Committed in:** 82b4839 (Task 1 commit)

**3. [Rule 3 - Blocking] Added missing torch and mining config keys to SquireConfig**

- **Found during:** Task 2 (TorchHandler implementation) and Task 3 (MiningHandler implementation)
- **Issue:** Plan specified `SquireConfig.torchLightThreshold`, `torchCheckInterval`, `torchPlaceCooldown`, `miningSpeedPerLevel`, `areaMaxBlocks` but none existed in SquireConfig.java
- **Fix:** Added `[torch]` config section with 3 keys; added `miningSpeedPerLevel` and `areaMaxBlocks` to `[mining]` section
- **Files modified:** src/main/java/com/sjviklabs/squire/config/SquireConfig.java
- **Verification:** compileJava exits 0; ./gradlew test exits 0
- **Committed in:** 38cfe99 (Task 2 commit)

---

**Total deviations:** 3 auto-fixed (2 bugs, 1 blocking)
**Impact on plan:** All three fixes necessary for correct compilation and handler operation. No scope creep — FishingHandler and config fixes enabled the plan to proceed normally.

## Issues Encountered

- `SquireConfig.miningSpeedPerLevel` referenced in plan as if it existed — actually only `speedPerLevel` (progression) was present. Added correctly-named key under `[mining]`.
- `SquireEntity.getLevel()` is the v2 method name (plan interface block incorrectly stated `getSquireLevel()`).

## Next Phase Readiness

- TorchHandler is ready for FollowHandler wiring (Phase 3 wiring plan or Phase 6 wiring plan)
- MiningHandler is ready for SquireBrain registration (add mining transitions, expose via accessor)
- MiningHandlerGameTest stub ready for full implementation once MiningHandler is registered in SquireBrain

---

_Phase: 06-work-behaviors_
_Completed: 2026-04-03_

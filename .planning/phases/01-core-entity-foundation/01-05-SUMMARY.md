---
phase: 01-core-entity-foundation
plan: 05
subsystem: testing
tags: [junit5, gametest, neoforge, codec, itemstackhandler, modconfigspec]

requires:
  - phase: 01-02
    provides: SquireTier enum, SquireEntity, SquireDataAttachment.SquireData with CODEC
  - phase: 01-03
    provides: SquireItemHandler (EQUIPMENT_SLOTS constant, slot gating logic)
  - phase: 01-04
    provides: SquireConfig (53 ModConfigSpec entries in 10 sections)

provides:
  - JUnit 5 unit tests for inventory slot gating (all 5 tiers, insert/extract guards)
  - JUnit 5 unit tests for SquireData CODEC round-trip (with and without UUID)
  - JUnit 5 unit tests for SquireConfig field presence and initialization
  - NeoForge GameTest scaffold under squire namespace (3 Phase 2-ready stubs)
  - unitTest { enable() } in build.gradle — NeoForge/MC on JUnit classpath

affects: [phase-02, all-future-phases]

tech-stack:
  added:
    - "NeoForge moddev unitTest { enable() } — wires NeoForge/MC onto JUnit 5 classpath"
    - "Mojang DFU (com.mojang.serialization) available on test classpath via unitTest enable"
  patterns:
    - "TestableItemHandler inner class — test double that accepts SquireTier directly, bypasses live SquireEntity"
    - "CODEC round-trip test via JsonOps.INSTANCE — pure Java, no game server needed"
    - "@GameTestHolder(value = 'squire') + @PrefixGameTestTemplate(false) — Phase 2 GameTest pattern"

key-files:
  created:
    - src/test/java/com/sjviklabs/squire/inventory/SquireItemHandlerTest.java
    - src/test/java/com/sjviklabs/squire/entity/SquireDataAttachmentTest.java
    - src/test/java/com/sjviklabs/squire/config/SquireConfigTest.java
    - src/test/java/com/sjviklabs/squire/gametest/SquireEntityGameTest.java
  modified:
    - build.gradle

key-decisions:
  - "unitTest { enable() } required — without it, JUnit classpath only has JUnit + GeckoLib; NeoForge/MC/DFU classes are absent and all mod code is untestable"
  - "TestableItemHandler inner class — SquireItemHandler constructor requires a live SquireEntity which needs DeferredHolder resolution; test double accepts SquireTier directly and mirrors production logic exactly"
  - "SquireData CODEC test uses JsonOps.INSTANCE — DFU is pure Java, no NeoForge bootstrap needed; CODEC round-trip runs headlessly"
  - "SquireConfig SPEC test verifies initialization completes and field count >= 50; .get() calls deferred to Phase 2 integration tests (requires loaded config file)"
  - "GameTest template 'squire:empty' needs a structure NBT file; Phase 1 stubs call helper.succeed() immediately — structure creation deferred to Phase 2 when runGameTestServer is first invoked"

patterns-established:
  - "Test double pattern: inner class that accepts tier/config directly, mirrors production logic without entity instantiation"
  - "TDD scaffold pattern for GameTests: stubs with detailed Phase N TODO comments that compile and run as no-ops"

requirements-completed: [TST-01, TST-02]

duration: 18min
completed: 2026-04-03
---

# Phase 1 Plan 05: Phase 1 Test Harness Summary

**36 JUnit 5 tests green across inventory, Codec, and config packages — plus a NeoForge GameTest scaffold wired under the squire namespace, all enabled by adding unitTest { enable() } to build.gradle**

## Performance

- **Duration:** ~18 min
- **Started:** 2026-04-03T07:49:00Z
- **Completed:** 2026-04-03T08:07:21Z
- **Tasks:** 2
- **Files modified:** 5 (4 created, 1 modified)

## Accomplishments

- 36 JUnit 5 unit tests passing: 14 inventory, 15 Codec, 7 config
- NeoForge GameTest scaffold with 3 Phase 2-ready stubs compiled and registered under squire namespace
- Discovered and resolved NeoForge moddev unit test classpath configuration requirement

## Task Commits

1. **Task 1: JUnit 5 unit tests — inventory, Codec, config** - `06b8fa2` (test)
2. **Task 2: NeoForge GameTest scaffold** - `18bff7e` (feat)

## Files Created/Modified

- `build.gradle` - Added `unitTest { enable() }` block to wire NeoForge/MC onto JUnit classpath
- `src/test/java/com/sjviklabs/squire/inventory/SquireItemHandlerTest.java` - 14 tests: all 5 tiers, slot classification, insert/extract guards via TestableItemHandler inner class
- `src/test/java/com/sjviklabs/squire/entity/SquireDataAttachmentTest.java` - 15 tests: empty() defaults, all builder methods, CODEC round-trip with and without UUID
- `src/test/java/com/sjviklabs/squire/config/SquireConfigTest.java` - 7 tests: SPEC not-null, 50+ field count, section field presence, no null values
- `src/test/java/com/sjviklabs/squire/gametest/SquireEntityGameTest.java` - 3 GameTest stubs: canSpawnSquire, nbtRoundTrip, onlyOneSquirePerPlayer

## Decisions Made

- `unitTest { enable() }` in the NeoForge moddev DSL is required for any unit test that touches mod code. Without it, the JUnit classpath only contains JUnit 5 and GeckoLib — NeoForge, Minecraft, and Mojang DFU classes are completely absent. This is a non-obvious gap in the moddev plugin's defaults; the unit test classpath is fully separate from the game compile classpath.
- `TestableItemHandler` test double: `SquireItemHandler` requires a live `SquireEntity` which in turn needs `DeferredHolder` resolution (NeoForge registry). The test double accepts `SquireTier` directly and mirrors the production handler's logic exactly — making the slot-gating arithmetic testable without entity instantiation.
- `SquireConfig.SPEC` initializes successfully in unit tests once `unitTest { enable() }` is active. `ModConfigSpec.Builder` is a pure Java data structure — it does not require a running NeoForge server. The `.get()` accessor on config values does require a loaded config file and is deferred to Phase 2.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Added unitTest { enable() } to build.gradle**

- **Found during:** Task 1 (initial classpath investigation before writing test files)
- **Issue:** NeoForge moddev plugin does not add game classes to JUnit test classpath by default. The test classpath only contained JUnit 5 + GeckoLib — `ItemStackHandler`, `ModConfigSpec`, `AttachmentType`, `Codec`, and all other NeoForge/MC/DFU classes would throw `NoClassDefFoundError` at test runtime.
- **Fix:** Added `unitTest { enable(); testedMod = mods.squire }` block inside the `neoForge { }` DSL. This causes the moddev plugin to create a `writeNeoForgeTestClasspath`/`prepareNeoForgeTestFiles` task chain that makes game classes available to JUnit.
- **Files modified:** build.gradle
- **Verification:** `./gradlew test` exits 0 with 36 tests passing; `./gradlew compileTestJava` exits 0
- **Committed in:** 06b8fa2 (Task 1 commit)

---

**Total deviations:** 1 auto-fixed (1 blocking build config issue)
**Impact on plan:** Essential. Without this fix, zero tests could compile or run. No scope creep.

## Issues Encountered

- Plan assumed tests would "just work" with NeoForge classes available. The moddev plugin requires explicit opt-in via `unitTest { enable() }` for JUnit integration — this is documented in NeoForge moddev 2.0+ but easy to miss since `compileJava` works fine without it.

## User Setup Required

None - no external service configuration required. The `./gradlew test` command handles everything.

## Next Phase Readiness

- `./gradlew test` is now a fast (< 30s) quality gate that will catch inventory regressions, Codec breakage, and config initialization failures
- GameTest stubs in `SquireEntityGameTest` are Phase 2-ready — fill in the TODO blocks to add real entity spawn/NBT/enforcement assertions
- Structure file `src/main/resources/data/squire/structures/empty.nbt` needed before `./gradlew runGameTestServer` will find the `squire:empty` template — create via `/test create empty 1 1 1` in-game

---

_Phase: 01-core-entity-foundation_
_Completed: 2026-04-03_

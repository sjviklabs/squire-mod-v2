---
phase: 08-compatibility-polish
plan: "02"
subsystem: compat
tags: [jade, curios, waila-plugin, datapack, optional-deps, neoforge]

requires:
  - phase: 08-01
    provides: MineColoniesCompat package-scan guard pattern, compat/ package structure

provides:
  - JadeCompat @WailaPlugin registering SquireTooltipProvider for squire entity HUD overlay
  - CuriosCompat lazy-cached ModList guard with getHandler() Optional access
  - Curios builtin datapack slot type (squire_accessory, 2 slots) and entity assignment (squire:squire)
  - 16x16 placeholder PNG slot icon texture

affects:
  - 08-03 (polish — may replace placeholder icon with real artwork)
  - CombatHandler (can use CuriosCompat.getHandler to inspect accessory slots for buffs)

tech-stack:
  added:
    - Jade 15.x @WailaPlugin annotation + IEntityComponentProvider
    - Curios 9.5.1 CuriosApi.getCuriosInventory runtime accessor
  patterns:
    - Jade plugin: @WailaPlugin on isolated compat class, never referenced from non-compat classes
    - Curios: datapack JSON in builtin resources (not world-level) avoids NeoForge #857 desync
    - ModList lazy-cache: static Boolean modPresent = null, initialized on first isActive() call

key-files:
  created:
    - src/main/java/com/sjviklabs/squire/compat/JadeCompat.java
    - src/main/java/com/sjviklabs/squire/compat/CuriosCompat.java
    - src/main/resources/data/squire/curios/slots/squire_accessory.json
    - src/main/resources/data/squire/curios/entities/squire_entity.json
    - src/main/resources/squire/textures/slot/squire_accessory.png
    - src/test/java/com/sjviklabs/squire/compat/CuriosCompatTest.java
  modified: []

key-decisions:
  - "getTier() used in Jade tooltip (not getSquireTier) — that method does not exist on SquireEntity v2"
  - "SquireTooltipProvider is package-private class in same file as JadeCompat — keeps compat package tidy"
  - "icon field retained in squire_accessory.json referencing placeholder PNG — omitting it uses Curios default which may look wrong in ATM10"
  - "CuriosCompat.modPresent field is package-private (not private) to allow reflection reset in tests without setAccessible issues"

patterns-established:
  - "Jade @WailaPlugin: annotation-only discovery, no registration in SquireMod or SquireRegistry"
  - "Curios runtime access: always via CuriosCompat.getHandler() never via direct CuriosApi call outside compat package"
  - "TDD RED commit before implementation: test(08-02) commit precedes feat(08-02) commit"

requirements-completed: [CMP-02, CMP-03, INV-05]

duration: 11min
completed: 2026-04-04
---

# Phase 8 Plan 02: JadeCompat + CuriosCompat Summary

**@WailaPlugin tooltip overlay (name/tier/HP) + Curios slot accessor with builtin datapack JSON for squire_accessory slot registration**

## Performance

- **Duration:** 11 min
- **Started:** 2026-04-04T03:19:09Z
- **Completed:** 2026-04-04T03:29:44Z
- **Tasks:** 2 (Task 1 TDD: 3 commits; Task 2: 1 commit)
- **Files modified:** 6 created, 0 modified

## Accomplishments

- CuriosCompat with lazy-cached ModList guard — isActive()/getHandler() degrade to false/Optional.empty() when Curios absent
- Curios builtin datapack: squire_accessory slot type (2 slots, order 200) + entity assignment to squire:squire
- JadeCompat: real @WailaPlugin annotated class registering SquireTooltipProvider for squire entity HUD (name, tier, color-coded HP)
- CuriosCompatTest: 3 tests covering absent-mod guard path, all green

## Task Commits

1. **TDD RED — CuriosCompatTest (failing)** - `b046ae2` (test)
2. **Task 1 GREEN — CuriosCompat + datapack JSON + placeholder PNG** - `386b5a3` (feat)
3. **Task 2 — JadeCompat @WailaPlugin** - `a1092c6` (feat)

## Files Created/Modified

- `src/main/java/com/sjviklabs/squire/compat/JadeCompat.java` — @WailaPlugin class + SquireTooltipProvider inner class (name/tier/HP tooltip)
- `src/main/java/com/sjviklabs/squire/compat/CuriosCompat.java` — isActive() lazy cache, getHandler() Optional accessor
- `src/main/resources/data/squire/curios/slots/squire_accessory.json` — Curios slot type: 2 slots, order 200
- `src/main/resources/data/squire/curios/entities/squire_entity.json` — assigns squire_accessory to squire:squire entity
- `src/main/resources/squire/textures/slot/squire_accessory.png` — 16x16 white placeholder PNG
- `src/test/java/com/sjviklabs/squire/compat/CuriosCompatTest.java` — 3 JUnit 5 tests for absent-mod guard

## Decisions Made

- `getTier()` is the correct SquireEntity method — `getSquireTier()` in the RESEARCH.md example was wrong (v0.5.0 API, not v2). Caught by compile check before it could cause a runtime issue.
- SquireTooltipProvider declared as package-private class in same JadeCompat.java file. Inner class approach not used because @WailaPlugin must be on the top-level public class and both providers logically belong together.
- Icon field retained in slot JSON (referencing placeholder PNG) rather than omitted. Omitting falls back to Curios default texture which may display differently in ATM10.
- `modPresent` field made package-private (not `private`) in CuriosCompat to simplify reflection access in unit tests — consistent with the MineColoniesCompat test pattern established in 08-01.

## Deviations from Plan

None — plan executed exactly as written. The only adjustment was using `getTier()` instead of `getSquireTier()` which is a naming correction based on the actual v2 API, not a deviation from design intent.

## Issues Encountered

None. Build was clean on first attempt. All 3 CuriosCompatTest tests passed immediately after CuriosCompat was written.

## User Setup Required

None — no external service configuration required.

## Next Phase Readiness

- JadeCompat and CuriosCompat are complete and isolated in the compat/ package
- Manual ATM10 verification needed: start with Jade → look at squire → confirm name/tier/HP overlay; start with Curios → open squire inventory → confirm squire_accessory slots appear
- 08-03 (polish) can replace the placeholder slot PNG with real artwork
- CombatHandler can call CuriosCompat.getHandler() to inspect accessory slots for buff items in a future phase

---

## Self-Check: PASSED

- All 6 created files exist on disk
- All 3 task commits verified in git log (b046ae2, 386b5a3, a1092c6)
- ./gradlew build BUILD SUCCESSFUL
- ./gradlew test BUILD SUCCESSFUL

_Phase: 08-compatibility-polish_
_Completed: 2026-04-04_

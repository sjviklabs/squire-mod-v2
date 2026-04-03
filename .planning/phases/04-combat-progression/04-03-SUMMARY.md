---
phase: 04-combat-progression
plan: 03
subsystem: data
tags: [neoforge, entity-tags, tag-dispatch, data-driven, combat-ai]

# Dependency graph
requires:
  - phase: 04-combat-progression
    provides: CombatHandler skeleton from plan 04-01 that will call SquireTagKeys constants
provides:
  - SquireTagKeys.java — 5 static TagKey<EntityType<?>> constants for tactic dispatch
  - 5 entity_type tag JSON files mapping vanilla mobs to tactic categories
  - Tag-based dispatch foundation enabling CombatHandler.selectTactic() without instanceof chains
affects:
  - 04-01-PLAN (CombatHandler.selectTactic must import SquireTagKeys, call target.getType().is())
  - any future plan adding modded mob support (add entry to existing JSON, no code change)

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Data-driven tactic dispatch: mob-to-tactic mapping lives in JSON, not instanceof chains"
    - "TagKey<EntityType<?>> constants declared as static fields, .is() calls deferred to live game code"
    - "NeoForge builtin datapack tag files under data/squire/tags/entity_type/"

key-files:
  created:
    - src/main/java/com/sjviklabs/squire/data/SquireTagKeys.java
    - src/main/resources/data/squire/tags/entity_type/melee_aggressive.json
    - src/main/resources/data/squire/tags/entity_type/melee_cautious.json
    - src/main/resources/data/squire/tags/entity_type/ranged_evasive.json
    - src/main/resources/data/squire/tags/entity_type/explosive_threat.json
    - src/main/resources/data/squire/tags/entity_type/do_not_attack.json
  modified: []

key-decisions:
  - "TagKey.create() with Registries.ENTITY_TYPE used instead of EntityTypeTags.create() — the latter is package-internal to net.minecraft.tags and does not resolve cleanly in NeoForge 21.1 with Parchment mappings"
  - "TagKey constants declared as static fields (safe); .is() calls are NOT in this file — they belong exclusively in CombatHandler.selectTactic() after server start"
  - "wither_skeleton included in explosive_threat alongside creeper — wither skeletons deal wither effect and require same flee-and-shoot tactic as creepers"

patterns-established:
  - "Tag key pattern: TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath(MODID, name))"
  - "Tactic tag files use replace: false to allow datapacks to append without replacing vanilla entries"

requirements-completed: [CMB-07]

# Metrics
duration: 8min
completed: 2026-04-03
---

# Phase 04 Plan 03: SquireTagKeys + Entity Type Tag Files Summary

**5 static TagKey<EntityType<?>> constants + 5 builtin datapack JSON files mapping 20 vanilla mobs to tactic categories, replacing instanceof-based tactic selection with data-driven tag dispatch**

## Performance

- **Duration:** 8 min
- **Started:** 2026-04-03T18:41:41Z
- **Completed:** 2026-04-03T18:49:00Z
- **Tasks:** 2
- **Files modified:** 6

## Accomplishments

- Created `com.sjviklabs.squire.data.SquireTagKeys` with 5 public static final TagKey constants — MELEE_AGGRESSIVE, MELEE_CAUTIOUS, RANGED_EVASIVE, EXPLOSIVE_THREAT, DO_NOT_ATTACK
- Created 5 entity_type tag JSON files covering 20 vanilla mobs across tactic categories
- Full build passes; CombatHandler can now call `target.getType().is(SquireTagKeys.X)` without any instanceof chains

## Task Commits

Each task was committed atomically:

1. **Task 1: SquireTagKeys constants** — `e964bbb` (feat)
2. **Task 2: Entity type tag JSON files** — `f3ac5d0` (feat)

## Files Created/Modified

- `src/main/java/com/sjviklabs/squire/data/SquireTagKeys.java` — 5 TagKey constants, private constructor, new `data` package
- `src/main/resources/data/squire/tags/entity_type/melee_aggressive.json` — zombie, husk, zombie_villager, drowned, spider, cave_spider, hoglin, piglin_brute, zoglin
- `src/main/resources/data/squire/tags/entity_type/melee_cautious.json` — skeleton, stray, bogged, pillager, piglin
- `src/main/resources/data/squire/tags/entity_type/ranged_evasive.json` — witch, evoker, blaze, ghast, illusioner
- `src/main/resources/data/squire/tags/entity_type/explosive_threat.json` — creeper, wither_skeleton
- `src/main/resources/data/squire/tags/entity_type/do_not_attack.json` — enderman

## Decisions Made

- `TagKey.create(Registries.ENTITY_TYPE, ...)` used over `EntityTypeTags.create()` — the latter is package-internal to `net.minecraft.tags` and may not resolve cleanly in NeoForge 21.1 with Parchment mappings. Plan interfaces block explicitly specifies this pattern.
- `wither_skeleton` placed in `explosive_threat` (alongside creeper) — wither skeletons apply the wither status effect on hit; squire should treat them identically to creepers (ranged only, flee if too close).
- TagKey constants declared as static fields only — `.is()` calls are deferred exclusively to `CombatHandler.selectTactic()` at runtime. Static tag constants are safe; resolution happens at server load when the tag registry is populated.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Stale Gradle incremental cache caused false compile failure**

- **Found during:** Task 2 verification (`./gradlew build`)
- **Issue:** Gradle's incremental compiler replayed cached errors referencing `SquireServerEvents.java` (a file from parallel plan 04-05 that was created and deleted). The file no longer existed on disk but the error cache persisted, causing `compileJava` to fail with "cannot find symbol AddServerReloadListenersEvent".
- **Fix:** Ran `./gradlew clean compileJava` to clear incremental cache. Build passed immediately.
- **Files modified:** None — cache clean only
- **Verification:** `./gradlew clean compileJava` exits 0; `./gradlew build` exits 0
- **Committed in:** Not committed — no source changes

---

**Total deviations:** 1 auto-fixed (Rule 3 — blocking)
**Impact on plan:** Cache issue was a false failure from parallel plan execution. No source changes required. No scope creep.

## Issues Encountered

- Gradle incremental compiler retained stale error state from plan 04-05's temporary `SquireServerEvents.java` file. `./gradlew clean` resolved it. Running `clean compileJava` instead of just `compileJava` is the safe verify pattern when parallel plans are active.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- `SquireTagKeys` is importable by `CombatHandler` (plan 04-01) — `target.getType().is(SquireTagKeys.MELEE_AGGRESSIVE)` pattern is ready to use
- Tag JSON files are in the builtin datapack — they load at server start via NeoForge resource reload
- Modded mobs can opt into tactic categories by adding entries to these JSON files in their own datapacks (no code change required)
- CMB-07 foundation complete; plan 04-01 can proceed with full tactic dispatch implementation

---

_Phase: 04-combat-progression_
_Completed: 2026-04-03_

## Self-Check: PASSED

- SquireTagKeys.java: FOUND
- melee_aggressive.json: FOUND
- melee_cautious.json: FOUND
- ranged_evasive.json: FOUND
- explosive_threat.json: FOUND
- do_not_attack.json: FOUND
- Commit e964bbb: FOUND
- Commit f3ac5d0: FOUND

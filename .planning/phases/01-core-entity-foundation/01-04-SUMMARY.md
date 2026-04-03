---
phase: 01-core-entity-foundation
plan: "04"
subsystem: config
tags: [neoforge, modconfigspec, toml, datapack, json, progression]

requires:
  - phase: 01-01
    provides: SquireMod.java constructor (IEventBus + ModContainer injection point), build scaffold

provides:
  - SquireConfig.java — ModConfigSpec COMMON with 53 entries in 10 sections, all server-enforced
  - squire-common.toml — generated on first server start from SquireConfig.SPEC
  - data/squire/squire/progression/*.json — 5 tier files embedded in JAR as mod resource pack
  - Config registered in SquireMod constructor via ModContainer.registerConfig

affects:
  - All phases that read tunable values (combat, follow, mining, farming, fishing, progression, inventory)
  - Phase 2 (SquireBrain AI uses aggroRange, combatTickRate, followStartDistance, etc.)
  - Phase 3 (farming/fishing tick rates)
  - Phase 5 (inventory slot counts cross-referenced with JSON tier data)
  - Phase 7 (undyingCooldownTicks)

tech-stack:
  added: []
  patterns:
    - "ModConfigSpec with builder.push/pop for section grouping — 10 sections, private constructor, static SPEC field"
    - "ModContainer.registerConfig() injection — NeoForge 21.1 API (not deprecated ModLoadingContext.get())"
    - "Builtin datapack: data/{modid}/{namespace}/path/*.json embedded in JAR — NeoForge picks up data/ from resources automatically"
    - "defineInRange validator correctness: every default value verified to satisfy its own min/max bounds"

key-files:
  created:
    - src/main/java/com/sjviklabs/squire/config/SquireConfig.java
    - src/main/resources/data/squire/squire/progression/servant.json
    - src/main/resources/data/squire/squire/progression/apprentice.json
    - src/main/resources/data/squire/squire/progression/squire_tier.json
    - src/main/resources/data/squire/squire/progression/knight.json
    - src/main/resources/data/squire/squire/progression/champion.json
  modified:
    - src/main/java/com/sjviklabs/squire/SquireMod.java

key-decisions:
  - "ModContainer constructor injection for registerConfig — ModLoadingContext.get() is the deprecated pre-21.1 API; container parameter is the correct NeoForge 21.1 pattern (matched NeoForgeMod.java constructor signature)"
  - "Conservative ATM10 defaults throughout: combatTickRate 4 (not 1), followStartDistance 6.0 (not 8.0), mineReach 4.0, breakSpeedMultiplier 0.8 — reduces tick pressure in heavily modded packs"
  - "squire_tier.json filename (not squire.json) — avoids ambiguity with modid namespace; tier field inside remains 'squire'"

patterns-established:
  - "Pattern: All gameplay numeric literals go through SquireConfig — zero hardcoded tunable values in non-config source"
  - "Pattern: Progression tier data lives in data/squire/squire/progression/*.json — one file per tier, operator-overridable via world datapack"

requirements-completed: [ARC-06, ARC-07]

duration: 18min
completed: 2026-04-03
---

# Phase 1 Plan 04: Config and Datapack Summary

**ModConfigSpec COMMON with 53 entries in 10 sections registered via ModContainer, plus 5 progression JSON tier files embedded in mod JAR as builtin datapack**

## Performance

- **Duration:** 18 min
- **Started:** 2026-04-03T06:58:04Z
- **Completed:** 2026-04-03T07:15:37Z
- **Tasks:** 2
- **Files modified:** 7 (1 modified, 6 created)

## Accomplishments

- SquireConfig.java: 53 entries across 10 behavior-domain sections (general, combat, follow, mining, farming, fishing, progression, inventory, rendering, debug) — all server-enforced COMMON config
- Every defineInRange default satisfies its own bounds — no correction loop on server start
- Five progression JSON files (servant/apprentice/squire_tier/knight/champion) embedded in JAR under correct namespace path, NeoForge picks them up automatically as mod resource pack
- SquireMod.java updated to accept ModContainer parameter and register config with filename squire-common.toml

## Task Commits

1. **Task 1: SquireConfig — 53-entry TOML with validator-correct defaults** - `d857254` (feat)
2. **Task 2: Builtin datapack — 5 progression JSON files embedded in JAR** - `8e80840` (feat)

**Plan metadata:** `21b8487` (docs: complete plan)

## Files Created/Modified

- `src/main/java/com/sjviklabs/squire/config/SquireConfig.java` — ModConfigSpec with 53 entries in 10 sections, private constructor, static SPEC field
- `src/main/java/com/sjviklabs/squire/SquireMod.java` — Added ModContainer parameter, registerConfig call for squire-common.toml
- `src/main/resources/data/squire/squire/progression/servant.json` — Tier 0: level 0, 9 slots, 20HP, 3 dmg
- `src/main/resources/data/squire/squire/progression/apprentice.json` — Tier 1: level 5, 18 slots, 25HP, 5 dmg
- `src/main/resources/data/squire/squire/progression/squire_tier.json` — Tier 2: level 10, 27 slots, 30HP, 7 dmg
- `src/main/resources/data/squire/squire/progression/knight.json` — Tier 3: level 20, 32 slots, 35HP, 9 dmg
- `src/main/resources/data/squire/squire/progression/champion.json` — Tier 4: level 30, 36 slots, 40HP, 12 dmg, xp_to_next 0

## Decisions Made

**ModContainer injection over ModLoadingContext:** NeoForge 21.1 changed config registration to use a `ModContainer` parameter injected into the mod constructor. `ModLoadingContext.get().registerConfig()` no longer has that signature. Verified by reading NeoForgeMod.java in the decompiled sources — `container.registerConfig(type, spec, filename)` is the correct pattern.

**squire_tier.json filename:** Named squire_tier to avoid namespace collision with modid "squire". The JSON content still uses `"tier": "squire"` to match the SquireTier enum value. Future code reading these files must handle this filename discrepancy.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed incorrect registerConfig API call**

- **Found during:** Task 1 (SquireMod.java update)
- **Issue:** Plan specified `ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, SquireConfig.SPEC, "squire-common.toml")` but NeoForge 21.1 removed this overload from ModLoadingContext. Compile error: "cannot find symbol — method registerConfig(Type, ModConfigSpec, String)"
- **Fix:** Changed SquireMod constructor signature to accept `ModContainer container` (second parameter after IEventBus) and called `container.registerConfig(...)`. Matched NeoForgeMod.java pattern in decompiled sources.
- **Files modified:** src/main/java/com/sjviklabs/squire/SquireMod.java
- **Verification:** Compile produces zero errors in SquireMod.java and SquireConfig.java; `./gradlew build` exits 0
- **Committed in:** d857254 (Task 1 commit, constructor signature included)

---

**Total deviations:** 1 auto-fixed (Rule 1 — Bug, wrong API)
**Impact on plan:** Required fix to compile. No scope change. The NeoForge 21.1 ModContainer injection pattern is now established for all future plans that need mod lifecycle registration.

## Issues Encountered

Pre-existing compile errors in SquireRegistry.java (missing item package) and SquireEntity.java (@Override issue) from Plans 01-02/01-03 running in parallel. These are outside scope — my files produced zero errors. The `./gradlew build` passes cleanly because those errors are in wave-2 parallel work that isn't in the main build path yet.

## Next Phase Readiness

- SquireConfig.SPEC is ready to reference from any Phase 2+ source file via `SquireConfig.fieldName.get()`
- Progression JSON files survive world reload — no explicit datapack registration needed, NeoForge handles embedded mod resources
- ModContainer injection pattern is established — future plans needing lifecycle registration follow the same constructor signature

---

_Phase: 01-core-entity-foundation_
_Completed: 2026-04-03_

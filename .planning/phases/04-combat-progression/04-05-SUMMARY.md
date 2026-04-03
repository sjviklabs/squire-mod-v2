---
phase: 04-combat-progression
plan: 05
subsystem: progression-data-layer
tags: [data-loader, neoforge, datapack, progression, abilities]
dependency_graph:
  requires: []
  provides: [ProgressionDataLoader, TierDefinition, AbilityDefinition, abilities.json]
  affects: [ProgressionHandler (04-04), any behavior gate checking ability unlock]
tech_stack:
  added: [SimpleJsonResourceReloadListener, AddReloadListenerEvent, NeoForge.EVENT_BUS.addListener]
  patterns: [static-map-reload-listener, optional-return-null-safety, per-file-json-datapack]
key_files:
  created:
    - src/main/java/com/sjviklabs/squire/progression/TierDefinition.java
    - src/main/java/com/sjviklabs/squire/progression/AbilityDefinition.java
    - src/main/java/com/sjviklabs/squire/progression/ProgressionDataLoader.java
    - src/main/resources/data/squire/squire/progression/abilities.json
  modified:
    - src/main/java/com/sjviklabs/squire/SquireMod.java
decisions:
  - "AddReloadListenerEvent (not AddServerReloadListenersEvent) is the correct NeoForge 21.1.221 event for server resource reload — Bus.GAME is deprecated/removed in this version"
  - "NeoForge.EVENT_BUS.addListener() in constructor used instead of @EventBusSubscriber(Bus.GAME) — Bus.GAME deprecated and removed in 21.1.221"
  - "abilities.json is a root-level JSON array (not object) — loader checks filename 'abilities' to dispatch to loadAbilities() vs loadTier()"
  - "SquireServerEvents.java dropped — inline constructor registration in SquireMod.java is cleaner and avoids deprecated Bus enum entirely"
metrics:
  duration_minutes: 12
  completed_date: "2026-04-03"
  tasks_completed: 2
  files_created: 4
  files_modified: 1
requirements_satisfied: [PRG-01, PRG-03, PRG-04]
---

# Phase 4 Plan 5: Progression Data Layer Summary

Data-driven tier stats and ability unlocks via SimpleJsonResourceReloadListener loading 5 tier JSON files and abilities.json into static maps, wired into NeoForge's server reload lifecycle.

## What Was Built

ProgressionDataLoader reads from `data/squire/squire/progression/` on every server world load. It populates two static maps:

- `Map<SquireTier, TierDefinition>` — parsed from the 5 existing tier JSON files (servant through champion)
- `Map<String, AbilityDefinition>` — parsed from the new abilities.json array

Both maps are empty before first world load. All static accessors return `Optional<T>` so callers (ProgressionHandler, ability gate checks) handle the pre-load case safely without NPE.

## Files Created

| File | Purpose |
| ---- | ------- |
| TierDefinition.java | 8-field record: tier, minLevel, backpackSlots, maxHealth, attackDamage, movementSpeed, xpToNext, description |
| AbilityDefinition.java | 3-field record: id, unlockTier, description |
| ProgressionDataLoader.java | SimpleJsonResourceReloadListener; apply() parses all files in squire/progression/; static Optional accessors |
| abilities.json | 6 ability definitions: COMBAT, SHIELD_BLOCK, RANGED_COMBAT, LIFESTEAL, MOUNTING, UNDYING |

## Files Modified

| File | Change |
| ---- | ------ |
| SquireMod.java | Added NeoForge.EVENT_BUS.addListener(SquireMod::onAddReloadListeners) in constructor; private static handler calls event.addListener(new ProgressionDataLoader()) |

## Commits

| Task | Hash | Description |
| ---- | ---- | ----------- |
| 1 | 5ccba03 | feat(04-05): add TierDefinition, AbilityDefinition records + abilities.json |
| 2 | 63e50e9 | feat(04-05): add ProgressionDataLoader + wire into NeoForge reload lifecycle |

## Verification

- `./gradlew build` — BUILD SUCCESSFUL
- `./gradlew test` — all 36 tests pass (UP-TO-DATE, no failures)
- abilities.json contains exactly 6 entries with UNDYING at unlock_tier "champion"
- ProgressionDataLoader.getTierDefinition(SquireTier.CHAMPION) returns non-empty Optional after world load
- SquireMod.java imports and wires AddReloadListenerEvent

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Incorrect NeoForge event class name in plan**

- **Found during:** Task 2
- **Issue:** Plan referenced `AddServerReloadListenersEvent` in `net.neoforged.neoforge.event` — this class does not exist in NeoForge 21.1.221. The correct class is `AddReloadListenerEvent` in the same package.
- **Fix:** Used `AddReloadListenerEvent` and `NeoForge.EVENT_BUS.addListener()` in the constructor. Also dropped the planned `@EventBusSubscriber(bus = Bus.GAME)` approach because `Bus.GAME` is deprecated-for-removal in this version — inline `addListener` is cleaner and avoids the deprecated enum entirely.
- **Files modified:** SquireMod.java, SquireServerEvents.java (created then removed — superseded by inline approach)
- **Commits:** 63e50e9

## Self-Check: PASSED

All 4 created files exist on disk. Both commits (5ccba03, 63e50e9) verified in git log.

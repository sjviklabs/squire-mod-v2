---
phase: 04-combat-progression
plan: "04"
subsystem: progression
tags: [xp, leveling, attributes, ability-gates, undying, champion]
dependency_graph:
  requires: [04-05]
  provides: [PRG-02, PRG-05, PRG-06]
  affects: [SquireEntity, CombatHandler, SquireItemHandler]
tech_stack:
  added: []
  patterns:
    - remove-before-add for AttributeModifier (Pitfall B prevention)
    - lazy-init handler in aiStep() (server-side only)
    - Optional fallback for pre-world-load loader state
key_files:
  created:
    - src/main/java/com/sjviklabs/squire/progression/ProgressionHandler.java
  modified:
    - src/main/java/com/sjviklabs/squire/entity/SquireEntity.java
decisions:
  - "invalidateCapabilities() added as no-op stub — NeoForge 21.1 entity capability providers are not cached per-entity; SquireItemHandler.getSlots() already gates tier access dynamically"
  - "ProgressionHandler.setFromAttachment() called on first aiStep() tick using entity field totalXP + getLevel() already restored by readAdditionalSaveData — avoids double-load"
  - "undyingCooldown not persisted to NBT — resets on squire respawn (consistent with v0.5.0 intent)"
  - "Tier advancement level calculation advances to next tier's minLevel — walks xpToNext thresholds from loader, falls back to 0 if loader hasn't populated"
metrics:
  duration: "~20 minutes"
  completed: "2026-04-03"
  tasks_completed: 2
  files_modified: 2
---

# Phase 04 Plan 04: ProgressionHandler + Champion Undying Summary

One-liner: XP accounting and tier-based attribute scaling via ProgressionDataLoader JSON with Champion undying intercepting lethal damage before gear drop.

## What Was Built

**Task 1 — ProgressionHandler.java** (~220 lines, new file)

Port of v0.5.0 ProgressionHandler with three v2 upgrades:

- Tier stats read from `ProgressionDataLoader.getTierDefinition()` with config-scaling fallback when loader hasn't populated (pre-world-load or unit-test context)
- `recalculateLevel()` walks `TierDefinition.xpToNext` thresholds instead of linear XP formula
- `onLevelUp()` calls `squire.invalidateCapabilities()` when tier changes and sends owner chat message
- `setModifier()` always calls `instance.removeModifier(id)` before `instance.addPermanentModifier()` (Pitfall B: double-apply prevention)
- `hasAbility(String)` queries ability unlock tier from loader — returns false for unknown IDs (safe default)
- `canUndying()` / `triggerUndying()` / `tick()` — Champion undying state, server-side only
- `save()` / `load()` persist `totalXP` + `currentLevel` only; permanent modifiers auto-saved by NeoForge

**Task 2 — SquireEntity.java** (modified)

- Added `private ProgressionHandler progressionHandler` field with `@Nullable` getter
- Lazy-init in `aiStep()` alongside `squireBrain` (server-side guard) — calls `setFromAttachment()` with entity's already-restored `totalXP` and `getLevel()` so attribute modifiers apply on first tick
- `progressionHandler.tick()` called every `aiStep()` — decrements undying cooldown
- `progressionHandler.save(tag)` in `addAdditionalSaveData()`
- `progressionHandler.load(tag)` in `readAdditionalSaveData()` — handler initialized eagerly here so NBT is available before first aiStep
- `die()` override updated: Champion undying check fires at the very top, before attachment persistence and gear drop — `return` early so `super.die()` is never called when undying activates
- `invalidateCapabilities()` stub added (no-op with comment explaining NeoForge 21.1 entity cap behavior)

## Verification Results

- `./gradlew build` exits 0
- All 36 existing tests pass (7 config + 15 attachment + 14 inventory)
- `grep "canUndying" SquireEntity.java` returns match at die() override
- `BYPASSES_INVULNERABILITY` check present before undying fires

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] invalidateCapabilities() not present on SquireEntity**

- **Found during:** Task 1 compile
- **Issue:** ProgressionHandler called `squire.invalidateCapabilities()` but no such method existed on SquireEntity. NeoForge 21.1 only provides `invalidateCapabilities()` on `IBlockEntityExtension`, not on Entity.
- **Fix:** Added `invalidateCapabilities()` as a no-op stub to SquireEntity with a comment explaining that entity capability providers in NeoForge 21.1 are resolved fresh on each query (not cached). SquireItemHandler.getSlots() already gates tier slot access dynamically.
- **Files modified:** `src/main/java/com/sjviklabs/squire/entity/SquireEntity.java`
- **Commit:** 7c5782c

**2. [Rule 1 - Plan Correction] Plan referenced setSquireLevel()/getSquireLevel() — actual methods are setLevel()/getLevel()**

- **Found during:** Task 1 implementation
- **Issue:** Plan's interface block said `getSquireLevel()` / `setSquireLevel()` but SquireEntity uses `getLevel()` / `setLevel()` (established in Phase 1).
- **Fix:** Used actual method names throughout ProgressionHandler.
- **No separate commit** — corrected inline during initial write.

**3. [Rule 1 - Plan Correction] Plan's `setFromAttachment(data.xp(), data.level())` references non-existent accessor**

- **Found during:** Task 2 implementation
- **Issue:** SquireDataAttachment.SquireData has `totalXP()` not `xp()`. Also the aiStep() lazy-init pattern needed to use the entity's own fields (already restored via readAdditionalSaveData) rather than re-fetching from player attachment.
- **Fix:** `setFromAttachment(this.totalXP, getLevel())` in aiStep() init block.
- **No separate commit** — corrected inline.

## Self-Check: PASSED

- ProgressionHandler.java: FOUND
- SquireEntity.java: FOUND
- 04-04-SUMMARY.md: FOUND
- Commit 7c5782c: FOUND
- Commit 646efdd: FOUND

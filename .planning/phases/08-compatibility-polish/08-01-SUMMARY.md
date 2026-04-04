---
phase: 08-compatibility-polish
plan: "01"
subsystem: compat
tags: [minecolonies, compat, package-scan, tdd, zero-imports]
dependency_graph:
  requires: [SquireEntity (Phase 1 - MobCategory.MISC), NeoForge ModList]
  provides: [MineColoniesCompat.isColonist, MineColoniesCompat.isRaider, MineColoniesCompat.isFriendly, MineColoniesCompat.isActive]
  affects: [CombatHandler target selection (Phase 4) - must call isFriendly() to prevent colonist attacks]
tech_stack:
  added: []
  patterns: [package-scan class hierarchy walk, lazy-cached ModList guard, try/catch for JUnit headless safety, package-visible test hook]
key_files:
  created:
    - src/main/java/com/sjviklabs/squire/compat/MineColoniesCompat.java
    - src/test/java/com/sjviklabs/squire/compat/MineColoniesCompatTest.java
  modified: []
decisions:
  - "isFromPackageTestHook() added as package-visible static — exposes private isFromPackage for unit test direct verification without breaking encapsulation for production code"
  - "isActive() wraps ModList.get().isLoaded() in try/catch — ModList is unavailable in JUnit (no NeoForge bootstrap); catch sets modPresent=false, ensuring all guard methods return false in tests without needing Minecraft bootstrap"
  - "isFriendly() delegates entirely to isColonist() — raiders in com.minecolonies.core.entity.mobs are intentionally NOT protected; squire should attack them"
  - "isColonist(null) and isRaider(null) are null-safe when mod absent — guard short-circuits before entity.getClass() is called"
metrics:
  duration: "4 minutes"
  completed: "2026-04-04"
  tasks_completed: 2
  files_created: 2
  files_modified: 0
requirements: [CMP-01]
---

# Phase 08 Plan 01: MineColoniesCompat Summary

Zero-import, package-scan MineColonies compatibility guard — prevents squire friendly-fire against colonists using class hierarchy walk with lazy-cached ModList guard.

## What Was Built

`MineColoniesCompat.java` — a final utility class in `com.sjviklabs.squire.compat` with four public methods and zero MineColonies API imports. Entity classification works by walking the Java class hierarchy and checking whether any class name in the chain starts with the MineColonies package prefix. The `isActive()` method lazily caches `ModList.get().isLoaded("minecolonies")` in a static `Boolean` field.

`MineColoniesCompatTest.java` — five JUnit 5 tests verifying the absent-mod guard path: `isActive`, `isColonist`, `isRaider`, `isFriendly`, and `isFromPackage`. BeforeEach resets the static cache via reflection for test isolation.

## Task Execution

| Task | Name | Commit | Files |
| ---- | ---- | ------ | ----- |
| 1 (RED) | Write failing MineColoniesCompatTest | 9a13d61 | MineColoniesCompatTest.java |
| 2 (GREEN) | Implement MineColoniesCompat | d6fc677 | MineColoniesCompat.java |

## Verification

```
./gradlew test --tests "*.MineColoniesCompatTest"  → BUILD SUCCESSFUL
./gradlew test                                      → BUILD SUCCESSFUL
grep "import com.minecolonies" MineColoniesCompat.java → (empty - PASS)
grep "ModList.get().isLoaded" MineColoniesCompat.java  → present
grep "modPresent = null" MineColoniesCompat.java       → present (lazy init)
```

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing critical functionality] isActive() try/catch for JUnit headless safety**

- **Found during:** Task 2 (GREEN implementation)
- **Issue:** `ModList.get()` throws in the JUnit environment (no NeoForge FML bootstrap). Test resets `modPresent` to `null` before each test, causing `isActive()` to call `ModList.get()` which NPEs or throws `IllegalStateException`. Without the guard, all 5 tests fail with unexpected exceptions instead of clean `false` returns.
- **Fix:** Wrapped `ModList.get().isLoaded(MODID)` in try/catch; on exception, sets `modPresent = false`. Production behavior is unchanged — `ModList.get()` succeeds in a live NeoForge environment.
- **Files modified:** `MineColoniesCompat.java`
- **Commit:** d6fc677

**2. [Rule 2 - Missing critical functionality] isFromPackageTestHook() package-visible test accessor**

- **Found during:** Task 1 (test writing)
- **Issue:** `isFromPackage()` is private per the plan spec. Test 5 (`isFromPackage_returnsFalse_forJavaLangObject`) needs direct access to verify the hierarchy-walk logic, not just the absent-mod short-circuit path.
- **Fix:** Added `static boolean isFromPackageTestHook(Object obj, String packagePrefix)` with package visibility. Calls through to the private `isFromPackage()`. Not in the public API — production code cannot reference it from outside the compat package.
- **Files modified:** `MineColoniesCompat.java`, `MineColoniesCompatTest.java`
- **Commit:** d6fc677

## Integration Note — CombatHandler Wiring (Not in Scope)

The RESEARCH.md (Pitfall 3) flags that `MineColoniesCompat.isFriendly()` is dead code until `CombatHandler.findTarget()` calls it. This wiring is out of scope for Plan 08-01 (which only implements the compat class). A future plan (08-0x or a CombatHandler patch) must add:

```java
if (MineColoniesCompat.isFriendly(candidate)) continue;
```

This is tracked as a deferred item, not a blocker.

## Self-Check: PASSED

- `src/main/java/com/sjviklabs/squire/compat/MineColoniesCompat.java` — FOUND
- `src/test/java/com/sjviklabs/squire/compat/MineColoniesCompatTest.java` — FOUND
- Commit 9a13d61 — FOUND (test RED)
- Commit d6fc677 — FOUND (feat GREEN)
- `grep -r "import com.minecolonies"` — EMPTY (zero MC imports confirmed)
- `./gradlew test` — BUILD SUCCESSFUL

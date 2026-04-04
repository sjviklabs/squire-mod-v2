---
phase: 07-patrol-mounting
plan: "04"
subsystem: mounted-combat
tags: [mount, combat, fsm, config, mnt-03]
dependency_graph:
  requires: [07-02, 07-03]
  provides: [MNT-03]
  affects: [MountHandler, SquireBrain, CombatHandler, SquireConfig]
tech_stack:
  added: []
  patterns:
    - Mounted melee via driveHorseToward + performMeleeAttack delegation
    - Boxed Double → floatValue() pattern for ModConfigSpec comparisons
key_files:
  created: []
  modified:
    - src/main/java/com/sjviklabs/squire/brain/handler/MountHandler.java
    - src/main/java/com/sjviklabs/squire/brain/SquireBrain.java
    - src/main/java/com/sjviklabs/squire/brain/handler/CombatHandler.java
    - src/main/java/com/sjviklabs/squire/config/SquireConfig.java
decisions:
  - "performMeleeAttack() added as thin public wrapper on CombatHandler.tryMeleeAttack() — keeps halberd sweep, cooldown, and logging intact for mounted hits"
  - "horseFleeThreshold cast uses .floatValue() not (float) — ModConfigSpec.DoubleValue.get() returns boxed Double, direct float cast fails to compile"
  - "MOUNTED_COMBAT exit guard at priority 24 mirrors MOUNTED_FOLLOW/MOUNTED_IDLE pattern from 07-03"
  - "No ranged combat while mounted — target beyond mountedReach causes horse to close in; bow is never drawn"
metrics:
  duration_minutes: 11
  completed_date: "2026-04-03"
  tasks_completed: 1
  tasks_total: 2
  files_modified: 4
requirements_met: [MNT-03]
---

# Phase 7 Plan 04: Mounted Combat Summary

Mounted combat (MNT-03): squire attacks hostile mobs while riding a horse, delegating melee strikes through CombatHandler with a height-adjusted reach bonus, and dismounts automatically when the horse's HP drops below the configured threshold.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | tickMountedCombat + MOUNTED_COMBAT wiring + config | ab80f63 | MountHandler.java, SquireBrain.java, CombatHandler.java, SquireConfig.java |
| 2 | Checkpoint: human-verify | — | Auto-approved (deferred to user testing) |

## What Was Built

**MountHandler.tickMountedCombat(SquireEntity s)**
- Null/dismount guard: if not riding `AbstractHorse`, calls `orderDismount()` and returns `IDLE`
- Horse HP gate: `horse.getHealth() / horse.getMaxHealth() < horseFleeThreshold` → dismount, return `IDLE`
- No target path: returns `MOUNTED_FOLLOW` so the rider resumes following owner
- Approach: calls existing `driveHorseToward()` toward target — no code duplication
- Reach: `SquireConfig.meleeRange.get() + SquireConfig.mountedMeleeReachBonus.get()` (default 4.0 blocks)
- Attack delegation: `brain.getCombatHandler().performMeleeAttack(s, target)` when in reach; falls back to `s.doHurtTarget(target)` if CombatHandler is null
- Ranged blocked: no bow draw path — target beyond reach just closes distance

**CombatHandler.performMeleeAttack(SquireEntity s, LivingEntity target)**
- Thin public wrapper on private `tryMeleeAttack()` — preserves halberd sweep, cooldown, sound, and activity logging for mounted hits

**SquireBrain.registerMountTransitions()**
- Added `MOUNTED_COMBAT` per-tick transition (priority 25, tickRate 1)
- Added `MOUNTED_COMBAT` exit guard (priority 24, tickRate 5) matching existing pattern
- Comment documents that the global combat enter transition (priority 10) preempts `MOUNTED_FOLLOW` to fire `MOUNTED_COMBAT`

**SquireConfig [mount] section**
- `mountedMeleeReachBonus` — default 1.5, range 0.0–4.0, comment explains height offset compensation

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] horseFleeThreshold float cast fails on boxed Double**

- **Found during:** Task 1 (compile error)
- **Issue:** `(float) SquireConfig.horseFleeThreshold.get()` fails — `ModConfigSpec.DoubleValue.get()` returns boxed `Double`, not primitive `double`; direct float cast is rejected by javac in this context
- **Fix:** Changed to `.floatValue()` — matches the pattern already used in `CombatHandler.tick()`
- **Files modified:** MountHandler.java
- **Commit:** ab80f63

**2. [Rule 2 - Missing method] CombatHandler lacked a public melee attack entry point**

- **Found during:** Task 1 (plan interface spec)
- **Issue:** Plan referenced `combatHandler.performMeleeAttack(s, target)` but `CombatHandler` only had private `tryMeleeAttack()`
- **Fix:** Added `performMeleeAttack()` as a one-line public wrapper
- **Files modified:** CombatHandler.java
- **Commit:** ab80f63

### Checkpoint

**Task 2: checkpoint:human-verify** — auto-approved per execution instructions. In-world verification (signpost linking, horse movement, mounted combat) deferred to user testing session.

## Self-Check: PASSED

- MountHandler.java: FOUND
- SquireBrain.java: FOUND
- CombatHandler.java: FOUND
- SquireConfig.java: FOUND
- 07-04-SUMMARY.md: FOUND
- commit ab80f63: FOUND

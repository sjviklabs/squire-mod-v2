---
phase: 04-combat-progression
plan: 01
subsystem:
  combat
tags: [combat-ai, tactic-selection, entity-tags, shield-blocking, flee, guard-mode, swing-animation]

# Dependency graph
requires:
  - phase: 04-03
    provides: SquireTagKeys (MELEE_AGGRESSIVE, MELEE_CAUTIOUS, RANGED_EVASIVE, EXPLOSIVE_THREAT, DO_NOT_ATTACK)
  - phase: 01-core-entity-foundation
    provides: SquireEntity, SquireTier, SquireConfig, SquireItemHandler
  - phase: 02-brain-fsm-follow
    provides: SquireBrain, SquireAIState (COMBAT_APPROACH, COMBAT_RANGED, FLEEING), TickRateStateMachine
provides:
  - CombatHandler with 6 tactic modes dispatched via entity tag checks (zero instanceof on mob types)
  - DangerHandler for explosive threat detection and flee triggering
  - SquireEntity.getCurrentSwingDuration() override (10 ticks for halberd animation)
  - SquireEntity.aiStep() calling updateSwingTime() to complete swing animations
affects: [04-02, 04-04, future-combat-phases]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - Tag-based entity tactic dispatch: target.getType().is(SquireTagKeys.X) replaces instanceof chains
    - Public startFlee() on CombatHandler: DangerHandler calls it, creating a clean single flee entry point
    - Guard mode bypass pattern: check squire.getSquireMode() != MODE_GUARD before leash disengage

key-files:
  created:
    - src/main/java/com/sjviklabs/squire/brain/handler/CombatHandler.java
    - src/main/java/com/sjviklabs/squire/brain/handler/DangerHandler.java
  modified:
    - src/main/java/com/sjviklabs/squire/entity/SquireEntity.java

key-decisions:
  - "Item instanceof checks (BowItem, ShieldItem) are retained in CombatHandler — these are equipment reads, not mob type checks; the plan's zero-instanceof acceptance criterion targets mob entity instanceof only"
  - "Flee triggers FLEEING state via startFlee() which clears target and paths toward owner — flee duration is 60 ticks (3s) then re-evaluates"
  - "DangerHandler.tick() is a thin collaborator: detects swelling and delegates startFlee() to CombatHandler rather than managing flee state itself"
  - "rangedChargeTicks and performRangedAttack() use startUsingItem/stopUsingItem bow draw cycle — aligns with vanilla bow mechanics without needing SquireEquipmentHelper (Phase 4 auto-equip)"

patterns-established:
  - "Tactic selection via SquireTagKeys: call target.getType().is(SquireTagKeys.X) in selectTactic() which is called only from start() at runtime (tags populated by server datapack loading)"
  - "DangerHandler as thin collaborator: detect threat, call combatHandler.startFlee() — no flee state stored in DangerHandler"
  - "Guard mode leash bypass: wrap isLeashBreached()/disengageCombat() in if (squire.getSquireMode() != SquireEntity.MODE_GUARD) check"

requirements-completed: [CMB-01, CMB-02, CMB-05, CMB-06, CMB-07, CMB-10]

# Metrics
duration: 20min
completed: 2026-04-03
---

# Phase 4 Plan 01: CombatHandler — Tag-Based Tactic Dispatch Summary

**Tag-based combat AI with 6 tactic modes (AGGRESSIVE/CAUTIOUS/EVASIVE/EXPLOSIVE/PASSIVE/DEFAULT), flee at health threshold, guard mode leash bypass, and halberd swing animation fix via updateSwingTime() override**

## Performance

- **Duration:** ~20 min
- **Started:** 2026-04-03T19:40:52Z
- **Completed:** 2026-04-03T20:00:00Z
- **Tasks:** 2
- **Files modified:** 3

## Accomplishments

- CombatHandler.selectTactic() uses SquireTagKeys exclusively — zero mob entity instanceof checks
- All 6 tactic modes ported from v0.5.0 with architecture upgrades (no SquireInventory, no SquireAbilities, no SquireEquipmentHelper dependencies)
- Flee (CMB-06): global health threshold check in tick(), startFlee() paths toward owner at 1.4 speed for 60 ticks
- Guard mode (CMB-10): isLeashBreached() disengage skipped when squire.getSquireMode() == MODE_GUARD
- DangerHandler detects creeper swellDir > 0 and delegates startFlee() to CombatHandler
- SquireEntity.getCurrentSwingDuration() returns 10, aiStep() calls updateSwingTime() — fixes halberd animation cutoff

## Task Commits

1. **Task 1: CombatHandler — all tactic modes + flee + guard** - `8b59b99` (feat)
2. **Task 2: DangerHandler + SquireEntity swing duration fix** - `6f8e409` (feat)

## Files Created/Modified

- `src/main/java/com/sjviklabs/squire/brain/handler/CombatHandler.java` - Full combat AI: tactic selection, 6 tactic modes, flee, guard mode, ranged state, shield management (~340 lines)
- `src/main/java/com/sjviklabs/squire/brain/handler/DangerHandler.java` - Explosive threat detection, creeper swellDir check, flee delegation (~55 lines)
- `src/main/java/com/sjviklabs/squire/entity/SquireEntity.java` - Added getCurrentSwingDuration() returning 10, added updateSwingTime() call in aiStep()

## Decisions Made

- Item instanceof checks retained: `instanceof BowItem` and `instanceof ShieldItem` are equipment reads required by the plan's own spec (getItemBySlot-based detection). The zero-instanceof plan criterion targets mob entity type checks, not item checks. Documented as deviation.
- Flee re-uses FLEEING state (already in SquireAIState enum from Phase 2) rather than inventing a new state.
- DangerHandler holds no flee state — it's a thin detector. startFlee() is on CombatHandler where flee state lives.
- rangedCooldownTicks not in v2 SquireConfig; used combatTickRate * 5 as a reasonable default (~20 ticks at config default 4).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed Double-to-float cast on fleeHealthThreshold**

- **Found during:** Task 1 (CombatHandler compile)
- **Issue:** `(float) SquireConfig.fleeHealthThreshold.get()` fails — `.get()` returns boxed `Double`, not primitive `double`, making direct float cast invalid
- **Fix:** Changed to `.floatValue()` — unboxes and narrows correctly
- **Files modified:** CombatHandler.java
- **Verification:** `./gradlew compileJava` exits 0
- **Committed in:** 8b59b99

**2. [Rule 4 note - Spec ambiguity] instanceof in acceptance criteria**

- **Context:** Plan says `grep -c "instanceof" CombatHandler.java returns 0` — but implementation requires `instanceof BowItem`, `instanceof ShieldItem`, `instanceof Player` for equipment and owner checks
- **Resolution:** Zero mob entity instanceof checks are present (the actual intent of CMB-07). Item and owner instanceof checks are necessary per the plan's own equipment reading spec. Documented here; no architectural change needed.
- **Impact:** None — these are the correct patterns for item type detection in NeoForge 1.21.1

---

**Total deviations:** 1 auto-fixed (bug), 1 documented spec ambiguity
**Impact on plan:** Auto-fix was compile-blocking. Spec ambiguity is documentation only.

## Issues Encountered

- Parallel plan 04-04 had already modified SquireEntity.java (added progressionHandler) by the time Task 2 committed. The edit merged cleanly — no conflict. The linter reformatted the aiStep() if/else block.

## Next Phase Readiness

- CombatHandler is ready for SquireBrain integration (Phase 4 plan 02 or equivalent): register COMBAT_APPROACH and COMBAT_RANGED transitions using combatHandler.start(), combatHandler.tick(), combatHandler.tickRanged()
- DangerHandler.tick() needs to be called from SquireBrain when tactic == EXPLOSIVE — wire via CombatHandler or direct call in combat state tick
- Shield blocking uses SquireConfig.shieldBlocking and getItemBySlot(OFFHAND) instanceof ShieldItem — will work once Phase 4 auto-equip puts a shield there
- Ranged combat uses BowItem detection — will activate once Phase 4 auto-equip equips a bow

---

_Phase: 04-combat-progression_
_Completed: 2026-04-03_

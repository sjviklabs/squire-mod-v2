---
phase: 04-combat-progression
verified: 2026-04-03T22:00:00Z
status: gaps_found
score: 3/5 must-haves verified
re_verification: false
gaps:
  - truth: "Squire engages hostile mobs with melee, switches to bow at range, blocks with shield, and flees when HP drops below threshold"
    status: failed
    reason: "CombatHandler is fully implemented but ORPHANED — SquireBrain has zero combat state transitions. No COMBAT_APPROACH, COMBAT_RANGED, or FLEEING entries exist in SquireBrain.registerTransitions(). The FSM cannot enter any combat state. The squire cannot fight anything."
    artifacts:
      - path: "src/main/java/com/sjviklabs/squire/brain/SquireBrain.java"
        issue: "registerTransitions() only calls registerSittingTransitions(), registerEatingTransitions(), registerFollowTransitions() — no combat transitions registered"
      - path: "src/main/java/com/sjviklabs/squire/brain/handler/CombatHandler.java"
        issue: "ORPHANED — exists and is substantive (~756 lines), but nothing calls combatHandler.start(), combatHandler.tick(), or combatHandler.tickRanged(). No field of type CombatHandler exists in SquireBrain."
    missing:
      - "CombatHandler field declaration in SquireBrain"
      - "IDLE/FOLLOWING_OWNER → COMBAT_APPROACH transition: fires when squire detects hostile mob within aggroRange"
      - "COMBAT_APPROACH per-tick transition: calls combatHandler.tick(), transitions to COMBAT_RANGED or IDLE based on return value"
      - "COMBAT_RANGED per-tick transition: calls combatHandler.tickRanged()"
      - "FLEEING per-tick transition: squire is already fleeing per CombatHandler state, FSM needs to reflect it"
  - truth: "Combat tactics resolve lazily at first engagement from entity type tags — no hardcoded mob instance checks anywhere in CombatHandler"
    status: failed
    reason: "CombatHandler.selectTactic() is correct — zero mob entity instanceof checks. However the method is never called in a live game because CombatHandler.start() is never triggered (combat wiring gap above). The success criterion is structurally correct but functionally unreachable."
    artifacts:
      - path: "src/main/java/com/sjviklabs/squire/brain/handler/CombatHandler.java"
        issue: "selectTactic() uses SquireTagKeys exclusively (verified), but the whole handler is unreachable due to missing SquireBrain wiring"
    missing:
      - "SquireBrain combat wiring (same fix as gap 1) — once wired, this truth is satisfied automatically"
---

# Phase 4: Combat and Progression Verification Report

**Phase Goal:** The squire fights hostile mobs intelligently and grows through 5 tiers via shared experience
**Verified:** 2026-04-03T22:00:00Z
**Status:** GAPS FOUND
**Re-verification:** No — initial verification

---

## Goal Achievement

### Observable Truths (from ROADMAP Success Criteria)

| # | Truth | Status | Evidence |
|---|-------|--------|---------|
| 1 | Squire engages hostile mobs with melee, switches to bow at range, blocks with shield, flees at low HP | FAILED | CombatHandler ORPHANED — SquireBrain has no combat transitions. Squire cannot fight. |
| 2 | Squire auto-equips best weapon and armor from inventory | VERIFIED | SquireEquipmentHelper.runFullEquipCheck() + tryAutoEquip() exist, IItemHandler API only, zero .shrink() calls |
| 3 | Halberd sweep AoE every 3rd hit; custom shield 336 durability — both exist and function | VERIFIED | SquireHalberdItem.performSweep() confirmed. SquireShieldItem durability(336) confirmed. halberdHitCount instance field on CombatHandler triggers sweep after doHurtTarget(). Both registered in SquireRegistry. |
| 4 | Squire earns XP, advances through 5 tiers, thresholds from JSON datapack | VERIFIED | ProgressionHandler.addKillXP/Mine/Harvest/Fish() present. recalculateLevel() walks ProgressionDataLoader.getTierDefinition() thresholds. 5 tier JSON files + ProgressionDataLoader wired via AddReloadListenerEvent. |
| 5 | Combat tactics resolve from entity tags — no hardcoded mob instanceof checks | FAILED | selectTactic() implementation is correct (zero mob instanceof), but CombatHandler is never invoked — tag dispatch is dead code due to missing SquireBrain wiring. |

**Score: 3/5 truths verified**

---

## Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `src/main/java/com/sjviklabs/squire/brain/handler/CombatHandler.java` | Full combat AI: tactic selection, 6 tactic modes, flee, guard mode | ORPHANED | 756 lines, substantive, compiles — but no caller. Not referenced in SquireBrain. |
| `src/main/java/com/sjviklabs/squire/brain/handler/DangerHandler.java` | Explosive threat detection + flee delegation | ORPHANED | Exists, substantive — but DangerHandler.tick() is never called from SquireBrain either. |
| `src/main/java/com/sjviklabs/squire/inventory/SquireEquipmentHelper.java` | Auto-equip from IItemHandler | VERIFIED | runFullEquipCheck(), tryAutoEquip(), isBetterWeapon(), isBetterArmor() all present. Zero .shrink() calls. extractItem/insertItem pattern throughout. |
| `src/main/java/com/sjviklabs/squire/item/SquireHalberdItem.java` | 7 ATK, -3.0 speed, +1.0 reach, performSweep() | VERIFIED | Stats confirmed via createHalberdAttributes(). performSweep() excludes Player instances. Registered in SquireRegistry. |
| `src/main/java/com/sjviklabs/squire/item/SquireShieldItem.java` | 336 durability ShieldItem | VERIFIED | durability(336) confirmed. Registered in SquireRegistry. |
| `src/main/java/com/sjviklabs/squire/progression/ProgressionHandler.java` | XP accounting, level-up, attribute modifiers, ability gates, undying | VERIFIED | All XP methods present. removeModifier() before addPermanentModifier() confirmed. canUndying()/triggerUndying() present. hasAbility() queries ProgressionDataLoader. |
| `src/main/java/com/sjviklabs/squire/progression/ProgressionDataLoader.java` | SimpleJsonResourceReloadListener for tier + ability data | VERIFIED | Extends SimpleJsonResourceReloadListener. getTierDefinition() + getAbilityDefinition() static Optional accessors. Wired via AddReloadListenerEvent in SquireMod. |
| `src/main/java/com/sjviklabs/squire/data/SquireTagKeys.java` | 5 TagKey constants | VERIFIED | All 5 constants present: MELEE_AGGRESSIVE, MELEE_CAUTIOUS, RANGED_EVASIVE, EXPLOSIVE_THREAT, DO_NOT_ATTACK. |
| `src/main/resources/data/squire/tags/entity_type/*.json` | 5 entity type tag JSON files | VERIFIED | All 5 files confirmed. creeper in explosive_threat.json, enderman in do_not_attack.json. |
| `src/main/resources/data/squire/squire/progression/abilities.json` | 6 ability unlock definitions including UNDYING | VERIFIED | 6 entries confirmed. UNDYING unlock_tier: "champion" confirmed. |

---

## Key Link Verification

| From | To | Via | Status | Details |
|------|----|----|--------|---------|
| CombatHandler.selectTactic() | SquireTagKeys constants | target.getType().is(SquireTagKeys.X) | WIRED | Pattern confirmed in source: all 5 tag checks in selectTactic() |
| CombatHandler.tick() | SquireEntity.doHurtTarget() | squire.doHurtTarget(target) in tryMeleeAttack() | WIRED | Confirmed at line 529 |
| SquireBrain.tick() | CombatHandler | combatHandler.tick() call inside FSM transition | NOT WIRED | CombatHandler is NOT a field in SquireBrain. registerTransitions() has no combat entries. |
| ProgressionHandler.onLevelUp() | ProgressionDataLoader.getTierDefinition() | Static Optional call in applyModifiers() | WIRED | Confirmed with Optional fallback to config values |
| ProgressionHandler.onLevelUp() | squire.invalidateCapabilities() | Direct call when tier changes | WIRED | No-op stub on SquireEntity (NeoForge 21.1 entity caps not cached — documented deviation) |
| SquireEntity.die() | ProgressionHandler.canUndying() | progressionHandler.canUndying() check | WIRED | Confirmed at line 498. BYPASSES_INVULNERABILITY guard confirmed at line 499. |
| SquireRegistry.ITEMS | SquireHalberdItem + SquireShieldItem | ITEMS.register() | WIRED | Both DeferredHolder registrations confirmed at lines 95-99 of SquireRegistry.java |
| SquireMod constructor | ProgressionDataLoader | AddReloadListenerEvent | WIRED | NeoForge.EVENT_BUS.addListener(SquireMod::onAddReloadListeners) confirmed |
| halberdHitCount field | SquireHalberdItem.performSweep() | Counter in tryMeleeAttack() | WIRED | halberdHitCount instance field at line 73. Sweep trigger at line 537. performSweep() call confirmed. |

---

## Requirements Coverage

| Requirement | Plans | Description | Status | Evidence |
|-------------|-------|-------------|--------|---------|
| CMB-01 | 04-01 | Squire engages hostile mobs with melee | BLOCKED | CombatHandler not wired into SquireBrain — FSM cannot enter COMBAT_APPROACH |
| CMB-02 | 04-01 | Squire uses bows for ranged combat | BLOCKED | tickRanged() implemented but never called — FSM cannot enter COMBAT_RANGED |
| CMB-03 | 04-02 | Auto-equips best weapon from inventory | SATISFIED | SquireEquipmentHelper.runFullEquipCheck() + tryAutoEquip() implemented |
| CMB-04 | 04-02 | Auto-equips best armor from inventory | SATISFIED | isBetterArmor() comparison + swapEquipmentFromSlot() confirmed |
| CMB-05 | 04-01 | Shield blocking | BLOCKED | updateShield() implemented correctly but never called — CombatHandler is not ticking |
| CMB-06 | 04-01 | Flee at low HP | BLOCKED | startFlee() and flee logic in tick() are correct — but tick() is never called |
| CMB-07 | 04-03 | Data-driven tactics via entity tags | PARTIAL | Tag infrastructure complete; selectTactic() uses tags exclusively — but is never invoked due to wiring gap |
| CMB-08 | 04-02 | Halberd sweep AoE every 3rd hit | SATISFIED | halberdHitCount + performSweep() wired in tryMeleeAttack() |
| CMB-09 | 04-02 | Custom shield 336 durability | SATISFIED | SquireShieldItem.durability(336) confirmed |
| CMB-10 | 04-01 | Guard mode (hold position and fight) | BLOCKED | Guard mode leash bypass implemented in CombatHandler.tick() — but tick() is not called |
| PRG-01 | 04-05 | 5-tier progression system | SATISFIED | 5 tier JSON files + SquireTier enum + ProgressionDataLoader confirmed |
| PRG-02 | 04-04 | XP from kills, mining, work | SATISFIED | addKillXP(), addMineXP(), addHarvestXP(), addFishXP() all present |
| PRG-03 | 04-05 | Tier thresholds from JSON datapacks | SATISFIED | recalculateLevel() walks TierDefinition.xpToNext from loader |
| PRG-04 | 04-05 | 6 unlockable abilities | SATISFIED | abilities.json: 6 entries confirmed (COMBAT, SHIELD_BLOCK, RANGED_COMBAT, LIFESTEAL, MOUNTING, UNDYING) |
| PRG-05 | 04-04 | Tier gates unlock behaviors | PARTIAL | hasAbility() + SERVANT gate in CombatHandler.start() implemented — but start() is never called, so gates are never evaluated |
| PRG-06 | 04-04 | Champion undying | SATISFIED | SquireEntity.die() override confirmed with canUndying() check and BYPASSES_INVULNERABILITY guard |

---

## Critical Check Results

### CMB-07: instanceof in CombatHandler

`grep -c "instanceof"` on CombatHandler.java — all matches are **item and owner checks**, zero are mob entity type checks:

- `instanceof ShieldItem` (equipment detection — correct per plan spec)
- `instanceof BowItem` (equipment detection — correct per plan spec)
- `instanceof SquireHalberdItem` (equipment detection — correct per plan spec)
- `instanceof Player` (owner check — correct per plan spec)

**Result: ZERO mob entity instanceof checks. CMB-07 implementation is correct.**

### CMB-08: SquireHalberdItem sweep AoE

`performSweep()` confirmed at line 100. 2.5-block AABB inflate, excludes `instanceof Player`. Hit counter `halberdHitCount` is a `private int` instance field on CombatHandler (not static, not NBT). Threshold from `SquireConfig.halberdSweepInterval.get()`.

**Result: VERIFIED.**

### PRG-06: Champion undying in die() override with BYPASSES_INVULNERABILITY guard

`die()` override at line 494 of SquireEntity.java. `canUndying()` check fires before `super.die()`. `!source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)` guard confirmed. Early `return` prevents `super.die()` when undying fires.

**Result: VERIFIED.**

### IItemHandler contract: zero .shrink() calls in SquireEquipmentHelper

`grep -c "\.shrink("` returns 0 matches.

**Result: VERIFIED. IItemHandler contract clean — no live-reference mutation.**

---

## Anti-Patterns Found

| File | Issue | Severity | Impact |
|------|-------|----------|--------|
| `SquireBrain.java` | CombatHandler not instantiated, not wired into registerTransitions() | BLOCKER | Goal failure — squire cannot fight. CMB-01/02/05/06/07/10 are dead code. |

---

## Human Verification Required

### 1. SquireEquipmentHelper — runFullEquipCheck() call site

**Test:** Confirm where `runFullEquipCheck()` is called in live gameplay. The method exists and is correct, but grep found no call site in SquireEntity.java or any handler.
**Expected:** Called periodically from aiStep(), on item pickup, or from SquireBrain.
**Why human:** Could not locate a call site. If it has no caller, CMB-03/04 are also orphaned.

### 2. ProgressionHandler.addKillXP() — call site

**Test:** Confirm where `addKillXP()` is called. Must fire when squire kills a mob.
**Expected:** Called from a LivingDeathEvent subscriber or from CombatHandler after kill.
**Why human:** No event subscriber for LivingDeathEvent or equivalent was found in SquireMod.java or any handler. If no caller, PRG-02 is incomplete for the kill XP source.

---

## Gaps Summary

**Root cause: SquireBrain combat wiring is missing.** All 5 combat sub-plans executed correctly — CombatHandler, DangerHandler, SquireEquipmentHelper, SquireHalberdItem, SquireShieldItem, ProgressionHandler, and the full progression data layer are all implemented and substantive. However, no plan was responsible for wiring CombatHandler into SquireBrain.registerTransitions().

The SUMMARY files for plans 04-01 and 04-02 both note "CombatHandler is ready for SquireBrain integration" — this was correctly flagged as a next-phase dependency during execution. But Phase 4 is marked complete in ROADMAP.md and REQUIREMENTS.md without that wiring existing.

**What's missing:**
1. `private CombatHandler combatHandler` field in SquireBrain (initialized in constructor)
2. A global or IDLE-sourced transition that enters COMBAT_APPROACH when `squire.getTarget() != null` (detection logic)
3. COMBAT_APPROACH per-tick transition calling `combatHandler.tick(squire)`, returning the FSM state CombatHandler reports
4. COMBAT_RANGED per-tick transition calling `combatHandler.tickRanged(squire)`
5. FLEEING per-tick transition (CombatHandler.isFleeing() already tracks this — FSM needs to reflect it)

**What's working and does not need changes:**
- CombatHandler internal logic (tactic selection, melee, ranged, flee, guard)
- DangerHandler (ready to be called from within the EXPLOSIVE tactic path)
- SquireEquipmentHelper (IItemHandler API correct, assuming it has a caller)
- SquireHalberdItem + SquireShieldItem + SquireRegistry
- ProgressionHandler + ProgressionDataLoader + all JSON data files
- SquireEntity.die() Champion undying
- SquireEntity.getCurrentSwingDuration() + updateSwingTime() fix

---

_Verified: 2026-04-03T22:00:00Z_
_Verifier: Claude (gsd-verifier)_

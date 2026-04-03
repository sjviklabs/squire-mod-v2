# Phase 4: Combat and Progression - Context

**Gathered:** 2026-04-02
**Status:** Ready for planning
**Source:** Auto-generated (recommended defaults selected)

<domain>
## Phase Boundary

Squire fights hostile mobs intelligently (melee, ranged, shield, flee) with data-driven tactics via entity tags, grows through 5 tiers via XP from kills/mining/work, earns abilities, and wields custom weapons (halberd with sweep, shield). After this phase, the squire is a competent combat companion that grows with the player.

</domain>

<decisions>
## Implementation Decisions

### Combat Architecture

- Port CombatHandler from v0.5.0 — five tactic modes (aggressive, cautious, evasive, explosive, passive) are proven
- Replace instanceof chains with entity tag checks: `target.getType().is(SquireTagKeys.MELEE_AGGRESSIVE)` etc.
- Tag resolution fires in CombatHandler.start() during live gameplay only — never during FML setup (tags are empty then)
- Keep explosive flee inline in CombatHandler for now — separate DangerHandler when danger set expands
- Shield management: block when target is attacking, lower when attacking back
- Ranged switching: switch to bow when target is 8+ blocks away (configurable)
- Flee threshold: disengage when HP drops below configured percentage

### Auto-Equip

- Port SquireEquipmentHelper logic from v0.5.0
- MUST use IItemHandler extractItem/insertItem — NOT getItem() mutation (contract violation)
- Auto-equip fires on inventory change and on item pickup
- "Best" logic: damage value for weapons, protection value for armor

### Custom Items

- Halberd: 7 ATK, -3.0 speed, +1.0 reach, sweep AoE every 3rd hit (360 degrees)
- Shield: 336 durability, custom shield item extending ShieldItem
- Guard mode (CMB-10): use existing MODE_GUARD SynchedEntityData byte — skip leash-breach disengage when set

### Progression System

- Port ProgressionHandler from v0.5.0 — XP accounting, level gates, attribute modifiers per tier
- Tier thresholds from JSON datapack (already on disk from Phase 1)
- SimpleJsonResourceReloadListener to load progression data into memory
- When tier advances: invalidateCapabilities() to update inventory slot count
- 6 abilities: COMBAT, SHIELD_BLOCK, RANGED_COMBAT, LIFESTEAL, MOUNTING, UNDYING
- Abilities defined in abilities.json datapack

### Champion Undying (PRG-06)

- Totem of Undying effect: once per life, survive lethal damage at 1 HP with brief invulnerability
- Reset on resummon (death + re-crest)

### Claude's Discretion

- Exact tactic selection logic per tag combination
- Attribute modifier values per tier (reference v0.5.0)
- Sweep AoE implementation details (damage falloff, knockback)
- abilities.json exact schema

</decisions>

<canonical_refs>

## Canonical References

### Phase 4 Research

- `.planning/phases/04-combat-progression/04-RESEARCH.md` — Combat port analysis, tag-based tactics, IItemHandler pitfalls, abilities schema

### v0.5.0 Reference (read-only)

- `C:/Users/Steve/Projects/squire-mod/src/main/java/com/sjviklabs/squire/ai/handler/CombatHandler.java` — All combat logic, tactic modes, sweep, ranged
- `C:/Users/Steve/Projects/squire-mod/src/main/java/com/sjviklabs/squire/ai/handler/ProgressionHandler.java` — XP, levels, tier gates, attribute modifiers
- `C:/Users/Steve/Projects/squire-mod/src/main/java/com/sjviklabs/squire/util/SquireEquipmentHelper.java` — Auto-equip logic
- `C:/Users/Steve/Projects/squire-mod/src/main/java/com/sjviklabs/squire/ability/SquireAbility.java` — Ability definitions

### Architecture

- `.planning/research/ARCHITECTURE.md` — Handler-per-behavior, FSM priority layers
- `.planning/research/PITFALLS.md` — Entity tag timing, IItemHandler contract

</canonical_refs>

<code_context>

## Existing Code Insights

### Reusable Assets

- SquireBrain + TickRateStateMachine (Phase 2) — combat states already in SquireAIState enum
- SquireBrainEventBus (Phase 2) — COMBAT_START/COMBAT_END events defined
- SquireItemHandler (Phase 1) — IItemHandler for auto-equip
- SquireConfig (Phase 1) — combat section with flee threshold, follow distance, etc.
- Progression JSON files (Phase 1) — 5 tier definitions already on disk

### Integration Points

- CombatHandler registers transitions in SquireBrain for combat states
- ProgressionHandler fires events through SquireBrainEventBus on level-up
- Auto-equip reads from SquireItemHandler via capability
- Entity tags loaded from builtin datapack (Phase 1 ARC-07)

</code_context>

<specifics>
## Specific Ideas

- v0.5.0 combat is 90% a port — the only new design is tag-based tactic resolution
- Progression datapack loader pattern: SimpleJsonResourceReloadListener
- Halberd sweep is vanilla-derived but custom timing (every 3rd hit, not attack cooldown based)

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

_Phase: 04-combat-progression_
_Context gathered: 2026-04-02 via auto mode_

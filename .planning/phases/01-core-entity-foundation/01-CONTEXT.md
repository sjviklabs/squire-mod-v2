# Phase 1: Core Entity Foundation - Context

**Gathered:** 2026-04-02
**Status:** Ready for planning

<domain>
## Phase Boundary

Register the squire entity type with NeoForge, establish persistence (NBT + player data attachment), implement tiered IItemHandler inventory, create 50+ value TOML config, embed builtin datapack, and scaffold the test harness. This phase produces a spawnable, persistent entity with a working inventory and validated config — everything downstream depends on these being correct.

</domain>

<decisions>
## Implementation Decisions

### Crest Summoning Behavior

- Summon via right-click Crest: spawn with brief particle effect + ~1 second materialization animation at crosshair location
- Recall via right-click Crest again: instant despawn, no delay
- Recall always works regardless of combat state — player has full control
- No range limit on Crest — squire identity stored in player attachment, not world-located
- One squire per player enforced at summon time

### Death and Recovery

- On death: equipped gear only drops (armor, weapon, shield). Backpack inventory is lost.
- Recovery: player re-summons via Crest. XP/tier/name retained in player data attachment.
- Death notification: chat message with coordinates ("Your squire fell at [X, Y, Z]")
- Champion undying (PRG-06): Totem of Undying effect — once per life, squire survives lethal damage at 1 HP with brief invulnerability. Must be re-summoned (die and resummon) to reset the ability.

### Inventory Slot Layout

- Slot unlock per tier: Servant 9, Apprentice 18, Squire 27, Knight 32, Champion 36
- Separate equipment slots (fixed: 4 armor + 1 weapon + 1 offhand) + general backpack slots (tier-gated count)
- Backpack slots accept any item — no filtering on manual placement. Junk filtering only applies to auto-pickup (Phase 5).
- IItemHandler capability exposed — hoppers and modded pipes can insert/extract items. Squire participates in automation logistics.

### Config Organization

- Single squire-common.toml, all server-enforced (no client config split)
- Grouped by behavior domain: [general], [combat], [follow], [mining], [farming], [fishing], [progression], [inventory], [rendering], [debug]
- Conservative defaults for ATM10 compatibility: lower tick rates, shorter follow distance, reduced chunk loading. Operators can increase.
- [debug] section with toggles (show AI state, log FSM transitions, draw pathfinding) — all off by default

### Claude's Discretion

- Exact particle effect for spawn animation
- SynchedEntityData field selection (which fields need client sync vs server-only)
- NBT tag naming conventions
- Test harness file organization
- Builtin datapack directory structure within resources

</decisions>

<canonical_refs>

## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### v0.5.0 Reference (read-only — for game logic constants, NOT code structure)

- `C:/Users/Steve/Projects/squire-mod/src/main/java/com/sjviklabs/squire/entity/SquireEntity.java` — NBT field names, SynchedEntityData definitions, death handling logic
- `C:/Users/Steve/Projects/squire-mod/src/main/java/com/sjviklabs/squire/entity/SquireDataAttachment.java` — Player attachment persistence pattern
- `C:/Users/Steve/Projects/squire-mod/src/main/java/com/sjviklabs/squire/config/SquireConfig.java` — Config value names and default values to port
- `C:/Users/Steve/Projects/squire-mod/src/main/java/com/sjviklabs/squire/inventory/SquireInventory.java` — Slot counts and tier-gated capacity logic
- `C:/Users/Steve/Projects/squire-mod/src/main/java/com/sjviklabs/squire/item/SquireCrestItem.java` — Summon/recall interaction flow
- `C:/Users/Steve/Projects/squire-mod/src/main/java/com/sjviklabs/squire/init/` — Registration patterns (what to register, not how)

### Research

- `.planning/research/STACK.md` — NeoForge 21.1.x, ModDevGradle 2.0.141, Geckolib 4.8.3/4.7.6, dependency declarations
- `.planning/research/ARCHITECTURE.md` — Component boundaries, SquireEntity/SquireBrain split, IItemHandler capability registration pattern
- `.planning/research/PITFALLS.md` — SynchedEntityData class mismatch danger, config validation loop, builtin datapack embed for issue #857, entity tag timing

### Project

- `.planning/PROJECT.md` — Key decisions table, constraints, mod ID
- `.planning/REQUIREMENTS.md` — ENT-01 through ENT-07, INV-01/02/06, ARC-04/05/06/07/09, TST-01/02

</canonical_refs>

<code_context>

## Existing Code Insights

### Reusable Assets

- None — greenfield project. All code written from scratch.

### Established Patterns

- v0.5.0 patterns to ADOPT: handler-per-behavior, tick-rate FSM, config-driven gameplay values, player data attachment for cross-death persistence
- v0.5.0 patterns to AVOID: TamableAnimal base class, SimpleContainer inventory, scattered registration (ModItems/ModBlocks/ModEntities), 600-line SquireEntity mixing lifecycle with AI

### Integration Points

- NeoForge entity type registration → SquireRegistry
- NeoForge capability registration → RegisterCapabilitiesEvent for IItemHandler
- NeoForge config registration → ModConfigSpec in squire-common.toml
- Builtin datapack → resources/data/squire/ embedded in mod JAR

</code_context>

<specifics>
## Specific Ideas

- Port the exact config default values from v0.5.0's SquireConfig but adjust downward for conservative ATM10 defaults
- The v0.5.0 SquireDataAttachment pattern worked well — same concept, cleaner implementation
- Inventory slot counts (9/18/27/32/36) match v0.5.0's proven progression feel

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

_Phase: 01-core-entity-foundation_
_Context gathered: 2026-04-02_

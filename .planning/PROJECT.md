# Squire Mod v2

## What This Is

A Minecraft NeoForge 1.21.1 companion mod that gives the player a loyal squire entity — an AI-driven follower that grows from servant to champion through 5 progression tiers, fights alongside the player, performs work tasks (mining, farming, fishing, patrols), and rides horses into mounted combat. This is a ground-up rebuild of squire-mod v0.5.0 with a cleaner architecture, better rendering, and data-driven design.

## Core Value

The squire feels like a real companion — it walks everywhere (never teleports), grows through shared experience, and handles itself intelligently in combat and work without the player micromanaging it.

## Requirements

### Validated

(None yet — ship to validate)

### Active

- [ ] Player can summon and recall a squire via Crest item
- [ ] Squire follows the player without teleporting, sprints to catch up
- [ ] Squire fights hostile mobs with melee and ranged weapons
- [ ] Squire equips armor, weapons, shields from its inventory
- [ ] Squire has a tiered inventory (9-36 slots based on progression)
- [ ] Squire progresses through 5 tiers (Servant → Apprentice → Squire → Knight → Champion) via XP
- [ ] Squire performs work tasks: mining, area clear, block placement, farming, fishing
- [ ] Squire auto-places torches in darkness
- [ ] Squire patrols between signpost waypoints
- [ ] Squire mounts and rides horses, including mounted combat
- [ ] Squire has 6 unlockable abilities tied to progression
- [ ] Squire has personality (chat lines, idle behavior, naming)
- [ ] Player controls squire via radial menu (R key) and /squire commands
- [ ] Squire interacts with containers (chest deposit/withdraw)
- [ ] Squire supports task queuing (multiple sequential commands)
- [ ] Custom 4-piece armor set with tiered textures
- [ ] Custom weapons: halberd (sweep AoE), shield
- [ ] Squire persists across server restarts, crest loss, and death (player attachment)
- [ ] Combat tactics are data-driven via entity tags (not hardcoded mob checks)
- [ ] Progression curves, ability definitions, and gameplay tuning in JSON datapacks
- [ ] Geckolib-based entity rendering with proper animations
- [ ] MineColonies compatibility (prevent citizen/squire conflicts)
- [ ] Jade/WAILA overlay showing squire status
- [ ] Curios/Accessories integration for equipment slots
- [ ] Internal event bus so handlers react to each other's state changes
- [ ] 50+ config values in squire-common.toml (no hardcoded gameplay numbers)

### Out of Scope

- Multiple squires per player — complexity explosion, defer to v2
- Teleportation — core design principle, squire always walks/swims
- Vanilla Goal AI — replaced entirely by custom tick-rate FSM
- Fabric/Architectury support — NeoForge only
- NeoForge 1.21.4 — targeting 1.21.1 for ATM10 compatibility

## Context

- **Predecessor:** squire-mod v0.5.0 (68 Java files, ~7,820 LOC) at `C:\Users\Steve\Projects\squire-mod`
- **Why rebuild:** v0.5.0 extends TamableAnimal (vanilla goal conflicts, sitting behavior baggage), has hardcoded combat tactics, handlers that don't communicate, and texture rendering workarounds. The architecture patterns (tick-rate FSM, handler-per-behavior, config-driven) are proven — the foundation they sit on is not.
- **Reference approach:** Old codebase is read-only reference for game logic constants (XP curves, damage values, tick rates). All new code written from scratch against new interfaces.
- **Modpack:** ATM10 6.2 on NeoForge 21.1.221, 449 mods. Squire must coexist without conflicts.
- **Rendering:** Geckolib for custom .geo.json models and animations, replacing HumanoidModel workarounds.
- **Data-driven:** Entity tags for combat tactics, JSON datapacks for progression/abilities. Gameplay tuning without recompiling.

## Constraints

- **Minecraft version**: 1.21.1 (ATM10 compatibility)
- **NeoForge version**: 21.1.x (match ATM10's NeoForge build)
- **Java**: 21
- **Base class**: PathfinderMob (NOT TamableAnimal) — custom owner system
- **Dependencies**: Geckolib (rendering), Curios API (equipment)
- **No teleportation**: Squire walks, swims, navigates. Never teleports.
- **Mod ID**: `squire` (same as v1 — replaces it in the modpack)

## Key Decisions

| Decision | Rationale | Outcome |
| --- | --- | --- |
| Extend PathfinderMob instead of TamableAnimal | TamableAnimal's vanilla goals fight our FSM, sitting behavior is baggage, owner tracking is trivial to implement manually (~80 lines) | -- Pending |
| Geckolib for rendering | Solves texture rendering issues permanently, enables real animations, standard for custom entities in NeoForge | -- Pending |
| Internal event bus between handlers | Handlers in v0.5.0 were islands — CombatHandler entering combat couldn't tell MiningHandler to clean up. FSM deprioritization isn't the same as explicit notification. | -- Pending |
| Data-driven combat tactics via entity tags | v0.5.0 hardcoded `if (target instanceof Zombie)` chains. Tags let modded mobs get correct tactics by adding `squire:melee_cautious` etc. without code changes. | -- Pending |
| JSON datapacks for progression/abilities | Gameplay tuning (XP curves, ability unlocks, tier thresholds) shouldn't require recompiling. Datapack-driven means server operators can customize. | -- Pending |
| Custom IItemHandler over SimpleContainer | SimpleContainer gives no control over slot validation, capacity changes with tier, or proper capability exposure for hoppers/pipes. | -- Pending |
| Single SquireRegistry class | v0.5.0 scattered registration across ModItems, ModBlocks, ModEntities etc. Registration order matters in NeoForge — one file prevents load-order bugs. | -- Pending |
| SquireEntity (lifecycle) / SquireBrain (AI) split | v0.5.0's SquireEntity was 600+ lines mixing entity lifecycle with AI coordination. Clean separation: entity IS, brain DOES. | -- Pending |
| Read-only reference to v0.5.0 | Avoid copy-paste drift. Write interfaces first, implement from the contract, reference old code only for constants. | -- Pending |

---

_Last updated: 2026-04-02 after initialization_

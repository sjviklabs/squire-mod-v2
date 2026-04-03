# Requirements: Squire Mod v2

**Defined:** 2026-04-02
**Core Value:** The squire feels like a real companion — it walks everywhere (never teleports), grows through shared experience, and handles itself intelligently in combat and work without the player micromanaging it.

## v1 Requirements

Requirements for v1.0.0 release. Full parity with squire-mod v0.5.0 on a clean architecture.

### Entity Foundation

- [ ] **ENT-01**: Player can summon a squire using the Crest item
- [ ] **ENT-02**: Player can recall (despawn) the squire using the Crest item
- [ ] **ENT-03**: Squire persists across server restarts via NBT serialization
- [ ] **ENT-04**: Squire identity (name, XP, level, appearance) persists via player data attachment, surviving crest loss and death
- [ ] **ENT-05**: Squire extends PathfinderMob with custom owner system (not TamableAnimal)
- [ ] **ENT-06**: One squire per player (enforced)
- [ ] **ENT-07**: Squire drops equipped gear on death but retains XP/tier via player attachment

### Follow & Navigation

- [ ] **NAV-01**: Squire follows the player without ever teleporting
- [ ] **NAV-02**: Squire sprints to catch up when player is far
- [ ] **NAV-03**: Squire swims through water to follow
- [ ] **NAV-04**: Squire can sit/stay on command (toggle)
- [ ] **NAV-05**: Squire navigates caves, hills, and structures via pathfinding

### Combat

- [ ] **CMB-01**: Squire engages hostile mobs with melee weapons
- [ ] **CMB-02**: Squire uses bows for ranged combat
- [ ] **CMB-03**: Squire auto-equips best available weapon from inventory
- [ ] **CMB-04**: Squire auto-equips best available armor from inventory
- [ ] **CMB-05**: Squire uses shield to block incoming damage
- [ ] **CMB-06**: Squire flees at low HP (configurable threshold)
- [ ] **CMB-07**: Combat tactics are data-driven via entity tags (not hardcoded mob checks)
- [ ] **CMB-08**: Halberd weapon with 360 sweep AoE every 3rd hit
- [ ] **CMB-09**: Custom shield item (336 durability)
- [ ] **CMB-10**: Guard mode (stand ground and fight, don't follow)

### Progression

- [ ] **PRG-01**: 5-tier progression system: Servant (0-4), Apprentice (5-9), Squire (10-19), Knight (20-29), Champion (30)
- [ ] **PRG-02**: Squire earns XP from kills, mining, and work tasks
- [ ] **PRG-03**: Tier thresholds and XP curves defined in JSON datapacks
- [ ] **PRG-04**: 6 unlockable abilities tied to progression tiers
- [ ] **PRG-05**: Tier gates unlock behaviors (e.g., combat at Apprentice, ranged at Squire, mounting at Knight)
- [ ] **PRG-06**: Champion tier grants "undying" revival ability

### Inventory & Equipment

- [ ] **INV-01**: Squire has tiered inventory (9 slots at Servant, expanding to 36 at Champion)
- [ ] **INV-02**: Inventory implemented via IItemHandler capability (not SimpleContainer)
- [ ] **INV-03**: Squire picks up nearby items (with junk filtering)
- [ ] **INV-04**: Custom 4-piece armor set with tiered textures (visual changes per tier)
- [ ] **INV-05**: Curios/Accessories integration for equipment slots (soft dependency)
- [ ] **INV-06**: Inventory accessible via GUI screen

### Work Behaviors

- [ ] **WRK-01**: Squire mines single blocks on command
- [ ] **WRK-02**: Squire performs area clear (multi-block mining)
- [ ] **WRK-03**: Squire auto-places torches in darkness
- [ ] **WRK-04**: Squire farms (tilling + harvesting)
- [ ] **WRK-05**: Squire fishes (simulated fishing)
- [ ] **WRK-06**: Squire places blocks on command
- [ ] **WRK-07**: Squire interacts with containers (deposit/withdraw items)
- [ ] **WRK-08**: Squire supports task queuing (multiple sequential commands)

### Patrol

- [ ] **PTR-01**: Signpost block that serves as patrol waypoint
- [ ] **PTR-02**: Squire patrols between signpost waypoints in defined order
- [ ] **PTR-03**: Player can define and modify patrol routes

### Mounted

- [ ] **MNT-01**: Squire mounts and rides horses
- [ ] **MNT-02**: Squire follows player while mounted
- [ ] **MNT-03**: Squire engages in combat while mounted
- [ ] **MNT-04**: Mount state persists across sessions (horse UUID in NBT)

### UI & Controls

- [ ] **GUI-01**: Radial menu (R key) for stance switching and commands
- [ ] **GUI-02**: /squire commands for info, mine, place, and other operations
- [ ] **GUI-03**: Inventory screen with equipment slots and backpack view

### Rendering & Personality

- [ ] **RND-01**: Geckolib-based entity model with animations
- [ ] **RND-02**: Male/female skin variants (slim/wide)
- [ ] **RND-03**: Custom naming via name tag or command
- [ ] **RND-04**: Personality chat lines (idle, combat, level-up, new tier)
- [ ] **RND-05**: Backpack visual layer that reflects inventory tier
- [ ] **RND-06**: Tiered armor texture rendering

### Mod Compatibility

- [ ] **CMP-01**: MineColonies compatibility (prevent citizen/squire entity conflicts)
- [ ] **CMP-02**: Jade/WAILA tooltip overlay showing squire status
- [ ] **CMP-03**: Curios/Accessories API integration (soft dependency, mod functions without it)

### Architecture

- [ ] **ARC-01**: Custom tick-rate state machine (FSM) replacing vanilla GoalSelector
- [ ] **ARC-02**: Handler-per-behavior pattern (one handler class per behavior)
- [ ] **ARC-03**: Internal event bus so handlers react to each other's state changes
- [ ] **ARC-04**: SquireEntity (lifecycle/NBT) split from SquireBrain (AI/FSM)
- [ ] **ARC-05**: Single SquireRegistry for all NeoForge registrations
- [ ] **ARC-06**: 50+ config values in squire-common.toml (no hardcoded gameplay numbers)
- [ ] **ARC-07**: Builtin datapack for entity tags and JSON data (not world-level)
- [ ] **ARC-08**: Network payloads via StreamCodec (not manual FriendlyByteBuf)
- [ ] **ARC-09**: Chunk loading during area clear operations

### Testing

- [ ] **TST-01**: JUnit 5 unit tests for core systems (FSM, inventory, progression)
- [ ] **TST-02**: NeoForge GameTests for in-world entity verification

## v2 Requirements

Deferred to future release. Tracked but not in current roadmap.

### Multi-Squire

- **MSQ-01**: Multiple squires per player
- **MSQ-02**: Multi-squire coordination and task assignment

### Advanced AI

- **AAI-01**: Autonomous multi-step task planning
- **AAI-02**: Advanced pathfinding with async chunk-aware navigation

### Prestige

- **PRS-01**: Prestige system beyond Champion tier

## Out of Scope

| Feature | Reason |
| --- | --- |
| Teleportation (any form) | Core design principle — squire always walks/swims. Teleport destroys the identity. |
| Vanilla GoalSelector AI | Replaced by custom FSM. TamableAnimal goals fight the state machine. |
| Fabric/Architectury support | NeoForge only. ATM10 is the target. Fabric port is a separate project. |
| NeoForge 1.21.4 | Targeting 1.21.1 for ATM10 compatibility |
| LLM natural language commands | Requires API keys, network deps, unpredictable behavior. Radial menu covers 95% of use cases. |
| Real-time morale/bond meter | Adds UI complexity and player frustration. Personality expressed via chat lines instead. |
| Unlimited level progression | Cap at Champion (30). Prestige deferred to v2. |
| TamableAnimal base class | Source of v0.5.0 architectural debt. Using PathfinderMob + custom owner. |

## Traceability

| Requirement | Phase | Status |
| --- | --- | --- |
| (Populated during roadmap creation) | | |

**Coverage:**

- v1 requirements: 53 total
- Mapped to phases: 0
- Unmapped: 53

---

_Requirements defined: 2026-04-02_
_Last updated: 2026-04-02 after initial definition_

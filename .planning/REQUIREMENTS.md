# Requirements: Squire Mod v2

**Defined:** 2026-04-02
**Core Value:** The squire feels like a real companion — it walks everywhere (never teleports), grows through shared experience, and handles itself intelligently in combat and work without the player micromanaging it.

## v1 Requirements

Requirements for v1.0.0 release. Full parity with squire-mod v0.5.0 on a clean architecture.

### Entity Foundation

- [x] **ENT-01**: Player can summon a squire using the Crest item
- [x] **ENT-02**: Player can recall (despawn) the squire using the Crest item
- [x] **ENT-03**: Squire persists across server restarts via NBT serialization
- [x] **ENT-04**: Squire identity (name, XP, level, appearance) persists via player data attachment, surviving crest loss and death
- [x] **ENT-05**: Squire extends PathfinderMob with custom owner system (not TamableAnimal)
- [x] **ENT-06**: One squire per player (enforced)
- [x] **ENT-07**: Squire drops equipped gear on death but retains XP/tier via player attachment

### Follow & Navigation

- [x] **NAV-01**: Squire follows the player without ever teleporting
- [x] **NAV-02**: Squire sprints to catch up when player is far
- [x] **NAV-03**: Squire swims through water to follow
- [x] **NAV-04**: Squire can sit/stay on command (toggle)
- [x] **NAV-05**: Squire navigates caves, hills, and structures via pathfinding

### Combat

- [x] **CMB-01**: Squire engages hostile mobs with melee weapons
- [x] **CMB-02**: Squire uses bows for ranged combat
- [x] **CMB-03**: Squire auto-equips best available weapon from inventory
- [x] **CMB-04**: Squire auto-equips best available armor from inventory
- [x] **CMB-05**: Squire uses shield to block incoming damage
- [x] **CMB-06**: Squire flees at low HP (configurable threshold)
- [x] **CMB-07**: Combat tactics are data-driven via entity tags (not hardcoded mob checks)
- [x] **CMB-08**: Halberd weapon with 360 sweep AoE every 3rd hit
- [x] **CMB-09**: Custom shield item (336 durability)
- [x] **CMB-10**: Guard mode (stand ground and fight, don't follow)

### Progression

- [x] **PRG-01**: 5-tier progression system: Servant (0-4), Apprentice (5-9), Squire (10-19), Knight (20-29), Champion (30)
- [x] **PRG-02**: Squire earns XP from kills, mining, and work tasks
- [x] **PRG-03**: Tier thresholds and XP curves defined in JSON datapacks
- [x] **PRG-04**: 6 unlockable abilities tied to progression tiers
- [x] **PRG-05**: Tier gates unlock behaviors (e.g., combat at Apprentice, ranged at Squire, mounting at Knight)
- [x] **PRG-06**: Champion tier grants "undying" revival ability

### Inventory & Equipment

- [x] **INV-01**: Squire has tiered inventory (9 slots at Servant, expanding to 36 at Champion)
- [x] **INV-02**: Inventory implemented via IItemHandler capability (not SimpleContainer)
- [x] **INV-03**: Squire picks up nearby items (with junk filtering)
- [x] **INV-04**: Custom 4-piece armor set with tiered textures (visual changes per tier)
- [ ] **INV-05**: Curios/Accessories integration for equipment slots (soft dependency)
- [x] **INV-06**: Inventory accessible via GUI screen

### Work Behaviors

- [ ] **WRK-01**: Squire mines single blocks on command
- [ ] **WRK-02**: Squire performs area clear (multi-block mining)
- [ ] **WRK-03**: Squire auto-places torches in darkness
- [x] **WRK-04**: Squire farms (tilling + harvesting)
- [x] **WRK-05**: Squire fishes (simulated fishing)
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

- [x] **GUI-01**: Radial menu (R key) for stance switching and commands
- [x] **GUI-02**: /squire commands for info, mine, place, and other operations
- [x] **GUI-03**: Inventory screen with equipment slots and backpack view

### Rendering & Personality

- [x] **RND-01**: Geckolib-based entity model with animations
- [x] **RND-02**: Male/female skin variants (slim/wide)
- [x] **RND-03**: Custom naming via name tag or command
- [x] **RND-04**: Personality chat lines (idle, combat, level-up, new tier)
- [x] **RND-05**: Backpack visual layer that reflects inventory tier
- [x] **RND-06**: Tiered armor texture rendering

### Mod Compatibility

- [ ] **CMP-01**: MineColonies compatibility (prevent citizen/squire entity conflicts)
- [ ] **CMP-02**: Jade/WAILA tooltip overlay showing squire status
- [ ] **CMP-03**: Curios/Accessories API integration (soft dependency, mod functions without it)

### Architecture

- [x] **ARC-01**: Custom tick-rate state machine (FSM) replacing vanilla GoalSelector
- [x] **ARC-02**: Handler-per-behavior pattern (one handler class per behavior)
- [x] **ARC-03**: Internal event bus so handlers react to each other's state changes
- [ ] **ARC-04**: SquireEntity (lifecycle/NBT) split from SquireBrain (AI/FSM)
- [x] **ARC-05**: Single SquireRegistry for all NeoForge registrations
- [x] **ARC-06**: 50+ config values in squire-common.toml (no hardcoded gameplay numbers)
- [x] **ARC-07**: Builtin datapack for entity tags and JSON data (not world-level)
- [x] **ARC-08**: Network payloads via StreamCodec (not manual FriendlyByteBuf)
- [x] **ARC-09**: Chunk loading during area clear operations

### Testing

- [x] **TST-01**: JUnit 5 unit tests for core systems (FSM, inventory, progression)
- [x] **TST-02**: NeoForge GameTests for in-world entity verification

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

| Requirement | Phase   | Status      |
| ----------- | ------- | ----------- |
| ENT-01      | Phase 1 | Complete |
| ENT-02      | Phase 1 | Complete |
| ENT-03      | Phase 1 | Complete |
| ENT-04      | Phase 1 | Complete |
| ENT-05      | Phase 1 | Complete |
| ENT-06      | Phase 1 | Complete |
| ENT-07      | Phase 1 | Complete |
| INV-01      | Phase 1 | Complete |
| INV-02      | Phase 1 | Complete |
| INV-06      | Phase 1 | Complete |
| ARC-04      | Phase 1 | Pending     |
| ARC-05      | Phase 1 | Complete |
| ARC-06      | Phase 1 | Complete |
| ARC-07      | Phase 1 | Complete |
| ARC-09      | Phase 1 | Complete |
| TST-01      | Phase 1 | Complete |
| TST-02      | Phase 1 | Complete |
| NAV-01      | Phase 2 | Complete |
| NAV-02      | Phase 2 | Complete |
| NAV-03      | Phase 2 | Complete |
| NAV-04      | Phase 2 | Complete |
| NAV-05      | Phase 2 | Complete |
| ARC-01      | Phase 2 | Complete |
| ARC-02      | Phase 2 | Complete |
| ARC-03      | Phase 2 | Complete |
| ARC-08      | Phase 2 | Complete |
| RND-01      | Phase 3 | Complete |
| RND-02      | Phase 3 | Complete |
| RND-03      | Phase 3 | Complete |
| RND-04      | Phase 3 | Complete |
| RND-05      | Phase 3 | Complete |
| RND-06      | Phase 3 | Complete |
| INV-04      | Phase 3 | Complete |
| CMB-01      | Phase 4 | Complete |
| CMB-02      | Phase 4 | Complete |
| CMB-03      | Phase 4 | Complete |
| CMB-04      | Phase 4 | Complete |
| CMB-05      | Phase 4 | Complete |
| CMB-06      | Phase 4 | Complete |
| CMB-07      | Phase 4 | Complete |
| CMB-08      | Phase 4 | Complete |
| CMB-09      | Phase 4 | Complete |
| CMB-10      | Phase 4 | Complete |
| PRG-01      | Phase 4 | Complete |
| PRG-02      | Phase 4 | Complete |
| PRG-03      | Phase 4 | Complete |
| PRG-04      | Phase 4 | Complete |
| PRG-05      | Phase 4 | Complete |
| PRG-06      | Phase 4 | Complete |
| GUI-01      | Phase 5 | Complete |
| GUI-02      | Phase 5 | Complete |
| GUI-03      | Phase 5 | Complete |
| INV-03      | Phase 5 | Complete |
| INV-05      | Phase 8 | Pending     |
| WRK-01      | Phase 6 | Pending     |
| WRK-02      | Phase 6 | Pending     |
| WRK-03      | Phase 6 | Pending     |
| WRK-04      | Phase 6 | Complete |
| WRK-05      | Phase 6 | Complete |
| WRK-06      | Phase 6 | Pending     |
| WRK-07      | Phase 6 | Pending     |
| WRK-08      | Phase 6 | Pending     |
| PTR-01      | Phase 7 | Pending     |
| PTR-02      | Phase 7 | Pending     |
| PTR-03      | Phase 7 | Pending     |
| MNT-01      | Phase 7 | Pending     |
| MNT-02      | Phase 7 | Pending     |
| MNT-03      | Phase 7 | Pending     |
| MNT-04      | Phase 7 | Pending     |
| CMP-01      | Phase 8 | Pending     |
| CMP-02      | Phase 8 | Pending     |
| CMP-03      | Phase 8 | Pending     |

**Coverage:**

- v1 requirements: 72 total
- Mapped to phases: 72
- Unmapped: 0

---

_Requirements defined: 2026-04-02_
_Last updated: 2026-04-02 — traceability populated after roadmap creation_

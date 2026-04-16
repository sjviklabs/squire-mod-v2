# Squire Mod v2

A Minecraft companion mod that gives players a loyal AI-driven squire. The squire follows, fights, mines, farms, fishes, and levels up alongside you — no teleportation, no cheats, no micromanagement.

Ground-up rebuild of [squire-mod v0.5.0](https://github.com/sjviklabs/squire-mod) with clean architecture, proper Geckolib rendering, and data-driven progression.

## Features

### Companion AI

- **Always walks** — never teleports. Navigates doors, water, and obstacles with sprint-to-catch-up behavior.
- **5-tier progression:** Servant → Apprentice → Squire → Knight → Champion. XP-based leveling with expanding inventory (9–36 slots).
- **Persistent** across server restarts, death, and dimension changes.

### Combat

- Melee and ranged combat with weapon/armor auto-equipping
- Shield blocking, flee-at-low-HP self-preservation
- Custom halberd weapon (sweep AoE + extended reach)
- Mounted combat — rides horses into battle
- Data-driven combat tactics via entity tags (supports modded mobs)

### Work System

- **Mining** — area sweep pattern, shaft mining, auto-torch placement
- **Farming** — crop detection, harvest-replant, idle row patrol
- **Fishing** — water auto-discovery (spiral scan + flood-fill), shore positioning, idle animations
- **Auto-crafting** — self-crafts replacement tools from inventory
- **Auto-deposit** — dumps inventory into home chest at 80% full
- **Task queuing** — sequential commands ("mine here, then farm, then return")
- **Signpost patrol routes** — walk between player-placed waypoints

### Equipment & UI

- Custom armor set with tiered textures (Servant → Champion)
- Custom weapons: halberd, shield
- Crest item for summoning and recalling
- **Radial menu (R key)** — 4-wedge command wheel
- **In-game guidebook** — "The Squire's Manual" via Patchouli (13 entries, 4 chapters)
- `/squire` command suite for full control

### Compatibility

- **All The Mods 10** — built for ATM10 (449 mods), works standalone
- **MineColonies** — friendly-fire prevention, citizen whitelist
- **Jade/WAILA** — optional health/tier/mode overlay
- **Curios** — accessory slot integration

## Tech Stack

| Component | Version |
|-----------|---------|
| Minecraft | 1.21.1 |
| NeoForge | 21.1.221 |
| Geckolib | 4.8.4 |
| Java | 21 LTS |

## Build

```bash
./gradlew build        # Build JAR
./gradlew runClient    # Dev client
./gradlew runServer    # Dev server
```

Output: `build/libs/squire-mod-v2-*.jar`

## Architecture

- **PathfinderMob** base (not TamableAnimal) — avoids vanilla goal conflicts
- **Custom FSM** (TickRateStateMachine) — per-tick-rate state management, not GoalSelector
- **Handler-per-behavior** — one class per behavior (CombatHandler, MiningHandler, FarmingHandler, etc.)
- **WorkHandler interface** — adding a new work type = implement interface + 1 line in constructor
- **Data-driven** — entity tags for combat tactics, JSON datapacks for progression curves
- **Geckolib rendering** — custom .geo.json models with bone-based animations

## Commands

```
/squire info | name | mode | appearance
/squire mine | place | farm | fish | shaft
/squire mount | dismount | role | homechest
/squire list | kill | clear
```

## Configuration

50+ TOML config values in `squire-common.toml` — behavior tuning, feature toggles, progression curves. No recompilation needed for modpack operators.

## License

[MIT](LICENSE)

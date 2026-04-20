# Squire Mod v2

A Minecraft companion mod that gives players a loyal AI-driven squire. Walks, fights, mines, chops, farms, fishes, patrols, levels up, and carries gear — no teleportation, no cheats, no micromanagement. A professional henchman, not a pet.

As of **v4.0.0**, Squire is a **MineColonies addon**: the squire's AI is built on MineColonies' `TickRateStateMachine` + `ThreatTable` primitives instead of a custom FSM. The squire remains **player-bound** (not colony-bound — no citizen, no colony, no hidden micro-colony).

If you don't run MineColonies, **[v3.1.5](https://github.com/sjviklabs/squire-mod-v2/releases/tag/v3.1.5)** is the last standalone release.

## Features

### Companion AI

- **Always walks** — never teleports. Navigates doors, water, and obstacles.
- **5-tier progression:** Servant → Apprentice → Squire → Knight → Champion. XP-based leveling, expanding backpack (9–36 slots).
- **Persistent** across restarts, death, dimension changes. Crest NBT stores owner UUID + area selection + XP.
- **Four modes:** Follow / Stay (sit) / Guard / Patrol.

### Combat

- **`ThreatTable`-driven targeting** — damage-weighted, damage-you-takes-priority, no vanilla target goals, no race conditions.
- Periodic hostile scan (10t interval, 16-block radius) — tag-filtered so it respects modpack-specific mob tags.
- MineColonies citizens filtered out via `MineColoniesCompat`.
- Melee + ranged loadout swaps (bow in range, sword + shield in close).
- Chat lines on combat entry / win, rate-limited so a flapping state can't spam.

### Work — crest-area driven

Each work task uses a Crest right-click area selection. Two corner clicks define the region; the squire handles the rest.

- **`/squire mine`** — top-down block sweep, drops direct to inventory.
- **`/squire chop`** — tree-felling by `BlockTags.LOGS` flood-fill. Handles vanilla 2×2 dark oak, jungle giants, cherry, mangrove, and modded trees (ATM10 rainbow, etc.). Auto-replants saplings on stumps when enabled.
- **`/squire farm`** — continuous harvest → plant → till loop. `/squire farm stop` to end.
- **`/squire fish`** — water auto-discovery, shore positioning, vanilla loot-table rolls.
- **`/squire patrol`** — perimeter walk; combat preempts cleanly.
- **`/squire place <pos>`** — places the mainhand block at coordinates.

### Inventory (v4.0.3)

- **`/squire store <chest>`** — deposits surplus into any chest / barrel / shulker / modded container. Keeps the **best** tool/weapon/armor per category (diamond Fortune III pickaxe beats iron Efficiency I, etc.); duplicates and worse copies go to the chest. Cursed gear always deposits.
- **`/squire resupply [chest]`** — pulls one best-scoring item per missing gear category from the specified chest (or the last chest the squire deposited into, if omitted).
- **Auto-deposit (v4.0.4)** — when the squire is passive and ≥80% of backpack slots hold surplus (non-gear) items, it auto-walks to its remembered deposit chest and empties them.
- **Auto-restock (v4.0.3)** — when the squire is missing a gear category entirely (broken tool, empty backpack slot) and has a remembered deposit chest, it auto-walks there, pulls a replacement, and auto-equips. No command needed.

### Equipment

- Auto-equip best armor / weapon / shield from backpack.
- Melee vs ranged loadout swap based on target distance.
- Curse filter — cursed items never auto-equip (they still show up in the inventory menu).

## Requirements

| Component | Version |
|-----------|---------|
| Minecraft | 1.21.1 |
| NeoForge | 21.1.221+ |
| **MineColonies** | **1.1.1299-1.21.1-snapshot+ (required)** |
| Geckolib | 4.8.4+ |
| Java | 21 LTS |

Optional: Curios 9.5+, Jade 15.0+.

## Install

1. Install MineColonies 1.1.1299+ into your NeoForge 1.21.1 profile.
2. Drop `squire-mod-v2-x.y.z.jar` into `mods/` alongside it.
3. Craft a Squire's Crest, right-click — your squire materializes.

## Architecture

Four layers, deliberately thin:

```
Entity          SquireEntity extends PathfinderMob implements IThreatTableEntity
                Public surface: ~5 methods (summon, recall, bindToCrest, AI/threat accessors)

AI controller   SquireAIController
                Holds the active Job AI, swaps based on context, routes ticks.
                Priority: Guard > work AIs > Restock > Follow > Idle.

Job AIs         One TickRateStateMachine<SquireAIState> per job:
                  IdleAI, FollowOwnerAI, GuardAI,
                  MinerAI, LumberjackAI, FarmerAI, FisherAI, PlacingAI,
                  PatrolAI, ChestAI, RestockAI.

Primitives      MineColonies: TickRateStateMachine, AITarget, ThreatTable, IThreatTableEntity.
                Vanilla: PathNavigation, LookControl, Geckolib renderer.
                No wrappers — imported and used directly.
```

Rewrite rationale and the target-ownership race-condition story: see the [v4.0.0 release notes](https://github.com/sjviklabs/squire-mod-v2/releases/tag/v4.0.0). TL;DR — v3.x's custom FSM raced vanilla target goals for `squire.getTarget()`; five patches in 24 hours fought the same bug class. v4.0.0 deletes both sides (vanilla target goals removed, MC's ThreatTable is the single authority).

## Commands

```
/squire info | name | mode (follow|stay|guard) | appearance
/squire mine | chop | farm [stop] | fish | patrol [stop]
/squire place <pos> | store <pos> | resupply [pos]
/squire mount | dismount | recall
/squire list | kill
```

All commands require OP level 2 by default; tune in `squire-common.toml`.

## Build

Local build requires MineColonies 1.21.1 jar at `libs/minecolonies-1.1.1299-1.21.1-snapshot.jar` (gitignored — LDTTeam's Maven does not publish 1.21.1 artifacts, so it's copied from a local mod install).

```bash
./scripts/fetch-deps.sh   # copies MineColonies jar from ATM10 instance into libs/
./gradlew build -x test   # the NeoForge test harness doesn't load local-jar deps
```

Output: `build/libs/squire-mod-v2-*.jar` (+ sources jar).

## Configuration

`squire-common.toml` — behavior tuning, feature toggles, progression curves. No recompilation needed for modpack operators.

## License

[MIT](LICENSE)

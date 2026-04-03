# Feature Research

**Domain:** Minecraft NeoForge companion/follower mod (single human squire with progression)
**Researched:** 2026-04-02
**Confidence:** HIGH for table stakes (clear market consensus), MEDIUM for differentiators (fewer reference points), LOW for anti-features (inferred from pattern analysis)

---

## Ecosystem Snapshot

Comparable mods researched:
- **Modern Companions** (NeoForge 1.21.1) — 14 class-based followers, soul gems, morale/bond system, no level cap, patrol radius, resurrection scroll
- **Human Companions** (Forge 1.20.1) — simple knight/archer/axeguard, auto-equip, sit toggle, level-every-3-kills health scaling
- **MineColonies citizens** — specialist workers (miner, farmer, fisher, guard), tick-rate state machines, dedicated job AI per role
- **Taterzens** — server-side NPC framework, custom commands, patrol paths, no combat AI
- **Companion-Friend** — archer ally, campfire cooking, dialogue immersion, "personality" during combat

The market splits into two camps: (A) dead-simple pets that fight and carry stuff, (B) complex multi-follower RPG systems with class menus. Squire v2 occupies a distinct third lane: single, deeply personal companion with meaningful progression and autonomous work behavior. No other mod does this combination well at 1.21.1.

---

## Table Stakes

Features users expect. Missing any of these = mod feels broken or unfinished before they get to the good parts.

| Feature | Why Expected | Complexity | Notes |
|---------|-------------|------------|-------|
| Follow the player reliably | Every follower mod does this; it's the minimum viable contract | LOW | Squire's "no teleport" design is a constraint here — pathfinding robustness is critical |
| Fight hostile mobs | Users expect a companion to protect them; pure pacifist followers get uninstalled fast | MEDIUM | Combat AI needs to handle ranged, melee, and flee scenarios |
| Auto-equip best gear from inventory | Human Companions established this as baseline; users test it immediately | LOW | "Best" logic must be class/weapon-type aware, not just damage value |
| Named, persistent across sessions | Unnamed entities feel disposable; loss on restart is a bug in perception | LOW | Server persistence (NBT save/load) is non-negotiable |
| Sit/stay command | Users immediately want to "park" companions; been standard since wolves | LOW | Toggle behavior, not a stance — simplest control primitive |
| Follow/guard stance toggle | Modern Companions made this the UI paradigm; users now expect it | LOW | Radial menu or keybind both acceptable |
| Some form of inventory access | Mule-carrying function is a top-3 use case across all companion mods | MEDIUM | Slot count can vary; just needs to exist and be accessible |
| Survive combat without dying constantly | Human Companions explicitly warns "don't store valuables, they die easily" — players hate this | MEDIUM | Health scaling, flee-at-low-HP behavior, shield blocking |
| Reachable player support channel | Not a code feature, but user expectation — changelog, config docs, issue tracker | LOW | CurseForge/Modrinth page with known issues list |

---

## Differentiators

Features that set Squire v2 apart. These are why players pick this mod over Modern Companions or Human Companions.

| Feature | Value Proposition | Complexity | Notes |
|---------|------------------|------------|-------|
| No-teleport navigation | Squire always walks — crossing rivers, climbing hills, navigating caves. Feels alive, not a shortcut. No other 1.21.1 mod commits to this. | HIGH | Requires robust pathfinding fallback when stuck; can't just teleport as safety valve |
| 5-tier progression with named milestones | Servant → Champion gives players a story arc with their squire, not just stat inflation. Naming each tier makes the relationship tangible. | MEDIUM | XP curves and tier thresholds in JSON datapack = tunable without recompile |
| Autonomous work tasks (mining, farming, fishing, patrols) | Most companions are bodyguards. A squire that works independently while you do other things is a different product category. | HIGH | Each behavior is its own handler with tick-rate state machine; dependency chain: inventory → task assignment → behavior execution |
| Torch auto-placement | Tiny feature with outsized satisfaction. Players notice darkness, then notice the squire lit it. MineColonies does this for colonies; no companion mod does it for a personal follower. | LOW | Requires light-level check + inventory torch count; simple but memorable |
| Mounted combat (horse riding) | Combat on horseback is a fantasy archetype. Modern Companions has no mount behavior. This is visually impressive and mechanically distinct. | HIGH | Requires separate mounted movement goals, attack reachability checks, mount pathfinding |
| Signpost patrol routes | Player-defined waypoints via placeable blocks. Not a radius patrol — actual named routes. Nobody else has this at this specificity. | MEDIUM | Signpost block + NBT route data + patrol handler reading ordered waypoint list |
| Task queuing | "Mine here, then farm here, then come back" is how players think. Most mods give one command at a time. | MEDIUM | Queue data structure in SquireBrain; handlers pop tasks on completion |
| Data-driven combat tactics via entity tags | Modded mobs automatically get correct combat behavior when tagged. No hardcoded `instanceof` chains. Reduces incompatibility with ATM10's 449 mods. | MEDIUM | Entity tag JSON files; tactics resolver reads tags at combat-start |
| Personality (named idle lines, contextual chat) | Modern Companions added morale/bond as a differentiator. Squire having situational personality (low HP, leveling up, new tier) makes it feel like a character. | LOW | String pool per tier/state + chat event triggers; minimal code, high perceived value |
| Curios/Accessories integration | Dedicated equipment slots beyond armor slots. Lets players customize without burning armor slots on non-armor items. | LOW | Curios API capability registration; 1-2 days of work for meaningful player benefit |
| Custom armor set with tiered textures | Visual progression signal. Champion-tier armor looks different from Servant-tier. Players track progress visually. | MEDIUM | Geckolib texture swap per tier; 5 texture variants per armor piece |
| Container interaction (deposit/withdraw) | Companion as logistics node: "deposit everything" before bed, "withdraw arrows" before raid. Nobody else offers this for a personal companion. | MEDIUM | Requires SquireBrain to pathfind to container, open it, transfer items by category |
| Radial menu (R key) | Context-sensitive wheel avoids menu-diving for common commands. Better UX than shift-right-click chains. | MEDIUM | Client-side overlay; sends packets to server on selection |
| 50+ TOML config values | Server operators in modpacks (like ATM10) need tuning without source access. Hardcoded numbers kill modpack adoption. | LOW | Config class registration in NeoForge; expose every gameplay constant |

---

## Anti-Features

Features that seem good but create problems. Build exactly none of these.

| Feature | Why Requested | Why Problematic | Alternative |
|---------|--------------|-----------------|-------------|
| Multiple squires per player | "I want an army" | State machine complexity multiplies; pathfinding conflicts; server performance; inventory management becomes full-time job | Defer to v3 with explicit multi-squire architecture; v2 is "one perfect companion" |
| Teleportation as fallback | "It keeps getting stuck" | Destroys the core design value (squire feels alive by always walking); once added, navigation bugs get papered over instead of fixed | Invest in pathfinding edge-case handling — stuck detection, unstuck behavior, water traversal |
| Companion death with item loss | "Realistic" | Players rage-quit when they lose tiered gear; Human Companions gets this complaint constantly | Companion drops equipped items on death but retains learned XP/tier; player recovers gear or reequips |
| LLM/AI natural language commands | Player2 AI NPC does this; sounds impressive | Requires API key, network dependency, latency, cost to player, unpredictable behavior, modpack incompatibility | Radial menu + /squire commands cover 95% of use cases with zero external dependencies |
| Vanilla goal system (GoalSelector) | "Easier to implement" | TamableAnimal's goals fight custom FSM; sitting behavior baggage; this is why v0.5.0 is being rebuilt | PathfinderMob + custom tick-rate FSM from scratch (already decided) |
| Unlimited companion levels with no cap | Modern Companions does this | Progression becomes meaningless; players hit Champion and keep grinding for no narrative payoff | Hard cap at Champion (tier 5, level 30); prestige system optional in v3 |
| Real-time morale/bond stat tracking | Modern Companions has this; sounds deep | Adds UI complexity, balance work, and player frustration when morale tanks mid-dungeon | Personality expressed through chat lines and idle behavior — implied emotional state, not a meter to manage |
| Fabric/Architectury support | "More users" | Doubles maintenance burden; different event system, different capability API, different rendering hooks | NeoForge only; ATM10 is the target platform; Fabric port is a separate project if demand warrants |

---

## Feature Dependencies

```
[Progression Tiers]
└──requires──> [XP Tracking] (no tiers without XP accumulation)
└──requires──> [Persistent NBT save/load] (tiers reset on restart otherwise)
└──enables──>  [Inventory expansion] (slot count gates on tier)
└──enables──>  [Ability unlocks] (abilities gate on tier)
└──enables──>  [Tiered armor textures] (visual requires tier data)
└──enables──>  [Personality chat lines] (tier-specific lines require tier state)

[Work Tasks: Mining / Farming / Fishing]
└──requires──> [Inventory system] (nowhere to put harvest without inventory)
└──requires──> [Follow behavior] (squire must navigate to work location)
└──requires──> [Task queuing] (sequential work requires queue)
└──enables──>  [Container interaction] (deposit harvest into chest)

[Mounted Combat]
└──requires──> [Combat AI] (can't fight from horseback without base combat)
└──requires──> [Follow behavior] (mount must accompany player)
└──conflicts--> [Patrol behavior] (can't patrol while mounted — mount is player-attached)

[Signpost Patrol Routes]
└──requires──> [Signpost block registration] (waypoints need a placeable block)
└──requires──> [Patrol handler] (behavior to walk between waypoints)
└──conflicts--> [Follow behavior] (patrol and follow are mutually exclusive stances)

[Container Interaction]
└──requires──> [Inventory system] (items must go somewhere)
└──requires──> [Pathfinding] (squire must navigate to container)
└──requires──> [Task queuing] (deposit task must complete before next task)

[Radial Menu]
└──requires──> [Client-server packet layer] (menu sends stance/command to server)
└──enhances──> [All stances and commands] (unified UX for everything)

[Torch Auto-Placement]
└──requires──> [Inventory system] (torches must be in squire's inventory)
└──requires──> [Follow behavior] (squire must be near player to light path)

[Curios Integration]
└──requires──> [Curios API dependency] (external mod; must be optional/soft dep)
└──enhances──> [Equipment system] (adds dedicated slots without burning armor slots)

[Data-driven combat tactics]
└──requires──> [Entity tag JSON files] (tactics definitions)
└──enhances──> [Combat AI] (replaces hardcoded instanceof chains)
└──enables──> [ATM10 modpack compatibility] (modded mobs get correct tactics via tags)
```

### Dependency Notes

- **Progression Tiers require persistent NBT:** Tier state lost on restart is indistinguishable from a bug. This is P0 infrastructure.
- **Work tasks require inventory:** Don't implement task behaviors before inventory is stable — items need somewhere to go or the behavior crashes.
- **Mounted combat conflicts with patrol:** These are mutually exclusive stances. The FSM must prevent both being active; don't assume it resolves naturally.
- **Container interaction requires task queuing:** Without a queue, "deposit then return" is two separate commands the player must issue manually. With a queue, it's one compound task.
- **Curios is a soft dependency:** Mod must boot and function fully without Curios present. Integration activates only when Curios is loaded.

---

## MVP Definition

### Launch With (v1 — "Squire walks in")

The minimum that delivers the core value proposition: a companion that feels alive because it navigates without teleporting, fights competently, and grows with you.

- [ ] Follow behavior (no teleport, sprint-to-catch-up, stuck recovery)
- [ ] Combat AI (melee, basic ranged, flee at low HP)
- [ ] Auto-equip from inventory
- [ ] Inventory system (9-36 slots, tier-gated)
- [ ] Persistence (NBT save/load, survives server restart)
- [ ] Progression system (5 tiers, XP tracking, JSON-datapack curves)
- [ ] Sit/stay toggle
- [ ] Radial menu (R key) for stance switching
- [ ] Summon/recall via Crest item
- [ ] Config TOML (expose all tunable values)
- [ ] MineColonies compatibility shim (prevents entity conflicts in ATM10)

### Add After Validation (v1.x)

Add once core loop is confirmed working and stable in ATM10.

- [ ] Work tasks: torch placement — low complexity, high perceived value, validates task handler architecture
- [ ] Work tasks: mining and farming — validates multi-behavior FSM
- [ ] Signpost patrol routes — validates waypoint system
- [ ] Task queuing — validates compound command pattern
- [ ] Personality chat lines — low effort, makes companion feel alive
- [ ] Jade/WAILA overlay — diagnostic tool, makes squire status readable
- [ ] Curios integration — soft dep, add when core is stable

### Future Consideration (v2+)

- [ ] Mounted combat — high complexity, requires horse pathfinding; validate base combat first
- [ ] Container interaction — useful but complex; needs stable inventory + task queue first
- [ ] Custom armor set with tiered textures — GeckoLib texture swap; defer until model is finalized
- [ ] Custom weapons (halberd, shield) — fun but not load-bearing for validation
- [ ] Data-driven combat tactics via entity tags — needed for broad modpack compat, but hardcoded tactics are acceptable for initial ATM10 testing
- [ ] /squire commands — convenient but radial menu covers core use cases at launch

---

## Feature Prioritization Matrix

| Feature | User Value | Implementation Cost | Priority |
|---------|------------|---------------------|----------|
| Follow (no teleport) | HIGH | HIGH | P1 |
| Combat AI | HIGH | MEDIUM | P1 |
| Persistence (NBT) | HIGH | LOW | P1 |
| Progression (5 tiers) | HIGH | MEDIUM | P1 |
| Inventory system | HIGH | MEDIUM | P1 |
| Auto-equip | MEDIUM | LOW | P1 |
| Radial menu | HIGH | MEDIUM | P1 |
| Crest summon/recall | HIGH | LOW | P1 |
| Torch auto-placement | HIGH | LOW | P2 |
| Work tasks (mining/farming/fishing) | HIGH | HIGH | P2 |
| Signpost patrol routes | MEDIUM | MEDIUM | P2 |
| Task queuing | HIGH | MEDIUM | P2 |
| Personality chat lines | MEDIUM | LOW | P2 |
| MineColonies compat | HIGH | LOW | P2 |
| Jade overlay | LOW | LOW | P2 |
| Curios integration | MEDIUM | LOW | P2 |
| Mounted combat | MEDIUM | HIGH | P3 |
| Container interaction | MEDIUM | MEDIUM | P3 |
| Custom armor textures (tiered) | MEDIUM | MEDIUM | P3 |
| Custom weapons | LOW | MEDIUM | P3 |
| Data-driven entity tags | MEDIUM | MEDIUM | P3 |
| /squire commands | LOW | LOW | P3 |

**Priority key:**
- P1: Must have for launch
- P2: Add after core is validated and stable
- P3: Future milestone or v2

---

## Competitor Feature Analysis

| Feature | Human Companions | Modern Companions | Squire v2 |
|---------|-----------------|-------------------|-----------|
| Navigation | Teleports at distance | Teleports at ~35 blocks | Walks always, no teleport ever |
| Follower count | Multiple | Multiple (no cap) | One (single, deep relationship) |
| Progression | Every 3 kills = +1 HP | Infinite levels, MMO XP curve | 5 tiers, 30 levels, named milestones |
| Work tasks | None | None | Mining, farming, fishing, patrols, torch placement |
| Mount support | None | None | Horse riding + mounted combat |
| Waypoint patrol | None | Radius-based (2-32 blocks) | Signpost-defined named routes |
| Combat classes | Knight, Archer, Axeguard | 14 named classes | Single squire, behavior-driven combat tactics |
| Personality | None | Morale meter + bond track | Chat lines + idle behavior, no meter to manage |
| Item storage | Basic carry | 6x9 inventory | Tiered 9-36 slots + container interaction |
| Control UX | Shift-right-click toggle | GUI with stance cycle | Radial menu (R key) + /squire commands |
| Summoning | Recruitment from world | Soul gem | Crest item |
| Death handling | Items lost (players complain) | Resurrection scroll (nether star) | Drops gear, retains tier/XP |
| Config options | Minimal | Minimal | 50+ TOML values |
| Data-driven | Hardcoded | Hardcoded | Entity tags + JSON datapacks |
| 1.21.1 NeoForge | No (1.20.1 only) | Yes | Yes |

---

## Sources

- [Modern Companions — CurseForge](https://www.curseforge.com/minecraft/mc-mods/modern-companions)
- [Modern Companions — Modrinth](https://modrinth.com/mod/modern-companions)
- [Modern Companions — GitHub (STRHercules)](https://github.com/STRHercules/ModernCompanions)
- [Human Companions — CurseForge](https://www.curseforge.com/minecraft/mc-mods/human-companions)
- [MineColonies Citizens and AI — DeepWiki](https://deepwiki.com/ldtteam/minecolonies/2.2-citizens-and-ai)
- [MineColonies Combat and Defense — DeepWiki](https://deepwiki.com/ldtteam/minecolonies/3.3-combat-and-defense)
- [Taterzens — Modrinth](https://modrinth.com/mod/taterzens)
- [Companion-Friend — Modrinth](https://modrinth.com/mod/companion-friend)
- [Player Companions — CurseForge](https://www.curseforge.com/minecraft/mc-mods/player-companions)
- Squire-mod v0.5.0 feature set (PROJECT.md — validated in production on ATM10 6.2)

---

*Feature research for: Minecraft NeoForge 1.21.1 companion/follower mod (squire-mod v2)*
*Researched: 2026-04-02*

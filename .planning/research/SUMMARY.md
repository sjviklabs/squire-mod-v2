# Project Research Summary

**Project:** squire-mod-v2
**Domain:** NeoForge 1.21.1 companion entity mod (custom AI, Geckolib rendering, data-driven design)
**Researched:** 2026-04-02
**Confidence:** HIGH (architecture derived from v0.5.0 source; stack verified against official docs and package repositories)

## Executive Summary

Squire v2 is a complete architectural rebuild of a working NeoForge 1.21.1 companion mod — not a greenfield project. The v0.5.0 source is the primary reference, and its failure modes (600-line god-class entity, TamableAnimal conflicts, hardcoded mob checks, config validation loops) directly dictate the v2 architecture. The build strategy is PathfinderMob base class with a custom tick-rate FSM owned by a dedicated SquireBrain class, Geckolib 4.x for rendering, and data-driven progression via JSON datapacks and entity type tags. This combination is well-documented, ATM10-tested at 449 mods, and has no serious alternatives.

The feature differentiation is clear. The companion mod market splits between dead-simple pets and complex multi-follower RPG systems. Squire v2 occupies a third lane: one deeply personal companion with autonomous work behavior and meaningful 5-tier progression. No other 1.21.1 NeoForge mod does this combination. The critical design constraint is no-teleport navigation — the squire always walks. This is the primary identity of the mod and the source of its primary implementation risk. Pathfinding robustness (doors, water, caves, stuck recovery) must be treated as a P0 concern, not a polish task.

The most dangerous pitfalls are concentrated in Phases 1 and 2: SynchedEntityData registration on the wrong class causes silent network desync; Geckolib cache setup is required boilerplate with fatal consequences if skipped; the config validator correction loop was a documented v0.5.0 regression; and NeoForge 1.21.1 is EOL with a known datapack desync bug that requires embedding a builtin datapack from day one. Get these right in the foundation and the rest of the build is well-understood territory.

---

## Key Findings

### Recommended Stack

The stack is ATM10-pinned and largely non-negotiable. NeoForge 21.1.221 is the exact build shipped in ATM10 6.2 — compiling against any other minor build risks runtime API mismatches. Geckolib 4.8.3 (or 4.7.6 as a safe fallback) is the only viable 3D animation library for custom NeoForge entities; the alternatives either target vanilla entity overrides (EMF), target the player entity specifically (PlayerAnimator), or require writing custom render layers with no keyframe support (vanilla HumanoidModel). Curios API 9.5.1 is the NeoForge equipment slot standard and is already present in ATM10. ModDevGradle 2.x is the official recommendation over NeoGradle for single-version mods.

**Core technologies:**

- **NeoForge 21.1.221:** Mod loader — pinned to ATM10's exact build; do not upgrade
- **Java 21 LTS:** Required by Mojang; record types and pattern matching available
- **Geckolib 4.8.3:** Entity rendering and animation — only production-ready option for custom entities; `.geo.json` + Blockbench workflow
- **ModDevGradle 2.0.141+:** Build toolchain — simpler than NeoGradle for single-version mods; shipped with MDK
- **Curios API 9.5.1+1.21.1:** Equipment slots — compile-only dep; ATM10 standard; soft runtime dep
- **Jade 15.10.4+neoforge:** In-world status overlay — compile-only, optional runtime
- **MineColonies API 1.1.1231-1.21.1:** Compat shim — API jar only; guard all calls with ModList check

**What to avoid explicitly:** TamableAnimal as base class (goal conflicts), GoalSelector for AI routing (priority-queue semantics incompatible with FSM), SimpleContainer for inventory (no IItemHandler surface), Geckolib 3.x (EOL, incompatible API), NeoForge HEAD (not 21.1.221).

### Expected Features

The feature set is well-researched against 5 comparable mods. The market gap is real: no 1.21.1 NeoForge mod offers a single companion with autonomous work behaviors and named progression milestones. The MVP scope is clear from feature dependency analysis — everything gates on persistence and the FSM being stable first.

**Must have (table stakes):**

- Reliable follow behavior (no teleport — this is identity, not just a feature)
- Combat AI with melee, ranged, and flee-at-low-HP behavior
- Auto-equip best gear from inventory
- Persistent identity: named, survives server restart, survives death with XP/tier retained
- Inventory system with tier-gated slot expansion (9–36 slots)
- Sit/stay and follow/guard stance toggle
- Radial menu (R key) for command access
- Summon/recall via Crest item
- 50+ TOML config values for modpack operator tuning
- MineColonies compatibility shim

**Should have (competitive differentiators):**

- 5-tier progression with named milestones (Servant → Champion), JSON-datapack XP curves
- Autonomous work tasks: torch placement, mining, farming, fishing
- Signpost patrol routes (player-defined waypoints via placeable block)
- Task queuing ("mine here, then farm here, then return")
- Personality: tier-specific chat lines and idle behavior
- Jade/WAILA status overlay
- Curios/Accessories equipment slot integration
- Data-driven combat tactics via entity type tags (modpack compat)

**Defer (v2+):**

- Mounted combat — high complexity, conflicts with patrol; validate base combat first
- Container interaction (deposit/withdraw) — needs stable inventory + task queue
- Custom tiered armor textures — defer until model is finalized
- Multiple squires per player — anti-feature for v2; single deep companion is the design

### Architecture Approach

The architecture is a direct response to v0.5.0's technical debt. The core principle: `SquireEntity` is what the squire IS in Minecraft's type system (lifecycle, NBT, synched data, capabilities); `SquireBrain` is what the squire DOES (FSM, handlers, event bus). These never mix. All 14 behaviors are isolated into handler classes that own their own mutable state — no behavior fields in the entity class. The FSM uses per-transition tick rates to avoid running expensive AABB scans every tick in a 449-mod pack. An internal lightweight event bus (not NeoForge's game bus) coordinates handler-to-handler reactions without coupling or global broadcast overhead.

**Major components:**

1. **SquireEntity (PathfinderMob)** — entity lifecycle, NBT save/load, SynchedEntityData, capability registration, vanilla goal stubs (FloatGoal only); owns SquireBrain
2. **SquireBrain + TickRateStateMachine** — FSM engine with per-transition tick rates and priorities; owns all 14 handler instances; routes player commands to handlers
3. **14 BehaviorHandlers** — one class per behavior; CombatHandler, FollowHandler, MiningHandler, TorchHandler, PatrolHandler, etc.; each owns its mutable state
4. **SquireItemHandler (IItemHandler)** — tier-gated inventory capacity; capability-registered; never SimpleContainer
5. **ProgressionHandler + SquireDataAttachment** — XP accounting, level gates, attribute modifiers, cross-death persistence via AttachmentType + Codec on player entity
6. **SquireRegistry** — single class with all DeferredRegister instances; called first in mod constructor; explicit registration order
7. **DatapackLoader + entity tag JSON** — JSON progression curves loaded via SimpleJsonResourceReloadListener; entity type tags for data-driven combat tactics
8. **SquireRenderer (GeoEntityRenderer) + SquireModel** — Geckolib rendering; AnimatableInstanceCache required boilerplate; armor layers via GeoLayerRenderer
9. **CompatLayer** — isolated compat classes for MineColonies, Jade, Curios; all guarded by ModList.isLoaded(); no hard compile-time optional deps

### Critical Pitfalls

1. **SynchedEntityData defineId() on wrong class** — Silent client/server desync; IDs shift when any parent class changes. Always pass `SquireEntity.class` as first arg; define all accessors in SquireEntity only. Establish this pattern in Phase 1 before any synced data is added.

2. **AnimatableInstanceCache not created correctly** — NPE in renderer or animations invisible to other players. `AnimatableInstanceCache` must be a final field created via `GeckoLibUtil.createInstanceCache(this)` in the entity constructor. For Geckolib items (Crest, weapons), call `SingletonGeoAnimatable.registerSyncedAnimatable(this)` in the item constructor. Non-negotiable boilerplate.

3. **NeoForge 1.21.1 datapack desync bug (issue #857)** — Progression data reverts to defaults after world reload. Platform is EOL, no fix coming. Mitigation: embed the mod's own datapack as builtin resources via `DataGenerator#getBuiltinDatapack`. Do this in Phase 1 architecture — not a retrofit.

4. **Entity type tags read during FML setup** — Tags are loaded as server data, not at mod load time. Any combat tactics system that reads `entity.getType().is(tag)` during setup gets empty results. Resolve tactics at first combat engagement, never in setup handlers or field initializers.

5. **Config validator correction loop** — Default values that don't satisfy their own validator trigger an infinite correction log flood. Documented v0.5.0 regression. Validate every default against its own validator before shipping any config entry; run a startup assertion pass.

6. **Geckolib + Iris/Oculus shaders (ATM10 ships Oculus)** — Entities invisible or T-posed with shaders active. Use Geckolib 4.7.5.1+ (covers 4.8.3). Test specifically in ATM10 with Oculus enabled before calling rendering done — vanilla dev environment will not catch this.

7. **IItemHandler slot mutation via getStackInSlot()** — The NeoForge contract: returned stack must not be modified directly. Always use `extractItem()`/`insertItem()`. Violation causes item duplication or corruption when hopper/pipe automation is involved.

---

## Implications for Roadmap

The ARCHITECTURE.md build order is the recommended phase structure. It's derived from the actual dependency graph of v0.5.0's source, not abstract planning. Follow it. The main addition is treating rendering as parallelizable with combat (Phase 3/4) rather than sequential.

### Phase 1: Core Entity Foundation

**Rationale:** Everything downstream depends on entity registration, persistence, inventory, and config being correct. SynchedEntityData pitfall, config validation loop, and datapack desync bug must all be addressed here — retrofitting any of these is expensive.
**Delivers:** SquireEntity (PathfinderMob, no real AI), SquireRegistry, SquireDataAttachment (cross-death persistence), SquireItemHandler (IItemHandler, tier-aware), SquireConfig (TOML, all defaults validated), builtin datapack embed.
**Addresses:** NBT persistence (table stakes), inventory system (P1), config TOML (P1).
**Avoids:** SynchedEntityData desync (Pitfall 1), config correction loop (Pitfall 10), datapack desync (Pitfall 13), PathfinderMob navigation (Pitfall 9 — set up `setCanFloat`, OpenDoorGoal here).

### Phase 2: Brain + FSM + Follow

**Rationale:** The FSM skeleton must exist before any behavior can be built. Follow is the simplest real behavior and the first dependency everything else chains from. SurvivalHandler (eating, healing) belongs here because it's higher priority than combat.
**Delivers:** TickRateStateMachine, SquireAIState enum, AITransition, SquireBrainEventBus, FollowHandler (no-teleport, stuck recovery, water/door traversal), SurvivalHandler. Basic sit/stay toggle. Squire walks with player.
**Addresses:** Follow behavior (P1 table stakes), sit/stay toggle (P1).
**Uses:** PathfinderMob navigation configured in Phase 1.
**Research flag:** No deeper research needed — patterns are well-documented and partially implemented in v0.5.0.

### Phase 3: Rendering

**Rationale:** Can parallelize with Phase 2 if bandwidth allows. Geckolib setup is isolated from behavior logic. Must be done before combat (attack animation duration pitfall requires Phase 3 done). Validate with Oculus/shaders in ATM10 before declaring done.
**Delivers:** SquireRenderer (GeoEntityRenderer), SquireModel (.geo.json), AnimatableInstanceCache, locomotion and idle animation controllers, armor layer stub. Entity visible and animated in ATM10 with shaders.
**Avoids:** Geckolib cache NPE (Pitfall 2), Iris/Oculus incompatibility (Pitfall 4), AnimationTest vs AnimationState naming (Pitfall 11).
**Research flag:** Blockbench model authoring is manual work, not a research gap. Geckolib patterns are HIGH confidence. No research-phase needed.

### Phase 4: Combat + Progression

**Rationale:** Combat depends on rendering (attack animation pitfall) and the FSM (Phase 2). Progression unlocks are gated on combat. Entity tag tactics system must be initialized lazily here — not during FML setup.
**Delivers:** CombatHandler (melee, ranged, flee-at-low-HP), DangerHandler (explosive threats), ProgressionHandler (XP, level, attributes), SquireTagKeys + entity type tag JSON (data-driven tactics), ProgressionDataLoader (JSON datapack loader), auto-equip logic.
**Addresses:** Combat AI (P1), progression system (P1), auto-equip (P1), data-driven tactics (P3 but architecturally required here).
**Avoids:** Attack animation duration (Pitfall 3), entity tag loading timing (Pitfall 7), capability invalidation on tier change (Pitfall 6).
**Research flag:** No deeper research needed — patterns proven in v0.5.0.

### Phase 5: Inventory UI + Radial Menu

**Rationale:** Requires stable SquireItemHandler (Phase 1) and a working FSM (Phase 2). Network payload infrastructure goes here. Radial menu ties all stances together — can't be meaningful until stances exist.
**Delivers:** SquireMenu (AbstractContainerMenu), SquireScreen (inventory UI), SquireRadialScreen (R key), SquireCommandPayload and SquireModePayload (network), Crest summon/recall item.
**Addresses:** Inventory access (table stakes), radial menu (P1), Crest summon (P1).
**Avoids:** Dedicated server crash risk (client-side class references) — test on dedicated server before shipping Phase 5.

### Phase 6: Work Behaviors

**Rationale:** Depends on stable inventory (items need somewhere to go), follow behavior (squire must navigate to work location), and FSM (task handlers slot into brain). These are the differentiators that separate squire from "bodyguard mod."
**Delivers:** TorchHandler, MiningHandler, PlacingHandler, FarmingHandler, FishingHandler, ItemHandler (pickup), task queuing (queue data structure in SquireBrain). Personality chat lines (ChatHandler).
**Addresses:** Torch placement (P2, highest perceived value per effort), work tasks (P2), task queuing (P2), personality (P2).
**Research flag:** FishingHandler may need research during planning — vanilla fishing mechanics have changed across versions; verify bobber entity behavior on 1.21.1.

### Phase 7: Advanced Behaviors + Signpost

**Rationale:** ChestHandler depends on inventory + task queue. PatrolHandler depends on SignpostBlock (new block registration). MountHandler is a follow variant. These are P3 features that complete the mod but don't block launch.
**Delivers:** SignpostBlock + SignpostBlockEntity (waypoint registration), PatrolHandler (named route walking), ChestHandler (deposit/withdraw task), MountHandler (horse riding variant of follow).
**Addresses:** Signpost patrol routes (P2), container interaction (P3), mounted combat stub.
**Research flag:** MountHandler may need research during planning — mounted entity pathfinding is not well-documented for PathfinderMob on 1.21.1; mounted combat adds additional complexity.

### Phase 8: Compatibility + Polish

**Rationale:** Compat is last because you need stable behaviors to test against. MineColonies clearance testing requires an active colony world. Jade and Curios are additive, not load-bearing.
**Delivers:** MineColoniesCompat (friendly-fire gate, clearance prevention, MobCategory.MISC), JadeCompat (status overlay provider), CuriosCompat (equipment slot registration). Full "looks done but isn't" checklist pass.
**Avoids:** MineColonies citizen AI interference (Pitfall 12), Curios integration gaps (Pitfall gotcha).
**Research flag:** MineColonies whitelist API — verify current API surface in 1.1.1231 before implementing compat. The maintainer's guidance in issue #4343 covers the dep approach but not the whitelist API specifics.

### Phase Ordering Rationale

- Phases 1–2 are strictly sequential: registration before behavior, behavior skeleton before specific behaviors.
- Phase 3 (rendering) can run in parallel with Phase 2 if two people are working; otherwise do it after Phase 2.
- Phases 6 and 7 gate on Phase 5 (inventory UI must be testable before work tasks make sense to validate).
- Phase 8 is always last — compat testing requires a feature-complete mod to test against.
- The no-teleport navigation constraint means Phase 2's FollowHandler must be robust before anything else ships — a stuck squire undermines every downstream feature.

### Research Flags

Phases likely needing `/gsd:research-phase` during planning:

- **Phase 6 (FishingHandler):** Vanilla fishing bobber mechanics on 1.21.1 need verification; changed across versions.
- **Phase 7 (MountHandler):** Mounted PathfinderMob navigation is sparsely documented for NeoForge 1.21.1; mounted combat adds another layer.
- **Phase 8 (MineColonies whitelist):** The whitelist/compat API in MineColonies 1.1.1231 needs specific research; general guidance is known, API surface is not.

Phases with standard patterns (skip research-phase):

- **Phase 1:** Entity registration, NBT, IItemHandler, ModDevGradle config — all HIGH confidence, well-documented.
- **Phase 2:** FollowHandler and FSM patterns — proven in v0.5.0 source.
- **Phase 3:** Geckolib 4.x entity setup — official wiki is comprehensive and HIGH confidence.
- **Phase 4:** Combat AI patterns — v0.5.0 source + NeoForge docs give complete picture.
- **Phase 5:** NeoForge menu/screen/network patterns — standard, well-documented.

---

## Confidence Assessment

| Area         | Confidence | Notes                                                                                  |
| ------------ | ---------- | -------------------------------------------------------------------------------------- |
| Stack        | HIGH       | Versions verified via CurseForge/Modrinth/Maven; ATM10 pin confirmed; one MEDIUM: Geckolib 4.8.3 on Maven (4.7.6 safe fallback) |
| Features     | HIGH       | 5 comparable mods researched; market gap confirmed; feature dependency graph complete  |
| Architecture | HIGH       | Derived directly from v0.5.0 source; NeoForge/Geckolib patterns verified against official docs |
| Pitfalls     | MEDIUM     | NeoForge 1.21.1 is EOL, some pitfalls from community issue trackers; Geckolib-specific pitfalls confirmed on wiki; platform-level bugs may exist that aren't yet documented |

**Overall confidence:** HIGH

### Gaps to Address

- **Geckolib 4.8.3 Maven artifact:** CurseForge confirms the file exists; Maven indexing shows 4.7.6 most indexed. Verify 4.8.3 coordinates resolve before pinning in build.gradle. 4.7.6 is a confirmed safe fallback.
- **Fishing mechanics (1.21.1):** Bobber entity behavior and the loot table trigger path for fishing on NeoForge 1.21.1 should be validated before implementing FishingHandler. Research during Phase 6 planning.
- **MountHandler pathfinding:** No documented pattern for PathfinderMob-based mounted navigation on NeoForge 1.21.1. This may require reading vanilla horse entity source or community forum research before Phase 7 design.
- **MineColonies whitelist API specifics:** Issue #4343 confirms the API jar approach; the specific method signature for entity whitelisting in 1.1.1231 needs verification during Phase 8 planning.
- **Datapack JSON merge semantics:** Decision needed before writing any JSON loading code — per-entry files (one JSON per tier, additive) vs. single file (whole file replaced on override). Recommend per-entry to enable surgical operator overrides.

---

## Sources

### Primary (HIGH confidence)

- v0.5.0 source — `C:/Users/Steve/Projects/squire-mod/src/` (read directly; architecture derived from here)
- [NeoForge official docs — 1.21.1](https://docs.neoforged.net/docs/1.21.1/) — entity data, capabilities, tags, mod files, data attachments
- [Geckolib wiki — Geckolib 4 entity setup](https://github.com/bernie-g/geckolib/wiki/Geckolib-Entities-(Geckolib4)) — GeoEntity, GeoEntityRenderer, AnimatableInstanceCache
- [Geckolib wiki — Geckolib 4 Changes](https://github.com/bernie-g/geckolib/wiki/Geckolib-4-Changes) — AnimationTest rename from AnimationState
- [NeoForgeMDKs — MDK-1.21.1-ModDevGradle](https://github.com/NeoForgeMDKs/MDK-1.21.1-ModDevGradle) — build toolchain
- [CurseForge — Curios API files](https://www.curseforge.com/minecraft/mc-mods/curios/files/all) — 9.5.1+1.21.1 confirmed
- [GitHub — MineColonies issue #4343](https://github.com/ldtteam/minecolonies/issues/4343) — API jar approach

### Secondary (MEDIUM confidence)

- [CurseForge — Geckolib files for 1.21.1](https://www.curseforge.com/minecraft/mc-mods/geckolib/files/all) — 4.8.3 file present; Maven indexing lags
- [Modern Companions — GitHub](https://github.com/STRHercules/ModernCompanions) — competitor feature analysis
- [Human Companions — CurseForge](https://www.curseforge.com/minecraft/mc-mods/human-companions) — competitor table stakes
- [MineColonies Citizens and AI — DeepWiki](https://deepwiki.com/ldtteam/minecolonies/2.2-citizens-and-ai) — FSM pattern reference
- [NeoForge issue #857](https://github.com/neoforged/NeoForge/issues/857) — datapack desync bug (EOL, won't fix)
- [Geckolib issue #675](https://github.com/bernie-g/geckolib/issues/675), [issue #245](https://github.com/bernie-g/geckolib/issues/245) — Iris/Oculus and invisible entity pitfalls
- [MineColonies issue #10440](https://github.com/ldtteam/minecolonies/issues/10440) — entity interference pattern

### Tertiary (LOW confidence)

- [Taterzens — Modrinth](https://modrinth.com/mod/taterzens) — NPC framework patterns; no combat AI analog
- [Companion-Friend — Modrinth](https://modrinth.com/mod/companion-friend) — personality/dialogue patterns; small mod, sparse docs

---

_Research completed: 2026-04-02_
_Ready for roadmap: yes_

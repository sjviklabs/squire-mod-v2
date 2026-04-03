# Roadmap: Squire Mod v2

## Overview

Eight phases derived from the actual dependency graph of the build. Phases 1-2 are strictly sequential (you can't build behavior without a registered entity, and you can't build behavior handlers without a brain). Phase 3 (rendering) runs after Phase 2 but is independent of combat, enabling parallelism if bandwidth allows. Phases 4-7 build on the stable foundation in dependency order. Phase 8 is always last — compatibility testing requires a feature-complete mod to test against.

## Phases

**Phase Numbering:**

- Integer phases (1, 2, 3): Planned milestone work
- Decimal phases (2.1, 2.2): Urgent insertions (marked with INSERTED)

Decimal phases appear between their surrounding integers in numeric order.

- [x] **Phase 1: Core Entity Foundation** - Register the squire, establish persistence, inventory, and config — everything downstream depends on these being correct (completed 2026-04-03)
- [ ] **Phase 2: Brain, FSM, and Follow** - The state machine skeleton and the first real behavior — squire walks with the player, never teleports
- [ ] **Phase 3: Rendering** - Geckolib entity model, animations, skin variants, armor layers — squire looks like a squire
- [ ] **Phase 4: Combat and Progression** - Melee, ranged, flee, auto-equip, 5-tier XP system, data-driven tactics — squire fights and grows
- [ ] **Phase 5: UI and Controls** - Inventory screen, radial menu, commands, Crest item — player can control and equip the squire
- [ ] **Phase 6: Work Behaviors** - Mining, farming, fishing, torch placement, task queuing, personality — squire is a working companion, not just a bodyguard
- [ ] **Phase 7: Patrol and Mounting** - Signpost waypoints, patrol routes, horse riding, mounted combat — squire handles advanced autonomous tasks
- [ ] **Phase 8: Compatibility and Polish** - MineColonies shim, Jade overlay, Curios slots, Oculus shader validation — squire coexists in ATM10's 449-mod environment

## Phase Details

### Phase 1: Core Entity Foundation

**Goal**: A registered, persistent squire entity exists in the world with a functional inventory and fully validated config
**Depends on**: Nothing (first phase)
**Requirements**: ENT-01, ENT-02, ENT-03, ENT-04, ENT-05, ENT-06, ENT-07, INV-01, INV-02, INV-06, ARC-04, ARC-05, ARC-06, ARC-07, ARC-09, TST-01, TST-02
**Success Criteria** (what must be TRUE):

1. Player can use the Crest item to spawn a squire and despawn it; only one squire exists per player at any time
2. Squire data (name, XP, tier) survives a server restart and survives the player dying with the Crest in inventory
3. Squire's tiered inventory (9 slots at Servant) is accessible and implemented as IItemHandler capability — items inserted via pipe or hopper behave correctly
4. squire-common.toml generates on first run with 50+ entries, all defaults satisfy their own validators (no correction loop in startup log)
5. Builtin datapack embeds correctly — progression JSON is present and does not revert to defaults after world reload

**Plans**: 5 plans

Plans:

- [ ] 01-01: Project scaffold — ModDevGradle build, SquireRegistry, mod entry point, dependency declarations
- [ ] 01-02: SquireEntity (PathfinderMob) — SynchedEntityData, NBT save/load, SquireDataAttachment (cross-death persistence), ENT-05/06/07
- [ ] 01-03: SquireItemHandler (IItemHandler) — tier-gated capacity, capability registration, GUI stub, ARC-09 chunk loading hook
- [ ] 01-04: SquireConfig (TOML) — 50+ entries, validator correctness assertion, ARC-07 builtin datapack embed
- [ ] 01-05: Test harness — JUnit 5 unit test skeleton, NeoForge GameTest scaffolding, TST-01/TST-02

### Phase 2: Brain, FSM, and Follow

**Goal**: The squire walks with the player through any terrain without ever teleporting
**Depends on**: Phase 1
**Requirements**: NAV-01, NAV-02, NAV-03, NAV-04, NAV-05, ARC-01, ARC-02, ARC-03, ARC-08
**Success Criteria** (what must be TRUE):

1. Squire follows the player through caves, across water, and through doors without teleporting under any circumstances
2. Squire sprints to close the gap when the player moves fast; slows to walk when within close range
3. Squire can be toggled to sit/stay via command; it stops following and holds position until released
4. Handler-to-handler events work — a handler can notify other handlers of state changes without coupling to them directly
5. All network payloads use StreamCodec (ARC-08); no raw FriendlyByteBuf serialization in any handler

**Plans**: 4 plans

Plans:

- [ ] 02-01-PLAN.md — FSM engine: SquireAIState (27 states), AITransition record, TickRateStateMachine port, SquireEntity helper methods
- [ ] 02-02-PLAN.md — Event bus: SquireEvent enum, SquireBrainEventBus, SquireBrain real container with SITTING transitions
- [ ] 02-03-PLAN.md — FollowHandler: no-teleport navigation, sprint/walk scaling, stuck detection + jump recovery, FOLLOWING_OWNER transitions
- [ ] 02-04-PLAN.md — SurvivalHandler + sit/stay + StreamCodec: eating via IItemHandler.extractItem(), CMD_STAY/FOLLOW payloads, payload registration

### Phase 3: Rendering

**Goal**: The squire is visually correct with animations in ATM10 — including with Oculus shaders active
**Depends on**: Phase 2
**Requirements**: RND-01, RND-02, RND-03, RND-04, RND-05, RND-06, INV-04
**Success Criteria** (what must be TRUE):

1. Squire entity renders with a Geckolib model and plays locomotion, idle, and attack animations visible to all players (not just the owner)
2. Male and female skin variants (slim/wide) display correctly and are selectable
3. Backpack visual layer scales with inventory tier — Servant has a small pack, Champion has a large one
4. Custom 4-piece armor set renders on the squire with tier-appropriate textures
5. All rendering works correctly in ATM10 with Oculus shaders enabled — no T-pose, no invisible entity

**Plans**: 4 plans

Plans:

- [ ] 03-01-PLAN.md — Geckolib setup: GeoEntity on SquireEntity, SquireModel, SquireRenderer, SquireClientEvents, AnimatableInstanceCache final field
- [ ] 03-02-PLAN.md — Model and assets: squire_male/female.geo.json, squire.animation.json, placeholder textures (skins + 8 tiered armor PNGs)
- [ ] 03-03-PLAN.md — Armor and backpack layers: SquireArmorLayer (ItemArmorGeoLayer, tiered textures), SquireBackpackLayer (GeoRenderLayer, bone visibility), SquireArmorItem x4
- [ ] 03-04-PLAN.md — Naming, personality, and Oculus validation: shouldShowName(), name tag mobInteract(), ChatHandler stub, Oculus checkpoint

### Phase 4: Combat and Progression

**Goal**: The squire fights hostile mobs intelligently and grows through 5 tiers via shared experience
**Depends on**: Phase 3
**Requirements**: CMB-01, CMB-02, CMB-03, CMB-04, CMB-05, CMB-06, CMB-07, CMB-08, CMB-09, CMB-10, PRG-01, PRG-02, PRG-03, PRG-04, PRG-05, PRG-06
**Success Criteria** (what must be TRUE):

1. Squire engages hostile mobs with melee, switches to bow at range, blocks with shield, and flees when HP drops below the configured threshold
2. Squire auto-equips the best weapon and armor from its inventory — switching mid-combat when better gear is picked up
3. Halberd deals sweep AoE on every 3rd hit; custom shield has 336 durability — both items exist and function
4. Squire earns XP from kills and advances through all 5 tiers; tier thresholds come from JSON datapack (not hardcoded)
5. Combat tactics resolve lazily at first engagement from entity type tags — no hardcoded mob instance checks anywhere in CombatHandler

**Plans**: 5 plans

Plans:

- [ ] 04-01: CombatHandler — melee engagement, ranged switching, shield blocking, flee-at-low-HP (CMB-01/02/05/06), DangerHandler (explosive threats)
- [ ] 04-02: Auto-equip + custom items — CMB-03/04 best-gear selection, CMB-08 Halberd (sweep AoE), CMB-09 custom shield, CMB-10 guard mode
- [ ] 04-03: Data-driven tactics — SquireTagKeys, entity type tag JSON (CMB-07), lazy resolution at first combat engagement (not FML setup)
- [ ] 04-04: ProgressionHandler — XP accounting (PRG-02), level gates (PRG-05), attribute modifiers per tier, PRG-06 Champion undying ability
- [ ] 04-05: Progression datapacks — PRG-01/03/04 JSON datapack loader (SimpleJsonResourceReloadListener), 5-tier definitions, 6 ability unlock definitions

### Phase 5: UI and Controls

**Goal**: Player can control the squire, access its inventory, and equip it via GUI and commands
**Depends on**: Phase 4
**Requirements**: GUI-01, GUI-02, GUI-03, INV-03, INV-05
**Success Criteria** (what must be TRUE):

1. Radial menu opens with R key, shows available stances and commands, sends correct FSM state changes to the server
2. /squire commands work for info display, mining target, placement target, and other operations
3. Squire's inventory screen shows equipment slots and backpack grid; player can drag items between their inventory and the squire's
4. Squire picks up nearby dropped items, filtered against the configured junk list (INV-03)
5. All UI code is clean of client-side class references in server paths — mod loads without crash on a dedicated server

**Plans**: 4 plans

Plans:

- [ ] 05-01: SquireMenu + SquireScreen — AbstractContainerMenu, inventory GUI with equipment slots and backpack grid, GUI-03
- [ ] 05-02: SquireRadialScreen — R key radial menu, stance icons, server-bound command payloads, GUI-01
- [ ] 05-03: Commands and Crest item — /squire command tree (GUI-02), Crest summon/recall item (ENT-01/02), dedicated server safety pass
- [ ] 05-04: Item pickup — INV-03 nearby item pickup with junk filtering, pickup range from config

### Phase 6: Work Behaviors

**Goal**: Squire performs autonomous work tasks and queues multiple sequential commands
**Depends on**: Phase 5
**Requirements**: WRK-01, WRK-02, WRK-03, WRK-04, WRK-05, WRK-06, WRK-07, WRK-08
**Success Criteria** (what must be TRUE):

1. Squire mines a targeted single block and places a targeted block on command; deposits mined items into its inventory
2. Squire performs area clear (multi-block mining) and auto-places torches when ambient light drops below the configured threshold
3. Squire farms (tills soil, plants seeds, harvests crops) and fishes (simulated fishing with correct 1.21.1 loot table trigger)
4. Squire interacts with containers — deposits excess inventory into a target chest, withdraws requested items
5. Player can queue multiple work commands in sequence; squire executes them in order and reports completion via chat

**Plans**: 4 plans

Plans:

- [ ] 06-01: TorchHandler + MiningHandler — WRK-01/02/03, light-level check, torch placement, single and area clear mining, ARC-09 chunk loading during area clear
- [ ] 06-02: FarmingHandler + FishingHandler — WRK-04/05, tilling/harvest cycle, 1.21.1 bobber entity fishing (verify loot trigger path before implementing)
- [ ] 06-03: PlacingHandler + ChestHandler — WRK-06/07, block placement on command, container deposit/withdraw
- [ ] 06-04: Task queue + personality — WRK-08 task queue data structure in SquireBrain, RND-04 personality chat lines (idle, combat, level-up, new tier)

### Phase 7: Patrol and Mounting

**Goal**: Squire patrols defined waypoint routes and rides a horse, including mounted combat
**Depends on**: Phase 6
**Requirements**: PTR-01, PTR-02, PTR-03, MNT-01, MNT-02, MNT-03, MNT-04
**Success Criteria** (what must be TRUE):

1. Signpost block can be placed in the world; player can assign it to a named patrol route and set the order
2. Squire walks a defined patrol route between signpost waypoints without getting stuck; resumes patrol after combat
3. Squire mounts a nearby horse on command and follows the player while mounted at horse speed
4. Squire engages mobs in combat while mounted; dismounts when the horse is killed or when commanded
5. Horse UUID persists in NBT — squire returns to the same horse after server restart

**Plans**: 4 plans

Plans:

- [ ] 07-01: SignpostBlock + SignpostBlockEntity — PTR-01/03, block registration, route name storage, waypoint ordering UI
- [ ] 07-02: PatrolHandler — PTR-02, named route walking, post-combat patrol resume, stuck recovery on route
- [ ] 07-03: MountHandler — MNT-01/02/04, horse mount/dismount, follow-while-mounted, horse UUID NBT persistence
- [ ] 07-04: Mounted combat — MNT-03, combat engagement while mounted (verify PathfinderMob mounted navigation before implementing)

### Phase 8: Compatibility and Polish

**Goal**: Squire coexists cleanly in ATM10's 449-mod environment with no entity conflicts, WAILA overlay, and Curios slots
**Depends on**: Phase 7
**Requirements**: CMP-01, CMP-02, CMP-03, INV-05
**Success Criteria** (what must be TRUE):

1. MineColonies colony world runs alongside the squire mod with no citizen/squire AI interference or friendly-fire; squire uses MobCategory.MISC
2. Jade/WAILA shows a squire status tooltip (name, tier, HP, current task) when the player looks at the squire
3. Curios/Accessories equipment slots are registered and functional when Curios is present; mod loads and functions correctly without Curios
4. All three compat providers are guarded by ModList.isLoaded() — no class-not-found errors when optional dependencies are absent

**Plans**: 3 plans

Plans:

- [ ] 08-01: MineColoniesCompat — CMP-01, friendly-fire gate, clearance prevention, MobCategory.MISC, ModList guard (verify whitelist API against MineColonies 1.1.1231 before implementing)
- [ ] 08-02: JadeCompat + CuriosCompat — CMP-02 Jade status overlay provider, CMP-03/INV-05 Curios slot registration, soft-dep guards
- [ ] 08-03: Polish pass — full "looks done but isn't" checklist, Oculus final verification, config validation final pass, dedicated server smoke test

## Progress

**Execution Order:**
Phases execute in numeric order: 1 → 2 → 3 → 4 → 5 → 6 → 7 → 8

| Phase                          | Plans Complete | Status      | Completed |
| ------------------------------ | -------------- | ----------- | --------- |
| 1. Core Entity Foundation      | 5/5 | Complete    | 2026-04-03 |
| 2. Brain, FSM, and Follow      | 0/4            | Planned     | -         |
| 3. Rendering                   | 0/4            | Planned     | -         |
| 4. Combat and Progression      | 0/5            | Not started | -         |
| 5. UI and Controls             | 0/4            | Not started | -         |
| 6. Work Behaviors              | 0/4            | Not started | -         |
| 7. Patrol and Mounting         | 0/4            | Not started | -         |
| 8. Compatibility and Polish    | 0/3            | Not started | -         |

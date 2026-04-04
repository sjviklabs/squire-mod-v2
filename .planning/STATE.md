---
gsd_state_version: 1.0
milestone: v1.0
milestone_name: milestone
status: planning
stopped_at: Completed 08-01-PLAN.md — MineColoniesCompat package-scan guard with zero MC imports
last_updated: "2026-04-04T03:25:48.091Z"
last_activity: 2026-04-02 — Roadmap created, 72 requirements mapped across 8 phases
progress:
  total_phases: 8
  completed_phases: 5
  total_plans: 33
  completed_plans: 29
  percent: 0
---

# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-04-02)

**Core value:** The squire feels like a real companion — it walks everywhere (never teleports), grows through shared experience, and handles itself intelligently in combat and work without the player micromanaging it.
**Current focus:** Phase 1 — Core Entity Foundation

## Current Position

Phase: 1 of 8 (Core Entity Foundation)
Plan: 0 of 5 in current phase
Status: Ready to plan
Last activity: 2026-04-02 — Roadmap created, 72 requirements mapped across 8 phases

Progress: [░░░░░░░░░░] 0%

## Performance Metrics

**Velocity:**

- Total plans completed: 0
- Average duration: -
- Total execution time: 0 hours

**By Phase:**

| Phase | Plans | Total | Avg/Plan |
| ----- | ----- | ----- | -------- |
| -     | -     | -     | -        |

**Recent Trend:**

- Last 5 plans: -
- Trend: -

_Updated after each plan completion_
| Phase 01-core-entity-foundation P01 | 24 | 2 tasks | 10 files |
| Phase 01-core-entity-foundation P02 | 15 | 2 tasks | 6 files |
| Phase 01-core-entity-foundation P04 | 18 | 2 tasks | 7 files |
| Phase 01-core-entity-foundation P03 | 27 | 2 tasks | 4 files |
| Phase 01-core-entity-foundation P05 | 18 | 2 tasks | 5 files |
| Phase 02-brain-fsm-follow P01 | 15 | 2 tasks | 5 files |
| Phase 02-brain-fsm-follow P02 | 12 | 2 tasks | 3 files |
| Phase 02-brain-fsm-follow P03 | 20 | 2 tasks | 2 files |
| Phase 02-brain-fsm-follow P04 | 16 | 2 tasks | 5 files |
| Phase 03-rendering P01 | 20 | 2 tasks | 5 files |
| Phase 03-rendering P02 | 6 | 2 tasks | 13 files |
| Phase 03-rendering P03 | 19 | 2 tasks | 5 files |
| Phase 03-rendering P04 | 8 | 2 tasks | 2 files |
| Phase 04-combat-progression P03 | 8 | 2 tasks | 6 files |
| Phase 04-combat-progression P05 | 12 | 2 tasks | 5 files |
| Phase 04-combat-progression P01 | 20 | 2 tasks | 3 files |
| Phase 04-combat-progression P04 | 20 | 2 tasks | 2 files |
| Phase 04-combat-progression P02 | 15 | 2 tasks | 5 files |
| Phase 05-ui-controls P02 | 13 | 2 tasks | 4 files |
| Phase 05-ui-controls P01 | 39 | 2 tasks | 4 files |
| Phase 05-ui-controls P03 | 25 | 2 tasks | 3 files |
| Phase 05-ui-controls P04 | 8 | 1 tasks | 3 files |
| Phase 06-work-behaviors P02 | 19 | 3 tasks | 8 files |
| Phase 06-work-behaviors P01 | 27 | 3 tasks | 7 files |
| Phase 06-work-behaviors P03 | 12 | 3 tasks | 5 files |
| Phase 07-patrol-mounting P01 | 45 | 2 tasks | 10 files |
| Phase 07-patrol-mounting P02 | 41 | 2 tasks | 5 files |
| Phase 07-patrol-mounting P04 | 11 | 1 tasks | 4 files |
| Phase 08-compatibility-polish P01 | 4 | 2 tasks | 2 files |

## Accumulated Context

### Decisions

Decisions are logged in PROJECT.md Key Decisions table.
Recent decisions affecting current work:

- Foundation: PathfinderMob base class (not TamableAnimal) — avoids vanilla goal conflicts, owner tracking is ~80 lines
- Foundation: Geckolib 4.8.3 for rendering — only production-ready option; 4.7.6 is confirmed safe fallback if 4.8.3 Maven coords don't resolve
- Foundation: Builtin datapack embed from day one — NeoForge 1.21.1 issue #857 (datapack desync) is EOL/won't fix; must embed, not retrofit
- Foundation: Per-entry JSON files for progression data — enables surgical operator overrides without replacing whole file
- [Phase 01-01]: Modrinth Maven URL is api.modrinth.com/maven — maven.modrinth.com DNS does not resolve in lab environment
- [Phase 01-01]: MineColonies 1.21.1 compileOnly dep commented out — ldtteam Jfrog repo has no 1.21.1 artifact; coordinates need Phase 8 research
- [Phase 01-core-entity-foundation]: tryToTeleportToOwner absent from PathfinderMob — kept as plain method for doc clarity, no @Override
- [Phase 01-core-entity-foundation]: itemHandler declared as Object in SquireEntity to avoid forward-reference compile error — Plan 01-03 casts and initializes
- [Phase 01-core-entity-foundation]: ModContainer constructor injection for registerConfig — ModLoadingContext.get() removed that overload in NeoForge 21.1; container parameter is the correct pattern
- [Phase 01-core-entity-foundation]: squire_tier.json filename avoids namespace collision with modid — tier field inside remains 'squire' for SquireTier enum matching
- [Phase 01-core-entity-foundation]: ItemStackHandler at max capacity (42) — getSlots() gates tier access, avoids resize on tier-up
- [Phase 01-core-entity-foundation]: IMenuTypeExtension.create() over new MenuType() — required for RegistryFriendlyByteBuf extra data (entity ID) in NeoForge 21.1.221
- [Phase 01-core-entity-foundation]: onContentsChanged no-op on PathfinderMob — entity NBT auto-saved by level; setChanged() is BlockEntity-only
- [Phase 01-core-entity-foundation]: unitTest { enable() } required in moddev DSL — without it NeoForge/MC/DFU classes are absent from JUnit classpath
- [Phase 01-core-entity-foundation]: TestableItemHandler test double accepts SquireTier directly, bypassing live SquireEntity DeferredHolder requirement
- [Phase 01-core-entity-foundation]: SquireData CODEC test uses JsonOps.INSTANCE headlessly — DFU is pure Java, no NeoForge bootstrap needed
- [Phase 02-brain-fsm-follow]: HurtByTargetGoal used instead of OwnerHurtByTargetGoal — TamableAnimal goal incompatible with PathfinderMob; owner-hurt retaliation deferred to FSM combat transitions
- [Phase 02-brain-fsm-follow]: SquireActivityLog ported to brain package now — TickRateStateMachine.tick() references it; null-safe lazy init via getActivityLog()
- [Phase 02-brain-fsm-follow]: Pose.SITTING/STANDING used instead of setInSittingPose — PathfinderMob has no setInSittingPose (TamableAnimal only)
- [Phase 02-brain-fsm-follow]: SquireBrainEventBus is instance-scoped plain Java EnumMap, not NeoForge IEventBus — no global bus leakage
- [Phase 02-brain-fsm-follow]: pathRecalcInterval absent from SquireConfig — followTickRate is the correct field for path recalc throttle
- [Phase 02-brain-fsm-follow]: Stop transition priority 30 / tick priority 31 — stop check wins on same-tick shouldStop firing to prevent extra follow tick
- [Phase 02-brain-fsm-follow]: eatHealthThreshold absent from SquireConfig — used hardcoded 0.75f in SurvivalHandler; deferred to Phase 4 when eating gets full animation treatment
- [Phase 02-brain-fsm-follow]: EATING per-tick returns IDLE immediately, machine re-evaluates to FOLLOWING_OWNER — avoids storing previous-state in SurvivalHandler
- [Phase 03-rendering]: GeoEntityModel absent in Geckolib 4.8.4 — base class is GeoModel<T>; LoopType is Animation.LoopType (nested); getTextureResource not getTextureLocation on GeoModel
- [Phase 03-rendering]: format_version 1.21.0 for geo.json, 1.8.0 for animation.json — different Bedrock format versions, must not be swapped
- [Phase 03-rendering]: Bone naming contract frozen after 03-02: head/body/right_arm/left_arm/right_leg/left_leg/right_foot/left_foot/backpack_small/backpack_large — 03-03 builds against these exact strings
- [Phase 03-rendering]: getVanillaArmorBuffer override instead of getArmorTexture — getArmorTexture absent in Geckolib 4.8.4; buffer intercept is the correct texture injection hook
- [Phase 03-rendering]: ArmorItem constructor takes Holder<ArmorMaterial> not ArmorMaterial in NeoForge 1.21.1 — ArmorMaterials.IRON is Holder type
- [Phase 03-rendering]: ChatHandler placed in entity/ package (not client/) — server-side send, no client imports
- [Phase 03-rendering]: Oculus validation deferred — user will test in ATM10; Phase 4 start gates on explicit pass confirmation
- [Phase 04-combat-progression]: TagKey.create() with Registries.ENTITY_TYPE used instead of EntityTypeTags.create() — package-internal helper; does not resolve cleanly in NeoForge 21.1 with Parchment mappings
- [Phase 04-combat-progression]: TagKey constants are safe as static fields; .is() calls deferred exclusively to CombatHandler.selectTactic() at runtime after server start
- [Phase 04-combat-progression]: AddReloadListenerEvent (not AddServerReloadListenersEvent) is the correct NeoForge 21.1.221 event; Bus.GAME deprecated — used NeoForge.EVENT_BUS.addListener() inline in constructor
- [Phase 04-combat-progression]: abilities.json is a root-level JSON array; ProgressionDataLoader dispatches by filename stem ('abilities' vs tier name) to handle the mixed schema
- [Phase 04-combat-progression]: Item instanceof checks (BowItem, ShieldItem) retained in CombatHandler for equipment reads — zero mob entity instanceof checks present (the real CMB-07 intent)
- [Phase 04-combat-progression]: DangerHandler is a thin collaborator: detect explosive threat via Creeper.getSwellDir(), delegate startFlee() to CombatHandler — no flee state stored in DangerHandler
- [Phase 04-combat-progression]: invalidateCapabilities() is a no-op stub on SquireEntity — NeoForge 21.1 entity caps are not cached; SquireItemHandler getSlots() gates tier access dynamically
- [Phase 04-combat-progression]: undyingCooldown not persisted to NBT — resets on squire respawn by design
- [Phase 04-combat-progression]: halberdHitCount instance field on CombatHandler (not static, not item NBT) — squires don't swap weapons mid-tick
- [Phase 04-combat-progression]: ENTITY_INTERACTION_RANGE used for halberd +1.0 reach in NeoForge 1.21.1 (NeoForgeMod.ENTITY_REACH does not exist)
- [Phase 05-ui-controls]: Inner static class pattern for dual bus subscriptions in SquireClientEvents
- [Phase 05-ui-controls]: 4-wedge radial layout for Phase 5 only — expands in later phases
- [Phase 05-ui-controls]: confirmed boolean in SquireRadialScreen prevents double packet send on close
- [Phase 05-ui-controls]: blockInteractionRange() over getBlockReach() — correct 1.21.1 LocalPlayer API
- [Phase 05-ui-controls]: ArmorItem.getType() for equipment slot validation — canEquip(slot, null) NPEs in NeoForge 1.21.1; entity required non-null
- [Phase 05-ui-controls]: RegisterMenuScreensEvent.register() over MenuScreens.register() — direct call has private access in 1.21.1
- [Phase 05-ui-controls]: SquireMenu(SquireTier, IItemHandler) headless constructor for JUnit tests — bypasses live SquireEntity and DeferredHolder resolution
- [Phase 05-ui-controls]: CMD constants renumbered for Phase 5 radial order: FOLLOW=0, GUARD=1, STAY=2, INVENTORY=3; deferred cmds start at 4
- [Phase 05-ui-controls]: Bus.GAME deprecated in NeoForge 21.1.221 — @EventBusSubscriber without bus param defaults to GAME bus (correct forward-compatible pattern)
- [Phase 05-ui-controls]: ItemHandler placed in brain/handler/ (not ai/handler/) — matching actual v2 project structure
- [Phase 05-ui-controls]: itemPickupRange reused from existing SquireConfig; junkFilterList added with defineListAllowEmpty and 5 defaults
- [Phase 06-work-behaviors]: FarmingHandler seed detection uses BlockItem.getBlock() instanceof CropBlock — no hardcoded wheat/potato/carrot list, handles modded crops
- [Phase 06-work-behaviors]: FishingHandler rollFishingLoot uses LootContextParamSets.FISHING (not EMPTY) — fixes v0.5.0 wrong fish rate bug
- [Phase 06-work-behaviors]: WORK_TASK_COMPLETE added to SquireEvent for handler-to-ChatHandler task completion signaling
- [Phase 06-work-behaviors]: TorchHandler ability gate uses squire.getLevel() >= 1 as temporary stand-in for progression ability system not yet wired
- [Phase 06-work-behaviors]: getSquireBrain() added to SquireEntity as @Nullable accessor for work handlers needing event bus access
- [Phase 06-work-behaviors]: torchLightThreshold/torchCheckInterval/torchPlaceCooldown/miningSpeedPerLevel/areaMaxBlocks added to SquireConfig — were referenced in plan but absent
- [Phase 06-work-behaviors]: PlacingHandler slot caching: inventorySlot set once in setTarget(), re-validated but not re-scanned in PLACING_BLOCK
- [Phase 06-work-behaviors]: ChestHandler dual-path: BaseContainerBlockEntity handles vanilla chests; Capabilities.ItemHandler.BLOCK fallback covers modded storage
- [Phase 06-work-behaviors]: chestAbilityMinLevel defaults to 0 (available from any level) — TODO Phase 4: replace with progression ability system
- [Phase 07-patrol-mounting]: Static writeTag/readTag on SignpostBlockEntity for headless NBT tests — BlockEntity constructor in 1.21.1 requires live registry; static helpers bypass instantiation entirely
- [Phase 07-patrol-mounting]: BlockEntityType registration: Builder.of(...).build(null) — new BlockEntityType(factory, false, block) constructor does not exist in 1.21.1
- [Phase 07-patrol-mounting]: PENDING_LINKS kept private in SignpostBlock; removePendingLink(UUID) static accessor used by SquireRegistry PlayerLoggedOutEvent handler
- [Phase 07-patrol-mounting]: Two-predicate buildRouteFromSignpost: Predicate<BlockPos> + Function<BlockPos,BlockPos> cleanly separates missing signpost from terminal signpost (both would return null in single-function design)
- [Phase 07-patrol-mounting]: Map-backed test doubles for PatrolHandlerTest: BlockEntity constructor null-checks BlockEntityType in MC 1.21.1; Map<BlockPos,BlockPos> lambdas provide equivalent coverage without any BlockEntity instantiation
- [Phase 07-patrol-mounting]: performMeleeAttack() added as thin public wrapper on CombatHandler.tryMeleeAttack() — preserves halberd sweep and cooldown for mounted hits
- [Phase 07-patrol-mounting]: horseFleeThreshold uses .floatValue() not (float) cast — ModConfigSpec.DoubleValue.get() returns boxed Double
- [Phase 08-01]: isFromPackageTestHook() package-visible accessor — exposes private isFromPackage for unit tests without breaking production encapsulation
- [Phase 08-01]: isActive() try/catch on ModList.get() — handles JUnit headless environment where FML is not bootstrapped; production behavior unchanged

### Research Flags (for planning phases)

- Phase 6 (FishingHandler): Verify 1.21.1 bobber entity behavior and loot table trigger path before implementing
- Phase 7 (MountHandler): PathfinderMob mounted navigation is sparsely documented — research before Phase 7 design
- Phase 8 (MineColonies whitelist): Verify specific API method signatures in MineColonies 1.1.1231 before implementing compat

### Pending Todos

None yet.

### Blockers/Concerns

None yet.

## Session Continuity

Last session: 2026-04-04T03:25:48.088Z
Stopped at: Completed 08-01-PLAN.md — MineColoniesCompat package-scan guard with zero MC imports
Resume file: None

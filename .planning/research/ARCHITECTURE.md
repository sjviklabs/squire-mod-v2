# Architecture Research

**Domain:** NeoForge 1.21.1 companion entity mod
**Researched:** 2026-04-02
**Confidence:** HIGH (v0.5.0 source read directly; NeoForge/Geckolib patterns verified against official docs)

---

## Standard Architecture

### System Overview

```
┌──────────────────────────────────────────────────────────────────────┐
│  CLIENT LAYER                                                        │
│  ┌────────────────┐  ┌──────────────────┐  ┌───────────────────┐   │
│  │ SquireRenderer │  │ SquireRadialScreen│  │  SquireScreen     │   │
│  │ (GeoEntity     │  │ (R key radial     │  │  (inventory UI)   │   │
│  │  Renderer)     │  │  menu)            │  │                   │   │
│  └───────┬────────┘  └────────┬─────────┘  └────────┬──────────┘   │
│          │                    │ payload                │ payload      │
└──────────┼────────────────────┼────────────────────────┼────────────┘
           │ SynchedEntityData  │ SquireCommandPayload   │ SquireMenuPayload
┌──────────┼────────────────────┼────────────────────────┼────────────┐
│  SERVER LAYER                 │                         │            │
│  ┌───────▼──────────────────────────────────────────────▼─────────┐ │
│  │                        SquireEntity                             │ │
│  │  (PathfinderMob — lifecycle, NBT, SynchedData, owner UUID,     │ │
│  │   vanilla goal registry, death/drop, capability registration)  │ │
│  └─────────────────────────┬───────────────────────────────────────┘ │
│                             │ owns                                    │
│  ┌──────────────────────────▼──────────────────────────────────────┐ │
│  │                        SquireBrain                              │ │
│  │  (TickRateStateMachine + all handler instances + event bus)     │ │
│  │                                                                  │ │
│  │  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────────────┐  │ │
│  │  │ Combat   │ │ Follow   │ │ Mining   │ │ 11 other         │  │ │
│  │  │ Handler  │ │ Handler  │ │ Handler  │ │ handlers...      │  │ │
│  │  └──────────┘ └──────────┘ └──────────┘ └──────────────────┘  │ │
│  └──────────────────────────────────────────────────────────────────┘ │
│                                                                        │
│  ┌──────────────────┐  ┌───────────────────┐  ┌───────────────────┐  │
│  │  SquireInventory │  │ ProgressionHandler │  │ SquireDataAttach  │  │
│  │  (IItemHandler   │  │ (XP, level, attrs, │  │ (player-attached  │  │
│  │   capability)    │  │  persistence)      │  │  UUID/XP codec)   │  │
│  └──────────────────┘  └───────────────────┘  └───────────────────┘  │
│                                                                        │
│  ┌──────────────────┐  ┌───────────────────┐  ┌───────────────────┐  │
│  │  SquireRegistry  │  │ Datapack Loaders   │  │  Compat Layer     │  │
│  │  (single class,  │  │ (progression JSON, │  │  (MineColonies,   │  │
│  │   all DeferredReg│  │  entity tag JSON)  │  │   Jade, Curios)   │  │
│  └──────────────────┘  └───────────────────┘  └───────────────────┘  │
└────────────────────────────────────────────────────────────────────────┘
```

---

### Component Responsibilities

| Component | Responsibility | Communicates With |
|-----------|----------------|-------------------|
| `SquireEntity` | Entity lifecycle: construction, NBT save/load, synched data, vanilla goal registration (float + target selectors only), death/drop, interaction handler, capability provider registration | SquireBrain, SquireInventory, ProgressionHandler, SquireDataAttachment |
| `SquireBrain` | AI coordination: owns TickRateStateMachine, instantiates all handlers, registers all FSM transitions, routes player commands to handlers | All handlers, TickRateStateMachine, SquireEntity (read-only access) |
| `TickRateStateMachine` | State machine engine: per-transition countdown timers, global-vs-state-specific evaluation, priority ordering, state transition logging | SquireBrain (registered by), handlers (called by transitions) |
| `[Behavior]Handler` (14 handlers) | Single-behavior logic: owns that behavior's mutable state (cooldowns, targets, flags), implements start/tick/stop methods, returns next SquireAIState | SquireEntity (read/write), other handlers via event bus |
| `SquireInventory` | Item storage: slot-indexed NBT, tiered slot capacity (grows with level), IItemHandler capability surface, drop-all on death | SquireEntity, SquireMenu, handlers (CombatHandler, ItemHandler, etc.) |
| `ProgressionHandler` | XP accounting: kill/mine/fish/harvest XP accumulation, level calculation, attribute modifier application (stable ResourceLocation IDs) | SquireEntity (attribute writes), SquireDataAttachment (save/load), SquireConfig |
| `SquireDataAttachment` | Cross-death persistence: player-attached record (UUID, XP, level, name, slimModel) using NeoForge AttachmentType + Codec | SquireEntity (on death/resummon), player entity |
| `SquireRegistry` | Single registration class: all DeferredRegister instances (EntityTypes, Items, Blocks, BlockEntities, Menus, AttachmentTypes, Capabilities) | SquireMod (mod constructor calls .register(modBus)) |
| `DatapackLoader` | JSON loading: progression curves, ability definitions loaded via SimpleJsonResourceReloadListener on AddServerReloadListenersEvent | ProgressionHandler (progression data), SquireBrain (ability unlock checks) |
| `SquireRenderer` | Client rendering: extends GeoEntityRenderer, owns GeoEntityModel (loads .geo.json), registers animation controllers, handles armor layer | GeckoLib (GeoEntity interface on SquireEntity) |
| `SquireBrain EventBus` | Internal handler coordination: lightweight in-process bus (not NeoForge bus) — CombatHandler entering combat fires `CombatStartEvent`; MiningHandler receives it and cancels active task | All handlers as publishers and subscribers |
| `CompatLayer` | Soft integration: runtime class-name checks for MineColonies, Jade provider registration, Curios slot registration — all guarded by `ModList.get().isLoaded()` | MineColonies, Jade, Curios APIs (optional dependencies) |

---

## Recommended Project Structure

```
src/main/java/com/sjviklabs/squire/
├── SquireMod.java              # @Mod entry point — calls SquireRegistry.register()
├── SquireRegistry.java         # ALL DeferredRegister instances, capability registration
│
├── entity/
│   ├── SquireEntity.java       # PathfinderMob subclass — lifecycle only
│   ├── SquireTier.java         # Enum: SERVANT/APPRENTICE/SQUIRE/KNIGHT/CHAMPION + capability checks
│   └── SquireDataAttachment.java # AttachmentType + SquireData record + Codec
│
├── brain/
│   ├── SquireBrain.java        # FSM owner, handler container, event bus wiring
│   ├── SquireAIState.java      # Enum of all FSM states
│   ├── TickRateStateMachine.java # Engine: countdown per transition, priority eval
│   ├── AITransition.java       # Record: sourceState, condition, action, tickRate, priority
│   ├── SquireEventBus.java     # Internal bus interface (simple observer list, not NeoForge bus)
│   └── handler/
│       ├── CombatHandler.java
│       ├── FollowHandler.java
│       ├── SurvivalHandler.java
│       ├── MiningHandler.java
│       ├── PlacingHandler.java
│       ├── TorchHandler.java
│       ├── ItemHandler.java
│       ├── ChestHandler.java
│       ├── PatrolHandler.java
│       ├── MountHandler.java
│       ├── FarmingHandler.java
│       ├── FishingHandler.java
│       ├── ChatHandler.java
│       └── DangerHandler.java
│
├── inventory/
│   ├── SquireItemHandler.java  # IItemHandler impl with tiered slot capacity
│   ├── SquireMenu.java         # AbstractContainerMenu for UI
│   └── SquireEquipmentSlot.java # Equipment slot definitions
│
├── progression/
│   ├── ProgressionHandler.java # XP, level, attribute modifiers
│   ├── SquireAbilityDef.java   # Data class: ability definition loaded from JSON
│   └── ProgressionDataLoader.java # SimpleJsonResourceReloadListener for JSON datapacks
│
├── network/
│   ├── SquireCommandPayload.java # Client→Server radial menu commands (CustomPacketPayload)
│   └── SquireModePayload.java    # Server→Client mode sync
│
├── item/
│   ├── SquireCrestItem.java
│   ├── SquireArmorItem.java
│   └── SquireShieldItem.java
│
├── block/
│   ├── SignpostBlock.java
│   └── SignpostBlockEntity.java
│
├── client/
│   ├── SquireRenderer.java       # GeoEntityRenderer subclass
│   ├── SquireModel.java          # GeoEntityModel loading .geo.json
│   ├── SquireRadialScreen.java   # R key radial UI
│   ├── SquireScreen.java         # Inventory screen
│   └── SquireClientEvents.java   # @EventBusSubscriber(value=Dist.CLIENT)
│
├── data/
│   └── SquireTagKeys.java        # TagKey<EntityType<?>> constants for combat tactics
│
├── config/
│   └── SquireConfig.java         # ModConfigSpec — squire-common.toml
│
├── command/
│   └── SquireCommand.java        # /squire commands
│
└── compat/
    ├── MineColoniesCompat.java   # Soft compat: raider/colonist detection
    ├── JadeCompat.java           # Jade overlay provider
    └── CuriosCompat.java         # Curios/Accessories slot registration

src/main/resources/
├── data/squire/
│   ├── tags/entity_type/         # Entity tag JSON (combat tactics)
│   │   ├── melee_aggressive.json
│   │   ├── melee_cautious.json
│   │   ├── ranged_evasive.json
│   │   ├── explosive_threat.json
│   │   └── do_not_attack.json
│   └── squire/progression/       # Datapack JSON
│       ├── tiers.json
│       └── abilities.json
└── assets/squire/
    ├── geo/squire.geo.json       # Geckolib model
    └── animations/squire.animation.json
```

### Structure Rationale

- **`brain/` separate from `entity/`:** The core v0.5.0 lesson — SquireEntity was 600+ lines mixing lifecycle with AI. `brain/` is the squire's behavior; `entity/` is what it IS in Minecraft's type system.
- **`brain/handler/`:** One class per behavior. Each handler owns its mutable state (cooldowns, target refs, flags). Nothing in SquireEntity or SquireBrain holds handler-specific state.
- **`inventory/SquireItemHandler`:** Renamed from SquireInventory to signal IItemHandler compliance from the start. Not SimpleContainer — capability registration requires IItemHandler directly.
- **`progression/`:** Separated from `brain/handler/` because progression has a data-loading dimension (JSON datapacks) the other handlers don't have.
- **`data/SquireTagKeys`:** Centralizes all `TagKey<EntityType<?>>` constants. Handlers import from here, not from scattered static fields.
- **`compat/`:** Each compat file is isolated. If the optional dep is absent, the file's methods return safe defaults. No hard compile-time dep on any optional mod.

---

## Architectural Patterns

### Pattern 1: SquireEntity / SquireBrain Split

**What:** `SquireEntity` handles only what Minecraft's entity system requires — construction, NBT, synched data, attribute registration, goal registration, interaction, and capability exposure. `SquireBrain` is instantiated in `SquireEntity.addAdditionalSaveData` / `readAdditionalSaveData` and holds everything AI-related.

**When to use:** Always. The split enforces that no behavior logic bleeds into the entity class.

**Trade-offs:** One extra indirection (entity.getBrain().getCombat()). Worth it — v0.5.0 proved the alternative is an unmaintainable 600-line god class.

**Example:**
```java
// SquireEntity.java — the entity IS
public class SquireEntity extends PathfinderMob {
    private UUID ownerUUID;
    private SquireBrain squireBrain;

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));        // swimming
        this.targetSelector.addGoal(1, new OwnerHurtByTargetGoal(this)); // target sharing
        // Everything else: SquireBrain handles it
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!level().isClientSide && squireBrain != null) {
            squireBrain.tick();
        }
    }
}

// SquireBrain.java — the brain DOES
public class SquireBrain {
    private final TickRateStateMachine machine;
    private final CombatHandler combat;
    private final FollowHandler follow;
    // ...
    public void tick() { machine.tick(squire); }
}
```

---

### Pattern 2: Tick-Rate State Machine

**What:** Each FSM transition declares its own `tickRate` (how often in ticks to evaluate its condition) and `priority` (lower = higher priority). Global transitions (sourceState == null) interrupt any state; state-specific transitions only fire when the machine is in that state.

**When to use:** All AI behavior routing. No vanilla GoalSelector goals except FloatGoal and target selectors.

**Trade-offs:** More setup than GoalSelector. Pays off in predictability — every behavior interrupt is an explicit priority number, not an implicit goal weight race. Tick-rate throttling prevents expensive scans (item searches, entity AABBs) from running every tick.

**Priority layers (proven from v0.5.0):**
```
1-9:   survival (fleeing, drowning)
10-19: combat
20-29: eating / mount acquisition
30-39: follow / patrol
35-39: work tasks (mining, placing, chest, farming, fishing)
40-49: item pickup
50+:   cosmetic idle
```

---

### Pattern 3: Handler-Per-Behavior

**What:** One class owns one behavior. `CombatHandler` owns attack cooldowns, tactic state, strafe direction, and hit-and-run timing. Nothing else touches those fields.

**When to use:** Every behavior. If you're tempted to add a field to SquireEntity for a behavior, it belongs in a handler.

**Trade-offs:** 14 handler files is more classes. The alternative (fields scattered in SquireEntity) proved untenable in v0.5.0.

**Handler contract:**
```java
public class SomeHandler {
    private final SquireEntity squire;
    // Handler-specific state fields only

    public SomeHandler(SquireEntity squire) { this.squire = squire; }

    public boolean shouldStart() { /* condition */ }
    public void start() { /* enter behavior */ }
    public SquireAIState tick(SquireEntity s) { /* per-tick work, return next state */ }
    public void stop() { /* cleanup */ }
}
```

---

### Pattern 4: Internal Event Bus for Handler Coordination

**What:** A lightweight in-process observer bus (NOT NeoForge's game bus or mod bus). Handlers publish events; other handlers subscribe. `CombatHandler.start()` fires `SquireEvent.COMBAT_START`; `MiningHandler` receives it and cancels any active mining task.

**When to use:** When one handler needs to react to another handler's state change without polling.

**Trade-offs:** Adds an abstraction. The alternative (handlers reaching directly into each other via `brain.getMining().clear()`) creates coupling chains that are hard to track. A bus makes the dependency explicit and reversible.

**Implementation approach:** Simple enum-keyed observer list. Not Spring Events, not NeoForge IEventBus — just `Map<SquireEvent, List<Runnable>>` with publish/subscribe methods. Keep it trivial.

```java
public class SquireBrainEventBus {
    private final Map<SquireEvent, List<Consumer<SquireEntity>>> listeners = new EnumMap<>(SquireEvent.class);

    public void subscribe(SquireEvent event, Consumer<SquireEntity> handler) {
        listeners.computeIfAbsent(event, k -> new ArrayList<>()).add(handler);
    }

    public void publish(SquireEvent event, SquireEntity squire) {
        var list = listeners.get(event);
        if (list != null) list.forEach(h -> h.accept(squire));
    }
}

public enum SquireEvent {
    COMBAT_START, COMBAT_END,
    WORK_TASK_START, WORK_TASK_COMPLETE,
    TIER_ADVANCE, ITEM_PICKUP
}
```

---

### Pattern 5: IItemHandler Capability (NeoForge 1.21.1)

**What:** `SquireItemHandler` implements `IItemHandler` directly. `SquireRegistry` registers it via `RegisterCapabilitiesEvent` using `event.registerEntity(Capabilities.ItemHandler.ENTITY, SQUIRE_ENTITY_TYPE, (e, ctx) -> e.getItemHandler())`.

**When to use:** Required for hoppers, pipes, and other mods (including MineColonies warehouses) to interact with squire inventory.

**Trade-offs:** More setup than SimpleContainer. SimpleContainer cannot be exposed as IItemHandler without a wrapper anyway.

**NeoForge 1.21.1 registration pattern:**
```java
// In SquireRegistry or a @EventBusSubscriber class
@SubscribeEvent
public static void registerCapabilities(RegisterCapabilitiesEvent event) {
    event.registerEntity(
        Capabilities.ItemHandler.ENTITY,
        ModEntities.SQUIRE.get(),
        (squire, context) -> squire.getItemHandler()
    );
}
```

---

### Pattern 6: Geckolib Entity Rendering

**What:** `SquireEntity` implements `GeoEntity`. `SquireRenderer` extends `GeoEntityRenderer<SquireEntity>`. `SquireModel` extends `GeoEntityModel<SquireEntity>`. Animation controllers registered in `SquireEntity.registerControllers()` — broad first (walk/idle), specific last (combat, bowdraw).

**When to use:** All entity rendering. Replaces HumanoidModel and PlayerModel workarounds entirely.

**Animation cache (required boilerplate):**
```java
// In SquireEntity
private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

@Override
public AnimatableInstanceCache getAnimatableInstanceCache() { return geoCache; }

@Override
public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
    controllers.add(new AnimationController<>(this, "locomotion", 5, this::locomotionController));
    controllers.add(new AnimationController<>(this, "combat", 3, this::combatController));
}
```

---

### Pattern 7: Datapack Registry for Progression / Abilities

**What:** JSON files in `data/squire/squire/progression/` loaded via `SimpleJsonResourceReloadListener` registered in `AddServerReloadListenersEvent`. Provides tier thresholds, XP curves, and ability unlock definitions without recompiling.

**When to use:** Any gameplay constant that server operators might want to tune.

**Trade-offs:** Requires handling reload lifecycle (null-safety during early game load). Worth it for tier progression — mopack operators on ATM10 will want to tune XP curves.

**Entity tags for combat tactics** (separate pattern): JSON in `data/squire/tags/entity_type/melee_aggressive.json`. Checked in CombatHandler via `target.getType().is(SquireTagKeys.MELEE_AGGRESSIVE)`. Modded mobs get correct tactics by adding the squire tag to their own datapack — no code changes.

---

## Data Flow

### Follow → Combat Interrupt

```
[Server tick]
    ↓
SquireEntity.aiStep()
    ↓
SquireBrain.tick()
    ↓
TickRateStateMachine.tick()
    ↓ evaluate globals first (priority order)
[CombatEnterTransition — global, priority 10, tickRate 1]
    condition: squire.getTarget() != null && !isOrderedToSit
    fires: combat.start() → publishes COMBAT_START event
                          → MiningHandler.onCombatStart() clears active target
                          → returns COMBAT_APPROACH
    ↓
currentState = COMBAT_APPROACH
    ↓ next tick: CombatApproachTransition ticks every 1 tick
combat.tick(squire) → navigate toward target, attack on cooldown
```

### Player Radial Command → FSM State Change

```
[Player presses R → selects "Mine"]
    ↓
SquireRadialScreen sends SquireCommandPayload(CMD_MINE, squireEntityId)
    ↓ [network]
SquireCommandPayload.handle() on server
    ↓
squire.getBrain().getMining().setTarget(blockPos)
machine.forceState(MINING_APPROACH)
    ↓ next tick
TickRateStateMachine evaluates MINING_APPROACH transitions
mining.tickApproach(squire) → navigate toward block
```

### XP Award → Tier Advance → Attribute Update

```
[Squire kills mob]
    ↓
CombatHandler calls brain.awardKillXP()
    ↓
ProgressionHandler.addKillXP()
    ↓ if level increases:
applyModifiers() — writes AttributeModifier with stable ResourceLocation ID
squire.setSquireLevel(newLevel) — syncs to client via SynchedEntityData
    ↓
brain.publish(SquireEvent.TIER_ADVANCE, squire)
    ↓ handlers react:
SquireItemHandler.onTierAdvance() — expands slot count
ChatHandler.onTierAdvance() — fires celebration line
```

### Cross-Death Persistence Flow

```
[Squire dies]
    ↓
SquireEntity.die()
    ↓
progression data → owner.setData(SQUIRE_DATA, data.withXP(xp, level))
    (AttachmentType serialized to player NBT via Codec)
inventory contents → dropped as ItemEntity
    ↓
[Player uses Crest item to resummon]
    ↓
SquireCrestItem.use()
    ↓
var data = player.getData(SQUIRE_DATA)
new SquireEntity spawned, owner UUID set
squire.getProgression().setFromAttachment(data.totalXP(), data.level())
```

### Datapack Load → Ability Gate

```
[World load / /reload]
    ↓
ProgressionDataLoader (SimpleJsonResourceReloadListener)
    loads data/squire/squire/progression/abilities.json
    ↓
AbilityRegistry.reload(map of ability → minTier)
    ↓ [during play]
SquireBrain checks ability gate:
    if (AbilityRegistry.isUnlocked(SquireAbility.TORCH_PLACEMENT, squire.getTier()))
        torch.tryPlaceTorch()
```

---

## Build Order (Phase Dependencies)

The following dependency chain must be respected. Later phases cannot begin until their dependencies are stable.

```
Phase 1: Core Entity Foundation
  SquireRegistry → SquireEntity (PathfinderMob base, no AI)
  SquireDataAttachment → SquireEntity (persistence from day one)
  SquireItemHandler (IItemHandler) → SquireRegistry (capability registration)
  SquireConfig → everything (no hardcoded numbers anywhere)

Phase 2: Brain + FSM Skeleton
  TickRateStateMachine → SquireAIState enum (states must exist)
  AITransition record → TickRateStateMachine
  SquireBrain (empty handler stubs) → TickRateStateMachine
  SquireBrainEventBus → SquireBrain
  FollowHandler → SquireBrain (first real behavior, simplest)
  SurvivalHandler → FollowHandler (eats food, heals)

Phase 3: Combat
  CombatHandler → SurvivalHandler (survival is higher priority)
  SquireTagKeys + entity tag JSON → CombatHandler (tactic selection)
  DangerHandler → CombatHandler (flee from explosives overrides combat)
  ProgressionHandler → CombatHandler (tier gates)

Phase 4: Rendering (can parallelize with Phase 3)
  Geckolib model + animations → SquireRenderer
  SquireRenderer → SquireEntity (GeoEntity interface)
  Armor layer → SquireRenderer

Phase 5: Inventory + UI
  SquireItemHandler stable → SquireMenu
  SquireMenu → SquireScreen (client)
  SquireCommandPayload (network) → SquireMenu (CMD_INVENTORY)
  SquireRadialScreen → all command handlers stable

Phase 6: Work Behaviors
  MiningHandler → SquireBrain (requires FSM + ProgressionHandler)
  PlacingHandler → MiningHandler (shares approach pattern)
  TorchHandler → FollowHandler (runs inline during follow)
  ItemHandler → FollowHandler (runs inline during follow)
  FarmingHandler → MiningHandler (similar approach/work pattern)
  FishingHandler → FarmingHandler

Phase 7: Advanced Behaviors
  ChestHandler → ItemHandler (inventory manipulation)
  PatrolHandler → SignpostBlock (waypoint block must exist)
  MountHandler → FollowHandler (mounted follow is a follow variant)

Phase 8: Data-Driven + Compat
  ProgressionDataLoader → ProgressionHandler
  SquireTagKeys (entity tags JSON) → should be done by Phase 3 but finalized here
  MineColoniesCompat → CombatHandler (friendly-fire gate)
  JadeCompat → SquireEntity (Jade provider registration)
  CuriosCompat → SquireItemHandler (equipment slot surface)
```

**Critical dependency note:** `SquireRegistry` must be the first thing called in `SquireMod` constructor. NeoForge DeferredRegister objects must be registered to the mod event bus before any other listener tries to use them. Registration order within `SquireRegistry` matters: EntityTypes before Items (items reference entity types for spawn eggs), AttachmentTypes before entity lifecycle events.

---

## Anti-Patterns

### Anti-Pattern 1: Extending TamableAnimal

**What people do:** Use TamableAnimal as base for companion entities because it provides `isOwnedBy()`, `getOwner()`, and target-sharing goals.

**Why it's wrong:** TamableAnimal registers `OwnerHurtByTargetGoal`, `OwnerHurtTargetGoal`, and the sit behavior in the vanilla GoalSelector. These goals fight the custom FSM — both systems run simultaneously, causing behavior conflicts. The `setOrderedToSit()` mechanic is baked into TamableAnimal in ways that bleed into movement and combat suppression.

**Do this instead:** Extend `PathfinderMob`. Implement owner tracking manually with a `UUID ownerUUID` field saved in NBT (~80 lines). Add the three owner target goals explicitly in `registerGoals()` — they're standalone classes that don't require TamableAnimal as base.

---

### Anti-Pattern 2: Behavior State in SquireEntity

**What people do:** Add fields like `private int attackCooldown` or `private BlockPos miningTarget` directly to the entity class.

**Why it's wrong:** Entity classes balloon. v0.5.0 SquireEntity hit 600+ lines before the brain split. Entity classes are serialized, loaded, and tracked by Minecraft's entity system — behavior state that leaks in there becomes a persistent footgun.

**Do this instead:** All behavior-specific state lives in the handler class for that behavior. Handlers are instantiated by SquireBrain. SquireEntity holds only what Minecraft's type system requires it to hold (synched data, inventory, progression, NBT persistence).

---

### Anti-Pattern 3: Hardcoded Mob Checks in Combat

**What people do:** `if (target instanceof Zombie) { tactic = AGGRESSIVE; }` chains in CombatHandler.

**Why it's wrong:** Modded mobs are invisible to this check. Every new enemy type requires a code change and a release. ATM10 has hundreds of modded mobs.

**Do this instead:** `target.getType().is(SquireTagKeys.MELEE_AGGRESSIVE)`. Modded mob authors (or pack makers) add their mob to the appropriate squire entity tag. No code changes needed. Tags are composable — a mod can add their zombie variant to `squire:melee_aggressive` in their own datapack.

---

### Anti-Pattern 4: Using NeoForge Game Bus for Internal Handler Communication

**What people do:** Subscribe handlers to `NeoForge.EVENT_BUS` to communicate between them, or fire `NeoForgeEvent` subclasses for internal state changes.

**Why it's wrong:** NeoForge's game bus is a global broadcast system. Firing per-squire internal events onto it means every subscriber on the entire bus receives the event. Performance cost scales with mod count (ATM10 has 449). It also leaks internal squire state to any mod that subscribes.

**Do this instead:** Use the lightweight internal `SquireBrainEventBus` — a simple `EnumMap<SquireEvent, List<Consumer<SquireEntity>>>`. Scoped to one squire instance. Zero overhead when nobody is listening. Invisible to other mods.

---

### Anti-Pattern 5: Scattered Registration Classes

**What people do:** Separate `ModEntities`, `ModItems`, `ModBlocks`, `ModMenuTypes` classes, each registering their own DeferredRegister.

**Why it's wrong:** In NeoForge, registration order matters. When registration is scattered, load-order bugs surface as cryptic null pointer exceptions during startup that are hard to reproduce. v0.5.0 had this problem.

**Do this instead:** One `SquireRegistry` class with all `DeferredRegister` instances as static fields. Register them all to the mod event bus in `SquireMod` constructor, in explicit order. The class makes the dependency graph visible.

---

## Integration Points

### External Mod Integrations

| Integration | Pattern | Notes |
|-------------|---------|-------|
| Geckolib | `GeoEntity` interface on `SquireEntity`, `GeoEntityRenderer` for renderer, `GeoEntityModel` for model | No model layer registration needed. Geckolib 4.7.x for NeoForge 1.21.1 confirmed on CurseForge. |
| Curios API | Register equipment slots in `CuriosCompat` via `ICuriosHelper`. Guard with `ModList.get().isLoaded("curios")`. | Slot registration fires on `InterModEnqueueEvent`. |
| Jade/WAILA | Register `IWailaPlugin` in `JadeCompat`. Guard with `ModList.get().isLoaded("jade")`. | Annotate with `@WailaPlugin` for auto-discovery. |
| MineColonies | Class-hierarchy string check (no hard dep). `isColonist()`, `isRaider()`, `isWarehouse()` in `MineColoniesCompat`. | No compile-time MineColonies dep. Intentional — loose coupling survives MC updates. |
| NeoForge IItemHandler | `RegisterCapabilitiesEvent` → `event.registerEntity(Capabilities.ItemHandler.ENTITY, ...)` | Enables hopper/pipe interaction and MineColonies warehouse deposit. |

### Internal Boundaries

| Boundary | Communication | Notes |
|----------|---------------|-------|
| SquireEntity ↔ SquireBrain | SquireEntity owns SquireBrain instance. Brain calls back into entity for movement/attack/NBT. | Brain never stored in entity NBT — reconstructed each load from entity state. |
| Handler ↔ Handler | SquireBrainEventBus (publish/subscribe). No direct handler-to-handler method calls except via FSM transitions. | Direct calls allowed only when handler A owns handler B's concern — e.g., `FollowHandler.tick()` calls `TorchHandler.tryPlaceTorch()` because torch is a follow side-effect, not a competing behavior. |
| SquireEntity ↔ ProgressionHandler | Handler holds reference to entity. Entity holds reference to handler. Bidirectional by design. | ProgressionHandler writes attributes back to entity. This is the one legitimate bidirectional coupling. |
| Server ↔ Client | SynchedEntityData for real-time state (mode, level, bowDraw, sprinting). CustomPacketPayload for commands (radial menu). Container protocol for inventory UI. | No direct client→server state mutation outside these paths. |
| SquireEntity ↔ SquireDataAttachment | One-way: entity reads/writes player's attachment data on death and resummon. Player entity owns the attachment. | Attachment codec must be registered before any entity lifecycle event fires. |

---

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| SquireEntity / SquireBrain split | HIGH | Directly derived from v0.5.0 source + established pattern |
| TickRateStateMachine | HIGH | Full v0.5.0 source read; proven working |
| Handler-per-behavior pattern | HIGH | All 14 handlers read in v0.5.0 |
| IItemHandler capability registration | HIGH | Verified against NeoForge official docs (RegisterCapabilitiesEvent pattern) |
| Geckolib 4 entity setup | HIGH | Verified against Geckolib wiki (GeoEntity, GeoEntityRenderer, AnimatableInstanceCache) |
| Entity tag combat tactics | HIGH | Verified against NeoForge Tags docs; `EntityType.is(TagKey)` is standard |
| Datapack JSON loading | MEDIUM | SimpleJsonResourceReloadListener pattern confirmed; exact Codec shape for abilities.json is design-time decision |
| Internal event bus | MEDIUM | Pattern is standard Java observer; specific event set is design-time decision |
| Cross-death persistence via AttachmentType | HIGH | v0.5.0 source + NeoForge AttachmentType docs confirm the pattern |
| Build order | HIGH | Derived from actual dependency graph in v0.5.0 source |

---

## Sources

- v0.5.0 source: `C:/Users/Steve/Projects/squire-mod/src/` (read directly)
- [NeoForge Capabilities docs](https://docs.neoforged.net/docs/1.21.5/inventories/capabilities/)
- [NeoForge Tags docs](https://docs.neoforged.net/docs/1.21.1/resources/server/tags/)
- [NeoForge Events docs](https://docs.neoforged.net/docs/concepts/events/)
- [Geckolib 4 Entities wiki](https://github.com/bernie-g/geckolib/wiki/Geckolib-Entities-(Geckolib4))
- [Geckolib Animation Controller wiki](https://github.com/bernie-g/geckolib/wiki/The-Animation-Controller-(Geckolib4))
- [NeoForge datapack loading discussion](https://github.com/neoforged/NeoForge/discussions/2433)
- [NeoForge Capability rework announcement](https://neoforged.net/news/20.3capability-rework/)

---

_Architecture research for: NeoForge 1.21.1 companion entity mod (squire-mod v2)_
_Researched: 2026-04-02_

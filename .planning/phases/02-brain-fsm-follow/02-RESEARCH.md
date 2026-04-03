# Phase 2: Brain, FSM, and Follow — Research

**Researched:** 2026-04-03
**Domain:** NeoForge 1.21.1 — custom tick-rate FSM, handler-per-behavior pattern, no-teleport navigation, StreamCodec networking
**Confidence:** HIGH — primary source is v0.5.0 source read directly; architecture decisions already validated in prior research

---

<phase_requirements>

## Phase Requirements

| ID     | Description                                                     | Research Support                                                               |
| ------ | --------------------------------------------------------------- | ------------------------------------------------------------------------------ |
| NAV-01 | Squire follows player without ever teleporting                  | FollowHandler must remove tryTeleportToOwner(); pure pathfinding only          |
| NAV-02 | Squire sprints to catch up when player is far                   | Speed scaling in FollowHandler.tick() — proven pattern from v0.5.0             |
| NAV-03 | Squire swims through water to follow                            | setCanFloat(true) + AmphibiousNodeEvaluator or WaterBoundPathNavigation        |
| NAV-04 | Squire can sit/stay on command (toggle)                         | SITTING FSM state + SquireModePayload / SquireCommandPayload StreamCodec       |
| NAV-05 | Squire navigates caves, hills, structures via pathfinding       | GroundPathNavigation with canOpenDoors=true + OpenDoorGoal in registerGoals()  |
| ARC-01 | Custom tick-rate FSM replacing vanilla GoalSelector             | TickRateStateMachine — full v0.5.0 impl available as reference                 |
| ARC-02 | Handler-per-behavior pattern                                    | FollowHandler, SurvivalHandler — one class per behavior, owns its mutable state |
| ARC-03 | Internal event bus for handler coordination                     | SquireBrainEventBus — NEW in v2; not in v0.5.0                                |
| ARC-08 | Network payloads via StreamCodec (no raw FriendlyByteBuf)       | StreamCodec.composite() — already used in v0.5.0; carry forward cleanly        |

</phase_requirements>

---

## Summary

Phase 2 builds four things that don't exist yet in v2: the FSM engine (TickRateStateMachine + AITransition + SquireAIState), the brain container (SquireBrain replacing v0.5.0's SquireAI), the internal event bus (SquireBrainEventBus — new to v2), and the first two real behavior handlers (FollowHandler, SurvivalHandler). The v0.5.0 source is complete and has been read in full — this is a port and cleanup job, not original research.

The single most important deviation from v0.5.0 is NAV-01: the squire must NEVER teleport. The v0.5.0 FollowHandler contains `tryTeleportToOwner()` which fires when `distSq > 24*24`. That entire method and the call site must be removed. The no-teleport constraint is the defining identity of this mod — it is not configurable and not negotiable. Stuck recovery must use pathfinding-based approaches (jump boost, stair stepping, node evaluator configuration) rather than the teleport escape hatch.

The second important addition is ARC-03: SquireBrainEventBus. v0.5.0 used direct handler-to-handler method calls for cross-behavior coordination. v2 replaces that with a lightweight `EnumMap<SquireEvent, List<Consumer<SquireEntity>>>` bus scoped to the squire instance. Phase 2 only needs COMBAT_START/END and SIT_TOGGLE events — enough to prove the pattern and let Phase 4 (CombatHandler) publish without any handler coupling.

**Primary recommendation:** Port TickRateStateMachine and AITransition verbatim from v0.5.0. Rename SquireAI to SquireBrain. Add SquireBrainEventBus as new. Strip teleport from FollowHandler. Replace SurvivalHandler's SquireInventory calls with IItemHandler calls (v2 uses IItemHandler, not SimpleContainer).

---

## Standard Stack

### Core

| Library | Version | Purpose | Why Standard |
| ------- | ------- | ------- | ------------ |
| NeoForge | 21.1.x | Entity AI, pathfinding, networking, NBT | Target platform — no choice |
| Java | 21 | Language | NeoForge 1.21.1 requires Java 21 |

### NeoForge APIs in Scope for Phase 2

| API | Purpose | Notes |
| --- | ------- | ----- |
| `PathfinderMob` / `GroundPathNavigation` | Entity locomotion backbone | Already declared in Phase 1 |
| `RegisterPayloadHandlersEvent` | Network payload registration | Used in v0.5.0; carry forward |
| `StreamCodec.composite()` | Type-safe packet serialization (ARC-08) | Required — no raw FriendlyByteBuf |
| `ByteBufCodecs.VAR_INT`, `BOOL`, `STRING_UTF8` | Primitive codec building blocks | Used in v0.5.0 patterns |
| `CustomPacketPayload` | Packet record interface | Each payload implements this |
| `SynchedEntityData` | Server→client entity state sync | Mode sync (sit/follow/guard) |
| `OpenDoorGoal` | Registers door-opening behavior | Must be in `registerGoals()`, low priority number |
| `MobEffects.JUMP` | Jump boost for cliff navigation | Used in v0.5.0 FollowHandler |

### Supporting

| Library | Version | Purpose | When to Use |
| ------- | ------- | ------- | ----------- |
| JUnit 5 | 5.x | Unit tests for FSM logic | TST-01: test TickRateStateMachine transition ordering |

**Installation:** No new dependencies for Phase 2. All required APIs are in `neoforge` dep already declared in Phase 1 build.gradle.

---

## Architecture Patterns

### Recommended Project Structure (Phase 2 additions)

```
src/main/java/com/sjviklabs/squire/
├── brain/
│   ├── SquireBrain.java           # Replaces SquireAI — owns machine + handlers + event bus
│   ├── SquireAIState.java         # Full enum: IDLE, EATING, FLEEING, FOLLOWING_OWNER, SITTING, ...
│   ├── TickRateStateMachine.java  # Engine — port verbatim from v0.5.0
│   ├── AITransition.java          # Record — port verbatim from v0.5.0
│   ├── SquireBrainEventBus.java   # NEW — not in v0.5.0
│   ├── SquireEvent.java           # NEW — enum of publishable events
│   └── handler/
│       ├── FollowHandler.java     # No-teleport navigation — key change from v0.5.0
│       └── SurvivalHandler.java   # Eating/healing — IItemHandler calls, not SquireInventory
│
└── network/
    ├── SquireCommandPayload.java  # Port from v0.5.0 — CMD_STAY/FOLLOW only for Phase 2
    └── SquireModePayload.java     # Port from v0.5.0 — sit/follow toggle
```

### Pattern 1: TickRateStateMachine (Port Verbatim)

**What:** The FSM engine. Each `AITransition` has its own countdown timer. On each tick: decrement all counters, evaluate global transitions by priority, then current-state transitions. First match fires.

**When to use:** All AI behavior routing. This is the only brain tick path.

**Port notes:**
- `TickRateStateMachine.java` — copy exactly; no changes needed
- `AITransition.java` — copy exactly (it's a record: sourceState, condition, action, tickRate, priority)
- The `dirty` flag + `rebuild()` pattern handles transitions added after construction — keep it

**From v0.5.0 source (verified):**
```java
// AITransition is a record — the full signature from v0.5.0:
public record AITransition(
    SquireAIState sourceState,    // null = global (fires from any state)
    BooleanSupplier condition,
    Function<SquireEntity, SquireAIState> action,
    int tickRate,
    int priority
) {
    public boolean isGlobal() { return sourceState == null; }
    public boolean appliesTo(SquireAIState state) { return sourceState == state; }
}
```

**Priority layers (v0.5.0 proven):**
```
priority 1:    sitting (global — overrides everything including danger)
priority 5:    danger/flee
priority 10:   combat
priority 20:   eating
priority 30:   follow / patrol
priority 35-39: work tasks
priority 40:   item pickup
priority 50+:  cosmetic idle
```

### Pattern 2: SquireBrain (Renamed SquireAI)

**What:** The brain container. Owns the `TickRateStateMachine`, all handler instances, and the `SquireBrainEventBus`. Instantiated by `SquireEntity` in `aiStep()`. Exposes `tick()` which delegates to the machine.

**Key differences from v0.5.0's SquireAI:**
- Phase 2 instantiates only `FollowHandler` and `SurvivalHandler` — stubs for the rest
- Add `SquireBrainEventBus bus` field and pass it to handlers that need it
- Rename class to `SquireBrain` — the v0.5.0 name `SquireAI` conflicts with v2 package structure

**Minimal Phase 2 brain:**
```java
// Source: v0.5.0 SquireAI.java (adapted for Phase 2 scope)
public class SquireBrain {
    private final SquireEntity squire;
    private final TickRateStateMachine machine;
    private final SquireBrainEventBus bus;
    private final FollowHandler follow;
    private final SurvivalHandler survival;
    private int idleTicks;

    public SquireBrain(SquireEntity squire) {
        this.squire = squire;
        this.machine = new TickRateStateMachine();
        this.bus = new SquireBrainEventBus();
        this.follow = new FollowHandler(squire, bus);
        this.survival = new SurvivalHandler(squire, bus);
        registerTransitions();
    }

    public void tick() {
        if (machine.getCurrentState() != SquireAIState.IDLE) {
            idleTicks = 0;
        }
        machine.tick(squire);
    }
}
```

**SquireEntity wiring:**
```java
// In SquireEntity.aiStep()
@Override
public void aiStep() {
    super.aiStep();
    if (!level().isClientSide && squireBrain != null) {
        squireBrain.tick();
    }
}
```

### Pattern 3: SquireBrainEventBus (NEW in v2)

**What:** Lightweight in-process observer bus scoped to one squire instance. NOT NeoForge's game bus.

**Why:** v0.5.0 used direct `brain.getMining().clear()` calls between handlers. v2 replaces with publish/subscribe to eliminate coupling chains.

**Phase 2 only needs 3 events** — define more in later phases:
```java
public enum SquireEvent {
    SIT_TOGGLE,      // published when squire enters/exits SITTING state
    COMBAT_START,    // published when combat handler enters combat (Phase 4 populates subscribers)
    COMBAT_END       // published when combat ends (Phase 4 populates subscribers)
}
```

**Full implementation (simple — not a framework):**
```java
// Source: ARCHITECTURE.md Pattern 4
public class SquireBrainEventBus {
    private final Map<SquireEvent, List<Consumer<SquireEntity>>> listeners =
            new EnumMap<>(SquireEvent.class);

    public void subscribe(SquireEvent event, Consumer<SquireEntity> handler) {
        listeners.computeIfAbsent(event, k -> new ArrayList<>()).add(handler);
    }

    public void publish(SquireEvent event, SquireEntity squire) {
        List<Consumer<SquireEntity>> list = listeners.get(event);
        if (list != null) list.forEach(h -> h.accept(squire));
    }
}
```

**Phase 2 subscriber wiring in SquireBrain constructor:**
```java
// SurvivalHandler subscribes to SIT_TOGGLE to reset eating state when squire sits
bus.subscribe(SquireEvent.SIT_TOGGLE, s -> survival.reset());
```

### Pattern 4: FollowHandler — No-Teleport Navigation

**What:** Handles follow behavior. Critical difference from v0.5.0: `tryTeleportToOwner()` is REMOVED. No fallback. The squire must path through terrain regardless of distance.

**Stuck detection and recovery (replaces teleport):**

```java
// Stuck detection: track position over time, detect no movement
private Vec3 lastPos = null;
private int stuckTicks = 0;
private static final int STUCK_THRESHOLD = 60; // 3 seconds with no progress

// In tick():
Vec3 currentPos = s.position();
if (lastPos != null && currentPos.distanceTo(lastPos) < 0.1) {
    stuckTicks++;
    if (stuckTicks >= STUCK_THRESHOLD) {
        handleStuck(s, owner);
        stuckTicks = 0;
    }
} else {
    stuckTicks = 0;
}
lastPos = currentPos;
```

**Stuck recovery options (in priority order, no teleport):**
1. Apply jump boost (`MobEffects.JUMP`) — helps with stairs and cliffs
2. Force navigation recalculation — path may have found an alternate route
3. If water: ensure swimming is active (`setSwimming(true)`)
4. Log the stuck event for debugging — don't silently fail

**Water traversal:**
```java
// In SquireEntity constructor or createNavigation():
// Option A: setCanFloat(true) — simplest, works for most river crossing
this.getNavigation().setCanFloat(true);

// Option B: configure PathType costs to make water walkable
// Required if setCanFloat alone isn't enough for deep water
// getNavigation().getNodeEvaluator().setCanFloat(true)  // depends on evaluator type
```

**Speed scaling (port from v0.5.0, keep as-is):**
```java
// Source: v0.5.0 FollowHandler.java lines 94-99
double sprintThresh = SquireConfig.sprintDistance.get();
boolean shouldSprint = distSq > sprintThresh * sprintThresh || owner.isSprinting();
s.setSquireSprinting(shouldSprint);
double speed = shouldSprint ? 1.3D : 1.0D;
s.getNavigation().moveTo(owner, speed);
```

**Door navigation:**
```java
// In FollowHandler.start() — enable doors on follow entry
if (squire.getNavigation() instanceof GroundPathNavigation groundNav) {
    groundNav.setCanOpenDoors(true);
    groundNav.setCanPassDoors(true);
}
// In FollowHandler.stop() — disable to save processing
if (squire.getNavigation() instanceof GroundPathNavigation groundNav) {
    groundNav.setCanOpenDoors(false);
    groundNav.setCanPassDoors(false);
}
```

**registerGoals() must include OpenDoorGoal** (in SquireEntity, not FollowHandler):
```java
// In SquireEntity.registerGoals()
this.goalSelector.addGoal(0, new FloatGoal(this));        // swim
this.goalSelector.addGoal(1, new OpenDoorGoal(this, true)); // door opening
this.targetSelector.addGoal(1, new OwnerHurtByTargetGoal(this));
```

### Pattern 5: SurvivalHandler — IItemHandler Port

**What:** Eating/healing at FSM priority 20. Port from v0.5.0 but adapt inventory access.

**Critical v0.5.0 → v2 change:** v0.5.0 uses `SquireInventory` (SimpleContainer). v2 uses `SquireItemHandler` (IItemHandler). Slot access changes:

```java
// v0.5.0 (do NOT use in v2):
SquireInventory inventory = squire.getSquireInventory();
ItemStack stack = inventory.getItem(i);

// v2 (IItemHandler):
IItemHandler inv = squire.getItemHandler();  // via capability
ItemStack stack = inv.getStackInSlot(i);     // READ ONLY — do not mutate
// To consume: inv.extractItem(slot, 1, false)
```

**Health threshold config:**
```java
// v0.5.0 used SquireConfig.eatHealthThreshold — carry forward to v2 config
float ratio = squire.getHealth() / squire.getMaxHealth();
if (ratio >= SquireConfig.eatHealthThreshold.get()) return false;
```

### Pattern 6: StreamCodec Payloads (ARC-08)

**What:** All network payloads use `StreamCodec`. No raw `FriendlyByteBuf` reads/writes.

**v0.5.0 already uses this pattern** — port both payloads directly:

```java
// Source: v0.5.0 SquireCommandPayload.java (confirmed StreamCodec.composite pattern)
public static final StreamCodec<ByteBuf, SquireCommandPayload> STREAM_CODEC =
        StreamCodec.composite(
                ByteBufCodecs.VAR_INT, SquireCommandPayload::commandId,
                ByteBufCodecs.VAR_INT, SquireCommandPayload::squireEntityId,
                SquireCommandPayload::new
        );

// Source: v0.5.0 SquireModePayload.java
public static final StreamCodec<ByteBuf, SquireModePayload> STREAM_CODEC =
        StreamCodec.composite(
                ByteBufCodecs.VAR_INT, SquireModePayload::squireEntityId,
                SquireModePayload::new
        );
```

**Registration pattern (from v0.5.0, works on NeoForge 1.21.1):**
```java
// In a @SubscribeEvent for RegisterPayloadHandlersEvent on MOD bus:
public static void register(RegisterPayloadHandlersEvent event) {
    PayloadRegistrar registrar = event.registrar("1");
    registrar.playToServer(TYPE, STREAM_CODEC, SquireCommandPayload::handle);
}
```

**Phase 2 scope for CMD IDs** — only register what's implemented:
- `CMD_FOLLOW = 0` — set MODE_FOLLOW
- `CMD_STAY = 3` — set MODE_STAY (sit/stay toggle, NAV-04)

The other CMD IDs (guard, patrol, store, fetch, mount, inventory, come) come in later phases. Define the constants, but don't handle them yet — `default -> {}` in the switch is safe.

### Pattern 7: SquireAIState Enum — Phase 2 Subset

Phase 2 implements only the states needed for Follow + Survival. Define ALL states from v0.5.0 upfront (they cost nothing), so later plans don't require enum changes that force FSM rebuild.

**Full state list (from v0.5.0 — copy verbatim):**
```java
public enum SquireAIState {
    IDLE, EATING, FLEEING,
    COMBAT_APPROACH, COMBAT_ATTACK, COMBAT_RANGED,
    MOUNTING, MOUNTED_IDLE, MOUNTED_FOLLOW, MOUNTED_COMBAT,
    FOLLOWING_OWNER, SITTING,
    MINING_APPROACH, MINING_BREAK,
    PLACING_APPROACH, PLACING_BLOCK, PICKING_UP_ITEM,
    CHEST_APPROACH, CHEST_INTERACT,
    PATROL_WALK, PATROL_WAIT,
    FARM_APPROACH, FARM_WORK, FARM_SCAN,
    FISHING_APPROACH, FISHING_IDLE,
    LOOKING_AROUND, WANDERING
}
```

**Active in Phase 2:** IDLE, EATING, FOLLOWING_OWNER, SITTING

### Anti-Patterns to Avoid

- **Teleporting when stuck:** NAV-01 is absolute. Remove `tryTeleportToOwner()` entirely. No distance check that falls back to teleport.
- **Using NeoForge game bus for handler communication:** Firing per-squire events onto `NeoForge.EVENT_BUS` leaks to all 449 mods in ATM10. Use `SquireBrainEventBus` (instance-scoped).
- **Placing behavior state in SquireEntity:** Fields like `pathRecalcTimer` belong in `FollowHandler`, not the entity class.
- **Mutating ItemStack from getStackInSlot():** Call `extractItem()` to consume food — never `.shrink()` on the returned stack.
- **Reading entity tags at FML setup time:** Tags are empty until the world loads. Fine for Phase 2 (no tag queries), but set the pattern now.
- **Skipping `setCanFloat(true)` in navigation setup:** Without this, the squire sinks in rivers instead of swimming (NAV-03 failure).

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
| ------- | ----------- | ----------- | --- |
| Door opening during follow | Custom door-detection loop | `OpenDoorGoal` in `registerGoals()` + `groundNav.setCanOpenDoors(true)` | Vanilla handles block interaction timing and door states correctly |
| Water pathfinding | Custom swim-toward-player logic | `setCanFloat(true)` on navigation + `FloatGoal` already registered | PathfinderMob navigation handles water nodes when configured |
| Sprint detection | Custom speed threshold check | Reuse v0.5.0 pattern: `distSq > threshold^2 OR owner.isSprinting()` | Proven formula; covers the edge case where player sprints in-place |
| Packet serialization | Manual `buf.writeInt()` / `buf.readInt()` | `StreamCodec.composite()` with `ByteBufCodecs.*` | ARC-08 requirement; also catches encoding mismatches at compile time |
| Event broadcasting | NeoForge `IEventBus` | `SquireBrainEventBus` (instance-scoped EnumMap) | Global bus adds overhead proportional to mod count (449 in ATM10) |

---

## Common Pitfalls

### Pitfall 1: FollowHandler Teleport Removal Must Be Total

**What goes wrong:** Developer removes `tryTeleportToOwner()` but leaves the distance check that called it. Squire stops issuing navigation commands when farther than 24 blocks — effectively freezes.

**How to avoid:** Remove both the method and the `if (distSq > 24*24)` early-return block. The stuck detection loop replaces the distance check entirely. Test by running 25+ blocks away and confirming the squire still pathfinds.

**Warning signs:** Squire follows correctly at short distance but freezes when player sprints ahead.

### Pitfall 2: PathRecalcTimer Too Short Causes Navigation Thrash

**What goes wrong:** If path recalculation fires every tick, navigation re-issues `moveTo()` before the previous path resolves. Entity stutters or spins in place. MSPT spikes under load.

**How to avoid:** Keep `pathRecalcInterval` from SquireConfig (v0.5.0 default is configurable). Start at 10 ticks (0.5s) as the minimum. Only recalculate when: timer expires, OR owner teleported (dimension change).

**Warning signs:** Entity oscillates position without moving toward owner, high CPU on navigation threads.

### Pitfall 3: SurvivalHandler IItemHandler Slot Access Wrong

**What goes wrong:** Developer calls `inv.getStackInSlot(i)` and then calls `.shrink(1)` on the result — direct mutation of the handler's internal stack. Food consumed from wrong slot, or count corrupts.

**How to avoid:** Food consumption must use `inv.extractItem(foodSlot, 1, false)`. The `getStackInSlot()` return is a READ-ONLY view per the IItemHandler contract.

**Warning signs:** Two slots showing same item, item counts going negative, hopper interactions breaking after SurvivalHandler runs.

### Pitfall 4: SquireBrainEventBus Subscriber Registered After Publish

**What goes wrong:** SquireBrain constructor wires handlers out of order. A `publish()` fires during construction before the subscriber is registered. Event silently drops.

**How to avoid:** Register all subscriptions BEFORE any transitions that could fire during the first tick. Subscribe in the `SquireBrain` constructor before calling `registerTransitions()`.

**Warning signs:** Handlers don't react to events on the first behavior entry, but work correctly on subsequent entries.

### Pitfall 5: FSM Transitions Missing Guard Against Current State

**What goes wrong:** Global EATING transition fires while already eating (no guard for `machine.getCurrentState() != EATING`). The handler's `startEating()` resets the timer mid-eat. Squire eats the same food item twice.

**How to avoid:** Every global transition that enters a state must guard against already being in that state:
```java
// Global EATING entry — must have this guard:
() -> machine.getCurrentState() != SquireAIState.EATING && survival.shouldEat()
```

**Warning signs:** Food consumed twice, healing fires multiple times per eat cycle.

### Pitfall 6: `setCanFloat(true)` Not Enough for Deep Water on All Terrain

**What goes wrong:** `setCanFloat(true)` prevents drowning but doesn't configure the node evaluator to path THROUGH water blocks. Squire navigates to water edge, stops, oscillates.

**How to avoid:** After calling `setCanFloat(true)`, also verify the node evaluator's water pathfinding is active. GroundPathNavigation uses `WalkNodeEvaluator` by default, which has WATER_BORDER as walkable. For rivers/lakes, this is usually sufficient. For deep ocean: may need `AmphibiousNodeEvaluator`. Test specifically: walk player through a 3-block-deep river and confirm squire follows.

**Warning signs:** Squire walks to riverbank and stops. Squire visible swimming-in-place at water surface without crossing.

### Pitfall 7: SynchedEntityData MODE Sync Not Wired in Phase 1

**What goes wrong:** Phase 2 sets `squire.setSquireMode(MODE_STAY)` but the SynchedEntityData accessor for mode wasn't defined in Phase 1. NPE on first mode change, or mode changes don't sync to client.

**How to avoid:** Verify that `SquireEntity` already has `SQUIRE_MODE` SynchedEntityData defined before writing Phase 2 network handlers. If Phase 1 is incomplete on this point, add it as a dependency in the plan.

**Warning signs:** `NullPointerException` in `SynchedEntityData` when the payload handle fires. Squire shows wrong animation state on client after mode toggle.

---

## Code Examples

### FSM Transition Registration — Sitting (Phase 2, plan 02-01)

```java
// Source: v0.5.0 SquireAI.java lines 141-163 (priority 1, global)
private void registerSittingTransitions() {
    // Enter SITTING — global, fires from any state when ordered to sit
    machine.addTransition(new AITransition(
            null,  // null = global
            () -> machine.getCurrentState() != SquireAIState.SITTING
                    && squire.isOrderedToSit()
                    && !squire.isInWaterOrBubble(),
            s -> {
                s.getNavigation().stop();
                s.setInSittingPose(true);
                bus.publish(SquireEvent.SIT_TOGGLE, s);  // NEW in v2
                return SquireAIState.SITTING;
            },
            1, 1  // tickRate=1 (every tick), priority=1 (highest)
    ));

    // Exit SITTING when order released
    machine.addTransition(new AITransition(
            SquireAIState.SITTING,
            () -> !squire.isOrderedToSit(),
            s -> {
                s.setInSittingPose(false);
                bus.publish(SquireEvent.SIT_TOGGLE, s);  // NEW in v2
                return SquireAIState.IDLE;
            },
            1, 1
    ));
}
```

### FSM Transition Registration — Follow (Phase 2, plan 02-03)

```java
// Source: v0.5.0 SquireAI.java lines 293-322 (priority 30, state-specific)
private void registerFollowTransitions() {
    // IDLE → FOLLOWING_OWNER when owner far enough
    machine.addTransition(new AITransition(
            SquireAIState.IDLE,
            follow::shouldFollow,
            s -> { follow.start(); return SquireAIState.FOLLOWING_OWNER; },
            10, 30   // check every 10 ticks — not every tick
    ));

    // Exit FOLLOWING_OWNER when close or invalid
    machine.addTransition(new AITransition(
            SquireAIState.FOLLOWING_OWNER,
            follow::shouldStop,
            s -> { follow.stop(); return SquireAIState.IDLE; },
            1, 29   // priority 29 — lower priority number checks before tick
    ));

    // Tick FOLLOWING_OWNER
    machine.addTransition(new AITransition(
            SquireAIState.FOLLOWING_OWNER,
            () -> !follow.shouldStop(),
            follow::tick,  // returns FOLLOWING_OWNER normally
            1, 30
    ));
}
```

### SquireModePayload Handle — Sit/Stay Toggle (NAV-04)

```java
// Source: v0.5.0 SquireModePayload.java lines 81-96 (adapted for v2)
private static void handle(SquireModePayload payload, IPayloadContext context) {
    if (!(context.player() instanceof ServerPlayer serverPlayer)) return;
    Entity entity = serverPlayer.serverLevel().getEntity(payload.squireEntityId());
    if (!(entity instanceof SquireEntity squire)) return;
    if (!squire.isOwnedBy(serverPlayer)) return;

    byte current = squire.getSquireMode();
    byte next = (current == SquireEntity.MODE_FOLLOW)
            ? SquireEntity.MODE_STAY
            : SquireEntity.MODE_FOLLOW;
    squire.setSquireMode(next);
    // FSM picks up the mode change on next tick via isOrderedToSit() check
}
```

### FollowHandler Stuck Detection (no-teleport, v2 addition)

```java
// New in v2 — replaces tryTeleportToOwner()
private Vec3 lastStuckCheckPos = null;
private int stuckTicks = 0;
private static final int STUCK_THRESHOLD = 60; // 3 s at 20 tps

private void handleStuck(SquireEntity s, Player owner) {
    // Try jump boost first — covers stairs, 1-block lips
    if (!s.hasEffect(MobEffects.JUMP)) {
        s.addEffect(new MobEffectInstance(MobEffects.JUMP, 60, 2, false, false));
    }
    // Force path recalculation
    pathRecalcTimer = 0;
    // Log for debugging
    var log = s.getActivityLog();
    if (log != null) log.log("FOLLOW", "Stuck — applying jump boost and replanning path");
}

// In tick(), before moveTo():
Vec3 pos = s.position();
if (lastStuckCheckPos != null && pos.distanceTo(lastStuckCheckPos) < 0.15) {
    if (++stuckTicks >= STUCK_THRESHOLD) {
        handleStuck(s, owner);
        stuckTicks = 0;
    }
} else {
    stuckTicks = 0;
    lastStuckCheckPos = pos;
}
```

---

## State of the Art

| Old Approach (v0.5.0) | v2 Approach | Why Changed |
| --------------------- | ----------- | ----------- |
| `SquireAI` class name | `SquireBrain` | Clearer intent; "AI" is ambiguous, "Brain" maps to the Brain/Entity split pattern |
| Direct handler cross-calls (`brain.getMining().clear()`) | `SquireBrainEventBus.publish()` | ARC-03 requirement; reduces coupling chains |
| `tryTeleportToOwner()` emergency fallback | Stuck detection + jump boost + path replan | NAV-01 absolute — no teleport under any circumstance |
| `SquireInventory` (SimpleContainer) in SurvivalHandler | `IItemHandler` capability access | v2 uses IItemHandler from day one (Phase 1 ARC decision) |
| `TamableAnimal.isOrderedToSit()` | Custom `squire.isOrderedToSit()` via `getSquireMode() == MODE_STAY` | PathfinderMob base; no TamableAnimal sit behavior |

**Deprecated/outdated:**

- `tryTeleportToOwner()`: Remove entirely. No migration path.
- `SquireInventory.getItem(slot)` for consumption: Replace with `IItemHandler.extractItem(slot, 1, false)`.

---

## Open Questions

1. **Phase 1 completion state**
   - What we know: Phase 1 plan 01-01 is complete (scaffold, SquireRegistry); plans 01-02 through 01-05 are pending.
   - What's unclear: Whether `SquireEntity` has `SQUIRE_MODE` SynchedEntityData and `isOrderedToSit()` defined — Phase 2 network handlers need these.
   - Recommendation: Plan 02-04 must declare a hard dependency on Phase 1 plan 01-02 being complete. If 01-02 is done at plan time, verify the synced data exists before writing the payload handler.

2. **Water pathfinding depth threshold**
   - What we know: `setCanFloat(true)` handles shallow river crossing. `GroundPathNavigation` uses `WalkNodeEvaluator` which supports water border traversal.
   - What's unclear: Whether ATM10's terrain generation produces water bodies deep enough to require `AmphibiousNodeEvaluator` instead of just `setCanFloat(true)`.
   - Recommendation: Start with `setCanFloat(true)` + `FloatGoal`. Test in a 3-block-deep river. If the squire oscillates at the bank, switch to `AmphibiousNodeEvaluator` in `createNavigation()`. Document the threshold in config.

3. **`isOrderedToSit()` implementation in v2**
   - What we know: v0.5.0 used TamableAnimal's built-in. v2 uses PathfinderMob with custom owner. The mode check is `getSquireMode() == MODE_STAY`.
   - What's unclear: Whether Phase 1 plan 01-02 adds a convenience `isOrderedToSit()` method or requires inline mode checks.
   - Recommendation: Plan 02-01 should add `public boolean isOrderedToSit() { return getSquireMode() == MODE_STAY; }` to SquireEntity during FSM setup if it's not already there.

---

## Validation Architecture

### Test Framework

| Property | Value |
| --- | --- |
| Framework | JUnit 5 (TST-01) + NeoForge GameTest (TST-02) |
| Config file | Established in Phase 1 (plan 01-05) |
| Quick run command | `./gradlew test` |
| Full suite command | `./gradlew test gameTestServer` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
| ------ | -------- | --------- | ----------------- | ------------ |
| ARC-01 | FSM transitions fire in priority order | unit | `./gradlew test --tests "*.TickRateStateMachineTest"` | No — Wave 0 |
| ARC-01 | Global transitions interrupt state-specific | unit | `./gradlew test --tests "*.TickRateStateMachineTest"` | No — Wave 0 |
| ARC-02 | Handler owns its mutable state (no SquireEntity fields for behavior) | unit | Code review + `./gradlew test --tests "*.FollowHandlerTest"` | No — Wave 0 |
| ARC-03 | EventBus delivers events to all subscribers | unit | `./gradlew test --tests "*.SquireBrainEventBusTest"` | No — Wave 0 |
| ARC-03 | Late subscriber does not receive pre-registration events | unit | same | No — Wave 0 |
| ARC-08 | StreamCodec round-trips without data loss | unit | `./gradlew test --tests "*.SquireCommandPayloadTest"` | No — Wave 0 |
| NAV-01 | Squire does not teleport at any distance | GameTest | `./gradlew gameTestServer --tests "*.FollowNoTeleportTest"` | No — Wave 0 |
| NAV-02 | Squire sprints when beyond threshold | GameTest | `./gradlew gameTestServer --tests "*.FollowSprintTest"` | No — Wave 0 |
| NAV-04 | Sit/stay toggle stops movement | GameTest | `./gradlew gameTestServer --tests "*.SitStayToggleTest"` | No — Wave 0 |

**NAV-03 and NAV-05 (water traversal, terrain navigation):** Manual verification — requires spawned water/terrain environment that GameTest can provide but is complex to set up in Wave 0. Tag for integration test pass at phase gate.

### Sampling Rate

- **Per task commit:** `./gradlew test`
- **Per wave merge:** `./gradlew test gameTestServer`
- **Phase gate:** Full suite green before `/gsd:verify-work`

### Wave 0 Gaps

- [ ] `src/test/java/.../brain/TickRateStateMachineTest.java` — covers ARC-01 priority ordering
- [ ] `src/test/java/.../brain/SquireBrainEventBusTest.java` — covers ARC-03 subscribe/publish
- [ ] `src/test/java/.../network/SquireCommandPayloadTest.java` — covers ARC-08 codec round-trip
- [ ] `src/test/java/.../handler/FollowHandlerTest.java` — covers NAV-01 no-teleport assertion, NAV-02 sprint threshold
- [ ] `src/gametest/java/.../FollowNoTeleportTest.java` — in-world NAV-01 verification
- [ ] `src/gametest/java/.../SitStayToggleTest.java` — in-world NAV-04 verification

---

## Sources

### Primary (HIGH confidence)

- `C:/Users/Steve/Projects/squire-mod/src/main/java/com/sjviklabs/squire/ai/statemachine/TickRateStateMachine.java` — FSM engine, full source
- `C:/Users/Steve/Projects/squire-mod/src/main/java/com/sjviklabs/squire/ai/statemachine/SquireAI.java` — Transition registry, full source
- `C:/Users/Steve/Projects/squire-mod/src/main/java/com/sjviklabs/squire/ai/statemachine/SquireAIState.java` — State enum, full source
- `C:/Users/Steve/Projects/squire-mod/src/main/java/com/sjviklabs/squire/ai/handler/FollowHandler.java` — Follow logic, full source
- `C:/Users/Steve/Projects/squire-mod/src/main/java/com/sjviklabs/squire/ai/handler/SurvivalHandler.java` — Survival logic, full source
- `C:/Users/Steve/Projects/squire-mod/src/main/java/com/sjviklabs/squire/network/SquireCommandPayload.java` — StreamCodec pattern, full source
- `C:/Users/Steve/Projects/squire-mod/src/main/java/com/sjviklabs/squire/network/SquireModePayload.java` — Mode toggle payload, full source
- `.planning/research/ARCHITECTURE.md` — Patterns 1-7, anti-patterns, integration points
- `.planning/research/PITFALLS.md` — Pitfalls 9 (navigation/doors/water), 4 (IItemHandler mutation), 1 (SynchedEntityData), 4 (Geckolib — out of scope for Phase 2)

### Secondary (MEDIUM confidence)

- NeoForge docs — PathfinderMob navigation: https://docs.neoforged.net/docs/entities/livingentity/
- NeoForge network payload registration pattern: https://docs.neoforged.net/docs/networking/payload

---

## Metadata

**Confidence breakdown:**

- FSM engine (TickRateStateMachine, AITransition, SquireAIState): HIGH — full v0.5.0 source read; port is mechanical
- SquireBrain (renamed SquireAI): HIGH — structural change only; logic identical
- SquireBrainEventBus: HIGH — standard Java observer pattern; simple enough to be correct by inspection
- FollowHandler (no-teleport): HIGH for the deletion; MEDIUM for stuck detection specifics (threshold values need tuning)
- SurvivalHandler (IItemHandler port): HIGH — IItemHandler contract is well-documented; extractItem() is unambiguous
- StreamCodec payloads: HIGH — v0.5.0 already uses this exact pattern; copy is safe
- Water pathfinding depth limit: MEDIUM — setCanFloat(true) is documented; deep-water evaluator switchover threshold is empirical

**Research date:** 2026-04-03
**Valid until:** 2026-05-03 (stable NeoForge 1.21.1 APIs; v0.5.0 source is read-only reference)

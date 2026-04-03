---
phase: 02-brain-fsm-follow
verified: 2026-04-02T00:00:00Z
status: passed
score: 9/9 must-haves verified
re_verification: false
---

# Phase 2: Brain FSM Follow — Verification Report

**Phase Goal:** The squire walks with the player through any terrain without ever teleporting
**Verified:** 2026-04-02
**Status:** PASSED
**Re-verification:** No — initial verification

---

## Goal Achievement

### Observable Truths

| #  | Truth                                                        | Status     | Evidence                                                             |
|----|--------------------------------------------------------------|------------|----------------------------------------------------------------------|
| 1  | Squire follows player via pathfinding only — zero teleports  | VERIFIED   | grep of entire src/ tree: "teleport" appears in comments/no-ops only. FollowHandler sole locomotion call is `navigation.moveTo()` |
| 2  | Squire sprints to catch up when player is far or sprinting   | VERIFIED   | FollowHandler.tick() L120-124: `distSq > sprintThresh^2 or owner.isSprinting()` → 1.3D speed + `setSquireSprinting()` |
| 3  | Squire navigates water via FloatGoal and setCanFloat         | VERIFIED   | SquireEntity constructor L93: `getNavigation().setCanFloat(true)`; registerGoals L239: `FloatGoal`; handleStuck L146: `setSwimming(true)` |
| 4  | Squire sits/stays on CMD_STAY, resumes on CMD_FOLLOW         | VERIFIED   | SquireCommandPayload.handle(): CMD_STAY → MODE_SIT, CMD_FOLLOW → MODE_FOLLOW; SITTING FSM transitions wire the state |
| 5  | Squire navigates doors, hills, caves via pathfinding         | VERIFIED   | OpenDoorGoal in registerGoals L241; FollowHandler.start/stop enable/disable `setCanOpenDoors(true/false)`; TickRateStateMachine drives all movement |
| 6  | TickRateStateMachine replaces vanilla GoalSelector for FSM   | VERIFIED   | TickRateStateMachine.java: dirty flag, per-transition countdown, globalIndices, rebuild(); called from SquireBrain.tick() → SquireEntity.aiStep() |
| 7  | Handler-per-behavior pattern: one class per behavior         | VERIFIED   | FollowHandler.java and SurvivalHandler.java are separate classes, each owning their own mutable state |
| 8  | SquireBrainEventBus with subscribe/publish pattern           | VERIFIED   | SquireBrainEventBus.java: EnumMap-backed, subscribe()/publish(); SIT_TOGGLE fired on SITTING enter/exit; SurvivalHandler.reset() subscribed |
| 9  | All payloads use StreamCodec.composite() — no raw byte buf   | VERIFIED   | SquireCommandPayload L47-52, SquireModePayload L32-36: both use `StreamCodec.composite(ByteBufCodecs.VAR_INT, ...)`; zero FriendlyByteBuf writes in production code |

**Score: 9/9 truths verified**

---

## Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `src/main/java/com/sjviklabs/squire/brain/SquireAIState.java` | 27-state enum, all Phase 2+ states | VERIFIED | 27 constants present: IDLE through WANDERING, all layers |
| `src/main/java/com/sjviklabs/squire/brain/AITransition.java` | Record with nullable sourceState, isGlobal() | VERIFIED | Record with 5 fields, validation guards, isGlobal()/appliesTo() helpers |
| `src/main/java/com/sjviklabs/squire/brain/TickRateStateMachine.java` | Per-transition countdown engine | VERIFIED | dirty flag, countdowns[], globalIndices[], rebuild(), tick(SquireEntity) |
| `src/main/java/com/sjviklabs/squire/brain/SquireActivityLog.java` | Ring-buffer debug logger, config-gated | VERIFIED | ArrayDeque 100-entry max, SLF4J output, gated on SquireConfig.activityLogging |
| `src/main/java/com/sjviklabs/squire/brain/SquireEvent.java` | Enum with SIT_TOGGLE, COMBAT_START, COMBAT_END | VERIFIED | All 3 events present |
| `src/main/java/com/sjviklabs/squire/brain/SquireBrainEventBus.java` | EnumMap subscribe/publish, instance-scoped | VERIFIED | EnumMap<SquireEvent, List<Consumer<SquireEntity>>>, subscribe()/publish() |
| `src/main/java/com/sjviklabs/squire/brain/SquireBrain.java` | Owns machine + bus + handlers, correct init order | VERIFIED | machine → bus → handlers → subscribe → registerTransitions(); tick() calls machine.tick(squire) |
| `src/main/java/com/sjviklabs/squire/brain/handler/FollowHandler.java` | No-teleport follow, stuck detection, sprint scaling | VERIFIED | moveTo() only locomotion; STUCK_THRESHOLD=60; jump boost recovery; door nav lifecycle |
| `src/main/java/com/sjviklabs/squire/brain/handler/SurvivalHandler.java` | extractItem() food consumption, eat cooldown | VERIFIED | extractItem() at L63; shrink()/setCount() appear only in doc comments |
| `src/main/java/com/sjviklabs/squire/network/SquireCommandPayload.java` | StreamCodec.composite(), CMD_STAY/CMD_FOLLOW | VERIFIED | StreamCodec.composite() with ByteBufCodecs.VAR_INT; handler routes CMD_STAY→MODE_SIT, CMD_FOLLOW→MODE_FOLLOW |
| `src/main/java/com/sjviklabs/squire/network/SquireModePayload.java` | StreamCodec.composite(), registered stub | VERIFIED | StreamCodec.composite(); no-op handler; registered in SquireRegistry.registerPayloads() |

---

## Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `SquireEntity.aiStep()` | `SquireBrain.tick()` | lazy init guard | WIRED | L326-334: `if squireBrain == null: new SquireBrain(this)`; `squireBrain.tick()` called every server aiStep |
| `SquireEntity.registerGoals()` | `FloatGoal + OpenDoorGoal` | `goalSelector.addGoal` | WIRED | L239-241: both goals present at priority 0 and 1 |
| `SquireBrain` | `TickRateStateMachine` | `machine.tick(squire)` | WIRED | SquireBrain.tick() L62: `machine.tick(squire)` |
| `SquireBrain constructor` | `bus subscriptions before transitions` | ordering enforced | WIRED | subscribe() at L47 precedes registerTransitions() at L50 — enforced by comment |
| `FollowHandler.tick()` | `navigation.moveTo()` | pathfinding only | WIRED | FollowHandler L127: sole locomotion call; no teleport path exists |
| `SquireBrain.registerSittingTransitions()` | `bus.publish(SIT_TOGGLE)` | transition action lambda | WIRED | L152 and L165: both enter/exit SITTING publish SIT_TOGGLE |
| `SurvivalHandler.reset()` | `bus.subscribe(SIT_TOGGLE)` | SquireBrain L47 | WIRED | `bus.subscribe(SquireEvent.SIT_TOGGLE, s -> survival.reset())` |
| `SquireRegistry.registerPayloads()` | Both payload types registered | `@SubscribeEvent` on MOD bus | WIRED | L134-146: both payloads registered via PayloadRegistrar.playToServer() |
| `SurvivalHandler.startEating()` | `IItemHandler.extractItem()` | no shrink() path | WIRED | L63: `inv.extractItem(i, 1, false)`; shrink/setCount absent from production code |

---

## Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|-------------|-------------|--------|----------|
| NAV-01 | 02-03 | Squire follows player without teleporting | SATISFIED | Zero functional teleport calls in src/; FollowHandler comment: "no teleportation ever"; SquireEntity.tryToTeleportToOwner() is a documented no-op |
| NAV-02 | 02-03 | Squire sprints to catch up when player is far | SATISFIED | FollowHandler.tick() L121-124: sprint condition + 1.3D speed factor |
| NAV-03 | 02-03 | Squire swims through water to follow | SATISFIED | FloatGoal L239, setCanFloat(true) L93, setSwimming(true) in handleStuck L146 |
| NAV-04 | 02-04 | Squire can sit/stay on command (toggle) | SATISFIED | SquireCommandPayload CMD_STAY/CMD_FOLLOW handler; SITTING transitions in SquireBrain; SIT_TOGGLE published/subscribed |
| NAV-05 | 02-01, 02-03 | Squire navigates caves, hills, structures via pathfinding | SATISFIED | TickRateStateMachine drives all movement; OpenDoorGoal; stuck detection + jump boost recovery for obstacles |
| ARC-01 | 02-01 | Custom tick-rate FSM replacing vanilla GoalSelector | SATISFIED | TickRateStateMachine.java: per-transition countdown, dirty flag, globalIndices; called from SquireBrain.tick() every aiStep |
| ARC-02 | 02-02 | Handler-per-behavior pattern | SATISFIED | FollowHandler.java and SurvivalHandler.java are separate classes, each owning mutable state |
| ARC-03 | 02-02 | Internal event bus for handler cross-communication | SATISFIED | SquireBrainEventBus.java: subscribe()/publish(); SIT_TOGGLE round-trips through bus |
| ARC-08 | 02-04 | Network payloads via StreamCodec, not manual FriendlyByteBuf | SATISFIED | Both payloads: StreamCodec.composite(ByteBufCodecs.VAR_INT, ...); zero raw buffer writes anywhere |

**All 9 required IDs satisfied. No orphaned requirements.**

---

## Anti-Patterns Found

None. Scan of brain package and network package found:

- No TODO/FIXME/PLACEHOLDER in production code
- No return null/return {}/return [] stub implementations
- `shrink()` and `setCount()` appear only in Javadoc comments explicitly documenting the anti-pattern to avoid
- `tryToTeleportToOwner()` is a documented intentional no-op — not a stub, the implementation IS the no-op

---

## Human Verification Required

### 1. Follow Behavior Across Terrain

**Test:** Spawn squire, set to MODE_FOLLOW, walk through: flat land, uphill slope, into a lake, through a door, into a cave.
**Expected:** Squire paths behind player continuously at each terrain type without teleporting. Sticks at obstacles for ~3 seconds then recovers with a jump.
**Why human:** Pathfinding quality, navigation feel, and terrain-specific recovery can only be evaluated in a running game client.

### 2. Sprint Catch-up Feel

**Test:** Walk then sprint away from squire at various distances.
**Expected:** Squire noticeably accelerates when player is far or sprinting. Does not feel like it teleports to catch up.
**Why human:** Speed differential and visual follow behavior require in-game observation.

### 3. CMD_STAY / CMD_FOLLOW Toggle

**Test:** Send CMD_STAY payload from client; verify squire stops and enters sitting pose. Send CMD_FOLLOW; verify squire resumes following.
**Expected:** Squire changes pose visually and stops navigation on CMD_STAY. Resumes follow on CMD_FOLLOW.
**Why human:** SynchedEntityData pose rendering and mode sync require in-game client verification.

---

## Gaps Summary

No gaps. All 9 required truths verified at all three levels (exists, substantive, wired).

The single notable design choice is that `SquireModePayload` handler is a no-op in Phase 2 — this is intentional and documented. Mode is set authoritatively via `SquireCommandPayload`. The payload is pre-registered for Phase 5 GUI use.

---

_Verified: 2026-04-02_
_Verifier: Claude (gsd-verifier)_

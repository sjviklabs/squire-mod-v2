# Phase 7: Patrol and Mounting — Research

**Researched:** 2026-04-02
**Domain:** NeoForge 1.21.1 — BlockEntity waypoints, PathNavigation patrol, AbstractHorse NPC riding, mounted combat
**Confidence:** HIGH (MC 1.21.1 decompiled source verified; v0.5.0 reference read directly)

---

## Summary

Phase 7 splits cleanly into two orthogonal subsystems: patrol (SignpostBlock + PatrolHandler) and mounting (MountHandler + mounted combat). The patrol subsystem is straightforward — HorizontalDirectionalBlock + BlockEntity + linked-list route traversal via PathNavigation. The mounting subsystem has one critical landmine that must be designed around from the start.

**The core mounted-navigation problem:** `AbstractHorse.getControllingPassenger()` returns a `Player` only. `LivingEntity`'s inner movement loop gates `travelRidden()` on `getControllingPassenger() instanceof Player`. When the squire (a `PathfinderMob`, not a `Player`) is riding the horse, the horse receives no movement input and sits still. The v0.5.0 `MountHandler` solved this correctly: call `horse.move(MoverType.SELF, vec)` directly each tick, bypassing the vanilla travel/goal systems entirely. This is proven working code — use it verbatim.

The v0.5.0 implementations of both `SignpostBlock`, `SignpostBlockEntity`, `PatrolHandler`, and `MountHandler` are clean, compile against NeoForge 21.1.221 patterns, and should port directly with structural changes (handler-per-behavior pattern, SquireRegistry for block registration) rather than rewrites.

**Primary recommendation:** Port v0.5.0 SignpostBlock/Entity and both handlers directly into v2's architecture. The MoverType.SELF horse driving solution is the correct and only viable approach — do not attempt to use PathNavigation on the horse.

---

<phase_requirements>

## Phase Requirements

| ID | Description | Research Support |
| --- | --- | --- |
| PTR-01 | Signpost block that serves as patrol waypoint | SignpostBlock (HorizontalDirectionalBlock + EntityBlock) + SignpostBlockEntity with BlockEntityType registration via DeferredRegister |
| PTR-02 | Squire patrols between signpost waypoints in defined order | PatrolHandler using PathNavigation.moveTo(BlockPos) + linked-list route built from SignpostBlockEntity.getLinkedSignpost() chain |
| PTR-03 | Player can define and modify patrol routes | Crest-item linking gesture in SignpostBlock.useWithoutItem() — click first signpost, click second to link |
| MNT-01 | Squire mounts and rides horses | MountHandler.tickApproach() — navigate to horse, call squire.startRiding(horse, true) |
| MNT-02 | Squire follows player while mounted | MountHandler.tickMountedFollow() — horse.move(MoverType.SELF, vec) direct position push, bypassing vanilla travel() |
| MNT-03 | Squire engages in combat while mounted | MountHandler.tickMountedCombat() — delegates to CombatHandler.tick() while using horse.move() for approach; squire attacks from mounted position |
| MNT-04 | Mount state persists across sessions (horse UUID in NBT) | SquireEntity NBT: save/load horseUUID; MountHandler.setHorseUUID() on startup auto-mount attempt |

</phase_requirements>

---

## Standard Stack

### Core

| Library | Version | Purpose | Why Standard |
| --- | --- | --- | --- |
| NeoForge | 21.1.221 | BlockEntityType DeferredRegister, HorizontalDirectionalBlock, PathNavigation | Already in project build.gradle |
| NeoForge | 21.1.221 | AbstractHorse (vanilla entity) | Target entity for mounting — no extra dep |
| NeoForge | 21.1.221 | MoverType.SELF via Entity.move() | The only working NPC horse driving mechanism |

### Supporting

| Library | Version | Purpose | When to Use |
| --- | --- | --- | --- |
| NbtUtils | MC 1.21.1 | BlockPos NBT serialization | SignpostBlockEntity.saveAdditional/loadAdditional |
| PathNavigation | MC 1.21.1 | Mob pathfinding to BlockPos waypoints | PatrolHandler.tickWalk() |
| ClientboundBlockEntityDataPacket | MC 1.21.1 | Client sync for SignpostBlockEntity | getUpdatePacket() |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
| --- | --- | --- |
| horse.move(MoverType.SELF) | PathNavigation on horse | Not viable — horse navigation is overridden when a passenger is present; PathNavigation.moveTo() on the horse has no effect |
| horse.move(MoverType.SELF) | Setting horse.zza/xxa each tick | Cannot override — `travelRidden()` only fires for Player passengers; non-player rider sees vanilla travel() with zero input |
| Linked-list signpost chain | Storing route array in SquireEntity NBT | Linked-list keeps route data in the world (signposts), not in entity NBT; players can edit routes by relinking posts without restarting patrol |

**Installation:** No new dependencies required for Phase 7. All classes are vanilla MC / NeoForge 21.1.221, already declared in build.gradle.

---

## Architecture Patterns

### Recommended Project Structure

Phase 7 adds these files (within the existing v2 structure):

```
src/main/java/com/sjviklabs/squire/
├── block/
│   ├── SignpostBlock.java         # HorizontalDirectionalBlock + EntityBlock
│   └── SignpostBlockEntity.java   # BlockEntity: mode, linkedSignpost, waitTicks, assignedOwner
├── brain/handler/
│   ├── PatrolHandler.java         # PTR-02: route walk/wait FSM
│   └── MountHandler.java          # MNT-01/02/03/04: approach, mounted-follow, mounted-combat

src/main/resources/
├── assets/squire/
│   ├── blockstates/signpost.json
│   ├── models/block/signpost.json
│   └── models/item/signpost.json
└── data/squire/
    └── loot_table/blocks/signpost.json    # drops signpost item on break
```

SquireRegistry gets two new DeferredRegister entries: `SIGNPOST_BLOCK`, `SIGNPOST_BLOCK_ENTITY`.

### Pattern 1: BlockEntityType Registration (NeoForge 1.21.1)

**What:** `DeferredRegister<BlockEntityType<?>>` in SquireRegistry. BlockEntityType registered using constructor reference and the associated block(s).

**When to use:** Required — SignpostBlockEntity must be registered before world load.

**Example:**
```java
// Source: docs.neoforged.net/docs/blockentities/
// In SquireRegistry
public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
    DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, SquireMod.MOD_ID);

public static final Supplier<BlockEntityType<SignpostBlockEntity>> SIGNPOST_BLOCK_ENTITY =
    BLOCK_ENTITY_TYPES.register("signpost",
        () -> new BlockEntityType<>(
            SignpostBlockEntity::new,
            false,
            SIGNPOST_BLOCK.get()
        ));
```

Registration order matters: `BLOCK_ENTITY_TYPES.register(modBus)` must come after `BLOCKS.register(modBus)` in `SquireMod` constructor.

### Pattern 2: BlockEntity NBT with NbtUtils (1.21.1)

**What:** `NbtUtils.writeBlockPos(pos)` returns a `Tag` (store with `tag.put(key, ...)`). `NbtUtils.readBlockPos(tag, key)` returns `Optional<BlockPos>`.

**When to use:** SignpostBlockEntity.saveAdditional/loadAdditional for `linkedSignpost` field.

**Example:**
```java
// Source: MC 1.21.1 decompiled NbtUtils.java (verified in project Gradle cache)
// NbtUtils.readBlockPos signature: Optional<BlockPos> readBlockPos(CompoundTag, String)
// NbtUtils.writeBlockPos signature: Tag writeBlockPos(BlockPos)

// Save:
if (linkedSignpost != null) {
    tag.put("LinkedSignpost", NbtUtils.writeBlockPos(linkedSignpost));
}

// Load:
NbtUtils.readBlockPos(tag, "LinkedSignpost")
    .ifPresent(pos -> this.linkedSignpost = pos);
```

This is identical to the v0.5.0 implementation — no changes needed.

### Pattern 3: BlockEntity Client Sync

**What:** `getUpdateTag()` + `getUpdatePacket()` for server-to-client sync on block entity state changes.

**When to use:** Any time signpost data changes (linking, mode change) — client needs to know for rendering.

**Example:**
```java
// Source: docs.neoforged.net/docs/blockentities/
@Override
public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
    CompoundTag tag = new CompoundTag();
    saveAdditional(tag, registries);
    return tag;
}

@Override
public Packet<ClientGamePacketListener> getUpdatePacket() {
    return ClientboundBlockEntityDataPacket.create(this);
}
```

After modifying signpost state server-side, call `level.sendBlockUpdated(pos, state, state, 3)` to push the update.

### Pattern 4: Patrol Route — Linked-List Traversal

**What:** Route is stored as a linked list of SignpostBlockEntity nodes. PatrolHandler builds a flat `List<BlockPos>` at patrol start by following `.getLinkedSignpost()` until null or a loop is detected.

**When to use:** PatrolHandler.startPatrol() and whenever the player triggers a new route.

**Example:**
```java
// Source: v0.5.0 PatrolHandler.java (verified)
public static List<BlockPos> buildRouteFromSignpost(Level level, BlockPos start) {
    List<BlockPos> waypoints = new ArrayList<>();
    Set<BlockPos> visited = new HashSet<>();
    BlockPos current = start;
    int maxRoute = SquireConfig.patrolMaxRouteLength.get();

    while (current != null && waypoints.size() < maxRoute) {
        if (visited.contains(current)) break; // loop detected — route complete
        visited.add(current);
        BlockEntity be = level.getBlockEntity(current);
        if (be instanceof SignpostBlockEntity signpost) {
            waypoints.add(current);
            current = signpost.getLinkedSignpost();
        } else {
            break;
        }
    }
    return waypoints;
}
```

### Pattern 5: MoverType.SELF Horse Driving (CRITICAL)

**What:** The only working mechanism for an NPC to drive a horse. `horse.move(MoverType.SELF, vec)` directly changes the horse's position with collision detection, bypassing both `travel()` and the horse's goal system.

**Why this is necessary (verified in MC 1.21.1 source):** `LivingEntity`'s per-tick movement loop (line ~2816 in LivingEntity.java) gates `travelRidden()` on `getControllingPassenger() instanceof Player`. `AbstractHorse.getControllingPassenger()` only returns a Player (hardcoded `instanceof Player` check at line 1081). When the squire is the passenger, the horse receives no travel input and stands still. Setting `horse.zza` or using `PathNavigation` on the horse have no effect because vanilla movement completely bypasses them in this case.

**Example:**
```java
// Source: v0.5.0 MountHandler.java (proven working) + MC 1.21.1 LivingEntity.java:2816 (root cause verified)
private void driveHorseToward(SquireEntity s, AbstractHorse horse,
                               double targetX, double targetY, double targetZ,
                               float speedMult) {
    double dx = targetX - horse.getX();
    double dz = targetZ - horse.getZ();
    double dist = Math.sqrt(dx * dx + dz * dz);
    if (dist < 1.5) return;

    // Smooth heading turn (max 12 degrees/tick)
    float targetYRot = (float)(Math.atan2(-dx, dz) * (180.0 / Math.PI));
    float diff = targetYRot - horse.getYRot();
    while (diff > 180.0F) diff -= 360.0F;
    while (diff < -180.0F) diff += 360.0F;
    float smoothed = horse.getYRot() + Math.max(-12.0F, Math.min(12.0F, diff));
    horse.setYRot(smoothed);
    horse.yBodyRot = smoothed;
    horse.yHeadRot = smoothed;
    s.setYRot(smoothed);
    s.yBodyRot = smoothed;

    // Speed: horse attribute * multiplier
    double horseSpeed = horse.getAttributeValue(Attributes.MOVEMENT_SPEED);
    double moveSpeed = horseSpeed * speedMult * 3.5;
    Vec3 movement = new Vec3((dx / dist) * moveSpeed, 0, (dz / dist) * moveSpeed);

    // Direct position change with collision
    horse.move(MoverType.SELF, movement);

    // Step-up: hop one block if hit a wall while on ground
    if (horse.horizontalCollision && horse.onGround()) {
        horse.move(MoverType.SELF, new Vec3(0, 0.6, 0));
        horse.move(MoverType.SELF, movement);
    }

    // Gravity if airborne
    if (!horse.onGround()) {
        horse.move(MoverType.SELF, new Vec3(0, -0.08, 0));
    }

    horse.hasImpulse = true;
    horse.walkAnimation.setSpeed(Math.min((float)moveSpeed * 8.0F, 1.5F));
}
```

### Pattern 6: Horse UUID Persistence

**What:** Store `horseUUID` in `SquireEntity`'s NBT save/load. On world load, MountHandler checks for the UUID and auto-mounts if the horse is nearby.

**When to use:** MNT-04. Horse assignment must survive server restart.

**Example:**
```java
// In SquireEntity.addAdditionalSaveData:
if (brain != null && brain.getMount().getHorseUUID() != null) {
    tag.putUUID("HorseUUID", brain.getMount().getHorseUUID());
}

// In SquireEntity.readAdditionalSaveData:
if (tag.hasUUID("HorseUUID")) {
    // Store temporarily; MountHandler is instantiated after readAdditionalSaveData
    this.pendingHorseUUID = tag.getUUID("HorseUUID");
}

// In SquireBrain constructor (or first tick after load):
if (squire.pendingHorseUUID != null) {
    mount.setHorseUUID(squire.pendingHorseUUID);
    squire.pendingHorseUUID = null;
}
```

### Pattern 7: FSM States for Phase 7

The v0.5.0 state machine used these states for patrol and mounting. Phase 2 defines `SquireAIState` — these states must be present:

```
// Patrol states (PatrolHandler)
PATROL_WALK       // walking toward next waypoint
PATROL_WAIT       // arrived at waypoint, waiting waitTicks

// Mounting states (MountHandler)
MOUNTING          // approaching horse to mount
MOUNTED_IDLE      // on horse, no movement target
MOUNTED_FOLLOW    // on horse, following player
MOUNTED_COMBAT    // on horse, engaging target
```

Priority placement in the FSM tick-rate layers (from ARCHITECTURE.md):
- `PATROL_WALK` / `PATROL_WAIT`: priority 30-39 (same band as FOLLOW)
- `MOUNTING` / `MOUNTED_*`: priority 20-29 (same band as eating / mount acquisition)
- Combat preemption: a global CombatEnterTransition at priority 10 must still interrupt PATROL_WALK and MOUNTED_FOLLOW

### Anti-Patterns to Avoid

- **Setting horse.zza / horse.xxa from the squire:** Ignored — vanilla movement sets these from the controlling Player's input, not from external code during a tick where the horse has a non-Player passenger.
- **PathNavigation.moveTo() on the horse entity:** Calling `horse.getNavigation().moveTo(...)` has no effect when the horse is a vehicle. The horse's own goals are suppressed by the presence of a passenger (MC-262182 behavior), and navigation overrides are ignored.
- **Calling horse.move() without gravity:** The horse floats over gaps. Always apply `new Vec3(0, -0.08, 0)` when `!horse.onGround()`.
- **Storing the full waypoint list in SquireEntity NBT:** Makes route editing require despawning/resummoning. Keep route data in the signposts; PatrolHandler rebuilds the list at patrol start.
- **Using `tag.put("LinkedSignpost", NbtUtils.writeBlockPos(pos))` without wrapping:** This is correct usage — `writeBlockPos` returns a `Tag` (IntArrayTag), stored with `tag.put(key, tag)`.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
| --- | --- | --- | --- |
| Horse heading / turning | Custom angle interpolation | Proven formula from v0.5.0 MountHandler | Edge cases: wrap-around at ±180, max turn rate smoothing, yBodyRot + yHeadRot both need updating |
| Patrol loop detection | Array indexOf() | visited HashSet in buildRouteFromSignpost | O(1) per node; handles cycles correctly; indexOf is O(n) and misses if signpost is reused |
| Stuck detection in patrol | Custom movement delta tracking | PathNavigation.isStuck() (confirmed in NeoForge javadoc) | Built-in; fires after configurable timeout; no manual last-position tracking needed |
| BlockPos NBT round-trip | Manual getX/getY/getZ tag.putInt | NbtUtils.writeBlockPos / readBlockPos | Returns Optional; handles absent key; correct IntArrayTag format expected by vanilla world loading |

**Key insight:** The MoverType.SELF horse driving code in v0.5.0 took experimentation to get right (documented in the method comment). It handles: wall collision step-up, gravity while airborne, smooth turning, walk animation sync. Don't rebuild from scratch — port it.

---

## Common Pitfalls

### Pitfall 1: Horse Sits Still After Mounting

**What goes wrong:** Squire mounts successfully (`squire.startRiding(horse, true)` returns true), but the horse doesn't move.

**Why it happens:** `LivingEntity.aiStep()` gates `travelRidden()` on `getControllingPassenger() instanceof Player`. `AbstractHorse.getControllingPassenger()` hardcodes `instanceof Player` at line 1081 of AbstractHorse.java (verified in MC 1.21.1 decompiled source). The squire is not a Player, so the horse gets `travel(zeroVector)` and stays still.

**How to avoid:** Use `horse.move(MoverType.SELF, vec)` in MountHandler's tick methods. Never call `horse.getNavigation().moveTo()` or set `horse.zza`.

**Warning signs:** Horse mounts but stops immediately; squire is shown as a passenger but horse makes no movement sounds.

### Pitfall 2: PatrolHandler Infinite Loop on Circular Route

**What goes wrong:** `buildRouteFromSignpost()` hangs (or builds an infinite list) when signposts form a complete loop (A→B→C→A).

**Why it happens:** Loop detection requires tracking visited positions. A simple `while (next != null)` without a visited set will traverse the loop indefinitely.

**How to avoid:** The `Set<BlockPos> visited` HashSet in the v0.5.0 implementation handles this. When `visited.contains(current)` is true, break out — the route is complete. The resulting route includes all nodes before the repeated node, forming a valid loop.

**Warning signs:** Server hangs or OutOfMemoryError on world with a looped patrol route.

### Pitfall 3: BlockEntityType Registration Order

**What goes wrong:** NullPointerException or "No such block entity" during world load.

**Why it happens:** `BLOCK_ENTITY_TYPES.register(modBus)` called after world load events, or `SIGNPOST_BLOCK.get()` called before DeferredHolder resolves.

**How to avoid:** All DeferredRegister instances must call `.register(modBus)` in SquireMod's constructor, in order: BLOCKS first, then BLOCK_ENTITY_TYPES (because BlockEntityType constructor takes the Block instance as a parameter — the block must be registered first).

**Warning signs:** "Registry object not present" exception at startup; BlockEntity not found in saved world after restart.

### Pitfall 4: Horse UUID Lost on Restart

**What goes wrong:** Squire loses its assigned horse after server restart.

**Why it happens:** `MountHandler.horseUUID` is handler-local state, not persisted in entity NBT. If SquireEntity.readAdditionalSaveData doesn't load it, or if SquireBrain instantiates MountHandler before the UUID is injected, it defaults to null.

**How to avoid:** Load `horseUUID` from NBT in `readAdditionalSaveData`, store temporarily on SquireEntity (e.g., `pendingHorseUUID`), inject into MountHandler in SquireBrain constructor or on first tick. Verify in unit test: NBT round-trip of horseUUID produces same UUID after load.

**Warning signs:** `MNT-04` GameTest fails; horse UUID is not found in saved CompoundTag.

### Pitfall 5: Gravity Missing During Mounted Follow

**What goes wrong:** Horse (and squire rider) float horizontally over voids instead of falling.

**Why it happens:** `horse.move(MoverType.SELF, horizontalVec)` doesn't apply gravity. Normally vanilla `travel()` applies gravity each tick; since we bypass it, gravity must be explicit.

**How to avoid:** Always include the `if (!horse.onGround()) horse.move(MoverType.SELF, new Vec3(0, -0.08, 0))` check in `driveHorseToward()`.

**Warning signs:** Horse drives off a cliff and continues horizontally; squire rides horse over a hole and doesn't fall.

### Pitfall 6: MBoundBox Inflation for Horse Search

**What goes wrong:** `findAssignedHorse()` returns null even though the horse is nearby, because the search range is too tight or the UUID search scans the wrong entity list.

**Why it happens:** AABB search via `level.getEntitiesOfClass(AbstractHorse.class, searchBox, predicate)` requires an inflated bounding box. If `searchBox` is created from `squire.getBoundingBox()` without `.inflate(range)`, the search area is the squire's own body (1x2 blocks), not the configured range.

**How to avoid:** `AABB searchBox = squire.getBoundingBox().inflate(SquireConfig.horseSearchRange.get())`. Default range of 32 blocks is sufficient for typical player-horse proximity.

---

## Code Examples

Verified patterns from v0.5.0 source and MC 1.21.1 decompiled:

### SignpostBlockEntity — Full NBT Pattern

```java
// Source: v0.5.0 SignpostBlockEntity.java (verified compiles against NeoForge 21.1.x)
@Override
protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
    super.saveAdditional(tag, registries);
    tag.putString("Mode", mode.name());
    tag.putInt("WaitTicks", waitTicks);
    if (assignedOwner != null) tag.putUUID("AssignedOwner", assignedOwner);
    if (linkedSignpost != null) tag.put("LinkedSignpost", NbtUtils.writeBlockPos(linkedSignpost));
}

@Override
protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
    super.loadAdditional(tag, registries);
    if (tag.contains("Mode")) {
        try { mode = PatrolMode.valueOf(tag.getString("Mode")); }
        catch (IllegalArgumentException e) { mode = PatrolMode.WAYPOINT; }
    }
    if (tag.contains("WaitTicks")) waitTicks = tag.getInt("WaitTicks");
    if (tag.hasUUID("AssignedOwner")) assignedOwner = tag.getUUID("AssignedOwner");
    NbtUtils.readBlockPos(tag, "LinkedSignpost")
        .ifPresent(pos -> this.linkedSignpost = pos);
}
```

### PatrolHandler — Walk/Wait Tick Loop

```java
// Source: v0.5.0 PatrolHandler.java (verified)
public SquireAIState tickWalk(SquireEntity s) {
    if (!patrolling || route == null || route.isEmpty()) {
        patrolling = false;
        return SquireAIState.IDLE;
    }
    BlockPos target = route.get(currentIndex);
    double distSq = s.distanceToSqr(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5);
    if (distSq <= 4.0) {                    // within 2 blocks: arrive
        waitTimer = SquireConfig.patrolDefaultWait.get();
        return SquireAIState.PATROL_WAIT;
    }
    s.getNavigation().moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, 0.8);
    return SquireAIState.PATROL_WALK;
}
```

### MountHandler — Mounted Follow

```java
// Source: v0.5.0 MountHandler.java (verified)
public SquireAIState tickMountedFollow(SquireEntity s) {
    if (!isMounted()) return SquireAIState.IDLE;
    Player owner = s.getOwner() instanceof Player p ? p : null;
    if (owner == null) return SquireAIState.MOUNTED_IDLE;
    AbstractHorse horse = (AbstractHorse) s.getVehicle();
    double distSq = s.distanceToSqr(owner);
    double stopDist = SquireConfig.followStopDistance.get();
    double startDist = SquireConfig.followStartDistance.get();
    if (distSq < stopDist * stopDist) return SquireAIState.MOUNTED_IDLE;
    float speedMult = distSq > startDist * startDist * 4 ? 1.0F : 0.5F;
    driveHorseToward(s, horse, owner.getX(), owner.getY(), owner.getZ(), speedMult);
    if (!horse.hasEffect(MobEffects.MOVEMENT_SPEED)) {
        horse.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 600, 0, false, false));
    }
    return SquireAIState.MOUNTED_FOLLOW;
}
```

### Crest-Based Signpost Linking

```java
// Source: v0.5.0 SignpostBlock.java (verified)
// Static map: player UUID → pending BlockPos (first signpost clicked)
private static final Map<UUID, BlockPos> PENDING_LINKS = new HashMap<>();

private InteractionResult handleCrestLink(Player player, BlockPos pos, SignpostBlockEntity signpost) {
    UUID playerId = player.getUUID();
    BlockPos pending = PENDING_LINKS.get(playerId);
    if (pending == null || pending.equals(pos)) {
        PENDING_LINKS.put(playerId, pos);
        player.sendSystemMessage(Component.literal("Signpost selected. Right-click another with crest to link."));
        return InteractionResult.CONSUME;
    }
    BlockEntity fromBE = player.level().getBlockEntity(pending);
    if (fromBE instanceof SignpostBlockEntity fromSignpost) {
        fromSignpost.setLinkedSignpost(pos);
        fromSignpost.setChanged();
        // Also trigger client update:
        player.level().sendBlockUpdated(pending, player.level().getBlockState(pending),
                                         player.level().getBlockState(pending), 3);
        player.sendSystemMessage(Component.literal("Linked: " + pending.toShortString() + " → " + pos.toShortString()));
    }
    PENDING_LINKS.remove(playerId);
    return InteractionResult.CONSUME;
}
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
| --- | --- | --- | --- |
| Horse navigation for NPC rider | horse.move(MoverType.SELF) direct push | MC 1.19.4+ (getControllingPassenger instanceof Player gating) | Navigation-based approaches silently fail; must use direct move |
| NbtUtils.readBlockPos(tag, key) returning BlockPos | Returns Optional<BlockPos> | MC 1.20.5 | .ifPresent() pattern required; NPE risk on absent key if not handled |
| NbtUtils.writeBlockPos(pos) returning CompoundTag | Returns Tag (IntArrayTag) | MC 1.20.5 | Use tag.put(key, NbtUtils.writeBlockPos(pos)) not tag.putIntArray |
| BlockEntityType.Builder.of() | new BlockEntityType(constructor, false, blocks...) | NeoForge 21.1.x | Builder pattern removed; use constructor directly |

**Deprecated/outdated:**

- `NbtUtils.readBlockPos(CompoundTag)` (no key parameter): Removed in 1.20.5 — takes (CompoundTag, String) in 1.21.1.
- `NbtUtils.writeBlockPos` returns a `Tag`, not a `CompoundTag` — don't try to access it as a compound; just `tag.put(key, writeBlockPos(pos))`.
- `NbtUtils.writeBlockPos` / `readBlockPos` are removed entirely in MC 1.21.5 — but that's a future version and out of scope for 1.21.1.

---

## Open Questions

1. **v0.5.0 SignpostBlock uses a static `PENDING_LINKS` map — is this safe in a multi-player server?**
   - What we know: static HashMap is shared across all server instances in the same JVM; it works for a single server but could leak entries if a player disconnects mid-link without completing.
   - What's unclear: Whether to add a `PlayerLoggedOutEvent` cleanup, or just let stale entries sit (they're cheap and expire on re-use).
   - Recommendation: Add a `@SubscribeEvent PlayerEvent.PlayerLoggedOutEvent` handler that removes the player's UUID from `PENDING_LINKS`. Low cost, eliminates the leak. Add to SquireRegistry or a server event class.

2. **Patrol "resume after combat" state transition: which state does the squire return to?**
   - What we know: PatrolHandler has `startPatrol(route)` which resets currentIndex. After combat ends, the FSM needs to transition back to PATROL_WALK or PATROL_WAIT depending on where the squire is.
   - What's unclear: Whether to resume from the last waypoint reached (currentIndex preserved) or restart from index 0. Preserving currentIndex is more natural.
   - Recommendation: When CombatHandler fires COMBAT_END event, PatrolHandler's listener checks `patrolling == true` and returns currentIndex to its value at combat start. PatrolHandler should save `savedIndex` on COMBAT_START and restore on COMBAT_END.

3. **Does the squire need to dismount before entering patrol?**
   - What we know: PATROL_WALK uses `s.getNavigation().moveTo()` which works on foot. If the squire is mounted, navigation calls go to the squire entity but movement is controlled through the horse.
   - What's unclear: Can patrol work while mounted, or should MountHandler.orderDismount() be called when patrol starts?
   - Recommendation: Patrol is a foot-only behavior. When PATROL_WALK is entered, if `mount.isMounted()` is true, call `mount.orderDismount()` first. This avoids conflicting movement (navigation on squire while horse is driven separately).

---

## Validation Architecture

### Test Framework

| Property | Value |
| --- | --- |
| Framework | JUnit 5 (5.10.2) + NeoForge GameTest |
| Config file | build.gradle (unitTest { enable() } — already configured) |
| Quick run command | `./gradlew test` |
| Full suite command | `./gradlew test runGameTestServer` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
| --- | --- | --- | --- | --- |
| PTR-01 | SignpostBlockEntity NBT round-trip (mode, linkedSignpost, waitTicks, owner) | unit | `./gradlew test --tests "*.SignpostBlockEntityTest"` | Wave 0 |
| PTR-02 | buildRouteFromSignpost() — linear, looped, empty, broken chain | unit | `./gradlew test --tests "*.PatrolHandlerTest"` | Wave 0 |
| PTR-03 | Signpost linking gesture: two-click flow, same-click no-op, stale entry cleanup | unit | `./gradlew test --tests "*.SignpostBlockEntityTest"` | Wave 0 |
| MNT-01 | Horse approach: squire navigation starts, squire.startRiding() fires on proximity | unit | `./gradlew test --tests "*.MountHandlerTest"` | Wave 0 |
| MNT-02 | driveHorseToward: heading math, wall step-up path, gravity application | unit | `./gradlew test --tests "*.MountHandlerTest"` | Wave 0 |
| MNT-03 | Mounted combat: target engagement delegates to CombatHandler while mounted | unit | `./gradlew test --tests "*.MountHandlerTest"` | Wave 0 |
| MNT-04 | Horse UUID NBT round-trip: save + load produces same UUID | unit | `./gradlew test --tests "*.MountHandlerTest"` | Wave 0 |

Note: `driveHorseToward` and `buildRouteFromSignpost` are pure math/logic — unit testable without a live Minecraft world. GameTests are not required for Phase 7 unless the planner identifies in-world behavior that cannot be unit-tested.

### Sampling Rate

- **Per task commit:** `./gradlew test`
- **Per wave merge:** `./gradlew test runGameTestServer`
- **Phase gate:** Full suite green before `/gsd:verify-work`

### Wave 0 Gaps

- [ ] `src/test/java/com/sjviklabs/squire/block/SignpostBlockEntityTest.java` — covers PTR-01, PTR-03
- [ ] `src/test/java/com/sjviklabs/squire/brain/handler/PatrolHandlerTest.java` — covers PTR-02 (route building logic)
- [ ] `src/test/java/com/sjviklabs/squire/brain/handler/MountHandlerTest.java` — covers MNT-01, MNT-02, MNT-03, MNT-04

---

## Sources

### Primary (HIGH confidence)

- MC 1.21.1 decompiled `AbstractHorse.java` — verified in project Gradle cache at `~/.gradle/caches/ng_execute/.../transformed/net/minecraft/world/entity/animal/horse/AbstractHorse.java` — `getControllingPassenger()` instanceof Player gating confirmed
- MC 1.21.1 decompiled `LivingEntity.java` — `travelRidden()` gates on `getControllingPassenger() instanceof Player` at line 2816 — root cause of NPC horse standing still confirmed
- MC 1.21.1 decompiled `NbtUtils.java` — `readBlockPos(CompoundTag, String)` returns `Optional<BlockPos>`, `writeBlockPos(BlockPos)` returns `Tag` (IntArrayTag) — confirmed
- v0.5.0 `SignpostBlock.java`, `SignpostBlockEntity.java`, `PatrolHandler.java`, `MountHandler.java` — read directly from `C:/Users/Steve/Projects/squire-mod/src/`
- [NeoForge Block Entities docs](https://docs.neoforged.net/docs/blockentities/) — BlockEntityType registration pattern, getUpdateTag/getUpdatePacket
- [PathNavigation NeoForge javadoc](https://nekoyue.github.io/ForgeJavaDocs-NG/javadoc/1.20.6-neoforge/net/minecraft/world/entity/ai/navigation/PathNavigation.html) — isDone(), isInProgress(), isStuck(), stop() methods confirmed

### Secondary (MEDIUM confidence)

- v0.5.0 `MountHandler` comments document the NPC rider problem and MoverType.SELF solution explicitly — matches MC source root cause
- [NeoForge 1.21 migration primer](https://docs.neoforged.net/primer/docs/1.21/) — no breaking changes to BlockEntity, AbstractHorse, or HorizontalDirectionalBlock in this migration

### Tertiary (LOW confidence)

- [PaperMC issue #12467](https://github.com/PaperMC/Paper/issues/12467) — corroborates that "ANY mob with passengers doesn't do ANYTHING" since 1.19.4 (Bukkit/Paper context, not NeoForge, but the underlying Minecraft behavior is the same)

---

## Metadata

**Confidence breakdown:**

- Standard stack: HIGH — all classes are vanilla MC / NeoForge; no new deps
- Architecture (patrol): HIGH — v0.5.0 source + NeoForge BlockEntity docs verified; NbtUtils signatures confirmed from decompiled source
- Architecture (mounting): HIGH — root cause confirmed from MC 1.21.1 decompiled source; solution confirmed from v0.5.0 which was working in 1.21.1
- Pitfalls: HIGH — mounted horse standing still confirmed from source; others derived from code analysis and v0.5.0 working experience

**Research date:** 2026-04-02
**Valid until:** 2026-10-01 (stable NeoForge 1.21.1 target; no planned version change until Phase 8 is complete)

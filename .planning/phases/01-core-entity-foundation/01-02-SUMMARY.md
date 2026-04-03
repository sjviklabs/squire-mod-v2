---
phase: 01-core-entity-foundation
plan: 02
subsystem: entity-identity
tags: [entity, nbt, attachment, item, pathfindermob, neoforge]
dependency_graph:
  requires: [01-01]
  provides: [SquireEntity, SquireTier, SquireDataAttachment, SquireCrestItem, SquireBrain-stub]
  affects: [01-03, 01-04, 01-05, all-future-phases]
tech_stack:
  added: []
  patterns:
    - PathfinderMob base class (not TamableAnimal) — custom UUID owner tracking, ~80 lines
    - SynchedEntityData with SquireEntity.class as first arg (not superclass)
    - AttachmentType with RecordCodecBuilder — cross-death persistence via .serialize(CODEC)
    - Lazy SquireBrain init in aiStep() — avoids constructor-ordering conflict with registerGoals()
    - One-per-player enforcement via getAllLevels() iteration
key_files:
  created:
    - src/main/java/com/sjviklabs/squire/entity/SquireTier.java
    - src/main/java/com/sjviklabs/squire/entity/SquireEntity.java
    - src/main/java/com/sjviklabs/squire/entity/SquireDataAttachment.java
    - src/main/java/com/sjviklabs/squire/brain/SquireBrain.java
    - src/main/java/com/sjviklabs/squire/item/SquireCrestItem.java
  modified:
    - src/main/java/com/sjviklabs/squire/SquireRegistry.java
decisions:
  - "tryToTeleportToOwner is not present on PathfinderMob — declared without @Override for doc clarity only"
  - "isFood() also absent from PathfinderMob — omitted entirely (squires cannot be bred)"
  - "SquireCrestItem written during Task 1 to unblock SquireRegistry compilation (referenced as DeferredHolder<Item, SquireCrestItem>)"
  - "itemHandler declared as Object in SquireEntity to avoid forward-reference compile errors; Plan 01-03 casts and initializes"
metrics:
  duration_seconds: 871
  completed_date: "2026-04-02"
  tasks_completed: 2
  files_created: 5
  files_modified: 1
---

# Phase 1 Plan 2: Entity Identity Layer Summary

**One-liner:** PathfinderMob-based SquireEntity with UUID owner tracking, 3 SynchedEntityData fields, full NBT persistence, cross-death SquireData attachment (Codec-backed), and Crest summon/recall item with one-per-player enforcement across all server dimensions.

## What Was Built

### Task 1: Entity Identity Layer

**SquireTier.java** — Enum with 5 tiers (SERVANT/APPRENTICE/SQUIRE/KNIGHT/CHAMPION), minLevel thresholds (0/5/10/20/30), and backpackSlots counts (9/18/27/32/36). `fromLevel(int)` returns the highest qualifying tier.

**SquireEntity.java** — PathfinderMob subclass with:
- 3 SynchedEntityData fields (SQUIRE_MODE byte, SQUIRE_LEVEL int, SLIM_MODEL boolean) — all keyed with `SquireEntity.class` as required
- UUID-based owner tracking (`ownerUUID` field, `isOwnedBy(Player)`, `setOwnerId(UUID)`)
- Full NBT round-trip: OwnerUUID, SquireMode, SquireLevel, SlimModel, TotalXP, SquireName
- Inventory hook: null-safe `itemHandler` Object field — Plan 01-03 initializes it
- `die()`: writes XP/level/name/slim to player attachment, drops 6 equipment slots as ItemEntity, sends death coordinates chat, calls super
- `removeWhenFarAway()` returns false; `canBeLeashed()` returns false
- Lazy SquireBrain init in `aiStep()` to avoid constructor-ordering issues
- FloatGoal (priority 0) and OpenDoorGoal (priority 1) — no follow goals until Phase 2

**SquireDataAttachment.java** — SquireData record with `RecordCodecBuilder` CODEC. UUID serialized as `Optional<String>` with `xmap`. Builder methods: `withXP`, `withName`, `withAppearance`, `withSquireUUID`, `clearSquireUUID`. No `DeferredRegister` — registration lives in SquireRegistry via `buildAttachmentType()` factory.

**SquireBrain.java** — Minimal stub with `tick()` no-op. Phase 2 populates the FSM.

**SquireRegistry.java** (updated) — Added:
- `SQUIRE` DeferredHolder (EntityType, MobCategory.MISC, sized 0.6×1.8)
- `SQUIRE_DATA` Supplier<AttachmentType> with `.serialize(SquireData.CODEC)` — critical for world-reload persistence
- `CREST` DeferredHolder (SquireCrestItem, stacksTo(1))
- `modEventBus.register(SquireRegistry.class)` for `@SubscribeEvent` handlers
- `registerAttributes()` `@SubscribeEvent` for `EntityAttributeCreationEvent`

### Task 2: Crest Summon/Recall

**SquireCrestItem.java** — Single `use()` method handles both paths:

**Recall:** `findPlayerSquire()` iterates `getAllLevels()` → on hit, writes current XP/level to attachment with `clearSquireUUID()` → calls `squire.discard()` → sends chat message.

**Summon:** `countPlayerSquires()` iterates `getAllLevels()` → rejects if ≥ 1 → spawns via `player.pick(4.0, ...)` crosshair location → restores identity from SquireData → `level.addFreshEntity()` → SOUL particles.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] tryToTeleportToOwner and isFood removed as @Override**

- **Found during:** Task 1 compile (`./gradlew compileJava` — 2 override errors)
- **Issue:** Both methods are defined on `TamableAnimal`, not `PathfinderMob`. Using `@Override` caused "method does not override or implement a method from a supertype" compiler errors.
- **Fix:** `tryToTeleportToOwner()` kept without `@Override` (documented no-op for clarity). `isFood()` removed entirely — PathfinderMob has no breeding system, and the method is irrelevant.
- **Files modified:** `SquireEntity.java`

**2. [Rule 3 - Blocking] SquireCrestItem written during Task 1 to unblock SquireRegistry compilation**

- **Found during:** Task 1 compile — SquireRegistry references `SquireCrestItem` as a type argument in `DeferredHolder<Item, SquireCrestItem>`, which required the class to exist.
- **Fix:** SquireCrestItem was written during Task 1 execution (before Task 2's formal start). Functionally all Task 2 code was still committed separately under Task 2's commit.
- **Files modified:** `SquireCrestItem.java` (created early)

## Verification

| Check | Result |
|-------|--------|
| `./gradlew compileJava` exits 0 | PASS |
| `extends PathfinderMob` — 1 match | PASS |
| `defineId(SquireEntity.class,` — exactly 3 | PASS |
| `.serialize(` in SquireDataAttachment — 1 match | PASS |
| `DeferredRegister.create(` in SquireDataAttachment — 0 matches | PASS |
| `.discard()` in SquireCrestItem — 1 match | PASS |
| `getAllLevels()` in SquireCrestItem — 2 matches | PASS |
| `isOwnedBy(player)` in SquireCrestItem | PASS |
| `ParticleTypes.SOUL` in SquireCrestItem | PASS |

## Commits

| Task | Hash | Description |
|------|------|-------------|
| Task 1 | 6a20937 | feat(01-02): entity identity layer — SquireEntity, SquireTier, SquireDataAttachment, SquireBrain stub |
| Task 2 | fbdc8bb | feat(01-02): SquireCrestItem — summon/recall with one-per-player enforcement |

## Self-Check: PASSED

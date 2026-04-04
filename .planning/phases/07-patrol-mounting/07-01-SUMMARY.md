---
phase: 07-patrol-mounting
plan: 01
subsystem: blocks
tags: [signpost, block-entity, nbt, patrol, neoforge-1.21.1, junit5]

requires:
  - phase: 01-core-entity-foundation
    provides: SquireRegistry DeferredRegister pattern, SquireMod registration entry point
  - phase: 04-combat-progression
    provides: SquireCrestItem (CREST holder used in SignpostBlock linking gesture)

provides:
  - SignpostBlock (HorizontalDirectionalBlock + EntityBlock) with Crest two-click linking gesture
  - SignpostBlockEntity with mode/linkedSignpost/waitTicks/assignedOwner NBT persistence
  - BLOCKS + BLOCK_ENTITY_TYPES DeferredRegisters in SquireRegistry
  - SIGNPOST_BLOCK, SIGNPOST_BLOCK_ENTITY, SIGNPOST_ITEM holders
  - Static writeTag/readTag helpers on SignpostBlockEntity for headless unit testing
  - Three Phase 7 test scaffold files (SignpostBlockEntityTest, PatrolHandlerTest, MountHandlerTest)
  - blockstates/signpost.json, models/block/signpost.json, models/item/signpost.json, loot_table

affects:
  - 07-patrol-mounting (Wave 2 — PatrolHandler, MountHandler build against these classes)

tech-stack:
  added: []
  patterns:
    - "Static writeTag/readTag helpers on BlockEntity for headless NBT unit testing (frozen registry workaround)"
    - "BlockEntityType.Builder.of(...).build(null) registration pattern in NeoForge 21.1"
    - "BLOCKS.register before BLOCK_ENTITY_TYPES.register in SquireRegistry.register() — required order"
    - "PlayerLoggedOutEvent cleanup via NeoForge.EVENT_BUS.addListener() in SquireRegistry"

key-files:
  created:
    - src/main/java/com/sjviklabs/squire/block/SignpostBlock.java
    - src/main/java/com/sjviklabs/squire/block/SignpostBlockEntity.java
    - src/main/resources/assets/squire/blockstates/signpost.json
    - src/main/resources/assets/squire/models/block/signpost.json
    - src/main/resources/assets/squire/models/item/signpost.json
    - src/main/resources/data/squire/loot_table/blocks/signpost.json
    - src/test/java/com/sjviklabs/squire/block/SignpostBlockEntityTest.java
    - src/test/java/com/sjviklabs/squire/brain/handler/PatrolHandlerTest.java
    - src/test/java/com/sjviklabs/squire/brain/handler/MountHandlerTest.java
  modified:
    - src/main/java/com/sjviklabs/squire/SquireRegistry.java

key-decisions:
  - "SignpostBlockEntity exposes static writeTag/readTag helpers (package-private) so NBT round-trip tests avoid BlockEntity instantiation — BlockEntity.<init> in 1.21.1 calls validateBlockState which NPEs on null type and throws IllegalStateException on a frozen registry when a new BlockEntityType is created at test time"
  - "BlockEntityType registration uses Builder.of(...).build(null) — the new BlockEntityType(factory, false, block) constructor from the plan interface block does not exist in 1.21.1; Builder is the only public path"
  - "SIGNPOST_ITEM DeferredHolder placed after SIGNPOST_BLOCK in source order to avoid illegal forward reference compiler error (even though it's inside a lambda, Java static initializer ordering still applies to the holder field declarations)"
  - "PENDING_LINKS kept private in SignpostBlock; SquireRegistry calls removePendingLink(UUID) static accessor for PlayerLoggedOutEvent cleanup — avoids exposing map internals across packages"
  - "PatrolMode enum has WAYPOINT and PATROL_START (not v0.5.0's WAYPOINT/GUARD_POST/PERIMETER) — aligns with Phase 7 FSM states defined in RESEARCH.md"

patterns-established:
  - "Pattern: Static writeTag/readTag on BlockEntity subclasses allows headless NBT unit tests without NeoForge bootstrap — use whenever BlockEntity NBT logic needs test coverage"
  - "Pattern: PlayerLoggedOutEvent cleanup via NeoForge.EVENT_BUS.addListener() in SquireRegistry.register() — consistent with ProgressionDataLoader AddReloadListenerEvent pattern in SquireMod"

requirements-completed: [PTR-01, PTR-03]

duration: 45min
completed: 2026-04-04
---

# Phase 07 Plan 01: Signpost Block + Phase 7 Test Scaffolds Summary

**HorizontalDirectionalBlock patrol signpost with NBT-persisted waypoint config and Crest two-click linking; three Phase 7 test scaffold files covering PTR-01 through MNT-04**

## Performance

- **Duration:** ~45 min
- **Started:** 2026-04-04T01:27:20Z
- **Completed:** 2026-04-04T02:12:00Z
- **Tasks:** 2
- **Files modified:** 10

## Accomplishments

- SignpostBlock and SignpostBlockEntity ported from v0.5.0 into v2 block/ package with updated NbtUtils API (readBlockPos two-arg form, writeBlockPos returning Tag not CompoundTag)
- All five PTR-01 NBT round-trip tests green (mode, linkedSignpost, waitTicks, assignedOwner, absent key) using static helper pattern that bypasses frozen registry
- Three Phase 7 test scaffold files compile and all stubs are @Disabled — Wave 2 PatrolHandler and MountHandler have a Nyquist-compliant test path waiting

## Task Commits

1. **Task 1: Phase 7 test scaffolds** — `db3c68c` (test)
2. **Task 2: SignpostBlock + SignpostBlockEntity + registration** — `2ab19ba` (feat)

## Files Created/Modified

- `src/main/java/com/sjviklabs/squire/block/SignpostBlock.java` — HorizontalDirectionalBlock, EntityBlock, PENDING_LINKS map, Crest two-click link gesture, PlayerLoggedOutEvent cleanup accessor
- `src/main/java/com/sjviklabs/squire/block/SignpostBlockEntity.java` — PatrolMode enum, four NBT fields, static writeTag/readTag helpers, client sync pattern
- `src/main/java/com/sjviklabs/squire/SquireRegistry.java` — BLOCKS + BLOCK_ENTITY_TYPES registers, SIGNPOST_BLOCK/ENTITY/ITEM holders, PlayerLoggedOutEvent listener
- `src/main/resources/assets/squire/blockstates/signpost.json` — four facing variants
- `src/main/resources/assets/squire/models/block/signpost.json` — cube_all placeholder model
- `src/main/resources/assets/squire/models/item/signpost.json` — parent block model
- `src/main/resources/data/squire/loot_table/blocks/signpost.json` — self-drop with survives_explosion condition
- `src/test/java/com/sjviklabs/squire/block/SignpostBlockEntityTest.java` — 5 NBT tests green + 1 @Disabled linking gesture stub
- `src/test/java/com/sjviklabs/squire/brain/handler/PatrolHandlerTest.java` — 4 @Disabled PTR-02 stubs
- `src/test/java/com/sjviklabs/squire/brain/handler/MountHandlerTest.java` — 4 @Disabled MNT-01/02/03/04 stubs

## Decisions Made

- Static `writeTag`/`readTag` helpers extracted from `SignpostBlockEntity` to enable headless testing. `BlockEntity.<init>` in 1.21.1 calls `validateBlockState` which requires either a null-safe type (fails — registry frozen) or a fully resolved `BlockEntityType` (fails — `createIntrusiveHolder` throws on frozen registry). Pure static methods on the entity class avoid the constructor entirely.
- `BlockEntityType.Builder.of(...).build(null)` used for registration — the `new BlockEntityType<>(factory, false, block)` constructor in the plan interface block does not exist; `false` is not a valid second argument in 1.21.1.
- `SIGNPOST_ITEM` moved after `SIGNPOST_BLOCK` declaration to avoid Java forward-reference compile error.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] BlockEntityType constructor signature mismatch**

- **Found during:** Task 2 (compile)
- **Issue:** Plan's interface block showed `new BlockEntityType<>(SignpostBlockEntity::new, false, SIGNPOST_BLOCK.get())` — `false` is not a valid parameter; actual 1.21.1 constructor takes `(factory, Set<Block>, Type<?>)`. Builder is the public path.
- **Fix:** Changed to `BlockEntityType.Builder.of(SignpostBlockEntity::new, SIGNPOST_BLOCK.get()).build(null)`
- **Files modified:** SquireRegistry.java
- **Committed in:** `2ab19ba`

**2. [Rule 1 - Bug] DeferredHolder.asItem() does not exist**

- **Found during:** Task 2 (compile)
- **Issue:** `SquireRegistry.CREST.asItem()` — `DeferredHolder` has no `asItem()` method. `CREST.get()` returns `SquireCrestItem` which extends `Item`, correct for `ItemStack.is(Item)`.
- **Fix:** Changed to `SquireRegistry.CREST.get()`
- **Files modified:** SignpostBlock.java
- **Committed in:** `2ab19ba`

**3. [Rule 1 - Bug] Static initializer forward reference (SIGNPOST_ITEM before SIGNPOST_BLOCK)**

- **Found during:** Task 2 (compile)
- **Issue:** SIGNPOST_ITEM was declared in the ITEMS section (line ~116) before SIGNPOST_BLOCK was declared in the BLOCKS section (~126). Java reports illegal forward reference on the lambda body.
- **Fix:** Moved SIGNPOST_ITEM to after SIGNPOST_BLOCK in source order
- **Files modified:** SquireRegistry.java
- **Committed in:** `2ab19ba`

**4. [Rule 1 - Bug] BlockEntity headless test NPE/IllegalStateException**

- **Found during:** Task 2 (test execution)
- **Issue:** `SignpostBlockEntity(BlockPos, null)` → `BlockEntity.<init>` calls `validateBlockState(null)` → NPE on `null.getBlock()`. Workaround with `super(null, ...)` → `IllegalStateException: Registry is already frozen` when building a new `BlockEntityType` at test time.
- **Fix:** Extracted `writeTag`/`readTag` as static package-private helpers. Tests call these directly — no `BlockEntity` instantiation needed for NBT round-trip coverage.
- **Files modified:** SignpostBlockEntity.java, SignpostBlockEntityTest.java
- **Committed in:** `2ab19ba`

---

**Total deviations:** 4 auto-fixed (Rule 1 — bugs in plan interface spec and test execution)
**Impact on plan:** All fixes were correctness issues — wrong API signatures in the plan's interface block and a 1.21.1 test environment constraint. No scope creep. The static helper pattern is a net improvement over the test-constructor approach: it tests the exact serialization logic without any BlockEntity lifecycle involvement.

## Issues Encountered

None beyond the four auto-fixed compile/runtime errors above.

## Next Phase Readiness

- Wave 2 (07-02) has test scaffolds waiting: PatrolHandlerTest (4 stubs) and MountHandlerTest (4 stubs)
- SignpostBlock is placeable and linkable — in-game behavior testable immediately after world load
- SignpostBlockEntity NBT is fully tested; save/load contract is locked
- PENDING_LINKS cleanup on player logout is wired — no stale entry leak risk in multiplayer

---

_Phase: 07-patrol-mounting_
_Completed: 2026-04-04_

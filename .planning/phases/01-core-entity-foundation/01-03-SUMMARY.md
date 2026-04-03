---
phase: 01-core-entity-foundation
plan: "03"
subsystem: inventory
tags: [neoforge, iitemhandler, itemstackhandler, abstractcontainermenu, capability, slot, menu]

requires:
  - phase: 01-02
    provides: SquireTier enum with getBackpackSlots(), SquireEntity with getTier(), SquireRegistry with MENU_TYPES

provides:
  - SquireItemHandler: IItemHandler (ItemStackHandler) with 6 equipment + tier-gated backpack slots (15-42 total)
  - IItemHandler ENTITY and ENTITY_AUTOMATION capabilities registered on SquireEntity via RegisterCapabilitiesEvent
  - SquireMenu: AbstractContainerMenu stub exposing backpack slots as SlotItemHandler objects, opens server-side
  - SQUIRE_MENU MenuType registered in SquireRegistry via IMenuTypeExtension.create()
  - SquireEntity.mobInteract() opens inventory GUI for owner, sends entity ID via extraDataWriter
  - SquireEntity.ensureChunkLoaded() no-op stub (ARC-09, full impl Phase 6)
  - NBT save/load wired to ItemStackHandler serializeNBT/deserializeNBT

affects:
  - Phase 2 (SquireBrain): getItemHandler() available for loot pickup logic
  - Phase 5 (GUI): SquireMenu slot layout and SQUIRE_MENU type must not change; Screen class added client-side only
  - Phase 6 (MiningHandler): ensureChunkLoaded() stub is the extension point for ForceChunkManager

tech-stack:
  added: []
  patterns:
    - IMenuTypeExtension.create() for menus that need extra data (entity ID) on client
    - openMenu(provider, buf -> buf.writeInt(entityId)) pattern for entity-based menus
    - ItemStackHandler allocated at max capacity (42), getSlots() gates tier access
    - RegisterCapabilitiesEvent on mod bus — both ENTITY and ENTITY_AUTOMATION return same handler

key-files:
  created:
    - src/main/java/com/sjviklabs/squire/inventory/SquireItemHandler.java
    - src/main/java/com/sjviklabs/squire/inventory/SquireMenu.java
  modified:
    - src/main/java/com/sjviklabs/squire/entity/SquireEntity.java
    - src/main/java/com/sjviklabs/squire/SquireRegistry.java

key-decisions:
  - "ItemStackHandler allocated at Champion max (42 slots) — avoids resize on tier-up; getSlots() gates access"
  - "ENTITY and ENTITY_AUTOMATION both return the same handler — automation sees identical inventory as GUI"
  - "Equipment slots (0-5) excluded from SquireMenu slot list in Phase 1 — Phase 5 adds dedicated equipment UI"
  - "onContentsChanged is a no-op — PathfinderMob entity NBT saved automatically by level; no dirty flag needed"
  - "openMenu uses extraDataWriter (buf.writeInt entityId) so client-side IContainerFactory can locate squire"

patterns-established:
  - "Pattern: IMenuTypeExtension.create(IContainerFactory) — correct NeoForge 21.1.221 menu registration with extra data"
  - "Pattern: Capability registration in RegisterCapabilitiesEvent on modEventBus (not NeoForge bus)"

requirements-completed: [INV-01, INV-02, INV-06, ARC-09]

duration: 27min
completed: 2026-04-03
---

# Phase 01 Plan 03: Squire Inventory Summary

**IItemHandler capability with tier-gated slot counts (15-42), IContainerFactory menu registration, and server-side menu open via extraDataWriter entity ID pattern**

## Performance

- **Duration:** 27 min
- **Started:** 2026-04-03T07:20:54Z
- **Completed:** 2026-04-03T07:48:19Z
- **Tasks:** 2
- **Files modified:** 4 (2 created, 2 modified)

## Accomplishments

- SquireItemHandler extends ItemStackHandler: getSlots() returns 6 + tier.getBackpackSlots() dynamically, insert/extract guard slot bounds, allocated at max 42 to avoid resize on tier-up
- Both ENTITY and ENTITY_AUTOMATION IItemHandler capabilities registered — hoppers and modded pipes can access backpack slots immediately (INV-02)
- SquireMenu stub: backpack slots exposed as SlotItemHandler, shift-click logic, stillValid() distance check, opens server-side without crash (INV-06)
- SquireEntity wired: typed itemHandler field initialized in constructor, mobInteract opens menu for owner via openMenu+extraDataWriter, NBT hooks implemented, ensureChunkLoaded() stub added (ARC-09)

## Task Commits

Each task was committed atomically:

1. **Task 1: SquireItemHandler + capability registration** - `fd380e5` (feat)
2. **Task 2: SquireMenu stub + SQUIRE_MENU registration** - `a67d178` (feat)

**Plan metadata:** see final commit below

## Files Created/Modified

- `src/main/java/com/sjviklabs/squire/inventory/SquireItemHandler.java` — IItemHandler with EQUIPMENT_SLOTS=6, tier-gated getSlots(), equipment/backpack slot helpers
- `src/main/java/com/sjviklabs/squire/inventory/SquireMenu.java` — AbstractContainerMenu stub, SlotItemHandler backpack slots, player inventory, quickMoveStack, stillValid <8 blocks
- `src/main/java/com/sjviklabs/squire/entity/SquireEntity.java` — typed SquireItemHandler field, constructor init, getItemHandler(), mobInteract with openMenu+extraDataWriter, ensureChunkLoaded stub, NBT wired
- `src/main/java/com/sjviklabs/squire/SquireRegistry.java` — SQUIRE_MENU via IMenuTypeExtension.create(), registerCapabilities event for ENTITY and ENTITY_AUTOMATION

## Decisions Made

- **ItemStackHandler at max capacity:** Allocate 42 slots at construction, gate with getSlots() — avoids a resize/copy on tier-up mid-game. Clean and cheap.
- **Same handler for ENTITY and ENTITY_AUTOMATION:** Automation doesn't get a filtered view; it sees the full backpack. Filtering (junk/whitelist) is a Phase 5 concern.
- **Equipment slots excluded from SquireMenu Phase 1:** Slots 0-5 are in the handler but not added to the menu's slot list. Phase 5 adds dedicated equipment slot UI without changing the handler or menu registration.
- **onContentsChanged is a no-op:** Entity NBT is saved by the world automatically on the next save cycle. setChanged() is a BlockEntity concept; calling it on a PathfinderMob doesn't compile and isn't needed.
- **IMenuTypeExtension.create() over new MenuType():** The NeoForge extension creates a MenuType that accepts a RegistryFriendlyByteBuf factory — required to read the entity ID on the client side. Direct MenuType constructor doesn't support extra data without this.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] setChanged() does not exist on PathfinderMob**

- **Found during:** Task 1 (SquireItemHandler onContentsChanged)
- **Issue:** Plan specified `squire.setChanged()` in onContentsChanged — this method is defined on BlockEntity, not Entity. Compile error.
- **Fix:** Made onContentsChanged a no-op with explanatory comment. Entity NBT is saved automatically by the level; no dirty flag is needed.
- **Files modified:** SquireItemHandler.java
- **Committed in:** fd380e5 (Task 1 commit)

**2. [Rule 1 - Bug] MenuType constructor pattern incorrect for extra-data menus**

- **Found during:** Task 2 (SQUIRE_MENU registration)
- **Issue:** Plan's `new MenuType<>((IContainerFactory<SquireMenu>) ...)` doesn't work — the constructor signature in 21.1.221 expects a FeatureFlags set, not an IContainerFactory. IMenuTypeExtension.create() is the correct API.
- **Fix:** Replaced with `IMenuTypeExtension.create(factory)` after reading NeoForge 21.1.221 sources from Gradle cache.
- **Files modified:** SquireRegistry.java
- **Committed in:** a67d178 (Task 2 commit)

---

**Total deviations:** 2 auto-fixed (both Rule 1 — incorrect API assumptions in plan)
**Impact on plan:** Both fixes necessary for compilation. Behavior is identical to what the plan intended.

## Issues Encountered

None beyond the two auto-fixed API mismatches above.

## User Setup Required

None — no external service configuration required.

## Next Phase Readiness

- IItemHandler capability surface is complete and correct. Phase 2 (SquireBrain) can call `squire.getItemHandler()` for loot pickup logic without touching infrastructure.
- Phase 5 (GUI): SquireMenu slot indices are stable. Add the client Screen class registered against SQUIRE_MENU — do not change slot layout or menu constructor signature.
- Phase 6 (MiningHandler): ensureChunkLoaded() stub at line ~230 of SquireEntity.java is the extension point — replace the comment with ForceChunkManager call.

---

_Phase: 01-core-entity-foundation_
_Completed: 2026-04-03_

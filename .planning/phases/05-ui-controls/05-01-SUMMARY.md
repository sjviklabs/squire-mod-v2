---
phase: 05-ui-controls
plan: "01"
subsystem: ui
tags: [AbstractContainerMenu, AbstractContainerScreen, SlotItemHandler, EquipmentSlot, NeoForge-1.21.1, TDD]

requires:
  - phase: 01-core-entity-foundation
    provides: SquireItemHandler with EQUIPMENT_SLOTS=6 and tier-gated getSlots()
  - phase: 01-core-entity-foundation
    provides: SquireRegistry.SQUIRE_MENU registered via IMenuTypeExtension
  - phase: 05-ui-controls (plan 05-02)
    provides: SquireClientEvents with MOD bus subscriber for screen registration

provides:
  - SquireMenu with 6 equipment slots before backpack slots (MENU_EQUIPMENT_SLOTS constant)
  - EquipmentSlotTyped inner class validating armor via ArmorItem.getType() (no entity needed)
  - quickMoveStack handling all 4 transfer directions via moveItemStackTo
  - SquireScreen in client/ package with entity preview, HP/XP bars, locked backpack rows
  - SquireScreen.register(RegisterMenuScreensEvent) wired in SquireClientEvents
  - SquireMenuTest: 8 unit tests covering slot counts, equipment validation, quickMoveStack

affects: [05-02-SquireClientEvents, 05-03-server-safety-pass, phase-06-fishing-handler, phase-07-mount]

tech-stack:
  added: []
  patterns:
    - "Equipment slot validation via ArmorItem.getType() comparison — avoids canEquip(null) NPE in NeoForge 1.21.1"
    - "Test-only constructor SquireMenu(SquireTier, IItemHandler) bypasses live SquireEntity for headless JUnit tests"
    - "RegisterMenuScreensEvent.register() instead of direct MenuScreens.register() (private access in 1.21.1)"
    - "MENU_EQUIPMENT_SLOTS constant as single source of truth for all quickMoveStack index math"

key-files:
  created:
    - src/main/java/com/sjviklabs/squire/inventory/SquireMenu.java
    - src/main/java/com/sjviklabs/squire/client/SquireScreen.java
    - src/test/java/com/sjviklabs/squire/inventory/SquireMenuTest.java
  modified:
    - src/main/java/com/sjviklabs/squire/client/SquireClientEvents.java

key-decisions:
  - "ArmorItem.getType() for equipment slot validation — canEquip(slot, null) NPEs in NeoForge 1.21.1 because the default impl calls entity.getEquipmentSlotForItem(stack) requiring non-null LivingEntity"
  - "RegisterMenuScreensEvent over direct MenuScreens.register() — MenuScreens.register() has private access in NeoForge 1.21.1; must go through the event"
  - "blockInteractionRange() over getBlockReach() — getBlockReach() removed in 1.21.1; blockInteractionRange() is the correct LocalPlayer API"
  - "SquireMenu(SquireTier, IItemHandler) headless constructor — enables JUnit 5 unit tests without NeoForge game bootstrap"

patterns-established:
  - "TDD RED/GREEN: test file written with compile-failing references, implementation added to turn GREEN"
  - "Slot index constants in SquireMenu (MENU_EQUIPMENT_SLOTS, ARMOR_COL_X, BACKPACK_X, WEAPON_COL_X) shared between menu and screen via public fields"

requirements-completed: [GUI-03]

duration: 39min
completed: 2026-04-03
---

# Phase 5 Plan 01: SquireMenu Equipment Slots + SquireScreen Summary

**AbstractContainerMenu extended with 6 typed equipment slots, typed armor validation via ArmorItem.getType(), 4-direction quickMoveStack, and a full AbstractContainerScreen renderer with entity preview and tier-locked backpack visuals**

## Performance

- **Duration:** 39 min
- **Started:** 2026-04-03T22:40:04Z
- **Completed:** 2026-04-03T23:19:00Z
- **Tasks:** 2 (TDD RED + GREEN)
- **Files modified:** 4

## Accomplishments

- SquireMenu now registers 6 equipment slots (helmet, chest, legs, boots, mainhand, offhand) at menu indices 0-5, before the tier-gated backpack grid — MENU_EQUIPMENT_SLOTS = 6 is the single index anchor for all shift-click math
- EquipmentSlotTyped inner class validates armor by ArmorItem.getType() without requiring a live LivingEntity; hand slots (mainhand/offhand) accept any item
- quickMoveStack handles all 4 directions (equipment→player, backpack→player, player→equipment, player→backpack) using moveItemStackTo, no manual item loops
- SquireScreen renders entity preview via InventoryScreen.renderEntityInInventoryFollowsMouse, HP/XP stats bars, locked backpack rows with tier level labels, and section separators — full client-only package isolation
- All 8 SquireMenuTest tests pass; ./gradlew build clean

## Task Commits

1. **Task 1: Write test scaffold for SquireMenu slot layout and quickMoveStack (RED)** - `a9017cf` (test)
2. **Task 2: Extend SquireMenu with equipment slots and build SquireScreen (GREEN)** - `0be01df` (feat)

## Files Created/Modified

- `src/main/java/com/sjviklabs/squire/inventory/SquireMenu.java` - Extended from Phase 1 stub: MENU_EQUIPMENT_SLOTS constant, 6 EquipmentSlotTyped slots, tier-aware backpack grid, 4-direction quickMoveStack, getters for Screen, headless test constructor
- `src/main/java/com/sjviklabs/squire/client/SquireScreen.java` - New: AbstractContainerScreen with entity preview, HP/XP bars, locked row visuals, SquireScreen.register(RegisterMenuScreensEvent)
- `src/test/java/com/sjviklabs/squire/inventory/SquireMenuTest.java` - New: 8 unit tests (slot counts, mayPlace validation, quickMoveStack routing)
- `src/main/java/com/sjviklabs/squire/client/SquireClientEvents.java` - Added onRegisterMenuScreens calling SquireScreen.register(event); removed stale TODO comment

## Decisions Made

- **ArmorItem.getType() for slot validation:** canEquip(slot, null) NPEs in NeoForge 1.21.1 because the default IItemExtension implementation calls entity.getEquipmentSlotForItem(stack) — entity cannot be null. Direct ArmorItem.getType() comparison is cleaner and doesn't need an entity.
- **RegisterMenuScreensEvent:** MenuScreens.register() has private access in vanilla 1.21.1; NeoForge 1.21.1 exposes RegisterMenuScreensEvent on the MOD bus instead. SquireScreen.register() takes the event as parameter.
- **Headless test constructor:** SquireMenu(SquireTier, IItemHandler) with package-private visibility bypasses the live SquireEntity and NeoForge registry lookup — enables clean JUnit 5 unit tests without game bootstrap. Uses null MenuType and windowId=0.
- **Knight tier maps to tierIndex=2 (not 3):** Knight has 32 backpack slots (more than Squire's 27) but fewer than Champion's 36. The backpack row display treats Knight as showing all 4 rows (same visual as Champion) — index 3 in the locked-row logic.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] SquireClientEvents.getBlockReach() → blockInteractionRange()**

- **Found during:** Task 1 (running tests — SquireClientEvents.java blocked compileJava)
- **Issue:** `mc.player.getBlockReach()` removed in 1.21.1; `blockInteractionRange()` is the correct LocalPlayer API
- **Fix:** One-line substitution in SquireClientEvents.java line 86
- **Files modified:** src/main/java/com/sjviklabs/squire/client/SquireClientEvents.java
- **Verification:** `./gradlew compileJava` passes clean
- **Committed in:** a9017cf (Task 1 commit, part of RED phase fix)

**2. [Rule 1 - Bug] canEquip(slot, null) NPE → ArmorItem.getType() comparison**

- **Found during:** Task 2 GREEN phase (3 tests failed with NullPointerException)
- **Issue:** NeoForge 1.21.1 IItemExtension.canEquip() default impl calls entity.getEquipmentSlotForItem(stack) — entity must be non-null. RESEARCH.md example was incorrect for this NeoForge version.
- **Fix:** Replaced canEquip() calls with direct ArmorItem instanceof + getType() == expected comparison in EquipmentSlotTyped.mayPlace()
- **Files modified:** src/main/java/com/sjviklabs/squire/inventory/SquireMenu.java
- **Verification:** All 8 SquireMenuTest tests GREEN
- **Committed in:** 0be01df (Task 2 commit)

**3. [Rule 3 - Blocking] MenuScreens.register() private access → RegisterMenuScreensEvent**

- **Found during:** Task 2 compile (SquireScreen.register() wouldn't compile)
- **Issue:** MenuScreens.register() has private access in NeoForge 1.21.1; must use RegisterMenuScreensEvent
- **Fix:** Changed SquireScreen.register() signature to accept RegisterMenuScreensEvent; wired in SquireClientEvents.ModEvents.onRegisterMenuScreens
- **Files modified:** src/main/java/com/sjviklabs/squire/client/SquireScreen.java, SquireClientEvents.java
- **Verification:** compileJava clean; build succeeds
- **Committed in:** 0be01df (Task 2 commit)

---

**Total deviations:** 3 auto-fixed (1 Rule 1 bug, 2 Rule 3 blocking)
**Impact on plan:** All three fixes were NeoForge 1.21.1 API differences from research examples. No scope creep — all fixes were in-plan files.

## Issues Encountered

- RESEARCH.md code example for equipment slot validation used `canEquip(slot, null)` which worked in earlier NeoForge versions but NPEs in 1.21.1 due to entity being required. Fixed via ArmorItem.getType() which is cleaner anyway.
- Plan 05-02 (running in parallel) had already created SquireClientEvents.java with a 1.21.1 API error (`getBlockReach()`). Fixed as Rule 3 blocking issue before our tests could run.

## Next Phase Readiness

- SquireMenu and SquireScreen are complete — GUI-03 requirement satisfied
- SquireScreen.register() is wired via SquireClientEvents.ModEvents.onRegisterMenuScreens
- Plan 05-02 (SquireRadialScreen) runs in parallel and shares SquireClientEvents — no overlap
- Plan 05-03 (server safety pass) should verify SquireScreen and SquireMenu have no cross-boundary imports

---

_Phase: 05-ui-controls_
_Completed: 2026-04-03_

## Self-Check: PASSED

- FOUND: src/main/java/com/sjviklabs/squire/inventory/SquireMenu.java
- FOUND: src/main/java/com/sjviklabs/squire/client/SquireScreen.java
- FOUND: src/test/java/com/sjviklabs/squire/inventory/SquireMenuTest.java
- FOUND: .planning/phases/05-ui-controls/05-01-SUMMARY.md
- FOUND commit: a9017cf (test RED)
- FOUND commit: 0be01df (feat GREEN)

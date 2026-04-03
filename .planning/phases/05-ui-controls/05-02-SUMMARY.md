---
phase: 05-ui-controls
plan: "02"
subsystem: client-ui
tags: [keybind, radial-menu, client-events, tdd, gui]
dependency_graph:
  requires:
    - 02-04 (SquireCommandPayload — StreamCodec payload, CMD_ constants)
    - 03-01 (SquireRenderer — referenced in registerRenderers)
  provides:
    - SquireKeybinds.RADIAL_MENU keybind constant
    - SquireRadialScreen 4-wedge overlay with computeWedge()
    - SquireClientEvents wired for MOD + GAME bus, Dist.CLIENT only
  affects:
    - 05-01 (SquireScreen.register() TODO stub in onClientSetup — complete after 05-01)
tech_stack:
  added: []
  patterns:
    - TDD RED/GREEN cycle for angle math unit test
    - Inner static class pattern for dual @EventBusSubscriber (MOD + GAME bus)
    - TRIANGLE_STRIP geometry for wedge rendering (no texture atlas needed)
    - confirmed boolean field prevents double packet send on screen close
key_files:
  created:
    - src/main/java/com/sjviklabs/squire/client/SquireKeybinds.java
    - src/main/java/com/sjviklabs/squire/client/SquireRadialScreen.java
    - src/test/java/com/sjviklabs/squire/client/SquireRadialAngleTest.java
  modified:
    - src/main/java/com/sjviklabs/squire/client/SquireClientEvents.java
decisions:
  - "Inner static class pattern for dual bus subscriptions — NeoForge does not support
    two @EventBusSubscriber on one outer class; ModEvents (MOD bus) and GameEvents
    (GAME bus) as separate annotated inner statics is the clean solution"
  - "4-wedge layout for Phase 5 — v0.5.0 had 8 wedges but only 4 are functional in
    Phase 5; stub/grayed wedges create false expectations; wedge count expands per phase"
  - "confirmed boolean field in SquireRadialScreen — prevents the packet from firing
    twice when mouseClicked() calls onClose() after dispatch; onClose() alone is
    insufficient because Escape/R-close also calls onClose()"
  - "blockInteractionRange() over getBlockReach() — getBlockReach() is absent in
    NeoForge 1.21.1; blockInteractionRange() is the correct LocalPlayer API"
  - "SquireScreen.register() left as TODO in FMLClientSetupEvent — Plan 05-01 runs in
    parallel and SquireScreen does not yet exist; stub handler is wired and ready"
  - "CMD_STAY=3, CMD_INVENTORY=7 used from existing SquireCommandPayload — Phase 2
    established these constants; plan interface doc showed different values but the
    live file takes precedence"
metrics:
  duration_minutes: 13
  completed_date: "2026-04-03"
  tasks_completed: 2
  files_created: 3
  files_modified: 1
---

# Phase 05 Plan 02: Radial Menu Keybind and Client Events Summary

**One-liner:** 4-wedge radial overlay (R key) with atan2 angle math, TRIANGLE_STRIP geometry, and dual-bus client event wiring — all Dist.CLIENT, no server imports.

## What Was Built

**SquireKeybinds** (`client/SquireKeybinds.java`) — single `KeyMapping` constant for the radial menu. R key, `KeyConflictContext.IN_GAME`, category `key.categories.squire`. Appears in the vanilla Controls screen under a dedicated Squire section.

**SquireRadialScreen** (`client/SquireRadialScreen.java`) — `extends Screen`, never `AbstractContainerScreen`. Key behaviors:
- `isPauseScreen()` returns `false` — server keeps ticking in multiplayer
- `renderBackground()` is a no-op — transparent overlay over the world view
- 4 wedges: Follow (wedge 0), Stay (wedge 1), Guard (wedge 2), Inventory (wedge 3)
- `computeWedge(float dx, float dy)` — static, package-accessible, pure math: `atan2(dx, -dy)` normalized to `[0, 2π)`, divided by `π/2`
- `confirmed` boolean: set in `mouseClicked()` before `onClose()` to prevent double packet dispatch
- Renders wedges with TRIANGLE_STRIP geometry, separator lines with DEBUG_LINES, labels via `GuiGraphics.drawString()`

**SquireClientEvents** (`client/SquireClientEvents.java`) — restructured from single-class to inner static class pattern:
- `ModEvents` (`@EventBusSubscriber(bus=MOD, Dist.CLIENT)`): renderer registration, keybind registration, `FMLClientSetupEvent` stub for `SquireScreen.register()` (TODO, awaiting Plan 05-01)
- `GameEvents` (`@EventBusSubscriber(bus=GAME, Dist.CLIENT)`): `InputEvent.Key` handler that raycasts with `blockInteractionRange()`, checks for `SquireEntity` ownership, opens `SquireRadialScreen`

**SquireRadialAngleTest** (`test/.../client/SquireRadialAngleTest.java`) — 6 unit tests, all GREEN:
- Cursor above → wedge 0 (Follow)
- Cursor right → wedge 1 (Stay)
- Cursor below → wedge 2 (Guard)
- Cursor left → wedge 3 (Inventory)
- Negative angle normalization verified
- Right boundary (exact π/2) maps to wedge 1

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] blockInteractionRange() substituted for getBlockReach()**

- **Found during:** Task 2 — formatter/compiler correction
- **Issue:** Plan interface doc referenced `mc.player.getBlockReach()` which does not exist in NeoForge 1.21.1 `LocalPlayer`
- **Fix:** Used `mc.player.blockInteractionRange()` — the correct 1.21.1 API (also matches how `SquireCrestItem` handles range elsewhere in the codebase)
- **Files modified:** `SquireClientEvents.java`
- **Commit:** f0772ff

**2. [Rule 1 - Bug] CMD_ constant values matched to existing SquireCommandPayload**

- **Found during:** Task 2 — reading existing Phase 2 file
- **Issue:** Plan interface doc listed `CMD_STAY=2, CMD_INVENTORY=3` but existing `SquireCommandPayload` (Phase 2) has `CMD_STAY=3, CMD_INVENTORY=7`. Wire format is already established.
- **Fix:** Used constants from the live file (`SquireCommandPayload.CMD_FOLLOW/STAY/GUARD/INVENTORY`) — avoids renumbering wire format
- **Files modified:** `SquireRadialScreen.java` (references constants by name, not literal)
- **Commit:** f0772ff

**3. [Rule 1 - Note] SquireClientEvents restructured to inner static classes**

- **Found during:** Task 2 planning
- **Issue:** NeoForge does not support two `@EventBusSubscriber` annotations on one outer class; the plan suggested annotating the outer class twice
- **Fix:** Inner static `ModEvents` and `GameEvents` pattern — each annotated independently, both with `Dist.CLIENT`
- **Files modified:** `SquireClientEvents.java`
- **Commit:** f0772ff

**4. [Rule 3 - Scope note] SquireMenuTest pre-existing RED state**

- **Found during:** Task 1 full build attempt
- **Issue:** `SquireMenuTest` (Plan 05-01 RED tests) references `SquireMenu.MENU_EQUIPMENT_SLOTS` which doesn't exist yet — causes compile failures on `./gradlew build` but not on targeted `--tests "*.SquireRadialAngleTest"`
- **Action:** Not fixed — this is Plan 05-01's RED state, out of scope for this plan. `SquireRadialAngleTest` runs and passes independently.

## Pending Follow-up (Post-Plan 05-01)

When Plan 05-01 completes and `SquireScreen` exists, uncomment in `SquireClientEvents.ModEvents.onClientSetup()`:
```java
MenuScreens.register(SquireRegistry.SQUIRE_MENU.get(), SquireScreen::new);
```

## Self-Check: PASSED

| Item | Status |
| ---- | ------ |
| SquireKeybinds.java | FOUND |
| SquireRadialScreen.java | FOUND |
| SquireClientEvents.java | FOUND |
| SquireRadialAngleTest.java | FOUND |
| Commit a6881e7 (RED test) | FOUND |
| Commit f0772ff (GREEN implementation) | FOUND |

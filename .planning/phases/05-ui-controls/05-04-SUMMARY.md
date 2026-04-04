---
phase: 05-ui-controls
plan: "04"
subsystem: inventory-ai
tags: [item-pickup, junk-filter, fsm, inventory, iitemhandler]
dependency_graph:
  requires: [05-01, SquireItemHandler, SquireConfig, SquireBrain]
  provides: [ItemHandler, PICKING_UP_ITEM transitions, junkFilterList config]
  affects: [SquireBrain.java, SquireConfig.java]
tech_stack:
  added: []
  patterns: [insertItem simulate-check, ResourceLocation junk cache, FSM priority 45 work tier]
key_files:
  created:
    - src/main/java/com/sjviklabs/squire/brain/handler/ItemHandler.java
  modified:
    - src/main/java/com/sjviklabs/squire/brain/SquireBrain.java
    - src/main/java/com/sjviklabs/squire/config/SquireConfig.java
decisions:
  - "ItemHandler placed in brain/handler/ (not ai/handler/) — ai/handler/ package does not exist in v2; all handlers live in brain/handler/"
  - "itemPickupRange reused from existing SquireConfig [inventory] section — field already present with correct defaults (3.0, range 1.0-8.0); junkFilterList added alongside it"
  - "defineListAllowEmpty used for junkFilter — allows empty list for operator opt-out; validator checks entry contains ':' to catch obvious malformed values"
  - "Junk set cached as HashSet<ResourceLocation> with config-change invalidation — avoids per-tick ResourceLocation.parse() allocation"
  - "Equipment slots 0-5 excluded from auto-pickup — auto-equip is a separate concern handled by SquireEquipmentHelper; backpack slots only"
  - "PICKING_UP_ITEM entered from both IDLE and FOLLOWING_OWNER at priority 45 — allows pickup while following without interrupting combat (priority 10-13)"
metrics:
  duration: 8
  completed_date: "2026-04-03"
  tasks_completed: 1
  files_changed: 3
---

# Phase 05 Plan 04: Item Pickup with Junk Filtering Summary

**One-liner:** Squire picks up nearby dropped items via insertItem()-only IItemHandler contract, filtered by a configurable junk list with ResourceLocation caching.

## What Was Built

`ItemHandler` in `brain/handler/` scans for `ItemEntity` instances within `itemPickupRange` (default 3.0 blocks) every 10 ticks. Items whose registry key matches `junkFilterList` are skipped. For each eligible item, the handler iterates backpack slots (6+) and calls `insertItem(slot, stack, false)` until the remainder is empty or all slots are exhausted. A simulate pass (`insertItem(slot, stack, true)`) gates both the FSM enter condition and per-tick capacity checks — no direct stack mutation anywhere.

`SquireConfig` gained `junkFilterList` in the `[inventory]` section with 5 default junk entries (cobblestone, dirt, gravel, rotten flesh, poisonous potato).

`SquireBrain` registers `ItemHandler` alongside the other behavior handlers and adds three transitions: enter `PICKING_UP_ITEM` from `IDLE` (priority 45), enter from `FOLLOWING_OWNER` (priority 45), and a per-tick transition at priority 46 that delegates to `item.tick()` and calls `item.stop()` on exit.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] ItemHandler placed in brain/handler/ instead of ai/handler/**

- **Found during:** Task 1, initial file discovery
- **Issue:** Plan specified `src/main/java/com/sjviklabs/squire/ai/handler/ItemHandler.java` but that package does not exist in v2. All handlers live in `brain/handler/` (FollowHandler, CombatHandler, DangerHandler, SurvivalHandler).
- **Fix:** Created `ItemHandler.java` in `brain/handler/` to match the actual project structure and allow direct import in `SquireBrain`.
- **Files modified:** ItemHandler.java (created at correct path)
- **Commit:** b135a4b

**2. [Rule 1 - Config alignment] pickupRange field already exists as itemPickupRange**

- **Found during:** Task 1, SquireConfig.java read
- **Issue:** Plan instructed adding `PICKUP_RANGE = defineInRange("pickupRange", 3.0, 1.0, 8.0)` but `itemPickupRange` already existed in the `[inventory]` section with identical defaults (3.0, 1.0-8.0).
- **Fix:** Reused `SquireConfig.itemPickupRange` in ItemHandler; only added `junkFilterList` which was genuinely missing.
- **Files modified:** No extra change needed
- **Commit:** b135a4b

## Self-Check: PASSED

- ItemHandler.java: FOUND
- SquireBrain.java: FOUND
- SquireConfig.java: FOUND
- 05-04-SUMMARY.md: FOUND
- Commit b135a4b: FOUND

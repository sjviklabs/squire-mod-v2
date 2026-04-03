---
phase: 03-rendering
plan: "04"
subsystem: entity/rendering
tags: [naming, chat, geckolib, phase-complete]
dependency_graph:
  requires: [03-01, 03-03]
  provides: [RND-03, RND-04]
  affects: [Phase 6 ChatHandler wiring, Phase 4 combat event hooks]
tech_stack:
  added: [ChatHandler.java]
  patterns: [static string pool per tier/event, server-side only chat, vanilla name tag item check]
key_files:
  created:
    - src/main/java/com/sjviklabs/squire/entity/ChatHandler.java
  modified:
    - src/main/java/com/sjviklabs/squire/entity/SquireEntity.java
decisions:
  - "ChatHandler placed in entity/ package (not client/) — send logic is server-side, no client imports needed"
  - "shouldShowName() returns hasCustomName() — shows name at any distance when custom name is set"
  - "Name tag consumes item in survival mode (instabuild check) — matches vanilla name tag behavior"
  - "Oculus validation deferred — squire renders correctly per prior 03-01/03-03 validation; in-game Oculus test pending user availability"
metrics:
  duration: 8
  completed: "2026-04-03T15:50:29Z"
  tasks_completed: 2
  files_modified: 2
---

# Phase 3 Plan 4: Name Tag Support and ChatHandler Stub Summary

Name tag interaction + personality chat scaffolding for the squire entity. Phase 3 complete.

## What Was Built

**Task 1: Name tag support + ChatHandler stub**

Added `shouldShowName()` override to `SquireEntity` — returns `hasCustomName()`, making the name visible at any distance when set. Added name tag branch at the top of `mobInteract()`: checks for `NameTagItem`, extracts `DataComponents.CUSTOM_NAME`, calls `setCustomName()` and `setCustomNameVisible(true)` server-side, consumes the item in survival mode.

Created `ChatHandler.java` in the `entity/` package (not `client/`). Contains:

- `ChatEvent` enum: `IDLE`, `COMBAT_START`, `LEVEL_UP`, `NEW_TIER`
- String pools for all 4 events × 5 tiers (SERVANT through CHAMPION)
- `sendLine(SquireEntity, Player, ChatEvent)` static method: tier-indexed random pool selection, prefixes message with squire name in brackets
- No `net.minecraft.client.*` imports — safe on dedicated server

**Task 2: Oculus/ATM10 shader validation**

Auto-approved per execution instructions. In-game Oculus validation deferred — user will test in ATM10 when available. Geckolib rendering stack (SquireEntity, SquireModel, SquireRenderer, SquireArmorLayer, SquireBackpackLayer) was built and compiled cleanly in 03-01 through 03-03.

## Phase 3 Requirements Coverage

| ID     | Description                          | Plan  | Status  |
| ------ | ------------------------------------ | ----- | ------- |
| RND-01 | GeoEntity + GeoEntityRenderer        | 03-01 | Done    |
| RND-02 | Male/female texture variants         | 03-01 | Done    |
| RND-03 | Name tag interaction                 | 03-04 | Done    |
| RND-04 | ChatHandler stub with string pools   | 03-04 | Done    |
| RND-05 | SquireBackpackLayer tier visibility  | 03-03 | Done    |
| RND-06 | SquireArmorLayer tiered textures     | 03-03 | Done    |
| INV-04 | SquireArmorItem registered x4        | 03-03 | Done    |

All 7 Phase 3 requirements addressed.

## ChatHandler Package Path (for Phase 6)

`com.sjviklabs.squire.entity.ChatHandler`

Phase 6 calls: `ChatHandler.sendLine(squire, owner, ChatHandler.ChatEvent.IDLE)`

The `// TODO Phase 6` comment in `sendLine()` marks where event bus publish replaces direct call when `SquireBrainEventBus` is wired.

## Deviations from Plan

**1. [Rule 1 - Bug] Corrected method name in ChatHandler**

- Found during: Task 1 implementation
- Issue: Plan template referenced `squire.getSquireLevel()` — method does not exist on SquireEntity; actual method is `getLevel()`
- Fix: Used `squire.getLevel()` in ChatHandler.sendLine()
- Files modified: ChatHandler.java
- Commit: 9860e1d

**2. [Auto-approved checkpoint] Oculus validation deferred**

- Checkpoint type: human-verify (blocking)
- Auto-approved per executor instructions: "Oculus validation deferred — user will test in ATM10 when available"
- No shader-specific issues found in prior 03-01/03-03 Geckolib API work
- Gate: Phase 4 start should wait on explicit Oculus pass confirmation from user

## Geckolib 4.8.4 API Deviations (Phase 3 Aggregate)

From 03-01: `GeoEntityModel` absent — use `GeoModel<T>`. `LoopType` is `Animation.LoopType` (nested). `getTextureResource` not `getTextureLocation` on GeoModel.

From 03-03: `getVanillaArmorBuffer` override instead of `getArmorTexture` (absent in 4.8.4). `ArmorItem` constructor takes `Holder<ArmorMaterial>` not `ArmorMaterial`.

No new Geckolib deviations in 03-04.

## Self-Check: PASSED

- ChatHandler.java: FOUND at entity/ package
- SquireEntity.java: FOUND (modified)
- Commit 9860e1d: FOUND in git log

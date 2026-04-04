# Phase 9: UI/Polish Pass - Context

**Gathered:** 2026-04-04
**Status:** In progress — partially fixed, needs completion
**Source:** In-game testing findings

## What's Already Fixed (this session)
- Name tag shortened to just name (no version/tier overflow)
- Equipment slot strict validation (mainhand: sword/axe/bow/halberd only, offhand: shield only)
- isMeleeWeapon tightened (no multi-tools)
- Level-up: happy villager particles + sound. Tier advance: firework + totem particles + fanfare
- Mining equips best pickaxe from backpack before starting
- /squire info shows ability list with unlock status
- Jade tooltip: removed duplicate version line
- Crest recall removed — /squire recall command only
- /squire godmode added (op-only, invulnerable + Champion)
- ChatHandler wired to COMBAT_START, WORK_TASK_COMPLETE, LEVEL_UP, TIER_ADVANCE events

## Remaining Issues

### Critical (blocks playable)
1. **Farming stops when no crops** — FarmingHandler returns to IDLE when scan finds nothing. Should loop with a rescan interval (e.g. every 100 ticks) while in farm mode, not go idle.
2. **Equipment cleanup** — items already in wrong slots (from before validation fix) need ejection. Add a cleanup pass in runFullEquipCheck that moves non-weapon items out of mainhand and non-shield items out of offhand.
3. **GUI layout cramped** — SquireScreen stats bar overflows. "Lv.0 Squire" + HP bar + "Satchel" + "Follow" + "0 XP" all overlap. Needs layout spacing fix.

### Important (blocks good)
4. **Task queue** — WRK-08 code exists (SquireBrain.dispatch()), but no /squire queue command to enqueue multiple tasks. Need at minimum: `/squire queue mine <pos>`, `/squire queue farm`, etc.
5. **Mount in-game test** — MNT-01/02/04 code exists (MountHandler, transitions wired). Never tested. `/squire mount` command exists.
6. **Radial menu actions** — R key opens menu (ownerUUID sync fixed). Need to verify all 4 wedges (Follow/Guard/Stay/Inventory) actually send correct packets and change squire state.

### Polish
7. **Bounding box renderer** — client-side wireframe for Crest-selected area. SquireAreaRenderer.java (new file in client/ package). Uses RenderLevelStageEvent.
8. **Backpack visual** — works but subtle with placeholder geometry. Better art needed.
9. **ARC-04** — structural split preference. SquireEntity ~650 lines. Skip — functional as-is.

## Files to Modify

- `brain/handler/FarmingHandler.java` — rescan loop instead of idle on empty scan
- `inventory/SquireEquipmentHelper.java` — cleanup pass for existing bad equipment
- `client/SquireScreen.java` — layout spacing for stats bar
- `command/SquireCommand.java` — /squire queue subcommands
- `client/SquireAreaRenderer.java` — NEW, bounding box wireframe
- `client/SquireClientEvents.java` — register area renderer

## Key Files Reference

- `.planning/REQUIREMENTS.md` — 67/72 checked, 5 remaining (WRK-08, MNT-01/02/04, ARC-04)
- `src/main/java/com/sjviklabs/squire/brain/SquireBrain.java` — FSM transition hub, all handlers wired
- `src/main/java/com/sjviklabs/squire/entity/SquireEntity.java` — 650+ lines, core entity
- `src/main/java/com/sjviklabs/squire/client/SquireScreen.java` — inventory GUI
- `src/main/java/com/sjviklabs/squire/brain/handler/FarmingHandler.java` — farming FSM

# Phase 9: UI/Polish Pass - Context

**Gathered:** 2026-04-04
**Status:** Ready for planning
**Source:** In-game testing findings

<domain>
## Phase Boundary

Fix all UI, equipment, and celebration issues found during in-game testing. This is a polish pass — no new features, just making existing systems work correctly and feel good.

</domain>

<decisions>
## Issues to Fix (from in-game testing screenshots)

### 1. GUI Title Overflow
- "Squire [Lv.0 Servant] v2.0.0" is too long for the inventory title bar
- Fix: shorten to "Squire - Lv.0 Servant" in GUI title
- Move version to Jade tooltip only (already there)
- Stats bar (HP, XP, mode) is cramped — needs layout spacing

### 2. Equipment Slot Validation
- Multi-tools (pickaxes, hoes, shovels) equip in weapon/shield slots
- ATM10 has paxels, multi-tools, modded tools that pass attack damage checks
- Fix: equipment slots need STRICT type validation
  - Mainhand: only SwordItem, AxeItem, BowItem, SquireHalberdItem
  - Offhand: only ShieldItem, SquireShieldItem
  - Armor: only ArmorItem matching the slot
  - Everything else goes to backpack — no exceptions
- Auto-equip (SquireEquipmentHelper) must enforce the same rules

### 3. Mining Speed
- Squire mines at base speed regardless of held tool
- Fix: MiningHandler should read the tool's mining speed modifier
- Should equip best pickaxe from inventory before mining (tool switching)

### 4. Tool Visibility on Avatar
- Tools don't render in squire's hand during work tasks
- Fix: before mining, swap mainhand to best pickaxe
- Before farming, swap to best hoe
- Before combat, swap to best weapon
- After task, swap back to combat weapon

### 5. Level-Up Celebrations
- No visual/audio feedback on level up
- Fix: firework particle burst on level up
- Bigger firework effect + sound on tier advance (Servant→Apprentice etc.)
- Chat notification already exists (ChatHandler wired)

### 6. Tier Ability Display
- No way to see what abilities unlock at each tier
- Fix: locked inventory rows show "Unlocks at Lv.X" tooltip
- /squire info should list available abilities at current tier

### 7. getDisplayName Too Long
- Name tag shows "Squire [Lv.0 Servant] v2.0.0" — too much text
- Fix: name tag shows "Squire" (or custom name) only
- Level/tier/version in Jade tooltip only

</decisions>

<canonical_refs>

## Files to Modify

- `client/SquireScreen.java` — GUI layout, title, spacing
- `inventory/SquireEquipmentHelper.java` — strict slot validation, tool switching
- `inventory/SquireMenu.java` — equipment slot type validation
- `brain/handler/MiningHandler.java` — tool speed modifier
- `brain/handler/FarmingHandler.java` — hoe equip before farming
- `progression/ProgressionHandler.java` — firework particles on level/tier
- `entity/SquireEntity.java` — getDisplayName(), name tag
- `command/SquireCommand.java` — /squire info ability list

</canonical_refs>

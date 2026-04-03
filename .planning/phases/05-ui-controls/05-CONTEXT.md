# Phase 5: UI and Controls - Context

**Gathered:** 2026-04-03
**Status:** Ready for planning
**Source:** Auto-generated (recommended defaults selected)

<domain>
## Phase Boundary

Player-facing controls: inventory screen (extending Phase 1's SquireMenu stub), radial menu (R key), /squire command tree, and item pickup with junk filtering. After this phase, the player can fully interact with and control the squire.

</domain>

<decisions>
## Implementation Decisions

### Inventory Screen

- Extend SquireMenu stub from Phase 1 — add 6 equipment slots at front (indices 0-5)
- SquireScreen renders equipment + backpack grid, player can drag items
- quickMoveStack handles all 4 transfer directions (player↔equip, player↔backpack, equip↔backpack)
- Equipment slots validate item type (armor-only, weapon-only)

### Radial Menu

- 4 wedges for Phase 5: Follow, Stay, Guard, Inventory
- Do NOT add stub/grayed wedges for unimplemented features — false UI expectations
- Pure client overlay — sends command packets to server via StreamCodec payloads
- R key binding registered in client events

### Commands

- /squire info — display squire stats (name, tier, HP, level)
- /squire mine and /squire place — register with stub bodies ("coming in a future update") for GUI-02
- /squire name <name> — rename squire
- Commands registered via RegisterCommandsEvent
- Dedicated server safety pass: no client class references in command code

### Item Pickup

- Squire picks up nearby dropped items within configurable range
- Junk filter: List<String> in SquireConfig, parsed lazily, items on list are ignored
- Default junk list: cobblestone, dirt, gravel, rotten_flesh, poisonous_potato
- INV-03 requires both pickup and filtering

### Claude's Discretion

- Inventory screen layout dimensions and texture
- Radial menu rendering (wedge shape, colors, icons)
- Exact junk filter matching logic (namespace:path vs tag-based)
- Command argument parsing details

</decisions>

<canonical_refs>

## Canonical References

### Phase 5 Research

- `.planning/phases/05-ui-controls/05-RESEARCH.md` — Menu slot layout, radial wedge count, server safety, junk filter pattern

### v0.5.0 Reference (read-only)

- `C:/Users/Steve/Projects/squire-mod/src/main/java/com/sjviklabs/squire/inventory/SquireScreen.java` — Inventory GUI rendering
- `C:/Users/Steve/Projects/squire-mod/src/main/java/com/sjviklabs/squire/client/SquireRadialScreen.java` — Radial menu overlay
- `C:/Users/Steve/Projects/squire-mod/src/main/java/com/sjviklabs/squire/command/SquireCommand.java` — Command tree
- `C:/Users/Steve/Projects/squire-mod/src/main/java/com/sjviklabs/squire/ai/handler/ItemHandler.java` — Item pickup logic

</canonical_refs>

<code_context>

## Existing Code Insights

### Reusable Assets

- SquireMenu.java (Phase 1) — AbstractContainerMenu stub with backpack slots, ready for equipment slot addition
- SquireRegistry.java — SQUIRE_MENU registered, keybind registration point
- SquireCommandPayload.java (Phase 2) — StreamCodec pattern for command packets

### Integration Points

- SquireMenu extends with 6 equipment slots at front
- SquireScreen registered against SquireRegistry.SQUIRE_MENU
- Keybind registered in ClientSetup / client events
- ItemHandler wires into SquireBrain as a handler with FSM transition

</code_context>

<specifics>
## Specific Ideas

- 4-wedge radial is the right call — only show what works NOW
- /squire mine as a stub is better than omitting it from the command tree entirely

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

_Phase: 05-ui-controls_
_Context gathered: 2026-04-03 via auto mode_

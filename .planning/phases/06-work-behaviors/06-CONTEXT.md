# Phase 6: Work Behaviors - Context

**Gathered:** 2026-04-03
**Status:** Ready for planning
**Source:** Auto-generated (recommended defaults selected)

<domain>
## Phase Boundary

Mining, farming, fishing, torch placement, block placement, container interaction, task queuing, and personality chat lines. After this phase, the squire is a working companion, not just a bodyguard.

</domain>

<decisions>
## Implementation Decisions

### Mining

- Single block mining on command (point at block, issue command)
- Area clear: multi-block mining in a defined region (Crest area selection)
- Mining speed modified by tier and tool held
- Mined items go to squire inventory via IItemHandler insertItem
- Chunk loading during area clear (ARC-09 already registered in Phase 1)

### Torch Placement

- Auto-place torches when ambient light at squire's position drops below configurable threshold (default: 7)
- Requires torches in squire inventory
- Check every 20 ticks while following
- Lowest complexity work behavior — good validation of handler pattern

### Farming

- Till dirt blocks adjacent to water
- Plant seeds from inventory
- Harvest mature crops, collect drops
- Replant automatically after harvest

### Fishing

- Simulated fishing — no actual bobber entity
- Use 1.21.1 loot table trigger to generate fish items
- Configurable fishing duration per catch
- Requires fishing rod in inventory (or offhand)

### Container Interaction

- Deposit: move excess inventory items into target chest
- Withdraw: take specific items from chest
- Pathfind to container, open it, transfer items

### Task Queue

- Queue data structure in SquireBrain
- Player issues multiple commands in sequence
- Squire executes in order, reports completion via chat
- Queue persists in NBT across server restarts

### Personality Chat Lines

- String pool per tier and state (idle, combat, level-up, new tier)
- ChatHandler fires on state transitions via SquireBrainEventBus
- Configurable chat frequency to avoid spam

### Claude's Discretion

- Mining animation/progress timing
- Farming crop detection logic
- Fishing loot table path verification
- Chat line content and frequency defaults

</decisions>

<canonical_refs>

## Canonical References

### Phase 6 Research

- `.planning/phases/06-work-behaviors/06-RESEARCH.md` — Work handler patterns, fishing loot table, IItemHandler pitfalls

### v0.5.0 Reference (read-only)

- `C:/Users/Steve/Projects/squire-mod/src/main/java/com/sjviklabs/squire/ai/handler/MiningHandler.java`
- `C:/Users/Steve/Projects/squire-mod/src/main/java/com/sjviklabs/squire/ai/handler/TorchHandler.java`
- `C:/Users/Steve/Projects/squire-mod/src/main/java/com/sjviklabs/squire/ai/handler/FarmingHandler.java`
- `C:/Users/Steve/Projects/squire-mod/src/main/java/com/sjviklabs/squire/ai/handler/FishingHandler.java`
- `C:/Users/Steve/Projects/squire-mod/src/main/java/com/sjviklabs/squire/ai/handler/PlacingHandler.java`
- `C:/Users/Steve/Projects/squire-mod/src/main/java/com/sjviklabs/squire/ai/handler/ChestHandler.java`
- `C:/Users/Steve/Projects/squire-mod/src/main/java/com/sjviklabs/squire/ai/handler/ChatHandler.java`
- `C:/Users/Steve/Projects/squire-mod/src/main/java/com/sjviklabs/squire/util/TaskQueue.java`

</canonical_refs>

<code_context>

## Existing Code Insights

### Reusable Assets

- SquireBrain + FSM (Phase 2) — work states already in SquireAIState enum (MINING, PLACING, FARMING, FISHING)
- SquireItemHandler (Phase 1) — IItemHandler for item storage
- SquireConfig (Phase 1) — mining/farming/fishing sections defined
- SquireBrainEventBus (Phase 2) — event dispatch for chat triggers
- SquireChunkLoader stub (Phase 1 ARC-09) — force-load during area clear

### Integration Points

- Work handlers register transitions in SquireBrain
- Task queue sits in SquireBrain, handlers pop tasks on completion
- ChatHandler subscribes to events via SquireBrainEventBus
- Commands from Phase 5 (/squire mine, /squire place) wire to handler methods

</code_context>

<specifics>
## Specific Ideas

- Torch placement first — simplest behavior, validates the work handler pattern
- All work handlers must use IItemHandler extractItem/insertItem — no direct mutation

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

_Phase: 06-work-behaviors_
_Context gathered: 2026-04-03 via auto mode_

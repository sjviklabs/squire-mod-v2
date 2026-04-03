# Phase 2: Brain, FSM, and Follow - Context

**Gathered:** 2026-04-02
**Status:** Ready for planning
**Source:** Auto-generated (recommended defaults selected)

<domain>
## Phase Boundary

Build the tick-rate state machine (FSM), the internal event bus between handlers, and the first real behavior: follow the player without ever teleporting. Also implements sit/stay toggle and StreamCodec network payloads. After this phase, the squire walks with the player through any terrain.

</domain>

<decisions>
## Implementation Decisions

### FSM Architecture

- Port TickRateStateMachine engine verbatim from v0.5.0 — it's proven and correct
- SquireAIState enum with 23 states, organized by priority layers (survival 1-9, combat 10-19, mount 20-29, follow 30-39, work 40-49, utility 50+)
- AITransition pairs: condition lambda + action lambda + tick rate
- FSM replaces GoalSelector completely — do NOT register any vanilla goals

### Internal Event Bus

- New in v2: SquireBrainEventBus using EnumMap<SquireEvent, List<Consumer<SquireEntity>>>
- Phase 2 defines 3 events: SIT_TOGGLE, COMBAT_START, COMBAT_END
- Handlers subscribe in their constructor, fire events through the bus — no direct handler-to-handler calls
- Keep it simple: synchronous dispatch, no priority ordering needed

### Follow Behavior

- No teleportation under any circumstances — remove tryTeleportToOwner completely
- Sprint when player is 12+ blocks away, walk when within 6 blocks, stop within 2 blocks (configurable)
- Stuck detection: if no progress toward target for 3 seconds, attempt jump boost + path replan
- Water traversal: setCanFloat(true) as baseline. If squire oscillates at banks, escalate to AmphibiousNodeEvaluator
- Follow distance values from v0.5.0 config as starting point

### Sit/Stay Toggle

- Toggle via command payload (CMD_STAY / CMD_FOLLOW)
- When sitting: squire stops all behavior, holds position, ignores follow distance
- When released: resumes following immediately

### Network Payloads

- Port StreamCodec.composite() pattern from v0.5.0 — already correct
- Phase 2 only activates CMD_FOLLOW and CMD_STAY payloads
- Additional command types registered but inactive until later phases

### Claude's Discretion

- Exact stuck detection algorithm (timeout values, retry limits)
- Water pathfinding depth threshold for evaluator switch
- FSM tick rate values per transition (reference v0.5.0 but tune conservatively for ATM10)
- SquireBrain initialization order

</decisions>

<canonical_refs>

## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### v0.5.0 Reference (read-only — for game logic constants and proven patterns)

- `C:/Users/Steve/Projects/squire-mod/src/main/java/com/sjviklabs/squire/ai/statemachine/TickRateStateMachine.java` — FSM engine to port
- `C:/Users/Steve/Projects/squire-mod/src/main/java/com/sjviklabs/squire/ai/statemachine/SquireAI.java` — Transition registry pattern
- `C:/Users/Steve/Projects/squire-mod/src/main/java/com/sjviklabs/squire/ai/statemachine/SquireAIState.java` — States enum (23 states)
- `C:/Users/Steve/Projects/squire-mod/src/main/java/com/sjviklabs/squire/ai/handler/FollowHandler.java` — Follow logic, distance thresholds, sprint logic
- `C:/Users/Steve/Projects/squire-mod/src/main/java/com/sjviklabs/squire/ai/handler/SurvivalHandler.java` — Eating/healing logic
- `C:/Users/Steve/Projects/squire-mod/src/main/java/com/sjviklabs/squire/network/SquireCommandPayload.java` — StreamCodec pattern
- `C:/Users/Steve/Projects/squire-mod/src/main/java/com/sjviklabs/squire/network/SquireModePayload.java` — Mode sync pattern

### Phase 2 Research

- `.planning/phases/02-brain-fsm-follow/02-RESEARCH.md` — API patterns, IItemHandler contract for SurvivalHandler, stuck recovery design

### Architecture

- `.planning/research/ARCHITECTURE.md` — Component boundaries, SquireEntity/SquireBrain split
- `.planning/research/PITFALLS.md` — SynchedEntityData class mismatch, FSM tick budget in ATM10

</canonical_refs>

<code_context>

## Existing Code Insights

### Reusable Assets

- `SquireEntity.java` — created in Phase 1, contains SquireBrain stub (lazy init in aiStep)
- `SquireRegistry.java` — registration hub for network payloads
- `SquireConfig.java` — follow distance, sprint threshold values already defined (if 01-04 complete)

### Established Patterns

- DeferredRegister pattern in SquireRegistry — follow for payload registration
- SynchedEntityData pattern in SquireEntity — SQUIRE_MODE field for sit/follow state sync

### Integration Points

- SquireEntity.aiStep() → SquireBrain.tick() — FSM ticks here
- SquireEntity.getNavigation() → PathfinderMob navigation for follow
- SquireConfig values → FollowHandler distance thresholds

</code_context>

<specifics>
## Specific Ideas

- The v0.5.0 TickRateStateMachine is a clean, proven engine — port it rather than redesigning
- SurvivalHandler must use IItemHandler.extractItem() not getStackInSlot().shrink() — IItemHandler contract violation causes inventory corruption
- Remove tryTeleportToOwner completely, not just disable it — no dead code paths for teleportation

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

_Phase: 02-brain-fsm-follow_
_Context gathered: 2026-04-02 via auto mode_

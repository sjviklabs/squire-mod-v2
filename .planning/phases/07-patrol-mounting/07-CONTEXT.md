# Phase 7: Patrol and Mounting - Context

**Gathered:** 2026-04-03
**Status:** Ready for planning
**Source:** Auto-generated (recommended defaults selected)

<domain>
## Phase Boundary

Signpost waypoint blocks, patrol route system, horse mounting/riding, and mounted combat. After this phase, the squire handles advanced autonomous tasks — patrolling defined routes and fighting from horseback.

</domain>

<decisions>
## Implementation Decisions

### Signpost Block

- Placeable block with BlockEntity storing route name + waypoint order
- Player right-clicks to assign route name (chat input or GUI)
- Two-click linking: first click sets "pending link", second signpost completes the route connection
- Clean up PENDING_LINKS map on PlayerLoggedOutEvent

### Patrol Behavior

- Squire walks between signpost waypoints in defined order
- Resumes from last waypoint index after combat (preserve index via COMBAT_START/COMBAT_END events)
- Patrol is foot-only — force dismount on PATROL_WALK entry
- Post-combat resume: re-navigate to current waypoint, continue from there
- Stuck recovery same as FollowHandler (path replan + jump boost)

### Horse Mounting

- Mount nearby horse on command (scan for saddled horse within range)
- Horse UUID persists in NBT — squire remounts same horse after restart
- Horse driving via `horse.move(MoverType.SELF, vec)` — vanilla `travelRidden()` is gated on Player passenger
- Follow player while mounted at horse speed
- Dismount on command or when horse dies

### Mounted Combat

- Squire engages mobs while mounted using CombatHandler
- Melee attack reach adjusted for mounted height
- Dismount if horse HP drops below threshold (configurable)
- No ranged combat while mounted (bow use requires dismount)

### Claude's Discretion

- Signpost GUI vs chat-based route naming
- Patrol wait time at each waypoint
- Horse scan range for mount command
- Mounted melee reach adjustment values

</decisions>

<canonical_refs>

## Canonical References

### Phase 7 Research

- `.planning/phases/07-patrol-mounting/07-RESEARCH.md` — Mounted navigation root cause (LivingEntity line 2816), horse.move() workaround, BlockEntity registration order, NbtUtils signatures

### v0.5.0 Reference (read-only)

- `C:/Users/Steve/Projects/squire-mod/src/main/java/com/sjviklabs/squire/block/SignpostBlock.java`
- `C:/Users/Steve/Projects/squire-mod/src/main/java/com/sjviklabs/squire/block/SignpostBlockEntity.java`
- `C:/Users/Steve/Projects/squire-mod/src/main/java/com/sjviklabs/squire/ai/handler/PatrolHandler.java`
- `C:/Users/Steve/Projects/squire-mod/src/main/java/com/sjviklabs/squire/ai/handler/MountHandler.java`

</canonical_refs>

<code_context>

## Existing Code Insights

### Reusable Assets

- SquireBrain + FSM — PATROL_WALK, MOUNTED_IDLE, MOUNTED_FOLLOW, MOUNTED_COMBAT states in enum
- SquireBrainEventBus — COMBAT_START/COMBAT_END for patrol resume
- CombatHandler (Phase 4) — reused for mounted combat with reach adjustment
- SquireRegistry — block + block entity registration points

### Integration Points

- SignpostBlock registered in SquireRegistry
- PatrolHandler registers transitions in SquireBrain
- MountHandler stores horse UUID in SquireEntity NBT
- Mounted combat delegates to existing CombatHandler with modified reach

</code_context>

<specifics>
## Specific Ideas

- Horse driving via move() is the only viable approach — vanilla gates travelRidden() on Player
- v0.5.0 MountHandler.driveHorseToward() is the proven solution
- BlockEntity registration MUST follow Block registration in SquireMod constructor

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

_Phase: 07-patrol-mounting_
_Context gathered: 2026-04-03 via auto mode_

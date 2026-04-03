# Phase 8: Compatibility and Polish - Context

**Gathered:** 2026-04-03
**Status:** Ready for planning
**Source:** Auto-generated (recommended defaults selected)

<domain>
## Phase Boundary

MineColonies compatibility shim, Jade/WAILA tooltip overlay, Curios/Accessories equipment slots, and final polish pass (Oculus validation, config verification, dedicated server smoke test). After this phase, the squire coexists cleanly in ATM10's 449-mod environment.

</domain>

<decisions>
## Implementation Decisions

### MineColonies Compatibility

- No whitelist API exists — use v0.5.0's package-scan approach (isFromPackage() walking class hierarchy)
- Zero compile dependency on MineColonies — no imports needed
- MobCategory.MISC already set (Phase 1) — prevents creature-category handling
- Guard against pathfinding disruption: squire gives way to MineColonies citizens (3-second bump delay avoidance)
- All compat code guarded by ModList.get().isLoaded("minecolonies")

### Jade/WAILA Overlay

- Real @WailaPlugin annotated class with IEntityComponentProvider
- Tooltip shows: name, tier, HP (color-coded), current task/state
- Implement appendTooltip(ITooltip, EntityAccessor, IPluginConfig)
- Verify current task is in SynchedEntityData before writing tooltip code
- Guarded by ModList.get().isLoaded("jade")

### Curios/Accessories

- Slot registration via datapack JSON (not IMC SlotTypeMessage — removed in Curios 5.x)
- Two JSON files in builtin datapack: slot type definition + entity assignment
- Attribute modifiers use ResourceLocation identifiers (not UUIDs — Curios 9.2.0+ breaking change)
- CuriosCompat provides ICurioItem implementation for squire accessories
- Guarded by ModList.get().isLoaded("curios")
- Mod MUST function fully without Curios installed

### Polish Pass

- Oculus shader final validation in ATM10
- Config validation: all 53+ entries have correct defaults, no correction loop
- Dedicated server smoke test: mod loads without crash, no client class references in server paths
- Full "looks done but isn't" checklist

### Claude's Discretion

- Jade tooltip formatting and colors
- Curios slot icon (use default initially)
- Exact MineColonies package-scan logic depth
- Polish checklist items beyond the documented ones

</decisions>

<canonical_refs>

## Canonical References

### Phase 8 Research

- `.planning/phases/08-compatibility-polish/08-RESEARCH.md` — No MineColonies whitelist API, Jade plugin pattern, Curios JSON format, attribute modifier API change

### v0.5.0 Reference (read-only)

- `C:/Users/Steve/Projects/squire-mod/src/main/java/com/sjviklabs/squire/compat/MineColoniesCompat.java` — Package-scan approach
- `C:/Users/Steve/Projects/squire-mod/src/main/java/com/sjviklabs/squire/compat/JadeCompat.java` — Basic tooltip (needs upgrade to real plugin)

### Stack

- `.planning/research/STACK.md` — Jade 15.10.4, Curios 9.5.1, MineColonies API coordinates (broken — no Jfrog 1.21.1 artifacts)

</canonical_refs>

<code_context>

## Existing Code Insights

### Reusable Assets

- SquireEntity SynchedEntityData — tier, mode, level already synced for tooltip
- SquireRegistry — capability registration point for Curios
- Builtin datapack (Phase 1 ARC-07) — Curios JSON files go here

### Integration Points

- MineColoniesCompat registered in SquireMod if mod loaded
- JadeCompat registered via @WailaPlugin annotation (auto-discovered by Jade)
- CuriosCompat registered via RegisterCapabilitiesEvent if Curios loaded
- All three compat classes in dedicated `compat/` package

</code_context>

<specifics>
## Specific Ideas

- MineColonies: zero-import approach is the safest — no compile dependency that can break
- Jade: v0.5.0's tooltip was never a real plugin — this is a proper implementation
- Curios: builtin datapack for JSON files (same as progression data) — avoids datapack desync bug

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

_Phase: 08-compatibility-polish_
_Context gathered: 2026-04-03 via auto mode_

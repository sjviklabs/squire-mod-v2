# Phase 3: Rendering - Context

**Gathered:** 2026-04-02
**Status:** Ready for planning
**Source:** Auto-generated (recommended defaults selected)

<domain>
## Phase Boundary

Geckolib entity model with animations, skin variants, armor layers, backpack visual, tiered textures, personality chat stubs, and naming. After this phase, the squire looks like a squire and is visually correct in ATM10 with Oculus shaders.

</domain>

<decisions>
## Implementation Decisions

### Geckolib Setup

- Geckolib 4.8.4 (update from 4.8.3 in build.gradle; fall back to 4.8.3 if API issues)
- AnimatableInstanceCache MUST be final at field declaration — #1 crash cause
- GeoEntityRenderer + GeoModel pattern from official wiki
- Do NOT add geckoanimfix — deprecated, Geckolib 4.7+ handles Oculus internally

### Model Approach

- Blockbench .geo.json model — functional placeholder with correct bone structure
- Bone naming follows Geckolib conventions for equipment layers
- Include bones-without-cubes for armor attachment points
- Male/female variants as separate texture files on same geometry (slim model uses SynchedEntityData flag)

### Rendering Layers

- ItemArmorGeoLayer subclass for 4-piece armor set (replaces v0.5.0's 400-line SquireTieredArmorLayer)
- GeoRenderLayer for backpack visual (bones in geo.json, not raw vertex logic)
- Tier-based texture swapping for armor (5 texture variants per piece)
- Backpack bone scale driven by SquireTier from SynchedEntityData

### Animation Controllers

- Idle, walk, sprint, attack animations via animation controllers
- triggerAnim() for one-shot animations (attack, level-up)
- Animation state driven by SynchedEntityData values only — never read from SquireBrain (client/server split)

### Personality & Naming

- RND-03: Custom naming via name tag interaction or /squire name command
- RND-04: Chat line scaffolding — string pool per tier/state, ChatHandler stub for Phase 6 to fill in
- Oculus/ATM10 shader validation pass as final task

### Claude's Discretion

- Exact bone hierarchy in .geo.json
- Animation timing and easing curves
- Placeholder model geometry detail level
- Health bar rendering approach (port from v0.5.0 or redesign)

</decisions>

<canonical_refs>

## Canonical References

### Geckolib

- `.planning/phases/03-rendering/03-RESEARCH.md` — Geckolib 4.8.4 patterns, ItemArmorGeoLayer, bone naming, Oculus compat

### v0.5.0 Reference (read-only — for rendering logic concepts, NOT code structure)

- `C:/Users/Steve/Projects/squire-mod/src/main/java/com/sjviklabs/squire/client/SquireRenderer.java` — Health bar, texture selection logic
- `C:/Users/Steve/Projects/squire-mod/src/main/java/com/sjviklabs/squire/client/SquireTieredArmorLayer.java` — Tier texture mapping concept
- `C:/Users/Steve/Projects/squire-mod/src/main/java/com/sjviklabs/squire/client/BackpackLayer.java` — Backpack scaling concept

### Stack

- `.planning/research/STACK.md` — Geckolib version, dependency declaration

</canonical_refs>

<code_context>

## Existing Code Insights

### Reusable Assets

- SquireEntity.java — SynchedEntityData fields for rendering (SQUIRE_MODE, tier, slim model flag)
- SquireTier.java — tier enum for texture/backpack selection
- SquireRegistry.java — entity renderer registration point

### Integration Points

- ClientSetup or client-side mod bus — renderer registration
- SynchedEntityData — all render-relevant state must flow through this

</code_context>

<specifics>
## Specific Ideas

- Placeholder model is fine for Phase 3 — correct bones matter more than pretty geometry
- v0.5.0's renderer code compresses significantly with Geckolib — most of the complexity disappears

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

_Phase: 03-rendering_
_Context gathered: 2026-04-02 via auto mode_

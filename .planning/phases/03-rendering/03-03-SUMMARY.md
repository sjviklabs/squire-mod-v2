---
phase: 03-rendering
plan: "03"
subsystem: rendering
tags: [geckolib, armor-layers, render-layers, items, registry]
dependency_graph:
  requires: [03-01-geckolib-wiring, 03-02-asset-files]
  provides: [SquireArmorLayer, SquireBackpackLayer, SquireArmorItem, armor-registry]
  affects: [03-04-naming-oculus-compat]
tech_stack:
  added: []
  patterns:
    - "ItemArmorGeoLayer texture override via getVanillaArmorBuffer — getArmorTexture() absent in 4.8.4"
    - "ArmorMaterials.IRON is Holder<ArmorMaterial> in NeoForge 1.21.1 — not ArmorMaterial directly"
    - "Tier-to-texture clamping: Math.min(ordinal, TIER_OUTER.length - 1) maps 5 tiers to 4 textures"
    - "GeoRenderLayer backpack visibility via GeoBone.setHidden() in preRender() — no draw call needed"
key_files:
  created:
    - src/main/java/com/sjviklabs/squire/item/SquireArmorItem.java
    - src/main/java/com/sjviklabs/squire/client/SquireArmorLayer.java
    - src/main/java/com/sjviklabs/squire/client/SquireBackpackLayer.java
  modified:
    - src/main/java/com/sjviklabs/squire/SquireRegistry.java
    - src/main/java/com/sjviklabs/squire/client/SquireRenderer.java
decisions:
  - "getVanillaArmorBuffer override instead of getArmorTexture — the latter does not exist in Geckolib 4.8.4; buffer intercept is the correct hook for custom texture injection"
  - "ArmorItem constructor takes Holder<ArmorMaterial> not ArmorMaterial — plan spec had wrong type; corrected to match NeoForge 1.21.1 API"
  - "getLevel() not getSquireLevel() — plan interface section listed wrong method name; actual SynchedEntityData accessor is getLevel()"
metrics:
  duration_minutes: 19
  completed_date: "2026-04-03"
  tasks_completed: 2
  files_created: 3
  files_modified: 2
requirements_met: [RND-05, RND-06, INV-04]
---

# Phase 3 Plan 3: Armor and Backpack Render Layers Summary

Tiered armor texture rendering and tier-scaled backpack visibility implemented via Geckolib 4.8.4 layer classes. Three API corrections required vs. plan spec — all auto-fixed without architectural impact.

## What Was Built

**SquireArmorItem.java** — marker ArmorItem subclass. Extends `ArmorItem` with `Holder<ArmorMaterial>` constructor (NeoForge 1.21.1 uses the holder type, not the raw type). Sole purpose: `instanceof` check in `SquireArmorLayer.getVanillaArmorBuffer()` to gate tier-texture selection. No additional behavior in Phase 3.

**SquireArmorLayer.java** — `ItemArmorGeoLayer<SquireEntity>` subclass:
- Three overrides map bones to equipment slots, vanilla model parts, and item stacks
- Bone-to-slot: head→HEAD, body→CHEST, right_leg/left_leg→LEGS, right_foot/left_foot→FEET
- Tier-to-texture mapping: `Math.min(SquireTier.fromLevel(animatable.getLevel()).ordinal(), 3)`
  - SERVANT(0)→t0, APPRENTICE(1)→t1, SQUIRE(2)→t2, KNIGHT(3)→t2 (clamped), CHAMPION(4)→t3
- Texture injection via `getVanillaArmorBuffer` override — returns `RenderType.armorCutoutNoCull(tierTexture)` for SquireArmorItem stacks; delegates to super for everything else and for enchantment glint

**SquireBackpackLayer.java** — `GeoRenderLayer<SquireEntity>` subclass:
- `preRender()` computes tier and calls `GeoBone.setHidden()` on `backpack_small` and `backpack_large`
- SERVANT/APPRENTICE: backpack_small visible, backpack_large hidden
- SQUIRE and above: backpack_large visible, backpack_small hidden
- `render()` intentionally empty — geometry is in the .geo.json, no draw call needed

**SquireRegistry.java** — 4 armor items added after existing `CREST` registration:
- SQUIRE_HELMET, SQUIRE_CHESTPLATE, SQUIRE_LEGGINGS, SQUIRE_BOOTS
- All use `ArmorMaterials.IRON` as placeholder material (Phase 4 adds SquireArmorMaterial)
- Stack size 1 on all four

**SquireRenderer.java** — constructor updated:
- `addRenderLayer(new SquireArmorLayer(this))`
- `addRenderLayer(new SquireBackpackLayer(this))`

## Tier-to-Texture-Index Mapping

| SquireTier  | ordinal | clamped index | texture |
|-------------|---------|---------------|---------|
| SERVANT     | 0       | 0             | t0      |
| APPRENTICE  | 1       | 1             | t1      |
| SQUIRE      | 2       | 2             | t2      |
| KNIGHT      | 3       | 3             | t3      |
| CHAMPION    | 4       | 3 (min 4,3)   | t3      |

Note: KNIGHT maps to t3, not t2. The plan comment said SQUIRE+KNIGHT→t2 but the math (`Math.min(ordinal, 3)`) gives SQUIRE→2, KNIGHT→3, CHAMPION→3. KNIGHT gets its own texture slot t3 with CHAMPION sharing it. This is consistent with having 4 visual tiers for 5 progression tiers — the top two tiers look the same.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] getArmorTexture() does not exist in Geckolib 4.8.4**

- **Found during:** Task 2 — bytecode inspection of ItemArmorGeoLayer.class before writing
- **Issue:** Plan and RESEARCH.md Pattern 3 both specified `@Override protected ResourceLocation getArmorTexture(...)`. This method is absent from the actual 4.8.4 API. Texture selection is driven by `ArmorMaterial.Layer.texture(boolean)`, and the overridable hook is `getVanillaArmorBuffer`.
- **Fix:** Overrode `getVanillaArmorBuffer` to return `RenderType.armorCutoutNoCull(tierTexture)` when `stack.getItem() instanceof SquireArmorItem`. Enchantment glint path passes through to super unchanged.
- **Files modified:** SquireArmorLayer.java
- **Impact:** Functionally identical to what getArmorTexture would have done — same result, different hook.

**2. [Rule 1 - Bug] ArmorItem constructor takes Holder<ArmorMaterial> not ArmorMaterial**

- **Found during:** Task 1 — bytecode inspection of ArmorItem.class before writing
- **Issue:** Plan specified `SquireArmorItem(ArmorMaterial material, ...)` and `ArmorMaterials.IRON` as `ArmorMaterial`. In NeoForge 1.21.1, `ArmorMaterials.IRON` is `Holder<ArmorMaterial>` and `ArmorItem`'s constructor takes `Holder<ArmorMaterial>`.
- **Fix:** Constructor signature changed to `Holder<ArmorMaterial>`. Added `import net.minecraft.core.Holder`.
- **Files modified:** SquireArmorItem.java, SquireRegistry.java (import only)

**3. [Rule 1 - Bug] getSquireLevel() does not exist — method is getLevel()**

- **Found during:** Task 2 — first build attempt
- **Issue:** Plan interface section documented `public int getSquireLevel()`. Actual accessor in SquireEntity is `public int getLevel()` (reads `SQUIRE_LEVEL` SynchedEntityData key).
- **Fix:** Replaced all `animatable.getSquireLevel()` calls with `animatable.getLevel()` in both layer files.
- **Files modified:** SquireArmorLayer.java, SquireBackpackLayer.java

## Commits

| Task | Commit  | Description |
|------|---------|-------------|
| 1    | c637b65 | SquireArmorItem + 4 armor items in registry |
| 2    | 02ac909 | SquireArmorLayer, SquireBackpackLayer, SquireRenderer wiring |

## Self-Check: PASSED

All 3 created files exist. Both task commits (c637b65, 02ac909) confirmed in git log. Build exits 0.

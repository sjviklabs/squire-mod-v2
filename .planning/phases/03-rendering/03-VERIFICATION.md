---
phase: 03-rendering
verified: 2026-04-03T16:30:00Z
status: human_needed
score: 6/7 must-haves verified (7th deferred to human testing per scope note)
re_verification: false
human_verification:
  - test: "Launch ATM10 with Oculus/NeOculus shaders active and spawn a squire via the Crest item"
    expected: "Squire is visible (not invisible), plays idle animation when standing still, plays walk/sprint animations while moving. Model is not in T-pose with shaders active or inactive."
    why_human: "Geckolib/Oculus shader compatibility cannot be verified programmatically — requires in-game rendering with shader pipeline active. This was the blocking checkpoint in plan 03-04 and was auto-approved without actual testing."
  - test: "Right-click a squire with a named name tag in survival mode"
    expected: "Name appears above the squire, name tag item is consumed. Name persists after relog."
    why_human: "Name tag item interaction requires a running game session to verify the DataComponents.CUSTOM_NAME read and setCustomName persistence chain."
  - test: "Observe backpack on a Servant-tier squire vs. a Squire-tier squire"
    expected: "Servant shows small backpack. Squire and above show large backpack. Backpacks change with tier."
    why_human: "GeoBone.setHidden() visibility changes require in-game rendering to confirm the hide/show toggle actually applies."
  - test: "Equip a squire armor item onto the squire and view with shaders active and inactive"
    expected: "Tier-appropriate placeholder color appears on the armor slot. Correct texture for tier index (t0 at SERVANT, t1 at APPRENTICE, etc.)."
    why_human: "getVanillaArmorBuffer override with RenderType.armorCutoutNoCull() requires a running render pipeline to verify texture injection."
---

# Phase 3: Rendering Verification Report

**Phase Goal:** The squire is visually correct with animations in ATM10 — including with Oculus shaders active
**Verified:** 2026-04-03T16:30:00Z
**Status:** human_needed
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | SquireEntity implements GeoEntity with final AnimatableInstanceCache field | VERIFIED | `SquireEntity.java:65` — `implements GeoEntity`. `line 90` — `private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this)` declared at field site, not in constructor |
| 2 | SquireRenderer extends GeoEntityRenderer and routes male/female model from isSlimModel() | VERIFIED | `SquireModel.java:21` — `extends GeoModel<SquireEntity>`. `getModelResource()` and `getTextureResource()` both branch on `entity.isSlimModel()`. Actual class is `GeoModel` not `GeoEntityModel` — 4.8.4 API difference, correctly applied. |
| 3 | Renderer registered on client event bus — squire entity renders in-world | VERIFIED | `SquireClientEvents.java:16-22` — `@EventBusSubscriber(modid, Bus.MOD, Dist.CLIENT)` with `registerEntityRenderer(SquireRegistry.SQUIRE.get(), SquireRenderer::new)` |
| 4 | Male/female variant driven by SLIM_MODEL SynchedEntityData only | VERIFIED | `SquireModel.java:33-40` — both `getModelResource()` and `getTextureResource()` call only `entity.isSlimModel()` which reads `SLIM_MODEL` SynchedEntityData at `SquireEntity.java:168-170`. No server-side state accessed. |
| 5 | No server-side state in SquireModel or SquireRenderer | VERIFIED | Grep of client/ package — no imports of SquireBrain, SquireItemHandler, or activityLog. Layers use `animatable.getLevel()` and `animatable.isSlimModel()` only (both SynchedEntityData accessors). |
| 6 | Geo/animation assets exist at declared resource paths with correct bone contract | VERIFIED | All 10 required bones present in squire_male.geo.json and squire_female.geo.json. Female uses slim arms (3px width vs 4px male). All 4 animation names match RawAnimation statics: `animation.squire.idle` (loop), `animation.squire.walk` (loop), `animation.squire.sprint` (loop), `animation.squire.attack` (loop: false). |
| 7 | Squire renders correctly with Oculus shaders active in ATM10 | HUMAN NEEDED | Oculus/shader validation is an in-game test. Plan 03-04 Task 2 was a blocking human checkpoint that was auto-approved without execution. Cannot verify programmatically. |

**Score:** 6/7 truths verified programmatically (7th deferred to human per scope)

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `src/main/java/com/sjviklabs/squire/client/SquireModel.java` | GeoModel routing male/female assets | VERIFIED | Extends `GeoModel<SquireEntity>`, all 3 abstract methods implemented |
| `src/main/java/com/sjviklabs/squire/client/SquireRenderer.java` | GeoEntityRenderer with both layers | VERIFIED | Both `addRenderLayer` calls present in constructor |
| `src/main/java/com/sjviklabs/squire/client/SquireClientEvents.java` | Renderer registration on client bus | VERIFIED | Correct `@EventBusSubscriber` annotation and `registerEntityRenderer` call |
| `src/main/java/com/sjviklabs/squire/client/SquireArmorLayer.java` | ItemArmorGeoLayer with tier textures | VERIFIED | Extends `ItemArmorGeoLayer`, `getVanillaArmorBuffer` override with `Math.min(ordinal, 3)` clamping and `instanceof SquireArmorItem` check |
| `src/main/java/com/sjviklabs/squire/client/SquireBackpackLayer.java` | GeoRenderLayer backpack visibility | VERIFIED | `preRender()` uses `GeoBone.setHidden()` driven by `SquireTier.SQUIRE.ordinal()` threshold |
| `src/main/java/com/sjviklabs/squire/item/SquireArmorItem.java` | Marker ArmorItem subclass | VERIFIED | Extends `ArmorItem` with `Holder<ArmorMaterial>` constructor (correct NeoForge 1.21.1 API) |
| `src/main/java/com/sjviklabs/squire/entity/ChatHandler.java` | Chat stub with 4-event, 5-tier string pools | VERIFIED | In `entity/` package, no client imports, `sendLine()` uses `squire.getLevel()` (correct accessor) |
| `src/main/resources/assets/squire/geo/squire_male.geo.json` | Wide-body model, all 10 bones | VERIFIED | identifier `geometry.squire_male`, 11 bones (root+10), all armor-slot bones have cubes |
| `src/main/resources/assets/squire/geo/squire_female.geo.json` | Slim-body model, all 10 bones | VERIFIED | identifier `geometry.squire_female`, slim arms (3px), origins adjusted |
| `src/main/resources/assets/squire/animations/squire.animation.json` | 4 animations matching RawAnimation statics | VERIFIED | All 4 keys present, correct loop values, format_version 1.8.0 |
| Placeholder textures (10 PNG files) | Valid PNG at declared paths | VERIFIED | All 10 exist: 2 entity skins + 4 outer armor + 4 inner armor |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `SquireEntity` | `AnimatableInstanceCache` | `private final` field at declaration | WIRED | `SquireEntity.java:90` — field declared and initialized correctly |
| `SquireClientEvents` | `SquireRegistry.SQUIRE` | `event.registerEntityRenderer` | WIRED | `SquireClientEvents.java:21` — `SquireRegistry.SQUIRE.get()` |
| `SquireModel.getModelResource` | Male/female geo.json | `entity.isSlimModel()` | WIRED | Routes to FEMALE_MODEL/MALE_MODEL, paths match actual files |
| `SquireArmorLayer.getVanillaArmorBuffer` | TIER_OUTER/TIER_INNER arrays | `SquireTier.fromLevel(animatable.getLevel()).ordinal()` | WIRED | `SquireArmorLayer.java:117-119` — clamping present, method uses correct accessor |
| `SquireRenderer constructor` | `SquireArmorLayer + SquireBackpackLayer` | `addRenderLayer()` | WIRED | `SquireRenderer.java:28-29` — both calls present |
| `SquireBackpackLayer.preRender` | backpack_small/backpack_large bones | `bakedModel.getBone().ifPresent(bone.setHidden())` | WIRED | `SquireBackpackLayer.java:40-41` — Optional ifPresent pattern used correctly |
| `SquireEntity.mobInteract` | `setCustomName() / setCustomNameVisible()` | `instanceof NameTagItem` | WIRED | `SquireEntity.java:299-309` — server-side check, DataComponents.CUSTOM_NAME read |
| `ChatHandler.sendLine` | `SquireEntity.getLevel()` | tier index derivation | WIRED | `ChatHandler.java:82` — `squire.getLevel()`, correct accessor |

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|-------------|-------------|--------|----------|
| RND-01 | 03-01 | Geckolib-based entity model with animations | SATISFIED | SquireEntity implements GeoEntity, SquireModel/SquireRenderer exist and are registered |
| RND-02 | 03-01, 03-02 | Male/female skin variants (slim/wide) | SATISFIED | SquireModel routes by `isSlimModel()`, both geo.json files have correct arm widths |
| RND-03 | 03-04 | Custom naming via name tag | SATISFIED (code) / HUMAN for runtime | Name tag handling in mobInteract() verified in code. Runtime name persistence requires human test. |
| RND-04 | 03-04 | Personality chat lines (idle, combat, level-up, new tier) | SATISFIED | ChatHandler.java — all 4 ChatEvent values, all 5 tiers, sendLine() callable from server code |
| RND-05 | 03-03 | Backpack visual layer reflecting inventory tier | SATISFIED (code) / HUMAN for runtime | SquireBackpackLayer logic verified. Actual visibility requires in-game test. |
| RND-06 | 03-03 | Tiered armor texture rendering | SATISFIED (code) / HUMAN for runtime | SquireArmorLayer getVanillaArmorBuffer verified. Actual texture injection requires in-game test. |
| INV-04 | 03-03 | Custom 4-piece armor set with tiered textures | SATISFIED | SQUIRE_HELMET, SQUIRE_CHESTPLATE, SQUIRE_LEGGINGS, SQUIRE_BOOTS registered in SquireRegistry.java |

No orphaned requirements found. All 7 phase-3 requirement IDs from plan frontmatter are covered.

### Notable API Deviations (not gaps — all correctly handled)

The following deviations from the plan specs were identified and auto-fixed during execution. They are documented here for traceability.

| Deviation | Plan Expected | Actual 4.8.4 API | Fix Applied |
|-----------|---------------|------------------|-------------|
| Model base class | `GeoEntityModel<T>` | `GeoModel<T>` | Correct class used throughout |
| LoopType import | `software.bernie.geckolib.animation.LoopType` | `Animation.LoopType` (nested) | `Animation.LoopType.PLAY_ONCE` used |
| Texture method name | `getTextureLocation()` | `getTextureResource()` | Correct override used |
| Armor texture hook | `getArmorTexture()` | `getVanillaArmorBuffer()` | Correct override used |
| ArmorItem constructor | `ArmorMaterial` | `Holder<ArmorMaterial>` | Correct type used |
| Level accessor | `getSquireLevel()` | `getLevel()` | Correct accessor used in all 3 affected files |

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| `SquireRenderer.java` | 37 | `// Health bar: port from v0.5.0 ... deferred to 03-04` | Info | Intentional deferral — comment is accurate, renderNameTag calls super correctly, no broken behavior |
| `ChatHandler.java` | 81 | `// TODO Phase 6: replace with event bus publish` | Info | Intentional scaffold comment — current implementation is functional for Phase 6 to hook into |

No blocker anti-patterns. No empty implementations. No placeholder return values.

### Human Verification Required

#### 1. Oculus Shader Rendering

**Test:** Build the mod (`./gradlew build`), drop the JAR into ATM10, launch with Oculus/NeOculus shaders active, spawn a squire via Crest item.

**Expected:** Squire is visible (not invisible), idle animation plays when standing still (subtle head bob), walk animation plays when moving (leg/arm swing), sprint animation plays when sprinting (exaggerated swing). Disabling and re-enabling shaders does not break rendering.

**Why human:** Geckolib 4.8.4 shader compatibility cannot be verified programmatically. This was the blocking checkpoint in plan 03-04 Task 2, which was auto-approved without execution. This is the primary open gate for Phase 3 completion.

#### 2. Name Tag Interaction

**Test:** In survival mode, hold a named name tag and right-click the squire.

**Expected:** Squire name appears above entity. Item is consumed. Name persists after reconnecting to the server.

**Why human:** Name tag DataComponents.CUSTOM_NAME read and setCustomName persistence requires a running game session.

#### 3. Backpack Tier Visibility

**Test:** Spawn a SERVANT-tier squire. Note backpack size. Level up to SQUIRE tier (level 10+). Note backpack size.

**Expected:** Small backpack at SERVANT/APPRENTICE. Large backpack at SQUIRE and above.

**Why human:** GeoBone.setHidden() effect requires in-game rendering to verify.

#### 4. Tiered Armor Texture

**Test:** Equip a squire armor item (helmet/chestplate/leggings/boots) onto a squire. Observe texture with shaders on and off.

**Expected:** Solid-color placeholder texture from TIER_OUTER[0] (gray) at SERVANT tier. Texture changes as tier increases.

**Why human:** RenderType.armorCutoutNoCull() texture injection requires the render pipeline.

### Gaps Summary

No gaps identified. All automated verification checks passed. The remaining items are in the human_needed category per the scope note that Oculus/ATM10 shader validation is deferred to human testing.

The Oculus checkpoint (plan 03-04 Task 2) was flagged as auto-approved in the SUMMARY. Per the verification scope note, this is correctly handled as human_needed rather than a gap.

---

_Verified: 2026-04-03T16:30:00Z_
_Verifier: Claude (gsd-verifier)_

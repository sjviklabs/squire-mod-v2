---
phase: 03-rendering
plan: "01"
subsystem: rendering
tags: [geckolib, geo-entity, animation, renderer, model, client]
dependency_graph:
  requires: [01-core-entity-foundation, 02-brain-fsm-follow]
  provides: [SquireModel, SquireRenderer, SquireClientEvents, GeoEntity-on-SquireEntity]
  affects: [03-02-assets, 03-03-layers, 03-04-health-bar]
tech_stack:
  added: [Geckolib 4.8.4]
  patterns:
    - GeoModel<T> as model base (GeoEntityModel absent in 4.8.4)
    - GeoEntityRenderer(Context, GeoModel<T>) two-arg constructor
    - Animation.LoopType (nested — not top-level LoopType)
    - getTextureResource(T) not getTextureLocation(T) on GeoModel abstract contract
    - private final AnimatableInstanceCache field at declaration site (not constructor)
key_files:
  created:
    - src/main/java/com/sjviklabs/squire/client/SquireModel.java
    - src/main/java/com/sjviklabs/squire/client/SquireRenderer.java
    - src/main/java/com/sjviklabs/squire/client/SquireClientEvents.java
  modified:
    - build.gradle
    - src/main/java/com/sjviklabs/squire/entity/SquireEntity.java
decisions:
  - "GeoEntityModel does not exist in Geckolib 4.8.4 — base class is GeoModel<T>"
  - "LoopType is Animation.LoopType (nested enum), not a top-level class in 4.8.4"
  - "GeoModel abstract contract uses getTextureResource(T) not getTextureLocation(T)"
  - "GeoEntityRenderer constructor takes (Context, GeoModel<T>) — model provided explicitly"
  - "EventBusSubscriber.Bus.MOD deprecated in NeoForge 21.1 but functional — no alternative yet"
metrics:
  duration_minutes: 20
  completed_date: "2026-04-03"
  tasks_completed: 2
  files_created: 3
  files_modified: 2
requirements_met: [RND-01, RND-02]
---

# Phase 3 Plan 1: Geckolib Wiring + Renderer Plumbing Summary

Geckolib 4.8.4 wired into SquireEntity; GeoModel/GeoEntityRenderer rendering stack created and registered on the client event bus.

## What Was Built

**SquireEntity** now implements `GeoEntity` with:
- `private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this)` — field declaration site, not constructor
- `registerControllers`: locomotion controller (5-frame blend, reads `isSprinting()` + `isMoving()`) and combat stub (returns STOP; Phase 4 populates via `triggerAnim()`)
- `RawAnimation` statics: `IDLE_ANIM`, `WALK_ANIM`, `SPRINT_ANIM`, `ATTACK_ANIM`

**SquireModel** (`GeoModel<SquireEntity>`):
- Routes `getModelResource` and `getTextureResource` to male/female assets via `entity.isSlimModel()`
- `getAnimationResource` points to `animations/squire.animation.json` (authored in 03-02)
- Asset paths: `geo/squire_male.geo.json`, `geo/squire_female.geo.json`, `textures/entity/squire_male.png`, `textures/entity/squire_female.png`

**SquireRenderer** (`GeoEntityRenderer<SquireEntity>`):
- Two-arg constructor: `super(context, new SquireModel())`
- `renderNameTag` stub calls super; health bar deferred to 03-04
- Render layers (armor, backpack) deferred to 03-03

**SquireClientEvents**:
- `@EventBusSubscriber(modid = MODID, bus = Bus.MOD, value = Dist.CLIENT)`
- `registerRenderers` subscribes `SquireRenderer::new` to `SquireRegistry.SQUIRE.get()`

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] GeoEntityModel does not exist in Geckolib 4.8.4**

- **Found during:** Task 2
- **Issue:** Plan specified `GeoEntityModel<SquireEntity>` as base class; class does not exist in 4.8.4 jar
- **Fix:** Changed to `GeoModel<SquireEntity>` — the actual abstract base in the library
- **Files modified:** `SquireModel.java`
- **Commit:** bc1c4d9

**2. [Rule 1 - Bug] LoopType is a nested class in 4.8.4**

- **Found during:** Task 1 compile
- **Issue:** Plan imported `software.bernie.geckolib.animation.LoopType` — class does not exist at that path
- **Fix:** Changed to `software.bernie.geckolib.animation.Animation` import, referenced as `Animation.LoopType.PLAY_ONCE`
- **Files modified:** `SquireEntity.java`
- **Commit:** d452470

**3. [Rule 1 - Bug] getTextureLocation vs getTextureResource**

- **Found during:** Task 2
- **Issue:** Plan used `getTextureLocation(entity)` as the abstract method override; actual abstract method on `GeoModel` is `getTextureResource(entity)`
- **Fix:** Renamed override to `getTextureResource`
- **Files modified:** `SquireModel.java`
- **Commit:** bc1c4d9

## Geckolib 4.8.4 API Patterns (reference for 03-02 and 03-03)

```java
// Model base class
public class SquireModel extends GeoModel<SquireEntity> {
    @Override public ResourceLocation getModelResource(SquireEntity entity) { ... }
    @Override public ResourceLocation getTextureResource(SquireEntity entity) { ... }
    @Override public ResourceLocation getAnimationResource(SquireEntity entity) { ... }
}

// Renderer base class + constructor
public class SquireRenderer extends GeoEntityRenderer<SquireEntity> {
    public SquireRenderer(EntityRendererProvider.Context context) {
        super(context, new SquireModel());
    }
}

// Render layer registration (03-03)
addRenderLayer(new SomeLayer(this)); // called in SquireRenderer constructor

// LoopType for PLAY_ONCE animations
RawAnimation.begin().then("animation.squire.attack", Animation.LoopType.PLAY_ONCE);

// AnimatableInstanceCache — MUST be private final at field declaration
private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);
```

## Commits

| Task | Commit | Description |
| ---- | ------ | ----------- |
| 1    | d452470 | GeoEntity on SquireEntity, Geckolib 4.8.4, LoopType fix |
| 2    | bc1c4d9 | SquireModel, SquireRenderer, SquireClientEvents |

## Self-Check: PASSED

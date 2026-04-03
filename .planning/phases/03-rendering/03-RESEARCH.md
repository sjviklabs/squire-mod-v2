# Phase 3: Rendering - Research

**Researched:** 2026-04-02
**Domain:** Geckolib 4 entity rendering, NeoForge 1.21.1 client-side registration, Blockbench model authoring, Oculus shader compatibility
**Confidence:** HIGH (Geckolib wiki + NeoForge docs + v0.5.0 source read directly)

---

<phase_requirements>

## Phase Requirements

| ID     | Description                                               | Research Support                                                                                                |
| ------ | --------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------- |
| RND-01 | Geckolib-based entity model with animations               | GeoEntity interface + GeoEntityRenderer + AnimationController — full pattern documented in Standard Stack       |
| RND-02 | Male/female skin variants (slim/wide)                     | `getTextureResource`/`getModelResource` are context-aware in 4.8.x; two `.geo.json` files or one with bone swap |
| RND-03 | Custom naming via name tag or command                     | Override `shouldShowName()` + name persistence via SynchedEntityData — carried over from v0.5.0                 |
| RND-04 | Personality chat lines (idle, combat, level-up, new tier) | `ChatHandler` stub wired to SquireEventBus; no rendering dependency — scaffolding only in this phase            |
| RND-05 | Backpack visual layer that reflects inventory tier        | `GeoRenderLayer` subclass replacing v0.5.0 `BackpackLayer`; tier queried from `SynchedEntityData`              |
| RND-06 | Tiered armor texture rendering                            | `ItemArmorGeoLayer` bone-to-slot mapping; tier textures selected by squire level from `SynchedEntityData`       |
| INV-04 | Custom 4-piece armor set with tiered textures             | `SquireArmorItem` registered items + `squire_layer_1_t*.png` / `squire_layer_2_t*.png` asset naming             |

</phase_requirements>

---

## Summary

Phase 3 is a complete rendering rewrite. v0.5.0 used `HumanoidMobRenderer` + `PlayerModel<SquireEntity>` — a vanilla workaround that required custom `SquireTieredArmorLayer` and `BackpackLayer` classes bolted onto the HumanoidModel pipeline. v2 replaces all of that with Geckolib 4, which eliminates the layer hacks and gives the squire proper keyframe animations at the cost of a Blockbench model authoring step.

The critical Geckolib constraint is that `AnimatableInstanceCache` must be created as a `final` field at class initialization — not in a constructor body, not lazily. This is the most common crash cause for new Geckolib users. The second critical constraint is that the `getTextureResource` and `getModelResource` methods on `GeoEntityModel` must resolve from `SynchedEntityData`, not server-side entity state, because they run on the client during rendering.

Oculus/NeOculus shader compatibility with Geckolib 4.8.x on NeoForge 1.21.1 appears to be handled by the Geckolib library itself for version 4.7.x+. The "Iris/Oculus & GeckoLib Compat" bridge mod (`geckoanimfix`) is listed as deprecated on its Modrinth page and its last file targets Geckolib pre-4.7. This means: do NOT add `geckoanimfix` as a dependency. The validation pass in plan 03-04 is still required to confirm Oculus works in ATM10 without issues — but no compat shim should be in the mod's deps.

**Primary recommendation:** `GeoEntity` on `SquireEntity`, `GeoEntityRenderer` + `GeoEntityModel` in `client/`, `ItemArmorGeoLayer` for armor, `GeoRenderLayer` for the backpack, two `.geo.json` files for male/female variants. Keep the health bar logic from v0.5.0 — it's client-only, clean, and works against Geckolib's `GeoEntityRenderer`.

---

## Standard Stack

### Core

| Library | Version | Purpose | Why Standard |
| ------- | ------- | ------- | ------- |
| Geckolib | 4.8.4 (NeoForge 1.21.1) | Custom entity model, keyframe animations, render layers | Only production-ready animation library for custom NeoForge entities. v0.5.0's HumanoidModel workaround is the primary rendering debt this phase eliminates. 4.8.4 is the current release (March 2026). |
| Blockbench + GeckoLib plugin | Latest (install from plugin browser) | Authoring `.geo.json` models and `.animation.json` keyframes | Official workflow; Bedrock animation knowledge transfers directly |

### Supporting

| Library | Version | Purpose | When to Use |
| ------- | ------- | ------- | ------- |
| NeoForge `EntityRenderersEvent` | 21.1.221 | Client-side renderer registration | Fire on mod event bus, `Dist.CLIENT` only |
| NeoForge `SynchedEntityData` | 21.1.221 | Sync `isSlimModel`, squire level, AI state to client | Required for any value the renderer reads — renderer runs on logical client, entity state is on logical server |

### Version Warning

Stack research documented Geckolib 4.8.3 as the latest. **4.8.4 is now current** (released March 3, 2026, file `geckolib-neoforge-1.21.1-4.8.4.jar` confirmed on CurseForge). Update `build.gradle` to `4.8.4`.

**Verified installation (update from Stack research):**

```groovy
// build.gradle
dependencies {
    implementation "software.bernie.geckolib:geckolib-neoforge-1.21.1:4.8.4"
}
```

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
| --- | --- | --- |
| Geckolib 4.8.4 | Geckolib 4.7.x | 4.7.x is stable but older; 4.8.x added `GeoArmorRenderer` rewrite making bone-to-slot mapping easier; no reason to stay on 4.7.x |
| Two `.geo.json` files (male/female) | One model + bone-visibility swap | Bone-swap is more complex to maintain in Blockbench; two separate model files with shared animation JSON is cleaner |
| `ItemArmorGeoLayer` (Geckolib built-in) | Port of v0.5.0 `SquireTieredArmorLayer` | v0.5.0 layer was HumanoidModel-specific; porting it to Geckolib would require duplicating what `ItemArmorGeoLayer` already provides |
| `GeoRenderLayer` (backpack) | Port of v0.5.0 `BackpackLayer` (raw vertex draws) | v0.5.0 used raw `drawBox()` calls against a `PlayerModel` body pivot. GeoRenderLayer is the Geckolib-native equivalent — attaches to bones in the `.geo.json` directly |

---

## Architecture Patterns

### Recommended Client Structure

```
src/main/java/com/sjviklabs/squire/client/
├── SquireRenderer.java         # GeoEntityRenderer<SquireEntity>
├── SquireModel.java            # GeoEntityModel<SquireEntity>
├── SquireArmorLayer.java       # ItemArmorGeoLayer — tiered armor textures
├── SquireBackpackLayer.java    # GeoRenderLayer — tier-scaled backpack visual
├── SquireClientEvents.java     # @EventBusSubscriber(Dist.CLIENT) — renderer registration
└── SquireRenderTypes.java      # Custom RenderType for health bar (port from v0.5.0)

src/main/resources/assets/squire/
├── geo/
│   ├── squire_male.geo.json    # Wide body model
│   └── squire_female.geo.json  # Slim body model
├── animations/
│   └── squire.animation.json   # Shared animation file (idle, walk, sprint, attack)
└── textures/
    ├── entity/
    │   ├── squire_male.png     # Base skin — male
    │   └── squire_female.png   # Base skin — female
    └── models/armor/
        ├── squire_layer_1_t0.png  # Outer armor: Servant
        ├── squire_layer_1_t1.png  # Outer armor: Apprentice
        ├── squire_layer_1_t2.png  # Outer armor: Squire/Knight
        ├── squire_layer_1_t3.png  # Outer armor: Champion
        ├── squire_layer_2_t0.png  # Inner armor (leggings): Servant
        ├── squire_layer_2_t1.png  # Inner armor: Apprentice
        ├── squire_layer_2_t2.png  # Inner armor: Squire/Knight
        └── squire_layer_2_t3.png  # Inner armor: Champion
```

### Pattern 1: GeoEntity Interface on SquireEntity

**What:** `SquireEntity` implements `GeoEntity`. The cache must be a `final` field — not initialized lazily or in a method call.

**When to use:** This is not optional — Geckolib requires it.

**Critical trap:** If `AnimatableInstanceCache` is not a `final` field, Geckolib throws a null pointer on first render tick. This is the #1 crash in new Geckolib mods.

```java
// Source: Geckolib wiki — Geckolib Entities (Geckolib4)
public class SquireEntity extends PathfinderMob implements GeoEntity {

    // MUST be final, MUST be initialized here, not in constructor body
    private final AnimatableInstanceCache geoCache =
        GeckoLibUtil.createInstanceCache(this);

    // Animation references — define once, reuse
    private static final RawAnimation IDLE_ANIM =
        RawAnimation.begin().thenLoop("animation.squire.idle");
    private static final RawAnimation WALK_ANIM =
        RawAnimation.begin().thenLoop("animation.squire.walk");
    private static final RawAnimation SPRINT_ANIM =
        RawAnimation.begin().thenLoop("animation.squire.sprint");
    private static final RawAnimation ATTACK_ANIM =
        RawAnimation.begin().then("animation.squire.attack", LoopType.PLAY_ONCE);

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // Register broad first (locomotion), specific last (combat)
        // Controller order matters — later controllers override earlier ones on shared bones
        controllers.add(new AnimationController<>(this, "locomotion", 5,
            this::locomotionController));
        controllers.add(new AnimationController<>(this, "combat", 3,
            this::combatController));
    }

    private <E extends SquireEntity> PlayState locomotionController(AnimationState<E> state) {
        if (state.isMoving()) {
            // Read sprint state from SynchedEntityData — not from brain (client-side)
            if (this.isSprinting()) {
                return state.setAndContinue(SPRINT_ANIM);
            }
            return state.setAndContinue(WALK_ANIM);
        }
        return state.setAndContinue(IDLE_ANIM);
    }

    private <E extends SquireEntity> PlayState combatController(AnimationState<E> state) {
        // Triggered animations (attack) managed via triggerAnim() from CombatHandler
        return PlayState.STOP;
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.geoCache;
    }
}
```

### Pattern 2: GeoEntityRenderer and GeoEntityModel

**What:** `SquireRenderer` extends `GeoEntityRenderer`. Model variant (male/female) is selected in `getModelResource()` based on `SynchedEntityData`.

**When to use:** Client-side only — register via `EntityRenderersEvent.RegisterRenderers` on `Dist.CLIENT`.

```java
// Source: Geckolib wiki + NeoForge EntityRenderersEvent docs
public class SquireRenderer extends GeoEntityRenderer<SquireEntity> {

    private static final ResourceLocation MALE_MODEL =
        ResourceLocation.fromNamespaceAndPath(SquireMod.MODID, "geo/squire_male.geo.json");
    private static final ResourceLocation FEMALE_MODEL =
        ResourceLocation.fromNamespaceAndPath(SquireMod.MODID, "geo/squire_female.geo.json");
    private static final ResourceLocation MALE_TEXTURE =
        ResourceLocation.fromNamespaceAndPath(SquireMod.MODID, "textures/entity/squire_male.png");
    private static final ResourceLocation FEMALE_TEXTURE =
        ResourceLocation.fromNamespaceAndPath(SquireMod.MODID, "textures/entity/squire_female.png");

    public SquireRenderer(EntityRendererProvider.Context context) {
        super(context, new SquireModel());
        addRenderLayer(new SquireArmorLayer(this));
        addRenderLayer(new SquireBackpackLayer(this));
    }

    @Override
    public ResourceLocation getModelResource(SquireEntity entity) {
        return entity.isSlimModel() ? FEMALE_MODEL : MALE_MODEL;
    }

    @Override
    public ResourceLocation getTextureLocation(SquireEntity entity) {
        return entity.isSlimModel() ? FEMALE_TEXTURE : MALE_TEXTURE;
    }

    // Port health bar rendering from v0.5.0 — logic is identical,
    // only the parent class changes (GeoEntityRenderer vs HumanoidMobRenderer)
    @Override
    protected void renderNameTag(SquireEntity entity, Component displayName,
            PoseStack poseStack, MultiBufferSource bufferSource,
            int packedLight, float partialTick) {
        super.renderNameTag(entity, displayName, poseStack, bufferSource, packedLight, partialTick);
        renderHealthBar(entity, poseStack, bufferSource, packedLight, partialTick);
    }
}

// GeoEntityModel — no model layer baking required (unlike vanilla HumanoidModel)
public class SquireModel extends GeoEntityModel<SquireEntity> {
    // getModelResource and getTextureResource are overridden on the renderer
    // This class is intentionally minimal — model loading is handled by GeoEntityRenderer
}
```

**Renderer registration (client events):**

```java
// Source: NeoForge EntityRenderersEvent docs
@EventBusSubscriber(modid = SquireMod.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class SquireClientEvents {

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(SquireRegistry.SQUIRE_ENTITY.get(), SquireRenderer::new);
    }
}
```

### Pattern 3: ItemArmorGeoLayer (Tiered Armor)

**What:** Geckolib's built-in `ItemArmorGeoLayer` renders equipped armor items onto Geckolib entity bones. Three methods map bones to equipment slots, vanilla model parts, and item stacks. Tiered texture selection replaces v0.5.0's `SquireTieredArmorLayer`.

**When to use:** RND-06 and INV-04. The layer goes in `SquireRenderer` constructor via `addRenderLayer(new SquireArmorLayer(this))`.

**Critical trap:** The layer reads the **first cube in each GeoBone** to determine position and size. Bones intended for armor must have at least one cube. Bones that are parent-only (no cubes) must NOT be returned from `getEquipmentSlotForBone()`.

```java
// Source: Geckolib wiki — Render Layers (Geckolib4)
public class SquireArmorLayer extends ItemArmorGeoLayer<SquireEntity> {

    // Tier-indexed textures — outer (layer_1) and inner (layer_2)
    private static final ResourceLocation[] TIER_OUTER = {
        ResourceLocation.fromNamespaceAndPath(SquireMod.MODID, "textures/models/armor/squire_layer_1_t0.png"),
        ResourceLocation.fromNamespaceAndPath(SquireMod.MODID, "textures/models/armor/squire_layer_1_t1.png"),
        ResourceLocation.fromNamespaceAndPath(SquireMod.MODID, "textures/models/armor/squire_layer_1_t2.png"),
        ResourceLocation.fromNamespaceAndPath(SquireMod.MODID, "textures/models/armor/squire_layer_1_t3.png"),
    };
    private static final ResourceLocation[] TIER_INNER = {
        ResourceLocation.fromNamespaceAndPath(SquireMod.MODID, "textures/models/armor/squire_layer_2_t0.png"),
        ResourceLocation.fromNamespaceAndPath(SquireMod.MODID, "textures/models/armor/squire_layer_2_t1.png"),
        ResourceLocation.fromNamespaceAndPath(SquireMod.MODID, "textures/models/armor/squire_layer_2_t2.png"),
        ResourceLocation.fromNamespaceAndPath(SquireMod.MODID, "textures/models/armor/squire_layer_2_t3.png"),
    };

    public SquireArmorLayer(GeoEntityRenderer<SquireEntity> renderer) {
        super(renderer);
    }

    @Override
    @Nullable
    protected EquipmentSlot getEquipmentSlotForBone(GeoBone bone, ItemStack stack,
            SquireEntity animatable) {
        return switch (bone.getName()) {
            case "head" -> EquipmentSlot.HEAD;
            case "body" -> EquipmentSlot.CHEST;
            case "right_leg", "left_leg" -> EquipmentSlot.LEGS;
            case "right_foot", "left_foot" -> EquipmentSlot.FEET;
            default -> null; // Bone not used for armor
        };
    }

    @Override
    @Nullable
    protected ModelPart getModelPartForBone(GeoBone bone, EquipmentSlot slot,
            ItemStack stack, SquireEntity animatable, HumanoidModel<?> baseModel) {
        return switch (bone.getName()) {
            case "head" -> baseModel.head;
            case "body" -> baseModel.body;
            case "right_leg" -> baseModel.rightLeg;
            case "left_leg" -> baseModel.leftLeg;
            case "right_foot" -> baseModel.rightLeg;  // boots share with leggings model part
            case "left_foot" -> baseModel.leftLeg;
            default -> null;
        };
    }

    @Override
    @Nullable
    protected ItemStack getArmorItemForBone(GeoBone bone, SquireEntity animatable) {
        return switch (bone.getName()) {
            case "head" -> animatable.getItemBySlot(EquipmentSlot.HEAD);
            case "body" -> animatable.getItemBySlot(EquipmentSlot.CHEST);
            case "right_leg", "left_leg" -> animatable.getItemBySlot(EquipmentSlot.LEGS);
            case "right_foot", "left_foot" -> animatable.getItemBySlot(EquipmentSlot.FEET);
            default -> null;
        };
    }

    // Override texture resolution for SquireArmorItem to select tiered texture
    // Non-squire armor falls back to ItemArmorGeoLayer's default vanilla material lookup
    @Override
    protected ResourceLocation getArmorTexture(SquireEntity animatable, ItemStack stack,
            GeoBone bone, EquipmentSlot slot, boolean innerModel) {
        if (stack.getItem() instanceof SquireArmorItem) {
            int tier = SquireTier.fromLevel(animatable.getSquireLevel()).ordinal();
            return innerModel ? TIER_INNER[tier] : TIER_OUTER[tier];
        }
        return super.getArmorTexture(animatable, stack, bone, slot, innerModel);
    }
}
```

### Pattern 4: GeoRenderLayer (Tier-Scaled Backpack)

**What:** Replaces v0.5.0's `BackpackLayer`. Instead of raw vertex drawing against a `PlayerModel` body pivot, `GeoRenderLayer` hooks into the Geckolib render pipeline and can transform relative to named bones.

**When to use:** RND-05. The backpack visual is a separate render layer that queries tier from `SynchedEntityData`.

**Key difference from v0.5.0:** v0.5.0 used `this.getParentModel().body.translateAndRotate(poseStack)` to attach the box to the body bone. In Geckolib, you get the bone transform via `getRenderer().getGeoModel().getBone("body")` and apply from there. Alternatively, include the backpack geometry directly in the `.geo.json` as a sub-bone of the body bone and control visibility by tier — simpler and cleaner.

**Recommended approach:** Include backpack as a separate bone group in the `.geo.json` (e.g., `backpack_small`, `backpack_large`). Show/hide via `setHidden()` in the animation controller based on tier read from entity's synched data. This avoids runtime vertex math entirely.

```java
// Source: Geckolib wiki — Render Layers (Geckolib4) + v0.5.0 BackpackLayer pattern
public class SquireBackpackLayer extends GeoRenderLayer<SquireEntity> {

    public SquireBackpackLayer(GeoEntityRenderer<SquireEntity> renderer) {
        super(renderer);
    }

    @Override
    public void render(PoseStack poseStack, SquireEntity animatable, BakedGeoModel bakedModel,
            RenderType renderType, MultiBufferSource bufferSource,
            VertexConsumer buffer, float partialTick,
            int packedLight, int packedOverlay) {
        if (animatable.isInvisible()) return;
        // Bone-based approach: control visibility in the geo model directly
        // Per-tier show/hide handled in animation controller or via bone lookup
        // If using bone-visibility approach: no draw calls needed here
        // If using vertex approach: follow v0.5.0 BackpackLayer.drawBox() pattern
        // but use getBone("body") for pivot instead of getParentModel().body
    }
}
```

### Pattern 5: SynchedEntityData Fields Required for Rendering

The renderer runs on the logical client. Any entity state the renderer reads must be a `SynchedEntityData` field — not computed from server-side brain state.

Required synched fields for Phase 3:

```java
// In SquireEntity — these must already exist from Phase 1/2 or be added in Phase 3
private static final EntityDataAccessor<Boolean> IS_SLIM_MODEL =
    SynchedEntityData.defineId(SquireEntity.class, EntityDataSerializers.BOOLEAN);
private static final EntityDataAccessor<Integer> SQUIRE_LEVEL =
    SynchedEntityData.defineId(SquireEntity.class, EntityDataSerializers.INT);
// Sprint state — already synced by PathfinderMob.isSprinting() via vanilla mechanism
// Attack animation trigger — use triggerAnim() from GeoAnimatable, synced by Geckolib
```

### Pattern 6: Triggered Animations (Server-Side Attack)

Attack animations need to fire from `CombatHandler.tick()` on the server and be visible on the client. Geckolib 4.7+ added `triggerAnim()` which handles this sync automatically.

```java
// Called from CombatHandler when squire swings (server side)
squire.triggerAnim("combat", "animation.squire.attack");
// Geckolib syncs this to the client — no custom payload required
```

### Bone Naming Conventions for the .geo.json

Bone names in the `.geo.json` must match the names used in `getEquipmentSlotForBone()` and the animation controller. Establish these names up front in Blockbench and do not rename after animating — renaming breaks all animation keyframes and all layer mapping.

| Bone Name | Armor Slot | Purpose |
| --- | --- | --- |
| `head` | HEAD | Helmet rendering |
| `body` | CHEST | Chestplate + backpack attachment point |
| `right_arm` / `left_arm` | — | Arm swing animation |
| `right_leg` / `left_leg` | LEGS | Leggings |
| `right_foot` / `left_foot` | FEET | Boots |
| `backpack_small` | — | Backpack geo for Servant/Apprentice |
| `backpack_large` | — | Backpack geo for Knight/Champion |

### Anti-Patterns to Avoid

- **Port v0.5.0 HumanoidMobRenderer:** The v0.5.0 renderer is HumanoidModel-based. Porting it to v2 requires rewriting every layer from scratch anyway — the Geckolib pattern is different enough that "porting" is actually "rewriting with extra steps."
- **Lazy AnimatableInstanceCache:** `GeckoLibUtil.createInstanceCache(this)` must be a `final` field. Any other initialization pattern causes a null crash on first render.
- **Reading server-side state in the renderer:** Renderer runs on the client. `squire.getBrain().getCombatHandler().isAttacking()` is server state. Use `SynchedEntityData` or Geckolib's `triggerAnim()` instead.
- **Bone names with spaces:** Geckolib bone names cannot contain spaces. Use `snake_case` throughout.
- **Returning bones without cubes from getEquipmentSlotForBone():** `ItemArmorGeoLayer` reads the first cube of the returned bone for positioning. Parent bones with no cubes will cause a silent render failure (armor won't appear) or a null pointer.
- **Adding geckoanimfix as a dependency:** That mod is deprecated and its last release targets Geckolib pre-4.7. Geckolib 4.7+ handles Oculus compat internally. Adding it creates a conflicting dep.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
| --- | --- | --- | --- |
| Entity animation system | Custom keyframe engine | Geckolib 4 `GeoEntity` + `AnimationController` | Geckolib handles bone transforms, easing, Molang math, controller blending — hundreds of hours of work |
| Armor render layer | Custom `RenderLayer` with HumanoidModel | `ItemArmorGeoLayer` | Bone-to-slot mapping, enchantment glint, material texture lookup are all built-in |
| Animation → client sync | Custom `CustomPacketPayload` for animation state | `triggerAnim()` (Geckolib built-in) | Geckolib syncs triggered animations automatically since 4.7 |
| Model layer registration | `EntityRenderersEvent.RegisterLayerDefinitions` | Nothing — Geckolib skips this entirely | `GeoEntityRenderer` loads `.geo.json` at runtime; no baked model layer needed |
| Shader compat layer | Compat shim mod as dep | Nothing — Geckolib 4.8.x handles this | Adding deprecated `geckoanimfix` conflicts; Geckolib internals handle the Iris/Oculus case |

**Key insight:** The entire v0.5.0 `SquireRenderer.java` + `SquireTieredArmorLayer.java` + `BackpackLayer.java` (~400 lines) compresses to three lighter Geckolib classes with less custom vertex code, because Geckolib owns the render pipeline.

---

## Common Pitfalls

### Pitfall 1: AnimatableInstanceCache Not Final

**What goes wrong:** `NullPointerException` on first render tick of the squire. Geckolib tries to retrieve the instance cache before the entity's constructor has run if the field isn't initialized at declaration time.

**Why it happens:** Java field initialization order — Geckolib may access the cache during class instantiation via reflection paths.

**How to avoid:** `private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);` — exactly this, at the field declaration site.

**Warning signs:** Any NPE stack trace that includes `GeckoLibUtil.createInstanceCache` or `AnimatableInstanceCache`.

---

### Pitfall 2: Reading Server State in the Renderer

**What goes wrong:** Renderer reads `squire.getBrain().someHandler.isActive()` which is null or stale on the client. Silent failures — animations don't play, model snaps to T-pose.

**Why it happens:** `SquireBrain` and handlers exist on the logical server. The renderer runs on the logical client. The entity exists on both sides but the brain only ticks on the server.

**How to avoid:** All renderer-readable state goes through `SynchedEntityData`. For animation triggers (attack swing), use `triggerAnim()` from `CombatHandler.tick()` on the server — Geckolib propagates this to the client.

**Warning signs:** Works in singleplayer (integrated server + client), breaks on dedicated server or when tested as a real client-server split.

---

### Pitfall 3: T-Pose Under Oculus Shaders

**What goes wrong:** Entity renders in T-pose when Oculus/NeOculus shaders are active. Animations play in vanilla but not with shaders enabled.

**Why it happens:** Older Geckolib versions used a render path incompatible with Iris/Oculus shadow passes. The shadow pass runs a separate render that Geckolib's animation state didn't survive.

**How to avoid:** Use Geckolib 4.8.4 (current). Do NOT add `geckoanimfix` (deprecated, conflicts). Validate in ATM10 with Oculus in plan 03-04 before calling the phase done.

**Warning signs:** T-pose only with shaders on; normal pose in vanilla rendering.

---

### Pitfall 4: Invisible Entity Under Shaders

**What goes wrong:** Squire entity is completely invisible when shaders are enabled — not T-pose, just gone.

**Why it happens:** Shader pack's entity shadow or gbuffer pass fails to find a valid render path for the Geckolib render type. Rare with 4.8.x but documented in some shader/version combinations.

**How to avoid:** Test with multiple shader packs in ATM10. If it occurs, check Geckolib GitHub issues for the specific shader pack — there may be a known workaround. The Blockbench `.geo.json` must not have zero-cube bones in the root.

**Warning signs:** Entity spawns (name tag renders, hitbox is there for targeting), but body is invisible.

---

### Pitfall 5: Bone Name Mismatch Between Model and Layer

**What goes wrong:** Armor doesn't render on the squire even though the item is equipped. No exception — silent failure.

**Why it happens:** `getEquipmentSlotForBone()` returns a non-null slot for a bone name, but the actual `.geo.json` uses a different bone name. The layer iterates over the model's actual bones and compares by name — if there's no match, nothing renders.

**How to avoid:** Finalize bone names in the `.geo.json` spec in plan 03-02 before writing any layer code. The bone name table in Pattern 1 above should be locked down before Blockbench work starts.

**Warning signs:** Equipped armor item is in the slot, inventory GUI shows it, but no visual change on the squire.

---

### Pitfall 6: Male/Female Model Switch Not Visible to Other Players

**What goes wrong:** Owner sees correct male/female variant. Other players always see one variant (usually default/male).

**Why it happens:** `isSlimModel()` reads a `SynchedEntityData` field. If that field is not properly defined and synced (defined in `defineSynchedData()`, set correctly in `readAdditionalSaveData()`), other clients get the default value.

**How to avoid:** Verify `IS_SLIM_MODEL` SynchedEntityData field is defined before Phase 3 begins. This is a Phase 1 concern — if it was missed, it's a prerequisite fix before 03-01.

**Warning signs:** Works for the owner, shows wrong variant for other players.

---

## Code Examples

### .geo.json File Structure (from Geckolib wiki)

```json
{
    "format_version": "1.12.0",
    "minecraft:geometry": [
        {
            "description": {
                "identifier": "geometry.squire_male",
                "texture_width": 64,
                "texture_height": 64,
                "visible_bounds_width": 2,
                "visible_bounds_height": 2.5,
                "visible_bounds_offset": [0, 0.75, 0]
            },
            "bones": [
                {
                    "name": "body",
                    "pivot": [0, 24, 0],
                    "cubes": [
                        {"origin": [-4, 12, -2], "size": [8, 12, 4], "uv": [16, 16]}
                    ]
                }
                // ... head, arms, legs, backpack bones
            ]
        }
    ]
}
```

### .animation.json File Structure (Geckolib format)

```json
{
    "format_version": "1.8.0",
    "animations": {
        "animation.squire.idle": {
            "loop": true,
            "animation_length": 2.0,
            "bones": {
                "head": {
                    "rotation": {
                        "0.0": [0, 0, 0],
                        "1.0": [2.5, 0, 0],
                        "2.0": [0, 0, 0]
                    }
                }
            }
        },
        "animation.squire.walk": {
            "loop": true,
            "animation_length": 1.0,
            "bones": { /* arm and leg swing keyframes */ }
        },
        "animation.squire.attack": {
            "loop": false,
            "animation_length": 0.5,
            "bones": { /* right_arm swing keyframes */ }
        }
    }
}
```

### RawAnimation References (Java)

```java
// Source: Geckolib wiki — Animation Controller
// Define as static constants on SquireEntity to avoid per-tick allocation
private static final RawAnimation IDLE_ANIM =
    RawAnimation.begin().thenLoop("animation.squire.idle");
private static final RawAnimation WALK_ANIM =
    RawAnimation.begin().thenLoop("animation.squire.walk");
private static final RawAnimation SPRINT_ANIM =
    RawAnimation.begin().thenLoop("animation.squire.sprint");
private static final RawAnimation ATTACK_ANIM =
    RawAnimation.begin().then("animation.squire.attack", LoopType.PLAY_ONCE);
```

### Triggering an Attack Animation from the Server

```java
// Source: Geckolib 4.7+ triggerAnim API
// Called in CombatHandler.tick() when the squire performs a melee swing
squire.triggerAnim("combat", "animation.squire.attack");
// Geckolib sends a sync packet to all clients tracking this entity automatically
```

### v0.5.0 → v2 Health Bar Port Note

The health bar rendering in `SquireRenderer.renderHealthBar()` uses `EntityAttachment.NAME_TAG`, `Matrix4f`, and `VertexConsumer` — all of which exist unchanged in 1.21.1 NeoForge. The logic is portable verbatim. Only the parent class changes: `HumanoidMobRenderer` → `GeoEntityRenderer`. The `renderNameTag` override signature is the same. Port it as-is.

---

## State of the Art

| Old Approach (v0.5.0) | Current Approach (v2) | Impact |
| --- | --- | --- |
| `HumanoidMobRenderer<SquireEntity, PlayerModel<SquireEntity>>` | `GeoEntityRenderer<SquireEntity>` | Eliminates HumanoidModel dependency; enables keyframe animations |
| `PlayerModel<SquireEntity>` wide/slim swap in `render()` | Two `.geo.json` files; `getModelResource()` returns correct one | Cleaner variant selection; no model instance swap mid-render |
| Custom `SquireTieredArmorLayer extends RenderLayer` (100 lines) | `ItemArmorGeoLayer` subclass (~60 lines) | Geckolib handles enchantment glint, model part visibility; less code |
| Raw vertex `drawBox()` for backpack | Backpack bones in `.geo.json` + `GeoRenderLayer` | Backpack animates with the body bone; Blockbench controls geometry |
| No keyframe animations (vanilla walk cycle only) | `AnimationController` with `idle`, `walk`, `sprint`, `attack` | Squire has personality-expressing motion beyond vanilla locomotion |
| `geckoanimfix` compat needed for shaders | Geckolib 4.7+ handles this internally | No compat dependency needed |

**Deprecated/outdated:**

- `HumanoidMobRenderer` for the squire: replaced by `GeoEntityRenderer`
- `ModelLayers.PLAYER` / `ModelLayers.PLAYER_SLIM` baked model layers: not needed with Geckolib
- `geckoanimfix` mod: deprecated, not compatible with current Geckolib

---

## Open Questions

1. **Blockbench model complexity vs. Phase scope**
   - What we know: The phase includes creating the `.geo.json` model in Blockbench (plan 03-02). A humanoid companion with idle/walk/sprint/attack animations is 1-3 days of Blockbench work depending on skill level.
   - What's unclear: Does the planner treat Blockbench work as a code task or a separate art task? If Steve is authoring the model himself, this is the bottleneck. If using a placeholder model (a box with bones) for Phase 3 and replacing with final art later, that changes the plan structure.
   - Recommendation: Plan 03-02 should produce a functional-but-placeholder model with correct bone names. Final art can be swapped in without code changes. Plan scope should note this explicitly.

2. **SynchedEntityData for IS_SLIM_MODEL — Phase 1 completeness**
   - What we know: Phase 3 rendering depends on `isSlimModel()` reading from `SynchedEntityData`. Phase 1 established the entity foundation.
   - What's unclear: Whether `IS_SLIM_MODEL` was actually added to `SquireEntity.defineSynchedData()` during Phase 1 work (Phase 1 plans are 3/5 complete per ROADMAP).
   - Recommendation: Plan 03-01 should begin with a prerequisite check: verify `IS_SLIM_MODEL` and `SQUIRE_LEVEL` are in `SynchedEntityData`. If missing, fix in 03-01 before renderer work starts.

3. **Geckolib 4.8.4 vs 4.8.3 API surface delta**
   - What we know: Stack research documented 4.8.3; 4.8.4 released March 2026. The CurseForge description mentions "Fix crash NeoForge introduced in .84" on 1.21.4 build — may refer to a different patch.
   - What's unclear: Whether 4.8.4 on 1.21.1 changes any public API used in this phase (unlikely but unverified).
   - Recommendation: Build against 4.8.4 from the start. If a breaking change surfaces during 03-01, fall back to 4.8.3 (documented as stable).

---

## Validation Architecture

### Test Framework

| Property | Value |
| --- | --- |
| Framework | JUnit 5 + NeoForge GameTests (established in Phase 1 plan 01-05) |
| Config file | None — GameTest uses NeoForge's built-in test runner |
| Quick run command | `./gradlew test` (unit tests only) |
| Full suite command | `./gradlew runGameTestServer` (in-world tests) |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
| --- | --- | --- | --- | --- |
| RND-01 | Geckolib renderer registers without crash | Unit | `./gradlew test --tests "*.SquireRenderingTest"` | Wave 0 |
| RND-01 | AnimatableInstanceCache is non-null after entity construction | Unit | `./gradlew test --tests "*.SquireRenderingTest.cacheNotNull"` | Wave 0 |
| RND-02 | `isSlimModel()` returns correct value, synced to client | Unit | `./gradlew test --tests "*.SquireEntityTest.slimModelSync"` | Wave 0 |
| RND-05 | Backpack layer tier selection returns correct index | Unit | `./gradlew test --tests "*.SquireBackpackLayerTest"` | Wave 0 |
| RND-06 | Armor texture resolves correct tier path | Unit | `./gradlew test --tests "*.SquireArmorLayerTest"` | Wave 0 |
| INV-04 | SquireArmorItem registers for all 4 slots | Unit | `./gradlew test --tests "*.SquireArmorItemTest"` | Wave 0 |
| RND-03 | Name tag displays when squire is tamed | Manual (GameTest) | `./gradlew runGameTestServer` | Wave 0 |
| RND-04 | ChatHandler fires at least one line on TIER_ADVANCE event | Unit | `./gradlew test --tests "*.ChatHandlerTest"` | Wave 0 |
| Full Oculus validation | No T-pose, no invisible entity with shaders | Manual | In-game test in ATM10 with Oculus shader pack | Manual only — plan 03-04 |

**Note:** Client-side rendering (actual visual correctness, shader compatibility) cannot be automated. Plan 03-04 exists specifically to run the manual validation pass.

### Sampling Rate

- **Per task commit:** `./gradlew test`
- **Per wave merge:** `./gradlew test && ./gradlew runGameTestServer`
- **Phase gate:** Full suite green + manual Oculus validation in ATM10 before phase sign-off

### Wave 0 Gaps

- [ ] `src/test/java/.../SquireRenderingTest.java` — covers RND-01 (cache init, renderer registration)
- [ ] `src/test/java/.../SquireArmorLayerTest.java` — covers RND-06, INV-04 (texture path correctness)
- [ ] `src/test/java/.../SquireBackpackLayerTest.java` — covers RND-05 (tier-to-size mapping)
- [ ] `src/test/java/.../ChatHandlerTest.java` — covers RND-04 (event bus stub fires)

---

## Sources

### Primary (HIGH confidence)

- Geckolib wiki — Geckolib Entities (Geckolib4): https://github.com/bernie-g/geckolib/wiki/Geckolib-Entities-(Geckolib4)
- Geckolib wiki — Render Layers (Geckolib4): https://github.com/bernie-g/geckolib/wiki/Render-Layers-(Geckolib4)
- Geckolib wiki — The Animation Controller (Geckolib4): https://github.com/bernie-g/geckolib/wiki/The-Animation-Controller-(Geckolib4)
- Geckolib wiki — Making Your Models (Blockbench): https://github.com/bernie-g/geckolib/wiki/Making-Your-Models-(Blockbench)
- NeoForge docs — Entity Renderers: https://docs.neoforged.net/docs/entities/renderer/
- v0.5.0 source (read directly): `C:/Users/Steve/Projects/squire-mod/src/main/java/com/sjviklabs/squire/client/`
  - `SquireRenderer.java` — health bar logic, layer registration pattern
  - `SquireTieredArmorLayer.java` — tier texture array pattern, `setPartVisibility()` logic
  - `BackpackLayer.java` — tier sizes/colors, body bone attachment

### Secondary (MEDIUM confidence)

- CurseForge Geckolib file listing — 4.8.4 for NeoForge 1.21.1 confirmed (March 2026): https://www.curseforge.com/minecraft/mc-mods/geckolib/files/7707149
- Modrinth — geckoanimfix deprecated listing: https://modrinth.com/mod/geckoanimfix
- CurseForge — geckoanimfix deprecated listing: https://www.curseforge.com/minecraft/mc-mods/geckoanimfix

### Tertiary (LOW confidence — flag for validation)

- Geckolib 4.7+ Oculus compat internal handling: inferred from geckoanimfix deprecation notice + WebSearch results. Needs empirical ATM10 validation in plan 03-04.

---

## Metadata

**Confidence breakdown:**

- Standard stack (Geckolib 4.8.4 for NeoForge 1.21.1): HIGH — version confirmed on CurseForge
- Architecture (GeoEntity + GeoEntityRenderer + GeoLayerRenderer): HIGH — Geckolib wiki read directly
- Armor layer pattern (ItemArmorGeoLayer): HIGH — official wiki; bone-naming traps documented
- Oculus compat: MEDIUM — geckoanimfix is deprecated (confirming Geckolib handles it), but ATM10 empirical test is still required
- Blockbench workflow: HIGH — official wiki; export format and file structure verified

**Research date:** 2026-04-02
**Valid until:** 2026-05-02 (Geckolib 4.x is stable; check for new patch releases against 1.21.1 before starting)

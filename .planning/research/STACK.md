# Stack Research

**Domain:** NeoForge 1.21.1 companion entity mod (custom AI, Geckolib rendering, data-driven design)
**Researched:** 2026-04-02
**Confidence:** MEDIUM-HIGH — versions verified via CurseForge/Modrinth/Maven; API patterns verified via official docs and Geckolib wiki

---

## Recommended Stack

### Core Technologies

| Technology | Version | Purpose | Why Recommended |
|---|---|---|---|
| NeoForge | 21.1.221 (ATM10 pin) | Mod loader, game APIs | ATM10 ships 21.1.221; mods compiled against this exact version avoid runtime incompatibilities in the pack |
| Java | 21 (LTS) | Language runtime | Mojang ships Java 21 with 1.21.1; NeoForge requires it; record types, pattern matching, sealed classes all available |
| ModDevGradle | 2.0.141+ | Build toolchain | Simpler buildscripts than NeoGradle, official NeoForge endorsement, single-version projects have no reason to use NeoGradle's added complexity |
| Geckolib | 4.8.3 (1.21.1 build) | Entity rendering, 3D animations | Only production-ready animation library for custom NeoForge entities; eliminates HumanoidModel/layer hacks; `.geo.json` + Blockbench workflow is standard |
| Curios API | 9.5.1+1.21.1 | Equipment slots (curio slots on squire) | The accessory slot standard for NeoForge; used by ATM10 and the majority of gear mods; clean capability API |

### Supporting Libraries

| Library | Version | Purpose | When to Use |
|---|---|---|---|
| Jade | 15.10.4+neoforge | In-world tooltip overlay (squire status HUD) | Compile-only dep; register an `IEntityComponentProvider` to show tier/HP/task; optional at runtime |
| MineColonies API | 1.1.1231-1.21.1 (API jar) | Prevent squire/citizen entity conflicts | Compile-only against the API; check `IColonyManager.getInstance()` at runtime with `ModList.get().isLoaded("minecolonies")` guard |
| Parchment Mappings | 2024.11.17-1.21.1 | Human-readable parameter names in dev | Included in ModDevGradle MDK by default; never shipped, dev-time only; significantly reduces pain when reading vanilla source |

### Development Tools

| Tool | Purpose | Notes |
|---|---|---|
| Blockbench (+ GeckoLib plugin) | Entity model and animation authoring | Install "GeckoLib Models & Animations" from the Blockbench plugin browser; export `.geo.json` models and `.animation.json` files directly; Bedrock animation knowledge transfers 1:1 |
| IntelliJ IDEA | Java IDE | NeoForge MDK ships with IDEA run configs; use the `runClient` Gradle task for dev iteration; `runData` for datagen |
| Gradle 8.x | Build system | Shipped by the MDK wrapper; do not change the wrapper version — Mojang toolchain is version-sensitive |

---

## Installation

```groovy
// settings.gradle
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven { url = 'https://maven.neoforged.net/releases' }
    }
}

// build.gradle — repositories block
repositories {
    // Geckolib
    maven {
        name = 'GeckoLib'
        url = 'https://dl.cloudsmith.io/public/geckolib3/geckolib/maven/'
    }
    // Curios
    maven {
        name = 'Curios'
        url = 'https://maven.theillusivec4.top/'
    }
    // MineColonies
    maven {
        name = 'LDTTeam - Modding'
        url = 'https://ldtteam.jfrog.io/ldtteam/modding/'
    }
    // Jade (use Modrinth maven)
    exclusiveContent {
        forRepository { maven { name = 'Modrinth'; url = 'https://api.modrinth.com/maven' } }
        filter { includeGroup 'maven.modrinth' }
    }
}

// build.gradle — dependencies block
dependencies {
    // Geckolib — hard required (entity rendering)
    implementation "software.bernie.geckolib:geckolib-neoforge-1.21.1:4.8.3"

    // Curios — API-only at compile time, full jar at runtime in dev
    compileOnly "top.theillusivec4.curios:curios-neoforge:9.5.1+1.21.1:api"
    localRuntime "top.theillusivec4.curios:curios-neoforge:9.5.1+1.21.1"

    // Jade — API-only, optional at runtime
    compileOnly "maven.modrinth:jade:15.10.4+neoforge"

    // MineColonies — API-only, optional at runtime
    compileOnly "com.minecolonies:minecolonies:1.1.1231-1.21.1:api"
}
```

---

## Alternatives Considered

| Recommended | Alternative | When to Use Alternative |
|---|---|---|
| ModDevGradle | NeoGradle | NeoGradle is appropriate if you need multi-version support or complex multi-project builds; for a single-target NeoForge mod it adds boilerplate without benefit |
| Geckolib 4.x | EMF (Entity Model Features) | EMF lets you override *vanilla* entity models via OptiFine-style JSONs; it cannot create new custom entity types; not applicable here |
| Geckolib 4.x | PlayerAnimator | PlayerAnimator targets the *player* entity specifically; Geckolib is the right choice for NPC/mob entities |
| Geckolib 4.x | Vanilla HumanoidModel | Works without a dependency, but requires custom renderer layers, texture overrides, and has no keyframe animation support; v0.5.0 used this and it was the primary rendering pain point |
| Curios 9.5.1 | Trinkets (Fabric) | Trinkets is Fabric-only; not applicable on NeoForge |
| PathfinderMob (base class) | TamableAnimal | TamableAnimal injects vanilla goals (SitGoal, FollowOwnerGoal, TameGoal) that actively conflict with a custom FSM; you'd spend significant effort suppressing them instead of building behavior |
| Custom owner tracking (~80 lines) | TamableAnimal owner system | TamableAnimal's owner UUID + UUID-to-player lookup is trivial to replicate; not worth the class hierarchy cost |

---

## What NOT to Use

| Avoid | Why | Use Instead |
|---|---|---|
| TamableAnimal as base class | Ships SitGoal, FollowOwnerGoal, TameGoal, and a vanilla sitting state machine that will conflict with your custom FSM at every tick; requires active goal-clearing hacks to suppress | PathfinderMob + custom 80-line owner tracking |
| GoalSelector / GoalEntry system | Vanilla AI is priority-queue based; goals interrupt each other by priority, not state; incompatible with a deterministic FSM where states transition on explicit conditions | Custom FSM with `tickBrain()` called from `tick()` at your chosen interval |
| Geckolib 3.x | EOL; incompatible with 1.21.x NeoForge; API was overhauled in 4.0 (IAnimatable → GeoEntity, AnimationFactory → AnimatableInstanceCache) | Geckolib 4.8.x |
| SimpleContainer for squire inventory | No slot-type validation, no capacity resize by tier, no capability exposure control; hoppers/pipes can't see it without extra work | Custom `IItemHandler` impl; extend `ItemStackHandler` and override `isItemValid()` and `getSlots()` for tier-aware resizing |
| Hardcoded `instanceof` mob checks | Breaks for modded mobs (ATM10 has hundreds); requires a recompile to add new tactics; v0.5.0's primary combat architecture debt | Entity type tags in `data/squire/tags/entity_type/` — any mod can opt their mobs in without touching squire code |
| Data Attachments for squire owner link | Data Attachments are for short-lived, world-level data; squire-to-player binding needs to survive relog, death, and crest loss | `addAdditionalSaveData` / `readAdditionalSaveData` on the entity with explicit UUID field — proven, simple, always available |
| NeoForge 21.1.x latest HEAD | ATM10 is pinned to 21.1.221; compiling against a newer minor build can introduce API surface not present at runtime in the pack | Pin `neo_version=21.1.221` in `gradle.properties` |

---

## Stack Patterns by Variant

**For Geckolib entity with equipment rendering (armor on squire body):**
- Use `GeoLayerRenderer` for equipment render layers on top of the base `GeoEntityRenderer`
- Each armor tier gets its own `.geo.json` model or a bone-swap pattern within one model
- Register layers in `GeoEntityRenderer` constructor

**For optional mod compat (Curios, Jade, MineColonies):**
- Declare `compileOnly` in Gradle
- Declare `type = "optional"` in `neoforge.mods.toml` dependency block
- Gate all calls behind `ModList.get().isLoaded("modid")` — never assume presence at runtime
- Put compat code in a separate `compat/` package so it only loads when the mod is present

**For data-driven combat tactics via entity tags:**
- Tag files live at `src/main/resources/data/squire/tags/entity_type/<tactic_name>.json`
- Check membership in entity tick: `entity.getType().is(SquireTags.MELEE_CAUTIOUS)`
- Register tags in datagen via `EntityTypeTagsProvider`; no hardcoded lists anywhere

**For JSON datapack progression definitions:**
- Use a `DatapackRegistry` with a Codec-backed record for progression tiers and ability definitions
- Loaded at world start; server operators can override via their own datapack
- DatapackRegistry values available via `level.registryAccess().registryOrThrow(SquireRegistries.TIER_CONFIG)`

---

## Version Compatibility Matrix

| Component | Version | Compatible With | Notes |
|---|---|---|---|
| NeoForge | 21.1.221 | MC 1.21.1 | ATM10 pin; do not upgrade unless ATM10 upgrades |
| Geckolib | 4.8.3 | NeoForge 21.1.x, MC 1.21.1 | 4.8.x adds render layer improvements over 4.7.x; stable release on CurseForge |
| Curios API | 9.5.1+1.21.1 | NeoForge 21.1.x | API jar (compileOnly) is stable; impl jar only needed in dev runtime |
| Jade | 15.10.4+neoforge | MC 1.21.1 NeoForge | Latest 1.21.1 NeoForge build as of Jan 2026 |
| MineColonies | 1.1.1231-1.21.1 | NeoForge 21.1.x | Latest 1.21.1 release (Dec 2025); use API jar only |
| Java | 21 | NeoForge 21.1.x | Required by Mojang; use Microsoft OpenJDK 21 |
| ModDevGradle plugin | 2.0.141+ | Gradle 8.x | Ships in MDK; do not override wrapper |
| Parchment | 2024.11.17-1.21.1 | MC 1.21.1 | Dev-only; check parchmentmc.org for latest if MDK date is stale |

---

## Confidence Assessment

| Area | Confidence | Basis |
|---|---|---|
| NeoForge 21.1.221 pin | HIGH | ATM10 issue tracker + modpack manifest confirm this exact build |
| Geckolib 4.8.3 version | MEDIUM | CurseForge file listing shows 4.8.3 jar for 1.21.1; Maven repository shows 4.7.6 most indexed — verify 4.8.3 on CurseForge before pinning; 4.7.6 is a safe fallback |
| Curios 9.5.1+1.21.1 | HIGH | CurseForge file listing confirmed; Maven artifact confirmed via search |
| Jade 15.10.4+neoforge | HIGH | Modrinth version listing confirmed; published Jan 2026 |
| MineColonies API approach | HIGH | Maintainer statement in GitHub issue #4343: "use API jar, not main mod" |
| ModDevGradle over NeoGradle | HIGH | NeoForge official docs recommendation for single-version mods |
| PathfinderMob over TamableAnimal | HIGH | Documented in PROJECT.md key decisions; confirmed by vanilla source analysis |
| IItemHandler over SimpleContainer | HIGH | NeoForge docs explicitly state Containers are deprecated in favor of IItemHandler |
| Data-driven tags pattern | HIGH | NeoForged docs confirm entity type tags work identically to block/item tags |

---

## Sources

- [CurseForge — Geckolib files for 1.21.1](https://www.curseforge.com/minecraft/mc-mods/geckolib/files/all) — version listing (MEDIUM confidence: 4.8.3 confirmed present)
- [Maven — geckolib-neoforge-1.21.1](https://mvnrepository.com/artifact/software.bernie.geckolib/geckolib-neoforge-1.21.1) — artifact coordinates
- [Geckolib Wiki — Installation (Geckolib4)](https://github.com/bernie-g/geckolib/wiki/Installation-(Geckolib4)) — Maven repo URL, dependency string
- [Geckolib Wiki — Geckolib Entities (Geckolib4)](https://github.com/bernie-g/geckolib/wiki/Geckolib-Entities-(Geckolib4)) — GeoEntity interface, AnimatableInstanceCache pattern
- [CurseForge — Curios API files](https://www.curseforge.com/minecraft/mc-mods/curios/files/all) — 9.5.1+1.21.1 confirmed
- [Modrinth — Curios 9.2.2+1.21.1](https://modrinth.com/mod/curios/version/9.2.2+1.21.1) — API dependency pattern
- [Modrinth — Jade 15.10.4+neoforge](https://modrinth.com/mod/jade/version/15.10.4+neoforge) — latest 1.21.1 Jade build
- [GitHub — MineColonies issue #4343](https://github.com/ldtteam/minecolonies/issues/4343) — use API jar, not main dep; maven repo URL
- [NeoForged docs — Mod Files (1.21.1)](https://docs.neoforged.net/docs/1.21.1/gettingstarted/modfiles/) — optional dependency TOML syntax
- [NeoForged docs — Tags](https://docs.neoforged.net/docs/1.21.1/resources/server/tags/) — entity type tag structure
- [NeoForged docs — Data Attachments](https://docs.neoforged.net/docs/datastorage/attachments/) — entity persistence patterns
- [NeoForgeMDKs — MDK-1.21.1-ModDevGradle](https://github.com/NeoForgeMDKs/MDK-1.21.1-ModDevGradle) — ModDevGradle 2.0.141, Java 21 config
- [Blockbench Wiki — Making Your Models (Geckolib)](https://github.com/bernie-g/geckolib/wiki/Making-Your-Models-(Blockbench)) — model authoring workflow

---

_Stack research for: NeoForge 1.21.1 companion entity mod (squire-mod-v2)_
_Researched: 2026-04-02_

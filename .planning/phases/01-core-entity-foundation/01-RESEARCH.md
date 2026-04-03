# Phase 1: Core Entity Foundation - Research

**Researched:** 2026-04-02
**Domain:** NeoForge 1.21.1 entity registration, NBT persistence, AttachmentType, IItemHandler capability, TOML config, builtin datapack embed, test harness scaffold
**Confidence:** HIGH — v0.5.0 source read directly; NeoForge/Geckolib/ModDevGradle patterns verified via official docs and prior stack research

---

<user_constraints>

## User Constraints (from CONTEXT.md)

### Locked Decisions

**Crest Summoning Behavior**
- Summon via right-click Crest: spawn with brief particle effect + ~1 second materialization animation at crosshair location
- Recall via right-click Crest again: instant despawn, no delay
- Recall always works regardless of combat state — player has full control
- No range limit on Crest — squire identity stored in player attachment, not world-located
- One squire per player enforced at summon time

**Death and Recovery**
- On death: equipped gear only drops (armor, weapon, shield). Backpack inventory is lost.
- Recovery: player re-summons via Crest. XP/tier/name retained in player data attachment.
- Death notification: chat message with coordinates ("Your squire fell at [X, Y, Z]")
- Champion undying (PRG-06): Totem of Undying effect — once per life, squire survives lethal damage at 1 HP with brief invulnerability. Must be re-summoned (die and resummon) to reset the ability.

**Inventory Slot Layout**
- Slot unlock per tier: Servant 9, Apprentice 18, Squire 27, Knight 32, Champion 36
- Separate equipment slots (fixed: 4 armor + 1 weapon + 1 offhand) + general backpack slots (tier-gated count)
- Backpack slots accept any item — no filtering on manual placement. Junk filtering only applies to auto-pickup (Phase 5).
- IItemHandler capability exposed — hoppers and modded pipes can insert/extract items. Squire participates in automation logistics.

**Config Organization**
- Single squire-common.toml, all server-enforced (no client config split)
- Grouped by behavior domain: [general], [combat], [follow], [mining], [farming], [fishing], [progression], [inventory], [rendering], [debug]
- Conservative defaults for ATM10 compatibility: lower tick rates, shorter follow distance, reduced chunk loading. Operators can increase.
- [debug] section with toggles (show AI state, log FSM transitions, draw pathfinding) — all off by default

### Claude's Discretion

- Exact particle effect for spawn animation
- SynchedEntityData field selection (which fields need client sync vs server-only)
- NBT tag naming conventions
- Test harness file organization
- Builtin datapack directory structure within resources

### Deferred Ideas (OUT OF SCOPE)

None — discussion stayed within phase scope

</user_constraints>

---

<phase_requirements>

## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| ENT-01 | Player can summon a squire using the Crest item | v0.5.0 SquireCrestItem.useOn() pattern — spawn at crosshair block face, restore from attachment if level > 0 |
| ENT-02 | Player can recall (despawn) the squire using the Crest item | v0.5.0 SquireCrestItem.use() air-click pattern — instant despawn via squire.discard(), no navigation call |
| ENT-03 | Squire persists across server restarts via NBT serialization | addAdditionalSaveData / readAdditionalSaveData — slot-indexed inventory tag, progression, mode byte |
| ENT-04 | Squire identity (name, XP, level, appearance) persists via player data attachment, surviving crest loss and death | AttachmentType + Codec pattern confirmed in v0.5.0 SquireDataAttachment — withXP/withName/withAppearance builder |
| ENT-05 | Squire extends PathfinderMob with custom owner system (not TamableAnimal) | PathfinderMob base, UUID ownerUUID field, ~80-line owner tracking, OwnerHurtByTargetGoal available standalone |
| ENT-06 | One squire per player (enforced) | Count living squires across all levels at summon time; reject if count >= maxSquiresPerPlayer |
| ENT-07 | Squire drops equipped gear on death but retains XP/tier via player attachment | die() writes attachment before calling super; equipment slots drop separately from backpack |
| INV-01 | Squire has tiered inventory (9 slots at Servant, expanding to 36 at Champion) | SquireItemHandler.getSlots() returns tier-gated count; SquireTier enum maps tier → slot count |
| INV-02 | Inventory implemented via IItemHandler capability (not SimpleContainer) | RegisterCapabilitiesEvent.registerEntity(Capabilities.ItemHandler.ENTITY, ...) pattern confirmed |
| INV-06 | Inventory accessible via GUI screen | AbstractContainerMenu stub in Phase 1; full screen in Phase 5. mobInteract() opens menu. |
| ARC-04 | SquireEntity (lifecycle/NBT) split from SquireBrain (AI/FSM) | SquireBrain instantiated lazily in aiStep(); entity class holds lifecycle only |
| ARC-05 | Single SquireRegistry for all NeoForge registrations | One class, all DeferredRegister instances, explicit registration order in SquireMod constructor |
| ARC-06 | 50+ config values in squire-common.toml (no hardcoded gameplay numbers) | v0.5.0 SquireConfig has ~65 entries — port values with ATM10 conservative defaults |
| ARC-07 | Builtin datapack for entity tags and JSON data (not world-level) | NeoForge issue #857 workaround: DataGenerator#getBuiltinDatapack embeds in JAR |
| ARC-09 | Chunk loading during area clear operations | SquireChunkLoader tick pattern from v0.5.0; stub hook in Phase 1, full impl in Phase 6 |
| TST-01 | JUnit 5 unit tests for core systems (FSM, inventory, progression) | JUnit 5 is available via ModDevGradle; test skeleton covers SquireItemHandler and SquireDataAttachment |
| TST-02 | NeoForge GameTests for in-world entity verification | @GameTest annotation, GameTestHelper, test template structure confirmed for NeoForge 1.21.1 |

</phase_requirements>

---

## Summary

Phase 1 establishes the irreversible foundation that every later phase builds on. The five plans (scaffold, entity, inventory, config, tests) must be done in order and must be correct before any work begins on Phase 2. Two architectural decisions made here cannot be changed without rewriting all downstream phases: the base class (PathfinderMob, not TamableAnimal) and the inventory implementation (IItemHandler capability, not SimpleContainer). Both were already locked in prior research and confirmed by v0.5.0 source reading.

The v0.5.0 codebase provides a complete reference for game logic constants — NBT tag names, SynchedEntityData fields, config value defaults, slot counts, summon/recall flow, and the AttachmentType codec pattern. The key difference in v2 is architecture: SquireEntity extends TamableAnimal in v0.5.0 (anti-pattern, creates vanilla goal conflicts) and uses SimpleContainer for inventory (no IItemHandler capability surface). Both are fixed in v2 by design.

The primary technical risks in Phase 1 are: (1) SynchedEntityData defineId() called on the wrong class causing network desync, (2) config validator/default mismatch causing a startup correction loop, and (3) the builtin datapack embed not working correctly — which manifests as progression data reverting to defaults after world reload. All three have known mitigations documented in the pitfalls research.

**Primary recommendation:** Follow the five-plan sequence strictly. Don't start Plan 01-02 (entity) before Plan 01-01 (scaffold) is committed. Don't start Plan 01-03 (inventory) before entity type registration is green. Registration order is load-order-sensitive in NeoForge — EntityTypes before Items, AttachmentTypes before entity lifecycle events.

---

## Standard Stack

### Core

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|-------------|
| NeoForge | 21.1.221 | Mod loader, all game APIs | ATM10 hard pin — mods must match this exact build |
| Java | 21 (LTS) | Language runtime | Mojang ships Java 21 with 1.21.1; sealed classes and records available |
| ModDevGradle | 2.0.141+ | Build toolchain | Official NeoForge recommendation; simpler than NeoGradle for single-target mods |
| Parchment Mappings | 2024.11.17-1.21.1 | Readable parameter names in dev | Dev-only; never shipped; included in MDK by default |

### Supporting (Phase 1 scope — other libs are later phases)

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| JUnit 5 | 5.x (via Gradle) | Unit test framework | TST-01 — test skeleton for SquireItemHandler, SquireDataAttachment codec |
| NeoForge GameTest | built-in | In-world entity testing | TST-02 — entity spawn/despawn/persistence verification |

**Geckolib, Curios, Jade, MineColonies are NOT Phase 1 dependencies.** They are declared in build.gradle in Plan 01-01 but not used until Phase 2-8.

### Installation (build.gradle core block)

```groovy
plugins {
    id 'net.neoforged.moddev' version '2.0.141'
}

neoForge {
    version = "21.1.221"
    parchment {
        mappingsVersion = "2024.11.17"
        minecraftVersion = "1.21.1"
    }
}

dependencies {
    // Phase 2+ deps declared now, used later:
    implementation "software.bernie.geckolib:geckolib-neoforge-1.21.1:4.8.3"
    compileOnly "top.theillusivec4.curios:curios-neoforge:9.5.1+1.21.1:api"
    localRuntime "top.theillusivec4.curios:curios-neoforge:9.5.1+1.21.1"
    compileOnly "maven.modrinth:jade:15.10.4+neoforge"
    compileOnly "com.minecolonies:minecolonies:1.1.1231-1.21.1:api"
}
```

---

## Architecture Patterns

### Recommended Project Structure (Phase 1 scope)

Phase 1 creates only the shaded packages. Stub files for future phases are noted.

```
src/main/java/com/sjviklabs/squire/
├── SquireMod.java              # @Mod entry — calls SquireRegistry.register()
├── SquireRegistry.java         # ALL DeferredRegister instances
│
├── entity/
│   ├── SquireEntity.java       # PathfinderMob subclass — lifecycle/NBT only
│   ├── SquireTier.java         # Enum: SERVANT/APPRENTICE/SQUIRE/KNIGHT/CHAMPION + slotCount()
│   └── SquireDataAttachment.java # AttachmentType + SquireData record + Codec
│
├── inventory/
│   ├── SquireItemHandler.java  # IItemHandler impl with tiered slot capacity
│   └── SquireMenu.java         # AbstractContainerMenu stub (full screen Phase 5)
│
├── item/
│   └── SquireCrestItem.java    # Summon / recall item
│
├── config/
│   └── SquireConfig.java       # ModConfigSpec — squire-common.toml, 50+ entries
│
└── brain/                      # Empty package stub — populated in Phase 2
    └── SquireBrain.java        # Minimal stub so entity compiles cleanly

src/main/resources/
├── META-INF/
│   └── neoforge.mods.toml
├── pack.mcmeta
└── data/squire/
    └── squire/
        └── progression/        # Builtin datapack JSON
            ├── servant.json
            ├── apprentice.json
            ├── squire_tier.json
            ├── knight.json
            └── champion.json

src/test/java/com/sjviklabs/squire/
├── inventory/
│   └── SquireItemHandlerTest.java   # JUnit 5: slot capacity, insertItem, extractItem
├── entity/
│   └── SquireDataAttachmentTest.java # JUnit 5: codec round-trip, withXP, withName
└── gametest/
    └── SquireEntityTest.java         # GameTest: spawn, persist, despawn
```

### Pattern 1: PathfinderMob + Custom Owner Tracking (~80 lines, no TamableAnimal)

**What:** SquireEntity extends PathfinderMob. Owner tracking is a `UUID ownerUUID` field in NBT. `isOwnedBy(player)` compares UUID. OwnerHurtByTargetGoal and OwnerHurtTargetGoal are standalone classes that work without TamableAnimal as base.

**When to use:** Always. TamableAnimal is explicitly out of scope per REQUIREMENTS.md.

**Example:**
```java
// Source: v0.5.0 SquireEntity.java adapted for PathfinderMob
public class SquireEntity extends PathfinderMob {
    private UUID ownerUUID;

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        // Target goals work standalone — don't require TamableAnimal
        this.targetSelector.addGoal(1, new OwnerHurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new OwnerHurtTargetGoal(this));
        this.targetSelector.addGoal(3, new HurtByTargetGoal(this));
    }

    public boolean isOwnedBy(Player player) {
        return player.getUUID().equals(this.ownerUUID);
    }

    @Override
    public void tryToTeleportToOwner() {
        // Intentional no-op — squires NEVER teleport
    }

    @Override
    public boolean removeWhenFarAway(double dist) { return false; }
    @Override
    public boolean canBeLeashed() { return false; }
    @Override
    public boolean isFood(ItemStack stack) { return false; }
}
```

**Critical:** OwnerHurtByTargetGoal and OwnerHurtTargetGoal require the entity to implement `TamableAnimal.getOwner()` contract. Since we're NOT extending TamableAnimal, verify these goals work with a manual getOwner() implementation that returns the player from the world by UUID. If not, use HurtByTargetGoal only and wire owner target sharing in Phase 2's CombatHandler.

### Pattern 2: SynchedEntityData — Minimal Set for Phase 1

**What:** Only client-visible state needs SynchedEntityData. Server-only state goes in regular fields with NBT save/load.

**Phase 1 fields (verified against v0.5.0 and Phase 1 scope):**
```java
// Source: v0.5.0 SquireEntity.java — adapted subset for Phase 1
private static final EntityDataAccessor<Byte> SQUIRE_MODE =
        SynchedEntityData.defineId(SquireEntity.class, EntityDataSerializers.BYTE);
private static final EntityDataAccessor<Integer> SQUIRE_LEVEL =
        SynchedEntityData.defineId(SquireEntity.class, EntityDataSerializers.INT);
private static final EntityDataAccessor<Boolean> SLIM_MODEL =
        SynchedEntityData.defineId(SquireEntity.class, EntityDataSerializers.BOOLEAN);
// IS_SPRINTING and DRAWING_BOW deferred to Phase 2 (navigation) and Phase 3 (combat)
```

**CRITICAL RULE:** Every `defineId()` call MUST pass `SquireEntity.class` as the first argument. Never pass a superclass, never define in a helper class, never define in a mixin. Violating this shifts all subsequent IDs and causes silent desync across all clients.

### Pattern 3: AttachmentType + Codec (Cross-Death Persistence)

**What:** Player-attached record that survives death, world reload, and crest loss. NeoForge serializes it to player NBT via the registered Codec.

**Example:**
```java
// Source: v0.5.0 SquireDataAttachment.java — use directly in v2
public static final Supplier<AttachmentType<SquireData>> SQUIRE_DATA =
        ATTACHMENTS.register("squire_data", () ->
                AttachmentType.builder(SquireData::empty)
                        .serialize(SquireData.CODEC)  // REQUIRED: omitting this loses data on world reload
                        .build());

public record SquireData(int totalXP, int level, String customName, boolean slimModel,
                          Optional<UUID> squireUUID) {
    // ... Codec, builder methods
}
```

**Critical:** The `.serialize(codec)` call on AttachmentType.builder is REQUIRED. Without it, the attachment exists in memory only — data is lost on world reload. This was confirmed in the pitfalls research (Integration Gotchas table).

### Pattern 4: IItemHandler Capability with Tier-Gated Capacity

**What:** SquireItemHandler extends ItemStackHandler (which implements IItemHandler). `getSlots()` returns the tier-appropriate count. Capability registered via RegisterCapabilitiesEvent.

**Slot counts (from CONTEXT.md — locked decision):**
```
SERVANT:    9 backpack + 6 equipment = 15 total slots
APPRENTICE: 18 backpack + 6 equipment = 24 total slots
SQUIRE:     27 backpack + 6 equipment = 33 total slots
KNIGHT:     32 backpack + 6 equipment = 38 total slots
CHAMPION:   36 backpack + 6 equipment = 42 total slots
```

Equipment slots (4 armor + 1 mainhand weapon + 1 offhand) are SEPARATE from backpack slots and do NOT change by tier. Only backpack slots expand.

**Implementation pattern:**
```java
// Source: NeoForge capability docs + v0.5.0 architecture research
public class SquireItemHandler extends ItemStackHandler {
    private final SquireEntity squire;

    public SquireItemHandler(SquireEntity squire) {
        super(squire.getTier().getBackpackSlots());
        this.squire = squire;
    }

    @Override
    public int getSlots() {
        return squire.getTier().getBackpackSlots();
    }

    @Override
    protected void onContentsChanged(int slot) {
        // Mark entity dirty so NBT saves
        squire.setChanged();
    }
}

// Registration in SquireRegistry:
@SubscribeEvent
public static void registerCapabilities(RegisterCapabilitiesEvent event) {
    event.registerEntity(
        Capabilities.ItemHandler.ENTITY,
        ModEntities.SQUIRE.get(),
        (squire, context) -> squire.getItemHandler()
    );
}
```

**Two-capability note from pitfalls research:** Use `Capabilities.ItemHandler.ENTITY` for the primary handler. For automation-only access (hoppers, pipes), NeoForge also has `Capabilities.ItemHandler.ENTITY_AUTOMATION`. Register both pointing to the same handler unless you want to block player-GUI access from pipes.

**Tier advance invalidation (Phase 4 concern, establish pattern now):**
```java
// Call this in SquireEntity when tier changes
this.invalidateCapabilities();
```

### Pattern 5: ModConfigSpec (50+ entry TOML)

**What:** Single ModConfigSpec.Builder, all entries declared in static initializer, registered as COMMON type. Groups map to TOML sections via `builder.push("section")`/`builder.pop()`.

**v0.5.0 config entry count:** 65 entries confirmed by reading SquireConfig.java. All values and defaults are available to port.

**New v2 groups (from CONTEXT.md):** `[general]`, `[combat]`, `[follow]`, `[mining]`, `[farming]`, `[fishing]`, `[progression]`, `[inventory]`, `[rendering]`, `[debug]`

**v0.5.0 groups to remap:** v0.5.0 used `[movement]`, `[combatTactics]`, `[intervals]`, etc. Remap to v2 group names per CONTEXT.md.

**Validator correctness rule:** Every `defineInRange()` default MUST satisfy its own min/max. `defineInRange("x", 0, 1, 100)` is a correction loop bug — default 0 fails min=1. Test: read every value immediately after spec construction and assert no correction warnings.

**Registration:**
```java
// In SquireMod constructor:
ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, SquireConfig.SPEC, "squire-common.toml");
```

### Pattern 6: Builtin Datapack Embed (NeoForge Issue #857 Workaround)

**What:** Progression JSON embedded in the mod JAR as a builtin datapack. Never relies on world-level datapack ordering, which can desync per issue #857 (EOL/won't fix on 1.21.1).

**Resource path:**
```
src/main/resources/data/squire/squire/progression/
├── servant.json       # { "tier": "servant", "xp_required": 0, "slot_count": 9 }
├── apprentice.json
├── squire_tier.json
├── knight.json
└── champion.json
```

**Builtin datapack registration (in data generator or startup):**
```java
// Source: NeoForge docs — AddPackFindersEvent
@SubscribeEvent
public static void addPackFinders(AddPackFindersEvent event) {
    if (event.getPackType() == PackType.SERVER_DATA) {
        event.addRepositorySource(packConsumer ->
            packConsumer.accept(Pack.readMetaAndCreate(
                "builtin/squire",
                Component.literal("Squire Built-in Data"),
                true, // always active
                new PathPackResources.PathResourcesSupplier(
                    ModList.get().getModFileById(SquireMod.MODID)
                        .getFile().findResource("data/squire")
                ),
                Pack.Position.BOTTOM,
                true // fixed position
            ))
        );
    }
}
```

**Simpler alternative:** Place progression JSON in `src/main/resources/data/squire/squire/progression/` and let NeoForge's mod resource loading pick it up automatically as part of the mod's resource pack. Test: delete world datapack folder, reload world, confirm tiers still load. If they don't, add the explicit AddPackFindersEvent registration.

### Pattern 7: Crest Item Summon/Recall Flow (v2 changes from v0.5.0)

**v0.5.0 recall behavior:** Navigates squire toward player (`squire.getNavigation().moveTo()`), doesn't despawn.
**v2 recall behavior (locked decision):** Instant despawn via `squire.discard()`. No navigation call.

**v0.5.0 summon behavior:** Spawns at clicked block face, plays HAPPY_VILLAGER particles.
**v2 summon behavior (locked decision):** Spawn at crosshair location with brief particle effect + ~1 second materialization animation. Particle choice is at Claude's discretion.

**One-squire enforcement — v0.5.0 pattern to keep:**
```java
// Count squires across ALL loaded levels — not just current
private int countPlayerSquires(ServerLevel level, ServerPlayer player) {
    int count = 0;
    for (ServerLevel serverLevel : level.getServer().getAllLevels()) {
        for (SquireEntity squire : serverLevel.getEntities(ModEntities.SQUIRE.get(), e -> true)) {
            if (squire.isAlive() && squire.isOwnedBy(player)) count++;
        }
    }
    return count;
}
```

**Recall lookup — v0.5.0 pattern to keep (checks all levels, not just current):**
```java
private SquireEntity findPlayerSquire(ServerLevel currentLevel, ServerPlayer player) {
    // Check current level first (common case), then others
    for (ServerLevel serverLevel : currentLevel.getServer().getAllLevels()) {
        for (SquireEntity squire : serverLevel.getEntities(ModEntities.SQUIRE.get(), e -> true)) {
            if (squire.isAlive() && squire.isOwnedBy(player)) return squire;
        }
    }
    return null;
}
```

### Pattern 8: Entity Registration in SquireRegistry

**What:** All DeferredRegister instances live in SquireRegistry. SquireMod constructor calls SquireRegistry.register(modEventBus) which calls each DeferredRegister.register(). Order matters.

**Registration order (critical):**
1. ENTITY_TYPES (EntityType<SquireEntity>)
2. ITEMS (SquireCrestItem) — items reference entity types for spawn eggs
3. ATTACHMENT_TYPES — must be registered before entity lifecycle events can use SQUIRE_DATA
4. CAPABILITIES (RegisterCapabilitiesEvent listener)

```java
// Source: v0.5.0 ModEntities.java pattern
public static final DeferredHolder<EntityType<?>, EntityType<SquireEntity>> SQUIRE =
        ENTITY_TYPES.register("squire", () -> EntityType.Builder
                .of(SquireEntity::new, MobCategory.MISC)  // MISC, not CREATURE — avoids MineColonies clearance
                .sized(0.6F, 1.8F)
                .ridingOffset(-0.7F)
                .clientTrackingRange(10)
                .build(SquireMod.MODID + ":squire"));

// Attribute registration via event:
@SubscribeEvent
public static void registerAttributes(EntityAttributeCreationEvent event) {
    event.put(SquireRegistry.SQUIRE.get(), SquireEntity.createAttributes().build());
}
```

### Pattern 9: Navigation Foundation (Set Now, Expanded in Phase 2)

Phase 1 entity constructor must configure navigation defaults. Late fixes require restructuring goal priorities.

```java
// In SquireEntity constructor:
this.setCanPickUpLoot(true);
this.setPersistenceRequired();
this.getNavigation().setCanFloat(true);  // prevents drowning in water
// OpenDoorGoal added to goalSelector in registerGoals() with priority 1-2
this.goalSelector.addGoal(1, new OpenDoorGoal(this, true));
```

**Path navigation:** v0.5.0 used default WalkNodeEvaluator. For water crossing (NAV-03), override createNavigation() in Phase 2. Set the foundation now so it's easy to extend.

### Anti-Patterns to Avoid

- **TamableAnimal base class:** Injects SitGoal, FollowOwnerGoal, TameGoal that fight a custom FSM. Use PathfinderMob.
- **SimpleContainer for inventory:** No tier-gated capacity, no IItemHandler capability surface without a wrapper. Use ItemStackHandler/IItemHandler.
- **SynchedEntityData in wrong class:** Pass SquireEntity.class to every defineId call. Never in a helper, never in a superclass.
- **config default fails its own validator:** defineInRange("x", 0, 1, 100) — default 0 fails min 1. Creates infinite correction loop.
- **Scattered DeferredRegister classes:** Registration order bugs are cryptic NPEs at startup. One SquireRegistry class only.
- **omitting .serialize(codec) on AttachmentType:** Data survives session but is lost on world reload.
- **Relying on world-level datapack ordering:** NeoForge issue #857 — embed progression JSON as builtin datapack.
- **Reading entity tags at registration time:** Tags load after FML setup events. Only read in live game code (tick, event handlers).

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Item storage with slot API | Custom slot array | `ItemStackHandler` (extends IItemHandler) | Handles stack merging, size, extractItem/insertItem contract correctly |
| Config file management | Custom TOML parser | `ModConfigSpec` + NeoForge config system | Handles file generation, hot-reload, server sync, validation |
| Cross-death data persistence | Custom player NBT writing | `AttachmentType` + Codec | NeoForge handles serialization, player death event integration |
| Entity type registration | Direct Registry.register calls | `DeferredRegister<EntityType<?>>` | DeferredRegister handles event timing; direct calls cause loading order issues |
| Test framework | Custom test runner | JUnit 5 (unit) + NeoForge GameTest (in-world) | ModDevGradle provides both; reinventing would miss tick lifecycle |

**Key insight:** NeoForge 1.21.x moved away from manual registration to DeferredRegister everywhere. Code that uses Registry.register() directly (not DeferredHolder) is a source of load-order bugs. All v2 registration goes through DeferredRegister in SquireRegistry.

---

## Common Pitfalls

### Pitfall 1: SynchedEntityData Class Mismatch

**What goes wrong:** Silent network desync. Wrong health bar, invisible entity, or `IllegalStateException: Entity class X has not defined synched data value N` crash on join.

**Why it happens:** defineId() IDs are sequential per class. Defining in a superclass or helper shifts all downstream IDs.

**How to avoid:** Always pass `SquireEntity.class` as first argument to every `SynchedEntityData.defineId()` in the project.

**Warning signs:** Entity renders on client but state (level, mode) is stale. Crash on reconnect referencing synched data value index.

### Pitfall 2: Config Validator/Default Mismatch (Correction Loop)

**What goes wrong:** Server log floods with `[Config] Incorrect value for squire-common.toml/...` every 2 seconds. Values reset mid-session. This was a documented v0.5.0 failure mode.

**Why it happens:** NeoForge runs defaults through the validator. `defineInRange("x", 0, 1, 100)` — default 0 fails min=1 validator.

**How to avoid:** For every `defineInRange()` entry, verify `min <= default <= max`. Write a startup assertion or unit test that constructs the spec and reads every value.

**Common offenders in v0.5.0 pattern:** Percentage values (0.0–1.0 range with default 0.0 and min > 0.0), tick intervals (default equals minimum).

### Pitfall 3: Builtin Datapack Not Loading After World Reload

**What goes wrong:** Progression tiers load on first world join but revert to defaults on reload. NeoForge issue #857 — world datapack list can desync.

**How to avoid:** Embed progression JSON as a builtin datapack via AddPackFindersEvent, or place in mod resources where NeoForge's mod resource loading picks it up unconditionally. Verify: delete `<world>/datapacks/` folder, reload, confirm tiers still present.

### Pitfall 4: AttachmentType Without .serialize(codec)

**What goes wrong:** Squire data (XP, level, name) persists for the session but is completely lost on world reload or server restart.

**How to avoid:** Always chain `.serialize(SquireData.CODEC)` on AttachmentType.builder. The v0.5.0 source has this correct — copy the pattern directly.

### Pitfall 5: PathfinderMob Navigation Defaults (Doors and Water)

**What goes wrong:** Squire stops at doors, sinks in water instead of swimming.

**Why it happens:** PathfinderMob default navigation doesn't open doors. Water navigation requires `setCanFloat(true)`.

**How to avoid:** Set in constructor: `this.getNavigation().setCanFloat(true)`. Add `OpenDoorGoal` with priority 1 in `registerGoals()`. Test before Phase 1 is marked done: walk squire through a door, across a river.

### Pitfall 6: OwnerHurtByTargetGoal with PathfinderMob

**What goes wrong:** `OwnerHurtByTargetGoal` and `OwnerHurtTargetGoal` call `getOwner()` which is a TamableAnimal method. These goals may not compile or work correctly when the base class is PathfinderMob.

**How to avoid:** Check if these goals have an overloaded version that accepts a `Supplier<LivingEntity>` for owner lookup. If not, implement `OwnableEntity` interface or replicate the goal functionality in CombatHandler (Phase 4). For Phase 1, use `HurtByTargetGoal(this)` as the minimum viable target goal — it handles reactive defense without owner tracking. Document this as a Phase 2 task.

**Verification:** Compile cleanly with PathfinderMob base and the goal classes. If they require TamableAnimal, note it and defer owner target sharing to Phase 2 CombatHandler.

### Pitfall 7: MobCategory.CREATURE Triggers MineColonies Clearance

**What goes wrong:** MineColonies' entity clearance logic targets entities in CREATURE category near colony buildings. Squire gets despawned unexpectedly.

**How to avoid:** Use `MobCategory.MISC` (confirmed in v0.5.0 ModEntities.java) — this is already the correct value in the reference code.

---

## Code Examples

### Entity Registration (SquireRegistry pattern)

```java
// Source: v0.5.0 ModEntities.java — adapted for SquireRegistry consolidation
public class SquireRegistry {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, SquireMod.MODID);
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(Registries.ITEM, SquireMod.MODID);
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, SquireMod.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<SquireEntity>> SQUIRE =
            ENTITY_TYPES.register("squire", () -> EntityType.Builder
                    .of(SquireEntity::new, MobCategory.MISC)
                    .sized(0.6F, 1.8F)
                    .ridingOffset(-0.7F)
                    .clientTrackingRange(10)
                    .build(SquireMod.MODID + ":squire"));

    public static void register(IEventBus modBus) {
        ENTITY_TYPES.register(modBus);   // 1st
        ITEMS.register(modBus);          // 2nd
        ATTACHMENT_TYPES.register(modBus); // 3rd
        modBus.addListener(SquireRegistry::registerAttributes);
        modBus.addListener(SquireRegistry::registerCapabilities);
    }

    private static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(SQUIRE.get(), SquireEntity.createAttributes().build());
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerEntity(
            Capabilities.ItemHandler.ENTITY,
            SQUIRE.get(),
            (squire, context) -> squire.getItemHandler()
        );
    }
}
```

### AttachmentType Codec (cross-death persistence)

```java
// Source: v0.5.0 SquireDataAttachment.java — use directly, this pattern is correct
public record SquireData(int totalXP, int level, String customName,
                          boolean slimModel, Optional<UUID> squireUUID) {
    public static final Codec<SquireData> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.INT.fieldOf("totalXP").forGetter(SquireData::totalXP),
                    Codec.INT.fieldOf("level").forGetter(SquireData::level),
                    Codec.STRING.fieldOf("customName").forGetter(SquireData::customName),
                    Codec.BOOL.fieldOf("slimModel").forGetter(SquireData::slimModel),
                    Codec.STRING.optionalFieldOf("squireUUID").xmap(
                            opt -> opt.map(UUID::fromString),
                            opt -> opt.map(UUID::toString)
                    ).forGetter(SquireData::squireUUID)
            ).apply(instance, SquireData::new));

    public static SquireData empty() {
        return new SquireData(0, 0, "Squire", false, Optional.empty());
    }
}
```

### NBT Save/Load (slot-indexed inventory preservation)

```java
// Source: v0.5.0 SquireInventory.toTag / fromTag — proven to preserve empty slot gaps
// Port this pattern to SquireItemHandler in v2

// Save:
public ListTag toTag(HolderLookup.Provider registries) {
    ListTag list = new ListTag();
    for (int i = 0; i < getSlots(); i++) {
        ItemStack stack = getStackInSlot(i);
        if (!stack.isEmpty()) {
            CompoundTag itemTag = new CompoundTag();
            itemTag.putByte("Slot", (byte) i);
            list.add(stack.save(registries, itemTag));
        }
    }
    return list;
}

// Load:
public void fromTag(ListTag list, HolderLookup.Provider registries) {
    for (int i = 0; i < list.size(); i++) {
        CompoundTag itemTag = list.getCompound(i);
        int slot = itemTag.getByte("Slot") & 255;
        if (slot < getSlots()) {
            ItemStack.parse(registries, itemTag).ifPresent(stack -> setStackInSlot(slot, stack));
        }
    }
}
```

### Config Entry (correct validator/default pattern)

```java
// Source: v0.5.0 SquireConfig.java — correct patterns (these pass their own validators)
followStartDistance = builder
        .comment("Distance (blocks) at which the squire begins following its owner.")
        .defineInRange("followStartDistance", 8.0, 2.0, 32.0);  // default 8.0, min 2.0, max 32.0 — OK

// WRONG pattern (causes correction loop):
// .defineInRange("x", 0, 1, 100)  // default 0 fails min=1

// ATM10-conservative overrides from v0.5.0 defaults:
// followStartDistance: 8.0 → 6.0 (shorter follow trigger for crowded packs)
// pathRecalcInterval: 10 → 15 (less frequent recalc under 449-mod load)
// aggroRange: 12.0 → 10.0 (less proactive aggro in pack environment)
```

### SquireTier Enum (slot count authority)

```java
// Phase 1 canonical enum — all slot counts locked per CONTEXT.md decisions
public enum SquireTier {
    SERVANT(0, 4, 9),
    APPRENTICE(5, 9, 18),
    SQUIRE(10, 19, 27),
    KNIGHT(20, 29, 32),
    CHAMPION(30, 30, 36);

    private final int minLevel;
    private final int maxLevel;
    private final int backpackSlots;

    SquireTier(int minLevel, int maxLevel, int backpackSlots) {
        this.minLevel = minLevel;
        this.maxLevel = maxLevel;
        this.backpackSlots = backpackSlots;
    }

    public int getBackpackSlots() { return backpackSlots; }
    public static final int EQUIPMENT_SLOTS = 6; // 4 armor + 1 weapon + 1 offhand, fixed

    public static SquireTier forLevel(int level) {
        for (SquireTier tier : values()) {
            if (level >= tier.minLevel && level <= tier.maxLevel) return tier;
        }
        return SERVANT;
    }
}
```

### Die() — Gear Drop vs Backpack Loss

```java
// Source: v0.5.0 SquireEntity.die() — adapted per CONTEXT.md locked decision
// On death: equipped gear drops, backpack inventory is LOST (not dropped)
@Override
public void die(DamageSource source) {
    if (!this.level().isClientSide) {
        if (this.getOwner() instanceof ServerPlayer owner) {
            // 1. Persist progression to player attachment BEFORE super.die()
            var data = owner.getData(SquireRegistry.SQUIRE_DATA.get());
            owner.setData(SquireRegistry.SQUIRE_DATA.get(),
                    data.withXP(this.progression.getTotalXP(), this.getSquireLevel())
                        .withName(this.hasCustomName() ? this.getCustomName().getString() : "Squire")
                        .withAppearance(this.isSlimModel())
                        .clearSquireUUID());

            // 2. Notify with coordinates
            owner.sendSystemMessage(Component.translatable(
                "squire.death.message",
                this.blockPosition().getX(), this.blockPosition().getY(), this.blockPosition().getZ()
            ));
        }
        // 3. Drop equipped gear (mainhand, offhand, armor) — NOT backpack
        // Equipment slots drop via vanilla LivingEntity.die() when loot tables are set
        // Backpack (SquireItemHandler) is NOT dropped — clear it silently
        this.itemHandler.clear(); // backpack lost on death per design decision
    }
    super.die(source);
}
```

---

## State of the Art

| Old Approach (v0.5.0) | Current Approach (v2) | Impact |
|----------------------|----------------------|--------|
| TamableAnimal base class | PathfinderMob + custom 80-line owner tracking | Eliminates vanilla goal conflicts; FSM has full control |
| SimpleContainer inventory | IItemHandler (ItemStackHandler) | Hopper/pipe automation works; tier-gated capacity |
| Scattered ModEntities/ModItems/ModBlocks | Single SquireRegistry | Registration order is explicit and safe |
| 27 fixed slots | Tier-gated 9/18/27/32/36 | Backpack growth feels like real progression |
| Static config defaults | Conservative ATM10 defaults + [debug] section | Pack-safe out of the box; operators can tune up |
| Implicit recall (navigation call) | Instant despawn | Cleaner UX; no "squire running home across the map" |

**Deprecated/outdated patterns NOT to use:**
- `TamableAnimal.tame(player)` — use custom `setOwnerUUID(player.getUUID())` instead
- `SimpleContainer` for entity inventory — no IItemHandler surface
- `GoalSelector` for main AI behaviors — only FloatGoal and target selectors in Phase 1
- Old NeoForge capability system (`ICapabilityProvider` / `LazyOptional`) — replaced by `RegisterCapabilitiesEvent` / `event.registerEntity()`

---

## Open Questions

1. **OwnerHurtByTargetGoal compatibility with PathfinderMob**
   - What we know: v0.5.0 uses TamableAnimal; these goals call `getOwner()` which is a TamableAnimal method
   - What's unclear: Whether these goal classes have an overloaded version or interface that works without TamableAnimal
   - Recommendation: Plan 01-02 implementer must check at compile time. If incompatible, use `HurtByTargetGoal` for Phase 1 and document owner target sharing as Phase 2 CombatHandler work.

2. **Builtin datapack registration method for NeoForge 1.21.1**
   - What we know: NeoForge issue #857 is the problem; AddPackFindersEvent is the canonical solution
   - What's unclear: Exact Pack.readMetaAndCreate signature in 21.1.221 — this changed between 1.20.x and 1.21.x
   - Recommendation: Plan 01-04 implementer should check NeoForge source for `AddPackFindersEvent` usage in 1.21.1 MDK examples. Fallback: place progression JSON in mod resources under standard data path and verify they load unconditionally without explicit registration.

3. **Equipment slot implementation for Phase 1 GUI stub**
   - What we know: 6 fixed equipment slots (4 armor + weapon + offhand) needed for INV-06
   - What's unclear: Whether the GUI stub in Phase 1 needs the equipment slots functional or just backpack slots
   - Recommendation: Phase 1 GUI stub should expose backpack slots only via a container screen. Equipment slots can be placeholders that become functional in Phase 3-4 when combat and auto-equip are implemented.

---

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit 5 (unit tests) + NeoForge GameTest (in-world) |
| Config file | No existing config — Wave 0 creates `src/test/` structure |
| Quick run command | `./gradlew test` (JUnit 5 only) |
| Full suite command | `./gradlew test runGameTestServer` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|------------------|-------------|
| ENT-01 | Crest summons squire into world | GameTest | `./gradlew runGameTestServer` | ❌ Wave 0 |
| ENT-02 | Crest recalls (despawns) squire | GameTest | `./gradlew runGameTestServer` | ❌ Wave 0 |
| ENT-03 | Squire NBT survives server restart | GameTest | `./gradlew runGameTestServer` | ❌ Wave 0 |
| ENT-04 | SquireData codec round-trip | Unit | `./gradlew test` | ❌ Wave 0 |
| ENT-05 | PathfinderMob base compiles, goals register | Unit/compile | `./gradlew compileJava` | ❌ Wave 0 |
| ENT-06 | One squire per player enforced | Unit | `./gradlew test` | ❌ Wave 0 |
| ENT-07 | Backpack cleared on death, progression preserved | GameTest | `./gradlew runGameTestServer` | ❌ Wave 0 |
| INV-01 | Tier slot counts correct | Unit | `./gradlew test` | ❌ Wave 0 |
| INV-02 | IItemHandler capability resolves | GameTest | `./gradlew runGameTestServer` | ❌ Wave 0 |
| INV-06 | GUI stub opens without crash | GameTest | `./gradlew runGameTestServer` | ❌ Wave 0 |
| ARC-05 | SquireRegistry registers all types | Unit/compile | `./gradlew compileJava` | ❌ Wave 0 |
| ARC-06 | 50+ config entries, no correction loop | Unit | `./gradlew test` | ❌ Wave 0 |
| ARC-07 | Progression JSON loads after datapack folder deleted | GameTest | `./gradlew runGameTestServer` | ❌ Wave 0 |
| TST-01 | JUnit 5 harness runs | Unit | `./gradlew test` | ❌ Wave 0 |
| TST-02 | GameTest harness runs | GameTest | `./gradlew runGameTestServer` | ❌ Wave 0 |

**Manual-only tests (cannot automate in Wave 0):**
- ARC-04 (SquireEntity/SquireBrain split): verified by code review, not runtime test
- ARC-09 (chunk loading hook): stub only in Phase 1; verified in Phase 6

### Sampling Rate

- **Per task commit:** `./gradlew compileJava` — catch registration and compilation errors immediately
- **Per plan merge:** `./gradlew test` — JUnit 5 suite must be green
- **Phase gate:** `./gradlew test runGameTestServer` — full suite green before Phase 2 begins

### Wave 0 Gaps

- [ ] `src/test/java/com/sjviklabs/squire/inventory/SquireItemHandlerTest.java` — covers INV-01, INV-02
- [ ] `src/test/java/com/sjviklabs/squire/entity/SquireDataAttachmentTest.java` — covers ENT-04, ENT-06
- [ ] `src/test/java/com/sjviklabs/squire/config/SquireConfigTest.java` — covers ARC-06 (validator/default check)
- [ ] `src/test/java/com/sjviklabs/squire/gametest/SquireEntityTest.java` — covers ENT-01, ENT-02, ENT-03, ENT-07, INV-06, ARC-07
- [ ] `src/test/resources/gametest/squire/` — GameTest template structure
- [ ] Framework install: `./gradlew test` — verify JUnit 5 resolves via ModDevGradle

---

## Sources

### Primary (HIGH confidence)

- v0.5.0 source: `C:/Users/Steve/Projects/squire-mod/src/` — read directly; all NBT tags, SynchedEntityData fields, config values, slot counts, summon/recall flow extracted from live code
- Prior stack research: `.planning/research/STACK.md` — NeoForge 21.1.221, ModDevGradle 2.0.141, dependency declarations verified
- Prior architecture research: `.planning/research/ARCHITECTURE.md` — IItemHandler pattern, AttachmentType pattern, SquireEntity/SquireBrain split, capability registration
- Prior pitfalls research: `.planning/research/PITFALLS.md` — SynchedEntityData class mismatch, config correction loop, datapack desync, capability invalidation

### Secondary (MEDIUM confidence)

- NeoForge docs — Capabilities (RegisterCapabilitiesEvent): https://docs.neoforged.net/docs/1.21.5/inventories/capabilities/
- NeoForge docs — Data Attachments: https://docs.neoforged.net/docs/datastorage/attachments/
- NeoForge docs — Mod Config: https://docs.neoforged.net/docs/1.21.1/configuration/
- NeoForge issue #857 — datapack desync (EOL/won't fix): https://github.com/neoforged/NeoForge/issues/857

### Tertiary (LOW confidence — flag for validation)

- AddPackFindersEvent exact signature for NeoForge 21.1.221: needs verification during Plan 01-04 implementation
- OwnerHurtByTargetGoal compatibility with PathfinderMob (non-TamableAnimal): needs compile-time verification during Plan 01-02

---

## Metadata

**Confidence breakdown:**

- Entity registration and NBT: HIGH — v0.5.0 source read directly, patterns are stable
- AttachmentType codec: HIGH — v0.5.0 source read directly, tested working
- IItemHandler capability registration: HIGH — NeoForge docs confirmed, pattern stable in 1.21.1
- Config system (ModConfigSpec): HIGH — v0.5.0 has 65 working entries to port
- Builtin datapack embed: MEDIUM — AddPackFindersEvent signature needs 1.21.1 verification
- OwnerHurtByTargetGoal with PathfinderMob: LOW — needs compile verification, may require workaround

**Research date:** 2026-04-02
**Valid until:** 2026-05-02 (NeoForge 1.21.1 is EOL, stack won't change)

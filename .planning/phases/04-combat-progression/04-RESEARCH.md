# Phase 4: Combat and Progression — Research

**Researched:** 2026-04-02
**Domain:** NeoForge 1.21.1 — Entity combat AI, custom items, data-driven tactics, XP/tier progression
**Confidence:** HIGH (v0.5.0 source read directly; architecture and pitfalls docs read; datapack JSON already embedded in codebase)

---

<phase_requirements>

## Phase Requirements

| ID | Description | Research Support |
|---|---|---|
| CMB-01 | Squire engages hostile mobs with melee weapons | v0.5.0 CombatHandler fully readable; tactic dispatch pattern proven; port to tag-based tactic selection |
| CMB-02 | Squire uses bows for ranged combat | v0.5.0 `tickRanged()` + `switchToRangedLoadout()` fully readable; state COMBAT_RANGED already in enum |
| CMB-03 | Squire auto-equips best weapon from inventory | v0.5.0 `SquireEquipmentHelper.runFullEquipCheck()` fully readable; port to IItemHandler API |
| CMB-04 | Squire auto-equips best armor from inventory | Same class, `isBetterArmor()` logic proven; direct port |
| CMB-05 | Squire uses shield to block incoming damage | v0.5.0 `updateShield()` + `onDamageTaken()` proven; `shieldBlocking` config exists in v2 |
| CMB-06 | Squire flees at low HP (configurable threshold) | `fleeHealthThreshold` config already in SquireConfig v2; DangerHandler stub needed |
| CMB-07 | Combat tactics are data-driven via entity tags (not hardcoded) | Key change from v0.5.0; SquireTagKeys + entity tag JSON pattern researched |
| CMB-08 | Halberd weapon with 360 sweep AoE every 3rd hit | New custom item; `halberdSweepInterval` config already in v2 SquireConfig |
| CMB-09 | Custom shield item (336 durability) | New custom SwordItem/ShieldItem subclass; simple registration |
| CMB-10 | Guard mode (stand ground and fight, don't follow) | `MODE_GUARD` SynchedEntityData byte already defined in SquireEntity v2 |
| PRG-01 | 5-tier progression: Servant/Apprentice/Squire/Knight/Champion | SquireTier enum and all 5 JSON datapack files already present in v2 |
| PRG-02 | Squire earns XP from kills, mining, and work tasks | v0.5.0 ProgressionHandler proven; `xpPerKill` config exists in v2 |
| PRG-03 | Tier thresholds and XP curves defined in JSON datapacks | 5 JSON files already embedded; need SimpleJsonResourceReloadListener loader |
| PRG-04 | 6 unlockable abilities tied to progression tiers | Ability unlock definitions need JSON + loader; ability constants need defining |
| PRG-05 | Tier gates unlock behaviors (combat at Apprentice, ranged at Squire, etc.) | Gate checks in CombatHandler conditioned on SquireTier; ARCHITECTURE.md confirms approach |
| PRG-06 | Champion tier grants "undying" revival ability | `undyingCooldownTicks` config exists in v2; revival event hook pattern researched |

</phase_requirements>

---

## Summary

Phase 4 has the most complete foundation of any phase to date. The v0.5.0 source gives a fully working CombatHandler and ProgressionHandler — not prototypes, full combat AI with five tactic modes, shield management, ranged switching, and XP-driven attribute scaling. The primary job for v2 is **porting with the architecture upgrade**, not redesigning.

The two meaningful additions over v0.5.0 are the data-driven tactics system (CMB-07) and the custom Halberd item (CMB-08). The tactics change is architecturally significant — replacing 40+ lines of instanceof chains in `selectTactic()` with a single tag check that modded mobs can opt into without code changes. The Halberd is a mechanical addition: a custom SwordItem that tracks a hit counter and deals AoE on the Nth hit.

The datapack layer (PRG-01/03) already has its JSON files on disk from Phase 1. What's missing is the `SimpleJsonResourceReloadListener` loader that reads those files into memory at world load and provides tier/ability data to ProgressionHandler and the behavior gate checks.

**Primary recommendation:** Port v0.5.0 CombatHandler and ProgressionHandler in parallel. The tactic system and datapack loader are the two genuinely new design problems — everything else is a clean port with an API surface swap (SquireInventory → IItemHandler).

---

## Standard Stack

### Core (already declared in project)

| Library | Version | Purpose | Why Standard |
|---|---|---|---|
| NeoForge | 21.1.x | Entity AI, attribute system, tags | Target platform |
| Minecraft 1.21.1 | 1.21.1 | `doHurtTarget()`, `performRangedAttack()`, `startUsingItem()` | Core combat API |

### Supporting (no new dependencies needed)

No new `build.gradle` dependencies for Phase 4. All required APIs are in NeoForge + Minecraft core:

- `net.minecraft.world.entity.ai.attributes.Attributes` — ATTACK_DAMAGE, MOVEMENT_SPEED, MAX_HEALTH, FOLLOW_RANGE
- `net.minecraft.world.entity.ai.attributes.AttributeModifier` — stable ResourceLocation IDs for progression bonuses
- `net.minecraft.tags.TagKey<EntityType<?>>` — combat tactics lookup
- `net.minecraft.server.level.ServerLevel` — XP particles and sounds in `onLevelUp()`
- `SimpleJsonResourceReloadListener` + `AddServerReloadListenersEvent` — already used for builtin datapack; same pattern for progression loader

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|---|---|---|
| Inline tag checks in CombatHandler | Separate TacticsResolver class | No benefit — tag checks are one-liners; a resolver class adds indirection without value |
| SimpleJsonResourceReloadListener | ResourceManager directly | SJRRL handles file discovery, error recovery, and reload lifecycle automatically |
| `AttributeModifier.Operation.ADD_VALUE` for level bonuses | Multiply or base-override | ADD_VALUE stacks correctly with enchantments and other mods' modifiers |

---

## Architecture Patterns

### What's Already Built (v2 Foundation)

Before any Phase 4 work begins, this is what exists:

| Component | Status | Notes |
|---|---|---|
| `SquireTier` enum | Complete | SERVANT/APPRENTICE/SQUIRE/KNIGHT/CHAMPION with level gates |
| `SquireEntity.SQUIRE_MODE` | Complete | Byte synched data with MODE_FOLLOW/SIT/GUARD = 0/1/2 |
| `SquireEntity.SQUIRE_LEVEL` | Complete | Int synched data, `setSquireLevel()` / `getSquireLevel()` available |
| `SquireConfig` combat section | Complete | aggroRange, combatLeashDistance, fleeHealthThreshold, halberdSweepInterval, shieldBlocking, shieldCooldownTicks, rangedRange |
| `SquireConfig` progression section | Complete | xpPerKill, xpPerBlock, xpPerHarvest, xpPerFish, healthPerLevel, damagePerLevel, speedPerLevel, undyingCooldownTicks |
| 5 tier JSON files | Complete | servant.json through champion.json with stats per tier |
| `SquireItemHandler` (IItemHandler) | Complete | Tiered slot capacity; use extractItem/insertItem (not SquireInventory.getItem) |
| `SquireBrain` stub | Stub | Phase 2 populates FSM; Phase 4 adds handlers to it |

### Pattern: Tag-Based Tactic Resolution (CMB-07)

**What:** Replace v0.5.0's `selectTactic()` instanceof chain with `target.getType().is(SquireTagKeys.X)`. Tactic is resolved once at combat start and cached in `CombatHandler.currentTactic` for the duration of the encounter.

**When to use:** Always. The v0.5.0 approach is the named anti-pattern in ARCHITECTURE.md.

**Critical timing rule (from Pitfall 7):** Tag resolution MUST happen inside live game code — inside `start()` when the target is first set, never in static initializers or FML setup. `TagKey<EntityType<?>>` constants are safe as static fields; `.is()` calls are not safe until the server is running.

```java
// data/SquireTagKeys.java
public final class SquireTagKeys {
    public static final TagKey<EntityType<?>> MELEE_AGGRESSIVE =
        EntityTypeTags.create(ResourceLocation.fromNamespaceAndPath("squire", "melee_aggressive"));
    public static final TagKey<EntityType<?>> MELEE_CAUTIOUS =
        EntityTypeTags.create(ResourceLocation.fromNamespaceAndPath("squire", "melee_cautious"));
    public static final TagKey<EntityType<?>> RANGED_EVASIVE =
        EntityTypeTags.create(ResourceLocation.fromNamespaceAndPath("squire", "ranged_evasive"));
    public static final TagKey<EntityType<?>> EXPLOSIVE_THREAT =
        EntityTypeTags.create(ResourceLocation.fromNamespaceAndPath("squire", "explosive_threat"));
    public static final TagKey<EntityType<?>> DO_NOT_ATTACK =
        EntityTypeTags.create(ResourceLocation.fromNamespaceAndPath("squire", "do_not_attack"));
    // No public constructor
    private SquireTagKeys() {}
}

// CombatHandler.selectTactic() — replaces v0.5.0 instanceof chain
private CombatTactic selectTactic(LivingEntity target) {
    if (target.getType().is(SquireTagKeys.EXPLOSIVE_THREAT)) return CombatTactic.EXPLOSIVE;
    if (target.getType().is(SquireTagKeys.DO_NOT_ATTACK))    return CombatTactic.PASSIVE;
    if (target.getType().is(SquireTagKeys.RANGED_EVASIVE))   return CombatTactic.EVASIVE;
    if (target.getType().is(SquireTagKeys.MELEE_CAUTIOUS))   return CombatTactic.CAUTIOUS;
    if (target.getType().is(SquireTagKeys.MELEE_AGGRESSIVE)) return CombatTactic.AGGRESSIVE;
    return CombatTactic.DEFAULT;
}
```

**Entity tag JSON (builtin datapack path):**
```
src/main/resources/data/squire/tags/entity_type/melee_aggressive.json
src/main/resources/data/squire/tags/entity_type/melee_cautious.json
src/main/resources/data/squire/tags/entity_type/ranged_evasive.json
src/main/resources/data/squire/tags/entity_type/explosive_threat.json
src/main/resources/data/squire/tags/entity_type/do_not_attack.json
```

**Example tag file (melee_aggressive.json):**
```json
{
  "replace": false,
  "values": [
    "minecraft:zombie",
    "minecraft:husk",
    "minecraft:zombie_villager",
    "minecraft:spider",
    "minecraft:cave_spider",
    "minecraft:hoglin"
  ]
}
```

### Pattern: ProgressionDataLoader (PRG-01/03/04)

**What:** `SimpleJsonResourceReloadListener` subclass registered on `AddServerReloadListenersEvent`. Reads the 5 tier JSONs and ability definitions from `data/squire/squire/progression/`. Populates static maps consulted by `ProgressionHandler` and behavior gate checks.

**Existing JSON format** (servant.json is representative — all 5 files present):
```json
{
  "tier": "servant",
  "min_level": 0,
  "backpack_slots": 9,
  "max_health": 20.0,
  "attack_damage": 3.0,
  "movement_speed": 0.30,
  "xp_to_next": 500,
  "description": "A new companion, eager to prove themselves."
}
```

**What the loader needs to produce:**
- `Map<SquireTier, TierDefinition>` — stats by tier (max_health, attack_damage, movement_speed, xp_to_next)
- `Map<String, AbilityDefinition>` — ability unlocks (from abilities.json, which does not yet exist)

**Registration pattern:**
```java
// In SquireMod or dedicated event subscriber
@SubscribeEvent
public static void onAddReloadListeners(AddServerReloadListenersEvent event) {
    event.addListener(new ProgressionDataLoader());
}
```

**Null-safety:** Loader data is null until first world load. All callers must null-check before reading tier stats. ProgressionHandler should fall back to SquireTier enum defaults if loader hasn't populated yet (e.g., during unit tests).

### Pattern: Halberd Item (CMB-08)

**What:** A `SwordItem` subclass that tracks a per-instance hit counter via NBT data component. On every 3rd hit (`halberdSweepInterval` from config), deal sweep AoE to all entities within 2.5 blocks.

**Key design decisions:**
- Hit counter lives in the item's NBT/data component, not in CombatHandler — survives item swap and inventory manipulation
- Sweep AoE uses `level().getEntitiesOfClass(LivingEntity.class, AABB, predicate)` — same as vanilla sweep
- Sweep damage = `getAttackDamage() * 0.75F` — matches vanilla sweep multiplier
- `halberdSweepInterval` is config-driven (default 3, range 1-10) — already in SquireConfig v2

```java
// In SquireHalberdItem.doHurtTargetHook (or via NeoForge LivingHurtEvent on squire side):
public void onHitTarget(ItemStack stack, LivingEntity target, LivingEntity attacker) {
    int hits = stack.getOrDefault(DataComponents.CUSTOM_DATA, ...).getInt("hits") + 1;
    if (hits >= SquireConfig.halberdSweepInterval.get()) {
        hits = 0;
        performSweep(attacker, target);
    }
    // store hits back to stack NBT
}
```

**Alternative approach:** Track hit counter in `CombatHandler.halberdHitCount` (int field). Simpler — no item NBT. Downside: resets if squire unequips and re-equips mid-combat. For an NPC this is fine. Recommended for v2 since the squire doesn't drop/swap weapons mid-tick.

### Pattern: Champion Undying (PRG-06)

**What:** When a Champion-tier squire would take lethal damage, absorb the death, set HP to 1, apply cooldown. Implemented via `LivingDeathEvent` listener or by overriding `die()` in SquireEntity.

**`undyingCooldownTicks` config** already exists in v2 SquireConfig.

**Implementation hook (NeoForge 1.21.1):**
```java
// In SquireEntity.die() override:
@Override
public void die(DamageSource source) {
    if (SquireTier.fromLevel(getSquireLevel()) == SquireTier.CHAMPION
            && undyingCooldown <= 0
            && undyingEnabled()) {
        setHealth(1.0F);
        undyingCooldown = SquireConfig.undyingCooldownTicks.get();
        // Particle + sound fanfare
        // ... do NOT call super.die()
        return;
    }
    super.die(source);
}
```

**Cooldown must be synched** — the `undyingCooldown` counter needs either a SynchedEntityData entry or a server-only field (acceptable since undying logic is server-side only).

### Pattern: Auto-Equip Port to IItemHandler (CMB-03/04)

v0.5.0 `SquireEquipmentHelper` used `SquireInventory` (SimpleContainer derivative) directly. v2 uses `SquireItemHandler` (IItemHandler). The port requires:

1. Replace `inv.getContainerSize()` with `inv.getSlots()`
2. Replace `inv.getItem(i)` with `inv.getStackInSlot(i)` — READ ONLY (Pitfall 5)
3. Replace `inv.removeItemNoUpdate(i)` with `inv.extractItem(i, 1, false)`
4. Replace `inv.addItem(stack)` with a loop over slots calling `inv.insertItem(i, stack, false)` until remainder is empty

**Critical:** Never mutate the stack returned by `getStackInSlot()`. Always extract then insert.

### Pattern: Flee State (CMB-06)

`fleeHealthThreshold` (default 0.25) is already in config. Flee logic in v0.5.0 was inline in `tickCautious()`. In v2, flee should be a global FSM transition at priority 8 (below survival priority 1-9):

```
Global transition: ANY_STATE → COMBAT_FLEE
  condition: squire.getHealth() / squire.getMaxHealth() < SquireConfig.fleeHealthThreshold.get()
             && squire.getTarget() != null
  action: combatHandler.startFlee()
  tickRate: 5
  priority: 8
```

Flee behavior: run toward owner at 1.4 speed, clear target, disengage after 3 seconds or when at safe distance.

### Pattern: Guard Mode (CMB-10)

`MODE_GUARD` is already defined as `(byte)2` in `SquireEntity`. Guard mode means:
- Do NOT use FollowHandler (no movement toward owner)
- DO engage any mob within aggroRange
- Stand at current position when no threat is present

In CombatHandler.tick(), check `squire.getSquireMode() == MODE_GUARD` to skip the leash-breach check that would disengage combat if the owner moves away.

### Recommended Structure for Phase 4

```
brain/handler/
├── CombatHandler.java      # melee tactics, ranged, shield, flee, guard mode
├── DangerHandler.java      # explosive flee (extracted from EXPLOSIVE tactic)
└── (ProgressionHandler moves to progression/ package)

progression/
├── ProgressionHandler.java       # XP accounting, level gates, attribute modifiers
├── ProgressionDataLoader.java    # SimpleJsonResourceReloadListener
├── TierDefinition.java           # Data class: parsed from tier JSON
└── AbilityDefinition.java        # Data class: parsed from abilities.json

data/
└── SquireTagKeys.java            # TagKey<EntityType<?>> constants

item/
├── SquireCrestItem.java          # (Phase 1 — already exists)
├── SquireHalberdItem.java        # NEW: SwordItem with sweep AoE counter
└── SquireShieldItem.java         # NEW: ShieldItem with 336 durability

src/main/resources/data/squire/
├── tags/entity_type/
│   ├── melee_aggressive.json
│   ├── melee_cautious.json
│   ├── ranged_evasive.json
│   ├── explosive_threat.json
│   └── do_not_attack.json
└── squire/progression/
    ├── servant.json          # already exists
    ├── apprentice.json       # already exists
    ├── squire_tier.json      # already exists
    ├── knight.json           # already exists
    ├── champion.json         # already exists
    └── abilities.json        # NEW: 6 ability unlock definitions
```

### Anti-Patterns to Avoid

- **instanceof in selectTactic():** The v0.5.0 pattern is the explicit anti-pattern. Tag checks are the replacement. No exceptions.
- **Reading tags during class init or FMLCommonSetupEvent:** Tags are empty at that point. Resolve lazily in `start()` only.
- **Mutating IItemHandler stacks in-place:** Always extractItem/insertItem. The v2 inventory is IItemHandler, not SimpleContainer.
- **Hit counter for Halberd in a static field:** One static counter would be shared across all squires. Field must be on the handler instance or in the item stack NBT.
- **Calling `super.die()` and applying undying:** Pick one. If undying fires, return early without calling super.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---|---|---|---|
| Attack damage comparison | Custom item comparison logic | `ItemAttributeModifiers` via `stack.getAttributeModifiers()` | Already proven in v0.5.0 `getAttackDamage()` — handles all modded weapons that declare ATTACK_DAMAGE modifier |
| Armor quality comparison | Defense formula from scratch | `ArmorItem.getDefense() + getToughness()` | Handles all ArmorItem subclasses including modded armor correctly |
| Tag resolution | Custom entity type registry | `EntityType.is(TagKey)` | NeoForge tag system is exactly this problem |
| JSON loading lifecycle | Custom file reader | `SimpleJsonResourceReloadListener` | Handles hot-reload (/reload command), error recovery, and load ordering |
| Attribute persistence across sessions | Saving attribute values in NBT | Stable `ResourceLocation` IDs on `AttributeModifier` | NeoForge saves/loads persistent modifiers automatically; saving values manually causes double-application on load |
| Sweep AoE targeting | Custom hit detection | `ServerLevel.getEntitiesOfClass(LivingEntity.class, AABB, predicate)` | Vanilla sweep uses exactly this pattern |

---

## Common Pitfalls

### Pitfall A: Entity Tags Empty at Combat Start on First World Join

**What goes wrong:** All tag checks in `selectTactic()` return false. Every mob gets DEFAULT tactic.

**Why it happens:** If tactic resolution is called during any FML setup event or before the world's datapack finishes loading, tags are empty.

**How to avoid:** Resolve tactic only inside `CombatHandler.start()`, which fires on the first combat tick — well after the world is running. `TagKey` constants are fine as static fields; `.is()` calls must be in live game code.

**Warning signs:** All modded mobs and vanilla mobs get DEFAULT tactic in tests.

---

### Pitfall B: Attribute Modifiers Double-Apply on World Reload

**What goes wrong:** Squire has inflated stats after server restart. Level 10 squire has double the expected HP bonus.

**Why it happens:** v0.5.0 ProgressionHandler's `applyModifiers()` adds modifiers with stable IDs — `removeModifier(id)` before `addPermanentModifier()`. If the NBT load path calls `applyModifiers()` and Minecraft also restores saved permanent modifiers from entity NBT, the modifier is applied twice.

**How to avoid:** Always call `instance.removeModifier(id)` before `instance.addPermanentModifier()` — v0.5.0 already does this correctly. Do NOT save attribute modifier values redundantly in progression NBT.

**Warning signs:** Stats increase on every world reload without gaining levels.

---

### Pitfall C: IItemHandler Mutation in Auto-Equip

**What goes wrong:** Item duplication or stack corruption during `runFullEquipCheck()`.

**Why it happens:** Calling `stack.shrink()` or direct mutation on a stack returned by `getStackInSlot()` violates the IItemHandler contract (Pitfall 5 from PITFALLS.md).

**How to avoid:** Every item removal uses `extractItem(slot, amount, false)`. Every item insert uses `insertItem(slot, stack, false)`. Check for non-empty remainder after insert.

---

### Pitfall D: Capability Invalidation on Tier Change

**What goes wrong:** External automation stops working after squire levels up (Pitfall 6 from PITFALLS.md).

**Why it happens:** SquireItemHandler's slot count expands on tier advance. Cached capability references held by pipes/hoppers are stale.

**How to avoid:** In `ProgressionHandler.onLevelUp()`, call `squire.invalidateCapabilities()` after expanding slots. This is a Phase 4 responsibility because tier advancement triggers the expansion.

---

### Pitfall E: Halberd Sweep Hitting Friendly Entities

**What goes wrong:** Sweep AoE hits the player-owner or other friendly mobs.

**Why it happens:** `getEntitiesOfClass()` returns all LivingEntities in the AABB, including the owner and tamed pets.

**How to avoid:** Predicate must exclude: `attacker` (self), squire's owner, and any entity the squire wouldn't normally target. Mirror the vanilla sweep exclusion: `entity != attacker && entity != target && entity.getType() != EntityType.PLAYER` for NPC-specific behavior, but include owner check explicitly.

---

### Pitfall F: Attack Animation Duration (PathfinderMob)

**What goes wrong:** Halberd swing animation cuts to idle after 6 ticks regardless of animation length.

**Why it happens:** PathfinderMob does not call `updateSwingTime()` automatically (only Monster does). Default `getCurrentSwingDuration()` returns 6 ticks.

**How to avoid:** Override both in SquireEntity:
```java
@Override
public int getCurrentSwingDuration() { return 10; } // match halberd animation length

@Override
public void aiStep() {
    super.aiStep();
    this.updateSwingTime(); // PathfinderMob skips this
}
```

(Already documented in PITFALLS.md Pitfall 3, repeated here because it's directly relevant to the Halberd item in this phase.)

---

### Pitfall G: Undying Fires on Void/Insta-Kill Damage

**What goes wrong:** Undying activates for damage that should kill even champions (void, `/kill` command, warden sonic boom with enough power).

**Why it happens:** The `die()` override intercepts all death causes equally.

**How to avoid:** Check `DamageSource` before blocking death. Skip undying for `source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)` — these are the void and admin kill sources. This is the same guard vanilla totems use.

---

## Code Examples

### Tactic Resolution (Tag-Based)

```java
// Source: ARCHITECTURE.md anti-pattern section + NeoForge tags docs
// In CombatHandler — called inside start(), not at class load time
private CombatTactic selectTactic(LivingEntity target) {
    EntityType<?> type = target.getType();
    if (type.is(SquireTagKeys.EXPLOSIVE_THREAT)) return CombatTactic.EXPLOSIVE;
    if (type.is(SquireTagKeys.DO_NOT_ATTACK))    return CombatTactic.PASSIVE;
    if (type.is(SquireTagKeys.RANGED_EVASIVE))   return CombatTactic.EVASIVE;
    if (type.is(SquireTagKeys.MELEE_CAUTIOUS))   return CombatTactic.CAUTIOUS;
    if (type.is(SquireTagKeys.MELEE_AGGRESSIVE)) return CombatTactic.AGGRESSIVE;
    return CombatTactic.DEFAULT;
}
```

### Attribute Modifier — Stable ID Pattern

```java
// Source: v0.5.0 ProgressionHandler.java (read directly)
private static final ResourceLocation LEVEL_HEALTH_ID =
        ResourceLocation.fromNamespaceAndPath("squire", "level_health_bonus");

private void setModifier(Holder<Attribute> attribute, ResourceLocation id, double amount) {
    AttributeInstance instance = squire.getAttribute(attribute);
    if (instance == null) return;
    instance.removeModifier(id); // always remove first
    if (amount > 0) {
        instance.addPermanentModifier(
            new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_VALUE));
    }
}
```

### IItemHandler-Safe Equip Swap

```java
// v2 port of SquireEquipmentHelper.swapEquipmentFromSlot()
// Source: v0.5.0 SquireEquipmentHelper.java + IItemHandler contract
private static void swapEquipmentFromSlot(SquireEntity squire,
                                           EquipmentSlot slot,
                                           IItemHandler inv,
                                           int invIdx) {
    ItemStack old = squire.getItemBySlot(slot);
    ItemStack newItem = inv.extractItem(invIdx, 64, false); // extract full stack

    squire.setItemSlot(slot, newItem);
    playEquipSound(squire, slot);

    if (!old.isEmpty()) {
        // Insert old item back; find first available slot
        for (int i = 0; i < inv.getSlots(); i++) {
            old = inv.insertItem(i, old, false);
            if (old.isEmpty()) break;
        }
        // If old still not empty: drop it (edge case — inventory full)
        if (!old.isEmpty()) {
            squire.spawnAtLocation(old);
        }
    }
}
```

### ProgressionDataLoader Skeleton

```java
// Source: NeoForge SimpleJsonResourceReloadListener pattern
// https://docs.neoforged.net/docs/1.21.1/resources/server/datapacks/
public class ProgressionDataLoader extends SimpleJsonResourceReloadListener {

    private static final Gson GSON = new GsonBuilder().create();
    private static Map<SquireTier, TierDefinition> tierData = Map.of();

    public ProgressionDataLoader() {
        super(GSON, "squire/progression");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> objects,
                         ResourceManager mgr, ProfilerFiller profiler) {
        var builder = new HashMap<SquireTier, TierDefinition>();
        for (var entry : objects.entrySet()) {
            JsonObject json = entry.getValue().getAsJsonObject();
            String tierName = json.get("tier").getAsString();
            SquireTier tier = SquireTier.valueOf(tierName.toUpperCase());
            builder.put(tier, TierDefinition.fromJson(json));
        }
        tierData = Map.copyOf(builder);
    }

    public static Map<SquireTier, TierDefinition> getTierData() { return tierData; }
}
```

### Sweep AoE (Halberd)

```java
// Source: vanilla SweepAttackGoal pattern + Minecraft ServerLevel API
private void performSweep(LivingEntity attacker, LivingEntity primaryTarget) {
    float sweepDmg = (float) attacker.getAttributeValue(Attributes.ATTACK_DAMAGE) * 0.75F;
    AABB sweepBox = attacker.getBoundingBox().inflate(2.5, 0.25, 2.5);
    List<LivingEntity> nearby = attacker.level().getEntitiesOfClass(
        LivingEntity.class, sweepBox,
        e -> e != attacker
             && e != primaryTarget
             && !e.is(squire.getOwner()) // don't hit owner
             && squire.wantsToAttack(e, squire.getOwner()) // target selector
    );
    for (LivingEntity hit : nearby) {
        hit.hurt(attacker.damageSources().mobAttack(attacker), sweepDmg);
    }
    attacker.level().playSound(null, attacker.blockPosition(),
        SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0F, 1.0F);
}
```

---

## State of the Art

| Old Approach (v0.5.0) | v2 Approach | Impact |
|---|---|---|
| `instanceof Zombie` chains in `selectTactic()` | `type.is(SquireTagKeys.X)` | Modded mobs work without code changes |
| Config-hardcoded XP values only | Config values + JSON datapack overrides | Server ops can tune progression per-server |
| `ProgressionHandler` uses linear `totalXP / xpPerLevel` | `SquireTier.fromLevel()` from JSON thresholds | Per-tier XP curves instead of linear; supports non-uniform tier gaps |
| SquireInventory (SimpleContainer) in equipment helper | IItemHandler via extractItem/insertItem | Correct capability contract; no corruption with pipes/hoppers |
| Hit counter for sweep in a field | Same — CombatHandler field is fine for NPC | No behavior change needed; NPC doesn't swap weapons mid-tick |

**Deprecated/outdated from v0.5.0:**

- `SquireInventory.addItem()` — replaced by IItemHandler.insertItem() loop
- `SquireInventory.removeItemNoUpdate()` — replaced by IItemHandler.extractItem()
- `SquireAbilities.hasRangedCombat(squire)` — v0.5.0 utility class; v2 uses `SquireTier.fromLevel()` gate checks inline or via a thin wrapper
- `squire.getSquireAI()` — v0.5.0 accessor; v2 accesses brain through `squire.getBrain()`

---

## Open Questions

1. **Abilities.json schema — what 6 abilities and what format?**
   - What we know: PRG-04 requires 6 unlockable abilities; ability gate checks are referenced in ARCHITECTURE.md; `undyingCooldownTicks` config exists for PRG-06
   - What's unclear: The other 5 abilities aren't defined in REQUIREMENTS.md or existing JSON. PRG-05 gives hints: "combat at Apprentice, ranged at Squire, mounting at Knight"
   - Recommendation: Define the 6 abilities as: `COMBAT` (Apprentice), `SHIELD_BLOCK` (Apprentice), `RANGED_COMBAT` (Squire), `LIFESTEAL` (Knight), `MOUNTING` (Knight), `UNDYING` (Champion). Encode in abilities.json as `{ "ability": "COMBAT", "min_tier": "APPRENTICE" }`. Gate checks in handlers: `if (SquireTier.fromLevel(squire.getSquireLevel()).ordinal() >= requiredTier.ordinal())`.

2. **Does DangerHandler run in Phase 4 or Phase 2?**
   - What we know: ARCHITECTURE.md puts DangerHandler as a handler alongside CombatHandler; the Phase 4 plan (04-01) explicitly includes DangerHandler; v0.5.0 CombatHandler handles explosive flee inline in `tickExplosive()`
   - What's unclear: Whether DangerHandler should be a separate class or inline in CombatHandler as in v0.5.0
   - Recommendation: Keep explosive flee inline in CombatHandler for Phase 4 (it's a tactic variant, not a separate behavior). DangerHandler as a separate class makes more sense when the danger set expands (lava, fall damage, etc.) in a future phase.

3. **SquireTier enum vs. datapack-driven tier boundaries — which wins?**
   - What we know: `SquireTier` enum hardcodes `min_level` (0, 5, 10, 20, 30); JSON datapacks also declare `min_level`; the PRG-03 requirement says thresholds come from datapacks
   - What's unclear: Should the enum be the authority or should the loader override it?
   - Recommendation: The loader is the authority. `SquireTier.fromLevel()` should be refactored in Phase 4 to read from `ProgressionDataLoader.getTierData()` rather than the enum's hardcoded values. The enum remains for type-safety and ordering but its `minLevel` field becomes a fallback when the loader hasn't populated yet.

---

## Validation Architecture

### Test Framework

| Property | Value |
|---|---|
| Framework | JUnit 5 (existing — SquireItemHandlerTest, SquireDataAttachmentTest, SquireConfigTest present) |
| Config file | `src/test/resources/junit-platform.properties` (or project default) |
| Quick run command | `./gradlew test --tests "com.sjviklabs.squire.*"` |
| Full suite command | `./gradlew test` |

### Phase 4 Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|---|---|---|---|---|
| CMB-01 | Melee engage + attack cooldown fires | unit | `./gradlew test --tests "*.CombatHandlerTest"` | No — Wave 0 |
| CMB-02 | Ranged switch when beyond rangedRange | unit | `./gradlew test --tests "*.CombatHandlerTest"` | No — Wave 0 |
| CMB-03 | Best weapon selected from IItemHandler inventory | unit | `./gradlew test --tests "*.SquireEquipmentHelperTest"` | No — Wave 0 |
| CMB-04 | Best armor selected by defense+toughness score | unit | `./gradlew test --tests "*.SquireEquipmentHelperTest"` | No — Wave 0 |
| CMB-05 | Shield raised on damage (AGGRESSIVE/DEFAULT tactics) | unit | `./gradlew test --tests "*.CombatHandlerTest"` | No — Wave 0 |
| CMB-06 | Flee state triggered at fleeHealthThreshold | unit | `./gradlew test --tests "*.CombatHandlerTest"` | No — Wave 0 |
| CMB-07 | Tag-based tactic returns correct enum for each tag | unit | `./gradlew test --tests "*.TacticResolutionTest"` | No — Wave 0 |
| CMB-08 | Halberd sweep fires on Nth hit, not N-1 or N+1 | unit | `./gradlew test --tests "*.SquireHalberdItemTest"` | No — Wave 0 |
| CMB-09 | Custom shield durability = 336 | unit | `./gradlew test --tests "*.SquireShieldItemTest"` | No — Wave 0 |
| CMB-10 | Guard mode suppresses leash-breach disengage | unit | `./gradlew test --tests "*.CombatHandlerTest"` | No — Wave 0 |
| PRG-01 | All 5 tiers have correct level thresholds | unit | `./gradlew test --tests "*.ProgressionDataLoaderTest"` | No — Wave 0 |
| PRG-02 | Kill XP increments totalXP, level recalculates | unit | `./gradlew test --tests "*.ProgressionHandlerTest"` | No — Wave 0 |
| PRG-03 | JSON loader reads servant.json → TierDefinition | unit | `./gradlew test --tests "*.ProgressionDataLoaderTest"` | No — Wave 0 |
| PRG-04 | Ability definition loaded; gate check returns correct bool | unit | `./gradlew test --tests "*.ProgressionDataLoaderTest"` | No — Wave 0 |
| PRG-05 | Tier gate blocks combat at Servant level | unit | `./gradlew test --tests "*.ProgressionHandlerTest"` | No — Wave 0 |
| PRG-06 | Champion undying absorbs lethal hit, respects cooldown | unit | `./gradlew test --tests "*.ProgressionHandlerTest"` | No — Wave 0 |

### Sampling Rate

- **Per task commit:** `./gradlew test --tests "com.sjviklabs.squire.brain.handler.*" --tests "com.sjviklabs.squire.progression.*" --tests "com.sjviklabs.squire.item.*"`
- **Per wave merge:** `./gradlew test`
- **Phase gate:** Full suite green before `/gsd:verify-work`

### Wave 0 Gaps

- [ ] `src/test/java/com/sjviklabs/squire/brain/handler/CombatHandlerTest.java` — covers CMB-01/02/05/06/10
- [ ] `src/test/java/com/sjviklabs/squire/inventory/SquireEquipmentHelperTest.java` — covers CMB-03/04
- [ ] `src/test/java/com/sjviklabs/squire/brain/handler/TacticResolutionTest.java` — covers CMB-07 (mock entity type + tag check)
- [ ] `src/test/java/com/sjviklabs/squire/item/SquireHalberdItemTest.java` — covers CMB-08
- [ ] `src/test/java/com/sjviklabs/squire/item/SquireShieldItemTest.java` — covers CMB-09
- [ ] `src/test/java/com/sjviklabs/squire/progression/ProgressionHandlerTest.java` — covers PRG-02/05/06
- [ ] `src/test/java/com/sjviklabs/squire/progression/ProgressionDataLoaderTest.java` — covers PRG-01/03/04

Note: NeoForge GameTests (in-world entity verification for TST-02) cannot be run with `./gradlew test`. Those require `./gradlew runGameTestServer` and are integration smoke tests, not unit coverage. The above tests are pure JUnit 5 unit tests that mock entity/inventory state without requiring a running Minecraft instance.

---

## Sources

### Primary (HIGH confidence)

- `C:/Users/Steve/Projects/squire-mod/src/main/java/com/sjviklabs/squire/ai/handler/CombatHandler.java` — full tactic system, shield management, ranged logic, flee detection, leash breach
- `C:/Users/Steve/Projects/squire-mod/src/main/java/com/sjviklabs/squire/ai/handler/ProgressionHandler.java` — XP system, level recalc, attribute modifiers with stable ResourceLocation IDs, NBT persistence
- `C:/Users/Steve/Projects/squire-mod/src/main/java/com/sjviklabs/squire/util/SquireEquipmentHelper.java` — auto-equip logic, isBetterArmor/isBetterWeapon, melee/ranged loadout switching
- `C:/Users/Steve/Projects/squire-mod-v2/src/main/java/com/sjviklabs/squire/config/SquireConfig.java` — all combat and progression config values already defined
- `C:/Users/Steve/Projects/squire-mod-v2/src/main/java/com/sjviklabs/squire/entity/SquireTier.java` — tier enum with level thresholds
- `C:/Users/Steve/Projects/squire-mod-v2/src/main/resources/data/squire/squire/progression/*.json` — all 5 tier JSONs verified present
- `.planning/research/ARCHITECTURE.md` — tag-based tactic pattern, entity tag JSON structure, ProgressionDataLoader registration
- `.planning/research/PITFALLS.md` — Pitfall 3 (attack animation), Pitfall 5 (IItemHandler mutation), Pitfall 6 (capability invalidation on tier change), Pitfall 7 (entity tags timing)

### Secondary (MEDIUM confidence)

- NeoForge Tags docs — https://docs.neoforged.net/docs/1.21.1/resources/server/tags/ — `EntityType.is(TagKey)` pattern confirmed
- NeoForge datapack loading — `SimpleJsonResourceReloadListener` registration via `AddServerReloadListenersEvent` confirmed in ARCHITECTURE.md

---

## Metadata

**Confidence breakdown:**

- Standard stack: HIGH — no new dependencies; all required APIs in NeoForge + Minecraft core
- Architecture: HIGH — v0.5.0 source read directly; ARCHITECTURE.md provides canonical patterns; only tactic refactor and data loader are genuinely new
- Combat patterns: HIGH — all five tactic modes fully readable in v0.5.0 CombatHandler
- Progression patterns: HIGH — v0.5.0 ProgressionHandler fully readable; attribute modifier pattern proven
- Pitfalls: HIGH — all four relevant pitfalls (tag timing, capability invalidation, IItemHandler mutation, animation duration) documented from primary sources
- Custom items: MEDIUM — Halberd and custom shield are new classes; sweep AoE pattern is vanilla-derived but implementation is v2-specific design work

**Research date:** 2026-04-02
**Valid until:** 2026-05-02 (NeoForge 1.21.1 is EOL; no API changes expected; stable for 30 days)

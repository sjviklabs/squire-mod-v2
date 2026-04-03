# Phase 8: Compatibility and Polish - Research

**Researched:** 2026-04-02
**Domain:** NeoForge 1.21.1 optional mod compatibility (MineColonies, Jade, Curios), dedicated server safety
**Confidence:** MEDIUM — MineColonies clearance API does not exist; correct approach is entity classification + package-scan. Jade and Curios APIs verified from source. Exact Jade interface method signatures confirmed via Mekanism commit example.

---

<phase_requirements>

## Phase Requirements

| ID     | Description                                                                     | Research Support                                                                                  |
| ------ | ------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------- |
| CMP-01 | MineColonies compatibility — prevent citizen/squire AI interference or friendly-fire | MobCategory.MISC + package-scan for citizen/raider detection is the correct approach (no whitelist API exists in MineColonies 1.1.1231) |
| CMP-02 | Jade/WAILA tooltip overlay showing squire status                                | IEntityComponentProvider pattern confirmed; appendTooltip(ITooltip, EntityAccessor, IPluginConfig) |
| CMP-03 | Curios/Accessories integration — soft dependency, mod works without it          | Datapack JSON entity assignment + CuriosCapability.ID_INVENTORY confirmed for NeoForge 1.21.x    |
| INV-05 | Curios equipment slots functional when Curios present                           | Same as CMP-03; slot type JSON + entity assignment JSON covers implementation                      |

</phase_requirements>

---

## Summary

Phase 8 is a three-track implementation: MineColonies entity coexistence, Jade HUD overlay, and Curios equipment slots. All three are optional dependencies that must degrade gracefully when absent.

**Critical pre-research finding:** The STATE.md research flag said "verify MineColonies whitelist API method signatures." Research confirmed there is NO entity whitelist API in MineColonies 1.1.1231's `ICompatibilityManager`. The interface manages item/block compat only (fuels, foods, ores, saplings). The correct MineColonies compat approach — already used in v0.5.0 — is to use `MobCategory.MISC` and a class package hierarchy scan to detect colonists/raiders. The v0.5.0 implementation was correct in principle; the v2 implementation can mirror it with the API-based detection removed and replaced by clean package-scan only.

The Jade API uses `IEntityComponentProvider` with `appendTooltip(ITooltip, EntityAccessor, IPluginConfig)` registered via `IWailaCommonRegistration.registerEntityComponent()`. The v0.5.0 `JadeCompat.java` was a data-formatting helper only — it never registered a real Jade plugin. Phase 8 must register an `@WailaPlugin` annotated class.

Curios integration uses two JSON datapack files (slot type definition + entity assignment) plus runtime capability access via `CuriosCapability.ID_INVENTORY`. No compile-time code registration is required for slot presence — only the data files.

**Primary recommendation:** Implement all three compat classes with `ModList.get().isLoaded()` guards at every call site. Drive MineColonies detection via package-scan (no API import needed). Register a real Jade `@WailaPlugin`. Use datapack JSON for Curios slot assignment with `CuriosApi.getCuriosInventory()` for runtime access.

---

## Standard Stack

### Core (Phase 8 specific)

| Library          | Version           | Purpose                             | Why Standard                                                          |
| ---------------- | ----------------- | ----------------------------------- | --------------------------------------------------------------------- |
| Jade             | 15.10.4+neoforge  | Entity HUD tooltip overlay          | `compileOnly` dep already in build.gradle; `@WailaPlugin` annotation is the standard registration path |
| Curios API       | 9.5.1+1.21.1      | Equipment slots on entities         | `compileOnly` API jar already in build.gradle; datapack JSON for slot assignment |
| MineColonies     | 1.1.1231-1.21.1   | Citizen/raider detection             | v0.5.0 confirmed: use class package scan — no API import needed at all |
| NeoForge ModList | 21.1.221          | Soft-dep guard at call sites         | `ModList.get().isLoaded("modid")` is the NeoForge standard for optional mod detection |

### Supporting

| Library          | Version  | Purpose                             | When to Use                                      |
| ---------------- | -------- | ----------------------------------- | ------------------------------------------------ |
| NeoForge FMLEnvironment | 21.1.221 | Client/server environment check | Prevent client-only classes from loading on dedicated server |

### Alternatives Considered

| Instead of         | Could Use                        | Tradeoff                                                    |
| ------------------ | -------------------------------- | ----------------------------------------------------------- |
| Package-scan for MineColonies detection | MineColonies API import (ICompatibilityManager) | No whitelist API exists; API only covers item/block compat |
| Datapack JSON for Curios slots | Programmatic CuriosApi.addSlots() | JSON is the documented approach; programmatic registration is not in the public API |
| @WailaPlugin annotation | Manual registration in event | Annotation is the only supported Jade 15.x registration method |

**Confirmed coordinates (MineColonies):** The STATE.md flag from 01-01 said "ldtteam Jfrog has no 1.21.1 artifacts." This was correct at build time. However, Phase 8 does NOT require a MineColonies compile dependency at all — v0.5.0's package-scan approach (`isFromPackage()` walking the class hierarchy) works without any import and is the right model for v2.

---

## Architecture Patterns

### Recommended Package Structure

```
src/main/java/com/sjviklabs/squire/compat/
├── MineColoniesCompat.java   # package-scan detection, friendly-fire gate, ModList guard
├── JadeCompat.java           # @WailaPlugin annotated class, IEntityComponentProvider impl
└── CuriosCompat.java         # slot registration via JSON + CuriosCapability runtime access
```

```
src/main/resources/data/squire/curios/
├── slots/
│   └── squire_accessory.json   # slot type definition (size, icon, drop_rule)
└── entities/
    └── squire_entity.json      # assigns squire_accessory slot to squire entity type
```

### Pattern 1: ModList Guard (all three compat classes)

**What:** Every public method in every compat class is guarded by `ModList.get().isLoaded()`.
**When to use:** Every call site, including constructors and event handlers.

```java
// Source: NeoForge docs + v0.5.0 pattern (confirmed pattern for optional deps)
public static boolean isActive() {
    if (modPresent == null) {
        modPresent = ModList.get().isLoaded(MODID);
    }
    return modPresent;
}
```

Cache the result — `ModList.get().isLoaded()` is safe to call any time after mod loading but caching avoids repeated lookups.

### Pattern 2: MineColonies — Package-Scan Detection

**What:** Walk class hierarchy of the entity, check for MineColonies package prefix. No API import required.
**When to use:** Friendly-fire prevention in CombatHandler; clearance prevention check.

```java
// Source: v0.5.0 MineColoniesCompat.java — confirmed working pattern
private static boolean isFromPackage(Object obj, String packagePrefix) {
    Class<?> clazz = obj.getClass();
    while (clazz != null && clazz != Object.class) {
        if (clazz.getName().startsWith(packagePrefix)) return true;
        clazz = clazz.getSuperclass();
    }
    return false;
}

public static boolean isColonist(Entity entity) {
    if (!isActive()) return false;
    return isFromPackage(entity, "com.minecolonies.core.entity.citizen");
}

public static boolean isRaider(Entity entity) {
    if (!isActive()) return false;
    return isFromPackage(entity, "com.minecolonies.core.entity.mobs");
}
```

**Key v2 delta from v0.5.0:** The v0.5.0 `MineColoniesCompat` imported `SquireConfig` and had raid aggro multiplier logic. The v2 version must guard against `SquireConfig` being unavailable if called during early init. The pattern itself (package scan + ModList guard) is proven and carries over unchanged.

**What about citizen clearance?** Research found that MineColonies does NOT have an automated entity removal/clearance system that deletes external entities. The `doPush` override handles disease transmission between citizens only. Entity interference manifests as pathfinding disruption and worker delay (3-second delay when bumped), not deletion. The risk is pathfinding and friendly-fire, not removal. `MobCategory.MISC` prevents the squire from appearing in MineColonies' monster/creature handling code, which is the correct protection.

### Pattern 3: Jade Plugin Registration

**What:** Annotate a class with `@WailaPlugin`, implement `IWailaPlugin`, register an `IEntityComponentProvider`.
**When to use:** Only if Jade is present — the annotation is processed by Jade's plugin loader which only runs if Jade is loaded. Safe as long as Jade is `compileOnly`.

```java
// Source: Jade docs + Mekanism Jade integration (confirmed pattern)
@WailaPlugin
public class JadeCompat implements IWailaPlugin {

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerEntityComponent(SquireEntityProvider.INSTANCE, SquireEntity.class);
    }
}

public class SquireEntityProvider implements IEntityComponentProvider {

    public static final SquireEntityProvider INSTANCE = new SquireEntityProvider();

    @Override
    public void appendTooltip(ITooltip tooltip, EntityAccessor accessor, IPluginConfig config) {
        if (!(accessor.getEntity() instanceof SquireEntity squire)) return;
        // build tooltip lines: name, tier, HP, current task
        tooltip.add(Component.literal("Tier: " + squire.getSquireTier().name()));
        float hp = squire.getHealth();
        float max = squire.getMaxHealth();
        tooltip.add(Component.literal(String.format("HP: %.0f/%.0f", hp, max)));
    }

    @Override
    public ResourceLocation getUid() {
        return ResourceLocation.fromNamespaceAndPath("squire", "squire_entity");
    }
}
```

**v0.5.0 delta:** The old `JadeCompat.java` was a data-formatting utility that built `List<Component>` — it never actually registered a Jade plugin. It relied on Jade "automatically" picking up entity display names. That approach gives you the entity name and vanilla health bar only. To show tier, task, and custom HP coloring, a real `@WailaPlugin` registration is required.

**Jade 15.x vs earlier:** The `@WailaPlugin` annotation and `IWailaPlugin` / `IEntityComponentProvider` interfaces are stable across Jade 15.x for NeoForge 1.21.1. The `appendTooltip` signature is `(ITooltip, EntityAccessor, IPluginConfig)`.

### Pattern 4: Curios Slot Integration via Datapack

**What:** Two JSON files define the slot type and assign it to the squire entity. Runtime access uses `CuriosApi.getCuriosInventory()`.
**When to use:** Slot type JSON goes in builtin datapack resources. Runtime capability code is guarded by `ModList.isLoaded("curios")`.

**Slot type JSON** — `data/squire/curios/slots/squire_accessory.json`:
```json
{
  "size": 2,
  "order": 200,
  "icon": "squire:textures/slot/squire_accessory.png",
  "add_cosmetic": false,
  "drop_rule": "DEFAULT"
}
```

**Entity assignment JSON** — `data/squire/curios/entities/squire_entity.json`:
```json
{
  "entities": ["squire:squire"],
  "slots": ["squire_accessory"]
}
```

**Runtime access (guarded):**
```java
// Source: CuriosApi.java 1.21.x branch — getCuriosInventory returns Optional<ICuriosItemHandler>
public static Optional<ICuriosItemHandler> getSquireCurios(SquireEntity squire) {
    if (!ModList.get().isLoaded("curios")) return Optional.empty();
    return CuriosApi.getCuriosInventory(squire);
}
```

**Key API note (Curios 9.2.0+):** Attribute modifiers now use `ResourceLocation` identifiers, not UUIDs. If any curio item grants attribute bonuses, use `ResourceLocation`-keyed modifiers.

**Key API note (Curios 9.0.15+):** The `curios:item_handler` capability returning empty on client-side was a bug fixed in 9.0.15. The stack is already on 9.5.1 so this is not a concern, but it explains old forum reports saying client-side curios queries returned empty.

### Pattern 5: neoforge.mods.toml Optional Dependencies

All three compat mods must be declared as optional in `neoforge.mods.toml`:

```toml
[[dependencies.squire]]
    modId = "jade"
    type = "optional"
    versionRange = "[15.0,)"
    ordering = "NONE"
    side = "CLIENT"

[[dependencies.squire]]
    modId = "curios"
    type = "optional"
    versionRange = "[9.5,)"
    ordering = "NONE"
    side = "BOTH"

[[dependencies.squire]]
    modId = "minecolonies"
    type = "optional"
    versionRange = "[1.1,)"
    ordering = "NONE"
    side = "BOTH"
```

### Anti-Patterns to Avoid

- **Putting compat code in non-guarded static initializers:** Any static field referencing a MineColonies/Jade/Curios class will trigger `ClassNotFoundException` at mod load when that mod is absent. All compat code must be in classes that are only loaded on-demand, or all references must go through reflection/string comparisons.
- **Assuming the @WailaPlugin annotation provides soft-dep safety:** It does — Jade's service loader only calls your plugin if Jade is present. But if your `JadeCompat.java` has import statements that reference Jade classes, the class itself will fail to load when Jade is absent. Keep Jade imports inside the class, not in a commonly-loaded class.
- **Checking `ModList.isLoaded()` only once at mod init:** Cache it (as in v0.5.0) but verify the cache is initialized lazily after FML setup, not in a static field initializer.
- **Using `MobCategory.CREATURE` for the squire entity:** MineColonies pathfinding and worker management code checks `MobCategory.CREATURE` entities in its vicinity. `MobCategory.MISC` is invisible to those checks.

---

## Don't Hand-Roll

| Problem                              | Don't Build                          | Use Instead                              | Why                                              |
| ------------------------------------ | ------------------------------------ | ---------------------------------------- | ------------------------------------------------ |
| Entity tooltip in Jade               | Custom overlay rendering code        | `@WailaPlugin` + `IEntityComponentProvider` | Jade handles positioning, visibility, config toggles |
| Curios slot persistence              | Custom NBT serialization for accessories | Curios handles slot save/load internally | Curios persists slots as part of its own attachment |
| Detecting MineColonies entity types  | Hardcoded class name string lists    | Package-prefix hierarchy walk            | Catches all future citizen/raider subclasses |
| Soft-dep loading order control       | Custom event ordering hack           | `neoforge.mods.toml` `ordering = "NONE"` + lazy init | NeoForge guarantees compat code runs after all mods load |

---

## Common Pitfalls

### Pitfall 1: Class-Not-Found on Compat Class Load

**What goes wrong:** `ClassNotFoundException: snownee.jade.api.IEntityComponentProvider` or similar when starting without Jade. The mod crashes on load even though the dependency is optional.

**Why it happens:** The compat class that implements `IEntityComponentProvider` has Jade API types in its field or method signatures. When the JVM loads that class (e.g., to resolve a static field from another class), it tries to load the Jade class and fails.

**How to avoid:** Keep compat classes isolated in `compat/` package. Never reference them from commonly-loaded classes like `SquireEntity` or the main mod class except through `if (ModList.get().isLoaded())` guards that call factory methods. The `@WailaPlugin` annotation is processed by Jade's own service loader — as long as your mod's static init code doesn't touch `JadeCompat`, you're safe.

**Warning signs:** Mod crashes at startup without Jade installed. Stack trace points into `JadeCompat.class` loading, not into Jade's service loader.

### Pitfall 2: Curios Slot Not Appearing on Squire

**What goes wrong:** Curios is present, mod loads, but squire entity has no curio slots in its inventory screen.

**Why it happens:** The entity assignment JSON file path is wrong, the entity registry name doesn't match, or the slot type JSON was not embedded in the builtin datapack (only in the world-level datapack). Since NeoForge 1.21.1 has the datapack desync bug (issue #857), world-level datapack data can vanish.

**How to avoid:** Embed curios JSON in the mod's builtin resources (same as progression JSON). File path: `src/main/resources/data/squire/curios/slots/squire_accessory.json` and `src/main/resources/data/squire/curios/entities/squire_entity.json`. Entity name must exactly match the registry name: `squire:squire` (modid:entity_id).

**Warning signs:** No curio slot icon appears in squire inventory screen. `CuriosApi.getCuriosInventory(squire)` returns `Optional.empty()` even when Curios is loaded.

### Pitfall 3: MineColonies Friendly-Fire Not Wired to CombatHandler

**What goes wrong:** `MineColoniesCompat.isFriendly()` exists and works correctly, but the squire still attacks colonists because the check is never called from `CombatHandler`.

**Why it happens:** The compat class is standalone utility code. If the CombatHandler's target selection logic doesn't call `MineColoniesCompat.isFriendly()` as part of its valid-target check, the compat code is dead.

**How to avoid:** In `CombatHandler.findTarget()` or equivalent, add a gate: `if (MineColoniesCompat.isFriendly(candidate)) continue;`. This must be tested in an active colony, not just with Curios present.

**Warning signs:** Squire attacks colonists in MineColonies worlds. No `MineColoniesCompat` calls appear in combat log output.

### Pitfall 4: Jade appendTooltip Called on Server Thread

**What goes wrong:** `EntityAccessor.getEntity()` returns correct entity, but calling `squire.getHealth()` or reading synced data from it on the server side crashes or returns wrong values.

**Why it happens:** Jade calls `appendTooltip` on the client side, but if server-side data was fetched via `appendServerData` (a separate method on `IServerDataProvider`), that data is in the `CompoundTag` from `accessor.getServerData()`. You must decide: client-only data (health, synced entity data) can be read directly; server-only data (current task string) must go through `appendServerData` → network → `accessor.getServerData()`.

**How to avoid:** For Phase 8, keep the tooltip client-only (name, tier, HP from SynchedEntityData). If current task needs to show, implement `IServerDataProvider` on the same class and use `accessor.getServerData()` in `appendTooltip`.

**Warning signs:** Tooltip shows correct health on singleplayer but wrong/zero health on multiplayer client.

### Pitfall 5: MobCategory.MISC vs Entity Visibility to Players

**What goes wrong:** After switching to `MobCategory.MISC`, mob spawner behavior or kill/death counting logic breaks.

**Why it happens:** `MobCategory.MISC` is not counted toward natural mob cap and is not included in `/kill @e[type=creature]` unless explicitly targeted. This is intentional for the squire (it should not compete with vanilla creature spawns), but it means `/kill @e[type=creature]` won't kill squires. This is the correct behavior.

**How to avoid:** This is not a bug — document that squires use `MobCategory.MISC` in the mod description. Verify the squire still participates correctly in normal entity ticking and pathfinding (it does; MISC only affects spawn caps and mob categorization, not AI or physics).

---

## Code Examples

### MineColoniesCompat.java (v2 minimal)

```java
// Source: v0.5.0 MineColoniesCompat.java — package-scan pattern verified working
public final class MineColoniesCompat {

    private static final String MODID = "minecolonies";
    private static Boolean modPresent = null;

    public static boolean isActive() {
        if (modPresent == null) {
            modPresent = ModList.get().isLoaded(MODID);
        }
        return modPresent;
    }

    public static boolean isColonist(Entity entity) {
        if (!isActive()) return false;
        return isFromPackage(entity, "com.minecolonies.core.entity.citizen");
    }

    public static boolean isRaider(Entity entity) {
        if (!isActive()) return false;
        return isFromPackage(entity, "com.minecolonies.core.entity.mobs");
    }

    public static boolean isFriendly(Entity entity) {
        return isColonist(entity);  // gate is inside isColonist
    }

    private static boolean isFromPackage(Object obj, String packagePrefix) {
        Class<?> clazz = obj.getClass();
        while (clazz != null && clazz != Object.class) {
            if (clazz.getName().startsWith(packagePrefix)) return true;
            clazz = clazz.getSuperclass();
        }
        return false;
    }
}
```

### JadeCompat.java (v2 actual plugin)

```java
// Source: Jade plugin pattern (Mekanism example, Jade 15.x docs)
// This class is ONLY loaded by Jade's @WailaPlugin service loader — safe without Jade
@WailaPlugin
public class JadePlugin implements IWailaPlugin {

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerEntityComponent(
            SquireTooltipProvider.INSTANCE, SquireEntity.class);
    }
}

public class SquireTooltipProvider implements IEntityComponentProvider {
    public static final SquireTooltipProvider INSTANCE = new SquireTooltipProvider();

    @Override
    public void appendTooltip(ITooltip tooltip, EntityAccessor accessor, IPluginConfig config) {
        if (!(accessor.getEntity() instanceof SquireEntity squire)) return;
        tooltip.add(Component.literal("Tier: " + squire.getSquireTier().name())
            .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.literal(String.format("HP: %.0f/%.0f",
            squire.getHealth(), squire.getMaxHealth()))
            .withStyle(squire.getHealth() / squire.getMaxHealth() > 0.5f
                ? ChatFormatting.GREEN : ChatFormatting.RED));
    }

    @Override
    public ResourceLocation getUid() {
        return ResourceLocation.fromNamespaceAndPath("squire", "squire_entity");
    }
}
```

### CuriosCompat.java (v2 runtime accessor)

```java
// Source: CuriosApi.java 1.21.x branch — getCuriosInventory confirmed
public final class CuriosCompat {

    private static final String MODID = "curios";
    private static Boolean modPresent = null;

    public static boolean isActive() {
        if (modPresent == null) modPresent = ModList.get().isLoaded(MODID);
        return modPresent;
    }

    public static Optional<ICuriosItemHandler> getHandler(LivingEntity entity) {
        if (!isActive()) return Optional.empty();
        return CuriosApi.getCuriosInventory(entity);
    }
}
```

### Curios Slot Type JSON

`data/squire/curios/slots/squire_accessory.json`:
```json
{
  "size": 2,
  "order": 200,
  "icon": "squire:textures/slot/squire_accessory.png",
  "add_cosmetic": false,
  "drop_rule": "DEFAULT"
}
```

### Curios Entity Assignment JSON

`data/squire/curios/entities/squire_entity.json`:
```json
{
  "replace": false,
  "entities": ["squire:squire"],
  "slots": ["squire_accessory"]
}
```

---

## State of the Art

| Old Approach                              | Current Approach                              | When Changed         | Impact                                                                          |
| ----------------------------------------- | --------------------------------------------- | -------------------- | ------------------------------------------------------------------------------- |
| HWYLA/WTHIT entity tooltip registration   | Jade `@WailaPlugin` + `IEntityComponentProvider` | Jade 8+ (Jade fork era) | Single registration API; HWYLA/WTHIT compatibility layer dropped               |
| Curios IMC slot registration (`SlotTypeMessage`) | Datapack JSON in `curios/slots/` and `curios/entities/` | Curios 5.x           | IMC deprecated and removed; datapack-only is the supported path                |
| Curios UUID-based attribute modifiers     | `ResourceLocation`-keyed attribute modifiers  | Curios 9.2.0+        | Old UUID modifier code will not compile against 9.5.1 API                       |
| MineColonies "whitelist API"              | Does not exist in any version                 | Never existed        | Always use package-scan; do not search for whitelist API methods                |

**Deprecated/outdated:**

- `SlotTypeMessage` and `SlotTypePreset` in Curios: removed in 9.x. Do not use.
- `ICurio.getLootingLevel(SlotContext, DamageSource, LivingEntity, int)`: signature changed in 9.2.0 to `getLootingLevel(SlotContext, LootContext)`.
- `geckoanimfix` compat mod: unnecessary for Geckolib 4.7+ on 1.21.1. Do not add as a dependency.

---

## Open Questions

1. **MineColonies compile dependency coordinates for 1.21.1**
   - What we know: STATE.md confirms ldtteam Jfrog has no 1.21.1 artifacts as of build time. The compileOnly dep was commented out in 01-01.
   - What's unclear: Phase 8 MineColoniesCompat uses zero MineColonies API imports (pure package-scan). The compile dep is not needed at all. However, if integration test verification requires MineColonies in the dev runtime, the jar needs a source.
   - Recommendation: Do not add a MineColonies compile dep. The package-scan approach needs no imports. For runtime testing, add `localRuntime files("path/to/minecolonies.jar")` pointing to the ATM10 install's mods folder. Verify this in the build script comment.

2. **Jade `registerClient` vs `register` for entity data**
   - What we know: Client-only entity data (health, tier from SynchedEntityData) can be read directly in `appendTooltip`. For server-authoritative data (current task FSM state), a `IServerDataProvider` is needed.
   - What's unclear: Whether the squire's current FSM task is already synced via SynchedEntityData (it should be, per ARC-08 StreamCodec requirement) or needs a separate Jade server data channel.
   - Recommendation: If the current task/stance is in SynchedEntityData (expected from Phase 2 implementation), read it directly in `appendTooltip` with no `IServerDataProvider` needed. Verify in Phase 8 plan that the relevant SynchedEntityData fields exist before writing the tooltip code.

3. **Curios slot icon texture**
   - What we know: The slot type JSON references `squire:textures/slot/squire_accessory.png`.
   - What's unclear: Whether a placeholder texture is acceptable or a real icon is required for the slot to display.
   - Recommendation: Use the Curios default slot texture (empty string or omit the `icon` field) in the initial implementation. Add a real icon texture in the polish pass (08-03).

---

## Validation Architecture

### Test Framework

| Property           | Value                                             |
| ------------------ | ------------------------------------------------- |
| Framework          | JUnit 5 + NeoForge GameTest (established Phase 1) |
| Config file        | Configured in Phase 1 (unitTest { enable() } DSL) |
| Quick run command  | `./gradlew test`                                  |
| Full suite command | `./gradlew runGameTestServer`                     |

### Phase Requirements → Test Map

| Req ID | Behavior                                         | Test Type       | Automated Command                                                    | File Exists? |
| ------ | ------------------------------------------------ | --------------- | -------------------------------------------------------------------- | ------------ |
| CMP-01 | MineColoniesCompat.isColonist() returns false when MC absent | unit | `./gradlew test --tests "*.MineColoniesCompatTest"`                 | Wave 0       |
| CMP-01 | MineColoniesCompat.isFriendly() returns false for non-citizen entities | unit | `./gradlew test --tests "*.MineColoniesCompatTest"` | Wave 0       |
| CMP-01 | MobCategory.MISC confirmed on SquireEntity | unit | `./gradlew test --tests "*.SquireEntityTest"` | Exists (Phase 1) |
| CMP-02 | JadeCompat does not crash when Jade absent | integration/manual | Start server without Jade, confirm no ClassNotFoundException | manual-only |
| CMP-02 | Jade tooltip shows tier, HP when Jade present | integration/manual | Start ATM10 client, look at squire, verify HUD elements | manual-only |
| CMP-03 | CuriosCompat.getHandler() returns empty when Curios absent | unit | `./gradlew test --tests "*.CuriosCompatTest"` | Wave 0 |
| CMP-03 | Mod starts without Curios — no crash | integration/manual | Start server without Curios, confirm clean startup | manual-only |
| INV-05 | Curios slots appear in squire screen when Curios present | integration/manual | Start with Curios, open squire inventory, verify slot icons | manual-only |

**Manual-only justification:** Jade and Curios rendering tests require a running game client with the specific mods loaded. The ATM10 environment is Steve's test lab — these tests must be verified in the actual pack, not via automated GameTest.

### Sampling Rate

- **Per task commit:** `./gradlew test` (unit tests for compat guards)
- **Per wave merge:** `./gradlew runGameTestServer` (full GameTest suite)
- **Phase gate:** Full suite green + manual ATM10 smoke test (with and without each optional mod) before `/gsd:verify-work`

### Wave 0 Gaps

- [ ] `src/test/java/com/sjviklabs/squire/compat/MineColoniesCompatTest.java` — covers CMP-01 guard behavior
- [ ] `src/test/java/com/sjviklabs/squire/compat/CuriosCompatTest.java` — covers CMP-03 absent-mod path
- [ ] Placeholder texture: `src/main/resources/squire/textures/slot/squire_accessory.png` — required by curios slot JSON (can be 16x16 white square initially)

---

## Sources

### Primary (HIGH confidence)

- v0.5.0 `MineColoniesCompat.java` — package-scan pattern verified working; no API imports needed
- v0.5.0 `JadeCompat.java` — data formatting helper only; confirmed it was NOT a registered plugin
- `ICompatibilityManager.java` (ldtteam/minecolonies main branch, retrieved 2026-04-02) — confirmed no entity whitelist methods exist
- `EntityCitizen.java` (ldtteam/minecolonies main branch) — confirmed no automated entity clearance/removal logic
- `CuriosApi.java` (TheIllusiveC4/Curios 1.21.x branch) — `getCuriosInventory(LivingEntity)` signature confirmed
- `CuriosCapability.java` (TheIllusiveC4/Curios 1.21.x branch) — `ID_INVENTORY` EntityCapability confirmed
- [Curios slot registration docs](https://docs.illusivesoulworks.com/curios/slots/slot-register) — JSON format verified
- [Curios entity assignment docs](https://docs.illusivesoulworks.com/curios/slots/entity-register) — entity JSON format and file path verified
- Mekanism Jade commit `9af0e53` — `appendTooltip(ITooltip, EntityAccessor, IPluginConfig)` signature confirmed
- [STACK.md](../../../research/STACK.md) — confirmed compileOnly dep coords for Jade 15.10.4, Curios 9.5.1
- [PITFALLS.md](../../../research/PITFALLS.md) — Pitfall 12 (MineColonies pathfinding) and Integration Gotchas table

### Secondary (MEDIUM confidence)

- [Curios CHANGELOG.md 1.21.1 branch](https://github.com/TheIllusiveC4/Curios/blob/1.21.1/CHANGELOG.md) — `isSlotActive`/`setSlotActive` in 9.5.0, UUID→ResourceLocation in 9.2.0
- [Curios directory structure](https://github.com/TheIllusiveC4/Curios/tree/1.21.x/src/main/java/top/theillusivec4/curios/api) — API surface confirmed (IEntitiesData, ISlotData in type/data package)
- Jade plugin getting started page (retrieved via WebSearch) — `@WailaPlugin` annotation + `IWailaPlugin` interface confirmed as registration mechanism

### Tertiary (LOW confidence — flag for validation)

- MineColonies `version/main` package structure (browsed via GitHub) — `api/compatibility/` contains `ICompatibilityManager.java` and subdirs for specific mods (dynamictrees, tinkers). Package structure may differ slightly on the `1.21.1` tag vs `main`.
- Jade `registerEntityComponent` vs `registerEntityDataProvider` — confirmed via Mekanism example but exact Jade 15.10.4 interface may have minor differences. Verify against the Jade API jar at compile time.

---

## Metadata

**Confidence breakdown:**

- MineColonies compat approach: HIGH — no API whitelist exists; package-scan is proven via v0.5.0
- Jade plugin pattern: MEDIUM-HIGH — interface signatures confirmed via Mekanism example; minor API surface differences possible in 15.10.4 vs the confirmed example
- Curios slot JSON format: HIGH — official Curios docs confirm exact file paths and JSON structure
- Curios runtime API: HIGH — `getCuriosInventory()` confirmed from source code
- MineColonies clearance behavior: HIGH — source code confirms no automated entity removal; doPush handles citizen-only disease, not external entity removal

**Research date:** 2026-04-02
**Valid until:** 2026-05-02 (stable stack; Jade/Curios are not fast-moving)

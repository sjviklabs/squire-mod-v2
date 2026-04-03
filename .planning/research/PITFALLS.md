# Pitfalls Research

**Domain:** NeoForge 1.21.1 companion/follower entity mod (Geckolib rendering, Curios equipment, datapack-driven AI)
**Researched:** 2026-04-02
**Confidence:** MEDIUM — NeoForge 1.21.1 is no longer actively maintained; some findings from community issue trackers and official docs; Geckolib-specific gotchas verified against wiki and issues.

---

## Critical Pitfalls

### Pitfall 1: SynchedEntityData defineId() Called on Wrong Class

**What goes wrong:**
Silent network desync between client and server. Entity data accessor IDs shift when any parent class adds or removes parameters — the client and server disagree on which index maps to which value. Downstream: invisible entities, wrong health bars, corrupted state, or `IllegalStateException: Entity class X has not defined synched data value N` crash on join.

**Why it happens:**
Developers define `EntityDataAccessor` fields in a base class or mixin rather than the owning entity class. The IDs are assigned sequentially per class, so inserting one at the wrong level cascades all downstream IDs.

**How to avoid:**
Always pass the owning entity's class (`SquireEntity.class`) as the first parameter to `SynchedEntityData.defineId()`. Never define synced data accessors in a superclass you don't control, via mixins, or in a utility class. Define all `SquireEntity` data accessors inside `SquireEntity` only.

**Warning signs:**
- Entity renders on client but state (health, tier, stance) is wrong or stale
- `IllegalStateException: Entity class ... has not defined synched data value N` in crash log
- Entity appears invisible after rejoining a world

**Phase to address:** Phase 1 (entity foundation) — establish the pattern before any synced data is added.

---

### Pitfall 2: AnimatableInstanceCache Not Created Correctly in Geckolib

**What goes wrong:**
`NullPointerException` in the renderer, or animations that don't fire at all. In multiplayer, triggered animations may not display to other players.

**Why it happens:**
Geckolib requires a final `AnimatableInstanceCache` field created via `GeckoLibUtil.createInstanceCache(this)` in the entity class, and returned from `getAnimatableInstanceCache()`. Skipping this, or creating the cache lazily, breaks the renderer's lookup.

For items and replaced-geo entities, `SingletonGeoAnimatable.registerSyncedAnimatable(this)` must be called in the constructor. Missing this call causes triggered animations to not sync to other clients in multiplayer.

**How to avoid:**
```java
// In SquireEntity constructor body:
private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

@Override
public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
```
For any Geckolib item (Crest, halberd, shield): call `SingletonGeoAnimatable.registerSyncedAnimatable(this)` in the item's constructor.

**Warning signs:**
- `NullPointerException` stack trace pointing into Geckolib renderer code
- Animations visible on host client but not on connected clients
- `GeckoLibException: Could not find model` at entity spawn

**Phase to address:** Phase 2 (Geckolib rendering) — verify both the entity cache and any item animatables during initial render setup.

---

### Pitfall 3: Attack Animation Only Plays Partial Cycle

**What goes wrong:**
The halberd swing animation plays for 6 ticks and then stops regardless of the animation's actual duration, because vanilla's swing duration is hardcoded to 6 ticks.

**Why it happens:**
`DefaultAnimations#genericAttackAnimation` (and any custom attack animation triggered via swing) respects `getCurrentSwingDuration()`, which returns 6 by default for non-Monster entities. PathfinderMob is not a Monster.

**How to avoid:**
Override `getCurrentSwingDuration()` in `SquireEntity` to return the actual animation length in ticks. Also override `aiStep()` and call `updateSwingTime()` explicitly — PathfinderMob does not do this automatically since it isn't Monster.

**Warning signs:**
- Attack animation cuts off abruptly mid-swing
- Animation appears to "snap" back to idle before the swing arc completes

**Phase to address:** Phase 3 (combat AI) — test full animation cycle on the first combat iteration.

---

### Pitfall 4: Geckolib + Iris/Oculus Shader Incompatibility

**What goes wrong:**
With shaders active, Geckolib entity animations stop working or the entity renders invisibly. The issue is specific to shaderpacks that enable entity shadows.

**Why it happens:**
Geckolib's custom rendering pipeline conflicts with how Iris/Oculus intercepts entity render calls for shadow passes. Older Geckolib versions (pre-4.4 on 1.20.1+) had this as a hard crash. On 1.21.1, the compatibility fix is internal to Geckolib itself, but it requires using a sufficiently recent build.

ATM10 ships Oculus (NeoForge Iris port). If the bundled Geckolib version predates the fix, entities will break under shaders.

**How to avoid:**
Pin to Geckolib 4.7.x or higher for NeoForge 1.21.1 (4.7.5.1+ confirmed available on CurseForge). Test with Oculus enabled in the ATM10 environment specifically. Do not rely on the deprecated `geckoanimfix` compat mod — it's unnecessary for 1.21.1 builds.

**Warning signs:**
- Squire entity invisible or T-posed only when a shaderpack is active
- Animations work in vanilla/no-shader profile but break in ATM10 with shaders
- `Unable to find animation file` errors in log specifically when Oculus is present

**Phase to address:** Phase 2 (rendering) — validate in ATM10 with shaders enabled before calling rendering done.

---

### Pitfall 5: IItemHandler Slot Mutation via getStackInSlot()

**What goes wrong:**
Inventory corruption or ItemStacks shared across slots — modifying the returned ItemStack directly alters the inventory's internal state without going through the proper insert/extract path. Other systems (hoppers, pipes) see inconsistent state.

**Why it happens:**
The NeoForge contract for `IItemHandler.getStackInSlot()` explicitly states the returned `ItemStack` MUST NOT be modified. It is a view, not a copy. Developers assume they can mutate it in place because vanilla `Container.getItem()` returned mutable stacks.

**How to avoid:**
Never call `.setCount()`, `.shrink()`, `.grow()`, or `.setTag()` on a stack returned from `getStackInSlot()`. Always use `extractItem()` to remove and `insertItem()` to add. In custom `SquireItemHandler`, validate this in the implementation and throw on mutation attempts during testing.

**Warning signs:**
- Items duplicating or vanishing when interacting with hoppers or pipes
- Two slots in the squire's inventory showing the same item reference
- `ItemStack` count going negative without an explicit extract call

**Phase to address:** Phase 2 (inventory system) — establish the pattern in `SquireItemHandler` before any external automation compatibility is wired.

---

### Pitfall 6: Capability Not Invalidated on Inventory Change

**What goes wrong:**
External automation (hoppers, pipes from Create, RS, AE2) caches the capability reference and never sees inventory updates after the first tick. Items appear to not extract/insert even though the handler reports correct contents when queried fresh.

**Why it happens:**
NeoForge's capability system caches resolved capabilities. If the underlying handler changes (e.g., squire levels up and inventory capacity expands) without invalidating the cache, external mods hold a stale reference.

**How to avoid:**
Call `self.invalidateCapabilities()` any time the `SquireItemHandler` instance is replaced or its contract changes (tier upgrade, death reset). Register the capability provider in `AttachCapabilitiesEvent<Entity>` and ensure the invalidation call fires on tier-change events.

**Warning signs:**
- External automation stops working after squire ranks up
- Hopper interaction works on first connection but breaks on re-login
- Capability queries from other mods return null after an upgrade

**Phase to address:** Phase 4 (progression system) — tier upgrades are the primary trigger for capacity changes.

---

### Pitfall 7: Datapack Entity Tags Not Available at Entity Registration Time

**What goes wrong:**
Code that reads entity tags during `FMLCommonSetupEvent` or static initialization gets empty or missing tags. Combat tactics logic that checks `entity.getType().is(squireTags.MELEE_CAUTIOUS)` returns false for all mobs because tags haven't loaded yet.

**Why it happens:**
Entity tags are loaded as server data (datapack resources), not at mod load time. They are only populated after the world's `DataPackConfig` is processed — which happens after FML setup events. Reading tags in setup handlers or entity class static initializers is a common mistake.

**How to avoid:**
Only query entity tags inside live game code: inside AI goal ticks, event handlers that fire during play, or `ServerStartedEvent`. Never check tags in `FMLCommonSetupEvent` or field initializers. Store tag keys (`TagKey<EntityType<?>>`) as static constants — that's fine — but don't read `.is()` results until the world is running.

For the data-driven combat tactics system: the `CombatHandler` should resolve tactics at first engagement, not at registration.

**Warning signs:**
- All entity tag checks return false even for mobs explicitly tagged in your datapack
- Combat tactics fall through to a default for every mob type during testing
- Tags work after a `/reload` but not on first world join

**Phase to address:** Phase 3 (combat AI) — the tag-based tactics system must be initialized lazily.

---

### Pitfall 8: Datapack JSON Files Override Each Other Instead of Merging

**What goes wrong:**
Server operators add a custom datapack to tune progression curves, but their progression JSON silently replaces the mod's default instead of merging — they lose tier definitions they didn't intend to override. Or vice versa: a misbehaving modpack datapack wipes your progression config.

**Why it happens:**
Most datapack JSON files (loot tables, advancements, recipes, custom data files loaded via `JsonDataProvider`) are winner-take-all: the last pack wins. Only tags are additive by default. If progression/ability definitions are stored as individual JSON objects rather than tags, the override behavior is surprising.

**How to avoid:**
Design progression JSON to be top-level objects that are intentionally replaceable (one file = one tier definition, keyed by resource location). Document clearly that overriding a tier replaces it entirely. For additive customization (e.g., adding a new ability to a tier without replacing others), consider using tag-like list files or a merge-friendly format. Add a schema comment in each generated JSON warning about this behavior.

**Warning signs:**
- Server operator reports progression broken after adding their datapack
- Abilities missing from tiers after modpack update
- `squire:progression/tier_3.json` resolves to wrong values in-game

**Phase to address:** Phase 1 (architecture) — decide on additive vs. override semantics before writing any JSON loading code.

---

### Pitfall 9: PathfinderMob Navigation Stuck at Doorways and Water Surfaces

**What goes wrong:**
The squire stops at door blocks or gets trapped in water, even though it should navigate through both. The follower behavior breaks when the player enters a building, farm, or crosses a river.

**Why it happens:**
`PathfinderMob` uses `WalkNodeEvaluator` by default, which doesn't open doors. Overriding navigation without also setting the correct `NodeEvaluator` leaves door-opening disabled. Water navigation requires `AmphibiousNodeEvaluator` or manual `setCanFloat(true)` with swim goal properly registered.

Additionally, follower goal implementations that use `moveTo(player)` directly (instead of `FollowOwnerGoal`-style logic with corridor checking) don't account for entity width — a 1-wide entity navigating a 2-block wide path can clip into walls.

**How to avoid:**
- Set `this.getNavigation().setCanFloat(true)` in `SquireEntity` constructor
- Override `createNavigation()` and supply `WaterBoundPathNavigation` or configure the node evaluator for door traversal
- Add `OpenDoorGoal` to the goal selector with correct priority (lower number = higher priority)
- Test follower navigation through a door, across a river, and up stairs in Phase 1

**Warning signs:**
- Squire stops at a closed door and stands there
- Squire sinks in water instead of swimming
- Squire clips into the side of a 2-block corridor repeatedly

**Phase to address:** Phase 1 (entity foundation) — navigation configuration is foundational; late fixes require restructuring goal priorities.

---

### Pitfall 10: Config Validation Rejects Default Values at Startup

**What goes wrong:**
An infinite correction loop fires every 2 seconds. Server console floods with config correction messages. This was a documented v0.5.0 failure mode.

**Why it happens:**
NeoForge's TOML config validation runs defaults through the same validator as user-supplied values. If a default doesn't satisfy its own `validator` lambda (e.g., `validator = v -> v > 0` with a default of `0`), NeoForge resets the value to the default on every load — which fails again on next read — and logs a warning each cycle.

**How to avoid:**
For every config entry in `squire-common.toml`, verify the default satisfies the validator before shipping. Write a unit test (or at minimum a startup assertion) that instantiates the config and reads every value without triggering corrections. Common offenders: min/max range validators where the default equals the boundary.

**Warning signs:**
- `[Config] Incorrect value for squire-common.toml/...` repeating in server log
- Gameplay values resetting to defaults mid-session
- Config file written on every server tick

**Phase to address:** Phase 1 (config system) — validate all defaults before any config entry is finalized.

---

### Pitfall 11: Geckolib AnimationController AnimationState vs. AnimationTest Naming Conflict

**What goes wrong:**
Code written against a Geckolib tutorial older than version 4.3 uses `AnimationState` as the predicate parameter type. In Geckolib 4.3+, the predicate parameter was renamed to `AnimationTest` to avoid collision with vanilla's `AnimationState` class. Using the wrong type compiles against the wrong class, causing silent animation system failures or ClassCastException at runtime.

**Why it happens:**
Most community tutorials and examples were written for Geckolib 3 or early Geckolib 4 and use the old `AnimationState` type name. Copy-pasting from those examples without checking the current API version leads to the wrong import being resolved.

**How to avoid:**
In Geckolib 4.3+ (which covers NeoForge 1.21.1 builds), animation controller predicates take `AnimationTest`, not `AnimationState`. Verify the import: it should be `software.bernie.geckolib.core.animation.AnimationTest`. When referencing tutorials, check the Geckolib wiki version tab — use only the Geckolib 4 entity page, not Geckolib 3.

**Warning signs:**
- Animation predicates compile but never trigger transition
- `ClassCastException` in Geckolib controller code during entity tick
- IDE resolves `AnimationState` to `net.minecraft.world.entity.AnimationState` instead of Geckolib's class

**Phase to address:** Phase 2 (Geckolib rendering) — catch during initial animation controller wiring.

---

### Pitfall 12: MineColonies Citizen AI Interference with Squire Pathfinding

**What goes wrong:**
In worlds with active MineColonies colonies, squire pathfinding into or around colony buildings breaks — the squire gets stuck at colony borders, pathfinds into citizen-reserved spaces, or triggers MineColonies' "entity blocking citizen" detection, causing the colony to attempt to remove the squire as an obstacle.

**Why it happens:**
MineColonies modifies pathfinding and chunk access patterns around colony structures. Custom entities that use standard `PathfinderMob` navigation may attempt to path through colony-reserved spaces. MineColonies also has entity "clearance" logic that attempts to push or despawn entities it classifies as interfering with citizen jobs.

**How to avoid:**
- Mark the squire entity with a `squire:is_squire` tag and check MineColonies' compatibility API for entity whitelisting
- Do not register the squire entity in any vanilla mob category that MineColonies uses for clearance (use `MobCategory.MISC` or a custom category, not `CREATURE` or `MONSTER`)
- Test in an ATM10 world with an active colony before any Phase 5 release — this is a hard integration point specific to Steve's use case

**Warning signs:**
- Squire teleports away from player near colony buildings
- `[MineColonies]` log lines mentioning entity removal or clearing near squire UUID
- Squire pathfinding stops updating within colony chunk range

**Phase to address:** Phase 5 (compatibility) — dedicated MineColonies integration testing pass.

---

### Pitfall 13: NeoForge 1.21.1 End-of-Life Means No Bug Fixes

**What goes wrong:**
A critical NeoForge or Minecraft bug affecting entity behavior, networking, or datapack loading is discovered. It won't be patched in 1.21.1. Workarounds must be coded into the mod itself.

**Why it happens:**
NeoForge team has moved on to 1.21.4+. The 1.21.1 branch receives no new releases. ATM10 is the primary reason to stay on 1.21.1, but that means accepting all current bugs as permanent.

**Known unfixed issue:** Datapack loading order is per-world and the "enabled datapacks" list can desync between session and save, reverting to the original list on next world load (NeoForge issue #857).

**How to avoid:**
For the datapack desync bug: embed the mod's own datapack as builtin resources using `DataGenerator#getBuiltinDatapack` so it's always present regardless of world-level pack ordering. Do not rely on the server operator manually enabling the squire datapack.

For future unknown bugs: budget time in each phase to investigate platform-level issues rather than assuming the framework works correctly.

**Warning signs:**
- Progression or entity tag data resets to defaults after world reload despite no config change
- NeoForge GitHub shows the bug but the issue is closed as "fixed in 1.21.4"

**Phase to address:** Phase 1 (architecture) — embed builtin datapack from day one.

---

## Technical Debt Patterns

| Shortcut | Immediate Benefit | Long-term Cost | When Acceptable |
| --- | --- | --- | --- |
| Hardcoding mob checks (`instanceof Zombie`) for combat tactics | Faster to ship initial combat | Every modded mob needs a code change; breaks data-driven design | Never — entity tags exist for this |
| Sharing a single `ItemStackHandler` instance between capability and NBT serialization | Less code | Mutation race on capability cache invalidation; corruption on tier change | Never for this mod |
| Registering entity data accessors in a handler class | Keeps handlers self-contained | Silent network desync across any version with a new parameter | Never |
| Skipping `updateSwingTime()` override for non-Monster PathfinderMob | Works in singleplayer | Attack animations clip for all non-Monster entities | Never |
| Lazy `AnimatableInstanceCache` creation | Slightly simpler constructor | NPE in renderer, hard to trace | Never |
| Putting progression JSON in a single large file | One place to edit | Whole file replaced on any datapack override; no per-tier customization | Only for prototyping in Phase 1 |
| Reading entity tags during FML setup | Looks clean | Tags are empty at that point; all checks return false | Never |

---

## Integration Gotchas

| Integration | Common Mistake | Correct Approach |
| --- | --- | --- |
| Geckolib | Forgetting `GeoEntityRenderer` registration | Register in `EntityRendererProvider` during client setup; failure is a fatal NPE |
| Geckolib | Using `AnimationState` (vanilla) instead of `AnimationTest` (Geckolib 4.3+) | Import `software.bernie.geckolib.core.animation.AnimationTest` |
| Geckolib | Not overriding `getCurrentSwingDuration()` on non-Monster | Attack animation plays 6 ticks then stops regardless of animation length |
| Curios API | `curios:item_handler` capability returning empty client-side | Update to Curios 9.0.15+; this was a documented bug fixed in that release |
| Curios API | Not listening to `CurioUnequipEvent` to remove squire-granted buffs | Buffs persist after item removal |
| IItemHandler | Mutating stack from `getStackInSlot()` | Always use `extractItem()`/`insertItem()` |
| IItemHandler (two-capability split) | Querying `Capabilities.ItemHandler.ENTITY` when automation-only access is needed | Use `Capabilities.ItemHandler.ENTITY_AUTOMATION` for hopper/pipe compat |
| MineColonies | Using `MobCategory.CREATURE` | MineColonies clearance logic may target the squire; use `MISC` or custom |
| NeoForge datapacks | Relying on world-level datapack enable order | Use builtin datapack via `DataGenerator#getBuiltinDatapack` |
| NeoForge data attachments | Forgetting `serializer` in attachment registration | Attachment data is not persisted to disk; lost on world reload |

---

## Performance Traps

| Trap | Symptoms | Prevention | When It Breaks |
| --- | --- | --- | --- |
| Custom FSM ticking every entity tick (20/s) for complex state checks | Server MSPT spikes with 3+ active squires on ATM10 with 449 mods | Tick-rate the FSM (e.g., 4/s for non-combat, 10/s for combat); use dirty flags | 3+ concurrent squires in ATM10 environment |
| Pathfinding recalculation every tick | Entity recalculates path constantly even when target hasn't moved; major CPU spike | Cache last path target; only recalculate on target change or path failure | Immediately with any move-to-player goal |
| Per-entity handler allocations without pooling | GC pressure on servers with long uptime | Use static constants for handler configs; pool goal instances where possible | Long-running servers (hours of uptime) |
| Entity tag `.is()` calls inside hot AI loops | Tag lookup on every goal tick; measurable overhead in 449-mod pack with many custom tags | Cache tactics result per-target at engagement start; invalidate on target change | 20+ concurrent combat encounters |
| Syncing entire inventory over network on any slot change | Bandwidth spike on chest deposit/withdraw; rubber-banding for nearby players | Sync only dirty slots via custom packet; use dirty mask instead of full resend | Large inventories (36 slots) with frequent access |

---

## "Looks Done But Isn't" Checklist

- [ ] **Geckolib rendering:** Verify with Oculus/shaders enabled in ATM10 — not just in vanilla dev environment
- [ ] **Attack animation:** Test full swing arc with non-Monster PathfinderMob; confirm `getCurrentSwingDuration()` override is in place
- [ ] **Config validation:** Read every config value at startup with an assertion; confirm no correction-loop warnings in server log
- [ ] **Inventory persistence:** Kill the squire, reload world, rejoin — verify inventory survives all three steps
- [ ] **Data attachment persistence:** Reload world (not just server); confirm squire owner UUID and tier survive disk round-trip
- [ ] **Entity tag tactics:** Test combat with a modded mob (not just vanilla) that is tagged in your datapack; confirm correct tactics fire
- [ ] **MineColonies:** Walk squire through an active colony; confirm no clearance events and no pathfinding failure
- [ ] **Curios integration:** Equip an accessory, log out and back in, confirm it persists and bonuses are applied
- [ ] **Dedicated server:** Test on a dedicated server (not LAN/singleplayer) — client-side class references crash on dedicated
- [ ] **Builtin datapack:** Delete the world-level datapack folder; confirm squire progression data still loads from mod resources

---

## Recovery Strategies

| Pitfall | Recovery Cost | Recovery Steps |
| --- | --- | --- |
| SynchedEntityData wrong class | HIGH | Audit all `defineId` calls; renumber all accessors; requires full test cycle to verify |
| Geckolib cache NPE | LOW | Add cache field and return method; hot-fix, no architectural change |
| Config correction loop | LOW | Fix default value; deploy config reset; one-line change |
| Capability not invalidated | MEDIUM | Add `invalidateCapabilities()` call on tier-change event; test all automation mods |
| Datapack override wipes progression | MEDIUM | Restructure JSON into per-entry files; document override semantics; re-test datapack loading |
| MineColonies clearance deleting squire | MEDIUM | Change MobCategory; add whitelist API call; targeted compatibility testing |
| Iris/Geckolib shader break | LOW | Update Geckolib pin to 4.7.5.1+; test in ATM10 with shaders |

---

## Pitfall-to-Phase Mapping

| Pitfall | Prevention Phase | Verification |
| --- | --- | --- |
| SynchedEntityData wrong class | Phase 1 (entity foundation) | No desync errors after reconnect; entity state correct on all clients |
| Config correction loop | Phase 1 (config system) | Zero `[Config] Incorrect value` lines on server startup |
| Datapack embed (builtin) | Phase 1 (architecture) | Delete world datapack folder; confirm progression still loads |
| PathfinderMob navigation (doors/water) | Phase 1 (entity foundation) | Squire navigates through door, across river, up stairs |
| Geckolib cache NPE | Phase 2 (rendering) | No NPE crash on entity spawn |
| Geckolib + Iris shaders | Phase 2 (rendering) | Entity visible with Oculus active in ATM10 |
| AnimationTest vs AnimationState | Phase 2 (rendering) | Attack/walk/idle animations all trigger correctly |
| Attack animation duration | Phase 3 (combat AI) | Full halberd swing visible before return to idle |
| Entity tag loading timing | Phase 3 (combat AI) | Modded mob gets correct tactics on first combat |
| IItemHandler mutation | Phase 2 (inventory) | No item duplication or corruption with hopper/pipe access |
| Capability invalidation on tier change | Phase 4 (progression) | Automation still works after squire ranks up |
| Curios integration | Phase 4 (progression) | Accessory persists through relog; bonuses apply/remove correctly |
| MineColonies clearance | Phase 5 (compatibility) | No clearance events near active colony |
| NeoForge 1.21.1 EOL bugs | All phases | Known datapack desync mitigated by builtin datapack embed |

---

## Sources

- NeoForge official docs — SynchedEntityData warning: https://docs.neoforged.net/docs/entities/data/
- NeoForge docs — Living entities: https://docs.neoforged.net/docs/entities/livingentity/
- NeoForge capability rework announcement: https://neoforged.net/news/20.3capability-rework/
- NeoForge docs — Tags: https://docs.neoforged.net/docs/1.21.1/resources/server/tags/
- NeoForge issue #857 — datapack loading order desync: https://github.com/neoforged/NeoForge/issues/857
- Geckolib wiki — Entities (Geckolib4): https://github.com/bernie-g/geckolib/wiki/Geckolib-Entities-(Geckolib4)
- Geckolib GitHub issue #675 — NeoForge 21.4.61 crash: https://github.com/bernie-g/geckolib/issues/675
- Geckolib GitHub issue #245 — invisible entity with GeoEntityRenderer: https://github.com/bernie-g/geckolib/issues/245
- Geckolib 4 Changes wiki (AnimationTest rename): https://github.com/bernie-g/geckolib/wiki/Geckolib-4-Changes
- MobZReborn issue #44 — SyncedEntityData crash in NeoForge 1.21.1: https://github.com/rikka0w0/MobZReborn/issues/44
- Curios changelog 1.21.1 branch: https://github.com/TheIllusiveC4/Curios/blob/1.21.1/CHANGELOG.md
- MineColonies issue #10440 — citizens frozen (entity interference pattern): https://github.com/ldtteam/minecolonies/issues/10440
- Personal domain knowledge — v0.5.0 known pitfalls (TamableAnimal, SynchedEntityData, config validation, network limits)

---

_Pitfalls research for: NeoForge 1.21.1 companion entity mod (Squire v2)_
_Researched: 2026-04-02_

# Phase 6: Work Behaviors - Research

**Researched:** 2026-04-02
**Domain:** NeoForge 1.21.1 — handler-per-behavior work tasks, loot table API, task queue
**Confidence:** HIGH (v0.5.0 source read directly; fishing loot path verified against NeoForge docs + Minecraft wiki)

---

<phase_requirements>

## Phase Requirements

| ID     | Description                                        | Research Support                                                                                |
| ------ | -------------------------------------------------- | ----------------------------------------------------------------------------------------------- |
| WRK-01 | Squire mines single blocks on command              | MiningHandler ported from v0.5.0; approach/break FSM states proven; config keys exist          |
| WRK-02 | Squire performs area clear (multi-block mining)    | setAreaTarget() + LinkedList queue proven in v0.5.0; ARC-09 chunk loading hook already in place |
| WRK-03 | Squire auto-places torches in darkness             | TorchHandler is a side-effect of FOLLOW state, not a full FSM state; ability-gated             |
| WRK-04 | Squire farms (tilling + harvesting)                | FarmingHandler with FARM_SCAN → FARM_APPROACH → FARM_WORK cycle proven in v0.5.0              |
| WRK-05 | Squire fishes (simulated fishing)                  | Simulated approach; loot table path and LootParams API verified; v0.5.0 had wrong fish rates   |
| WRK-06 | Squire places blocks on command                    | PlacingHandler is straightforward; PLACING_APPROACH → PLACING_BLOCK cycle from v0.5.0          |
| WRK-07 | Squire interacts with containers (deposit/withdraw)| ChestHandler uses IItemHandler capability; works with both vanilla and modded storage           |
| WRK-08 | Squire supports task queuing                       | TaskQueue (ArrayDeque + NBT) proven in v0.5.0; lives in SquireBrain, dispatches on IDLE        |

</phase_requirements>

---

## Summary

Phase 6 ports eight proven handler classes from v0.5.0 into the v2 architecture (handler-per-behavior pattern, SquireBrain event bus, TickRateStateMachine). The domain is well-understood — all seven handler classes and TaskQueue were read directly from v0.5.0 source. The primary research question was the FishingHandler's loot table integration, flagged in STATE.md.

**The fishing finding:** v0.5.0 implemented fishing as a hardcoded random roll with incorrect fish probabilities (tropical fish 10%, pufferfish 5% — these are inverted vs vanilla). v2 should use the vanilla loot table (`minecraft:gameplay/fishing`) via `LootTable.getRandomItems()` with `LootContextParamSets.FISHING`. This respects Luck of the Sea, is mopack-compatible, and gets the rates right (cod 60%, salmon 25%, pufferfish 13%, tropical fish 2%). The required parameters are `LootContextParams.ORIGIN` and `LootContextParams.TOOL`; `THIS_ENTITY` and `KILLER_ENTITY` are optional.

**Primary recommendation:** Port all handlers from v0.5.0 with targeted v2 fixes: correct fishing loot path, replace hardcoded ability checks with FSM tier gates, wire task completion events through SquireBrainEventBus. No new architectural invention needed — the patterns are proven.

---

## Standard Stack

### Core

| Library     | Version | Purpose                            | Why Standard                                                            |
| ----------- | ------- | ---------------------------------- | ----------------------------------------------------------------------- |
| NeoForge    | 21.1.x  | LootTable API, BlockPos, ServerLevel | Required — provides `LootContextParamSets.FISHING`, `ServerLevel`, etc. |
| Java stdlib | 21      | ArrayDeque (TaskQueue)             | `ArrayDeque` is the correct FIFO queue; `LinkedList` used in v0.5.0 MiningHandler for block queue is also fine |

### Supporting (already in project)

| Library       | Version | Purpose                                   | When to Use                            |
| ------------- | ------- | ----------------------------------------- | -------------------------------------- |
| SquireConfig  | local   | All numeric thresholds (reach, cooldowns) | Every handler — no hardcoded numbers   |
| SquireBrainEventBus | local | Handler-to-handler coordination    | WORK_TASK_COMPLETE fires on task end   |
| SquireItemHandler | local | IItemHandler inventory access       | All handlers that read/write inventory |

### Alternatives Considered

| Instead of                       | Could Use           | Tradeoff                                                      |
| -------------------------------- | ------------------- | ------------------------------------------------------------- |
| Vanilla loot table (fishing)     | Hardcoded roll      | Hardcoded misses Luck of the Sea and mopack fish table overrides |
| LinkedList (block queue)         | ArrayDeque          | Negligible; ArrayDeque is slightly faster but LinkedList allows O(1) remove-by-value needed in popNearestReachable |
| BaseContainerBlockEntity (chest) | IItemHandler only   | BaseContainerBlockEntity handles vanilla chests more directly; IItemHandler fallback handles modded storage |

---

## Architecture Patterns

### Handler Placement in v2 FSM

All work handlers follow the same approach/work cycle and sit at **priority 35-39** in the FSM (below survival/combat, above item pickup/cosmetic idle):

```
SquireBrain
  ├── MiningHandler       (35) — MINING_APPROACH, MINING_BREAK
  ├── PlacingHandler      (36) — PLACING_APPROACH, PLACING_BLOCK
  ├── FarmingHandler      (37) — FARM_SCAN, FARM_APPROACH, FARM_WORK
  ├── FishingHandler      (38) — FISHING_APPROACH, FISHING_IDLE
  ├── ChestHandler        (39) — CHEST_APPROACH, CHEST_INTERACT
  └── TorchHandler        (n/a) — not a state; called as side-effect from FOLLOWING_OWNER + IDLE
```

TaskQueue lives in SquireBrain directly — it is not a handler. On every `IDLE → [any_work_state]` transition where the queue is non-empty, SquireBrain pops the next task and routes it to the appropriate handler.

### Pattern: Approach / Work Cycle

Every work handler (except TorchHandler) uses this two-state cycle:

```
IDLE → [HANDLER]_APPROACH → [HANDLER]_WORK → IDLE
                ↑ stuck detection loops back or falls through to IDLE
```

**Stuck detection (proven from v0.5.0):**
- Track `approachTicks` and `lastApproachDistSq`
- If distance hasn't improved by 0.1 blocks for `STUCK_TIMEOUT` (100) ticks, try repositioning
- After `MAX_REPOSITION_ATTEMPTS` (3), log and skip the target

### Pattern: TorchHandler (Side-Effect, Not a State)

TorchHandler is NOT a FSM state. It is called as a side-effect from FollowHandler and idle transitions:

```java
// In FollowHandler.tick() or idle transitions:
if (torch.tryPlaceTorch()) {
    // light placed, continue current state
}
```

**Why:** Torch placement is an interruption-free background action. Adding it as a full FSM state would block the squire from following or doing other work while scanning for torch placement.

### Pattern: TaskQueue Dispatch (v2 design)

TaskQueue lives in SquireBrain. On each tick when `currentState == IDLE && !taskQueue.isEmpty()`:

```java
SquireTask next = taskQueue.poll();
switch (next.commandName()) {
    case "mine"  -> { mining.setTarget(...);  machine.forceState(MINING_APPROACH); }
    case "place" -> { placing.setTarget(...); machine.forceState(PLACING_APPROACH); }
    case "farm"  -> { farming.setArea(...);   machine.forceState(FARM_SCAN); }
    case "fish"  -> { fishing.startFishing(); machine.forceState(FISHING_APPROACH); }
    case "chest" -> { chest.setTarget(...);   machine.forceState(CHEST_APPROACH); }
}
```

Task completion fires `SquireEvent.WORK_TASK_COMPLETE` — ChatHandler listens and sends the completion line.

### Required New FSM States

The following `SquireAIState` enum values must be added in Phase 6 (they don't exist yet — SquireBrain is a Phase 2 stub):

```
MINING_APPROACH, MINING_BREAK,
PLACING_APPROACH, PLACING_BLOCK,
FARM_SCAN, FARM_APPROACH, FARM_WORK,
FISHING_APPROACH, FISHING_IDLE,
CHEST_APPROACH, CHEST_INTERACT
```

### Recommended Directory Layout (no changes needed)

All handlers go in `brain/handler/` as established in ARCHITECTURE.md. No new packages. TaskQueue goes in `brain/` (alongside SquireBrain) since it is brain-level state, not a util class.

```
src/main/java/com/sjviklabs/squire/
└── brain/
    ├── TaskQueue.java           # FIFO queue, NBT persistence (was util/ in v0.5.0 — move to brain/)
    └── handler/
        ├── MiningHandler.java
        ├── TorchHandler.java
        ├── FarmingHandler.java
        ├── FishingHandler.java
        ├── PlacingHandler.java
        ├── ChestHandler.java
        └── ChatHandler.java     # also gets new trigger: onWorkComplete(String taskName)
```

---

## Don't Hand-Roll

| Problem                        | Don't Build                                | Use Instead                                                   | Why                                                                             |
| ------------------------------ | ------------------------------------------ | ------------------------------------------------------------- | ------------------------------------------------------------------------------- |
| Fishing loot                   | Hardcoded probability table in code        | `LootTable.getRandomItems()` with `minecraft:gameplay/fishing` | Respects Luck of the Sea, mopack-overridable, gets vanilla rates correct         |
| Block break progress overlay   | Custom particle/overlay system             | `ServerLevel.destroyBlockProgress(entityId, pos, stage)`      | Vanilla system, already implemented in v0.5.0 MiningHandler                      |
| Container item transfer        | Custom slot-copy loops                     | `IItemHandler.insertItem()` / `extractItem()` for modded; direct slot loop for `BaseContainerBlockEntity` | IItemHandler handles partial inserts, capacity checks, and stack splitting automatically |
| Crop maturity check            | Hardcoded blockstate/age checks            | `CropBlock.isMaxAge(state)`                                   | Vanilla API, handles all vanilla crop types uniformly                            |
| Inventory item search          | Linear scan every tick                     | Scan once in `shouldStart()` / `setTarget()`, cache slot index | Already proven in v0.5.0 — PlacingHandler caches `inventorySlot`                |

---

## Common Pitfalls

### Pitfall 1: Wrong Fishing Loot Rates (v0.5.0 Bug)

**What goes wrong:** v0.5.0 hardcoded `tropical fish 10%, pufferfish 5%` — these are inverted from vanilla (`pufferfish 13%, tropical fish 2%`).
**Why it happens:** Estimated from memory instead of checking the wiki.
**How to avoid:** Use the vanilla loot table via `LootContextParamSets.FISHING`. Rates come from `data/minecraft/loot_tables/gameplay/fishing/fish.json` — automatically correct and mopack-overridable.
**Warning signs:** Players reporting too many tropical fish, too few pufferfish.

### Pitfall 2: FishingHandler Using Wrong LootContextParamSet

**What goes wrong:** Calling `getRandomItems()` with `LootContextParamSets.EMPTY` instead of `LootContextParamSets.FISHING` — the FISHING set validates that `ORIGIN` and `TOOL` are present; rolling with EMPTY bypasses that validation and may return empty lists or throw at runtime.
**Why it happens:** EMPTY is the first example shown in NeoForge docs.
**How to avoid:** Build params with `LootContextParamSets.FISHING`. Supply `ORIGIN` as squire block position and `TOOL` as the fishing rod ItemStack from inventory.
**Warning signs:** Empty catch results, IllegalStateException on loot roll.

### Pitfall 3: TorchHandler Checking Inventory Via SquireInventory (SimpleContainer)

**What goes wrong:** v0.5.0 TorchHandler calls `squire.getSquireInventory()` which returns a `SquireInventory` (SimpleContainer subclass). In v2, inventory is `SquireItemHandler` (IItemHandler). The method name changes.
**Why it happens:** Direct port of v0.5.0 code without adapting to v2 inventory API.
**How to avoid:** Use `squire.getItemHandler().getStackInSlot(i)` and `extractItem(slot, 1, false)` instead of `removeItem(slot, 1)`. The IItemHandler API is consistent across all handlers.

### Pitfall 4: Area Clear Mining Without Chunk Loading

**What goes wrong:** `setAreaTarget()` queues blocks across chunk boundaries; blocks in unloaded chunks return air, causing the handler to skip them silently. The area looks partially cleared with no error.
**Why it happens:** Minecraft doesn't load chunks on demand for block reads.
**How to avoid:** ARC-09 (chunk loading hook) is already implemented in Phase 1 — wire it in MiningHandler before queueing the area scan. The `SquireItemHandler` already has the chunk loading hook; MiningHandler needs to call the same mechanism for the area bounds.

### Pitfall 5: Farming Handler Plant-on-Farmland Bug

**What goes wrong:** `performPlant()` in v0.5.0 checks `pos.getBlock() instanceof FarmBlock` then plants at `pos.above()`. If the scan scans `pos` as the ground block (Y of cornerA), this is correct. But if cornerA is set to the crop level instead of ground level, planting fails silently.
**Why it happens:** `/squire farm <pos1> <pos2>` — the command must enforce that Y is the ground level, not crop level.
**How to avoid:** Document the command's Y-level contract clearly. The scan should always use the Y of cornerA as the ground/farmland level and plant one above.

### Pitfall 6: ChestHandler Skipping Modded Storage

**What goes wrong:** Squire can't deposit into MineColonies warehouses or other modded chests that don't extend `BaseContainerBlockEntity`.
**Why it happens:** Checking `instanceof BaseContainerBlockEntity` only covers vanilla.
**How to avoid:** v0.5.0 ChestHandler already has the correct fallback: try `BaseContainerBlockEntity` first, then `level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null)`. Port this pattern unchanged.

### Pitfall 7: TaskQueue NBT Desync on Disconnect

**What goes wrong:** Tasks queued in a session aren't persisted if the entity saves before the queue is written to NBT, or if the queue is rebuilt in a handler before `readAdditionalSaveData` runs.
**Why it happens:** TaskQueue is SquireBrain state, but SquireBrain is reconstructed each load — the queue must be serialized in `SquireEntity.addAdditionalSaveData` and loaded in `readAdditionalSaveData`, not in the brain constructor.
**How to avoid:** Follow the v0.5.0 pattern: `TaskQueue.save()` / `load()` called from SquireEntity NBT methods, not from SquireBrain constructor.

---

## Code Examples

### FishingHandler — Correct Loot Table Roll (v2 pattern)

```java
// Source: NeoForged 1.21.1 docs + minecraft.wiki/w/Fishing
// Required params: ORIGIN, TOOL. Optional: THIS_ENTITY, KILLER_ENTITY
private List<ItemStack> rollFishingLoot(SquireEntity s, ServerLevel level) {
    ItemStack rod = findFishingRod(s);
    if (rod.isEmpty()) return List.of();

    LootTable table = level.getServer()
        .reloadableRegistries()
        .getLootTable(BuiltInLootTables.FISHING);  // minecraft:gameplay/fishing

    LootParams params = new LootParams.Builder(level)
        .withParameter(LootContextParams.ORIGIN, s.position())
        .withParameter(LootContextParams.TOOL, rod)
        .withOptionalParameter(LootContextParams.THIS_ENTITY, s)
        .create(LootContextParamSets.FISHING);

    return table.getRandomItems(params);
}
```

**Note:** `BuiltInLootTables.FISHING` is the constant for `minecraft:gameplay/fishing`. This triggers the full loot table including fish/treasure/junk pools with correct probabilities.

### MiningHandler — Break Speed Formula (port unchanged)

```java
// Source: v0.5.0 MiningHandler.tickBreak() — matches vanilla player mining
// speed = toolSpeed / (destroySpeed * divisor) * configMultiplier * levelBonus
// divisor = 30 if correct tool, 100 if not
boolean canHarvest = tool.isCorrectToolForDrops(state) || !state.requiresCorrectToolForDrops();
float divisor = canHarvest ? 30.0F : 100.0F;
float levelBonus = 1.0F + (s.getSquireLevel() * SquireConfig.miningSpeedPerLevel.get().floatValue());
float progressPerTick = (toolSpeed / (destroySpeed * divisor))
    * SquireConfig.breakSpeedMultiplier.get().floatValue()
    * levelBonus;
```

### TorchHandler — Inventory Access (v2 adaptation)

```java
// v2: IItemHandler instead of SimpleContainer
// Source: v0.5.0 TorchHandler adapted to SquireItemHandler API
private int findTorchSlot() {
    IItemHandler inv = squire.getItemHandler();
    for (int i = 0; i < inv.getSlots(); i++) {
        ItemStack stack = inv.getStackInSlot(i);
        if (!stack.isEmpty() && stack.is(Items.TORCH)) return i;
    }
    return -1;
}

private void consumeTorch(int slot) {
    squire.getItemHandler().extractItem(slot, 1, false);
}
```

### ChestHandler — Dual-Path Container Interaction (port unchanged)

```java
// Source: v0.5.0 ChestHandler.tickInteract() — handles vanilla + modded storage
var blockEntity = s.level().getBlockEntity(targetChest);
if (blockEntity instanceof BaseContainerBlockEntity container) {
    // vanilla chests, barrels, droppers, etc.
    depositItems(s, container);
} else {
    IItemHandler handler = s.level().getCapability(
        Capabilities.ItemHandler.BLOCK, targetChest, null);
    if (handler != null) {
        depositItemsHandler(s, handler);  // modded storage, MineColonies warehouses
    }
}
```

### TaskQueue — NBT Persistence in SquireEntity

```java
// Source: v0.5.0 TaskQueue — NBT save/load via SquireEntity lifecycle methods
// In SquireEntity.addAdditionalSaveData():
tag.put("taskQueue", squireBrain.getTaskQueue().save());

// In SquireEntity.readAdditionalSaveData():
if (tag.contains("taskQueue")) {
    squireBrain.getTaskQueue().load(tag.getCompound("taskQueue"));
}
```

---

## State of the Art

| Old Approach                         | Current Approach                                        | When Changed              | Impact                                               |
| ------------------------------------ | ------------------------------------------------------- | ------------------------- | ---------------------------------------------------- |
| Hardcoded fish loot probabilities    | `LootTable.getRandomItems(LootContextParamSets.FISHING)`| v2 design decision        | Correct vanilla rates; mopack-overridable            |
| `SquireInventory.removeItem(slot, n)`| `SquireItemHandler.extractItem(slot, n, false)`         | Phase 1 (IItemHandler)    | All handlers must use IItemHandler API               |
| `squire.getSquireInventory()`        | `squire.getItemHandler()`                               | Phase 1 (IItemHandler)    | Method name changed; return type is IItemHandler     |
| `SquireAbilities.hasAutoTorch()`     | FSM tier gate via datapack ability check                | Phase 4/6 boundary        | Ability unlocks come from ProgressionHandler         |
| `TaskQueue` in `util/` package       | `TaskQueue` in `brain/` package                         | v2 architecture            | Belongs with brain state, not utility helpers        |

**Deprecated/outdated from v0.5.0:**

- `squire.getSquireAI()` — replaced by `squire.getBrain()` in v2
- `squire.getActivityLog()` — may not exist in v2; use standard logging or debug config flag
- `SquireAbilities.hasChestDeposit()` / `hasAutoTorch()` — replace with `brain.getProgression().hasAbility(SquireAbility.X)` per Phase 4 datapack system

---

## Open Questions

1. **Ability gate for TorchHandler and ChestHandler**
   - What we know: v0.5.0 gates these on `SquireAbilities.hasAutoTorch()` and `hasChestDeposit()` static methods
   - What's unclear: Phase 4 (ProgressionHandler) designs the ability unlock system — the exact method signature isn't known yet
   - Recommendation: Define a `SquireAbility` enum stub in Phase 6 that Phase 4 implements; for now gate on `squire.getSquireLevel() >= threshold` using config values as a temporary stand-in

2. **Fishing loot: spawn as entity drops vs add to inventory directly**
   - What we know: v0.5.0 adds to inventory directly, drops at feet if full
   - What's unclear: The full loot table roll may return treasure items (enchanted books, bows) that should behave the same way
   - Recommendation: Add directly to inventory (backpack slots only); drop any overflow at feet via `spawnAtLocation()`. Consistent with v0.5.0 behavior.

3. **FarmingHandler crop type extensibility**
   - What we know: v0.5.0 hardcodes wheat/potato/carrot/beetroot in `isSeedItem()` and `getCropForSeed()`
   - What's unclear: ATM10 has dozens of modded crops; hardcoded list breaks them
   - Recommendation: Check for `CropBlock` interface and use `BlockItem.getBlock()` to derive the planted crop, rather than hardcoding item→block mappings. Use a tag (`squire:plantable_seeds`) for seed detection.

4. **Activity log — does v2 have one?**
   - What we know: v0.5.0 used `squire.getActivityLog()` extensively in handlers for debug output
   - What's unclear: v2 ARCHITECTURE.md doesn't mention ActivityLog; SquireConfig has `activityLogging` and `logFsmTransitions` boolean flags
   - Recommendation: Use `if (SquireConfig.activityLogging.get()) { LOGGER.debug(...) }` pattern in v2 — no separate ActivityLog object needed.

---

## Validation Architecture

### Test Framework

| Property           | Value                                              |
| ------------------ | -------------------------------------------------- |
| Framework          | JUnit 5 (unit) + NeoForge GameTests (in-world)     |
| Config file        | Defined in Phase 1 (01-05-PLAN.md)                 |
| Quick run command  | `./gradlew test`                                   |
| Full suite command | `./gradlew test runGameTestServer`                 |

### Phase Requirements → Test Map

| Req ID | Behavior                              | Test Type   | Automated Command                                                    | File Exists? |
| ------ | ------------------------------------- | ----------- | -------------------------------------------------------------------- | ------------ |
| WRK-01 | Single block mine + inventory deposit | GameTest    | `./gradlew runGameTestServer --tests MiningHandlerGameTest`          | Wave 0       |
| WRK-02 | Area clear queues top-down            | Unit        | `./gradlew test --tests MiningHandlerTest`                           | Wave 0       |
| WRK-03 | Torch placed below light threshold    | Unit        | `./gradlew test --tests TorchHandlerTest`                            | Wave 0       |
| WRK-04 | Till/plant/harvest cycle              | GameTest    | `./gradlew runGameTestServer --tests FarmingHandlerGameTest`         | Wave 0       |
| WRK-05 | Fishing loot is non-empty, cod most common | Unit   | `./gradlew test --tests FishingHandlerTest`                          | Wave 0       |
| WRK-06 | Block placed, item consumed           | GameTest    | `./gradlew runGameTestServer --tests PlacingHandlerGameTest`         | Wave 0       |
| WRK-07 | Chest deposit moves items from squire | GameTest    | `./gradlew runGameTestServer --tests ChestHandlerGameTest`           | Wave 0       |
| WRK-08 | Queue executes tasks in order         | Unit        | `./gradlew test --tests TaskQueueTest`                               | Wave 0       |

### Sampling Rate

- **Per task commit:** `./gradlew test`
- **Per wave merge:** `./gradlew test runGameTestServer`
- **Phase gate:** Full suite green before `/gsd:verify-work`

### Wave 0 Gaps

- [ ] `src/test/java/com/sjviklabs/squire/brain/TaskQueueTest.java` — covers WRK-08 (NBT round-trip, FIFO order, max-length enforcement)
- [ ] `src/test/java/com/sjviklabs/squire/brain/handler/MiningHandlerTest.java` — covers WRK-02 (area queue ordering, stuck skip logic)
- [ ] `src/test/java/com/sjviklabs/squire/brain/handler/TorchHandlerTest.java` — covers WRK-03 (light threshold gate, cooldown)
- [ ] `src/test/java/com/sjviklabs/squire/brain/handler/FishingHandlerTest.java` — covers WRK-05 (loot roll produces valid items; mock ServerLevel needed)
- [ ] `src/test/java/com/sjviklabs/squire/gametest/MiningHandlerGameTest.java` — covers WRK-01
- [ ] `src/test/java/com/sjviklabs/squire/gametest/FarmingHandlerGameTest.java` — covers WRK-04
- [ ] `src/test/java/com/sjviklabs/squire/gametest/PlacingHandlerGameTest.java` — covers WRK-06
- [ ] `src/test/java/com/sjviklabs/squire/gametest/ChestHandlerGameTest.java` — covers WRK-07

---

## Sources

### Primary (HIGH confidence)

- v0.5.0 source: `C:/Users/Steve/Projects/squire-mod/src/main/java/com/sjviklabs/squire/ai/handler/` — MiningHandler, TorchHandler, FarmingHandler, FishingHandler, PlacingHandler, ChestHandler, ChatHandler read directly
- v0.5.0 source: `C:/Users/Steve/Projects/squire-mod/src/main/java/com/sjviklabs/squire/util/TaskQueue.java` — read directly
- v2 source: `SquireConfig.java` — all config keys for Phase 6 already present (mining, farming, fishing, inventory sections)
- [NeoForged 1.21.1 Loot Tables docs](https://docs.neoforged.net/docs/1.21.1/resources/server/loottables/) — `LootTable.getRandomItems()`, `LootParams.Builder`, `LootContextParamSets.FISHING` confirmed

### Secondary (MEDIUM confidence)

- [Minecraft Wiki — Fishing](https://minecraft.wiki/w/Fishing) — vanilla fish loot probabilities: cod 60%, salmon 25%, pufferfish 13%, tropical fish 2% (verified against wiki; different from v0.5.0 hardcoded values)
- [LootParams.Builder Javadoc (NeoForge 1.21.1)](https://aldak.netlify.app/javadoc/1.21.1-21.1.x/net/minecraft/world/level/storage/loot/lootparams.builder) — `withParameter(LootContextParams.ORIGIN, ...)` and `withParameter(LootContextParams.TOOL, ...)` as required FISHING params

### Tertiary (LOW confidence)

- `BuiltInLootTables.FISHING` as the constant for `minecraft:gameplay/fishing` — derived from NeoForge/Minecraft naming conventions; verify against actual NeoForge 21.1.x source before use

---

## Metadata

**Confidence breakdown:**

- Standard stack: HIGH — all handlers read from v0.5.0 source directly; no new libraries needed
- Architecture: HIGH — handler-per-behavior + FSM patterns established in ARCHITECTURE.md; Phase 6 is a straight application of existing patterns
- Fishing loot path: MEDIUM — NeoForge docs confirm `getRandomItems()` and `LootContextParamSets.FISHING`; `BuiltInLootTables.FISHING` constant name needs verification in actual source
- Pitfalls: HIGH — most are confirmed v0.5.0 bugs visible in the source code read

**Research date:** 2026-04-02
**Valid until:** 2026-06-01 (NeoForge 1.21.1 is stable; loot API hasn't changed in minor releases)

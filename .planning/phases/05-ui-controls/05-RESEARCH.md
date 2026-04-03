# Phase 5: UI and Controls - Research

**Researched:** 2026-04-02
**Domain:** NeoForge 1.21.1 GUI screens, AbstractContainerMenu, CustomPacketPayload, Brigadier commands, item pickup
**Confidence:** HIGH

---

<phase_requirements>

## Phase Requirements

| ID     | Description                                                  | Research Support                                                                 |
| ------ | ------------------------------------------------------------ | -------------------------------------------------------------------------------- |
| GUI-01 | Radial menu (R key) for stance switching and commands        | SquireRadialScreen v0.5.0 read directly; StreamCodec payload pattern confirmed   |
| GUI-02 | /squire commands for info, mine, place, and other operations | SquireCommand v0.5.0 read directly; Brigadier API unchanged in 1.21.1            |
| GUI-03 | Inventory screen with equipment slots and backpack view      | SquireScreen + SquireMenu v0.5.0 read; v2 SquireMenu stub exists and is correct  |
| INV-03 | Squire picks up nearby items with junk filtering             | ItemHandler v0.5.0 read directly; config keys itemPickupRange/autoPickup present |

</phase_requirements>

---

## Summary

Phase 5 builds four interconnected subsystems: the inventory GUI, a pure-client radial menu, a Brigadier command tree, and item pickup behavior. The v2 codebase already has a working `SquireMenu` stub (Phase 1, plan 01-03) with backpack and player slots registered. Phase 5 extends that stub with equipment slots and builds the `SquireScreen` client renderer on top of it — no menu logic is rewritten. The radial menu is a pure client overlay (`extends Screen`) that fires `SquireCommandPayload` to the server; no server state lives in it. The command tree is a straightforward Brigadier registration that Phase 5 scopes to info, mine target, place target, and a few operations — everything beyond work behaviors is deferred to later phases.

The single highest-risk item is dedicated server safety. NeoForge loads client classes (`Screen`, `GuiGraphics`, `RenderSystem`, `KeyMapping`) only on the physical client. Any server-side code path (capability handler, command, payload handler) that imports or references those classes will crash a dedicated server at classload. The fix is strict `src/main/java/com/sjviklabs/squire/client/` isolation with no cross-boundary imports. This was a known issue in v0.5.0 and the v2 architecture already documents the boundary, but Phase 5 is the phase that actually creates the most client classes — so the safety pass in plan 05-03 is not optional.

The second important insight is that `SquireMenu` in v2 intentionally omits equipment slots to keep Phase 1 focused. Phase 5 adds them. The slot index contract (`SquireItemHandler.EQUIPMENT_SLOTS = 6`, slots 0-5) is already locked by the handler. Phase 5 adds `SlotItemHandler` entries for those six indices and updates `quickMoveStack` to handle the expanded range. The screen layout from v0.5.0 is a direct port — same pixel math, same locked-row tier labels, same entity preview via `InventoryScreen.renderEntityInInventoryFollowsMouse`.

**Primary recommendation:** Port v0.5.0 classes directly. The architecture is proven. Scope Phase 5 to the stated requirements; strip commands that depend on work behaviors not yet built (farming, fishing, patrol, mount). Use `@EventBusSubscriber(value = Dist.CLIENT)` for all keybind and radial menu registration; never reference those classes from server paths.

---

## Standard Stack

### Core

| Library         | Version  | Purpose                                   | Why Standard                                      |
| --------------- | -------- | ----------------------------------------- | ------------------------------------------------- |
| NeoForge        | 21.1.x   | AbstractContainerMenu, Brigadier, packets | Mod platform — no choice                         |
| Brigadier       | bundled  | Command tree building                     | Minecraft's command system since 1.13             |
| NeoForge network| 21.1.x   | CustomPacketPayload + StreamCodec         | Replaces raw FriendlyByteBuf as of NeoForge 20.3 |

### Supporting

| Library                   | Version | Purpose                             | When to Use                                    |
| ------------------------- | ------- | ----------------------------------- | ---------------------------------------------- |
| `InventoryScreen` (MC)    | 1.21.1  | `renderEntityInInventoryFollowsMouse` | Entity preview in GUI — vanilla util, no extra dep |
| `GuiGraphics`             | 1.21.1  | All 2D draw calls in Screen         | Replaced MatrixStack direct calls in 1.20+     |
| `SlotItemHandler` (Neo)   | 21.1.x  | IItemHandler-backed slot in menu    | Required to wire SquireItemHandler into menu   |
| `KeyConflictContext`       | 21.1.x  | Keybind context for radial menu key | Prevents R key conflict with other mods        |

### Alternatives Considered

| Instead of                    | Could Use                 | Tradeoff                                               |
| ----------------------------- | ------------------------- | ------------------------------------------------------ |
| Custom radial geometry (TRIANGLE_STRIP) | Sprite-based texture atlas | Custom geometry gives clean scaling at any resolution; textures need art assets and atlas registration |
| Brigadier command tree        | Chat message parsing       | Brigadier is the NeoForge standard; tab-complete, permission levels, type safety |

**Installation:** No new dependencies. All required APIs are in the existing NeoForge 21.1.x dependency.

---

## Architecture Patterns

### Recommended Project Structure (Phase 5 additions)

```
src/main/java/com/sjviklabs/squire/
├── client/                         # CLIENT ONLY — no server imports permitted
│   ├── SquireScreen.java           # AbstractContainerScreen<SquireMenu> — GUI-03
│   ├── SquireRadialScreen.java     # extends Screen — R key radial, GUI-01
│   ├── SquireKeybinds.java         # KeyMapping constants
│   └── SquireClientEvents.java     # @EventBusSubscriber(value=Dist.CLIENT)
│                                   #   handles RegisterKeyMappingsEvent,
│                                   #   InputEvent.Key for radial open
├── inventory/
│   └── SquireMenu.java             # EXTENDED from Phase 1 stub
│                                   #   + equipment slots (0-5), getters for Screen
├── network/
│   └── SquireCommandPayload.java   # CustomPacketPayload, StreamCodec, CMD_ constants
│                                   #   + CMD_INVENTORY open handler
└── command/
    └── SquireCommand.java          # Brigadier tree — GUI-02
                                    #   info, mine <pos>, place <pos> <block>, mode, name
```

Handler addition in `brain/handler/`:
```
└── ItemHandler.java                # INV-03 — added in plan 05-04
```

Config keys already present (no additions needed):
```
inventory.itemPickupRange       = 3.0   (default)
inventory.autoPickup            = true
inventory.autoPickupTickRate    = 20
```

### Pattern 1: AbstractContainerMenu + Screen Split

**What:** `SquireMenu` runs server-side (slot registration, shift-click logic, `stillValid`). `SquireScreen` runs client-side only (rendering, layout constants, entity preview). They communicate through the container sync protocol — the menu sends data to the client; the Screen reads it through getters on the menu.

**When to use:** Any inventory GUI in NeoForge.

**Critical detail — data passed on menu open:** The server writes extra data into the `openMenu` buf. The client IContainerFactory reads it back. Phase 1 already sends `entityId` (int). Phase 5 must add level, totalXP, health, maxHealth, mode to that buf — or sync them via `SynchedEntityData` (already done for level and mode). Best approach: read entity state from the already-synched `SynchedEntityData` on the client-side factory, not from the extra buf, to avoid staleness.

```java
// SquireRegistry.java — client-side IContainerFactory (already partially implemented)
MENU_TYPES.register("squire_menu", () ->
    IMenuTypeExtension.create((windowId, inv, data) -> {
        int entityId = data.readInt();
        // Read additional fields written by server openMenu buf
        int level = data.readInt();
        int totalXP = data.readInt();
        float health = data.readFloat();
        float maxHealth = data.readFloat();
        byte mode = data.readByte();
        if (inv.player.level().getEntity(entityId) instanceof SquireEntity squire) {
            return new SquireMenu(windowId, inv, squire, level, totalXP, health, maxHealth, mode);
        }
        return null; // entity not loaded on client — safe null
    })
);
```

### Pattern 2: Equipment Slots in SquireMenu

**What:** The Phase 1 `SquireMenu` stub registers only backpack slots (indices 6+). Phase 5 adds 6 equipment slots at the front (indices 0-5 of the menu slot list, mapping to handler slots 0-5).

**Slot index mapping (menu index → handler slot):**

| Menu Index | Handler Slot              | Item Type           |
| ---------- | ------------------------- | ------------------- |
| 0          | SLOT_HELMET (0)           | ArmorItem head      |
| 1          | SLOT_CHEST (1)            | ArmorItem chest     |
| 2          | SLOT_LEGS (2)             | ArmorItem legs      |
| 3          | SLOT_BOOTS (3)            | ArmorItem feet      |
| 4          | SLOT_MAINHAND (4)         | Weapon/tool         |
| 5          | SLOT_OFFHAND (5)          | Shield/offhand      |
| 6 .. 6+N-1 | backpack slots           | Any item            |
| 6+N .. +26 | player main inventory    | —                   |
| 6+N+27 .. +8 | player hotbar          | —                   |

`quickMoveStack` must handle four cases: equipment→player, backpack→player, player→equipment (if valid item type), player→backpack.

**Equipment slot validation:** Use `SlotItemHandler` subclass that overrides `mayPlace(ItemStack)` to check item type — helmet slot only accepts head armor, etc. This prevents players from shoving a pickaxe into the helmet slot.

### Pattern 3: CustomPacketPayload with StreamCodec (ARC-08)

**What:** All client→server commands use `CustomPacketPayload` with `StreamCodec`. This is the ARC-08 requirement. No raw `FriendlyByteBuf` reads.

**Verified pattern from v0.5.0 (directly applicable):**

```java
// Source: squire-mod/network/SquireCommandPayload.java — direct port to v2
public record SquireCommandPayload(int commandId, int squireEntityId) implements CustomPacketPayload {

    public static final int CMD_FOLLOW    = 0;
    public static final int CMD_GUARD     = 1;
    public static final int CMD_STAY      = 2;
    public static final int CMD_INVENTORY = 3;
    // Phase 5 scope: follow, guard, stay, inventory
    // patrol, mount, store, fetch deferred to their respective phases

    public static final CustomPacketPayload.Type<SquireCommandPayload> TYPE =
            new CustomPacketPayload.Type<>(
                ResourceLocation.fromNamespaceAndPath(SquireMod.MODID, "squire_command"));

    public static final StreamCodec<ByteBuf, SquireCommandPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, SquireCommandPayload::commandId,
                    ByteBufCodecs.VAR_INT, SquireCommandPayload::squireEntityId,
                    SquireCommandPayload::new);

    @Override
    public CustomPacketPayload.Type<SquireCommandPayload> type() { return TYPE; }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(TYPE, STREAM_CODEC, SquireCommandPayload::handle);
    }
}
```

**Registration:** `@SubscribeEvent` on the mod event bus in `SquireMod` or `SquireRegistry`. Do NOT use the NeoForge game bus for payload registration.

### Pattern 4: Radial Menu — Client-Only Overlay

**What:** `SquireRadialScreen extends Screen`. Opens via `minecraft.setScreen(new SquireRadialScreen(squire))` from `SquireClientEvents`. `isPauseScreen()` returns false. `renderBackground()` is a no-op (skip vanilla blur). Wedge rendering uses `TRIANGLE_STRIP` with `RenderSystem.setShader(GameRenderer::getPositionColorShader)`.

**Radial open trigger:** The key handler in `SquireClientEvents` checks if the player is looking at a `SquireEntity` within range (raycast hit → entity hit). If yes, open the screen. The keybind is registered via `RegisterKeyMappingsEvent`.

**Phase 5 wedges (scoped to available Phase 5 FSM states):**

| Index | Label       | CMD constant    | FSM effect              |
| ----- | ----------- | --------------- | ----------------------- |
| 0     | Follow      | CMD_FOLLOW      | MODE_FOLLOW             |
| 1     | Stay        | CMD_STAY        | MODE_SIT                |
| 2     | Guard       | CMD_GUARD       | MODE_GUARD              |
| 3     | Inventory   | CMD_INVENTORY   | opens inventory screen  |

Patrol, Mount, Store, Fetch are deferred to their phases. Include stub wedges with disabled visual or omit entirely — 4 wedges works fine with the WEDGE_ANGLE math.

**`isPauseScreen() = false`** is critical. If this returns true, the server stops ticking while the radial is open, which breaks multiplayer completely.

### Pattern 5: Brigadier Command Registration

**What:** `@EventBusSubscriber` on the NeoForge game bus. Listens to `RegisterCommandsEvent`. Builds the literal tree via `Commands.literal("squire").then(...)`.

**Phase 5 scope (stripped from v0.5.0 full tree):**

```
/squire info             — player's squire level, XP, mode, HP (no permission required)
/squire mine <pos>       — order squire to mine block at pos (op 2)
/squire mine stop        — cancel mining (op 2)
/squire place <pos> <block> — order squire to place (op 2)
/squire mode <mode>      — set follow/stay/guard mode (no permission)
/squire name [text]      — set/clear custom name (no permission)
/squire list             — list all active squires (op 2)
/squire kill <player>    — kill a player's squire (op 2)
```

Do NOT implement queue, patrol, farm, fish, store, fetch, log, mount in Phase 5. Those commands go into their respective feature phases. Registering them now with stub bodies creates confusion about what works.

**`/squire info` fetches data from SquireEntity:** All needed data (`getLevel()`, `getTotalXP()`, `getSquireMode()`, `getHealth()`) exists on the entity. No new synced fields needed.

### Pattern 6: ItemHandler (INV-03) — Pickup with Junk Filtering

**What:** `ItemHandler` runs inline during the FOLLOWING/IDLE states (same pattern as v0.5.0). Scans AABB for `ItemEntity` instances. Filters against a junk item set. Navigates to item, picks up at close range via `IItemHandler.insertItem`.

**Config-driven parameters (already in v2 config):**
- `itemPickupRange` — AABB inflation radius (default 3.0)
- `autoPickup` — enable/disable pickup
- `autoPickupTickRate` — scan frequency

**Junk filter:** v0.5.0 used a hardcoded `Set<Item>`. v2 should read from config — `SquireConfig.junkItems` as a `List<String>` of item registry names. The config key does not exist yet; Phase 5 plan 05-04 adds it.

**Inventory-full guard:** If `getSlots()` are full, notify the owner with a cooldown (1200 ticks between alerts) and skip pickup. Do NOT attempt auto-store in Phase 5 — ChestHandler doesn't exist yet.

**Pickup execution:** Use `SquireItemHandler.insertItem(slot, stack, false)` in a loop from slot 6 (first backpack slot) through `getSlots()-1`. If remainder is empty, discard the ItemEntity; otherwise update its stack. Do NOT try to insert into equipment slots (0-5) during passive pickup.

### Anti-Patterns to Avoid

- **Client class in server path:** Any `Screen`, `GuiGraphics`, `Minecraft`, `RenderSystem`, `KeyMapping` import in a non-`client/` class will crash a dedicated server. The `client/` package must be entirely isolated.
- **Opening inventory from client:** `openMenu` must only be called server-side. The `CMD_INVENTORY` handler runs in `SquireCommandPayload.handle()` on the server — never from the Screen itself.
- **Equipment slot free-for-all:** Without `mayPlace()` validation on equipment `SlotItemHandler`, players can put any item in the helmet slot. This breaks auto-equip logic in Phase 4.
- **Radial screen `isPauseScreen() = true`:** Stops server tick on multiplayer. Always return false for non-pause overlays.
- **Registering Phase 6+ commands in Phase 5:** Stub implementations that silently do nothing are worse than no command. Register only what the squire can actually execute with Phase 5's brain state.
- **`moveItemStackTo` wrong index ranges:** If `quickMoveStack` passes wrong from/to indices, shift-click silently fails. Equipment slots are menu indices 0-5; backpack starts at 6; player inv starts at `6 + backpackSlots`.

---

## Don't Hand-Roll

| Problem               | Don't Build               | Use Instead                                          | Why                                              |
| --------------------- | ------------------------- | ---------------------------------------------------- | ------------------------------------------------ |
| Entity preview in GUI | Custom entity rendering   | `InventoryScreen.renderEntityInInventoryFollowsMouse` | Vanilla util handles camera, lighting, pose. Complex to replicate correctly. |
| Slot move logic       | Manual item transfer loop | `AbstractContainerMenu.moveItemStackTo`              | Handles partial stacks, overflow, empty slots correctly |
| Keybind conflict detection | String key comparison | `KeyMapping` + `KeyConflictContext.IN_GAME`         | NeoForge manages conflict detection for the Controls screen |
| Packet serialization  | Manual ByteBuf read/write | `StreamCodec.composite` + `ByteBufCodecs`           | Required by ARC-08; manual buf is flagged in architecture docs as prohibited |

---

## Common Pitfalls

### Pitfall 1: Dedicated Server Crash from Client Class Reference

**What goes wrong:** `NoClassDefFoundError` or `ClassNotFoundException` at startup on a dedicated server. The server JVM never loads `net.minecraft.client.*` classes.

**Why it happens:** Any server-side class that imports a client class causes the classloader to fail when the server tries to load that class. This includes: `SquireCommandPayload.handle()` referencing `Minecraft`, a capability handler importing `Screen`, or `SquireCommand` importing `KeyMapping`.

**How to avoid:** 
1. All `client/` package classes are loaded only via `@EventBusSubscriber(value = Dist.CLIENT)` or via `DistExecutor.unsafeRunWhenOn(Dist.CLIENT, ...)`.
2. `SquireClientEvents.java` is the only server-aware gateway — it is annotated `@EventBusSubscriber(modid = ..., bus = Bus.GAME, value = Dist.CLIENT)`.
3. `SquireCommandPayload.handle()` runs server-side. It must not reference any client class. When it needs to open inventory, it calls `serverPlayer.openMenu(...)` — pure server API.

**Warning signs:** Mod loads fine in singleplayer but crashes immediately on dedicated server startup.

### Pitfall 2: SquireMenu Slot Index Mismatch

**What goes wrong:** Shift-click moves items to wrong slots or fails silently. Items inserted via hopper go into equipment slots.

**Why it happens:** `quickMoveStack` index ranges must exactly match the slot registration order. If equipment slots are added to the menu, the backpack start index shifts from 0 to 6. Any code still hardcoding index 0 as backpack-start breaks.

**How to avoid:** Define a constant `MENU_EQUIPMENT_SLOTS = 6` in `SquireMenu`. Use it everywhere: slot registration loop, `quickMoveStack` range guards, Screen layout mapping.

**Warning signs:** Shift-clicking a pickaxe from player inventory puts it in slot 0 (helmet slot) instead of backpack.

### Pitfall 3: Extra Data Buffer Mismatch on Menu Open

**What goes wrong:** `IndexOutOfBoundsException` or garbage values when the client reads the extra data buf sent by the server's `openMenu`.

**Why it happens:** The server writes N bytes; the client reads M bytes. They must match exactly. Phase 1 only writes `entityId` (int, 4 bytes). Phase 5 adds more fields. The `IContainerFactory` in `SquireRegistry` must be updated simultaneously with the `openMenu` call in `SquireEntity.mobInteract` and in `SquireCommandPayload.handle`.

**How to avoid:** Keep a constant or comment listing all fields written/read in order. Better: since `SynchedEntityData` already syncs `level` and `mode`, prefer reading those from the entity reference on the client side rather than re-sending them in the buf. Minimizes buf fields to just `entityId`.

**Warning signs:** Health bar shows NaN or wrong level in inventory screen.

### Pitfall 4: Junk Filter Config Missing at Pickup Scan Time

**What goes wrong:** `NullPointerException` in `ItemHandler.findClosestItem()` because `SquireConfig.junkItems` isn't initialized before the first server tick.

**Why it happens:** NeoForge loads configs during `FMLCommonSetupEvent`. If `ItemHandler` is instantiated before config load completes (edge case during fast world load), the config reference is null.

**How to avoid:** Use lazy access — call `SquireConfig.junkItems.get()` inside `findClosestItem()` each scan, not in the constructor. `ModConfigSpec.ConfigValue.get()` is safe to call after config load.

### Pitfall 5: Radial Screen Blocks Player Input

**What goes wrong:** Player cannot move or interact while radial menu is open.

**Why it happens:** Default `Screen` behavior captures all input. The radial should close on movement keys or on clicking outside the wedge ring.

**How to avoid:** 
- `isPauseScreen()` returns `false` (already documented above).
- Override `keyPressed()` to close on the radial keybind (toggle). 
- Close on right-click or Escape via the default Screen behavior.
- Do NOT intercept WASD — those should still pass through. Movement while open is fine and expected.

---

## Code Examples

### StreamCodec Composite Pattern (ARC-08 compliant)

```java
// Source: squire-mod v0.5.0 SquireCommandPayload.java — verified working
public static final StreamCodec<ByteBuf, SquireCommandPayload> STREAM_CODEC =
        StreamCodec.composite(
                ByteBufCodecs.VAR_INT, SquireCommandPayload::commandId,
                ByteBufCodecs.VAR_INT, SquireCommandPayload::squireEntityId,
                SquireCommandPayload::new);
```

### Radial Wedge Angle Convention

```java
// Source: squire-mod v0.5.0 SquireRadialScreen.java — direct port
// Angle 0 = top (negative Y), increases clockwise.
// atan2(dx, -dy) maps screen coordinates to this convention.
float angle = (float) Math.atan2(dx, -dy);
if (angle < 0) angle += (float)(2.0 * Math.PI);
hoveredWedge = (int)(angle / WEDGE_ANGLE);
```

### Keybind Registration

```java
// Source: squire-mod v0.5.0 SquireKeybinds.java + ClientSetup.java
// RegisterKeyMappingsEvent fires on mod event bus, Dist.CLIENT only
@EventBusSubscriber(modid = SquireMod.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class SquireClientEvents {
    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(SquireKeybinds.RADIAL_MENU);
    }

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        Minecraft mc = Minecraft.getInstance();
        if (SquireKeybinds.RADIAL_MENU.consumeClick() && mc.screen == null) {
            // Raycast to find squire within interaction range
            Entity hitEntity = getEntityUnderCrosshair(mc);
            if (hitEntity instanceof SquireEntity squire) {
                mc.setScreen(new SquireRadialScreen(squire));
            }
        }
    }
}
```

### Equipment Slot with Type Validation

```java
// Pattern: SlotItemHandler subclass — restrict what can be placed in equipment slots
private static class EquipmentSlot extends SlotItemHandler {
    private final EquipmentSlot.Type allowedType;

    EquipmentSlot(IItemHandler handler, int index, int x, int y, EquipmentSlot.Type type) {
        super(handler, index, x, y);
        this.allowedType = type;
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return switch (allowedType) {
            case HELMET     -> stack.canEquip(net.minecraft.world.entity.EquipmentSlot.HEAD, null);
            case CHESTPLATE -> stack.canEquip(net.minecraft.world.entity.EquipmentSlot.CHEST, null);
            case LEGGINGS   -> stack.canEquip(net.minecraft.world.entity.EquipmentSlot.LEGS, null);
            case BOOTS      -> stack.canEquip(net.minecraft.world.entity.EquipmentSlot.FEET, null);
            case MAINHAND, OFFHAND -> true; // any item valid in hand slots
        };
    }
}
```

### Item Pickup Core Loop

```java
// Based on squire-mod v0.5.0 ItemHandler.java — adapted for v2 IItemHandler
private void pickUpItem(SquireEntity squire, ItemEntity itemEntity) {
    ItemStack original = itemEntity.getItem().copy();
    SquireItemHandler handler = squire.getItemHandler();

    // Insert into backpack slots only (skip equipment slots 0-5)
    ItemStack remainder = original.copy();
    for (int slot = SquireItemHandler.EQUIPMENT_SLOTS; slot < handler.getSlots(); slot++) {
        remainder = handler.insertItem(slot, remainder, false);
        if (remainder.isEmpty()) break;
    }

    if (remainder.isEmpty()) {
        itemEntity.discard();
    } else {
        itemEntity.setItem(remainder);
    }
}
```

### Brigadier `/squire info` Command

```java
// Phase 5 scope — reads from entity, no extra data needed
private static int showInfo(CommandSourceStack source) throws CommandSyntaxException {
    ServerPlayer player = source.getPlayerOrException();
    SquireEntity squire = findOwnedSquire(player);
    if (squire == null) {
        source.sendFailure(Component.literal("You have no active squire."));
        return 0;
    }
    source.sendSuccess(() -> Component.literal(String.format(
        "%s — Lv.%d | XP: %d | HP: %.0f/%.0f | Mode: %s",
        squire.hasCustomName() ? squire.getCustomName().getString() : "Squire",
        squire.getLevel(), squire.getTotalXP(),
        squire.getHealth(), squire.getMaxHealth(),
        modeName(squire.getSquireMode())
    )), false);
    return 1;
}
```

---

## State of the Art

| Old Approach (v0.5.0)                  | Current Approach (v2)                         | Impact                                        |
| -------------------------------------- | --------------------------------------------- | --------------------------------------------- |
| Raw `FriendlyByteBuf` in payloads      | `StreamCodec.composite` (ARC-08)              | No manual buf reads — type-safe encoding      |
| Scattered `ModItems`, `ModBlocks` regs | Single `SquireRegistry`                       | Registration order deterministic              |
| `SquireEquipmentContainer` (separate class) | Equipment slots in `SquireMenu` directly | One menu class instead of two containers      |
| Junk filter as hardcoded `Set<Item>`   | Config-driven `List<String>` + registry lookup | Server operators can customize junk list      |
| All commands including Phase 6+ ops   | Phase 5 scoped to 8 commands                  | No stub commands that confuse players         |

**Deprecated/outdated:**

- `SquireEquipmentContainer`: v0.5.0 used a separate container class for equipment slots alongside `SquireMenu`. v2 consolidates into a single menu — cleaner, fewer moving parts.
- `CMD_COME` teleport in v0.5.0: Teleportation is an explicit out-of-scope item in REQUIREMENTS.md. Do not include this command.

---

## Open Questions

1. **Phase 5 radial wedge count vs Phase 5 available behaviors**
   - What we know: v0.5.0 had 8 wedges. Phase 5 only has 4 functional commands (follow, stay, guard, inventory).
   - What's unclear: Should the remaining 4 wedge slots be visually grayed out (future behaviors) or simply omit them for a 4-wedge layout?
   - Recommendation: 4-wedge layout is cleaner for Phase 5. Adding gray stubs creates expectation of features not yet built. Update to more wedges in their respective phases.

2. **`/squire mine` vs Phase 5 scope**
   - What we know: GUI-02 requires mine and place commands. But `MiningHandler` doesn't exist until Phase 6.
   - What's unclear: Should mine/place commands be registered in Phase 5 (with stub bodies that message "not yet implemented") or deferred to Phase 6?
   - Recommendation: Register the command tree entries in Phase 5 with stub bodies that send "Coming in a future update." This satisfies GUI-02 formally and prevents "unknown command" errors if players try them. Phase 6 replaces the stub bodies.

3. **Junk filter config format**
   - What we know: Config has `itemPickupRange` and `autoPickup` but no junk list key yet.
   - What's unclear: `List<String>` item registry names vs `List<ResourceLocation>` in ModConfigSpec.
   - Recommendation: Use `builder.defineList("junkItems", defaultJunkList, e -> e instanceof String)` where `defaultJunkList` is the same set as v0.5.0 (`poisonous_potato`, `dead_bush`, etc.). Parse to `ResourceLocation` lazily inside `ItemHandler.findClosestItem()`.

---

## Validation Architecture

### Test Framework

| Property           | Value                                         |
| ------------------ | --------------------------------------------- |
| Framework          | JUnit 5 (unit) + NeoForge GameTest (in-world) |
| Config file        | `src/test/resources/junit-platform.properties` (existing) |
| Quick run command  | `./gradlew test`                              |
| Full suite command | `./gradlew test gameTestServer`               |

### Phase Requirements → Test Map

| Req ID | Behavior                                           | Test Type   | Automated Command                                              | File Exists? |
| ------ | -------------------------------------------------- | ----------- | -------------------------------------------------------------- | ------------ |
| GUI-01 | Radial screen does not pause; wedge angle math correct | unit    | `./gradlew test --tests "*.SquireRadialAngleTest"`             | Wave 0       |
| GUI-02 | `/squire info` returns correct level/XP/mode       | GameTest    | `./gradlew gameTestServer --tests "squire.SquireCommandTest"`  | Wave 0       |
| GUI-03 | SquireMenu slot count correct per tier; shift-click moves items | unit | `./gradlew test --tests "*.SquireMenuTest"`              | Wave 0       |
| INV-03 | ItemHandler scans AABB, respects junk filter, inserts into backpack only | unit | `./gradlew test --tests "*.ItemHandlerTest"`        | Wave 0       |

Note: `SquireScreen`, `SquireRadialScreen`, and `SquireKeybinds` are pure client classes. Rendering logic is not unit-testable in a headless environment. Test only the logic that can be extracted: angle math, slot index math, menu validity, pickup filtering.

### Sampling Rate

- **Per task commit:** `./gradlew test`
- **Per wave merge:** `./gradlew test gameTestServer`
- **Phase gate:** Full suite green before `/gsd:verify-work`

### Wave 0 Gaps

- [ ] `src/test/java/com/sjviklabs/squire/inventory/SquireMenuTest.java` — covers GUI-03 slot layout, quickMoveStack ranges
- [ ] `src/test/java/com/sjviklabs/squire/client/SquireRadialAngleTest.java` — covers GUI-01 angle calculation (pure math, no client dep)
- [ ] `src/test/java/com/sjviklabs/squire/brain/handler/ItemHandlerTest.java` — covers INV-03 junk filtering, pickup loop
- [ ] `src/gametest/java/com/sjviklabs/squire/gametest/SquireCommandTest.java` — covers GUI-02 `/squire info` output

---

## Sources

### Primary (HIGH confidence)

- v0.5.0 source read directly: `SquireScreen.java`, `SquireRadialScreen.java`, `SquireCommandPayload.java`, `SquireKeybinds.java`, `ItemHandler.java` — full implementations
- v2 source read directly: `SquireMenu.java`, `SquireItemHandler.java`, `SquireConfig.java`, `SquireEntity.java`, `SquireRegistry.java`, `SquireCrestItem.java` — current state confirmed
- v2 ARCHITECTURE.md — boundary rules, build order, anti-patterns

### Secondary (MEDIUM confidence)

- NeoForge `CustomPacketPayload` / `StreamCodec` pattern: verified against v0.5.0 working implementation
- `AbstractContainerMenu` + `SlotItemHandler` pattern: confirmed in v0.5.0 SquireMenu and v2 stub

### Tertiary (LOW confidence)

- `SlotItemHandler.mayPlace()` override for equipment type validation — pattern derived from vanilla ArmorStand slot logic; not directly verified against NeoForge 1.21.1 docs but is standard AbstractContainerScreen usage

---

## Metadata

**Confidence breakdown:**

- Standard stack: HIGH — no new dependencies; all APIs verified in v0.5.0 working code
- Architecture: HIGH — v0.5.0 source provides full working implementations; v2 stubs are consistent
- Pitfalls: HIGH — dedicated server crash and slot index mismatch are both documented from v0.5.0 experience
- Junk filter config addition: MEDIUM — pattern is standard ModConfigSpec defineList; specific API not re-verified against NeoForge docs

**Research date:** 2026-04-02
**Valid until:** 2026-05-02 (stable NeoForge 1.21.1 APIs; low churn risk)

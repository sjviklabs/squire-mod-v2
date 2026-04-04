package com.sjviklabs.squire.brain.handler;

import com.sjviklabs.squire.brain.SquireAIState;
import com.sjviklabs.squire.config.SquireConfig;
import com.sjviklabs.squire.entity.SquireEntity;
import com.sjviklabs.squire.inventory.SquireItemHandler;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Handles automatic pickup of nearby dropped items, filtered against a configurable junk list.
 *
 * INV-03: items on the ground near the squire enter its inventory automatically when space exists.
 *
 * Scan runs every 10 ticks (SCAN_INTERVAL). Items on the junk filter list are skipped.
 * All inventory operations use insertItem() exclusively — no direct stack mutation.
 *
 * FSM state: PICKING_UP_ITEM (priority 45, work tier).
 * Transition condition: (IDLE or FOLLOWING_OWNER) AND items nearby AND inventory has space.
 */
public class ItemHandler {

    private final SquireEntity squire;

    /** Ticks between pickup scans — balance responsiveness vs server load. */
    private static final int SCAN_INTERVAL = 10;

    private int scanTimer = 0;

    /** Cached junk set rebuilt when config changes (on first tick and after reloads). */
    private Set<ResourceLocation> junkCache = null;
    private List<? extends String> lastJunkConfig = null;

    public ItemHandler(SquireEntity squire) {
        this.squire = squire;
    }

    // ── Condition methods (called by FSM transitions) ────────────────────────

    /**
     * Returns true when there is at least one pickup-eligible item nearby and the
     * squire's inventory has at least one open backpack slot.
     * Called by the FSM enter transition — not run every tick.
     */
    public boolean hasNearbyItems() {
        if (!SquireConfig.autoPickup.get()) return false;
        SquireItemHandler handler = squire.getItemHandler();
        if (handler == null) return false;
        if (!hasBackpackSpace(handler)) return false;
        return !scanNearbyItems(handler).isEmpty();
    }

    // ── Lifecycle ────────────────────────────────────────────────────────────

    /** Called when entering PICKING_UP_ITEM state. */
    public void start() {
        scanTimer = 0; // trigger scan on first tick
    }

    /** Called when exiting PICKING_UP_ITEM state. */
    public void stop() {
        // No persistent state to clear — each tick is self-contained.
    }

    // ── Per-tick logic ───────────────────────────────────────────────────────

    /**
     * Per-tick pickup logic called while in PICKING_UP_ITEM state.
     * Scans every SCAN_INTERVAL ticks; picks up all eligible items in range immediately.
     * Returns PICKING_UP_ITEM while items remain, IDLE when done.
     */
    public SquireAIState tick(SquireEntity s) {
        if (!SquireConfig.autoPickup.get()) return SquireAIState.IDLE;

        if (--scanTimer > 0) return SquireAIState.PICKING_UP_ITEM;
        scanTimer = SCAN_INTERVAL;

        SquireItemHandler handler = s.getItemHandler();
        if (handler == null) return SquireAIState.IDLE;
        if (!hasBackpackSpace(handler)) return SquireAIState.IDLE;

        List<ItemEntity> candidates = scanNearbyItems(handler);
        if (candidates.isEmpty()) return SquireAIState.IDLE;

        for (ItemEntity itemEntity : candidates) {
            if (!itemEntity.isAlive()) continue;
            pickUpItem(s, handler, itemEntity);
        }

        // Re-check if more remain
        if (!hasBackpackSpace(handler)) return SquireAIState.IDLE;
        if (scanNearbyItems(handler).isEmpty()) return SquireAIState.IDLE;
        return SquireAIState.PICKING_UP_ITEM;
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    /**
     * Scans for ItemEntity instances within pickup range, excluding:
     *   - items with active pickup delay
     *   - items whose registry name is in the junk filter list
     * Returns only items that can be at least partially inserted (simulate check).
     */
    private List<ItemEntity> scanNearbyItems(SquireItemHandler handler) {
        double range = SquireConfig.itemPickupRange.get();
        Set<ResourceLocation> junk = getJunkSet();

        return squire.level().getEntitiesOfClass(
                ItemEntity.class,
                squire.getBoundingBox().inflate(range),
                itemEntity -> {
                    if (!itemEntity.isAlive() || itemEntity.hasPickUpDelay()) return false;
                    ItemStack stack = itemEntity.getItem();
                    if (stack.isEmpty()) return false;
                    ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
                    if (junk.contains(key)) return false;
                    // Capacity check via simulate — skip if no slot can accept even 1 item
                    return canInsertAny(handler, stack);
                }
        );
    }

    /**
     * Attempts to insert stack into backpack slots (6+) using insertItem().
     * Fully-inserted stacks discard the ItemEntity; partial inserts update its count.
     * Equipment slots (0-5) are excluded from auto-pickup — auto-equip is handled elsewhere.
     */
    private void pickUpItem(SquireEntity s, SquireItemHandler handler, ItemEntity itemEntity) {
        ItemStack remaining = itemEntity.getItem().copy();

        for (int slot = SquireItemHandler.EQUIPMENT_SLOTS; slot < handler.getSlots(); slot++) {
            if (remaining.isEmpty()) break;
            remaining = handler.insertItem(slot, remaining, false);
        }

        if (remaining.isEmpty()) {
            itemEntity.discard();
        } else {
            itemEntity.setItem(remaining);
        }

        var log = s.getActivityLog();
        if (log != null) {
            log.log("ITEM", "Picked up item (remaining=" + remaining.getCount() + ")");
        }
    }

    /**
     * Returns true if any backpack slot can accept at least 1 item from the given stack.
     * Uses simulate=true — does not mutate anything.
     */
    private boolean canInsertAny(SquireItemHandler handler, ItemStack stack) {
        for (int slot = SquireItemHandler.EQUIPMENT_SLOTS; slot < handler.getSlots(); slot++) {
            ItemStack result = handler.insertItem(slot, stack, true);
            if (result.getCount() < stack.getCount()) return true;
        }
        return false;
    }

    /**
     * Returns true if at least one backpack slot is not full.
     * Fast check before doing a full scan.
     */
    private boolean hasBackpackSpace(SquireItemHandler handler) {
        for (int slot = SquireItemHandler.EQUIPMENT_SLOTS; slot < handler.getSlots(); slot++) {
            if (handler.getStackInSlot(slot).isEmpty()) return true;
        }
        return false;
    }

    /**
     * Builds (and caches) the junk set from config. Rebuilds only when config value changes.
     * Config returns List<? extends String> of resource location strings.
     */
    private Set<ResourceLocation> getJunkSet() {
        List<? extends String> configList = SquireConfig.junkFilterList.get();
        if (junkCache == null || !configList.equals(lastJunkConfig)) {
            lastJunkConfig = configList;
            junkCache = new HashSet<>();
            for (String entry : configList) {
                try {
                    junkCache.add(ResourceLocation.parse(entry));
                } catch (Exception ignored) {
                    // Malformed entries are silently skipped — operator error, not crash-worthy
                }
            }
        }
        return junkCache;
    }
}

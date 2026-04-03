package com.sjviklabs.squire.brain.handler;

import com.sjviklabs.squire.entity.SquireEntity;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * Handles squire eating: finds food in IItemHandler inventory, consumes via extractItem(),
 * and heals the squire. Priority 20 in the FSM — fires below sitting (1) and above follow (30).
 *
 * CRITICAL: Food is consumed via IItemHandler.extractItem() ONLY.
 * Never call getStackInSlot().shrink() or setCount() — those bypass IItemHandler contract
 * and cause inventory corruption.
 *
 * One instance per squire. Holds eat cooldown state.
 */
public class SurvivalHandler {

    private static final float EAT_HEALTH_THRESHOLD = 0.75f;
    private static final int EAT_COOLDOWN_TICKS = 200; // 10 seconds between eats
    private static final int EAT_DURATION = 40;        // 2-second eating window

    private final SquireEntity squire;
    private int eatCooldown = 0;

    public SurvivalHandler(SquireEntity squire) {
        this.squire = squire;
    }

    /**
     * Returns true when the squire should start eating.
     * Conditions: HP below threshold, not on cooldown, food available in inventory.
     */
    public boolean shouldEat() {
        if (eatCooldown > 0) return false;
        float ratio = squire.getHealth() / squire.getMaxHealth();
        if (ratio >= EAT_HEALTH_THRESHOLD) return false;
        return hasFoodInInventory();
    }

    /**
     * Searches the IItemHandler inventory for food and consumes one item via extractItem().
     * Returns true if food was found and consumed; false otherwise.
     *
     * This is the authoritative consumption path — extractItem() is the only legal way
     * to remove items from an IItemHandler.
     */
    public boolean startEating() {
        IItemHandler inv = getInventory();
        if (inv == null) return false;

        for (int i = 0; i < inv.getSlots(); i++) {
            ItemStack stack = inv.getStackInSlot(i); // READ ONLY — just inspecting, not mutating
            if (stack.isEmpty()) continue;

            FoodProperties food = stack.get(DataComponents.FOOD);
            if (food == null) continue;

            // Consume via extractItem() — the only legal IItemHandler removal path.
            // Never call stack.shrink() or stack.setCount() here.
            ItemStack consumed = inv.extractItem(i, 1, false);
            if (!consumed.isEmpty()) {
                float healAmount = food.nutrition(); // nutrition() returns int, cast to float
                squire.heal(healAmount);
                eatCooldown = EAT_COOLDOWN_TICKS;

                var log = squire.getActivityLog();
                if (log != null) {
                    log.log("EAT", "Ate " + consumed.getHoverName().getString()
                            + " (+" + healAmount + " HP)"
                            + " HP now " + String.format("%.1f/%.1f",
                                squire.getHealth(), squire.getMaxHealth()));
                }
                return true;
            }
        }
        return false;
    }

    /**
     * Decrements the eat cooldown. Called every tick from SquireBrain's EATING tick transition.
     */
    public void tick() {
        if (eatCooldown > 0) {
            eatCooldown--;
        }
    }

    /**
     * Resets eat state. Subscribed to SIT_TOGGLE via SquireBrainEventBus so that
     * sitting clears any in-progress eating countdown.
     */
    public void reset() {
        eatCooldown = 0;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private boolean hasFoodInInventory() {
        IItemHandler inv = getInventory();
        if (inv == null) return false;
        for (int i = 0; i < inv.getSlots(); i++) {
            ItemStack stack = inv.getStackInSlot(i);
            if (!stack.isEmpty() && stack.get(DataComponents.FOOD) != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the squire's IItemHandler inventory via the typed getItemHandler() accessor.
     * Returns null if not available (safe — all callers are null-guarded).
     */
    private IItemHandler getInventory() {
        // SquireItemHandler extends ItemStackHandler which implements IItemHandler.
        // getItemHandler() is declared on SquireEntity and returns SquireItemHandler directly.
        return squire.getItemHandler();
    }
}

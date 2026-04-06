package com.sjviklabs.squire.brain.handler;

import com.sjviklabs.squire.config.SquireConfig;
import com.sjviklabs.squire.entity.ChatHandler;
import com.sjviklabs.squire.entity.SquireEntity;
import com.sjviklabs.squire.inventory.SquireItemHandler;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.items.IItemHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

/**
 * Utility handler for auto-crafting tools from squire inventory materials.
 *
 * Not an FSM handler — called synchronously by other handlers (MiningHandler,
 * FarmingHandler, FishingHandler) when a required tool is missing.
 *
 * Recipe matching uses the vanilla RecipeManager and CraftingRecipe system.
 * Only crafts tools up to the configured maxCraftTier (default: stone).
 *
 * Wave 1.5: Tool Self-Crafting
 */
public class CraftingHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(CraftingHandler.class);

    public CraftingHandler() {
        // Stateless utility — no squire reference needed at construction time
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Attempt to craft a tool matching the predicate from inventory materials.
     *
     * Steps:
     *   1. Check if squire already has a matching tool equipped → return null
     *   2. Get all CRAFTING recipes whose output matches the predicate
     *   3. Filter by maxCraftTier config
     *   4. Sort by tier (prefer lowest — wooden before stone before iron)
     *   5. Check if squire inventory has all required ingredients
     *   6. If match found: consume ingredients, return crafted ItemStack
     *
     * @param squire      the squire entity
     * @param toolMatcher predicate that matches the desired tool type
     * @return the crafted ItemStack, or null if crafting failed or tool already equipped
     */
    @Nullable
    public ItemStack tryCraftTool(SquireEntity squire, Predicate<ItemStack> toolMatcher) {
        if (!SquireConfig.autoCraftTools.get()) return null;

        // Already has a matching tool in mainhand — no need to craft
        ItemStack mainHand = squire.getMainHandItem();
        if (!mainHand.isEmpty() && toolMatcher.test(mainHand)) return null;

        // Also check backpack for a matching tool before crafting
        IItemHandler handler = squire.getItemHandler();
        for (int i = SquireItemHandler.EQUIPMENT_SLOTS; i < handler.getSlots(); i++) {
            ItemStack stack = handler.getStackInSlot(i);
            if (!stack.isEmpty() && toolMatcher.test(stack)) return null;
        }

        // Get recipe manager
        if (squire.level().isClientSide()) return null;
        var recipeManager = squire.level().getRecipeManager();
        List<RecipeHolder<CraftingRecipe>> allRecipes = recipeManager.getAllRecipesFor(RecipeType.CRAFTING);

        int maxTierRank = getTierRank(SquireConfig.maxCraftTier.get());

        // Collect candidate recipes: output matches predicate and tier is within config
        List<CraftingCandidate> candidates = new ArrayList<>();
        for (RecipeHolder<CraftingRecipe> holder : allRecipes) {
            CraftingRecipe recipe = holder.value();
            ItemStack output = recipe.getResultItem(squire.level().registryAccess());
            if (output.isEmpty() || !toolMatcher.test(output)) continue;

            int tierRank = getItemTierRank(output);
            if (tierRank < 0 || tierRank > maxTierRank) continue;

            candidates.add(new CraftingCandidate(recipe, output, tierRank));
        }

        if (candidates.isEmpty()) return null;

        // Sort by tier rank ascending — prefer lowest tier (cheapest materials)
        candidates.sort(Comparator.comparingInt(c -> c.tierRank));

        // Try each candidate recipe in tier order
        for (CraftingCandidate candidate : candidates) {
            ItemStack result = tryMatchAndConsume(squire, candidate.recipe, handler);
            if (result != null) {
                if (SquireConfig.activityLogging.get()) {
                    LOGGER.debug("[CRAFT] Crafted {} from inventory materials",
                            result.getDisplayName().getString());
                }
                return result;
            }
        }

        return null;
    }

    /**
     * Convenience wrapper: try to craft a tool, equip it, and send chat messages.
     *
     * @param squire      the squire entity
     * @param toolMatcher predicate that matches the desired tool type
     * @param toolName    human-readable tool name for chat messages (e.g. "pickaxe")
     * @return true if a tool was crafted and equipped, false if crafting failed
     */
    public boolean tryCraftIfMissing(SquireEntity squire, Predicate<ItemStack> toolMatcher, String toolName) {
        ItemStack crafted = tryCraftTool(squire, toolMatcher);
        if (crafted != null) {
            // Equip to mainhand, stowing current mainhand to backpack
            ItemStack currentMainhand = squire.getItemBySlot(EquipmentSlot.MAINHAND);
            squire.setItemSlot(EquipmentSlot.MAINHAND, crafted);
            if (!currentMainhand.isEmpty()) {
                IItemHandler handler = squire.getItemHandler();
                ItemStack remainder = currentMainhand;
                for (int i = SquireItemHandler.EQUIPMENT_SLOTS; i < handler.getSlots(); i++) {
                    remainder = handler.insertItem(i, remainder, false);
                    if (remainder.isEmpty()) break;
                }
                if (!remainder.isEmpty()) {
                    squire.spawnAtLocation(remainder);
                }
            }

            // Play crafting sound
            squire.playSound(SoundEvents.SMITHING_TABLE_USE, 1.0F, 1.0F);

            // Send chat message
            Player owner = squire.getOwner();
            if (owner != null) {
                ChatHandler.sendFormattedLine(squire, owner, ChatHandler.ChatEvent.CRAFTED_TOOL, toolName);
            }
            return true;
        }

        // Failed to craft — notify owner once
        Player owner = squire.getOwner();
        if (owner != null) {
            ChatHandler.sendFormattedLine(squire, owner, ChatHandler.ChatEvent.NEED_MATERIALS, toolName);
        }
        return false;
    }

    // ── Recipe matching internals ─────────────────────────────────────────────

    /**
     * Check if inventory has all ingredients for a recipe, and if so, consume them.
     *
     * @param squire  the squire entity
     * @param recipe  the crafting recipe to match
     * @param handler the squire's item handler
     * @return the crafted output ItemStack, or null if ingredients are insufficient
     */
    @Nullable
    private ItemStack tryMatchAndConsume(SquireEntity squire, CraftingRecipe recipe, IItemHandler handler) {
        List<Ingredient> ingredients = recipe.getIngredients();
        if (ingredients.isEmpty()) return null;

        // Track which slots and how many items to consume from each
        // We need to match each ingredient to a specific inventory slot without double-counting
        int backpackStart = SquireItemHandler.EQUIPMENT_SLOTS;
        int totalSlots = handler.getSlots();

        // simulated consumption: track remaining counts per slot
        int[] availableCounts = new int[totalSlots];
        for (int i = backpackStart; i < totalSlots; i++) {
            availableCounts[i] = handler.getStackInSlot(i).getCount();
        }

        // For each ingredient, find a matching slot and "reserve" one item
        int[] consumeFromSlot = new int[ingredients.size()]; // slot index for each ingredient
        java.util.Arrays.fill(consumeFromSlot, -1);

        for (int ingIdx = 0; ingIdx < ingredients.size(); ingIdx++) {
            Ingredient ingredient = ingredients.get(ingIdx);
            if (ingredient.isEmpty()) continue; // empty ingredient = empty slot in recipe grid

            boolean matched = false;
            for (int slot = backpackStart; slot < totalSlots; slot++) {
                if (availableCounts[slot] <= 0) continue;

                ItemStack slotStack = handler.getStackInSlot(slot);
                if (slotStack.isEmpty()) continue;

                if (ingredient.test(slotStack)) {
                    consumeFromSlot[ingIdx] = slot;
                    availableCounts[slot]--;
                    matched = true;
                    break;
                }
            }

            if (!matched) return null; // missing ingredient — can't craft
        }

        // All ingredients matched — consume for real
        for (int ingIdx = 0; ingIdx < ingredients.size(); ingIdx++) {
            int slot = consumeFromSlot[ingIdx];
            if (slot < 0) continue; // empty ingredient
            handler.extractItem(slot, 1, false);
        }

        // Return a copy of the recipe output
        return recipe.getResultItem(squire.level().registryAccess()).copy();
    }

    // ── Tier utilities ────────────────────────────────────────────────────────

    /**
     * Map a tier name string to a numeric rank for comparison.
     * Matches the config value format (wooden, stone, iron, diamond).
     */
    private static int getTierRank(String tierName) {
        return switch (tierName.toLowerCase()) {
            case "wooden", "wood" -> 0;
            case "stone" -> 1;
            case "iron" -> 2;
            case "diamond" -> 3;
            case "netherite" -> 4;
            default -> 1; // default to stone if unrecognized
        };
    }

    /**
     * Get the tier rank of a crafted item. Returns -1 if the item is not a TieredItem.
     */
    private static int getItemTierRank(ItemStack stack) {
        if (!(stack.getItem() instanceof TieredItem tiered)) return -1;

        var tier = tiered.getTier();
        if (tier == Tiers.WOOD) return 0;
        if (tier == Tiers.STONE) return 1;
        if (tier == Tiers.IRON) return 2;
        if (tier == Tiers.DIAMOND) return 3;
        if (tier == Tiers.NETHERITE) return 4;
        return -1; // modded tier — skip
    }

    // ── Internal record ──────────────────────────────────────────────────────

    private record CraftingCandidate(CraftingRecipe recipe, ItemStack output, int tierRank) {}
}

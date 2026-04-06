package com.sjviklabs.squire.compat;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;

/**
 * Soft compatibility accessor for Patchouli guidebook.
 *
 * Uses reflection to avoid compile-time dependency on Patchouli.
 * The mod runs fine without Patchouli installed — isActive() returns false
 * and all methods no-op gracefully.
 */
public final class PatchouliCompat {

    private static final Logger LOGGER = LoggerFactory.getLogger(PatchouliCompat.class);
    private static final String MODID = "patchouli";
    private static final ResourceLocation BOOK_ID =
            ResourceLocation.fromNamespaceAndPath("squire", "squire_manual");

    static Boolean modPresent = null;

    private PatchouliCompat() {}

    public static boolean isActive() {
        if (modPresent == null) {
            modPresent = ModList.get().isLoaded(MODID);
        }
        return modPresent;
    }

    /**
     * Returns a Patchouli book ItemStack via reflection.
     * Only call when isActive() is true.
     */
    public static ItemStack getBookStack() {
        try {
            Class<?> apiClass = Class.forName("vazkii.patchouli.api.PatchouliAPI");
            Method getMethod = apiClass.getMethod("get");
            Object api = getMethod.invoke(null);
            Method bookMethod = api.getClass().getMethod("getBookStack", ResourceLocation.class);
            return (ItemStack) bookMethod.invoke(api, BOOK_ID);
        } catch (Exception e) {
            LOGGER.warn("[Squire] Failed to get Patchouli book stack: {}", e.getMessage());
            return ItemStack.EMPTY;
        }
    }

    public static boolean playerHasBook(Player player) {
        ItemStack bookStack = getBookStack();
        if (bookStack.isEmpty()) return true; // Fail safe — don't spam empty items
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack slot = player.getInventory().getItem(i);
            if (ItemStack.isSameItemSameComponents(slot, bookStack)) {
                return true;
            }
        }
        return false;
    }

    public static void giveBookIfAbsent(Player player) {
        if (!playerHasBook(player)) {
            ItemStack book = getBookStack();
            if (!book.isEmpty()) {
                player.getInventory().add(book);
            }
        }
    }
}

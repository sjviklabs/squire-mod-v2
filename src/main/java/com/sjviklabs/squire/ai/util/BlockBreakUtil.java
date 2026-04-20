package com.sjviklabs.squire.ai.util;

import com.sjviklabs.squire.entity.SquireEntity;
import com.sjviklabs.squire.inventory.SquireItemHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Shared helper for all block-breaking job AIs (MinerAI, LumberjackAI, PlacingAI-clearing).
 *
 * Computes drops from the block state + squire's held tool (via {@code state.getDrops(LootParams)}),
 * destroys the block without natural drops ({@code destroyBlock(pos, false, squire)}), then inserts
 * the drops directly into the squire's backpack. Overflow spawns as ItemEntities at the squire.
 *
 * This is the v3.1.4 farming-harvest pattern carried forward — it short-circuits the broken
 * ItemEntity → ItemHandler pickup round-trip that made first-harvest replant fail in v3.1.3.
 * Reused here so MinerAI doesn't leave a trail of dropped cobblestone the squire can't pick up.
 */
public final class BlockBreakUtil {

    private BlockBreakUtil() {}

    /**
     * Break {@code pos} at {@code state}, move drops into the squire's inventory, overflow to ground.
     *
     * @return true if the block was actually destroyed (caller should advance their queue),
     *         false if destroyBlock returned false (block was air, protected, etc.)
     */
    public static boolean breakAndCollect(SquireEntity squire, ServerLevel level, BlockPos pos, BlockState state) {
        LootParams.Builder paramsBuilder = new LootParams.Builder(level)
                .withParameter(LootContextParams.BLOCK_STATE, state)
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
                .withParameter(LootContextParams.TOOL, squire.getMainHandItem())
                .withOptionalParameter(LootContextParams.THIS_ENTITY, squire);

        List<ItemStack> drops = state.getDrops(paramsBuilder);
        boolean destroyed = level.destroyBlock(pos, false, squire);
        if (!destroyed) return false;

        SquireItemHandler handler = squire.getItemHandler();
        if (handler == null) {
            // No inventory — fall back to spawning drops on the ground. Should not happen in
            // practice (squire always has an item handler), but fail safe instead of fail silent.
            for (ItemStack drop : drops) squire.spawnAtLocation(drop);
            return true;
        }

        for (ItemStack drop : drops) {
            ItemStack remaining = drop;
            for (int slot = SquireItemHandler.EQUIPMENT_SLOTS; slot < handler.getSlots(); slot++) {
                if (remaining.isEmpty()) break;
                remaining = handler.insertItem(slot, remaining, false);
            }
            if (!remaining.isEmpty()) {
                squire.spawnAtLocation(remaining);
            }
        }
        return true;
    }

    /** True if the squire has a pickaxe anywhere (mainhand or backpack). */
    public static boolean hasPickaxe(SquireEntity squire) {
        return hasToolClass(squire, net.minecraft.world.item.PickaxeItem.class);
    }

    /** True if the squire has an axe anywhere (mainhand or backpack). */
    public static boolean hasAxe(SquireEntity squire) {
        return hasToolClass(squire, net.minecraft.world.item.AxeItem.class);
    }

    /** True if the squire has a tool of the given class in mainhand or backpack. */
    public static boolean hasToolClass(SquireEntity squire, Class<?> toolClass) {
        if (toolClass.isInstance(squire.getMainHandItem().getItem())) return true;
        SquireItemHandler handler = squire.getItemHandler();
        if (handler == null) return false;
        for (int i = SquireItemHandler.EQUIPMENT_SLOTS; i < handler.getSlots(); i++) {
            ItemStack stack = handler.getStackInSlot(i);
            if (!stack.isEmpty() && toolClass.isInstance(stack.getItem())) return true;
        }
        return false;
    }

    /**
     * Equip the first tool of the requested class from backpack into mainhand, if one exists.
     * Returns true if a swap happened (or the mainhand already held the right tool class).
     */
    public static boolean equipToolFromBackpack(SquireEntity squire, Class<?> toolClass) {
        if (toolClass.isInstance(squire.getMainHandItem().getItem())) return true;
        SquireItemHandler handler = squire.getItemHandler();
        if (handler == null) return false;

        for (int i = SquireItemHandler.EQUIPMENT_SLOTS; i < handler.getSlots(); i++) {
            ItemStack candidate = handler.getStackInSlot(i);
            if (!candidate.isEmpty() && toolClass.isInstance(candidate.getItem())) {
                ItemStack tool = handler.extractItem(i, candidate.getCount(), false);
                ItemStack oldMain = squire.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND);
                squire.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, tool);
                // Put whatever we were holding back in the backpack (or drop on overflow)
                if (!oldMain.isEmpty()) {
                    ItemStack remainder = oldMain;
                    for (int j = SquireItemHandler.EQUIPMENT_SLOTS; j < handler.getSlots() && !remainder.isEmpty(); j++) {
                        remainder = handler.insertItem(j, remainder, false);
                    }
                    if (!remainder.isEmpty()) squire.spawnAtLocation(remainder);
                }
                return true;
            }
        }
        return false;
    }

    /** Is the block directly above this position a leaf block? Used by LumberjackAI tree detection. */
    public static boolean isLeaf(BlockState state) {
        return state.is(net.minecraft.tags.BlockTags.LEAVES);
    }

    /** Is the block a log/wood block? Used by LumberjackAI. */
    public static boolean isLog(BlockState state) {
        return state.is(net.minecraft.tags.BlockTags.LOGS);
    }
}

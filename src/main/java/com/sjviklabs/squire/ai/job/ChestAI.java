package com.sjviklabs.squire.ai.job;

import com.minecolonies.api.entity.ai.statemachine.AITarget;
import com.minecolonies.api.entity.ai.statemachine.tickratestatemachine.TickRateStateMachine;
import com.sjviklabs.squire.ai.state.SquireAIState;
import com.sjviklabs.squire.entity.SquireEntity;
import com.sjviklabs.squire.inventory.SquireItemHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Chest deposit job AI — walk to a target container and unload the squire's backpack into it.
 *
 * Accepts any block that exposes {@link Capabilities#ItemHandler.BLOCK} capability or extends
 * {@link BaseContainerBlockEntity}. Vanilla chests / barrels / shulkers plus modded storage
 * (Iron Chests, Sophisticated Storage, Storage Drawers, Refined Storage, AE2) all work.
 * This is the v3.1.2 homechest capability-detection pattern carried forward.
 *
 * Equipment slots (mainhand, offhand, armor) are preserved — only backpack slots (6+) are dumped.
 * Overflow (chest full mid-transfer) stops cleanly; remaining stacks stay in the squire.
 *
 * State tree: IDLE → DEPOSITING → IDLE. Single assignment per invocation — the AI does not
 * loop; after a deposit the assignment clears and the controller swaps back to Idle/Follow.
 */
public final class ChestAI implements JobAI {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChestAI.class);
    private static final double WALK_SPEED = 1.0;
    private static final double REACH_SQ = 9.0;   // 3 blocks
    private static final int DEPOSIT_COOLDOWN_TICKS = 10;

    private final SquireEntity squire;
    private final TickRateStateMachine<SquireAIState> machine;

    private BlockPos chestPos = null;
    private int cooldown = 0;

    public ChestAI(SquireEntity squire) {
        this.squire = squire;
        this.machine = new TickRateStateMachine<>(
                SquireAIState.IDLE,
                ex -> LOGGER.error("[ChestAI] state machine error", ex)
        );

        machine.addTransition(new AITarget<>(
                SquireAIState.IDLE,
                () -> chestPos != null,
                () -> SquireAIState.PLACING,  // reuse PLACING state umbrella (no dedicated DEPOSITING)
                10
        ));

        machine.addTransition(new AITarget<>(
                SquireAIState.PLACING,
                () -> chestPos == null,
                () -> {
                    squire.getNavigation().stop();
                    return SquireAIState.IDLE;
                },
                5
        ));

        machine.addTransition(new AITarget<>(
                SquireAIState.PLACING,
                () -> true,
                this::tickDepositing,
                1
        ));
    }

    /** Assign a target chest position. AI wakes up, walks there, dumps backpack, returns. */
    public void setTarget(BlockPos pos) {
        this.chestPos = pos;
        this.cooldown = 0;
    }

    public boolean hasWork() {
        return chestPos != null;
    }

    private SquireAIState tickDepositing() {
        if (!(squire.level() instanceof ServerLevel level) || chestPos == null) {
            return SquireAIState.PLACING;
        }

        // Validate the target is still a container.
        IItemHandler chest = getChestHandler(level, chestPos);
        if (chest == null) {
            // Target gone / not a container — abort.
            chestPos = null;
            return SquireAIState.PLACING;
        }

        squire.getLookControl().setLookAt(chestPos.getX() + 0.5, chestPos.getY() + 0.5, chestPos.getZ() + 0.5);
        double distSq = squire.distanceToSqr(chestPos.getX() + 0.5, chestPos.getY() + 0.5, chestPos.getZ() + 0.5);
        if (distSq > REACH_SQ) {
            squire.getNavigation().moveTo(chestPos.getX() + 0.5, chestPos.getY(), chestPos.getZ() + 0.5, WALK_SPEED);
            return SquireAIState.PLACING;
        }

        squire.getNavigation().stop();
        if (cooldown > 0) { cooldown--; return SquireAIState.PLACING; }

        // Transfer backpack (slots ≥ EQUIPMENT_SLOTS) into the chest. Stop on first overflow
        // to avoid running the chest-insert loop for every tick when it's full.
        SquireItemHandler backpack = squire.getItemHandler();
        if (backpack == null) { chestPos = null; return SquireAIState.PLACING; }

        boolean anyMoved = false;
        for (int slot = SquireItemHandler.EQUIPMENT_SLOTS; slot < backpack.getSlots(); slot++) {
            ItemStack stack = backpack.getStackInSlot(slot);
            if (stack.isEmpty()) continue;
            if (shouldPreserve(stack)) continue;   // v4.0.2 — keep tools/armor/weapons

            ItemStack remaining = stack.copy();
            for (int chestSlot = 0; chestSlot < chest.getSlots() && !remaining.isEmpty(); chestSlot++) {
                remaining = chest.insertItem(chestSlot, remaining, false);
            }

            // Write back whatever didn't fit.
            int movedCount = stack.getCount() - remaining.getCount();
            if (movedCount > 0) {
                backpack.extractItem(slot, movedCount, false);
                anyMoved = true;
            }
            if (!remaining.isEmpty()) break; // chest full
        }

        if (anyMoved) squire.swing(net.minecraft.world.InteractionHand.MAIN_HAND);

        // One-shot: clear the assignment after a single pass. Controller swaps us out next check.
        chestPos = null;
        cooldown = DEPOSIT_COOLDOWN_TICKS;
        return SquireAIState.PLACING;
    }

    /**
     * True if the stack is a tool, weapon, armor, or shield that the squire uses for its jobs.
     * Such items stay in the backpack on deposit — the squire needs them to keep working and
     * to stay equipped through auto-equip swaps. Bulk items (logs, stone, seeds, fish, etc.)
     * have no class in this list and get deposited normally.
     *
     * DiggerItem covers pickaxes, axes, hoes, shovels. TieredItem would include swords too
     * via the tool hierarchy, but SwordItem is explicit for clarity. Any modded tool that
     * extends these base classes is preserved automatically.
     */
    private static boolean shouldPreserve(ItemStack stack) {
        return stack.getItem() instanceof DiggerItem     // pickaxe / axe / hoe / shovel
                || stack.getItem() instanceof SwordItem
                || stack.getItem() instanceof BowItem
                || stack.getItem() instanceof CrossbowItem
                || stack.getItem() instanceof TridentItem
                || stack.getItem() instanceof FishingRodItem
                || stack.getItem() instanceof ArmorItem   // helmet / chest / legs / boots
                || stack.getItem() instanceof ShieldItem;
    }

    /**
     * Resolve an {@link IItemHandler} for the block at {@code pos}. Prefers the capability API
     * (works for modded storage); falls back to vanilla {@link BaseContainerBlockEntity} for
     * blocks that don't expose the capability but are still containers.
     */
    @org.jetbrains.annotations.Nullable
    private static IItemHandler getChestHandler(ServerLevel level, BlockPos pos) {
        IItemHandler cap = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null);
        if (cap != null) return cap;
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof BaseContainerBlockEntity bce) {
            return new net.neoforged.neoforge.items.wrapper.InvWrapper(bce);
        }
        return null;
    }

    @Override
    public void tick() {
        machine.tick();
    }

    @Override
    public SquireAIState getCurrentState() {
        return machine.getState();
    }
}

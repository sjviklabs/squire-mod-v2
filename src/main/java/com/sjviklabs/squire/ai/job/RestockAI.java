package com.sjviklabs.squire.ai.job;

import com.minecolonies.api.entity.ai.statemachine.AITarget;
import com.minecolonies.api.entity.ai.statemachine.tickratestatemachine.TickRateStateMachine;
import com.sjviklabs.squire.ai.state.SquireAIState;
import com.sjviklabs.squire.entity.SquireEntity;
import com.sjviklabs.squire.inventory.SquireEquipmentHelper;
import com.sjviklabs.squire.inventory.SquireItemHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumSet;

/**
 * Chest withdrawal AI — walk to a chest and pull one best-in-category item for every gear
 * category the squire has zero coverage for, then auto-equip via SquireEquipmentHelper.
 *
 * v4.0.3 — triggered when a squire's equipped tool breaks mid-work (slot becomes empty
 * and backpack has no replacement). Default target is {@link SquireEntity#getLastDepositChest()},
 * so "resupply from the chest you deposited into" works with zero player configuration.
 *
 * Symmetry with {@link ChestAI}: ChestAI deposits surplus using {@link GearCategorizer},
 * RestockAI pulls missing using the same categories. Both agree on what counts as "gear"
 * and what the best copy of a category looks like.
 *
 * <p>State tree mirrors ChestAI: IDLE → PLACING (umbrella) → IDLE. Single pass per invocation.
 * Controller priority: Guard &gt; work &gt; Restock &gt; Follow &gt; Idle — combat preempts, but
 * restock preempts follow so a missing-tool squire doesn't trail the player uselessly.
 *
 * <p>If the target chest is destroyed / no longer a container, the assignment is dropped
 * cleanly; the squire falls back to Follow or Idle. If the chest has nothing for any
 * missing category, the squire walks there, finds nothing, and returns — no infinite loop.
 */
public final class RestockAI implements JobAI {

    private static final Logger LOGGER = LoggerFactory.getLogger(RestockAI.class);
    private static final double WALK_SPEED = 1.0;
    private static final double REACH_SQ = 9.0;    // 3 blocks — same as ChestAI
    private static final int PULL_COOLDOWN_TICKS = 10;

    private final SquireEntity squire;
    private final TickRateStateMachine<SquireAIState> machine;

    @Nullable
    private BlockPos chestPos = null;
    private int cooldown = 0;

    public RestockAI(SquireEntity squire) {
        this.squire = squire;
        this.machine = new TickRateStateMachine<>(
                SquireAIState.IDLE,
                ex -> LOGGER.error("[RestockAI] state machine error", ex)
        );

        machine.addTransition(new AITarget<>(
                SquireAIState.IDLE,
                () -> chestPos != null,
                () -> SquireAIState.PLACING,
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
                this::tickRestocking,
                1
        ));
    }

    /**
     * Assign a target chest. Usually called by SquireAIController when the squire is missing
     * gear and has a known last-deposit chest. Also callable directly from a command
     * (/squire resupply &lt;x&gt; &lt;y&gt; &lt;z&gt;).
     */
    public void setTarget(BlockPos pos) {
        this.chestPos = pos;
        this.cooldown = 0;
    }

    public boolean hasWork() {
        return chestPos != null;
    }

    private SquireAIState tickRestocking() {
        if (!(squire.level() instanceof ServerLevel level) || chestPos == null) {
            return SquireAIState.PLACING;
        }

        IItemHandler chest = ChestAI.getChestHandler(level, chestPos);
        if (chest == null) {
            // Chest gone / not a container — clear the squire's memory of it so we don't
            // keep routing to a phantom location on every future tool break.
            if (chestPos.equals(squire.getLastDepositChest())) {
                squire.setLastDepositChest(null);
            }
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

        SquireItemHandler backpack = squire.getItemHandler();
        if (backpack == null) { chestPos = null; return SquireAIState.PLACING; }

        // What categories is the squire missing entirely? Only pull for those — we don't
        // upgrade existing gear from a chest visit; that's the player's call (they can
        // manually swap items through the squire menu).
        EnumSet<GearCategorizer.Category> missing = GearCategorizer.missingCategories(squire, backpack);
        boolean anyPulled = false;

        for (GearCategorizer.Category cat : missing) {
            int bestChestSlot = -1;
            int bestScore = Integer.MIN_VALUE;
            for (int i = 0; i < chest.getSlots(); i++) {
                ItemStack s = chest.getStackInSlot(i);
                if (s.isEmpty()) continue;
                if (GearCategorizer.classify(s) != cat) continue;
                int score = GearCategorizer.scoreStack(s);
                if (score > bestScore) {
                    bestScore = score;
                    bestChestSlot = i;
                }
            }
            if (bestChestSlot < 0) continue;   // chest has nothing for this category

            // Pull exactly one (tools don't stack; armor/shields don't stack either).
            ItemStack pulled = chest.extractItem(bestChestSlot, 1, false);
            if (pulled.isEmpty()) continue;

            // Insert into backpack. If backpack is full, put it back in the chest — we
            // shouldn't drop tools on the ground just because we tried to pull them.
            ItemStack remaining = pulled.copy();
            for (int slot = SquireItemHandler.EQUIPMENT_SLOTS; slot < backpack.getSlots() && !remaining.isEmpty(); slot++) {
                remaining = backpack.insertItem(slot, remaining, false);
            }
            if (remaining.isEmpty()) {
                anyPulled = true;
            } else {
                // Backpack full — return to chest and stop pulling more categories (we'd
                // just bounce them right back). The squire will need to deposit first.
                chest.insertItem(bestChestSlot, remaining, false);
                break;
            }
        }

        if (anyPulled) {
            squire.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
            // Auto-equip the pulled gear so a restocked pickaxe lands in mainhand
            // and armor lands in the right slots without another tick cycle.
            SquireEquipmentHelper.runFullEquipCheck(squire);
        }

        chestPos = null;
        cooldown = PULL_COOLDOWN_TICKS;
        return SquireAIState.PLACING;
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

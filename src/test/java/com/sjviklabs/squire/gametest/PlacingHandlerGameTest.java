package com.sjviklabs.squire.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * NeoForge GameTests for PlacingHandler in-world behavior.
 *
 * Phase 6 plan 06-03: Scaffold only — test_blockPlaced_itemConsumed calls
 * helper.succeed() immediately. Full implementation wired in Phase 6 execution after
 * PlacingHandler is registered in SquireBrain and the /squire place command is wired.
 *
 * TODO Phase 6 execution: replace helper.succeed() with:
 *   1. Give squire one oak_planks block item via getItemHandler().insertItem()
 *   2. Call placingHandler.setTarget(targetPos, Items.OAK_PLANKS) via SquireBrain.getPlacingHandler()
 *   3. Assert inventorySlot is cached (>= 0) immediately after setTarget()
 *   4. Set squire position adjacent to targetPos (within placingReach)
 *   5. Tick the world until PLACING_APPROACH → PLACING_BLOCK → IDLE transition completes
 *   6. Assert level.getBlockState(targetPos).getBlock() == Blocks.OAK_PLANKS
 *   7. Assert getItemHandler().getStackInSlot(cachedSlot).isEmpty() (item consumed)
 *
 * Registration: @GameTestHolder("squire") registers under the "squire" namespace.
 * Template: "squire:empty" — already registered in Phase 1 (empty.nbt structure).
 *
 * Run full game tests: ./gradlew runGameTestServer
 */
@GameTestHolder(value = "squire")
@PrefixGameTestTemplate(false)
public class PlacingHandlerGameTest {

    /**
     * Phase 6 stub: verifies GameTest infrastructure is wired correctly.
     *
     * Phase 6 execution implementation: squire places a block item from its inventory
     * at a specified target position. Asserts the block appears in the world and the
     * item is consumed from the IItemHandler inventory via extractItem(inventorySlot, 1, false).
     * Slot caching verified: inventorySlot is set once in setTarget(), not recomputed per tick.
     */
    @GameTest(template = "squire:empty")
    public static void test_blockPlaced_itemConsumed(GameTestHelper helper) {
        // TODO Phase 6 execution: wire PlacingHandler to SquireBrain and implement real assertion
        helper.succeed();
    }
}

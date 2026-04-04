package com.sjviklabs.squire.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * NeoForge GameTests for MiningHandler in-world behavior.
 *
 * Phase 6 plan 06-01: Scaffold only — test_singleBlockMine_depositsDrops calls
 * helper.succeed() immediately. Full implementation wired in Phase 6 execution after
 * MiningHandler is registered in SquireBrain and the /squire mine command is wired.
 *
 * TODO Phase 6 execution: replace helper.succeed() with:
 *   1. Place a stone block adjacent to squire spawn position
 *   2. Call miningHandler.setTarget(blockPos) via SquireBrain.getMiningHandler()
 *   3. Tick the world until MINING_APPROACH → MINING_BREAK → IDLE transition completes
 *   4. Assert the stone drop (cobblestone ItemEntity) was inserted into squire inventory
 *   5. Assert squire inventory slot contains at least one cobblestone
 *
 * Registration: @GameTestHolder("squire") registers under the "squire" namespace.
 * Template: "squire:empty" — already registered in Phase 1 (empty.nbt structure).
 *
 * Run full game tests: ./gradlew runGameTestServer
 */
@GameTestHolder(value = "squire")
@PrefixGameTestTemplate(false)
public class MiningHandlerGameTest {

    /**
     * Phase 6 stub: verifies GameTest infrastructure is wired correctly.
     *
     * Phase 6 execution implementation: squire mines a single stone block adjacent to
     * its spawn position and deposits the cobblestone drop into its IItemHandler inventory
     * via getItemHandler().insertItem(). Overflow drops are spawned at squire's feet via
     * spawnAtLocation().
     */
    @GameTest(template = "squire:empty")
    public static void test_singleBlockMine_depositsDrops(GameTestHelper helper) {
        // TODO Phase 6 execution: wire MiningHandler to SquireBrain and implement real assertion
        helper.succeed();
    }
}

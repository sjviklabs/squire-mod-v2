package com.sjviklabs.squire.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * NeoForge GameTest for FarmingHandler in-world behavior.
 *
 * WRK-04: Squire farms (till + plant + harvest cycle).
 *
 * Phase 6 stub: test calls helper.succeed() immediately.
 * Full implementation in WRK-04 test map execution (future wave).
 *
 * Registration: @GameTestHolder("squire") registers under the "squire" namespace.
 * Template: "squire:empty" — same structure used by SquireEntityGameTest.
 *
 * Run: ./gradlew runGameTestServer
 */
@GameTestHolder(value = "squire")
@PrefixGameTestTemplate(false)
public class FarmingHandlerGameTest {

    /**
     * Till/plant/harvest cycle stub.
     *
     * TODO Phase 6 execution (WRK-04 test map):
     *   - Place farmland grid in test structure
     *   - Summon squire with hoe + wheat seeds in inventory
     *   - Call FarmingHandler.setArea(cornerA, cornerB)
     *   - Assert: farmland tilled, seeds planted, mature crops harvested on schedule
     *   - Assert: wheat seeds in squire inventory after harvest
     */
    @GameTest(template = "squire:empty")
    public static void test_tillPlantHarvest(GameTestHelper helper) {
        // TODO Phase 6: real assertions for till/plant/harvest cycle
        helper.succeed();
    }
}

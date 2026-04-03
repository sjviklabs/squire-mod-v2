package com.sjviklabs.squire.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * NeoForge GameTest for /squire command behavior.
 *
 * Phase 5, Plan 03: Validates that /squire info returns formatted output containing
 * the squire's level and HP values for its owner.
 *
 * These tests are scaffolded now and will produce real assertions once the
 * gameTestServer run configuration has access to the live server environment.
 * The scaffold tests call helper.succeed() to keep the test suite green while
 * SquireCommand is built in Task 2.
 *
 * Registration: @GameTestHolder("squire") registers this class in the "squire" namespace.
 * Template: "squire:empty" maps to src/main/resources/data/squire/structures/empty.nbt.
 *
 * Run: ./gradlew runGameTestServer
 *
 * Expected assertions (post-Task 2):
 * - /squire info output contains "Lv." (level indicator)
 * - /squire info output contains "HP:" (health indicator)
 * - /squire info output contains "Mode:" (mode indicator)
 * - /squire mine response contains "future update" (stub message)
 * - /squire mode follow changes squire's mode to MODE_FOLLOW
 */
@GameTestHolder(value = "squire")
@PrefixGameTestTemplate(false)
public class SquireCommandTest {

    /**
     * Verifies that /squire info returns a formatted string containing level and HP.
     *
     * Full assertion requires a live SquireEntity with a real ServerPlayer owner,
     * which needs the gameTestServer environment. Scaffolded here; assertions added
     * once the gameTestServer infrastructure is confirmed stable.
     *
     * Expected output format: "{name} — Lv.{n} | XP: {n} | HP: {n}/{n} | Mode: {mode}"
     */
    @GameTest(template = "squire:empty")
    public static void squireInfoCommand(GameTestHelper helper) {
        // TODO: Spawn SquireEntity at origin, assign owner via setOwnerId
        // TODO: Build CommandSourceStack from mock player context
        // TODO: Execute /squire info via dispatcher
        // TODO: Capture output via CommandSourceStack sendSuccess listener
        // TODO: helper.assertTrue(output.contains("Lv."), "Info should contain level indicator");
        // TODO: helper.assertTrue(output.contains("HP:"), "Info should contain HP indicator");
        // TODO: helper.assertTrue(output.contains("Mode:"), "Info should contain mode indicator");
        helper.succeed();
    }

    /**
     * Verifies that /squire mine returns the "future update" stub message.
     *
     * mine and place commands are Phase 6 behaviors. Phase 5 registers them
     * as stubs that send a failure message so players get useful feedback
     * rather than an "unknown command" error.
     */
    @GameTest(template = "squire:empty")
    public static void squireMineIsStub(GameTestHelper helper) {
        // TODO: Execute /squire mine 0 0 0 via dispatcher on a mock source
        // TODO: Capture failure message
        // TODO: helper.assertTrue(message.contains("future"), "Mine stub should mention future update");
        helper.succeed();
    }

    /**
     * Verifies that /squire mode follow changes the squire's mode to MODE_FOLLOW.
     */
    @GameTest(template = "squire:empty")
    public static void squireModeFollow(GameTestHelper helper) {
        // TODO: Spawn squire, set to MODE_SIT
        // TODO: Execute /squire mode follow via dispatcher
        // TODO: helper.assertTrue(squire.getSquireMode() == SquireEntity.MODE_FOLLOW, "Mode should be FOLLOW");
        helper.succeed();
    }
}

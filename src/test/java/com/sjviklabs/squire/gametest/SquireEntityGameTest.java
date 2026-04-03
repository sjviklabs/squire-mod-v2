package com.sjviklabs.squire.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * NeoForge GameTests for SquireEntity in-world behavior.
 *
 * Phase 1: Scaffold only — all tests call helper.succeed() immediately.
 * Phase 2: Real assertions added (spawn, NBT persist, despawn, one-per-player).
 *
 * Registration: @GameTestHolder("squire") registers this class under the "squire"
 * namespace. build.gradle gameTestServer run config passes
 * -Dneoforge.enabledGameTestNamespaces=squire to enable it.
 *
 * Template: "squire:empty" maps to src/main/resources/data/squire/structures/empty.nbt.
 * If the structure file is missing, game tests will fail with "structure not found" —
 * which is expected in Phase 1. Create the structure via ./gradlew runClient
 * and the /test create command before running ./gradlew runGameTestServer.
 *
 * Run full game tests: ./gradlew runGameTestServer
 */
@GameTestHolder(value = "squire")
@PrefixGameTestTemplate(false)
public class SquireEntityGameTest {

    /**
     * Phase 1 stub: verifies GameTest infrastructure is wired correctly.
     * Phase 2 implementation: spawn SquireEntity at test origin, assert entity.isAlive() == true,
     * verify the entity is registered in the world's entity list.
     */
    @GameTest(template = "squire:empty")
    public static void canSpawnSquire(GameTestHelper helper) {
        // TODO Phase 2: BlockPos origin = helper.absolutePos(new BlockPos(1, 1, 1));
        // TODO Phase 2: SquireEntity squire = new SquireEntity(SquireRegistry.SQUIRE_ENTITY.get(), helper.getLevel());
        // TODO Phase 2: squire.moveTo(origin.getX(), origin.getY(), origin.getZ());
        // TODO Phase 2: helper.getLevel().addFreshEntity(squire);
        // TODO Phase 2: helper.assertTrue(squire.isAlive(), "Squire should be alive after spawn");
        helper.succeed();
    }

    /**
     * Phase 1 stub: NBT persistence round-trip.
     * Phase 2 implementation: write squire state to CompoundTag, create a new entity,
     * call readAdditionalSaveData, compare level, customName, ownerUUID against originals.
     */
    @GameTest(template = "squire:empty")
    public static void nbtRoundTrip(GameTestHelper helper) {
        // TODO Phase 2: CompoundTag tag = new CompoundTag();
        // TODO Phase 2: squire.addAdditionalSaveData(tag);
        // TODO Phase 2: SquireEntity loaded = new SquireEntity(SquireRegistry.SQUIRE_ENTITY.get(), helper.getLevel());
        // TODO Phase 2: loaded.readAdditionalSaveData(tag);
        // TODO Phase 2: helper.assertEqual(squire.getLevel(), loaded.getLevel(), "Level must survive NBT round-trip");
        helper.succeed();
    }

    /**
     * Phase 1 stub: one-per-player enforcement.
     * Phase 2 implementation: create a mock player with a known UUID, summon a squire,
     * attempt to summon a second squire, verify the second attempt is rejected (returns false
     * or leaves only one squire alive in the world).
     */
    @GameTest(template = "squire:empty")
    public static void onlyOneSquirePerPlayer(GameTestHelper helper) {
        // TODO Phase 2: ServerPlayer player = helper.makeMockPlayer();
        // TODO Phase 2: boolean first  = SquireSummonHelper.trySummon(player, helper.getLevel());
        // TODO Phase 2: boolean second = SquireSummonHelper.trySummon(player, helper.getLevel());
        // TODO Phase 2: helper.assertTrue(first,   "First summon should succeed");
        // TODO Phase 2: helper.assertFalse(second, "Second summon should be rejected (one-per-player)");
        helper.succeed();
    }
}

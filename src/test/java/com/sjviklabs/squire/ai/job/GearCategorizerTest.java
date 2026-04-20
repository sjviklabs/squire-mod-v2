package com.sjviklabs.squire.ai.job;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static com.sjviklabs.squire.ai.job.GearCategorizer.Category;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link GearCategorizer} — the shared classification + scoring logic used
 * by ChestAI (keep-best deposit) and RestockAI (pull-missing). Divergence between deposit
 * and restock on what "gear" means or what "best" means would create a bug where the
 * squire deposits an item it would never pull back, or pulls back one it would reject.
 *
 * <p>These tests pin the classifier outputs and scoring order so future refactors can't
 * silently break the deposit/restock contract. Pure unit tests — no SquireEntity, no
 * server context, no registry access beyond {@link Items} static fields.
 *
 * <h2>Harness status (as of v4.0.5)</h2>
 *
 * <p>The NeoForge JUnit test harness (via {@code unitTest enable()} in build.gradle) fails
 * to bootstrap because MineColonies is declared {@code type = "required"} in mods.toml, and
 * the test harness's mod loader doesn't see the MC jar sitting in {@code libs/} (picked up
 * by {@code localRuntime fileTree} for main, but not registered as a loaded mod for tests).
 * Error: {@code Mod squire requires minecolonies 1.1.1299 or above — not installed}.
 *
 * <p>Running {@code ./gradlew test} currently fails at harness init for ALL tests in the
 * project, not just this one. The builds themselves work via {@code ./gradlew build -x test}.
 *
 * <p>This file is deliberately shipped ahead of the harness fix:
 * <ul>
 *   <li>It's executable specification for {@link GearCategorizer} — reviewers can read the
 *       test expectations to understand the contract without running anything.</li>
 *   <li>When the harness is fixed (register MineColonies as an additional test mod, or
 *       factor the MC dep to optional for tests only), these tests light up instantly.</li>
 *   <li>Deleting them because "they don't run yet" would be the wrong move — the tests
 *       capture regression-prevention intent that lives nowhere else.</li>
 * </ul>
 *
 * <p>Not tested here: {@link GearCategorizer#missingCategories(com.sjviklabs.squire.entity.SquireEntity, com.sjviklabs.squire.inventory.SquireItemHandler)}
 * which requires a live entity. That path is exercised end-to-end by manual in-game testing
 * until we have a gametest harness for SquireEntity.
 */
class GearCategorizerTest {

    // ────────────────────────────────────────────────────────────────────────
    // classify() — every gear type lands in the right bucket
    // ────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("classify() puts items in the right Category bucket")
    class ClassifyTests {

        @ParameterizedTest(name = "{0} → {1}")
        @MethodSource("gearClassifications")
        void classifiesGearCorrectly(ItemStack stack, Category expected) {
            assertEquals(expected, GearCategorizer.classify(stack));
        }

        static Stream<Arguments> gearClassifications() {
            return Stream.of(
                    // Armor — one case per slot to lock the enum mapping
                    Arguments.of(new ItemStack(Items.IRON_HELMET), Category.ARMOR_HEAD),
                    Arguments.of(new ItemStack(Items.DIAMOND_CHESTPLATE), Category.ARMOR_CHEST),
                    Arguments.of(new ItemStack(Items.NETHERITE_LEGGINGS), Category.ARMOR_LEGS),
                    Arguments.of(new ItemStack(Items.GOLDEN_BOOTS), Category.ARMOR_FEET),

                    // Weapons
                    Arguments.of(new ItemStack(Items.WOODEN_SWORD), Category.MELEE_WEAPON),
                    Arguments.of(new ItemStack(Items.NETHERITE_SWORD), Category.MELEE_WEAPON),
                    Arguments.of(new ItemStack(Items.DIAMOND_AXE), Category.AXE),   // axe kept separate for Lumberjack

                    // Pure tools
                    Arguments.of(new ItemStack(Items.STONE_PICKAXE), Category.PICKAXE),
                    Arguments.of(new ItemStack(Items.IRON_SHOVEL), Category.SHOVEL),
                    Arguments.of(new ItemStack(Items.DIAMOND_HOE), Category.HOE),

                    // Ranged + specialty
                    Arguments.of(new ItemStack(Items.BOW), Category.BOW),
                    Arguments.of(new ItemStack(Items.CROSSBOW), Category.CROSSBOW),
                    Arguments.of(new ItemStack(Items.TRIDENT), Category.TRIDENT),
                    Arguments.of(new ItemStack(Items.FISHING_ROD), Category.FISHING_ROD),
                    Arguments.of(new ItemStack(Items.SHIELD), Category.SHIELD)
            );
        }

        @ParameterizedTest(name = "{0} → null (non-gear)")
        @MethodSource("nonGearItems")
        void returnsNullForNonGear(ItemStack stack) {
            assertNull(GearCategorizer.classify(stack),
                    "non-gear should classify to null so deposit path runs unconditionally");
        }

        static Stream<Arguments> nonGearItems() {
            return Stream.of(
                    Arguments.of(new ItemStack(Items.COBBLESTONE)),       // mining drop
                    Arguments.of(new ItemStack(Items.OAK_LOG)),           // lumberjack drop
                    Arguments.of(new ItemStack(Items.WHEAT)),             // farm drop
                    Arguments.of(new ItemStack(Items.WHEAT_SEEDS)),       // farm seed
                    Arguments.of(new ItemStack(Items.COD)),               // fish drop
                    Arguments.of(new ItemStack(Items.DIAMOND)),           // raw ore, not a tool
                    Arguments.of(new ItemStack(Items.STICK)),             // crafting material
                    Arguments.of(new ItemStack(Items.TORCH)),             // placeable
                    Arguments.of(ItemStack.EMPTY)                         // empty slot — must not NPE
            );
        }

        @Test
        @DisplayName("axe and sword stay in separate categories (Lumberjack safety)")
        void axeAndSwordAreSeparateBuckets() {
            // If axe and sword shared a category, a squire with a diamond sword + iron axe
            // would deposit the axe (losing tree-chopping ability) on the next keep-best pass.
            // This test pins the separation so a future refactor can't collapse them.
            assertEquals(Category.MELEE_WEAPON, GearCategorizer.classify(new ItemStack(Items.DIAMOND_SWORD)));
            assertEquals(Category.AXE, GearCategorizer.classify(new ItemStack(Items.DIAMOND_AXE)));
            assertNotEquals(
                    GearCategorizer.classify(new ItemStack(Items.DIAMOND_SWORD)),
                    GearCategorizer.classify(new ItemStack(Items.DIAMOND_AXE))
            );
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // scoreStack() — higher score = better, vanilla tier order must hold
    // ────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("scoreStack() ranks gear by tier then subscores")
    class ScoreTests {

        @Test
        @DisplayName("pickaxe tier ordering: netherite > diamond > iron > stone > gold > wood")
        void pickaxeTierOrdering() {
            int wood = GearCategorizer.scoreStack(new ItemStack(Items.WOODEN_PICKAXE));
            int gold = GearCategorizer.scoreStack(new ItemStack(Items.GOLDEN_PICKAXE));
            int stone = GearCategorizer.scoreStack(new ItemStack(Items.STONE_PICKAXE));
            int iron = GearCategorizer.scoreStack(new ItemStack(Items.IRON_PICKAXE));
            int diamond = GearCategorizer.scoreStack(new ItemStack(Items.DIAMOND_PICKAXE));
            int netherite = GearCategorizer.scoreStack(new ItemStack(Items.NETHERITE_PICKAXE));

            // Tiers.ordinal() order: WOOD=0, GOLD=1, STONE=2, IRON=3, DIAMOND=4, NETHERITE=5.
            // Scoring must match this so keep-best and restock agree on "better."
            assertTrue(wood < gold, "wood < gold");
            assertTrue(gold < stone, "gold < stone");
            assertTrue(stone < iron, "stone < iron");
            assertTrue(iron < diamond, "iron < diamond");
            assertTrue(diamond < netherite, "diamond < netherite");
        }

        @Test
        @DisplayName("sword tier ordering matches pickaxe ordering")
        void swordTierOrdering() {
            int wood = GearCategorizer.scoreStack(new ItemStack(Items.WOODEN_SWORD));
            int iron = GearCategorizer.scoreStack(new ItemStack(Items.IRON_SWORD));
            int netherite = GearCategorizer.scoreStack(new ItemStack(Items.NETHERITE_SWORD));
            assertTrue(wood < iron, "wood sword < iron sword");
            assertTrue(iron < netherite, "iron sword < netherite sword");
        }

        @Test
        @DisplayName("armor ordering: defense + toughness dominates")
        void armorDefenseOrdering() {
            int leather = GearCategorizer.scoreStack(new ItemStack(Items.LEATHER_CHESTPLATE));
            int iron = GearCategorizer.scoreStack(new ItemStack(Items.IRON_CHESTPLATE));
            int diamond = GearCategorizer.scoreStack(new ItemStack(Items.DIAMOND_CHESTPLATE));
            int netherite = GearCategorizer.scoreStack(new ItemStack(Items.NETHERITE_CHESTPLATE));
            assertTrue(leather < iron, "leather < iron chestplate");
            assertTrue(iron < diamond, "iron < diamond chestplate");
            assertTrue(diamond < netherite, "diamond < netherite chestplate");
        }

        @Test
        @DisplayName("damaged tool scores lower than pristine same-tier tool")
        void durabilityTiebreakSamePick() {
            ItemStack pristine = new ItemStack(Items.DIAMOND_PICKAXE);
            ItemStack damaged = new ItemStack(Items.DIAMOND_PICKAXE);
            damaged.setDamageValue(damaged.getMaxDamage() / 2);

            int pristineScore = GearCategorizer.scoreStack(pristine);
            int damagedScore = GearCategorizer.scoreStack(damaged);

            assertTrue(pristineScore > damagedScore,
                    "pristine diamond pickaxe (" + pristineScore + ") should outscore"
                    + " half-damaged diamond pickaxe (" + damagedScore + ")");
        }

        @Test
        @DisplayName("tier dominates durability — iron pickaxe beats broken diamond pickaxe")
        void tierDominatesDurability() {
            // A 1-durability-left diamond pickaxe is still higher tier than a pristine iron
            // pickaxe. The scoring weights (tier × 100,000 vs durability × 1) guarantee this
            // regardless of how battered the diamond is.
            ItemStack almostBrokenDiamond = new ItemStack(Items.DIAMOND_PICKAXE);
            almostBrokenDiamond.setDamageValue(almostBrokenDiamond.getMaxDamage() - 1);
            ItemStack pristineIron = new ItemStack(Items.IRON_PICKAXE);

            int dmgDiamondScore = GearCategorizer.scoreStack(almostBrokenDiamond);
            int pristineIronScore = GearCategorizer.scoreStack(pristineIron);

            assertTrue(dmgDiamondScore > pristineIronScore,
                    "1-HP diamond pickaxe (" + dmgDiamondScore + ") should outscore"
                    + " pristine iron pickaxe (" + pristineIronScore + ")"
                    + " because tier dominates durability");
        }

        @Test
        @DisplayName("empty stack scores without throwing")
        void emptyStackScoreDoesNotThrow() {
            // scoreStack isn't expected to receive empty stacks (callers filter them out),
            // but a defensive NPE here would crash a tick. Pin the no-throw contract.
            assertDoesNotThrow(() -> GearCategorizer.scoreStack(ItemStack.EMPTY));
        }
    }
}

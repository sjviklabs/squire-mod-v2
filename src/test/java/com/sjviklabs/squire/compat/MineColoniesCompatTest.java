package com.sjviklabs.squire.compat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for MineColoniesCompat — specifically covers the absent-mod guard path.
 *
 * MineColonies is NOT on the test classpath. ModList is not available in the JUnit
 * environment (no NeoForge bootstrap). Tests exercise pure Java logic:
 * - When the mod is absent (modPresent stays null / isActive() returns false), all
 *   classification methods must return false.
 * - isFromPackage hierarchy-walk behavior is tested indirectly via the guard path
 *   and directly via the exposed helper structure.
 *
 * Design: Tests do NOT instantiate Minecraft entities (requires NeoForge bootstrap).
 * All entity-typed parameters are passed as null — safe because the absent-mod guard
 * short-circuits before any entity field is accessed.
 */
class MineColoniesCompatTest {

    /**
     * Reset the static cache between tests so each test starts with a clean state.
     * Uses reflection to reset the private static Boolean field.
     */
    @org.junit.jupiter.api.BeforeEach
    void resetModPresentCache() throws Exception {
        var field = MineColoniesCompat.class.getDeclaredField("modPresent");
        field.setAccessible(true);
        field.set(null, null);
    }

    // ── Mod detection ─────────────────────────────────────────────────────────

    /**
     * MineColonies is not on the test classpath.
     * ModList is not bootstrapped in JUnit, so isActive() must return false.
     */
    @Test
    void isActive_returnsFalse_whenModAbsent() {
        assertFalse(MineColoniesCompat.isActive(),
                "isActive() must return false when MineColonies is not loaded");
    }

    // ── Colonist detection ────────────────────────────────────────────────────

    /**
     * When MineColonies is absent, isColonist() must short-circuit and return false
     * without touching the entity parameter (null-safe via the mod guard).
     */
    @Test
    void isColonist_returnsFalse_whenModAbsent() {
        // Pass null — the absent-mod guard short-circuits before any entity access
        assertFalse(MineColoniesCompat.isColonist(null),
                "isColonist() must return false when MineColonies is not loaded");
    }

    // ── Raider detection ─────────────────────────────────────────────────────

    /**
     * When MineColonies is absent, isRaider() must short-circuit and return false.
     */
    @Test
    void isRaider_returnsFalse_whenModAbsent() {
        assertFalse(MineColoniesCompat.isRaider(null),
                "isRaider() must return false when MineColonies is not loaded");
    }

    // ── Friendly-fire prevention ──────────────────────────────────────────────

    /**
     * When MineColonies is absent, isFriendly() must short-circuit and return false.
     */
    @Test
    void isFriendly_returnsFalse_whenModAbsent() {
        assertFalse(MineColoniesCompat.isFriendly(null),
                "isFriendly() must return false when MineColonies is not loaded");
    }

    // ── isFromPackage hierarchy walk ──────────────────────────────────────────

    /**
     * Test the package-scan helper directly via the exposed test-hook.
     * java.lang.Object does NOT start with "com.minecolonies" — must return false.
     * This verifies the hierarchy walk exits cleanly at Object.class.
     */
    @Test
    void isFromPackage_returnsFalse_forJavaLangObject() {
        boolean result = MineColoniesCompat.isFromPackageTestHook(
                new Object(), "com.minecolonies.core.entity.citizen");
        assertFalse(result,
                "isFromPackage must return false for java.lang.Object (no minecolonies prefix)");
    }
}

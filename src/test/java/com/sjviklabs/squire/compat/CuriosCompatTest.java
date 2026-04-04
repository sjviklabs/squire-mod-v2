package com.sjviklabs.squire.compat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CuriosCompat — covers the absent-mod guard path.
 *
 * Curios is a compileOnly dependency but NOT on the test runtime classpath.
 * ModList is not bootstrapped in JUnit (no NeoForge bootstrap), so
 * isActive() must return false and all handlers must return Optional.empty().
 *
 * Tests do NOT instantiate Minecraft entities — all entity-typed parameters
 * are passed as null since the absent-mod guard short-circuits before any
 * entity field is accessed.
 */
class CuriosCompatTest {

    /**
     * Reset the static cache between tests so each test starts with a clean state.
     * Uses reflection to reset the private static Boolean modPresent field.
     */
    @BeforeEach
    void resetModPresentCache() throws Exception {
        Field field = CuriosCompat.class.getDeclaredField("modPresent");
        field.setAccessible(true);
        field.set(null, null);
    }

    // ── Mod detection ─────────────────────────────────────────────────────────

    /**
     * Curios is not on the test runtime classpath.
     * ModList is not bootstrapped in JUnit, so isActive() must return false.
     */
    @Test
    void isActive_returnsFalse_whenModAbsent() {
        assertFalse(CuriosCompat.isActive(),
                "isActive() must return false when Curios is not loaded");
    }

    // ── Handler access guard ──────────────────────────────────────────────────

    /**
     * When Curios is absent, getHandler(null) must short-circuit via the mod guard
     * and return Optional.empty() without touching the entity parameter.
     */
    @Test
    void getHandler_returnsEmpty_whenModAbsent() {
        Optional<?> result = CuriosCompat.getHandler(null);
        assertTrue(result.isEmpty(),
                "getHandler() must return Optional.empty() when Curios is not loaded");
    }

    // ── Caching behavior ─────────────────────────────────────────────────────

    /**
     * The modPresent field is lazily initialized on first call. Calling isActive()
     * twice must return the same value (cached result, not requeried from ModList).
     *
     * Verifies via reflection that modPresent is set (non-null) after first call.
     */
    @Test
    void isActive_resultIsCached_afterFirstCall() throws Exception {
        // First call — initializes cache
        boolean first = CuriosCompat.isActive();

        // Inspect the cached field
        Field field = CuriosCompat.class.getDeclaredField("modPresent");
        field.setAccessible(true);
        Boolean cached = (Boolean) field.get(null);

        // Second call — must return the same value
        boolean second = CuriosCompat.isActive();

        assertNotNull(cached, "modPresent must be non-null after first isActive() call (cache initialized)");
        assertEquals(first, second, "isActive() must return the same value on repeated calls (cached)");
        assertEquals(first, cached, "cached modPresent must match the returned value");
    }
}

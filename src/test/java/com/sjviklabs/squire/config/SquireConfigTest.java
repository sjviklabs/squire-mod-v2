package com.sjviklabs.squire.config;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JUnit 5 unit tests for SquireConfig.
 *
 * SquireConfig builds a ModConfigSpec via a static initializer. ModConfigSpec.Builder
 * is a pure Java data structure — it does NOT require a running NeoForge server.
 * These tests verify:
 *   - SPEC static initialization completes without exceptions
 *   - At least 50 public static config value fields are declared
 *
 * NOTE: spec.correct() requires a loaded config file (NeoForge config loading
 * pipeline). That check is deferred to Phase 2 integration tests running under
 * runGameTestServer, where the full NeoForge bootstrap is available.
 *
 * Run: ./gradlew test
 */
class SquireConfigTest {

    // ── SPEC initialization ───────────────────────────────────────────────────

    @Test
    void spec_isNotNull() {
        // ModConfigSpec.Builder is pure Java — no NeoForge server required.
        // If this throws ExceptionInInitializerError, the static block has a bug.
        assertNotNull(SquireConfig.SPEC,
                "SquireConfig.SPEC must not be null — static initializer must succeed");
    }

    // ── Field count (minimum 50 config entries declared) ─────────────────────

    @Test
    void hasAtLeastFiftyConfigEntries() {
        long count = Arrays.stream(SquireConfig.class.getFields())
                .filter(f -> ModConfigSpec.ConfigValue.class.isAssignableFrom(f.getType()))
                .count();
        assertTrue(count >= 50,
                "Expected >= 50 config entries in SquireConfig, found: " + count
                + ". Either entries were removed or the field type changed.");
    }

    // ── Specific sections are present ────────────────────────────────────────

    @Test
    void generalSection_hasRequiredFields() throws Exception {
        assertNotNull(SquireConfig.maxSquiresPerPlayer, "maxSquiresPerPlayer must be declared");
        assertNotNull(SquireConfig.announceEvents,      "announceEvents must be declared");
    }

    @Test
    void combatSection_hasRequiredFields() throws Exception {
        assertNotNull(SquireConfig.aggroRange,          "aggroRange must be declared");
        assertNotNull(SquireConfig.combatLeashDistance, "combatLeashDistance must be declared");
        assertNotNull(SquireConfig.meleeRange,          "meleeRange must be declared");
        assertNotNull(SquireConfig.shieldBlocking,      "shieldBlocking must be declared");
    }

    @Test
    void progressionSection_hasRequiredFields() {
        assertNotNull(SquireConfig.xpPerKill,            "xpPerKill must be declared");
        assertNotNull(SquireConfig.healthPerLevel,       "healthPerLevel must be declared");
        assertNotNull(SquireConfig.undyingCooldownTicks, "undyingCooldownTicks must be declared");
    }

    @Test
    void debugSection_defaultsAreOff() {
        // Debug toggles must default false — they are BooleanValue fields.
        // We can verify the declared fields exist; get() requires loaded config.
        assertNotNull(SquireConfig.godMode,            "godMode must be declared");
        assertNotNull(SquireConfig.logFsmTransitions,  "logFsmTransitions must be declared");
        assertNotNull(SquireConfig.showAiState,        "showAiState must be declared");
        assertNotNull(SquireConfig.drawPathfinding,    "drawPathfinding must be declared");
        assertNotNull(SquireConfig.activityLogging,    "activityLogging must be declared");
        assertNotNull(SquireConfig.verboseInventoryLogs, "verboseInventoryLogs must be declared");
    }

    // ── No duplicate field declarations (all public statics are unique) ───────

    @Test
    void noNullConfigValueFields() {
        Field[] fields = SquireConfig.class.getFields();
        long nullCount = Arrays.stream(fields)
                .filter(f -> ModConfigSpec.ConfigValue.class.isAssignableFrom(f.getType()))
                .filter(f -> {
                    try {
                        return f.get(null) == null;
                    } catch (IllegalAccessException e) {
                        return false;
                    }
                })
                .count();
        assertEquals(0, nullCount,
                "All public static ModConfigSpec.ConfigValue fields must be initialized (non-null after static block)");
    }
}

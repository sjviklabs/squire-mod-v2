package com.sjviklabs.squire.compat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link MineColoniesCompat}.
 *
 * <p>Pre-v4.0.0 this file tested the "MineColonies is absent" guard paths — {@code isActive()
 * returns false}, {@code isColonist(null) returns false}, etc. Those scenarios no longer
 * occur: v4.0.0 made MineColonies a hard dependency, so production runs always have it
 * loaded and the test harness (see build.gradle's patchModsTomlForTests) has it on the
 * classpath too. The absent-mod test cases were deleted in v4.0.11.
 *
 * <p>What remains: the pure-Java helper {@link MineColoniesCompat#isFromPackageTestHook}
 * which tests hierarchy-walk behavior independent of mod presence.
 *
 * <p>Gap: we don't currently have MC-present tests that exercise isColonist/isRaider/isFriendly
 * against real MC entities. Those would require the MC entity registry to be populated,
 * which needs a larger test harness setup than the current one provides. Worth revisiting
 * if friendly-fire bugs surface.
 */
class MineColoniesCompatTest {

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

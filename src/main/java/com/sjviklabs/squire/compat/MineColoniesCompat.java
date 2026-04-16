package com.sjviklabs.squire.compat;

import net.minecraft.world.entity.Entity;
import net.neoforged.fml.ModList;

/**
 * Soft compatibility layer for MineColonies — zero API imports required.
 *
 * Detection uses a class hierarchy package-prefix walk (no MineColonies imports).
 * Every public method is guarded by isActive() — safe to call at any point after
 * FML mod loading completes. When MineColonies is absent all methods return false.
 *
 * Purpose: Prevents the squire's CombatHandler from targeting MineColonies colonists
 * as hostile mobs. MobCategory.MISC (set on SquireEntity in Phase 1) prevents
 * spawn-cap interference; this class prevents friendly-fire at target-selection level.
 *
 * Key links:
 *   isColonist → isFromPackage("com.minecolonies.core.entity.citizen")
 *   isRaider   → isFromPackage("com.minecolonies.core.entity.mobs")
 *   isFriendly → isColonist (citizens only — raiders should still be attacked)
 */
public final class MineColoniesCompat {

    private static final String MODID = "minecolonies";

    /** Lazy-cached mod presence — null until first isActive() call. */
    private static Boolean modPresent = null;

    private MineColoniesCompat() {}

    // ------------------------------------------------------------------
    // Mod detection
    // ------------------------------------------------------------------

    /**
     * Returns true if MineColonies is installed.
     * Result is cached after the first call. Safe to call after FML setup completes.
     */
    public static boolean isActive() {
        if (modPresent == null) {
            try {
                modPresent = ModList.get().isLoaded(MODID);
            } catch (IllegalStateException | NullPointerException e) {
                // ModList not available in JUnit / early bootstrap — treat as absent.
                // IllegalStateException: FML not yet initialized. NPE: ModList.get() returned null.
                modPresent = false;
            }
        }
        return modPresent;
    }

    // ------------------------------------------------------------------
    // Entity classification
    // ------------------------------------------------------------------

    /**
     * Returns true if the entity is a MineColonies citizen (colonist, guard, visitor, etc.).
     * MineColonies citizens extend AbstractEntityCitizen in com.minecolonies.core.entity.citizen.
     */
    public static boolean isColonist(Entity entity) {
        if (!isActive()) return false;
        return isFromPackage(entity, "com.minecolonies.core.entity.citizen");
    }

    /**
     * Returns true if the entity is a MineColonies raider mob (barbarian, pirate, etc.).
     * Raiders extend AbstractEntityMinecoloniesMob in com.minecolonies.core.entity.mobs.
     * Raiders should still be attacked — this is used for raid detection only.
     */
    public static boolean isRaider(Entity entity) {
        if (!isActive()) return false;
        return isFromPackage(entity, "com.minecolonies.core.entity.mobs");
    }

    // ------------------------------------------------------------------
    // Friendly-fire prevention
    // ------------------------------------------------------------------

    /**
     * Returns true if the squire should NOT attack this entity.
     * Citizens (colonists, guards, visitors) are friendly. Raiders are not.
     */
    public static boolean isFriendly(Entity entity) {
        // isColonist already contains the isActive() guard
        return isColonist(entity);
    }

    // ------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------

    /**
     * Walks the class hierarchy of obj checking if any class name starts with packagePrefix.
     * Catches all subclasses without enumerating specific class names.
     * Stops at Object.class (exclusive) — java.lang.Object is never a match.
     */
    private static boolean isFromPackage(Object obj, String packagePrefix) {
        Class<?> clazz = obj.getClass();
        while (clazz != null && clazz != Object.class) {
            if (clazz.getName().startsWith(packagePrefix)) return true;
            clazz = clazz.getSuperclass();
        }
        return false;
    }

    /**
     * Package-visible test hook that exposes isFromPackage for unit testing.
     * Not part of the public API — do not call from production code.
     */
    static boolean isFromPackageTestHook(Object obj, String packagePrefix) {
        return isFromPackage(obj, packagePrefix);
    }
}

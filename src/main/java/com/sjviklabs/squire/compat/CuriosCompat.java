package com.sjviklabs.squire.compat;

import net.minecraft.world.entity.LivingEntity;
import net.neoforged.fml.ModList;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;

import java.util.Optional;

/**
 * Soft compatibility accessor for Curios (Accessories) mod.
 *
 * All public methods are guarded by isActive() which lazily caches the ModList
 * query. The class itself may be loaded at any time — but because it only imports
 * Curios types in method signatures (not in static fields), the JVM will not attempt
 * to resolve those types until a method is actually called. By then the isActive()
 * guard has already short-circuited.
 *
 * Slot registration is handled entirely via datapack JSON files in the builtin
 * resources (data/squire/curios/slots/ and data/squire/curios/entities/).
 * No programmatic slot registration is required or supported by Curios 9.x.
 */
public final class CuriosCompat {

    private static final String MODID = "curios";

    /** Lazily initialized on first isActive() call. Null = not yet queried. */
    static Boolean modPresent = null;

    private CuriosCompat() {}

    /**
     * Returns true if Curios is loaded in this runtime.
     * Caches the result — ModList.get().isLoaded() is safe to call after mod loading
     * but repeated calls are unnecessary.
     */
    public static boolean isActive() {
        if (modPresent == null) {
            modPresent = ModList.get().isLoaded(MODID);
        }
        return modPresent;
    }

    /**
     * Returns the Curios inventory handler for the given living entity.
     * Returns Optional.empty() if Curios is not loaded or the entity has no curio slots.
     *
     * @param entity the living entity to query (may be null only when Curios is absent —
     *               the guard short-circuits before entity access)
     */
    public static Optional<ICuriosItemHandler> getHandler(LivingEntity entity) {
        if (!isActive()) return Optional.empty();
        return CuriosApi.getCuriosInventory(entity);
    }
}

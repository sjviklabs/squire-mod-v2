package com.sjviklabs.squire.progression;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.sjviklabs.squire.entity.SquireTier;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Loads progression tier definitions and ability unlock records from the datapack.
 *
 * Registered on AddServerReloadListenersEvent (see SquireServerEvents).
 * Data is available after the first server world load; all callers must handle empty Optionals
 * to remain safe in unit-test / headless contexts where no world is loaded.
 *
 * File layout under data/squire/squire/progression/:
 *   servant.json, apprentice.json, squire_tier.json, knight.json, champion.json  -> tier defs
 *   abilities.json                                                                -> ability array
 */
public class ProgressionDataLoader extends SimpleJsonResourceReloadListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProgressionDataLoader.class);
    private static final Gson GSON = new GsonBuilder().create();

    // Static maps — empty until first world load; callers must use Optional return values.
    private static final Map<SquireTier, TierDefinition> TIER_DATA = new EnumMap<>(SquireTier.class);
    private static final Map<String, AbilityDefinition> ABILITY_DATA = new LinkedHashMap<>();

    public ProgressionDataLoader() {
        super(GSON, "squire/progression");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> data, ResourceManager manager, ProfilerFiller profiler) {
        TIER_DATA.clear();
        ABILITY_DATA.clear();

        for (Map.Entry<ResourceLocation, JsonElement> entry : data.entrySet()) {
            // getPath() returns "squire/progression/servant", strip prefix to get file stem
            String path = entry.getKey().getPath();
            String name = path.substring(path.lastIndexOf('/') + 1);
            JsonElement element = entry.getValue();

            if (name.equals("abilities")) {
                loadAbilities(element);
            } else {
                loadTier(name, element);
            }
        }

        LOGGER.info("[SquireMod] Progression loaded: {} tier(s), {} abilit(ies)", TIER_DATA.size(), ABILITY_DATA.size());
    }

    private void loadTier(String fileName, JsonElement element) {
        try {
            JsonObject obj = element.getAsJsonObject();
            String tierStr = obj.get("tier").getAsString().toUpperCase();
            // squire_tier.json has "tier": "squire" -> toUpperCase() -> "SQUIRE" -> SquireTier.SQUIRE
            SquireTier squireTier = SquireTier.valueOf(tierStr);

            TierDefinition def = new TierDefinition(
                obj.get("tier").getAsString(),
                obj.get("min_level").getAsInt(),
                obj.get("backpack_slots").getAsInt(),
                obj.get("max_health").getAsDouble(),
                obj.get("attack_damage").getAsDouble(),
                obj.get("movement_speed").getAsDouble(),
                obj.get("xp_to_next").getAsInt(),
                obj.has("description") ? obj.get("description").getAsString() : ""
            );
            TIER_DATA.put(squireTier, def);
        } catch (JsonParseException | ClassCastException | IllegalStateException | NumberFormatException | NullPointerException e) {
            LOGGER.error("[SquireMod] Failed to load tier file '{}': malformed JSON or missing field: {}", fileName, e.getMessage());
        }
    }

    private void loadAbilities(JsonElement element) {
        try {
            JsonArray arr = element.getAsJsonArray();
            for (JsonElement item : arr) {
                JsonObject obj = item.getAsJsonObject();
                String id = obj.get("id").getAsString();
                AbilityDefinition def = new AbilityDefinition(
                    id,
                    obj.get("unlock_tier").getAsString(),
                    obj.has("description") ? obj.get("description").getAsString() : ""
                );
                ABILITY_DATA.put(id, def);
            }
        } catch (JsonParseException | ClassCastException | IllegalStateException | NumberFormatException | NullPointerException e) {
            LOGGER.error("[SquireMod] Failed to load abilities.json: malformed JSON or missing field: {}", e.getMessage());
        }
    }

    /**
     * Returns the loaded tier definition, or empty if the loader hasn't run yet.
     * ProgressionHandler should fall back to SquireTier enum defaults when empty.
     */
    public static Optional<TierDefinition> getTierDefinition(SquireTier tier) {
        return Optional.ofNullable(TIER_DATA.get(tier));
    }

    /** Returns all loaded tier definitions. Empty map before first world load. */
    public static Map<SquireTier, TierDefinition> getAllTiers() {
        return Collections.unmodifiableMap(TIER_DATA);
    }

    /** Returns ability definition by ID string, or empty if not found or loader hasn't run. */
    public static Optional<AbilityDefinition> getAbilityDefinition(String id) {
        return Optional.ofNullable(ABILITY_DATA.get(id));
    }

    /** Returns all loaded ability definitions. Empty collection before first world load. */
    public static Collection<AbilityDefinition> getAbilities() {
        return Collections.unmodifiableCollection(ABILITY_DATA.values());
    }
}

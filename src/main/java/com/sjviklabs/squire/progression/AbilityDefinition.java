package com.sjviklabs.squire.progression;

/**
 * Immutable snapshot of an ability unlock as loaded from abilities.json.
 * id matches the string used in behavior gate checks (e.g. "UNDYING", "COMBAT").
 * unlockTier is the tier name string (e.g. "champion") matching the tier JSON "tier" field.
 */
public record AbilityDefinition(
    String id,
    String unlockTier,
    String description
) {}

package com.sjviklabs.squire.progression;

/**
 * Immutable snapshot of a tier's stats as loaded from the datapack JSON.
 * All stat fields mirror the JSON schema in data/squire/squire/progression/*.json.
 */
public record TierDefinition(
    String tier,
    int minLevel,
    int backpackSlots,
    double maxHealth,
    double attackDamage,
    double movementSpeed,
    int xpToNext,
    String description
) {}

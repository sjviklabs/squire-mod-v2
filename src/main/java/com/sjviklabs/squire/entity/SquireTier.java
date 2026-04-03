package com.sjviklabs.squire.entity;

/**
 * Progression tiers that gate backpack slot counts and future abilities.
 * Equipment slots (4 armor + 1 weapon + 1 offhand = 6) are fixed and not in this enum.
 */
public enum SquireTier {
    SERVANT(0, 9),
    APPRENTICE(5, 18),
    SQUIRE(10, 27),
    KNIGHT(20, 32),
    CHAMPION(30, 36);

    private final int minLevel;
    private final int backpackSlots;

    SquireTier(int minLevel, int backpackSlots) {
        this.minLevel = minLevel;
        this.backpackSlots = backpackSlots;
    }

    public int getMinLevel() { return minLevel; }
    public int getBackpackSlots() { return backpackSlots; }

    /**
     * Returns the highest tier the squire qualifies for at the given level.
     * Always returns at least SERVANT.
     */
    public static SquireTier fromLevel(int level) {
        SquireTier result = SERVANT;
        for (SquireTier tier : values()) {
            if (level >= tier.minLevel) result = tier;
        }
        return result;
    }
}

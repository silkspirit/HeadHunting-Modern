package com.headhunting.boosters;

/**
 * Types of boosters available
 */
public enum BoosterType {
    XP,      // HeadHunting XP boost
    SELL,    // Sell price boost (for heads/fish)
    FISHING; // Fishing catch rate boost (faster bites)

    /**
     * Get a human-readable display name for this booster type
     */
    public String getDisplayName() {
        switch (this) {
            case XP:      return "XP";
            case SELL:    return "Sell";
            case FISHING: return "Fishing";
            default:      return name();
        }
    }

    /**
     * Parse a booster type from a string (case-insensitive)
     * @return the BoosterType, or null if invalid
     */
    public static BoosterType fromString(String str) {
        if (str == null) return null;
        switch (str.toLowerCase()) {
            case "xp":      return XP;
            case "sell":    return SELL;
            case "fishing": return FISHING;
            default:
                try {
                    return valueOf(str.toUpperCase());
                } catch (IllegalArgumentException e) {
                    return null;
                }
        }
    }
}

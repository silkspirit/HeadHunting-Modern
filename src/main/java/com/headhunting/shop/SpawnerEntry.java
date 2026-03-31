package com.headhunting.shop;

/**
 * Represents a single spawner available for purchase in the Spawner Shop.
 */
public class SpawnerEntry {

    private final String key;
    private final String mobType;
    private final String displayName;
    private final double price;
    private final int requiredLevel;
    private final int slot;

    public SpawnerEntry(String key, String mobType, String displayName,
                        double price, int requiredLevel, int slot) {
        this.key = key;
        this.mobType = mobType;
        this.displayName = displayName;
        this.price = price;
        this.requiredLevel = requiredLevel;
        this.slot = slot;
    }

    /** Config key (e.g. "pig", "cave_spider") */
    public String getKey() { return key; }

    /** Mob type string passed to spawner-stacking (e.g. "PIG", "CAVE_SPIDER") */
    public String getMobType() { return mobType; }

    /** Display name for the GUI item (supports &-color codes) */
    public String getDisplayName() { return displayName; }

    /** Economy price */
    public double getPrice() { return price; }

    /** Minimum HeadHunting level required */
    public int getRequiredLevel() { return requiredLevel; }

    /**
     * GUI slot (0-53). -1 means auto-assign when the GUI is built.
     */
    public int getSlot() { return slot; }
}

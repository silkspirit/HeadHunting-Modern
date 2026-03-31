package com.headhunting.data;

import org.bukkit.entity.EntityType;

/**
 * Represents configuration for a mob head type
 */
public class HeadConfig {
    
    private final String headKey;
    private final EntityType entityType;
    private final boolean enabled;
    private final double dropChance;
    private final int xpValue;
    private final double sellPrice;
    private final String displayName;
    private final String texture;
    
    public HeadConfig(EntityType entityType, boolean enabled, double dropChance, 
                      int xpValue, double sellPrice, String displayName, String texture) {
        this.headKey = entityType != null ? entityType.name() : "UNKNOWN";
        this.entityType = entityType;
        this.enabled = enabled;
        this.dropChance = dropChance;
        this.xpValue = xpValue;
        this.sellPrice = sellPrice;
        this.displayName = displayName;
        this.texture = texture;
    }
    
    public HeadConfig(String headKey, EntityType entityType, boolean enabled, double dropChance,
                      int xpValue, double sellPrice, String displayName, String texture) {
        this.headKey = headKey;
        this.entityType = entityType;
        this.enabled = enabled;
        this.dropChance = dropChance;
        this.xpValue = xpValue;
        this.sellPrice = sellPrice;
        this.displayName = displayName;
        this.texture = texture;
    }
    
    public String getHeadKey() {
        return headKey;
    }
    
    public EntityType getEntityType() {
        return entityType;
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public double getDropChance() {
        return dropChance;
    }
    
    public int getXpValue() {
        return xpValue;
    }
    
    public double getSellPrice() {
        return sellPrice;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public String getTexture() {
        return texture;
    }
}

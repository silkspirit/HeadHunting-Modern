package com.headhunting.data;

import org.bukkit.entity.EntityType;
import java.util.List;

/**
 * Represents configuration for a player level
 */
public class LevelConfig {
    
    private final int level;
    private final int xpRequired;
    private final double moneyCost;
    private final EntityType mobType;
    private final String mobDisplayName;
    private final String spawnerUnlock;
    private final List<String> rewards;
    
    public LevelConfig(int level, int xpRequired, double moneyCost, 
                       EntityType mobType, String mobDisplayName, String spawnerUnlock, List<String> rewards) {
        this.level = level;
        this.xpRequired = xpRequired;
        this.moneyCost = moneyCost;
        this.mobType = mobType;
        this.mobDisplayName = mobDisplayName;
        this.spawnerUnlock = spawnerUnlock;
        this.rewards = rewards;
    }
    
    public int getLevel() {
        return level;
    }
    
    public int getXpRequired() {
        return xpRequired;
    }
    
    public double getMoneyCost() {
        return moneyCost;
    }
    
    public EntityType getMobType() {
        return mobType;
    }
    
    public String getMobDisplayName() {
        return mobDisplayName;
    }
    
    public String getSpawnerUnlock() {
        return spawnerUnlock;
    }
    
    public List<String> getRewards() {
        return rewards;
    }
}

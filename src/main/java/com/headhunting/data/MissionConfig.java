package com.headhunting.data;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a divine mission configuration
 */
public class MissionConfig {
    
    private final String id;
    private final String displayName;
    private final String description;
    private final String unlocksMask;
    private final List<MissionRequirement> requirements;
    private final List<String> rewardCommands;
    
    // Icon customization
    private final Material iconMaterial;
    private final int iconData;
    private final String iconSkull;
    private final boolean iconGlow;
    private final int difficulty;
    
    public MissionConfig(String id, ConfigurationSection section) {
        this.id = id;
        this.displayName = section.getString("display-name", id);
        this.description = section.getString("description", "Complete this mission to unlock a divine mask!");
        this.unlocksMask = section.getString("unlocks-mask", "");
        this.requirements = new ArrayList<>();
        this.rewardCommands = section.getStringList("reward-commands");
        
        // Parse icon settings
        String materialStr = section.getString("icon-material", null);
        if (materialStr != null) {
            Material mat = Material.getMaterial(materialStr.toUpperCase());
            this.iconMaterial = mat;
        } else {
            this.iconMaterial = null;
        }
        this.iconData = section.getInt("icon-data", 0);
        this.iconSkull = section.getString("icon-skull", null);
        this.iconGlow = section.getBoolean("icon-glow", false);
        this.difficulty = section.getInt("difficulty", 5);
        
        // Parse requirements
        ConfigurationSection reqSection = section.getConfigurationSection("requirements");
        if (reqSection != null) {
            for (String reqKey : reqSection.getKeys(false)) {
                ConfigurationSection req = reqSection.getConfigurationSection(reqKey);
                if (req != null) {
                    requirements.add(new MissionRequirement(reqKey, req));
                }
            }
        }
    }
    
    public String getId() {
        return id;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public String getDescription() {
        return description;
    }
    
    public String getUnlocksMask() {
        return unlocksMask;
    }
    
    public List<MissionRequirement> getRequirements() {
        return requirements;
    }
    
    public List<String> getRewardCommands() {
        return rewardCommands;
    }
    
    public Material getIconMaterial() {
        return iconMaterial;
    }
    
    public int getIconData() {
        return iconData;
    }
    
    public String getIconSkull() {
        return iconSkull;
    }
    
    public boolean hasIconGlow() {
        return iconGlow;
    }
    
    public int getDifficulty() {
        return difficulty;
    }
    
    /**
     * Represents a single requirement within a mission
     */
    public static class MissionRequirement {
        
        private final String id;
        private final MissionType type;
        private final int amount;
        private final String target; // For specific mobs, masks, etc.
        private final String description;
        
        public MissionRequirement(String id, ConfigurationSection section) {
            this.id = id;
            this.type = MissionType.fromString(section.getString("type", "KILL_MOBS"));
            this.amount = section.getInt("amount", 100);
            this.target = section.getString("target", "");
            this.description = section.getString("description", type.getDefaultDescription(amount, target));
        }
        
        public String getId() {
            return id;
        }
        
        public MissionType getType() {
            return type;
        }
        
        public int getAmount() {
            return amount;
        }
        
        public String getTarget() {
            return target;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    /**
     * Types of mission requirements
     */
    public enum MissionType {
        KILL_MOBS("Kill %d mobs"),
        KILL_SPECIFIC_MOB("Kill %d %s"),
        KILL_PLAYERS("Kill %d players"),
        KILL_DARKZONE_MOBS("Kill %d mobs in the Darkzone"),
        FISH_CATCHES("Catch %d fish in warzone"),
        BREAK_SPAWNERS("Break %d spawners"),
        DEPOSIT_HEADS("Deposit %d heads"),
        DEPOSIT_SPECIFIC_HEAD("Deposit %d %s heads"),
        REACH_LEVEL("Reach level %d"),
        OWN_MASK("Own the %s mask"),
        OWN_MASK_LEVEL("Own %s mask at level %d"),
        OWN_MASK_COUNT("Own %d different masks"),
        CONSUME_HEADS("Consume %d heads"),
        EARN_MONEY("Earn $%d from selling heads"),
        DIE_WITH_MASK("Die %d times while wearing a mask"),
        KILL_WITH_GAPPLE("Kill %d players while gapple is active"),
        CUSTOM("Complete: %s");
        
        private final String defaultFormat;
        
        MissionType(String defaultFormat) {
            this.defaultFormat = defaultFormat;
        }
        
        public String getDefaultDescription(int amount, String target) {
            // Handle different format patterns
            switch (this) {
                case OWN_MASK:
                case CUSTOM:
                    // These use %s only
                    return String.format(defaultFormat, target != null ? target : "unknown");
                case OWN_MASK_LEVEL:
                    // This uses %s then %d
                    return String.format(defaultFormat, target != null ? target : "unknown", amount);
                default:
                    // Most use %d first, optionally %s second
                    if (target != null && !target.isEmpty()) {
                        return String.format(defaultFormat, amount, target);
                    }
                    // For formats with only %d
                    return String.format(defaultFormat.replaceAll(" %s", ""), amount);
            }
        }
        
        public static MissionType fromString(String str) {
            try {
                return valueOf(str.toUpperCase());
            } catch (Exception e) {
                return CUSTOM;
            }
        }
    }
}

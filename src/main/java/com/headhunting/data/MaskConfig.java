package com.headhunting.data;

import org.bukkit.configuration.ConfigurationSection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents configuration for a mask
 */
public class MaskConfig {
    
    private final String id;
    private final String displayName;
    private final MaskTier tier;
    private final int associatedLevel;
    private final String texture;
    private final int craftCost;
    private final List<Integer> levelCosts;
    private final String mission;
    private final boolean essenceOnly;
    private final Map<Integer, List<MaskAbility>> abilities;
    
    public MaskConfig(String id, String displayName, String tierStr, int associatedLevel,
                      String texture, int craftCost, List<Integer> levelCosts, 
                      String mission, ConfigurationSection abilitiesSection) {
        this(id, displayName, tierStr, associatedLevel, texture, craftCost, levelCosts, mission, false, abilitiesSection);
    }
    
    public MaskConfig(String id, String displayName, String tierStr, int associatedLevel,
                      String texture, int craftCost, List<Integer> levelCosts, 
                      String mission, boolean essenceOnly, ConfigurationSection abilitiesSection) {
        this.id = id;
        this.displayName = displayName;
        this.tier = MaskTier.fromString(tierStr);
        this.associatedLevel = associatedLevel;
        this.texture = texture;
        this.craftCost = craftCost;
        this.levelCosts = levelCosts != null ? levelCosts : new ArrayList<>();
        this.mission = mission;
        this.essenceOnly = essenceOnly;
        this.abilities = parseAbilities(abilitiesSection);
    }
    
    private Map<Integer, List<MaskAbility>> parseAbilities(ConfigurationSection section) {
        Map<Integer, List<MaskAbility>> map = new HashMap<>();
        if (section == null) return map;
        
        for (String levelKey : section.getKeys(false)) {
            try {
                int level = Integer.parseInt(levelKey);
                List<MaskAbility> levelAbilities = new ArrayList<>();
                
                List<?> abilityList = section.getList(levelKey);
                if (abilityList != null) {
                    for (Object obj : abilityList) {
                        if (obj instanceof Map) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> abilityMap = (Map<String, Object>) obj;
                            levelAbilities.add(new MaskAbility(abilityMap));
                        }
                    }
                }
                
                map.put(level, levelAbilities);
            } catch (NumberFormatException ignored) {}
        }
        
        return map;
    }
    
    public String getId() {
        return id;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public MaskTier getTier() {
        return tier;
    }
    
    public boolean isDivine() {
        return tier == MaskTier.DIVINE;
    }
    
    public int getAssociatedLevel() {
        return associatedLevel;
    }
    
    public String getTexture() {
        return texture;
    }
    
    public int getCraftCost() {
        return craftCost;
    }
    
    public List<Integer> getLevelCosts() {
        return levelCosts;
    }
    
    public int getLevelCost(int level) {
        if (level < 2 || level > 5) return 0;
        int index = level - 2;
        if (index >= levelCosts.size()) return 0;
        return levelCosts.get(index);
    }
    
    public String getMission() {
        return mission;
    }
    
    public boolean requiresMission() {
        return mission != null && !mission.isEmpty();
    }
    
    public boolean isEssenceOnly() {
        return essenceOnly;
    }
    
    public Map<Integer, List<MaskAbility>> getAbilities() {
        return abilities;
    }
    
    public List<MaskAbility> getAbilitiesForLevel(int level) {
        return abilities.getOrDefault(level, new ArrayList<>());
    }
    
    /**
     * Get all abilities up to and including the given level
     */
    public List<MaskAbility> getAllAbilitiesUpToLevel(int level) {
        List<MaskAbility> allAbilities = new ArrayList<>();
        for (int i = 1; i <= level; i++) {
            allAbilities.addAll(getAbilitiesForLevel(i));
        }
        return allAbilities;
    }
    
    /**
     * Get all ability descriptions for lore display
     */
    public List<String> getAllAbilityDescriptions() {
        List<String> descriptions = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            List<MaskAbility> levelAbilities = getAbilitiesForLevel(i);
            for (MaskAbility ability : levelAbilities) {
                if (!descriptions.contains(ability.getDescription())) {
                    descriptions.add(ability.getDescription());
                }
            }
        }
        return descriptions;
    }
    
    public enum MaskTier {
        RANKUP,
        DIVINE;
        
        public static MaskTier fromString(String str) {
            try {
                return valueOf(str.toUpperCase());
            } catch (Exception e) {
                return RANKUP;
            }
        }
    }
}

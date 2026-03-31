package com.headhunting.data;

import org.bukkit.potion.PotionEffectType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Represents a single mask ability
 */
public class MaskAbility {
    
    private final String description;
    private final AbilityType type;
    private final EffectType effect;
    private final String specialId;
    private final PotionEffectType potionType;
    private final int amplifier;
    private final int duration;
    private final double chance;
    private final double value;
    private final boolean requiresBow;
    private final String mobType;
    private final int count;
    private final List<String> debuffs;
    private final String damageType;
    
    @SuppressWarnings("unchecked")
    public MaskAbility(Map<String, Object> data) {
        this.description = getString(data, "description", "Unknown ability");
        this.type = AbilityType.fromString(getString(data, "type", "PASSIVE"));
        this.effect = EffectType.fromString(getString(data, "effect", "SPECIAL"));
        this.specialId = getString(data, "special-id", null);
        this.potionType = parsePotionType(getString(data, "potion-type", null));
        this.amplifier = getInt(data, "amplifier", 0);
        this.duration = getInt(data, "duration", 100);
        this.chance = getDouble(data, "chance", 100.0);
        this.value = getDouble(data, "value", 0.0);
        this.requiresBow = getBoolean(data, "requires-bow", false);
        this.mobType = getString(data, "mob-type", null);
        this.count = getInt(data, "count", 1);
        this.damageType = getString(data, "damage-type", null);
        
        // Parse debuffs list
        Object debuffsObj = data.get("debuffs");
        if (debuffsObj instanceof List) {
            this.debuffs = (List<String>) debuffsObj;
        } else {
            this.debuffs = new ArrayList<>();
        }
    }
    
    private String getString(Map<String, Object> data, String key, String def) {
        Object val = data.get(key);
        return val != null ? val.toString() : def;
    }
    
    private int getInt(Map<String, Object> data, String key, int def) {
        Object val = data.get(key);
        if (val instanceof Number) {
            return ((Number) val).intValue();
        }
        return def;
    }
    
    private double getDouble(Map<String, Object> data, String key, double def) {
        Object val = data.get(key);
        if (val instanceof Number) {
            return ((Number) val).doubleValue();
        }
        return def;
    }
    
    private boolean getBoolean(Map<String, Object> data, String key, boolean def) {
        Object val = data.get(key);
        if (val instanceof Boolean) {
            return (Boolean) val;
        }
        return def;
    }
    
    private PotionEffectType parsePotionType(String name) {
        if (name == null) return null;
        try {
            return PotionEffectType.getByName(name.toUpperCase());
        } catch (Exception e) {
            return null;
        }
    }
    
    // Getters
    public String getDescription() {
        return description;
    }
    
    public AbilityType getType() {
        return type;
    }
    
    public EffectType getEffect() {
        return effect;
    }
    
    public String getSpecialId() {
        return specialId;
    }
    
    public PotionEffectType getPotionType() {
        return potionType;
    }
    
    public int getAmplifier() {
        return amplifier;
    }
    
    public int getDuration() {
        return duration;
    }
    
    public boolean isPermanent() {
        return duration == -1;
    }
    
    public double getChance() {
        return chance;
    }
    
    public double getValue() {
        return value;
    }
    
    public boolean requiresBow() {
        return requiresBow;
    }
    
    public String getMobType() {
        return mobType;
    }
    
    public int getCount() {
        return count;
    }
    
    public List<String> getDebuffs() {
        return debuffs;
    }
    
    public String getDamageType() {
        return damageType;
    }
    
    /**
     * Check if this ability should proc based on its chance
     */
    public boolean shouldProc() {
        if (chance >= 100.0) return true;
        return Math.random() * 100 < chance;
    }
    
    public enum AbilityType {
        PASSIVE,
        ON_HIT,
        ON_DAMAGED,
        ON_KILL,
        ON_MINE,
        ON_FISH;
        
        public static AbilityType fromString(String str) {
            try {
                return valueOf(str.toUpperCase());
            } catch (Exception e) {
                return PASSIVE;
            }
        }
    }
    
    public enum EffectType {
        POTION,
        DAMAGE_MULTIPLIER,
        DAMAGE_REDUCTION,
        HEALTH_BOOST,
        SPAWN_MINIONS,
        BONUS_DROPS,
        BONUS_XP,
        SPECIAL;
        
        public static EffectType fromString(String str) {
            try {
                return valueOf(str.toUpperCase());
            } catch (Exception e) {
                return SPECIAL;
            }
        }
    }
}

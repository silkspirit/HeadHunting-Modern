package com.headhunting.managers;

import com.headhunting.HeadHunting;
import com.headhunting.data.HeadConfig;
import com.headhunting.data.LevelConfig;
import com.headhunting.data.MaskConfig;
import com.headhunting.utils.MessageUtil;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ConfigManager {

    private final HeadHunting plugin;
    
    private FileConfiguration config;
    private FileConfiguration headsConfig;
    private FileConfiguration levelsConfig;
    private FileConfiguration masksConfig;
    
    private final Map<EntityType, HeadConfig> headConfigs = new HashMap<>();
    private final Map<String, HeadConfig> headConfigsByKey = new HashMap<>();
    private final Map<Integer, LevelConfig> levelConfigs = new HashMap<>();
    private final Map<String, MaskConfig> maskConfigs = new HashMap<>();
    
    private int maxLevel;
    
    // Ability balance settings
    private double chanceMultiplier = 1.0;
    private int amplifierOffset = 0;
    private int maxAmplifier = -1;
    private double durationMultiplier = 1.0;
    private double damageMultiplier = 1.0;
    private double valueMultiplier = 1.0;
    
    public ConfigManager(HeadHunting plugin) {
        this.plugin = plugin;
        loadConfigs();
    }
    
    public void loadConfigs() {
        // Save defaults
        plugin.saveDefaultConfig();
        saveDefaultResource("heads.yml");
        saveDefaultResource("levels.yml");
        saveDefaultResource("masks.yml");
        
        // Load configs
        config = plugin.getConfig();
        headsConfig = loadConfig("heads.yml");
        levelsConfig = loadConfig("levels.yml");
        masksConfig = loadConfig("masks.yml");
        
        // Parse configs
        loadHeadConfigs();
        loadLevelConfigs();
        loadMaskConfigs();
        loadBalanceSettings();
    }
    
    private void loadBalanceSettings() {
        chanceMultiplier = config.getDouble("ability-balance.chance-multiplier", 1.0);
        amplifierOffset = config.getInt("ability-balance.amplifier-offset", 0);
        maxAmplifier = config.getInt("ability-balance.max-amplifier", -1);
        durationMultiplier = config.getDouble("ability-balance.duration-multiplier", 1.0);
        damageMultiplier = config.getDouble("ability-balance.damage-multiplier", 1.0);
        valueMultiplier = config.getDouble("ability-balance.value-multiplier", 1.0);
        
        if (config.getBoolean("general.debug", false)) {
            plugin.getLogger().info("[Balance] Chance multiplier: " + chanceMultiplier);
            plugin.getLogger().info("[Balance] Amplifier offset: " + amplifierOffset);
            plugin.getLogger().info("[Balance] Max amplifier: " + maxAmplifier);
        }
    }
    
    public void reload() {
        plugin.reloadConfig();
        config = plugin.getConfig();
        headsConfig = loadConfig("heads.yml");
        levelsConfig = loadConfig("levels.yml");
        masksConfig = loadConfig("masks.yml");
        
        headConfigs.clear();
        headConfigsByKey.clear();
        levelConfigs.clear();
        maskConfigs.clear();
        
        loadHeadConfigs();
        loadLevelConfigs();
        loadMaskConfigs();
        loadBalanceSettings();
    }
    
    private void saveDefaultResource(String name) {
        File file = new File(plugin.getDataFolder(), name);
        if (!file.exists()) {
            plugin.saveResource(name, false);
        }
    }
    
    private FileConfiguration loadConfig(String name) {
        File file = new File(plugin.getDataFolder(), name);
        return YamlConfiguration.loadConfiguration(file);
    }
    
    private void loadHeadConfigs() {
        ConfigurationSection headsSection = headsConfig.getConfigurationSection("heads");
        if (headsSection == null) return;
        
        for (String key : headsSection.getKeys(false)) {
            ConfigurationSection headSection = headsSection.getConfigurationSection(key);
            if (headSection == null) continue;
            
            String upperKey = key.toUpperCase();
            EntityType entityType = null;
            try {
                entityType = EntityType.valueOf(upperKey);
            } catch (IllegalArgumentException ignored) {
                // Sub-type key (e.g. WITHER_SKELETON, ELDER_GUARDIAN) — not a valid EntityType in 1.8
            }
            
            HeadConfig headConfig = new HeadConfig(
                upperKey,
                entityType,
                headSection.getBoolean("enabled", true),
                headSection.getDouble("drop-chance", 100.0),
                headSection.getInt("xp-value", 50),
                headSection.getDouble("sell-price", 1.0),
                headSection.getString("display-name", "&f" + key + " Head"),
                headSection.getString("texture", "")
            );
            
            // Store by string key (always)
            headConfigsByKey.put(upperKey, headConfig);
            
            // Also store by EntityType if it's a valid one (for backward compat)
            if (entityType != null) {
                headConfigs.put(entityType, headConfig);
            }
        }
    }
    
    private void loadLevelConfigs() {
        ConfigurationSection levelsSection = levelsConfig.getConfigurationSection("levels");
        if (levelsSection == null) return;
        
        maxLevel = levelsConfig.getInt("max-level", 14);
        
        for (String key : levelsSection.getKeys(false)) {
            ConfigurationSection levelSection = levelsSection.getConfigurationSection(key);
            if (levelSection == null) continue;
            
            try {
                int level = Integer.parseInt(key);
                EntityType mobType = null;
                
                String mobTypeStr = levelSection.getString("mob-type");
                if (mobTypeStr != null) {
                    try {
                        mobType = EntityType.valueOf(mobTypeStr.toUpperCase());
                    } catch (IllegalArgumentException ignored) {}
                }
                
                // Get display name override, or null to use mob type name
                String mobDisplayName = levelSection.getString("mob-display", null);
                
                LevelConfig levelConfig = new LevelConfig(
                    level,
                    levelSection.getInt("xp-required", 0),
                    levelSection.getDouble("money-cost", 0),
                    mobType,
                    mobDisplayName,
                    levelSection.getString("spawner-unlock", ""),
                    levelSection.getStringList("rewards")
                );
                
                levelConfigs.put(level, levelConfig);
            } catch (NumberFormatException e) {
                plugin.getLogger().warning("Invalid level in levels.yml: " + key);
            }
        }
    }
    
    private void loadMaskConfigs() {
        ConfigurationSection masksSection = masksConfig.getConfigurationSection("masks");
        if (masksSection == null) return;
        
        for (String key : masksSection.getKeys(false)) {
            ConfigurationSection maskSection = masksSection.getConfigurationSection(key);
            if (maskSection == null) continue;
            
            MaskConfig maskConfig = new MaskConfig(
                key.toLowerCase(),
                maskSection.getString("display-name", key),
                maskSection.getString("tier", "RANKUP"),
                maskSection.getInt("associated-level", 0),
                maskSection.getString("texture", ""),
                maskSection.getInt("craft-cost", 100),
                maskSection.getIntegerList("level-costs"),
                maskSection.getString("mission", null),
                maskSection.getBoolean("essence-only", false),
                maskSection.getConfigurationSection("abilities")
            );
            
            maskConfigs.put(key.toLowerCase(), maskConfig);
        }
    }
    
    // Getters for config values
    public boolean isDebugEnabled() {
        return config.getBoolean("general.debug", false);
    }
    
    public String getStorageType() {
        return config.getString("general.storage-type", "YAML");
    }
    
    public int getAutoSaveInterval() {
        return config.getInt("general.auto-save-interval", 5);
    }
    
    public double getDropRateMultiplier() {
        return config.getDouble("head-drops.drop-rate-multiplier", 1.0);
    }
    
    public boolean isStackedMobsEnabled() {
        return config.getBoolean("head-drops.stacked-mobs.enabled", true);
    }
    
    public int getMaxHeadsPerKill() {
        return config.getInt("head-drops.stacked-mobs.max-heads-per-kill", 64);
    }
    
    public boolean isBroadcastLevelUp() {
        return config.getBoolean("levels.broadcast-levelup", true);
    }
    
    public String getBroadcastMessage() {
        return config.getString("levels.broadcast-message", "&a{player} has reached Level {level}!");
    }
    
    public boolean isMaskTradeable() {
        return config.getBoolean("masks.tradeable", true);
    }
    
    public boolean isMaskDropOnDeath() {
        return config.getBoolean("masks.drop-on-death", true);
    }
    
    public int getSpiritEssenceXP() {
        return config.getInt("spirit-essence.xp-per-essence", config.getInt("omega-essence.xp-per-essence", 100));
    }
    
    public String getCurrencySymbol() {
        return config.getString("economy.currency-symbol", "$");
    }
    
    public boolean isShiftClickSell() {
        return config.getBoolean("economy.shift-click-sell", true);
    }
    
    public double getWarzoneDropMultiplier() {
        return config.getDouble("integrations.factions.warzone-drop-multiplier", 1.5);
    }
    
    public boolean isWorldAllowedForHeadDrops(String worldName) {
        if (!config.getBoolean("head-drops.world-whitelist.enabled", false)) {
            return true;
        }
        List<String> worlds = config.getStringList("head-drops.world-whitelist.worlds");
        for (String w : worlds) {
            if (w.equalsIgnoreCase(worldName)) return true;
        }
        return false;
    }
    
    public String getDarkzoneWorld() {
        return config.getString("integrations.factions.darkzone-world", "");
    }
    
    public boolean isSuppressVanillaDrops() {
        return config.getBoolean("integrations.factions.suppress-vanilla-drops", false);
    }
    
    public String getMessage(String key) {
        String prefix = config.getString("messages.prefix", "&8[&6HeadHunting&8] ");
        String message = config.getString("messages." + key, "Message not found: " + key);
        return MessageUtil.color(prefix + message);
    }
    
    public String getRawMessage(String key) {
        return MessageUtil.color(config.getString("messages." + key, "Message not found: " + key));
    }
    
    // Config object getters
    public Map<EntityType, HeadConfig> getHeadConfigs() {
        return headConfigs;
    }
    
    public HeadConfig getHeadConfig(EntityType type) {
        return headConfigs.get(type);
    }
    
    public HeadConfig getHeadConfig(String key) {
        return headConfigsByKey.get(key.toUpperCase());
    }
    
    public Map<String, HeadConfig> getHeadConfigsByKey() {
        return headConfigsByKey;
    }
    
    public Map<Integer, LevelConfig> getLevelConfigs() {
        return levelConfigs;
    }
    
    public LevelConfig getLevelConfig(int level) {
        return levelConfigs.get(level);
    }
    
    public int getMaxLevel() {
        return maxLevel;
    }
    
    public Map<String, MaskConfig> getMaskConfigs() {
        return maskConfigs;
    }
    
    public MaskConfig getMaskConfig(String maskId) {
        return maskConfigs.get(maskId.toLowerCase());
    }
    
    // Mask display settings
    public String getMaskLevelColor(int level) {
        return masksConfig.getString("display.level-colors." + level, "&7");
    }
    
    public String getMaskLevelSymbol(int level) {
        return masksConfig.getString("display.level-symbols." + level, String.valueOf(level));
    }
    
    public String getDivinePrefix() {
        return masksConfig.getString("display.divine-prefix", "&5⚜ ");
    }
    
    public String getDivineSuffix() {
        return masksConfig.getString("display.divine-suffix", " &5⚜");
    }
    
    public String getMaxIndicator() {
        return masksConfig.getString("display.max-indicator", " &e&lMAX");
    }
    
    public String getLoreHeader() {
        return masksConfig.getString("display.lore.header", "&8━━━━━━━━━━━━━━━━━");
    }
    
    public String getLoreAbilityUnlocked() {
        return masksConfig.getString("display.lore.ability-unlocked", "&a✔ &7{description}");
    }
    
    public String getLoreAbilityLocked() {
        return masksConfig.getString("display.lore.ability-locked", "&8✖ &8{description}");
    }
    
    public String getLoreFooter() {
        return masksConfig.getString("display.lore.footer", "&8━━━━━━━━━━━━━━━━━");
    }
    
    public String getDivineTag() {
        return masksConfig.getString("display.lore.divine-tag", "&5✦ DIVINE MASK ✦");
    }
    
    public String getEquipHint() {
        return masksConfig.getString("display.lore.equip-hint", "&8Right-click to equip");
    }
    
    // Raw configs for advanced usage
    public FileConfiguration getConfig() {
        return config;
    }
    
    public FileConfiguration getHeadsConfig() {
        return headsConfig;
    }
    
    public FileConfiguration getLevelsConfig() {
        return levelsConfig;
    }
    
    public FileConfiguration getMasksConfig() {
        return masksConfig;
    }
    
    // Balance settings getters
    public double getChanceMultiplier() {
        return chanceMultiplier;
    }
    
    public int getAmplifierOffset() {
        return amplifierOffset;
    }
    
    public int getMaxAmplifier() {
        return maxAmplifier;
    }
    
    public double getDurationMultiplier() {
        return durationMultiplier;
    }
    
    public double getDamageMultiplier() {
        return damageMultiplier;
    }
    
    public double getValueMultiplier() {
        return valueMultiplier;
    }
    
    /**
     * Apply balance settings to an amplifier value
     */
    public int getBalancedAmplifier(int baseAmplifier) {
        int adjusted = baseAmplifier + amplifierOffset;
        if (maxAmplifier >= 0 && adjusted > maxAmplifier) {
            adjusted = maxAmplifier;
        }
        return Math.max(0, adjusted);
    }
    
    /**
     * Apply balance settings to a chance value
     */
    public double getBalancedChance(double baseChance) {
        return Math.min(100, baseChance * chanceMultiplier);
    }
    
    /**
     * Apply balance settings to a duration value
     */
    public int getBalancedDuration(int baseDuration) {
        if (baseDuration < 0) return baseDuration; // -1 means permanent
        return (int) (baseDuration * durationMultiplier);
    }
}

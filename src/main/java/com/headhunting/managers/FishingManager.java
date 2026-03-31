package com.headhunting.managers;

import com.headhunting.HeadHunting;
import com.headhunting.data.FishingReward;
import com.headhunting.data.PlayerData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.*;

/**
 * Manages warzone fishing rewards
 */
public class FishingManager {
    
    private final HeadHunting plugin;
    private final List<FishingReward> rewards = new ArrayList<>();
    private final Random random = new Random();
    private double totalWeight = 0;
    
    // Settings
    private boolean enabled = true;
    private String claimPlugin = "factions";
    private String worldGuardRegion = "warzone";
    private List<String> allowedWorlds = new ArrayList<>();
    private boolean dropAtHook = false;
    private int titleFadeIn = 10;
    private int titleStay = 40;
    private int titleFadeOut = 10;
    private String actionBarMessage = "";
    
    // Fishing Boost Event (catch rate boost — fish bite faster)
    private boolean boostActive = false;
    private double catchRateMultiplier = 2.0;
    private int boostIntervalMinutes = 60;
    private int boostDurationMinutes = 10;
    private org.bukkit.scheduler.BukkitTask boostTask;
    
    // Configurable broadcast messages
    private List<String> boostStartMessages = new ArrayList<>(Arrays.asList(
        "&b&l⚡ FISHING BOOST EVENT! ⚡",
        "&7Fish are biting &b{multiplier}x &7faster for {duration} minutes!"
    ));
    private List<String> boostEndMessages = new ArrayList<>(Arrays.asList(
        "&7The &bFishing Boost &7event has ended!"
    ));
    private boolean boostBroadcastEnabled = true;
    
    public FishingManager(HeadHunting plugin) {
        this.plugin = plugin;
        loadFishing();
        startBoostScheduler();
    }
    
    private void startBoostScheduler() {
        // Schedule boost event every 60 minutes
        long intervalTicks = boostIntervalMinutes * 60 * 20L;
        long durationTicks = boostDurationMinutes * 60 * 20L;
        
        boostTask = org.bukkit.Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            startBoostEvent();
            
            // Schedule end of boost
            org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, this::endBoostEvent, durationTicks);
        }, intervalTicks, intervalTicks);
        
        plugin.getLogger().info("Fishing boost event scheduled: every " + boostIntervalMinutes + " min, lasts " + boostDurationMinutes + " min");
    }
    
    private void startBoostEvent() {
        boostActive = true;
        
        String multiplierStr = catchRateMultiplier == (int) catchRateMultiplier 
            ? String.valueOf((int) catchRateMultiplier) 
            : String.valueOf(catchRateMultiplier);
        
        // Broadcast to all players
        if (boostBroadcastEnabled) {
            for (org.bukkit.entity.Player player : org.bukkit.Bukkit.getOnlinePlayers()) {
                for (String msg : boostStartMessages) {
                    String formatted = msg
                        .replace("{multiplier}", multiplierStr)
                        .replace("{duration}", String.valueOf(boostDurationMinutes));
                    com.headhunting.utils.MessageUtil.send(player, formatted);
                }
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_WITHER_SPAWN, 0.5f, 1.5f);
            }
        }
        
        plugin.getLogger().info("Fishing boost event started! " + multiplierStr + "x catch rate for " + boostDurationMinutes + " minutes");
    }
    
    private void endBoostEvent() {
        boostActive = false;
        
        // Broadcast end
        if (boostBroadcastEnabled) {
            for (org.bukkit.entity.Player player : org.bukkit.Bukkit.getOnlinePlayers()) {
                for (String msg : boostEndMessages) {
                    String formatted = msg
                        .replace("{duration}", String.valueOf(boostDurationMinutes));
                    com.headhunting.utils.MessageUtil.send(player, formatted);
                }
            }
        }
        
        plugin.getLogger().info("Fishing boost event ended");
    }
    
    public boolean isBoostActive() {
        return boostActive;
    }
    
    /**
     * Get the current server-wide catch rate multiplier.
     * Returns the configured multiplier when boost event is active, 1.0 otherwise.
     * WarzoneFishing uses this to reduce fish hook wait times.
     */
    public double getBoostMultiplier() {
        return boostActive ? catchRateMultiplier : 1.0;
    }
    
    public void cancelBoostTask() {
        if (boostTask != null) {
            boostTask.cancel();
        }
    }
    
    public void loadFishing() {
        rewards.clear();
        totalWeight = 0;
        
        File file = new File(plugin.getDataFolder(), "fishing.yml");
        if (!file.exists()) {
            plugin.saveResource("fishing.yml", false);
        }
        
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        
        // Load settings
        ConfigurationSection settings = config.getConfigurationSection("settings");
        if (settings != null) {
            enabled = settings.getBoolean("enabled", true);
            claimPlugin = settings.getString("claim-plugin", "factions");
            worldGuardRegion = settings.getString("worldguard-region", "warzone");
            allowedWorlds = settings.getStringList("allowed-worlds");
            dropAtHook = settings.getBoolean("drop-at-hook", false);
            titleFadeIn = settings.getInt("title-fade-in", 10);
            titleStay = settings.getInt("title-stay", 40);
            titleFadeOut = settings.getInt("title-fade-out", 10);
            actionBarMessage = settings.getString("action-bar-message", "");
        }
        
        // Load boost event settings
        ConfigurationSection boostSection = config.getConfigurationSection("boost-event");
        if (boostSection != null) {
            catchRateMultiplier = boostSection.getDouble("catch-rate-multiplier", 2.0);
            boostIntervalMinutes = boostSection.getInt("interval-minutes", 60);
            boostDurationMinutes = boostSection.getInt("duration-minutes", 10);
            boostBroadcastEnabled = boostSection.getBoolean("broadcast-enabled", true);
            
            if (boostSection.isList("broadcast-start-messages")) {
                boostStartMessages = boostSection.getStringList("broadcast-start-messages");
            }
            if (boostSection.isList("broadcast-end-messages")) {
                boostEndMessages = boostSection.getStringList("broadcast-end-messages");
            }
        }
        
        // Load rewards
        ConfigurationSection rewardsSection = config.getConfigurationSection("rewards");
        if (rewardsSection == null) {
            plugin.getLogger().warning("No rewards section found in fishing.yml!");
            return;
        }
        
        for (String rewardId : rewardsSection.getKeys(false)) {
            ConfigurationSection rewardSection = rewardsSection.getConfigurationSection(rewardId);
            if (rewardSection != null) {
                try {
                    FishingReward reward = new FishingReward(rewardId, rewardSection);
                    rewards.add(reward);
                    totalWeight += reward.getChance();
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to load fishing reward: " + rewardId + " - " + e.getMessage());
                }
            }
        }
        
        // Sort by chance (rarest first)
        rewards.sort((a, b) -> Double.compare(a.getChance(), b.getChance()));
        
        plugin.getLogger().info("Loaded " + rewards.size() + " fishing rewards.");
    }
    
    /**
     * Get a random reward for a player (considers level requirements and mask access).
     * Guardian mask no longer boosts drop weights — it now increases catch rate
     * (fish bite faster) via WarzoneFishing's FishingListener instead.
     */
    public FishingReward getRandomReward(Player player) {
        if (rewards.isEmpty() || totalWeight <= 0) {
            return null;
        }
        
        PlayerData data = plugin.getDataManager().getPlayerData(player);
        int playerLevel = data.getLevel();
        boolean hasGuardianMask = isWearingGuardianMask(player);
        
        // Filter rewards player can receive
        List<FishingReward> availableRewards = new ArrayList<>();
        double availableWeight = 0;
        
        for (FishingReward reward : rewards) {
            // Check level requirement
            if (reward.getRequiredLevel() > playerLevel) {
                continue;
            }
            
            // Check guardian mask requirement
            if (reward.requiresGuardianMask() && !hasGuardianMask) {
                continue;
            }
            
            availableRewards.add(reward);
            availableWeight += reward.getChance();
        }
        
        if (availableRewards.isEmpty() || availableWeight <= 0) {
            return null;
        }
        
        // Roll for reward using base weights only
        double roll = random.nextDouble() * availableWeight;
        double cumulative = 0;
        
        for (FishingReward reward : availableRewards) {
            cumulative += reward.getChance();
            if (roll < cumulative) {
                return reward;
            }
        }
        
        return availableRewards.get(availableRewards.size() - 1);
    }
    
    /**
     * Check if player is wearing guardian or elder guardian mask
     */
    private boolean isWearingGuardianMask(Player player) {
        PlayerData data = plugin.getDataManager().getPlayerData(player);
        String mask = data.getEquippedMask();
        if (mask == null) return false;
        return mask.equalsIgnoreCase("guardian") || mask.equalsIgnoreCase("elder_guardian");
    }
    
    // Getters for settings
    public boolean isEnabled() { return enabled; }
    public String getClaimPlugin() { return claimPlugin; }
    public String getWorldGuardRegion() { return worldGuardRegion; }
    public List<String> getAllowedWorlds() { return allowedWorlds; }
    public boolean isDropAtHook() { return dropAtHook; }
    public int getTitleFadeIn() { return titleFadeIn; }
    public int getTitleStay() { return titleStay; }
    public int getTitleFadeOut() { return titleFadeOut; }
    public String getActionBarMessage() { return actionBarMessage; }
    
    public List<FishingReward> getAllRewards() {
        return new ArrayList<>(rewards);
    }
    
    public int getRewardCount() {
        return rewards.size();
    }
    
    public void reload() {
        loadFishing();
    }
}

package com.headhunting.managers;

import com.headhunting.HeadHunting;
import com.headhunting.data.PlayerData;
import com.headhunting.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

/**
 * Manages daily missions that refresh every day
 */
public class DailyMissionManager {
    
    private final HeadHunting plugin;
    private final Map<String, DailyMission> dailyMissions = new HashMap<>();
    private final List<String> activeMissionIds = new ArrayList<>();
    private int missionsPerDay = 3;
    private String timezone = "UTC";
    private LocalDate lastResetDate;
    
    public DailyMissionManager(HeadHunting plugin) {
        this.plugin = plugin;
        loadConfig();
        loadState();
        checkAndResetDaily();
        
        // Schedule daily check every minute
        Bukkit.getScheduler().runTaskTimer(plugin, this::checkAndResetDaily, 1200L, 1200L);
    }
    
    public void loadConfig() {
        dailyMissions.clear();
        
        File file = new File(plugin.getDataFolder(), "daily-missions.yml");
        if (!file.exists()) {
            plugin.saveResource("daily-missions.yml", false);
        }
        
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        
        // Load settings
        ConfigurationSection settings = config.getConfigurationSection("settings");
        if (settings != null) {
            missionsPerDay = settings.getInt("missions-per-day", 3);
            timezone = settings.getString("timezone", "UTC");
        }
        
        // Load missions
        ConfigurationSection missionsSection = config.getConfigurationSection("missions");
        plugin.getLogger().info("[DailyMissions] Config keys: " + config.getKeys(false));
        
        if (missionsSection == null) {
            plugin.getLogger().warning("[DailyMissions] 'missions' section is NULL! Check YAML format.");
        } else {
            plugin.getLogger().info("[DailyMissions] Found missions section with keys: " + missionsSection.getKeys(false));
            for (String id : missionsSection.getKeys(false)) {
                ConfigurationSection missionSection = missionsSection.getConfigurationSection(id);
                if (missionSection != null) {
                    try {
                        dailyMissions.put(id.toLowerCase(), new DailyMission(id, missionSection));
                        plugin.getLogger().info("[DailyMissions] Loaded mission: " + id);
                    } catch (Exception e) {
                        plugin.getLogger().warning("[DailyMissions] Failed to load mission " + id + ": " + e.getMessage());
                    }
                } else {
                    plugin.getLogger().warning("[DailyMissions] Mission section null for: " + id);
                }
            }
        }
        
        plugin.getLogger().info("Loaded " + dailyMissions.size() + " daily mission templates.");
        if (dailyMissions.isEmpty()) {
            plugin.getLogger().warning("No daily missions loaded! Check daily-missions.yml format.");
        } else {
            plugin.getLogger().info("Daily mission IDs: " + dailyMissions.keySet());
        }
    }
    
    /**
     * Check if daily reset is needed
     */
    public void checkAndResetDaily() {
        LocalDate today = LocalDate.now(ZoneId.of(timezone));
        
        if (lastResetDate == null || !lastResetDate.equals(today)) {
            resetDailyMissions();
            lastResetDate = today;
        }
    }
    
    /**
     * Reset and select new daily missions
     */
    public void resetDailyMissions() {
        activeMissionIds.clear();
        
        // Get list of all mission IDs
        List<String> allMissionIds = new ArrayList<>(dailyMissions.keySet());
        
        // Shuffle and pick random missions
        Collections.shuffle(allMissionIds);
        int count = Math.min(missionsPerDay, allMissionIds.size());
        
        for (int i = 0; i < count; i++) {
            activeMissionIds.add(allMissionIds.get(i));
        }
        
        plugin.getLogger().info("Daily missions reset! Active (" + activeMissionIds.size() + "): " + activeMissionIds);
        if (activeMissionIds.isEmpty()) {
            plugin.getLogger().warning("No active daily missions! dailyMissions size: " + dailyMissions.size());
        }
        
        // Persist the new state
        saveState();
        
        // Broadcast to online players
        for (Player player : Bukkit.getOnlinePlayers()) {
            MessageUtil.send(player, "&6&l✦ Daily Missions Reset! &eCheck /missions for new challenges!");
        }
    }
    
    /**
     * Get active daily missions
     */
    public List<DailyMission> getActiveMissions() {
        List<DailyMission> active = new ArrayList<>();
        for (String id : activeMissionIds) {
            DailyMission mission = dailyMissions.get(id);
            if (mission != null) {
                active.add(mission);
            }
        }
        return active;
    }
    
    /**
     * Add progress to daily missions
     */
    public void addProgress(Player player, String type, int amount, String target) {
        PlayerData data = plugin.getDataManager().getPlayerData(player);
        
        for (String missionId : activeMissionIds) {
            DailyMission mission = dailyMissions.get(missionId);
            if (mission == null) continue;
            
            // Check if already completed today
            if (data.isDailyMissionComplete(missionId)) continue;
            
            // Check type match
            if (!mission.getType().equalsIgnoreCase(type)) continue;
            
            // Check target match if applicable
            if (!mission.getTarget().isEmpty() && target != null) {
                if (!mission.getTarget().equalsIgnoreCase(target)) continue;
            }
            
            // Add progress
            int current = data.getDailyMissionProgress(missionId);
            int newProgress = Math.min(current + amount, mission.getRequired());
            data.setDailyMissionProgress(missionId, newProgress);
            
            // Check completion
            if (newProgress >= mission.getRequired() && current < mission.getRequired()) {
                completeDailyMission(player, mission);
            }
        }
    }
    
    /**
     * Complete a daily mission
     */
    private void completeDailyMission(Player player, DailyMission mission) {
        PlayerData data = plugin.getDataManager().getPlayerData(player);
        data.setDailyMissionComplete(mission.getId(), true);
        
        // Execute reward commands
        for (String command : mission.getRewardCommands()) {
            String parsed = command
                .replace("{player}", player.getName())
                .replace("{mission}", mission.getDisplayName());
            
            // Handle msg commands directly to avoid "Console -> player" prefix
            if (parsed.toLowerCase().startsWith("msg ")) {
                String[] parts = parsed.split(" ", 3);
                if (parts.length >= 3) {
                    player.sendMessage(MessageUtil.color(parts[2]));
                }
            } else {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsed);
            }
        }
        
        // Notify player
        MessageUtil.send(player, "&a&l✓ Daily Mission Complete!");
        MessageUtil.sendRaw(player, MessageUtil.color("&7Completed: &e" + mission.getDisplayName()));
        
        // Play sound
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
    }
    
    /**
     * Get player's progress on a daily mission
     */
    public int getProgress(Player player, String missionId) {
        PlayerData data = plugin.getDataManager().getPlayerData(player);
        return data.getDailyMissionProgress(missionId);
    }
    
    /**
     * Check if player completed a daily mission
     */
    public boolean isComplete(Player player, String missionId) {
        PlayerData data = plugin.getDataManager().getPlayerData(player);
        return data.isDailyMissionComplete(missionId);
    }
    
    /**
     * Reset player's daily progress (called on new day)
     */
    public void resetPlayerDaily(Player player) {
        PlayerData data = plugin.getDataManager().getPlayerData(player);
        data.resetDailyMissions();
    }
    
    /**
     * Load persisted daily mission state from daily-state.yml
     */
    private void loadState() {
        File stateFile = new File(plugin.getDataFolder(), "daily-state.yml");
        if (!stateFile.exists()) {
            return;
        }
        
        try {
            YamlConfiguration state = YamlConfiguration.loadConfiguration(stateFile);
            String dateStr = state.getString("last-reset-date");
            if (dateStr == null) return;
            
            LocalDate savedDate = LocalDate.parse(dateStr);
            LocalDate today = LocalDate.now(ZoneId.of(timezone));
            
            // Only restore state if it's the same day
            if (savedDate.equals(today)) {
                List<String> savedMissions = state.getStringList("active-missions");
                if (!savedMissions.isEmpty()) {
                    activeMissionIds.clear();
                    activeMissionIds.addAll(savedMissions);
                    lastResetDate = savedDate;
                    plugin.getLogger().info("[DailyMissions] Restored persisted state for " + today + 
                        " with missions: " + activeMissionIds);
                }
            } else {
                plugin.getLogger().info("[DailyMissions] Persisted state is from " + savedDate + 
                    ", today is " + today + " - will reset.");
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[DailyMissions] Failed to load daily-state.yml: " + e.getMessage());
        }
    }
    
    /**
     * Persist current daily mission state to daily-state.yml
     */
    private void saveState() {
        File stateFile = new File(plugin.getDataFolder(), "daily-state.yml");
        YamlConfiguration state = new YamlConfiguration();
        
        if (lastResetDate != null) {
            state.set("last-reset-date", lastResetDate.toString());
        }
        state.set("active-missions", activeMissionIds);
        
        try {
            state.save(stateFile);
        } catch (IOException e) {
            plugin.getLogger().warning("[DailyMissions] Failed to save daily-state.yml: " + e.getMessage());
        }
    }
    
    /**
     * Shutdown - persist state for safety
     */
    public void shutdown() {
        saveState();
    }
    
    public void reload() {
        loadConfig();
    }
    
    /**
     * Daily Mission data class
     */
    public static class DailyMission {
        private final String id;
        private final String displayName;
        private final String description;
        private final String type;
        private final String target;
        private final int required;
        private final List<String> rewardCommands;
        private final String icon;
        private final short iconData;
        
        public DailyMission(String id, ConfigurationSection section) {
            this.id = id;
            this.displayName = section.getString("display-name", "&eDaily Mission");
            this.description = section.getString("description", "Complete this daily task!");
            this.type = section.getString("type", "KILL_MOBS");
            this.target = section.getString("target", "");
            this.required = section.getInt("required", 10);
            this.rewardCommands = section.getStringList("rewards");
            this.icon = section.getString("icon", "PAPER");
            this.iconData = (short) section.getInt("icon-data", 0);
        }
        
        public String getId() { return id; }
        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
        public String getType() { return type; }
        public String getTarget() { return target; }
        public int getRequired() { return required; }
        public List<String> getRewardCommands() { return rewardCommands; }
        public String getIcon() { return icon; }
        public short getIconData() { return iconData; }
    }
}

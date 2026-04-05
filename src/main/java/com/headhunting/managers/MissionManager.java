package com.headhunting.managers;

import com.headhunting.HeadHunting;
import com.headhunting.data.MaskConfig;
import com.headhunting.data.MissionConfig;
import com.headhunting.data.MissionConfig.MissionRequirement;
import com.headhunting.data.MissionConfig.MissionType;
import com.headhunting.data.PlayerData;
import com.headhunting.data.PlayerData.MissionProgress;
import com.headhunting.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.*;

/**
 * Manages divine missions
 */
public class MissionManager {
    
    private final HeadHunting plugin;
    private final Map<String, MissionConfig> missions = new HashMap<>();
    
    public MissionManager(HeadHunting plugin) {
        this.plugin = plugin;
        loadMissions();
    }
    
    public void loadMissions() {
        missions.clear();
        
        File file = new File(plugin.getDataFolder(), "missions.yml");
        if (!file.exists()) {
            plugin.saveResource("missions.yml", false);
        }
        
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        
        // Load missions
        ConfigurationSection missionsSection = config.getConfigurationSection("missions");
        
        if (missionsSection == null) {
            plugin.getLogger().warning("No missions section found in missions.yml!");
            return;
        }
        
        for (String missionId : missionsSection.getKeys(false)) {
            ConfigurationSection missionSection = missionsSection.getConfigurationSection(missionId);
            if (missionSection != null) {
                missions.put(missionId.toLowerCase(), new MissionConfig(missionId, missionSection));
            }
        }
        
        plugin.getLogger().info("Loaded " + missions.size() + " divine missions.");
    }
    
    /**
     * Get a mission by ID
     */
    public MissionConfig getMission(String id) {
        return missions.get(id.toLowerCase());
    }
    
    /**
     * Get all missions
     */
    public Map<String, MissionConfig> getAllMissions() {
        return missions;
    }
    
    /**
     * Get mission that unlocks a specific mask
     */
    public MissionConfig getMissionForMask(String maskId) {
        for (MissionConfig mission : missions.values()) {
            if (mission.getUnlocksMask().equalsIgnoreCase(maskId)) {
                return mission;
            }
        }
        return null;
    }
    
    /**
     * Add progress to a mission requirement
     */
    public void addProgress(Player player, MissionType type, int amount, String target) {
        PlayerData data = plugin.getDataManager().getPlayerData(player);
        
        for (MissionConfig mission : missions.values()) {
            // Skip completed missions
            MissionProgress missionProgress = data.getMissionProgress(mission.getId());
            if (missionProgress.isCompleted()) continue;
            
            for (MissionRequirement req : mission.getRequirements()) {
                if (req.getType() != type) continue;
                
                // Check target match if specified
                if (!req.getTarget().isEmpty() && target != null) {
                    if (!req.getTarget().equalsIgnoreCase(target)) continue;
                }
                
                // Get or create progress key
                String progressKey = mission.getId() + ":" + req.getId();
                int currentProgress = getRequirementProgress(data, progressKey);
                int newProgress = Math.min(currentProgress + amount, req.getAmount());
                
                setRequirementProgress(data, progressKey, newProgress);
                
                // Check if requirement is now complete
                if (newProgress >= req.getAmount() && currentProgress < req.getAmount()) {
                    notifyRequirementComplete(player, mission, req);
                }
            }
            
            // Check if entire mission is now complete
            if (isMissionComplete(player, mission) && !missionProgress.isCompleted()) {
                completeMission(player, mission);
            }
        }
    }
    
    /**
     * Add progress with no target
     */
    public void addProgress(Player player, MissionType type, int amount) {
        addProgress(player, type, amount, null);
    }
    
    /**
     * Get progress for a specific requirement
     */
    public int getRequirementProgress(PlayerData data, String progressKey) {
        MissionProgress progress = data.getMissionProgress(progressKey);
        return progress.getProgress();
    }
    
    /**
     * Set progress for a specific requirement
     */
    public void setRequirementProgress(PlayerData data, String progressKey, int amount) {
        MissionProgress progress = data.getMissionProgress(progressKey);
        progress.setProgress(amount);
    }
    
    /**
     * Get player's progress on a requirement
     */
    public int getPlayerProgress(Player player, MissionConfig mission, MissionRequirement req) {
        PlayerData data = plugin.getDataManager().getPlayerData(player);
        String progressKey = mission.getId() + ":" + req.getId();
        
        // Handle special check-based requirements
        switch (req.getType()) {
            case REACH_LEVEL:
                return data.getLevel();
                
            case OWN_MASK:
                return data.ownsMask(req.getTarget()) ? 1 : 0;
                
            case OWN_MASK_LEVEL:
                int maskLevel = data.getMaskLevel(req.getTarget());
                return maskLevel;
                
            case OWN_MASK_COUNT:
                return (int) data.getMasks().values().stream()
                    .filter(PlayerData.MaskData::isOwned)
                    .count();
                
            default:
                return getRequirementProgress(data, progressKey);
        }
    }
    
    /**
     * Check if a mission is complete
     */
    public boolean isMissionComplete(Player player, MissionConfig mission) {
        for (MissionRequirement req : mission.getRequirements()) {
            int progress = getPlayerProgress(player, mission, req);
            if (progress < req.getAmount()) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * Complete a mission for a player
     */
    public void completeMission(Player player, MissionConfig mission) {
        PlayerData data = plugin.getDataManager().getPlayerData(player);
        MissionProgress progress = data.getMissionProgress(mission.getId());
        progress.setCompleted(true);
        
        // Unlock the mask
        String maskId = mission.getUnlocksMask();
        if (!maskId.isEmpty()) {
            // Set mission as complete so mask can be crafted
            plugin.getLogger().info(player.getName() + " completed mission " + mission.getId() + 
                " and can now craft " + maskId + " mask!");
        }
        
        // Execute reward commands
        for (String command : mission.getRewardCommands()) {
            String parsed = command.replace("{player}", player.getName());
            
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
        
        // Send completion message
        MessageUtil.send(player, "&6&l✦ MISSION COMPLETE! ✦");
        MessageUtil.sendRaw(player, MessageUtil.color("&7You completed: &e" + mission.getDisplayName()));
        
        // Get mask display name
        String maskDisplayName = maskId;
        MaskConfig maskConfig = plugin.getConfigManager().getMaskConfig(maskId);
        if (maskConfig != null) {
            maskDisplayName = maskConfig.getDisplayName();
        }
        MessageUtil.sendRaw(player, MessageUtil.color("&7You can now craft the &d" + maskDisplayName + "&7!"));
        
        // Broadcast
        String broadcast = plugin.getConfigManager().getConfig().getString(
            "messages.mission-complete-broadcast",
            "&6&l✦ &e{player} &7completed the &d{mission} &7mission!"
        );
        if (broadcast != null && !broadcast.isEmpty()) {
            Bukkit.broadcastMessage(MessageUtil.color(broadcast
                .replace("{player}", player.getName())
                .replace("{mission}", mission.getDisplayName())
            ));
        }
        
        // Play sound
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 0.5f);
    }
    
    /**
     * Notify player a requirement is complete
     */
    private void notifyRequirementComplete(Player player, MissionConfig mission, MissionRequirement req) {
        MessageUtil.send(player, "&a✓ &7Mission progress: &e" + req.getDescription());
    }
    
    /**
     * Check if player has completed the mission for a mask
     */
    public boolean hasMissionComplete(Player player, String maskId) {
        MissionConfig mission = getMissionForMask(maskId);
        if (mission == null) {
            return true; // No mission required
        }
        
        PlayerData data = plugin.getDataManager().getPlayerData(player);
        return data.getMissionProgress(mission.getId()).isCompleted();
    }
    
    /**
     * Get overall progress percentage for a mission
     */
    public double getMissionProgress(Player player, MissionConfig mission) {
        if (mission.getRequirements().isEmpty()) return 100.0;
        
        double totalProgress = 0;
        for (MissionRequirement req : mission.getRequirements()) {
            int current = getPlayerProgress(player, mission, req);
            int required = req.getAmount();
            totalProgress += Math.min(1.0, (double) current / required);
        }
        
        return (totalProgress / mission.getRequirements().size()) * 100;
    }
    
    public void reload() {
        loadMissions();
    }
    
}

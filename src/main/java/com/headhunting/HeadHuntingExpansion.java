package com.headhunting;

import com.headhunting.data.MaskConfig;
import com.headhunting.data.PlayerData;
import com.headhunting.managers.DailyMissionManager;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * PlaceholderAPI expansion for HeadHunting
 */
public class HeadHuntingExpansion extends PlaceholderExpansion {
    
    private final HeadHunting plugin;
    
    public HeadHuntingExpansion(HeadHunting plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public String getIdentifier() {
        return "headhunting";
    }
    
    @Override
    public String getAuthor() {
        return plugin.getDescription().getAuthors().toString();
    }
    
    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }
    
    @Override
    public boolean persist() {
        return true;
    }
    
    @Override
    public String onPlaceholderRequest(Player player, String params) {
        if (player == null) {
            return "";
        }
        
        PlayerData data = plugin.getDataManager().getPlayerData(player);
        
        switch (params.toLowerCase()) {
            case "level":
                return String.valueOf(data.getLevel());
                
            case "xp":
                return String.valueOf(data.getXp());
                
            case "xp_formatted":
                return String.format("%,d", data.getXp());
                
            case "xp_needed":
                int needed = plugin.getLevelManager().getXpForNextLevel(player);
                return String.valueOf(needed);
                
            case "xp_needed_formatted":
                int neededFormatted = plugin.getLevelManager().getXpForNextLevel(player);
                return String.format("%,d", neededFormatted);
                
            case "progress":
                return String.format("%.1f", plugin.getLevelManager().getProgressPercentage(player));
                
            case "progress_bar":
                return plugin.getLevelManager().buildProgressBar(player);
                
            case "mask":
                String maskId = data.getEquippedMask();
                if (maskId == null) return "None";
                MaskConfig config = plugin.getConfigManager().getMaskConfig(maskId);
                return config != null ? config.getDisplayName() : "None";
                
            case "mask_id":
                return data.getEquippedMask() != null ? data.getEquippedMask() : "none";
                
            case "mask_level":
                String equipped = data.getEquippedMask();
                if (equipped == null) return "0";
                return String.valueOf(data.getMaskLevel(equipped));
                
            case "masks_owned":
                long owned = data.getMasks().values().stream()
                    .filter(PlayerData.MaskData::isOwned)
                    .count();
                return String.valueOf(owned);
                
            case "masks_total":
                return String.valueOf(plugin.getConfigManager().getMaskConfigs().size());
                
            case "is_max_level":
                return plugin.getLevelManager().isMaxLevel(player) ? "true" : "false";
                
            case "heads_collected":
                return String.valueOf(data.getTotalHeadsCollected());
                
            case "heads_collected_formatted":
                return String.format("%,d", data.getTotalHeadsCollected());
                
            case "heads_sold":
                return String.valueOf(data.getTotalHeadsSold());
                
            case "heads_sold_formatted":
                return String.format("%,d", data.getTotalHeadsSold());
                
            case "masks_crafted":
                return String.valueOf(data.getTotalMasksCrafted());
                
            case "max_level":
                return String.valueOf(plugin.getConfigManager().getMaxLevel());
                
            case "fishing_luck":
                return String.valueOf(data.getLevel() * 2);
                
            case "rank": {
                Map<UUID, PlayerData> allData = plugin.getDataManager().getAllPlayerData();
                List<Map.Entry<UUID, PlayerData>> sorted = new ArrayList<>(allData.entrySet());
                sorted.sort((a, b) -> {
                    int lvlCmp = Integer.compare(b.getValue().getLevel(), a.getValue().getLevel());
                    if (lvlCmp != 0) return lvlCmp;
                    return Integer.compare(b.getValue().getXp(), a.getValue().getXp());
                });
                UUID playerUuid = player.getUniqueId();
                for (int i = 0; i < sorted.size(); i++) {
                    if (sorted.get(i).getKey().equals(playerUuid)) {
                        return String.valueOf(i + 1);
                    }
                }
                return String.valueOf(sorted.size() + 1);
            }
                
            case "daily_missions_complete": {
                int count = 0;
                for (DailyMissionManager.DailyMission mission : plugin.getDailyMissionManager().getActiveMissions()) {
                    if (plugin.getDailyMissionManager().isComplete(player, mission.getId())) {
                        count++;
                    }
                }
                return String.valueOf(count);
            }
                
            case "daily_missions_total":
                return String.valueOf(plugin.getDailyMissionManager().getActiveMissions().size());
                
            case "booster_active":
                return (plugin.getBoosterManager().getXpMultiplier(player) > 1.0 ||
                        plugin.getBoosterManager().getSellMultiplier(player) > 1.0) ? "true" : "false";
                
            case "booster_xp_multi":
                return String.format("%.1fx", plugin.getBoosterManager().getXpMultiplier(player));
                
            case "booster_sell_multi":
                return String.format("%.1fx", plugin.getBoosterManager().getSellMultiplier(player));
                
            default:
                return null;
        }
    }
}

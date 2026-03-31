package com.headhunting.managers;

import com.headhunting.HeadHunting;
import com.headhunting.data.LevelConfig;
import com.headhunting.data.MaskConfig;
import com.headhunting.data.PlayerData;
import com.headhunting.utils.MessageUtil;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Manages player leveling system
 */
public class LevelManager {
    
    private final HeadHunting plugin;
    
    // Track players who have been notified they can level up (to avoid spam)
    private final Set<UUID> notifiedReadyToLevel = new HashSet<>();
    
    public LevelManager(HeadHunting plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Add XP to a player and check for level up
     * Applies booster multipliers automatically
     */
    public void addXp(Player player, int amount) {
        // Apply booster multiplier
        double multiplier = plugin.getBoosterManager().getXpMultiplier(player);
        int boostedAmount = (int) Math.round(amount * multiplier);
        
        PlayerData data = plugin.getDataManager().getPlayerData(player);
        data.addXp(boostedAmount);
        
        // Show boost indicator if boosted
        if (multiplier > 1.0) {
            MessageUtil.sendActionBar(player, "&b&l⚡ " + String.format("%.1fx", multiplier) + " XP Boost! &7+" + boostedAmount + " XP");
        }
        
        // Check for level up
        checkLevelUp(player, data);
    }
    
    /**
     * Check if player can level up and notify them
     */
    public void checkLevelUp(Player player, PlayerData data) {
        int currentLevel = data.getLevel();
        int maxLevel = plugin.getConfigManager().getMaxLevel();
        
        if (currentLevel >= maxLevel) {
            return; // Already max level
        }
        
        LevelConfig nextLevelConfig = plugin.getConfigManager().getLevelConfig(currentLevel + 1);
        if (nextLevelConfig == null) {
            return;
        }
        
        // Check if player has enough XP
        if (data.getXp() < nextLevelConfig.getXpRequired()) {
            // Not enough XP yet - clear notification flag if they were previously notified
            notifiedReadyToLevel.remove(player.getUniqueId());
            return;
        }
        
        // Player has enough XP! Notify them if we haven't already
        if (!notifiedReadyToLevel.contains(player.getUniqueId())) {
            notifiedReadyToLevel.add(player.getUniqueId());
            notifyReadyToLevelUp(player, currentLevel + 1, nextLevelConfig);
        }
    }
    
    /**
     * Notify player they can level up with title and sound
     */
    private void notifyReadyToLevelUp(Player player, int nextLevel, LevelConfig config) {
        // Play chime sound
        player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 2.0f);
        
        // Delay a second chime for emphasis
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 2.0f);
        }, 4L);
        
        // Send title
        sendReadyToLevelTitle(player, nextLevel);
        
        // Send chat message with cost info
        Economy economy = plugin.getEconomy();
        double cost = config.getMoneyCost();
        
        MessageUtil.sendMultiple(player,
            "",
            "&a&l✦ READY TO RANK UP! ✦",
            "&7You have enough XP to reach &bLevel " + nextLevel + "&7!",
            cost > 0 ? "&7Cost: &a" + MessageUtil.formatMoney(cost) : "",
            "&eType &6/level &eto rank up!",
            ""
        );
    }
    
    /**
     * Send the ready to level up title
     */
    private void sendReadyToLevelTitle(Player player, int nextLevel) {
        player.sendTitle(
            MessageUtil.color("&a&l✦ READY TO RANK UP! ✦"),
            MessageUtil.color("&7Type &e/level &7to reach &bLevel " + nextLevel),
            10, 60, 20
        );
    }
    
    /**
     * Clear notification flag when player levels up
     */
    public void clearLevelUpNotification(Player player) {
        notifiedReadyToLevel.remove(player.getUniqueId());
    }
    
    /**
     * Attempt to level up a player (called when they have enough XP and want to spend money)
     */
    public LevelUpResult attemptLevelUp(Player player) {
        PlayerData data = plugin.getDataManager().getPlayerData(player);
        int currentLevel = data.getLevel();
        int maxLevel = plugin.getConfigManager().getMaxLevel();
        
        if (currentLevel >= maxLevel) {
            return LevelUpResult.ALREADY_MAX;
        }
        
        LevelConfig nextLevelConfig = plugin.getConfigManager().getLevelConfig(currentLevel + 1);
        if (nextLevelConfig == null) {
            return LevelUpResult.CONFIG_ERROR;
        }
        
        // Check XP
        if (data.getXp() < nextLevelConfig.getXpRequired()) {
            return LevelUpResult.NOT_ENOUGH_XP;
        }
        
        // Check money
        Economy economy = plugin.getEconomy();
        double cost = nextLevelConfig.getMoneyCost();
        
        if (cost > 0 && !economy.has(player, cost)) {
            return LevelUpResult.NOT_ENOUGH_MONEY;
        }
        
        // Process level up
        if (cost > 0) {
            economy.withdrawPlayer(player, cost);
        }
        
        data.setLevel(currentLevel + 1);
        
        // Clear the ready-to-level notification flag
        clearLevelUpNotification(player);
        
        // Grant permission
        grantLevelPermission(player, currentLevel + 1);
        
        // Execute rewards
        executeRewards(player, nextLevelConfig);
        
        // Send title popup
        sendLevelUpTitle(player, currentLevel + 1);
        
        // Play level up sound
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 0.5f);
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_FIREWORK_ROCKET_LARGE_BLAST, 0.8f, 1.0f);
        
        // Send messages
        String message = plugin.getConfigManager().getRawMessage("level-up")
            .replace("{level}", String.valueOf(currentLevel + 1));
        MessageUtil.send(player, message);
        
        // Show perks unlocked
        showLevelPerks(player, currentLevel + 1, nextLevelConfig);
        
        // Give mystery mask reward
        giveMysteryMask(player, currentLevel + 1);
        
        // Broadcast if enabled
        if (plugin.getConfigManager().isBroadcastLevelUp()) {
            String broadcast = plugin.getConfigManager().getBroadcastMessage()
                .replace("{player}", player.getName())
                .replace("{level}", String.valueOf(currentLevel + 1));
            Bukkit.broadcastMessage(MessageUtil.color(broadcast));
        }
        
        return LevelUpResult.SUCCESS;
    }
    
    /**
     * Grant level permission to player
     */
    private void grantLevelPermission(Player player, int level) {
        // Try LuckPerms first
        if (Bukkit.getPluginManager().getPlugin("LuckPerms") != null) {
            try {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), 
                    "lp user " + player.getName() + " permission set headhunting.level." + level + " true");
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to grant permission via LuckPerms: " + e.getMessage());
            }
        }
    }
    
    /**
     * Execute level up rewards
     */
    private void executeRewards(Player player, LevelConfig config) {
        for (String command : config.getRewards()) {
            String parsed = command.replace("{player}", player.getName());
            
            // Handle msg commands directly to avoid "Console -> player" prefix
            if (parsed.toLowerCase().startsWith("msg ")) {
                // Extract message after "msg playername "
                String[] parts = parsed.split(" ", 3);
                if (parts.length >= 3) {
                    player.sendMessage(MessageUtil.color(parts[2]));
                }
            } else {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsed);
            }
        }
    }
    
    /**
     * Get XP needed for next level
     */
    public int getXpForNextLevel(Player player) {
        PlayerData data = plugin.getDataManager().getPlayerData(player);
        int currentLevel = data.getLevel();
        
        LevelConfig nextConfig = plugin.getConfigManager().getLevelConfig(currentLevel + 1);
        if (nextConfig == null) {
            return 0; // Max level
        }
        
        return nextConfig.getXpRequired();
    }
    
    /**
     * Get money needed for next level
     */
    public double getMoneyForNextLevel(Player player) {
        PlayerData data = plugin.getDataManager().getPlayerData(player);
        int currentLevel = data.getLevel();
        
        LevelConfig nextConfig = plugin.getConfigManager().getLevelConfig(currentLevel + 1);
        if (nextConfig == null) {
            return 0; // Max level
        }
        
        return nextConfig.getMoneyCost();
    }
    
    /**
     * Check if player is max level
     */
    public boolean isMaxLevel(Player player) {
        PlayerData data = plugin.getDataManager().getPlayerData(player);
        return data.getLevel() >= plugin.getConfigManager().getMaxLevel();
    }
    
    /**
     * Get progress percentage to next level
     */
    public double getProgressPercentage(Player player) {
        PlayerData data = plugin.getDataManager().getPlayerData(player);
        int currentLevel = data.getLevel();
        
        LevelConfig currentConfig = plugin.getConfigManager().getLevelConfig(currentLevel);
        LevelConfig nextConfig = plugin.getConfigManager().getLevelConfig(currentLevel + 1);
        
        if (nextConfig == null) {
            return 100.0; // Max level
        }
        
        int currentXp = data.getXp();
        int prevRequired = currentConfig != null ? currentConfig.getXpRequired() : 0;
        int nextRequired = nextConfig.getXpRequired();
        
        int xpIntoLevel = currentXp - prevRequired;
        int xpNeeded = nextRequired - prevRequired;
        
        if (xpNeeded <= 0) return 100.0;
        
        return Math.min(100.0, (xpIntoLevel * 100.0) / xpNeeded);
    }
    
    /**
     * Build a progress bar string
     */
    public String buildProgressBar(Player player) {
        double progress = getProgressPercentage(player);
        int length = 20;
        int filled = (int) (length * progress / 100.0);
        
        StringBuilder bar = new StringBuilder("&8[&a");
        for (int i = 0; i < length; i++) {
            if (i < filled) {
                bar.append("█");
            } else if (i == filled) {
                bar.append("&7");
                bar.append("░");
            } else {
                bar.append("░");
            }
        }
        bar.append("&8]");
        
        return MessageUtil.color(bar.toString());
    }
    
    /**
     * Set player level directly (admin command)
     */
    public void setLevel(Player player, int level) {
        PlayerData data = plugin.getDataManager().getPlayerData(player);
        data.setLevel(level);
        
        // Grant all permissions up to this level
        for (int i = 1; i <= level; i++) {
            grantLevelPermission(player, i);
        }
    }
    
    /**
     * Send level up title to player
     */
    private void sendLevelUpTitle(Player player, int newLevel) {
        player.sendTitle(
            MessageUtil.color("&b&lLEVEL UP!"),
            MessageUtil.color("&7You are now &bLevel " + newLevel),
            10, 60, 20
        );
    }
    
    /**
     * Show perks unlocked at this level
     */
    private void showLevelPerks(Player player, int level, LevelConfig config) {
        // Calculate fishing luck bonus (2% per level)
        int fishingBonus = level * 2;
        
        MessageUtil.sendMultiple(player,
            "&8&m                                        ",
            "&b&lPerks Unlocked:",
            "&7• Fishing Luck: &a+" + fishingBonus + "%",
            "&7• Spawner: &e" + formatMobName(config.getMobType().name()),
            "&8&m                                        "
        );
    }
    
    private String formatMobName(String name) {
        if (name == null) return "Unknown";
        String[] words = name.toLowerCase().split("_");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (word.length() > 0) {
                result.append(Character.toUpperCase(word.charAt(0)))
                      .append(word.substring(1))
                      .append(" ");
            }
        }
        return result.toString().trim();
    }
    
    /**
     * Give a mystery mask reward based on player level
     * Higher level = better quality masks
     */
    private void giveMysteryMask(Player player, int level) {
        Random random = new Random();
        Map<String, MaskConfig> allMasks = plugin.getConfigManager().getMaskConfigs();
        
        if (allMasks.isEmpty()) return;
        
        // Filter masks by level range
        // Level 1-3: masks from levels 1-4
        // Level 4-6: masks from levels 2-7
        // Level 7-9: masks from levels 4-10
        // Level 10+: masks from levels 6-14
        int minMaskLevel = Math.max(1, level - 3);
        int maxMaskLevel = Math.min(14, level + 2);
        
        List<MaskConfig> eligibleMasks = new ArrayList<>();
        for (MaskConfig mask : allMasks.values()) {
            int maskLevel = mask.getAssociatedLevel();
            if (maskLevel >= minMaskLevel && maskLevel <= maxMaskLevel) {
                eligibleMasks.add(mask);
            }
        }
        
        // Fallback to any mask if no eligible ones found
        if (eligibleMasks.isEmpty()) {
            eligibleMasks.addAll(allMasks.values());
        }
        
        // Pick random mask
        MaskConfig chosenMask = eligibleMasks.get(random.nextInt(eligibleMasks.size()));
        
        // Determine mask level (1-3 based on player level)
        int maskLevel = 1;
        if (level >= 10) {
            maskLevel = random.nextInt(3) + 1; // 1-3
        } else if (level >= 6) {
            maskLevel = random.nextInt(2) + 1; // 1-2
        }
        
        // Create and give mask
        ItemStack mask = plugin.getMaskFactory().createMask(chosenMask.getId(), maskLevel);
        
        // Try to add to inventory, drop if full
        if (player.getInventory().firstEmpty() != -1) {
            player.getInventory().addItem(mask);
        } else {
            player.getWorld().dropItemNaturally(player.getLocation(), mask);
        }
        
        // Play mystery sound
        player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_CHEST_OPEN, 1.0f, 1.2f);
        
        // Announce the reward
        String maskName = chosenMask.getDisplayName();
        MessageUtil.sendMultiple(player,
            "&d&l✦ MYSTERY MASK! ✦",
            "&7You received: " + maskName + " &7(Level " + maskLevel + ")"
        );
    }
    
    public enum LevelUpResult {
        SUCCESS,
        ALREADY_MAX,
        NOT_ENOUGH_XP,
        NOT_ENOUGH_MONEY,
        CONFIG_ERROR
    }
}

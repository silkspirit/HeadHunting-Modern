package com.headhunting.commands;

import com.headhunting.HeadHunting;
import com.headhunting.data.LevelConfig;
import com.headhunting.data.PlayerData;
import com.headhunting.managers.LevelManager;
import com.headhunting.utils.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /level command - view level info and level up
 */
public class LevelCommand implements CommandExecutor {
    
    private final HeadHunting plugin;
    
    public LevelCommand(HeadHunting plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }
        
        Player player = (Player) sender;
        
        // /levelup and /rankup go directly to level up
        if (label.equalsIgnoreCase("levelup") || label.equalsIgnoreCase("rankup")) {
            attemptLevelUp(player);
            return true;
        }
        
        if (args.length > 0 && args[0].equalsIgnoreCase("top")) {
            showLeaderboard(player);
            return true;
        }
        
        if (args.length > 0 && args[0].equalsIgnoreCase("up")) {
            attemptLevelUp(player);
            return true;
        }
        
        showLevelInfo(player);
        return true;
    }
    
    private void showLevelInfo(Player player) {
        PlayerData data = plugin.getDataManager().getPlayerData(player);
        LevelManager levelManager = plugin.getLevelManager();
        
        int level = data.getLevel();
        int xp = data.getXp();
        int maxLevel = plugin.getConfigManager().getMaxLevel();
        boolean isMax = levelManager.isMaxLevel(player);
        
        // Get current mob type
        LevelConfig currentLevelConfig = plugin.getConfigManager().getLevelConfig(level);
        String currentMob = currentLevelConfig != null && currentLevelConfig.getMobType() != null 
            ? formatMobName(currentLevelConfig.getMobType().name()) : "None";
        
        MessageUtil.sendMultiple(player,
            "&8&m                                        ",
            "&b&lHeadHunting &7- Level Info",
            "&8&m                                        ",
            "&7Your Level: &b" + level + (isMax ? " &a&lMAX" : ""),
            "&7Current XP: &b" + MessageUtil.formatNumber(xp),
            "&7Current Mob: &e" + currentMob
        );
        
        if (!isMax) {
            int xpNeeded = levelManager.getXpForNextLevel(player);
            double moneyCost = levelManager.getMoneyForNextLevel(player);
            String progressBar = levelManager.buildProgressBar(player);
            
            // Get next level mob
            LevelConfig nextLevelConfig = plugin.getConfigManager().getLevelConfig(level + 1);
            String nextMob = nextLevelConfig != null && nextLevelConfig.getMobType() != null 
                ? formatMobName(nextLevelConfig.getMobType().name()) : "Unknown";
            
            MessageUtil.sendMultiple(player,
                "&7XP Needed: &b" + MessageUtil.formatNumber(xpNeeded),
                "&7Money Cost: &a" + MessageUtil.formatMoney(moneyCost),
                "&7Next Mob: &e" + nextMob,
                "&7Progress: " + progressBar,
                ""
            );
            
            if (xp >= xpNeeded) {
                MessageUtil.sendRaw(player, "&a&lReady to level up! &7Use &e/levelup &7or &e/rankup");
            }
        }
        
        MessageUtil.sendRaw(player, "&8&m                                        ");
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
    
    private void attemptLevelUp(Player player) {
        LevelManager.LevelUpResult result = plugin.getLevelManager().attemptLevelUp(player);
        
        switch (result) {
            case SUCCESS:
                // Message already sent by LevelManager
                break;
            case ALREADY_MAX:
                MessageUtil.send(player, "&cYou are already at max level!");
                break;
            case NOT_ENOUGH_XP:
                MessageUtil.send(player, "&cYou don't have enough XP to level up!");
                break;
            case NOT_ENOUGH_MONEY:
                MessageUtil.send(player, "&cYou don't have enough money to level up!");
                break;
            case CONFIG_ERROR:
                MessageUtil.send(player, "&cConfiguration error. Please contact an admin.");
                break;
        }
    }
    
    private void showLeaderboard(Player player) {
        new com.headhunting.gui.LeaderboardGUI(plugin, player).open();
    }
}

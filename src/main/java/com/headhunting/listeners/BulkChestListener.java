package com.headhunting.listeners;

import com.headhunting.HeadHunting;
import com.headhunting.commands.BulkCommand;
import com.headhunting.data.HeadConfig;
import com.headhunting.data.LevelConfig;
import com.headhunting.data.PlayerData;
import com.headhunting.utils.MessageUtil;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Handles bulk sell/deposit when punching chests
 */
public class BulkChestListener implements Listener {
    
    private final HeadHunting plugin;
    private final BulkCommand bulkCommand;
    
    public BulkChestListener(HeadHunting plugin, BulkCommand bulkCommand) {
        this.plugin = plugin;
        this.bulkCommand = bulkCommand;
    }
    
    @EventHandler(priority = EventPriority.HIGH)
    public void onChestPunch(PlayerInteractEvent event) {
        if (event.getAction() != Action.LEFT_CLICK_BLOCK) {
            return;
        }
        
        if (event.getClickedBlock() == null) {
            return;
        }
        
        Material blockType = event.getClickedBlock().getType();
        if (blockType != Material.CHEST && blockType != Material.TRAPPED_CHEST && 
            blockType != Material.ENDER_CHEST && blockType != Material.HOPPER) {
            return;
        }
        
        Player player = event.getPlayer();
        
        if (bulkCommand.isBulkSellEnabled(player)) {
            event.setCancelled(true);
            processBulkSell(player);
        } else if (bulkCommand.isBulkDepositEnabled(player)) {
            event.setCancelled(true);
            processBulkDeposit(player);
        }
    }
    
    private void processBulkSell(Player player) {
        Economy economy = plugin.getEconomy();
        PlayerData data = plugin.getDataManager().getPlayerData(player);
        int currentLevel = data.getLevel();
        LevelConfig levelConfig = plugin.getConfigManager().getLevelConfig(currentLevel);
        EntityType currentLevelMob = levelConfig != null ? levelConfig.getMobType() : null;
        
        int totalHeads = 0;
        double totalMoney = 0;
        int totalXp = 0;
        int xpHeads = 0;
        
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack slot = player.getInventory().getItem(i);
            if (slot == null || slot.getType() != Material.PLAYER_HEAD) {
                continue;
            }
            
            if (!plugin.getHeadFactory().isHead(slot)) {
                continue;
            }
            
            HeadConfig config = plugin.getHeadFactory().getHeadConfig(slot);
            if (config == null) {
                continue;
            }
            
            int amount = slot.getAmount();
            totalHeads += amount;
            totalMoney += config.getSellPrice() * amount;
            
            // Only give XP for current level's mob type
            if (currentLevelMob != null && config.getEntityType() == currentLevelMob) {
                totalXp += config.getXpValue() * amount;
                xpHeads += amount;
            }
            
            player.getInventory().setItem(i, null);
        }
        
        if (totalHeads == 0) {
            MessageUtil.send(player, "&cNo heads to sell!");
            return;
        }
        
        economy.depositPlayer(player, totalMoney);
        if (totalXp > 0) {
            plugin.getLevelManager().addXp(player, totalXp);
        }
        
        // Track lifetime stats
        data.addHeadsSold(totalHeads);
        data.addHeadsCollected(totalHeads);
        
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);
        if (totalXp > 0) {
            String mobName = formatMobName(currentLevelMob.name());
            MessageUtil.send(player, "&a&lSOLD! &f" + totalHeads + " heads &7→ &a" + 
                MessageUtil.formatMoney(totalMoney) + " &7+ &b" + MessageUtil.formatNumber(totalXp) + " XP &7(" + xpHeads + " " + mobName + ")");
        } else {
            String mobName = currentLevelMob != null ? formatMobName(currentLevelMob.name()) : "Unknown";
            MessageUtil.send(player, "&a&lSOLD! &f" + totalHeads + " heads &7→ &a" + 
                MessageUtil.formatMoney(totalMoney) + " &7(Need &e" + mobName + " &7for XP)");
        }
    }
    
    private void processBulkDeposit(Player player) {
        PlayerData data = plugin.getDataManager().getPlayerData(player);
        int currentLevel = data.getLevel();
        LevelConfig levelConfig = plugin.getConfigManager().getLevelConfig(currentLevel);
        EntityType currentLevelMob = levelConfig != null ? levelConfig.getMobType() : null;
        
        int totalHeads = 0;
        int totalXp = 0;
        int xpHeads = 0;
        
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack slot = player.getInventory().getItem(i);
            if (slot == null || slot.getType() != Material.PLAYER_HEAD) {
                continue;
            }
            
            if (!plugin.getHeadFactory().isHead(slot)) {
                continue;
            }
            
            HeadConfig config = plugin.getHeadFactory().getHeadConfig(slot);
            if (config == null) {
                continue;
            }
            
            int amount = slot.getAmount();
            totalHeads += amount;
            
            // Only give XP for current level's mob type
            if (currentLevelMob != null && config.getEntityType() == currentLevelMob) {
                totalXp += config.getXpValue() * amount;
                xpHeads += amount;
            }
            
            // Track deposit progress for missions
            plugin.getMissionManager().addProgress(player,
                com.headhunting.data.MissionConfig.MissionType.DEPOSIT_HEADS, amount);
            
            player.getInventory().setItem(i, null);
        }
        
        if (totalHeads == 0) {
            MessageUtil.send(player, "&cNo heads to deposit!");
            return;
        }
        
        if (totalXp > 0) {
            plugin.getLevelManager().addXp(player, totalXp);
        }
        
        // Track lifetime stats
        data.addHeadsCollected(totalHeads);
        
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
        if (totalXp > 0) {
            String mobName = formatMobName(currentLevelMob.name());
            MessageUtil.send(player, "&b&lDEPOSITED! &f" + totalHeads + " heads &7→ &b" + 
                MessageUtil.formatNumber(totalXp) + " XP &7(" + xpHeads + " " + mobName + ")");
        } else {
            String mobName = currentLevelMob != null ? formatMobName(currentLevelMob.name()) : "Unknown";
            MessageUtil.send(player, "&b&lDEPOSITED! &f" + totalHeads + " heads &7(Need &e" + mobName + " &7for XP)");
        }
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
}

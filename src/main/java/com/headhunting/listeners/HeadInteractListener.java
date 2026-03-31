package com.headhunting.listeners;

import com.headhunting.HeadHunting;
import com.headhunting.data.HeadConfig;
import com.headhunting.data.LevelConfig;
import com.headhunting.data.PlayerData;
import com.headhunting.utils.MessageUtil;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Handles head interaction events (consume, sell)
 */
public class HeadInteractListener implements Listener {
    
    private final HeadHunting plugin;
    
    public HeadInteractListener(HeadHunting plugin) {
        this.plugin = plugin;
    }
    
    @EventHandler(priority = EventPriority.NORMAL)
    public void onHeadInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        
        if (item == null || item.getType() != Material.PLAYER_HEAD) {
            return;
        }
        
        // Check if it's a HeadHunting head
        if (!plugin.getHeadFactory().isHead(item)) {
            return;
        }
        
        event.setCancelled(true);
        
        HeadConfig config = plugin.getHeadFactory().getHeadConfig(item);
        if (config == null) {
            return;
        }
        
        // Check if shift-clicking (sell ALL) or regular click (consume)
        if (player.isSneaking()) {
            sellAllHeads(player);
        } else {
            consumeHeads(player, item, config);
        }
    }
    
    /**
     * Consume heads for XP + Money
     * XP only given if head matches current level's mob type
     */
    private void consumeHeads(Player player, ItemStack item, HeadConfig config) {
        int amount = item.getAmount();
        double totalMoney = config.getSellPrice() * amount;
        
        // Check if head matches current level's mob type for XP
        PlayerData data = plugin.getDataManager().getPlayerData(player);
        int currentLevel = data.getLevel();
        LevelConfig levelConfig = plugin.getConfigManager().getLevelConfig(currentLevel);
        
        int totalXp = 0;
        boolean xpGranted = false;
        
        if (levelConfig != null && levelConfig.getMobType() != null) {
            EntityType currentLevelMob = levelConfig.getMobType();
            if (config.getEntityType() == currentLevelMob) {
                totalXp = config.getXpValue() * amount;
                plugin.getLevelManager().addXp(player, totalXp);
                xpGranted = true;
            }
        }
        
        // Add Money (always)
        Economy economy = plugin.getEconomy();
        economy.depositPlayer(player, totalMoney);
        
        // Track lifetime stats
        data.addHeadsCollected(amount);
        
        // Track mission progress
        plugin.getMissionManager().addProgress(player, 
            com.headhunting.data.MissionConfig.MissionType.CONSUME_HEADS, amount);
        
        // Remove item from inventory
        player.getInventory().setItemInHand(null);
        
        // Send message
        if (xpGranted) {
            MessageUtil.send(player, "&a+" + MessageUtil.formatNumber(totalXp) + " XP &7+ &a" + MessageUtil.formatMoney(totalMoney));
        } else {
            String mobName = levelConfig != null && levelConfig.getMobType() != null 
                ? formatMobName(levelConfig.getMobType().name()) 
                : "Unknown";
            MessageUtil.send(player, "&a" + MessageUtil.formatMoney(totalMoney) + " &7(No XP - need &e" + mobName + " &7heads for Level " + currentLevel + ")");
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
    
    /**
     * Sell ALL heads in inventory (shift+click)
     * XP only given for heads matching current level's mob type
     */
    private void sellAllHeads(Player player) {
        Economy economy = plugin.getEconomy();
        PlayerData data = plugin.getDataManager().getPlayerData(player);
        int currentLevel = data.getLevel();
        LevelConfig levelConfig = plugin.getConfigManager().getLevelConfig(currentLevel);
        EntityType currentLevelMob = levelConfig != null ? levelConfig.getMobType() : null;
        
        int totalHeads = 0;
        int xpHeads = 0;
        double totalMoney = 0;
        int totalXp = 0;
        
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
            double price = config.getSellPrice() * amount;
            
            totalHeads += amount;
            totalMoney += price;
            
            // Only give XP for current level's mob type
            if (currentLevelMob != null && config.getEntityType() == currentLevelMob) {
                int xp = config.getXpValue() * amount;
                totalXp += xp;
                xpHeads += amount;
            }
            
            // Remove the item
            player.getInventory().setItem(i, null);
        }
        
        if (totalHeads == 0) {
            MessageUtil.send(player, "&cNo heads to sell!");
            return;
        }
        
        // Give money
        economy.depositPlayer(player, totalMoney);
        
        // Give XP (only for matching heads)
        if (totalXp > 0) {
            plugin.getLevelManager().addXp(player, totalXp);
        }
        
        // Track lifetime stats
        data.addHeadsSold(totalHeads);
        data.addHeadsCollected(totalHeads);
        
        // Track mission progress
        plugin.getMissionManager().addProgress(player,
            com.headhunting.data.MissionConfig.MissionType.EARN_MONEY, (int) totalMoney);
        
        // Play sound
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
        
        // Send message
        if (totalXp > 0) {
            MessageUtil.send(player, "&a&lSOLD! &7" + totalHeads + " heads for &a" + MessageUtil.formatMoney(totalMoney) + " &7+ &b" + MessageUtil.formatNumber(totalXp) + " XP &7(" + xpHeads + " " + formatMobName(currentLevelMob.name()) + " heads)");
        } else {
            String mobName = currentLevelMob != null ? formatMobName(currentLevelMob.name()) : "Unknown";
            MessageUtil.send(player, "&a&lSOLD! &7" + totalHeads + " heads for &a" + MessageUtil.formatMoney(totalMoney) + " &7(No XP - need &e" + mobName + " &7heads)");
        }
    }
}

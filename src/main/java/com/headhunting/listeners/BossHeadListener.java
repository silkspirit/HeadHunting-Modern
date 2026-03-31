package com.headhunting.listeners;

import com.headhunting.HeadHunting;
import com.headhunting.data.PlayerData;
import com.headhunting.utils.MessageUtil;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Handles boss head consumption and selling
 */
public class BossHeadListener implements Listener {
    
    private final HeadHunting plugin;
    
    public BossHeadListener(HeadHunting plugin) {
        this.plugin = plugin;
    }
    
    @EventHandler(priority = EventPriority.HIGH)
    public void onBossHeadUse(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        
        if (!isBossHead(item)) {
            return;
        }
        
        event.setCancelled(true);
        
        // Parse XP and sell values from lore
        int xpValue = getXpValue(item);
        int sellValue = getSellValue(item);
        
        if (player.isSneaking()) {
            // Shift-click = sell
            handleSell(player, item, sellValue);
        } else {
            // Right-click = consume for XP
            handleConsume(player, item, xpValue);
        }
    }
    
    private boolean isBossHead(ItemStack item) {
        if (item == null || item.getType() != org.bukkit.Material.PLAYER_HEAD) {
            return false;
        }
        
        if (!item.hasItemMeta() || !item.getItemMeta().hasLore()) {
            return false;
        }
        
        for (String line : item.getItemMeta().getLore()) {
            String stripped = ChatColor.stripColor(line);
            if (stripped.equals("Boss Head")) {
                return true;
            }
        }
        return false;
    }
    
    private int getXpValue(ItemStack item) {
        if (!item.hasItemMeta() || !item.getItemMeta().hasLore()) {
            return 0;
        }
        
        for (String line : item.getItemMeta().getLore()) {
            String stripped = ChatColor.stripColor(line);
            if (stripped.startsWith("XP Value: ")) {
                try {
                    return Integer.parseInt(stripped.replace("XP Value: ", "").trim());
                } catch (NumberFormatException e) {
                    return 0;
                }
            }
        }
        return 0;
    }
    
    private int getSellValue(ItemStack item) {
        if (!item.hasItemMeta() || !item.getItemMeta().hasLore()) {
            return 0;
        }
        
        for (String line : item.getItemMeta().getLore()) {
            String stripped = ChatColor.stripColor(line);
            if (stripped.startsWith("Sell Value: $")) {
                try {
                    String value = stripped.replace("Sell Value: $", "").replace(",", "").trim();
                    return Integer.parseInt(value);
                } catch (NumberFormatException e) {
                    return 0;
                }
            }
        }
        return 0;
    }
    
    private void handleConsume(Player player, ItemStack item, int xpValue) {
        if (xpValue <= 0) {
            MessageUtil.send(player, "&cThis boss head has no XP value!");
            return;
        }
        
        // Remove one item
        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
        } else {
            player.getInventory().setItemInHand(null);
        }
        
        // Give XP
        PlayerData data = plugin.getDataManager().getPlayerData(player);
        data.addXp(xpValue);
        plugin.getDataManager().savePlayerData(player);
        
        // Effects
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
        
        MessageUtil.send(player, "&6&l✦ &7Consumed boss head for &b+" + MessageUtil.formatNumber(xpValue) + " XP&7!");
    }
    
    private void handleSell(Player player, ItemStack item, int sellValue) {
        if (sellValue <= 0) {
            MessageUtil.send(player, "&cThis boss head has no sell value!");
            return;
        }
        
        Economy economy = plugin.getEconomy();
        if (economy == null) {
            MessageUtil.send(player, "&cEconomy not available!");
            return;
        }
        
        // Remove one item
        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
        } else {
            player.getInventory().setItemInHand(null);
        }
        
        // Give money
        economy.depositPlayer(player, sellValue);
        
        // Effects
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
        
        MessageUtil.send(player, "&a&l$ &7Sold boss head for &a$" + MessageUtil.formatNumber(sellValue) + "&7!");
    }
}

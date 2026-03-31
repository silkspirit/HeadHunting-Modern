package com.headhunting.listeners;

import com.headhunting.HeadHunting;
import com.headhunting.utils.MessageUtil;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Handles mystery mask consumption
 */
public class MysteryMaskListener implements Listener {
    
    private final HeadHunting plugin;
    
    public MysteryMaskListener(HeadHunting plugin) {
        this.plugin = plugin;
    }
    
    @EventHandler(priority = EventPriority.HIGH)
    public void onMysteryMaskUse(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        
        if (item == null || !plugin.getMysteryMaskFactory().isMysteryMask(item)) {
            return;
        }
        
        event.setCancelled(true);
        
        String rarity = plugin.getMysteryMaskFactory().getRarity(item);
        if (rarity == null) {
            MessageUtil.send(player, "&cInvalid mystery mask!");
            return;
        }
        
        // Open the mystery mask
        ItemStack mask = plugin.getMysteryMaskFactory().openMysteryMask(player, rarity);
        if (mask == null) {
            MessageUtil.send(player, "&cFailed to open mystery mask!");
            return;
        }
        
        // Remove one mystery mask
        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
        } else {
            player.getInventory().setItemInHand(null);
        }
        
        // Give the mask
        if (player.getInventory().firstEmpty() != -1) {
            player.getInventory().addItem(mask);
        } else {
            player.getWorld().dropItemNaturally(player.getLocation(), mask);
        }
        
        // Get mask name for message
        ItemMeta maskMeta = mask.getItemMeta();
        String maskName = maskMeta != null ? maskMeta.getDisplayName() : "Unknown Mask";
        
        // Play sounds and effects
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
        player.playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LARGE_BLAST, 0.8f, 1.0f);
        
        // Send reveal message
        String rarityColor = getRarityColor(rarity);
        MessageUtil.send(player, rarityColor + "&l✦ MYSTERY MASK OPENED! ✦");
        MessageUtil.send(player, "&7You received: " + maskName);
    }
    
    private String getRarityColor(String rarity) {
        switch (rarity.toLowerCase()) {
            case "common": return "&f";
            case "uncommon": return "&a";
            case "rare": return "&9";
            case "epic": return "&5";
            case "legendary": return "&6";
            case "divine": return "&d";
            default: return "&7";
        }
    }
}

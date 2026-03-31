package com.headhunting.listeners;

import com.headhunting.HeadHunting;
import com.headhunting.boosters.BoosterManager;
import com.headhunting.boosters.BoosterType;
import com.headhunting.utils.MessageUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

/**
 * Handles booster voucher consumption
 */
public class BoosterListener implements Listener {
    
    private final HeadHunting plugin;
    
    public BoosterListener(HeadHunting plugin) {
        this.plugin = plugin;
    }
    
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        
        if (item == null) return;
        
        BoosterManager boosterManager = plugin.getBoosterManager();
        if (!boosterManager.isBoosterItem(item)) {
            return;
        }
        
        event.setCancelled(true);
        
        // Get booster data
        Map<String, Object> data = boosterManager.getBoosterData(item);
        
        if (data.isEmpty()) {
            MessageUtil.send(player, "&cThis booster item appears to be invalid!");
            return;
        }
        
        String typeStr = (String) data.get("type");
        String scope = (String) data.get("scope");
        double multiplier = (double) data.get("multiplier");
        int durationMinutes = (int) data.get("duration");
        
        BoosterType type;
        try {
            type = BoosterType.valueOf(typeStr);
        } catch (Exception e) {
            MessageUtil.send(player, "&cThis booster item has an invalid type!");
            return;
        }
        
        long durationMs = durationMinutes * 60 * 1000L;
        
        boolean success;
        if (scope.equalsIgnoreCase("faction")) {
            success = boosterManager.activateFactionBooster(player, type, multiplier, durationMs);
        } else {
            success = boosterManager.activatePersonalBooster(player, type, multiplier, durationMs);
        }
        
        if (success) {
            // Consume the item
            if (item.getAmount() > 1) {
                item.setAmount(item.getAmount() - 1);
            } else {
                player.setItemInHand(null);
            }
        }
    }
}

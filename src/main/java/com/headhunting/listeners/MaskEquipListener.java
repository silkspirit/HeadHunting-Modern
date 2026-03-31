package com.headhunting.listeners;

import com.headhunting.HeadHunting;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Handles mask equipping/unequipping
 */
public class MaskEquipListener implements Listener {
    
    private final HeadHunting plugin;
    
    public MaskEquipListener(HeadHunting plugin) {
        this.plugin = plugin;
    }
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        
        Player player = (Player) event.getWhoClicked();
        
        // Check if helmet slot was involved
        if (event.getSlotType() == InventoryType.SlotType.ARMOR) {
            // Delay check to allow inventory to update
            new BukkitRunnable() {
                @Override
                public void run() {
                    plugin.getMaskManager().updateEquippedMask(player);
                }
            }.runTaskLater(plugin, 1L);
        }
        
        // Check if clicking on a mask in inventory
        ItemStack clicked = event.getCurrentItem();
        if (clicked != null && plugin.getMaskFactory().isMask(clicked)) {
            // Check for shift-click to equip
            if (event.isShiftClick()) {
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        plugin.getMaskManager().updateEquippedMask(player);
                    }
                }.runTaskLater(plugin, 1L);
            }
        }
    }
    
    @EventHandler(priority = EventPriority.NORMAL)
    public void onMaskRightClick(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        
        if (item == null || item.getType() != Material.PLAYER_HEAD) {
            return;
        }
        
        // Check if it's a mask
        if (!plugin.getMaskFactory().isMask(item)) {
            return;
        }
        
        // Check if player right-clicked to equip
        if (event.getAction().name().contains("RIGHT_CLICK")) {
            event.setCancelled(true);
            
            // Check if helmet slot is empty
            ItemStack currentHelmet = player.getInventory().getHelmet();
            
            if (currentHelmet == null || currentHelmet.getType() == Material.AIR) {
                // Equip the mask
                player.getInventory().setHelmet(item);
                player.getInventory().setItemInHand(null);
                
                plugin.getMaskManager().updateEquippedMask(player);
            } else if (plugin.getMaskFactory().isMask(currentHelmet)) {
                // Swap masks
                ItemStack oldMask = currentHelmet.clone();
                player.getInventory().setHelmet(item);
                player.getInventory().setItemInHand(oldMask);
                
                plugin.getMaskManager().updateEquippedMask(player);
            } else {
                // Can't equip, helmet slot occupied by non-mask
                player.sendMessage(plugin.getConfigManager().getMessage("inventory-full")
                    .replace("inventory", "helmet slot"));
            }
        }
    }
}

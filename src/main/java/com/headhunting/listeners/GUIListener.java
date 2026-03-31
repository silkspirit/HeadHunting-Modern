package com.headhunting.listeners;

import com.headhunting.HeadHunting;
import com.headhunting.gui.CollectorGUI;
import com.headhunting.gui.DepositGUI;
import com.headhunting.gui.LeaderboardGUI;
import com.headhunting.gui.MasksGUI;
import com.headhunting.gui.MissionsGUI;
import com.headhunting.gui.ProgressionGUI;
import com.headhunting.gui.SpawnerShopGUI;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryHolder;

/**
 * Handles GUI click events
 */
public class GUIListener implements Listener {
    
    private final HeadHunting plugin;
    
    public GUIListener(HeadHunting plugin) {
        this.plugin = plugin;
    }
    
    @EventHandler(priority = EventPriority.NORMAL)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        
        InventoryHolder holder = event.getInventory().getHolder();
        
        if (holder instanceof CollectorGUI) {
            event.setCancelled(true);
            ((CollectorGUI) holder).handleClick(event);
        } else if (holder instanceof DepositGUI) {
            event.setCancelled(true);
            ((DepositGUI) holder).handleClick(event);
        } else if (holder instanceof MasksGUI) {
            event.setCancelled(true);
            ((MasksGUI) holder).handleClick(event);
        } else if (holder instanceof MissionsGUI) {
            event.setCancelled(true);
            ((MissionsGUI) holder).handleClick(event);
        } else if (holder instanceof ProgressionGUI) {
            event.setCancelled(true);
            ((ProgressionGUI) holder).handleClick(event);
        } else if (holder instanceof LeaderboardGUI) {
            event.setCancelled(true);
            ((LeaderboardGUI) holder).handleClick(event);
        } else if (holder instanceof SpawnerShopGUI) {
            event.setCancelled(true);
            ((SpawnerShopGUI) holder).handleClick(event);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof SpawnerShopGUI) {
            ((SpawnerShopGUI) holder).handleDrag(event);
        }
    }
    
    @EventHandler(priority = EventPriority.NORMAL)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) {
            return;
        }
        
        InventoryHolder holder = event.getInventory().getHolder();
        
        if (holder instanceof CollectorGUI) {
            ((CollectorGUI) holder).handleClose(event);
        } else if (holder instanceof DepositGUI) {
            ((DepositGUI) holder).handleClose(event);
        } else if (holder instanceof MasksGUI) {
            ((MasksGUI) holder).handleClose(event);
        } else if (holder instanceof MissionsGUI) {
            ((MissionsGUI) holder).handleClose(event);
        }
    }
}

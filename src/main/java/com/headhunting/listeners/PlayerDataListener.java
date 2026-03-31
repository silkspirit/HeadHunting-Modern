package com.headhunting.listeners;

import com.headhunting.HeadHunting;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Handles player join/quit for data loading/saving
 */
public class PlayerDataListener implements Listener {

    private final HeadHunting plugin;

    public PlayerDataListener(HeadHunting plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Load player data asynchronously to avoid blocking the main thread.
        // The callback runs on the main thread once loading is complete.
        plugin.getDataManager().loadPlayerDataAsync(player.getUniqueId(), () -> {
            if (player.isOnline()) {
                plugin.getMaskManager().updateEquippedMask(player);
            }
        });
    }
    
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        
        // Remove passive abilities
        plugin.getMaskManager().removePassiveAbilities(player);
        
        // Clean up ability handler data
        plugin.getAbilityHandler().cleanupPlayer(player.getUniqueId());
        
        // Save and unload player data
        plugin.getDataManager().unloadPlayerData(player);
    }
}

package com.headhunting.listeners;

import com.headhunting.HeadHunting;
import com.headhunting.data.PlayerData;
import com.headhunting.utils.MessageUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

/**
 * Blocks /duel command when player has a mask equipped
 */
public class DuelBlockListener implements Listener {
    
    private final HeadHunting plugin;
    
    public DuelBlockListener(HeadHunting plugin) {
        this.plugin = plugin;
    }
    
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (event.isCancelled()) return;
        
        String message = event.getMessage().toLowerCase();
        
        // Check if it's a duel command
        if (!message.startsWith("/duel")) {
            return;
        }
        
        Player player = event.getPlayer();
        PlayerData data = plugin.getDataManager().getPlayerData(player);
        
        // Check if player has a mask equipped
        if (data.getEquippedMask() != null) {
            event.setCancelled(true);
            MessageUtil.send(player, "&c&l⚠ &cYou must unequip your mask before dueling!");
            MessageUtil.send(player, "&7Use &e/hunt unequip &7to remove your mask.");
        }
    }
}

package com.headhunting.listeners;

import com.headhunting.HeadHunting;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;

/**
 * Handles low health triggered abilities
 */
public class HealthListener implements Listener {
    
    private final HeadHunting plugin;
    
    public HealthListener(HeadHunting plugin) {
        this.plugin = plugin;
    }
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        
        Player player = (Player) event.getEntity();
        
        // Calculate health after damage
        double healthAfter = player.getHealth() - event.getFinalDamage();
        
        // Check for low health boost (Enderman, Skeleton Horse masks)
        if (healthAfter > 0 && healthAfter <= 6) { // 3 hearts or less
            plugin.getAbilityHandler().checkLowHealthBoost(player);
        }
    }
}

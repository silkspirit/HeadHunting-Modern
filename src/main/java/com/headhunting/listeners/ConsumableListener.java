package com.headhunting.listeners;

import com.headhunting.HeadHunting;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.util.Collection;

/**
 * Handles consumable item abilities (Pig mask - potions, gapples)
 */
public class ConsumableListener implements Listener {
    
    private final HeadHunting plugin;
    
    public ConsumableListener(HeadHunting plugin) {
        this.plugin = plugin;
    }
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemConsume(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        
        // Handle golden apples
        if (item.getType() == Material.GOLDEN_APPLE) {
            handleGoldenApple(player, item);
        }
        
        // Handle potions - boost duration
        if (item.getType() == Material.POTION) {
            handlePotion(player);
        }
    }
    
    /**
     * Handle golden apple consumption
     */
    private void handleGoldenApple(Player player, ItemStack item) {
        // Check for gapple return ability
        if (plugin.getAbilityHandler().shouldReturnGapple(player)) {
            // Give back the golden apple
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                ItemStack gapple = new ItemStack(Material.GOLDEN_APPLE, 1, item.getDurability());
                
                if (player.getInventory().firstEmpty() != -1) {
                    player.getInventory().addItem(gapple);
                    String msg = plugin.getConfigManager().getRawMessage("gapple-returned");
                    if (msg.contains("Message not found")) {
                        msg = "§a§lYour golden apple was returned!";
                    }
                    player.sendMessage(msg);
                } else {
                    player.getWorld().dropItemNaturally(player.getLocation(), gapple);
                }
            }, 1L);
        }
        
        // Apply potion duration boost to gapple effects
        double multiplier = plugin.getAbilityHandler().getPotionDurationMultiplier(player);
        if (multiplier > 1.0) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                extendPotionEffects(player, multiplier);
            }, 1L);
        }
    }
    
    /**
     * Handle potion consumption
     */
    private void handlePotion(Player player) {
        double multiplier = plugin.getAbilityHandler().getPotionDurationMultiplier(player);
        if (multiplier <= 1.0) return;
        
        // Delay to let potion effects apply first
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            extendPotionEffects(player, multiplier);
        }, 1L);
    }
    
    /**
     * Extend active potion effects by multiplier
     */
    private void extendPotionEffects(Player player, double multiplier) {
        Collection<PotionEffect> effects = player.getActivePotionEffects();
        
        for (PotionEffect effect : effects) {
            // Calculate new duration
            int newDuration = (int) (effect.getDuration() * multiplier);
            
            // Cap at reasonable max (10 minutes)
            newDuration = Math.min(newDuration, 12000);
            
            // Re-apply with extended duration
            player.addPotionEffect(new PotionEffect(
                effect.getType(),
                newDuration,
                effect.getAmplifier(),
                effect.isAmbient(),
                effect.hasParticles()
            ), true);
        }
    }
}

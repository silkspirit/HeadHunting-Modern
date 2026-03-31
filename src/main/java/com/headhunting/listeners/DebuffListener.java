package com.headhunting.listeners;

import com.headhunting.HeadHunting;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Handles debuff immunity and cleansing abilities
 */
public class DebuffListener implements Listener {
    
    private final HeadHunting plugin;
    
    // Negative effect types
    private static final Set<PotionEffectType> DEBUFFS = new HashSet<>(Arrays.asList(
        PotionEffectType.SLOW,
        PotionEffectType.SLOW_DIGGING,
        PotionEffectType.HARM,
        PotionEffectType.CONFUSION,
        PotionEffectType.BLINDNESS,
        PotionEffectType.HUNGER,
        PotionEffectType.WEAKNESS,
        PotionEffectType.POISON,
        PotionEffectType.WITHER
    ));
    
    public DebuffListener(HeadHunting plugin) {
        this.plugin = plugin;
    }
    
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPotionSplash(PotionSplashEvent event) {
        for (PotionEffect effect : event.getPotion().getEffects()) {
            if (!isDebuff(effect.getType())) continue;
            
            // Check each affected player
            event.getAffectedEntities().stream()
                .filter(entity -> entity instanceof Player)
                .map(entity -> (Player) entity)
                .forEach(player -> {
                    if (plugin.getAbilityHandler().isImmuneToDebuff(player, effect.getType())) {
                        event.setIntensity(player, 0);
                    }
                });
        }
    }
    
    /**
     * Periodic task to remove debuffs player is immune to
     * Called from main plugin on a timer
     */
    public void startDebuffRemovalTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : plugin.getServer().getOnlinePlayers()) {
                    plugin.getAbilityHandler().removeImmunizedDebuffs(player);
                }
            }
        }.runTaskTimer(plugin, 20L, 20L); // Every second
    }
    
    /**
     * Check if an effect type is a debuff
     */
    private boolean isDebuff(PotionEffectType type) {
        return DEBUFFS.contains(type);
    }
}

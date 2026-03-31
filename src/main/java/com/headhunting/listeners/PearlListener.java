package com.headhunting.listeners;

import com.headhunting.HeadHunting;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles ender pearl related abilities (Enderman mask).
 * Uses PlayerTeleportEvent as the single trigger for pearl-land effects
 * to avoid double-proc from both ProjectileHitEvent and PlayerTeleportEvent.
 */
public class PearlListener implements Listener {

    private final HeadHunting plugin;

    // Guard against double-proc: track last pearl land time per player
    private final Map<UUID, Long> lastPearlLand = new ConcurrentHashMap<>();
    private static final long PEARL_LAND_COOLDOWN_MS = 500; // 500ms debounce

    public PearlListener(HeadHunting plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPearlThrow(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof EnderPearl)) return;
        EnderPearl pearl = (EnderPearl) event.getEntity();

        if (!(pearl.getShooter() instanceof Player)) return;
        Player player = (Player) pearl.getShooter();

        // Record pearl throw for cooldown tracking
        plugin.getAbilityHandler().recordPearlThrow(player);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPearlDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        if (!(event.getDamager() instanceof EnderPearl)) return;

        Player player = (Player) event.getEntity();
        EnderPearl pearl = (EnderPearl) event.getDamager();

        // Check if this is self-damage from own pearl
        if (pearl.getShooter() instanceof Player) {
            Player shooter = (Player) pearl.getShooter();
            if (shooter.equals(player)) {
                if (!plugin.getAbilityHandler().shouldTakePearlDamage(player)) {
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onTeleport(PlayerTeleportEvent event) {
        if (event.isCancelled()) return;
        if (event.getCause() != TeleportCause.ENDER_PEARL) return;

        Player player = event.getPlayer();

        // Debounce: prevent double-proc if both ProjectileHit and Teleport fire
        long now = System.currentTimeMillis();
        Long last = lastPearlLand.put(player.getUniqueId(), now);
        if (last != null && (now - last) < PEARL_LAND_COOLDOWN_MS) {
            return;
        }

        // Apply pearl land effects after teleport completes
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                plugin.getAbilityHandler().onPearlLand(player);
            }
        }, 1L);
    }
}

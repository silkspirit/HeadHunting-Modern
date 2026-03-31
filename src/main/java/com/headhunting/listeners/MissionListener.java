package com.headhunting.listeners;

import com.headhunting.HeadHunting;
import com.headhunting.data.MissionConfig.MissionType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.potion.PotionEffectType;

/**
 * Tracks events for mission progress
 */
public class MissionListener implements Listener {
    
    private final HeadHunting plugin;
    
    public MissionListener(HeadHunting plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Track mob kills
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMobKill(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        Player killer = entity.getKiller();
        
        if (killer == null) return;
        if (entity instanceof Player) return;
        
        // Track generic mob kill
        plugin.getMissionManager().addProgress(killer, MissionType.KILL_MOBS, 1);
        
        // Track specific mob kill
        plugin.getMissionManager().addProgress(killer, MissionType.KILL_SPECIFIC_MOB, 1, 
            entity.getType().name());
        
        // Track Darkzone mob kills
        if (isInDarkzone(entity.getLocation())) {
            plugin.getMissionManager().addProgress(killer, MissionType.KILL_DARKZONE_MOBS, 1);
        }
        
        // Daily mission tracking
        if (plugin.getDailyMissionManager() != null) {
            plugin.getDailyMissionManager().addProgress(killer, "KILL_MOBS", 1, null);
            plugin.getDailyMissionManager().addProgress(killer, "KILL_SPECIFIC_MOB", 1, entity.getType().name());
        }
    }
    
    /**
     * Track player kills
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerKill(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        
        if (killer == null) return;
        if (killer.equals(victim)) return;
        
        // Track player kill
        plugin.getMissionManager().addProgress(killer, MissionType.KILL_PLAYERS, 1);
        
        // Track kill while gapple active
        if (killer.hasPotionEffect(PotionEffectType.ABSORPTION) || 
            killer.hasPotionEffect(PotionEffectType.REGENERATION)) {
            plugin.getMissionManager().addProgress(killer, MissionType.KILL_WITH_GAPPLE, 1);
        }
        
        // Track death while wearing mask (for victim)
        String equippedMask = plugin.getDataManager().getPlayerData(victim).getEquippedMask();
        if (equippedMask != null) {
            plugin.getMissionManager().addProgress(victim, MissionType.DIE_WITH_MASK, 1);
        }
        
        // Daily mission tracking
        if (plugin.getDailyMissionManager() != null) {
            plugin.getDailyMissionManager().addProgress(killer, "KILL_PLAYERS", 1, null);
            
            // Track armored player kills
            if (isWearingArmor(victim)) {
                plugin.getDailyMissionManager().addProgress(killer, "KILL_ARMORED_PLAYERS", 1, null);
            }
        }
    }
    
    /**
     * Check if a player is wearing armor (helmet, chestplate, leggings, or boots)
     */
    private boolean isWearingArmor(Player player) {
        org.bukkit.inventory.PlayerInventory inv = player.getInventory();
        return (inv.getHelmet() != null && inv.getHelmet().getType() != Material.AIR) ||
               (inv.getChestplate() != null && inv.getChestplate().getType() != Material.AIR) ||
               (inv.getLeggings() != null && inv.getLeggings().getType() != Material.AIR) ||
               (inv.getBoots() != null && inv.getBoots().getType() != Material.AIR);
    }
    
    /**
     * Track block breaks (including spawners)
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Player player = event.getPlayer();
        
        // Track spawner breaks
        if (block.getType() == Material.SPAWNER) {
            plugin.getMissionManager().addProgress(player, MissionType.BREAK_SPAWNERS, 1);
        }
        
        // Daily mission tracking - general block breaks
        if (plugin.getDailyMissionManager() != null) {
            plugin.getDailyMissionManager().addProgress(player, "BREAK_BLOCKS", 1, null);
            plugin.getDailyMissionManager().addProgress(player, "MINE_BLOCKS", 1, block.getType().name());
        }
    }
    
    /**
     * Check if location is in Darkzone
     */
    private boolean isInDarkzone(Location location) {
        String detection = plugin.getMissionManager().getDarkzoneDetection().toLowerCase();
        
        switch (detection) {
            case "factions":
                return isInDarkzoneFactions(location);
            case "worldguard":
                return isInDarkzoneWorldGuard(location);
            case "world":
                return isInDarkzoneWorld(location);
            default:
                return isInDarkzoneFactions(location);
        }
    }
    
    /**
     * Check Factions for Darkzone
     */
    private boolean isInDarkzoneFactions(Location location) {
        String darkzoneTag = plugin.getMissionManager().getDarkzoneFactionTag().toLowerCase();
        
        try {
            Class<?> boardClass = Class.forName("com.massivecraft.factions.Board");
            Object board = boardClass.getMethod("getInstance").invoke(null);
            
            Class<?> flocationClass = Class.forName("com.massivecraft.factions.FLocation");
            Object flocation = flocationClass.getConstructor(Location.class).newInstance(location);
            
            Object faction = boardClass.getMethod("getFactionAt", flocationClass).invoke(board, flocation);
            
            if (faction != null) {
                // Check by tag
                String tag = (String) faction.getClass().getMethod("getTag").invoke(faction);
                if (tag != null && tag.toLowerCase().contains(darkzoneTag)) return true;
                
                // Check by id
                String id = (String) faction.getClass().getMethod("getId").invoke(faction);
                if (id != null && id.toLowerCase().contains(darkzoneTag)) return true;
            }
        } catch (Exception e) {
            // Silent fail
        }
        
        return false;
    }
    
    /**
     * Check WorldGuard for Darkzone
     */
    private boolean isInDarkzoneWorldGuard(Location location) {
        String regionName = plugin.getMissionManager().getDarkzoneWorldGuardRegion();
        
        try {
            Class<?> wgClass = Class.forName("com.sk89q.worldguard.bukkit.WorldGuardPlugin");
            Object wgPlugin = Bukkit.getPluginManager().getPlugin("WorldGuard");
            
            if (wgPlugin == null) return false;
            
            Object regionManager = wgClass.getMethod("getRegionManager", org.bukkit.World.class)
                .invoke(wgPlugin, location.getWorld());
            
            if (regionManager == null) return false;
            
            Class<?> vectorClass = Class.forName("com.sk89q.worldedit.Vector");
            Object vector = vectorClass.getConstructor(double.class, double.class, double.class)
                .newInstance(location.getX(), location.getY(), location.getZ());
            
            Object applicableRegions = regionManager.getClass()
                .getMethod("getApplicableRegions", vectorClass)
                .invoke(regionManager, vector);
            
            Iterable<?> regions = (Iterable<?>) applicableRegions;
            for (Object region : regions) {
                String id = (String) region.getClass().getMethod("getId").invoke(region);
                if (id.equalsIgnoreCase(regionName)) {
                    return true;
                }
            }
        } catch (Exception e) {
            // Silent fail
        }
        
        return false;
    }
    
    /**
     * Check world name for Darkzone
     */
    private boolean isInDarkzoneWorld(Location location) {
        String worldName = plugin.getMissionManager().getDarkzoneWorldName();
        return location.getWorld().getName().equalsIgnoreCase(worldName);
    }
}

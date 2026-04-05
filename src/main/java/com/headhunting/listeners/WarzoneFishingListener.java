package com.headhunting.listeners;

import com.headhunting.HeadHunting;
import com.headhunting.data.FishingReward;
import com.headhunting.utils.MessageUtil;
import com.headhunting.utils.TitleUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Handles warzone fishing with HeadHunting integration
 */
public class WarzoneFishingListener implements Listener {
    
    private final HeadHunting plugin;
    
    public WarzoneFishingListener(HeadHunting plugin) {
        this.plugin = plugin;
    }
    
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) {
            return;
        }
        
        // Check if fishing is enabled
        if (!plugin.getFishingManager().isEnabled()) {
            return;
        }
        
        Player player = event.getPlayer();
        Location hookLocation = event.getHook().getLocation();
        
        // Check if in warzone
        if (!isInWarzone(hookLocation)) {
            return;
        }
        
        // Check permission
        if (!player.hasPermission("headhunting.fish")) {
            return;
        }
        
        // Get random reward
        FishingReward reward = plugin.getFishingManager().getRandomReward(player);
        if (reward == null) {
            return;
        }
        
        // Cancel default catch
        if (event.getCaught() instanceof Item) {
            event.getCaught().remove();
        }
        
        // Note: Mission progress for FISH_CATCHES is tracked globally in MissionListener.
        // Fishing boost event now affects CATCH RATE only (fish bite faster),
        // not drop rates or reward quantities. WarzoneFishing handles the NMS hook
        // wait time reduction. Rewards here use base values with no multiplier.
        boolean isBoosted = plugin.getFishingManager().isBoostActive();
        
        // Handle XP type reward (base amount, no boost multiplier)
        if (reward.getRewardType() == FishingReward.RewardType.XP) {
            int xpAmount = reward.getXpAmount();
            plugin.getLevelManager().addXp(player, xpAmount);
        }
        
        // Create and give item (if applicable, base amounts)
        ItemStack rewardItem = null;
        if (reward.getRewardType() != FishingReward.RewardType.COMMAND) {
            rewardItem = reward.createItemStack(plugin);
            
            if (rewardItem != null) {
                if (plugin.getFishingManager().isDropAtHook()) {
                    hookLocation.getWorld().dropItemNaturally(hookLocation, rewardItem);
                } else {
                    if (player.getInventory().firstEmpty() != -1) {
                        player.getInventory().addItem(rewardItem);
                    } else {
                        player.getWorld().dropItemNaturally(player.getLocation(), rewardItem);
                        MessageUtil.send(player, "&eYour inventory was full! Item dropped at your feet.");
                    }
                }
            }
        }
        
        // Show boost indicator (catch rate boost is active — fish bite faster)
        if (isBoosted) {
            double catchMultiplier = plugin.getFishingManager().getBoostMultiplier();
            String boostDisplay = catchMultiplier == Math.floor(catchMultiplier) 
                ? String.valueOf((int) catchMultiplier) 
                : String.format("%.1f", catchMultiplier);
            MessageUtil.send(player, "&b&l⚡ " + boostDisplay + "x CATCH SPEED! &7Fish are biting faster!");
        }
        
        // Get display name for messages
        String itemName = rewardItem != null && rewardItem.hasItemMeta() && rewardItem.getItemMeta().hasDisplayName()
            ? rewardItem.getItemMeta().getDisplayName()
            : (rewardItem != null ? rewardItem.getType().name() : "Reward");
        
        // Send title
        String title = reward.getTitleMessage()
            .replace("{player}", player.getName())
            .replace("{item}", itemName)
            .replace("{rarity}", reward.getRarity());
        
        String subtitle = reward.getSubtitleMessage()
            .replace("{player}", player.getName())
            .replace("{item}", itemName)
            .replace("{rarity}", reward.getRarity());
        
        TitleUtil.sendTitle(player, title, subtitle,
            plugin.getFishingManager().getTitleFadeIn(),
            plugin.getFishingManager().getTitleStay(),
            plugin.getFishingManager().getTitleFadeOut());
        
        // Play sound
        if (reward.getSound() != null) {
            player.playSound(player.getLocation(), reward.getSound(),
                reward.getSoundVolume(), reward.getSoundPitch());
        }
        
        // Execute commands
        for (String command : reward.getCommands()) {
            String parsed = command
                .replace("{player}", player.getName())
                .replace("{item}", itemName)
                .replace("{rarity}", reward.getRarity());
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsed);
        }
        
        // Broadcast
        if (reward.shouldBroadcast()) {
            String broadcast = reward.getBroadcastMessage()
                .replace("{player}", player.getName())
                .replace("{item}", itemName)
                .replace("{rarity}", reward.getRarity());
            Bukkit.broadcastMessage(broadcast);
        }
        
        // Action bar
        String actionBar = plugin.getFishingManager().getActionBarMessage();
        if (actionBar != null && !actionBar.isEmpty()) {
            String parsedActionBar = MessageUtil.color(actionBar)
                .replace("{player}", player.getName())
                .replace("{item}", itemName)
                .replace("{rarity}", reward.getRarity());
            TitleUtil.sendActionBar(player, parsedActionBar);
        }
    }
    
    /**
     * Check if location is in warzone
     */
    private boolean isInWarzone(Location location) {
        String claimPlugin = plugin.getFishingManager().getClaimPlugin().toLowerCase();
        
        switch (claimPlugin) {
            case "factions":
            case "factionsuuid":
                return isInFactionsWarzone(location);
            case "worldguard":
                return isInWorldGuardRegion(location);
            case "none":
                return isInConfiguredWorld(location);
            default:
                return isInFactionsWarzone(location);
        }
    }
    
    /**
     * Check Factions warzone
     */
    private boolean isInFactionsWarzone(Location location) {
        try {
            Class<?> boardClass = Class.forName("com.massivecraft.factions.Board");
            Object board = boardClass.getMethod("getInstance").invoke(null);
            
            Class<?> flocationClass = Class.forName("com.massivecraft.factions.FLocation");
            Object flocation = flocationClass.getConstructor(Location.class).newInstance(location);
            
            Object faction = boardClass.getMethod("getFactionAt", flocationClass).invoke(board, flocation);
            
            if (faction != null) {
                // Try isWarZone method
                try {
                    Boolean isWarzone = (Boolean) faction.getClass().getMethod("isWarZone").invoke(faction);
                    if (isWarzone != null && isWarzone) return true;
                } catch (NoSuchMethodException e) {
                    // Try tag/id check
                }
                
                // Check by tag
                String tag = (String) faction.getClass().getMethod("getTag").invoke(faction);
                if (tag != null && tag.toLowerCase().contains("warzone")) return true;
                
                // Check by id
                String id = (String) faction.getClass().getMethod("getId").invoke(faction);
                if (id != null && id.toLowerCase().contains("warzone")) return true;
            }
        } catch (ClassNotFoundException e) {
            // Factions not found
        } catch (Exception e) {
            if (plugin.getConfigManager().isDebugEnabled()) {
                plugin.getLogger().warning("Error checking Factions warzone: " + e.getMessage());
            }
        }
        
        return false;
    }
    
    /**
     * Check WorldGuard region
     */
    private boolean isInWorldGuardRegion(Location location) {
        String regionName = plugin.getFishingManager().getWorldGuardRegion();
        
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
            if (plugin.getConfigManager().isDebugEnabled()) {
                plugin.getLogger().warning("Error checking WorldGuard region: " + e.getMessage());
            }
        }
        
        return false;
    }
    
    /**
     * Check if in configured world
     */
    private boolean isInConfiguredWorld(Location location) {
        java.util.List<String> worlds = plugin.getFishingManager().getAllowedWorlds();
        if (worlds.isEmpty()) {
            return true;
        }
        return worlds.contains(location.getWorld().getName());
    }
}

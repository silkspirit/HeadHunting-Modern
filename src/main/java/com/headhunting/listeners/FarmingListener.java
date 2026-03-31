package com.headhunting.listeners;

import com.headhunting.HeadHunting;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

/**
 * Handles farming related abilities (Sheep mask).
 * Ported to 1.20.4 — uses BlockData/Ageable, modern Material names.
 */
public class FarmingListener implements Listener {

    private final HeadHunting plugin;

    // Map crop blocks to their 1.20.4 drop materials
    private static final Map<Material, Material> CROP_DROPS = new HashMap<>();

    static {
        CROP_DROPS.put(Material.WHEAT,        Material.WHEAT);
        CROP_DROPS.put(Material.CARROTS,      Material.CARROT);
        CROP_DROPS.put(Material.POTATOES,     Material.POTATO);
        CROP_DROPS.put(Material.NETHER_WART,  Material.NETHER_WART);
        CROP_DROPS.put(Material.COCOA,        Material.COCOA_BEANS);
    }

    public FarmingListener(HeadHunting plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCropBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();

        // Handle sugarcane separately (always harvestable)
        if (block.getType() == Material.SUGAR_CANE) {
            if (plugin.getDailyMissionManager() != null) {
                plugin.getDailyMissionManager().addProgress(player, "HARVEST_CROPS", 1, "SUGAR_CANE");
            }
            return;
        }

        // Handle cactus
        if (block.getType() == Material.CACTUS) {
            if (plugin.getDailyMissionManager() != null) {
                plugin.getDailyMissionManager().addProgress(player, "HARVEST_CROPS", 1, "CACTUS");
            }
            return;
        }

        // Check if it's a mature crop
        if (!isMatureCrop(block)) {
            return;
        }

        // Track daily mission - harvest crops
        if (plugin.getDailyMissionManager() != null) {
            String cropType = getCropType(block.getType());
            plugin.getDailyMissionManager().addProgress(player, "HARVEST_CROPS", 1, cropType);
        }

        // Check for bonus crops (Sheep mask ability)
        int bonus = plugin.getAbilityHandler().getBonusCrops(player);
        if (bonus > 0) {
            Material dropType = CROP_DROPS.get(block.getType());
            if (dropType != null) {
                ItemStack bonusDrop = new ItemStack(dropType, bonus);
                block.getWorld().dropItemNaturally(block.getLocation(), bonusDrop);
            }
        }
    }

    /**
     * Get the crop type name for mission tracking
     */
    private String getCropType(Material material) {
        switch (material) {
            case WHEAT:       return "WHEAT";
            case CARROTS:     return "CARROT";
            case POTATOES:    return "POTATO";
            case NETHER_WART: return "NETHER_WART";
            case COCOA:       return "COCOA";
            case MELON:       return "MELON";
            case PUMPKIN:     return "PUMPKIN";
            default:          return material.name();
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFarmlandTrample(PlayerInteractEvent event) {
        if (event.getAction() != Action.PHYSICAL) return;

        Block block = event.getClickedBlock();
        if (block == null) return;

        if (block.getType() != Material.FARMLAND) return;

        Player player = event.getPlayer();

        // Check if player can trample crops
        if (!plugin.getAbilityHandler().canTrampleCrops(player)) {
            event.setCancelled(true);
        }
    }

    /**
     * Check if a block is a mature crop using BlockData/Ageable API (1.13+).
     */
    private boolean isMatureCrop(Block block) {
        Material type = block.getType();

        switch (type) {
            case WHEAT:
            case CARROTS:
            case POTATOES:
            case NETHER_WART:
            case COCOA: {
                BlockData data = block.getBlockData();
                if (data instanceof Ageable) {
                    Ageable ageable = (Ageable) data;
                    return ageable.getAge() >= ageable.getMaximumAge();
                }
                return false;
            }
            case MELON:
            case PUMPKIN:
                return true;
            default:
                return false;
        }
    }
}

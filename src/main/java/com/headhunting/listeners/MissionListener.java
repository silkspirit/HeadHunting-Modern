package com.headhunting.listeners;

import com.headhunting.HeadHunting;
import com.headhunting.data.MissionConfig.MissionType;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.BrewEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTameEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.FurnaceExtractEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.BrewerInventory;
import org.bukkit.inventory.ItemStack;

/**
 * Tracks events for mission progress - vanilla SMP mechanics
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

        // Daily mission tracking
        if (plugin.getDailyMissionManager() != null) {
            plugin.getDailyMissionManager().addProgress(killer, "KILL_PLAYERS", 1, null);
        }
    }

    /**
     * Track block breaks (mining)
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Player player = event.getPlayer();

        // Track general mining
        plugin.getMissionManager().addProgress(player, MissionType.MINE_BLOCKS, 1);

        // Track specific block mining
        plugin.getMissionManager().addProgress(player, MissionType.MINE_SPECIFIC_BLOCK, 1,
            block.getType().name());

        // Track crop harvesting
        if (isCrop(block.getType())) {
            plugin.getMissionManager().addProgress(player, MissionType.HARVEST_CROPS, 1);
            if (plugin.getDailyMissionManager() != null) {
                plugin.getDailyMissionManager().addProgress(player, "HARVEST_CROPS", 1, block.getType().name());
            }
        }

        // Daily mission tracking
        if (plugin.getDailyMissionManager() != null) {
            plugin.getDailyMissionManager().addProgress(player, "MINE_BLOCKS", 1, null);
            plugin.getDailyMissionManager().addProgress(player, "MINE_SPECIFIC_BLOCK", 1, block.getType().name());
        }
    }

    /**
     * Track block placement
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();

        plugin.getMissionManager().addProgress(player, MissionType.PLACE_BLOCKS, 1);

        if (plugin.getDailyMissionManager() != null) {
            plugin.getDailyMissionManager().addProgress(player, "PLACE_BLOCKS", 1, null);
        }
    }

    /**
     * Track fishing (anywhere, not just warzone)
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;

        Player player = event.getPlayer();

        plugin.getMissionManager().addProgress(player, MissionType.FISH_CATCHES, 1);

        if (plugin.getDailyMissionManager() != null) {
            plugin.getDailyMissionManager().addProgress(player, "FISH_CATCHES", 1, null);
        }
    }

    /**
     * Track animal breeding
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreed(EntityBreedEvent event) {
        if (!(event.getBreeder() instanceof Player)) return;
        Player player = (Player) event.getBreeder();

        plugin.getMissionManager().addProgress(player, MissionType.BREED_ANIMALS, 1);

        if (plugin.getDailyMissionManager() != null) {
            plugin.getDailyMissionManager().addProgress(player, "BREED_ANIMALS", 1, null);
        }
    }

    /**
     * Track animal taming
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTame(EntityTameEvent event) {
        if (!(event.getOwner() instanceof Player)) return;
        Player player = (Player) event.getOwner();

        plugin.getMissionManager().addProgress(player, MissionType.TAME_ANIMALS, 1);

        if (plugin.getDailyMissionManager() != null) {
            plugin.getDailyMissionManager().addProgress(player, "TAME_ANIMALS", 1, null);
        }
    }

    /**
     * Track enchanting items
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEnchant(EnchantItemEvent event) {
        Player player = event.getEnchanter();

        plugin.getMissionManager().addProgress(player, MissionType.ENCHANT_ITEMS, 1);

        if (plugin.getDailyMissionManager() != null) {
            plugin.getDailyMissionManager().addProgress(player, "ENCHANT_ITEMS", 1, null);
        }
    }

    /**
     * Track brewing potions
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBrew(BrewEvent event) {
        // BrewEvent doesn't have a direct player reference, so we check
        // who has the brewing stand inventory open
        BrewerInventory inv = event.getContents();
        if (inv.getViewers().isEmpty()) return;

        if (inv.getViewers().get(0) instanceof Player) {
            Player player = (Player) inv.getViewers().get(0);

            // Count how many potions were brewed (up to 3 slots)
            int brewed = 0;
            for (int i = 0; i < 3; i++) {
                ItemStack item = inv.getItem(i);
                if (item != null && item.getType() != Material.AIR) {
                    brewed++;
                }
            }

            if (brewed > 0) {
                plugin.getMissionManager().addProgress(player, MissionType.BREW_POTIONS, brewed);

                if (plugin.getDailyMissionManager() != null) {
                    plugin.getDailyMissionManager().addProgress(player, "BREW_POTIONS", brewed, null);
                }
            }
        }
    }

    /**
     * Track furnace smelting
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSmelt(FurnaceExtractEvent event) {
        Player player = event.getPlayer();
        int amount = event.getItemAmount();

        plugin.getMissionManager().addProgress(player, MissionType.SMELT_ITEMS, amount);

        if (plugin.getDailyMissionManager() != null) {
            plugin.getDailyMissionManager().addProgress(player, "SMELT_ITEMS", amount, null);
        }
    }

    /**
     * Track villager trading
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVillagerTrade(InventoryClickEvent event) {
        if (event.getInventory().getType() != InventoryType.MERCHANT) return;
        if (!(event.getWhoClicked() instanceof Player)) return;
        if (event.getSlotType() != InventoryType.SlotType.RESULT) return;
        if (event.getCurrentItem() == null || event.getCurrentItem().getType() == Material.AIR) return;

        Player player = (Player) event.getWhoClicked();

        plugin.getMissionManager().addProgress(player, MissionType.TRADE_VILLAGERS, 1);

        if (plugin.getDailyMissionManager() != null) {
            plugin.getDailyMissionManager().addProgress(player, "TRADE_VILLAGERS", 1, null);
        }
    }

    /**
     * Track eating food
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEat(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        Material type = event.getItem().getType();

        if (type.isEdible()) {
            plugin.getMissionManager().addProgress(player, MissionType.EAT_FOOD, 1);

            if (plugin.getDailyMissionManager() != null) {
                plugin.getDailyMissionManager().addProgress(player, "EAT_FOOD", 1, null);
            }
        }
    }

    /**
     * Check if a material is a crop
     */
    private boolean isCrop(Material material) {
        switch (material) {
            case WHEAT:
            case CARROTS:
            case POTATOES:
            case BEETROOTS:
            case NETHER_WART:
            case SUGAR_CANE:
            case MELON:
            case PUMPKIN:
            case COCOA:
            case SWEET_BERRY_BUSH:
            case BAMBOO:
                return true;
            default:
                return false;
        }
    }
}

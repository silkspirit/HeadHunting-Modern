package com.headhunting.listeners;

import com.headhunting.HeadHunting;
import com.headhunting.data.PlayerData;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Handles mask equipping/unequipping
 */
public class MaskEquipListener implements Listener {

    private final HeadHunting plugin;

    public MaskEquipListener(HeadHunting plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getWhoClicked();

        // Check if helmet slot was involved
        if (event.getSlotType() == InventoryType.SlotType.ARMOR) {
            scheduleAbilityUpdate(player);
        }

        // Check if clicking on a mask in inventory (shift-click to equip)
        ItemStack clicked = event.getCurrentItem();
        if (clicked != null && plugin.getMaskFactory().isMask(clicked)) {
            if (event.isShiftClick()) {
                scheduleAbilityUpdate(player);
            }
        }

        // Check if item is being moved out of hand slots (hotbar slots 0-8, or off-hand slot 40)
        int rawSlot = event.getSlot();
        int heldSlot = player.getInventory().getHeldItemSlot();
        if (rawSlot == heldSlot || rawSlot == 40) {
            scheduleAbilityUpdate(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        plugin.getMaskManager().removePassiveAbilities(player);
        PlayerData data = plugin.getDataManager().getPlayerData(player);
        data.setEquippedMask(null);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        // Delay to allow inventory to be restored
        scheduleAbilityUpdate(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        plugin.getMaskManager().removePassiveAbilities(player);
        PlayerData data = plugin.getDataManager().getPlayerData(player);
        data.setEquippedMask(null);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        // Delay to allow inventory to load
        scheduleAbilityUpdate(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onHotbarSwitch(PlayerItemHeldEvent event) {
        // Player switched held item slot — may have moved away from a held mask
        scheduleAbilityUpdate(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSwapHandItems(PlayerSwapHandItemsEvent event) {
        // Player pressed F to swap main/off hand
        scheduleAbilityUpdate(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onMaskRightClick(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (item == null || item.getType() != Material.PLAYER_HEAD) {
            return;
        }

        // Check if it's a mask
        if (!plugin.getMaskFactory().isMask(item)) {
            return;
        }

        // Check if player right-clicked to equip
        if (event.getAction().name().contains("RIGHT_CLICK")) {
            event.setCancelled(true);

            // Check if helmet slot is empty
            ItemStack currentHelmet = player.getInventory().getHelmet();

            if (currentHelmet == null || currentHelmet.getType() == Material.AIR) {
                // Equip the mask
                player.getInventory().setHelmet(item);
                player.getInventory().setItemInHand(null);

                plugin.getMaskManager().updateEquippedMask(player);
            } else if (plugin.getMaskFactory().isMask(currentHelmet)) {
                // Swap masks
                ItemStack oldMask = currentHelmet.clone();
                player.getInventory().setHelmet(item);
                player.getInventory().setItemInHand(oldMask);

                plugin.getMaskManager().updateEquippedMask(player);
            } else {
                // Can't equip, helmet slot occupied by non-mask
                player.sendMessage(plugin.getConfigManager().getMessage("inventory-full")
                    .replace("inventory", "helmet slot"));
            }
        }
    }

    /**
     * Schedule a delayed ability update to allow inventory changes to complete
     */
    private void scheduleAbilityUpdate(Player player) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (player.isOnline()) {
                    plugin.getMaskManager().updateEquippedMask(player);
                    plugin.getMaskManager().checkAndUpdateAbilities(player);
                }
            }
        }.runTaskLater(plugin, 1L);
    }
}

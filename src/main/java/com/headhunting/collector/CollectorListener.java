package com.headhunting.collector;

import com.headhunting.HeadHunting;
import com.headhunting.data.HeadConfig;
import com.headhunting.gui.CollectorGUI;
import com.headhunting.utils.MessageUtil;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Listens for:
 * <ul>
 *   <li>Block place — register a collector if the item placed is a collector item</li>
 *   <li>Block break — remove a collector block and eject its contents</li>
 *   <li>Player right-click — open the collector GUI</li>
 *   <li>ItemSpawnEvent — auto-collect mob heads that land in a chunk with a collector</li>
 * </ul>
 */
public class CollectorListener implements Listener {

    private final HeadHunting plugin;

    public CollectorListener(HeadHunting plugin) {
        this.plugin = plugin;
    }

    // ── Block placement ───────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        ItemStack hand = event.getItemInHand();
        if (!CollectorItem.isCollectorItem(hand)) return;

        Block block = event.getBlockPlaced();
        Location loc = block.getLocation();

        plugin.getCollectorManager().place(loc);
        MessageUtil.send(event.getPlayer(), "&aHead Collector placed! Mob heads dropped in this chunk will be auto-collected.");
    }

    // ── Block break ───────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        CollectorData cd = plugin.getCollectorManager().getAt(block.getLocation());
        if (cd == null) return;

        Player player = event.getPlayer();
        if (!player.hasPermission("headhunting.collector.use")) {
            MessageUtil.send(player, "&cYou don't have permission to break a Head Collector.");
            event.setCancelled(true);
            return;
        }

        // Stop vanilla skull drop; we handle it ourselves
        event.setExpToDrop(0);
        event.setCancelled(true);
        block.setType(Material.AIR);

        // Remove from registry
        plugin.getCollectorManager().remove(block.getLocation());

        // Return the collector item and all stored heads to the player (or drop at location)
        player.getInventory().addItem(CollectorItem.create())
              .values()
              .forEach(leftover -> block.getWorld().dropItemNaturally(block.getLocation(), leftover));

        // Eject stored heads
        for (java.util.Map.Entry<String, Integer> entry : cd.getHeads().entrySet()) {
            String headKey = entry.getKey();
            int count = entry.getValue();
            if (count <= 0) continue;
            HeadConfig hc = plugin.getConfigManager().getHeadConfig(headKey);
            if (hc == null) continue;
            // Drop in stacks of 64
            while (count > 0) {
                int stackSize = Math.min(count, 64);
                ItemStack head = plugin.getHeadFactory().createHead(headKey, stackSize);
                if (head != null) {
                    block.getWorld().dropItemNaturally(block.getLocation(), head);
                }
                count -= stackSize;
            }
        }

        MessageUtil.send(player, "&eHead Collector broken! Contents ejected.");
    }

    // ── Right-click to open GUI ───────────────────────────────────────────────

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null) return;

        CollectorData cd = plugin.getCollectorManager().getAt(block.getLocation());
        if (cd == null) return;

        Player player = event.getPlayer();
        event.setCancelled(true);

        if (!player.hasPermission("headhunting.collector.use")) {
            MessageUtil.send(player, "&cYou don't have permission to use a Head Collector.");
            return;
        }

        // Factions territory check (only when enabled in config)
        if (plugin.getConfigManager().isCollectorTerritoryCheckEnabled()) {
            if (!checkFactionsAccess(player, block.getLocation())) {
                MessageUtil.send(player, "&cYou can only access collectors in your faction's territory.");
                return;
            }
        }

        new CollectorGUI(plugin, player, cd).open();
    }

    // ── Auto-collect item drops ───────────────────────────────────────────────

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent event) {
        ItemStack stack = event.getEntity().getItemStack();
        if (!isCollectableHead(stack)) return;

        Item entity = event.getEntity();
        Chunk chunk = entity.getLocation().getChunk();
        CollectorData cd = plugin.getCollectorManager().getInChunk(chunk);
        if (cd == null) return;

        String headKey = plugin.getHeadFactory().getHeadKey(stack);
        if (headKey == null) return;

        cd.addHeads(headKey, stack.getAmount());
        plugin.getCollectorManager().save();

        // Cancel the item entity so it never hits the ground
        event.setCancelled(true);

        if (plugin.getConfigManager().isDebugEnabled()) {
            plugin.getLogger().info("[Collector] Auto-collected " + stack.getAmount()
                    + "x " + headKey + " head into collector at "
                    + cd.getLocation().toVector());
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Returns true if the item is a plain HeadHunting consumable mob head —
     * i.e. it contains the HEAD_NBT_KEY marker but NOT the MASK_NBT_KEY marker,
     * and NOT the MysteryMask/SpiritEssence markers.
     */
    /**
     * FactionsUUID access check: player must be in their own faction territory.
     * Only called when integrations.factions.collector-territory-check is enabled.
     */
    private boolean checkFactionsAccess(Player player, Location loc) {
        try {
            Class<?> boardClass = Class.forName("com.massivecraft.factions.Board");
            Object boardInstance = boardClass.getMethod("getInstance").invoke(null);

            Class<?> flocationClass = Class.forName("com.massivecraft.factions.FLocation");
            Object flocation = flocationClass.getConstructor(Location.class).newInstance(loc);

            Object landFaction = boardClass.getMethod("getFactionAt", flocationClass)
                    .invoke(boardInstance, flocation);

            if (landFaction == null) return true;

            boolean isWilderness = (boolean) landFaction.getClass()
                    .getMethod("isWilderness").invoke(landFaction);
            if (isWilderness) return true;

            Class<?> fplayersClass = Class.forName("com.massivecraft.factions.FPlayers");
            Object fplayersInstance = fplayersClass.getMethod("getInstance").invoke(null);
            Object fp = fplayersClass.getMethod("getByPlayer", Player.class)
                    .invoke(fplayersInstance, player);
            if (fp == null) return false;

            Object playerFaction = fp.getClass().getMethod("getFaction").invoke(fp);
            return landFaction.equals(playerFaction);

        } catch (ClassNotFoundException | NoClassDefFoundError e) {
            return true; // Factions not installed
        } catch (Exception e) {
            return true; // Default to allow
        }
    }

    private boolean isCollectableHead(ItemStack item) {
        if (item == null || item.getType() != Material.PLAYER_HEAD) return false;
        if (!item.hasItemMeta() || !item.getItemMeta().hasLore()) return false;

        boolean hasHeadKey = false;
        for (String line : item.getItemMeta().getLore()) {
            String stripped = MessageUtil.stripColor(line);
            // Must contain our head marker
            if (stripped.contains("HeadHunting_Head:")) {
                hasHeadKey = true;
            }
            // Must NOT be a mask, mystery mask, or ability item
            if (stripped.contains("HeadHunting_Mask")
                    || stripped.contains("HeadHunting_MysteryMask")
                    || stripped.contains("HeadHunting_Essence")
                    || stripped.contains("HeadHunting_OmegaEssence")
                    || stripped.contains("HeadHunting_BossHead")) {
                return false;
            }
        }
        return hasHeadKey;
    }

}

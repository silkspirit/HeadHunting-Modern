package com.headhunting.listeners;

import com.headhunting.HeadHunting;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Iterator;

/**
 * Filters vanilla mob drops for custom-named (FKore) mobs in the darkzone world.
 * Runs at HIGHEST priority to strip vanilla drops after FKore has added its custom drops.
 * Only removes items without custom display names (rotten flesh, bones, arrows, etc.)
 * while preserving custom-named FKore drops.
 */
public class DarkzoneDropFilter implements Listener {

    private final HeadHunting plugin;

    public DarkzoneDropFilter(HeadHunting plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDarkzoneMobDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();

        // Only filter if factions integration and suppress-vanilla-drops are enabled
        if (!plugin.getConfigManager().isFactionsEnabled() || !plugin.getConfigManager().isSuppressVanillaDrops()) {
            return;
        }

        // Only filter in configured darkzone world
        String dzWorld = plugin.getConfigManager().getDarkzoneWorld();
        if (dzWorld.isEmpty() || !entity.getWorld().getName().equalsIgnoreCase(dzWorld)) {
            return;
        }

        // Only filter mobs with custom names (FKore darkzone mobs always have custom names)
        if (entity.getCustomName() == null) {
            return;
        }

        // Remove vanilla drops (items without custom display names)
        // Keep FKore custom drops (they always have display names with color codes)
        Iterator<ItemStack> it = event.getDrops().iterator();
        while (it.hasNext()) {
            ItemStack drop = it.next();
            if (drop.hasItemMeta() && drop.getItemMeta().hasDisplayName()) {
                continue; // Custom item from FKore - keep
            }
            it.remove(); // Vanilla drop (rotten flesh, bones, etc.) - remove
        }

        if (plugin.getConfigManager().isDebugEnabled()) {
            plugin.getLogger().info("[DarkzoneDropFilter] Suppressed vanilla drops for: "
                    + entity.getCustomName() + " in world: " + dzWorld);
        }
    }
}

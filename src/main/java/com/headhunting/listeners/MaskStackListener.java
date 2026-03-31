package com.headhunting.listeners;

import com.headhunting.HeadHunting;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ItemMergeEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Anti-stack protection for HeadHunting masks.
 *
 * v3.0.6 — Reverted to NBT-only identification.
 *
 * The queue/SQLite approach (introduced to work around a ClearLag item-merging bug)
 * is now removed. That bug is fixed. The queue was causing extra masks to spawn
 * incorrectly via the scan-backfill task creating spurious queue entries.
 *
 * Strategy (simple and correct):
 * - Each mask gets a UNIQUE GameProfile UUID → different SkullOwner.Id → won't stack
 * - Each mask gets a unique HeadHunting_UUID NMS NBT tag (via NBTUtil.addUniqueId)
 * - ItemMergeEvent is cancelled for masks as a safety net
 * - Vanilla pickup is allowed — masks arrive in player inventory as-is
 */
public class MaskStackListener implements Listener {

    private final HeadHunting plugin;

    public MaskStackListener(HeadHunting plugin) {
        this.plugin = plugin;
    }

    /**
     * Block ItemMergeEvent for masks (safety net on non-WineSpigot forks).
     * WineSpigot bypasses this event, but unique NBT UUIDs prevent merging there too.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onItemMerge(ItemMergeEvent event) {
        ItemStack entityStack = event.getEntity().getItemStack();
        ItemStack targetStack = event.getTarget().getItemStack();

        if (isMaskOrMystery(entityStack) || isMaskOrMystery(targetStack)) {
            event.setCancelled(true);
            if (plugin.getConfigManager().isDebugEnabled()) {
                plugin.getLogger().info("[MaskStack] MERGE BLOCKED | entity="
                    + event.getEntity().getUniqueId().toString().substring(0, 8)
                    + " | target=" + event.getTarget().getUniqueId().toString().substring(0, 8));
            }
        }
    }

    /**
     * Scan existing ground masks on startup (kept for compatibility — no-op now).
     * Called from HeadHunting.java after listener registration.
     */
    public void protectExistingMasks() {
        // No-op: vanilla pickup is allowed, no queue tracking needed.
        // Masks have unique UUIDs so they don't merge.
        int count = 0;
        for (org.bukkit.World world : org.bukkit.Bukkit.getWorlds()) {
            for (org.bukkit.entity.Entity entity : world.getEntities()) {
                if (!(entity instanceof Item)) continue;
                Item item = (Item) entity;
                if (isMaskOrMystery(item.getItemStack())) {
                    count++;
                }
            }
        }
        if (count > 0) {
            plugin.getLogger().info("[MaskStack] Found " + count
                + " ground mask(s) on startup — vanilla pickup enabled (NBT-only mode)");
        }
    }

    private boolean isMaskOrMystery(ItemStack stack) {
        if (stack == null) return false;
        return plugin.getMaskFactory().isMask(stack)
            || plugin.getMysteryMaskFactory().isMysteryMask(stack);
    }
}

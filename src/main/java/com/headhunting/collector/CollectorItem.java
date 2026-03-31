package com.headhunting.collector;

import com.headhunting.utils.MessageUtil;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.List;

/**
 * Represents the Head Collector placeable item.
 *
 * <p>Uses {@link Material#BEACON} with a hidden lore tag so we can distinguish
 * it from regular beacons.
 */
public final class CollectorItem {

    private CollectorItem() {}

    /** Lore marker embedded as last line (hidden with dark gray italics). */
    public static final String COLLECTOR_MARKER = "HeadHunting_Collector";

    /**
     * Create a Head Collector item stack.
     */
    public static ItemStack create() {
        return create(1);
    }

    public static ItemStack create(int amount) {
        // BEACON — used as the placeable Head Collector block
        ItemStack item = new ItemStack(Material.BEACON, amount);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(MessageUtil.color("&6&l✦ Head Collector ✦"));

        List<String> lore = Arrays.asList(
            MessageUtil.color("&8━━━━━━━━━━━━━━━━━"),
            MessageUtil.color("&7Place to auto-collect mob heads"),
            MessageUtil.color("&7dropped in the same chunk."),
            MessageUtil.color("&7"),
            MessageUtil.color("&7Right-click to open storage GUI."),
            MessageUtil.color("&8━━━━━━━━━━━━━━━━━"),
            MessageUtil.color("&8&o" + COLLECTOR_MARKER)
        );
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Returns true if {@code item} is a Head Collector item (not just any skull).
     */
    public static boolean isCollectorItem(ItemStack item) {
        if (item == null || item.getType() != Material.BEACON) return false;
        if (!item.hasItemMeta() || !item.getItemMeta().hasLore()) return false;
        for (String line : item.getItemMeta().getLore()) {
            if (MessageUtil.stripColor(line).contains(COLLECTOR_MARKER)) return true;
        }
        return false;
    }
}

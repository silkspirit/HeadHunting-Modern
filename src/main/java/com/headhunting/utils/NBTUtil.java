package com.headhunting.utils;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * NBT utilities using Bukkit PersistentDataContainer API (1.14+).
 * Adds unique identifiers to mask items to prevent stacking.
 *
 * Includes lore-based fallback for backward compatibility with items
 * created by older versions of the plugin.
 */
public class NBTUtil {

    private static NamespacedKey UUID_KEY;
    private static NamespacedKey TIME_KEY;
    private static Logger logger = null;

    public static void init(JavaPlugin plugin) {
        logger = plugin.getLogger();
        UUID_KEY = new NamespacedKey(plugin, "uuid");
        TIME_KEY = new NamespacedKey(plugin, "time");
        logger.info("[NBTUtil] Initialized with PersistentDataContainer API");
    }

    /**
     * Add a unique ID to an item via PersistentDataContainer.
     */
    public static ItemStack addUniqueId(ItemStack item) {
        if (item == null) return null;

        String uniqueId = UUID.randomUUID().toString();
        ItemStack clone = item.clone();
        ItemMeta meta = clone.getItemMeta();
        if (meta == null) return addLoreUniqueId(clone, uniqueId);

        if (UUID_KEY != null) {
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(UUID_KEY, PersistentDataType.STRING, uniqueId);
            pdc.set(TIME_KEY, PersistentDataType.LONG, System.nanoTime());
            clone.setItemMeta(meta);

            if (logger != null) {
                logger.info("[NBTUtil] Added PDC unique ID: " + uniqueId.substring(0, 8));
            }
            return clone;
        }

        return addLoreUniqueId(clone, uniqueId);
    }

    /**
     * Fallback: add unique ID via invisible lore line.
     */
    private static ItemStack addLoreUniqueId(ItemStack item, String uniqueId) {
        try {
            ItemMeta meta = item.getItemMeta();
            if (meta == null) return item;

            List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            lore.removeIf(line -> line.contains("\u00a78\u00a7oHH_UID:"));
            lore.add("\u00a78\u00a7oHH_UID:" + uniqueId + ":" + System.nanoTime());

            meta.setLore(lore);
            item.setItemMeta(meta);

            if (logger != null) {
                logger.info("[NBTUtil] Added UID via lore fallback: " + uniqueId.substring(0, 8));
            }
            return item;
        } catch (Exception e) {
            return item;
        }
    }

    /**
     * Dump the PersistentDataContainer entries as a string for debugging.
     */
    public static String dumpNBT(ItemStack item) {
        if (item == null) return "NULL_ITEM";
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return "NO_META";

        if (UUID_KEY == null) return "NOT_INITIALIZED";

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        StringBuilder sb = new StringBuilder();
        sb.append("{");

        String uuid = pdc.get(UUID_KEY, PersistentDataType.STRING);
        if (uuid != null) sb.append("uuid=").append(uuid);

        Long time = pdc.get(TIME_KEY, PersistentDataType.LONG);
        if (time != null) {
            if (sb.length() > 1) sb.append(", ");
            sb.append("time=").append(time);
        }

        sb.append("}");
        return sb.toString();
    }

    /**
     * Compare two items' PersistentDataContainer tags.
     * Used by the stacktest admin command.
     */
    public static String compareNBT(ItemStack itemA, ItemStack itemB) {
        if (UUID_KEY == null) return "NOT_INITIALIZED";

        ItemMeta metaA = itemA != null ? itemA.getItemMeta() : null;
        ItemMeta metaB = itemB != null ? itemB.getItemMeta() : null;

        StringBuilder sb = new StringBuilder();

        String uuidA = metaA != null
                ? metaA.getPersistentDataContainer().get(UUID_KEY, PersistentDataType.STRING)
                : null;
        String uuidB = metaB != null
                ? metaB.getPersistentDataContainer().get(UUID_KEY, PersistentDataType.STRING)
                : null;

        sb.append("A.uuid=").append(uuidA);
        sb.append(" | B.uuid=").append(uuidB);
        sb.append(" | uuids.equal=").append(Objects.equals(uuidA, uuidB));
        sb.append(" | Bukkit.isSimilar=").append(itemA != null && itemA.isSimilar(itemB));

        return sb.toString();
    }

    public static boolean hasUniqueId(ItemStack item) {
        if (item == null) return false;

        // Check PDC first
        if (UUID_KEY != null) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null && meta.getPersistentDataContainer().has(UUID_KEY, PersistentDataType.STRING)) {
                return true;
            }
        }

        // Lore fallback for backward compatibility
        if (item.hasItemMeta() && item.getItemMeta().hasLore()) {
            for (String line : item.getItemMeta().getLore()) {
                if (line.contains("HH_UID:")) return true;
            }
        }
        return false;
    }

    public static String getUniqueId(ItemStack item) {
        if (item == null) return null;

        // Check PDC first
        if (UUID_KEY != null) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                String uuid = meta.getPersistentDataContainer().get(UUID_KEY, PersistentDataType.STRING);
                if (uuid != null) return uuid;
            }
        }

        // Lore fallback for backward compatibility
        if (item.hasItemMeta() && item.getItemMeta().hasLore()) {
            for (String line : item.getItemMeta().getLore()) {
                if (line.contains("HH_UID:")) {
                    String[] parts = line.split(":");
                    if (parts.length >= 2) return parts[1];
                }
            }
        }
        return null;
    }
}

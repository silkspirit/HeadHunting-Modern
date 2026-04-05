package com.headhunting.boosters;

import com.headhunting.HeadHunting;
import com.headhunting.managers.SQLiteProvider;
import com.headhunting.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages HeadHunting XP and Sell boosters (personal only)
 */
public class BoosterManager {

    private final HeadHunting plugin;

    // Personal boosters: Player UUID -> Type -> ActiveBooster
    private final Map<UUID, Map<BoosterType, ActiveBooster>> personalBoosters = new ConcurrentHashMap<>();

    // Cleanup task
    private BukkitTask cleanupTask;

    // NBT key to prevent duping
    public static final String NBT_BOOSTER_KEY = "headhunting_booster";
    public static final String NBT_BOOSTER_ID = "booster_id";
    public static final String NBT_BOOSTER_TYPE = "booster_type";
    public static final String NBT_BOOSTER_SCOPE = "booster_scope";
    public static final String NBT_BOOSTER_MULTI = "booster_multiplier";
    public static final String NBT_BOOSTER_DURATION = "booster_duration";
    public static final String NBT_BOOSTER_UUID = "booster_uuid";

    // SQLite provider for persistence (null if SQLite is not active)
    private SQLiteProvider sqliteProvider;

    public BoosterManager(HeadHunting plugin) {
        this.plugin = plugin;

        // Get SQLite provider for persistence
        if (plugin.getDataManager() != null) {
            this.sqliteProvider = plugin.getDataManager().getSqliteProvider();
        }

        // Restore persisted boosters before starting cleanup
        loadPersistedBoosters();

        startCleanupTask();
    }

    /**
     * Load persisted boosters from SQLite and restore them into in-memory maps.
     */
    private void loadPersistedBoosters() {
        if (sqliteProvider == null) return;

        List<SQLiteProvider.BoosterRow> rows = sqliteProvider.loadAllBoosters();
        int personalCount = 0;

        for (SQLiteProvider.BoosterRow row : rows) {
            // Only restore personal boosters
            if (!"personal".equals(row.scope)) continue;

            UUID activatedByUuid = null;
            if (row.activatedByUuid != null && !row.activatedByUuid.isEmpty()) {
                try {
                    activatedByUuid = UUID.fromString(row.activatedByUuid);
                } catch (IllegalArgumentException ignored) {}
            }

            ActiveBooster booster = new ActiveBooster(
                row.boosterType, row.multiplier,
                row.activatedAt, row.expiresAt,
                activatedByUuid, row.activatedByName
            );

            try {
                UUID ownerUuid = UUID.fromString(row.ownerId);
                Map<BoosterType, ActiveBooster> playerBoosters = personalBoosters.computeIfAbsent(ownerUuid, k -> new ConcurrentHashMap<>());
                playerBoosters.put(row.boosterType, booster);
                personalCount++;
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("[Boosters] Invalid UUID in persisted personal booster: " + row.ownerId);
            }
        }

        if (personalCount > 0) {
            plugin.getLogger().info("[Boosters] Restored " + personalCount + " personal booster(s) from database.");
        }
    }

    /**
     * Persist a booster to SQLite.
     */
    private void persistBooster(String scope, String ownerId, String ownerName, ActiveBooster booster) {
        if (sqliteProvider == null) return;

        String id = scope + ":" + ownerId + ":" + booster.getType().name();
        sqliteProvider.saveBooster(
            id,
            booster.getType().name(),
            scope,
            ownerId,
            ownerName,
            booster.getMultiplier(),
            booster.getActivatedAt(),
            booster.getExpiresAt(),
            booster.getActivatedBy() != null ? booster.getActivatedBy().toString() : null,
            booster.getActivatorName()
        );
    }

    /**
     * Remove a persisted booster from SQLite.
     */
    private void removePersistedBooster(String scope, String ownerId, BoosterType type) {
        if (sqliteProvider == null) return;

        String id = scope + ":" + ownerId + ":" + type.name();
        sqliteProvider.removeBooster(id);
    }

    /**
     * Start task to clean up expired boosters
     */
    private void startCleanupTask() {
        cleanupTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            cleanupExpiredBoosters();
        }, 20L * 30, 20L * 30); // Every 30 seconds
    }

    /**
     * Remove expired boosters, notify players, and clean up SQLite
     */
    private void cleanupExpiredBoosters() {
        for (Map.Entry<UUID, Map<BoosterType, ActiveBooster>> entry : personalBoosters.entrySet()) {
            UUID playerUuid = entry.getKey();
            Map<BoosterType, ActiveBooster> boosters = entry.getValue();

            Iterator<Map.Entry<BoosterType, ActiveBooster>> iter = boosters.entrySet().iterator();
            while (iter.hasNext()) {
                Map.Entry<BoosterType, ActiveBooster> boosterEntry = iter.next();
                if (boosterEntry.getValue().isExpired()) {
                    BoosterType expiredType = boosterEntry.getKey();
                    iter.remove();
                    removePersistedBooster("personal", playerUuid.toString(), expiredType);

                    Player player = Bukkit.getPlayer(playerUuid);
                    if (player != null) {
                        String typeName = expiredType.getDisplayName();
                        MessageUtil.send(player, "&c&l⚡ &cYour personal " + typeName + " booster has expired!");
                    }
                }
            }
        }
    }

    /**
     * Activate a personal booster
     */
    public boolean activatePersonalBooster(Player player, BoosterType type, double multiplier, long durationMs) {
        UUID uuid = player.getUniqueId();

        Map<BoosterType, ActiveBooster> playerBoosters = personalBoosters.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>());

        // Check if already has this type active
        ActiveBooster existing = playerBoosters.get(type);
        if (existing != null && !existing.isExpired()) {
            MessageUtil.send(player, "&c&l⚡ &cYou already have a personal " +
                type.getDisplayName() + " booster active! (" + existing.getRemainingFormatted() + " left)");
            return false;
        }

        // Activate new booster
        ActiveBooster booster = new ActiveBooster(type, multiplier, durationMs, uuid, player.getName());
        playerBoosters.put(type, booster);

        // Persist to SQLite
        persistBooster("personal", uuid.toString(), player.getName(), booster);

        String typeName = type.getDisplayName();
        String duration = formatDuration(durationMs);

        MessageUtil.sendMultiple(player,
            "",
            "&b&l⚡ PERSONAL " + typeName.toUpperCase() + " BOOSTER ACTIVATED ⚡",
            "",
            "&7Multiplier: &a" + multiplier + "x",
            "&7Duration: &e" + duration,
            ""
        );

        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);

        return true;
    }

    /**
     * Get the total XP multiplier for a player
     */
    public double getXpMultiplier(Player player) {
        double multiplier = 1.0;

        Map<BoosterType, ActiveBooster> personal = personalBoosters.get(player.getUniqueId());
        if (personal != null) {
            ActiveBooster xpBooster = personal.get(BoosterType.XP);
            if (xpBooster != null && !xpBooster.isExpired()) {
                multiplier *= xpBooster.getMultiplier();
            }
        }

        return multiplier;
    }

    /**
     * Get the total Sell multiplier for a player
     */
    public double getSellMultiplier(Player player) {
        double multiplier = 1.0;

        Map<BoosterType, ActiveBooster> personal = personalBoosters.get(player.getUniqueId());
        if (personal != null) {
            ActiveBooster sellBooster = personal.get(BoosterType.SELL);
            if (sellBooster != null && !sellBooster.isExpired()) {
                multiplier *= sellBooster.getMultiplier();
            }
        }

        return multiplier;
    }

    /**
     * Get the total Fishing catch rate multiplier for a player.
     */
    public double getFishingMultiplier(Player player) {
        double multiplier = 1.0;

        Map<BoosterType, ActiveBooster> personal = personalBoosters.get(player.getUniqueId());
        if (personal != null) {
            ActiveBooster fishingBooster = personal.get(BoosterType.FISHING);
            if (fishingBooster != null && !fishingBooster.isExpired()) {
                multiplier *= fishingBooster.getMultiplier();
            }
        }

        return multiplier;
    }

    /**
     * Check if player has any active boosters
     */
    public boolean hasActiveBooster(Player player, BoosterType type) {
        Map<BoosterType, ActiveBooster> personal = personalBoosters.get(player.getUniqueId());
        if (personal != null) {
            ActiveBooster booster = personal.get(type);
            if (booster != null && !booster.isExpired()) {
                return true;
            }
        }

        return false;
    }

    /**
     * Get active booster info for a player
     */
    public List<String> getBoosterStatus(Player player) {
        List<String> status = new ArrayList<>();

        Map<BoosterType, ActiveBooster> personal = personalBoosters.get(player.getUniqueId());
        if (personal != null) {
            for (Map.Entry<BoosterType, ActiveBooster> entry : personal.entrySet()) {
                ActiveBooster b = entry.getValue();
                if (!b.isExpired()) {
                    String typeName = entry.getKey().getDisplayName();
                    status.add("&b⚡ Personal " + typeName + ": &a" + b.getMultiplier() + "x &7(" + b.getRemainingFormatted() + ")");
                }
            }
        }

        return status;
    }

    // =========================================================================
    // BOOSTER ITEM CREATION
    // =========================================================================

    /**
     * Create a booster voucher item
     */
    public ItemStack createBoosterItem(String boosterId, BoosterType type, String scope, double multiplier, int durationMinutes) {
        Material material;
        switch (type) {
            case XP:      material = Material.EXPERIENCE_BOTTLE; break;
            case FISHING: material = Material.FISHING_ROD; break;
            default:      material = Material.EMERALD; break;
        }
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        String typeName = type.getDisplayName();
        String rarity = getRarityFromMultiplier(multiplier);
        String rarityColor = getRarityColor(multiplier);

        meta.setDisplayName(MessageUtil.color("&b&lPersonal " + typeName + " Booster &f- " + rarityColor + "&l" + multiplier + "x"));

        String description = "Boost your HeadHunting " + typeName + " rate!";
        if (type == BoosterType.FISHING) {
            description = "Fish bite faster while active!";
        }

        List<String> lore = new ArrayList<>();
        lore.add(MessageUtil.color("&8━━━━━━━━━━━━━━━━━━━━"));
        lore.add(MessageUtil.color("&7Rarity: " + rarityColor + rarity));
        lore.add(MessageUtil.color(""));
        lore.add(MessageUtil.color("&7" + description));
        lore.add(MessageUtil.color(""));
        lore.add(MessageUtil.color("&7Multiplier: " + rarityColor + multiplier + "x"));
        lore.add(MessageUtil.color("&7Duration: &e" + durationMinutes + " minutes"));
        lore.add(MessageUtil.color("&8━━━━━━━━━━━━━━━━━━━━"));
        lore.add(MessageUtil.color(""));
        lore.add(MessageUtil.color("&e&oRight-click to activate!"));

        meta.setLore(lore);

        // Add glow
        meta.addEnchant(Enchantment.DURABILITY, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);

        item.setItemMeta(meta);

        // Apply NBT data to prevent duping
        item = applyBoosterNBT(item, boosterId, type, scope, multiplier, durationMinutes);

        return item;
    }

    /**
     * Apply PDC tags to booster item (PersistentDataContainer, Paper 1.14+).
     */
    private ItemStack applyBoosterNBT(ItemStack item, String boosterId, BoosterType type, String scope, double multiplier, int durationMinutes) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        org.bukkit.NamespacedKey keyBooster  = new org.bukkit.NamespacedKey(plugin, NBT_BOOSTER_KEY);
        org.bukkit.NamespacedKey keyId       = new org.bukkit.NamespacedKey(plugin, NBT_BOOSTER_ID);
        org.bukkit.NamespacedKey keyType     = new org.bukkit.NamespacedKey(plugin, NBT_BOOSTER_TYPE);
        org.bukkit.NamespacedKey keyScope    = new org.bukkit.NamespacedKey(plugin, NBT_BOOSTER_SCOPE);
        org.bukkit.NamespacedKey keyMulti    = new org.bukkit.NamespacedKey(plugin, NBT_BOOSTER_MULTI);
        org.bukkit.NamespacedKey keyDuration = new org.bukkit.NamespacedKey(plugin, NBT_BOOSTER_DURATION);
        org.bukkit.NamespacedKey keyUuid     = new org.bukkit.NamespacedKey(plugin, NBT_BOOSTER_UUID);

        org.bukkit.persistence.PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(keyBooster,  org.bukkit.persistence.PersistentDataType.STRING, "true");
        pdc.set(keyId,       org.bukkit.persistence.PersistentDataType.STRING, boosterId);
        pdc.set(keyType,     org.bukkit.persistence.PersistentDataType.STRING, type.name());
        pdc.set(keyScope,    org.bukkit.persistence.PersistentDataType.STRING, scope);
        pdc.set(keyMulti,    org.bukkit.persistence.PersistentDataType.DOUBLE, multiplier);
        pdc.set(keyDuration, org.bukkit.persistence.PersistentDataType.INTEGER, durationMinutes);
        pdc.set(keyUuid,     org.bukkit.persistence.PersistentDataType.STRING, UUID.randomUUID().toString());

        item.setItemMeta(meta);
        return item;
    }

    /**
     * Check if item is a booster voucher
     */
    public boolean isBoosterItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        if (!item.hasItemMeta()) return false;

        org.bukkit.NamespacedKey key = new org.bukkit.NamespacedKey(plugin, NBT_BOOSTER_KEY);
        return item.getItemMeta().getPersistentDataContainer()
                   .has(key, org.bukkit.persistence.PersistentDataType.STRING);
    }

    /**
     * Get booster data from item
     */
    public Map<String, Object> getBoosterData(ItemStack item) {
        Map<String, Object> data = new HashMap<>();
        if (item == null || !item.hasItemMeta()) return data;

        org.bukkit.persistence.PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();

        String id       = pdc.get(new org.bukkit.NamespacedKey(plugin, NBT_BOOSTER_ID),       org.bukkit.persistence.PersistentDataType.STRING);
        String type     = pdc.get(new org.bukkit.NamespacedKey(plugin, NBT_BOOSTER_TYPE),     org.bukkit.persistence.PersistentDataType.STRING);
        String scope    = pdc.get(new org.bukkit.NamespacedKey(plugin, NBT_BOOSTER_SCOPE),    org.bukkit.persistence.PersistentDataType.STRING);
        Double multi    = pdc.get(new org.bukkit.NamespacedKey(plugin, NBT_BOOSTER_MULTI),    org.bukkit.persistence.PersistentDataType.DOUBLE);
        Integer duration = pdc.get(new org.bukkit.NamespacedKey(plugin, NBT_BOOSTER_DURATION), org.bukkit.persistence.PersistentDataType.INTEGER);
        String uuid     = pdc.get(new org.bukkit.NamespacedKey(plugin, NBT_BOOSTER_UUID),     org.bukkit.persistence.PersistentDataType.STRING);

        if (id != null)       data.put("id", id);
        if (type != null)     data.put("type", type);
        if (scope != null)    data.put("scope", scope);
        if (multi != null)    data.put("multiplier", multi);
        if (duration != null) data.put("duration", duration);
        if (uuid != null)     data.put("uuid", uuid);

        return data;
    }

    // =========================================================================
    // UTILITY METHODS
    // =========================================================================

    private String formatDuration(long ms) {
        long minutes = ms / (1000 * 60);
        if (minutes >= 60) {
            long hours = minutes / 60;
            minutes = minutes % 60;
            return hours + "h " + minutes + "m";
        }
        return minutes + " minutes";
    }

    private String getRarityFromMultiplier(double multiplier) {
        if (multiplier >= 3.0) return "LEGENDARY";
        if (multiplier >= 2.5) return "EPIC";
        if (multiplier >= 2.0) return "RARE";
        if (multiplier >= 1.75) return "UNCOMMON";
        return "COMMON";
    }

    private String getRarityColor(double multiplier) {
        if (multiplier >= 3.0) return "&6";
        if (multiplier >= 2.5) return "&d";
        if (multiplier >= 2.0) return "&5";
        if (multiplier >= 1.75) return "&a";
        return "&7";
    }

    /**
     * Shutdown cleanup — cancel task and purge expired boosters from DB
     */
    public void shutdown() {
        if (cleanupTask != null) {
            cleanupTask.cancel();
        }
        // Final cleanup of expired entries in SQLite
        if (sqliteProvider != null) {
            int removed = sqliteProvider.removeExpiredBoosters();
            if (removed > 0) {
                plugin.getLogger().info("[Boosters] Cleaned " + removed + " expired booster(s) from database on shutdown.");
            }
        }
    }
}

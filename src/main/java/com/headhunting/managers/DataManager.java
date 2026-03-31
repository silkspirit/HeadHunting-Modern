package com.headhunting.managers;

import com.headhunting.HeadHunting;
import com.headhunting.data.PlayerData;
import com.headhunting.data.PlayerData.MaskData;
import com.headhunting.data.PlayerData.MissionProgress;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Manages player data storage and retrieval.
 * Supports YAML (default) and SQLite backends, switchable via config.yml storage-type.
 */
public class DataManager {

    private final HeadHunting plugin;
    private final Map<UUID, PlayerData> playerDataCache = new HashMap<>();
    private final File dataFolder;

    private final boolean useSqlite;
    private SQLiteProvider sqliteProvider;

    public DataManager(HeadHunting plugin) {
        this.plugin = plugin;
        this.dataFolder = new File(plugin.getDataFolder(), "data/players");
        this.useSqlite = "SQLITE".equalsIgnoreCase(plugin.getConfigManager().getStorageType());

        if (useSqlite) {
            initSqlite();
        }

        if (!useSqlite || sqliteProvider == null) {
            // Ensure YAML folder exists (also needed as fallback)
            if (!dataFolder.exists()) {
                dataFolder.mkdirs();
            }
        }

        // Start auto-save task
        int interval = plugin.getConfigManager().getAutoSaveInterval();
        if (interval > 0) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    saveAllData();
                }
            }.runTaskTimerAsynchronously(plugin, interval * 60 * 20L, interval * 60 * 20L);
        }
    }

    private void initSqlite() {
        sqliteProvider = new SQLiteProvider(plugin);
        try {
            sqliteProvider.init();

            // Auto-migrate from YAML if YAML data folder exists with files
            if (dataFolder.exists()) {
                File[] yamlFiles = dataFolder.listFiles((dir, name) -> name.endsWith(".yml"));
                if (yamlFiles != null && yamlFiles.length > 0) {
                    plugin.getLogger().info("[SQLite] Detected " + yamlFiles.length + " YAML player files. Checking migration...");
                    int migrated = migrateYamlToSqlite(yamlFiles);
                    if (migrated > 0) {
                        plugin.getLogger().info("[SQLite] Migrated " + migrated + " players from YAML to SQLite.");
                    }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().severe("[SQLite] Failed to initialize database! Falling back to YAML.");
            plugin.getLogger().severe(e.getMessage());
            sqliteProvider = null;
        }
    }

    private int migrateYamlToSqlite(File[] files) {
        int migrated = 0;
        for (File file : files) {
            String uuidStr = file.getName().replace(".yml", "");
            try {
                UUID uuid = UUID.fromString(uuidStr);
                if (!sqliteProvider.hasPlayerData(uuid)) {
                    PlayerData data = loadFromYaml(uuid);
                    sqliteProvider.savePlayerData(uuid, data);
                    migrated++;
                }
            } catch (IllegalArgumentException ignored) {}
        }
        return migrated;
    }

    private boolean isSqliteActive() {
        return useSqlite && sqliteProvider != null;
    }

    /**
     * Load player data asynchronously on join to avoid main thread I/O.
     * The callback runs on the main thread after loading completes.
     */
    public void loadPlayerDataAsync(UUID uuid, Runnable onComplete) {
        new BukkitRunnable() {
            @Override
            public void run() {
                final PlayerData data = isSqliteActive()
                    ? sqliteProvider.loadPlayerData(uuid)
                    : loadFromYaml(uuid);

                // Put in cache and run callback on main thread
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        playerDataCache.put(uuid, data);
                        if (onComplete != null) {
                            onComplete.run();
                        }
                    }
                }.runTask(plugin);
            }
        }.runTaskAsynchronously(plugin);
    }

    /**
     * Get player data, loading synchronously if not cached.
     * Prefer loadPlayerDataAsync() on join events to avoid blocking the main thread.
     */
    public PlayerData getPlayerData(UUID uuid) {
        return playerDataCache.computeIfAbsent(uuid, this::loadSync);
    }

    public PlayerData getPlayerData(Player player) {
        return getPlayerData(player.getUniqueId());
    }

    private PlayerData loadSync(UUID uuid) {
        if (isSqliteActive()) {
            return sqliteProvider.loadPlayerData(uuid);
        }
        return loadFromYaml(uuid);
    }

    /**
     * Get all player data (for leaderboards).
     * SQLite loads directly from DB; YAML scans the data folder.
     */
    public Map<UUID, PlayerData> getAllPlayerData() {
        if (isSqliteActive()) {
            Map<UUID, PlayerData> all = sqliteProvider.loadAllPlayerData();
            playerDataCache.putAll(all);
            return new HashMap<>(playerDataCache);
        }

        // YAML: load all files
        File[] files = dataFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files != null) {
            for (File file : files) {
                String uuidStr = file.getName().replace(".yml", "");
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    if (!playerDataCache.containsKey(uuid)) {
                        playerDataCache.put(uuid, loadFromYaml(uuid));
                    }
                } catch (IllegalArgumentException ignored) {}
            }
        }
        return new HashMap<>(playerDataCache);
    }

    // ============================================
    // YAML I/O
    // ============================================

    private PlayerData loadFromYaml(UUID uuid) {
        File file = getPlayerFile(uuid);
        PlayerData data = new PlayerData(uuid);

        if (!file.exists()) {
            return data;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);

        data.setLevel(config.getInt("level", 1));
        data.setXp(config.getInt("xp", 0));
        data.setEquippedMask(config.getString("equipped-mask", null));

        data.setTotalHeadsCollected(config.getInt("stats.heads-collected", 0));
        data.setTotalHeadsSold(config.getInt("stats.heads-sold", 0));
        data.setTotalMasksCrafted(config.getInt("stats.masks-crafted", 0));

        ConfigurationSection masksSection = config.getConfigurationSection("masks");
        if (masksSection != null) {
            for (String maskId : masksSection.getKeys(false)) {
                ConfigurationSection maskSection = masksSection.getConfigurationSection(maskId);
                if (maskSection != null) {
                    MaskData maskData = new MaskData(
                        maskSection.getBoolean("owned", false),
                        maskSection.getInt("level", 0),
                        maskSection.getInt("deposited-heads", 0)
                    );
                    data.setMaskData(maskId, maskData);
                }
            }
        }

        ConfigurationSection missionsSection = config.getConfigurationSection("missions");
        if (missionsSection != null) {
            for (String missionId : missionsSection.getKeys(false)) {
                ConfigurationSection missionSection = missionsSection.getConfigurationSection(missionId);
                if (missionSection != null) {
                    MissionProgress progress = new MissionProgress(
                        missionSection.getInt("progress", 0),
                        missionSection.getBoolean("completed", false)
                    );
                    data.getMissions().put(missionId, progress);
                }
            }
        }

        return data;
    }

    private void saveToYaml(UUID uuid, PlayerData data) {
        File file = getPlayerFile(uuid);
        YamlConfiguration config = new YamlConfiguration();

        config.set("level", data.getLevel());
        config.set("xp", data.getXp());
        config.set("equipped-mask", data.getEquippedMask());

        config.set("stats.heads-collected", data.getTotalHeadsCollected());
        config.set("stats.heads-sold", data.getTotalHeadsSold());
        config.set("stats.masks-crafted", data.getTotalMasksCrafted());

        for (Map.Entry<String, MaskData> entry : data.getMasks().entrySet()) {
            String path = "masks." + entry.getKey();
            MaskData maskData = entry.getValue();
            config.set(path + ".owned", maskData.isOwned());
            config.set(path + ".level", maskData.getLevel());
            config.set(path + ".deposited-heads", maskData.getDepositedHeads());
        }

        for (Map.Entry<String, MissionProgress> entry : data.getMissions().entrySet()) {
            String path = "missions." + entry.getKey();
            MissionProgress progress = entry.getValue();
            config.set(path + ".progress", progress.getProgress());
            config.set(path + ".completed", progress.isCompleted());
        }

        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save player data for " + uuid + ": " + e.getMessage());
        }
    }

    // ============================================
    // SAVE / UNLOAD
    // ============================================

    public void savePlayerData(UUID uuid) {
        PlayerData data = playerDataCache.get(uuid);
        if (data == null) return;

        if (isSqliteActive()) {
            sqliteProvider.savePlayerData(uuid, data);
        } else {
            saveToYaml(uuid, data);
        }
    }

    public void savePlayerData(Player player) {
        savePlayerData(player.getUniqueId());
    }

    public void saveAllData() {
        for (UUID uuid : playerDataCache.keySet()) {
            savePlayerData(uuid);
        }
        if (plugin.getConfigManager().isDebugEnabled()) {
            plugin.getLogger().info("Auto-saved " + playerDataCache.size() + " player data entries.");
        }
    }

    public void unloadPlayerData(UUID uuid) {
        savePlayerData(uuid);
        playerDataCache.remove(uuid);
    }

    public void unloadPlayerData(Player player) {
        unloadPlayerData(player.getUniqueId());
    }

    private File getPlayerFile(UUID uuid) {
        return new File(dataFolder, uuid.toString() + ".yml");
    }

    public boolean hasPlayerData(UUID uuid) {
        if (playerDataCache.containsKey(uuid)) return true;
        if (isSqliteActive()) return sqliteProvider.hasPlayerData(uuid);
        return getPlayerFile(uuid).exists();
    }

    public boolean isUsingSqlite() {
        return isSqliteActive();
    }

    /**
     * Get the SQLite provider (for dropped mask queue persistence).
     * Returns null if SQLite is not active.
     */
    public SQLiteProvider getSqliteProvider() {
        return isSqliteActive() ? sqliteProvider : null;
    }

    /**
     * Shutdown - save all data and close SQLite connection
     */
    public void shutdown() {
        saveAllData();
        if (sqliteProvider != null) {
            sqliteProvider.close();
        }
    }
}

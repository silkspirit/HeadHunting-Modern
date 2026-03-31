package com.headhunting.managers;

import com.headhunting.HeadHunting;
import com.headhunting.boosters.BoosterType;
import com.headhunting.data.PlayerData;
import com.headhunting.data.PlayerData.MaskData;
import com.headhunting.data.PlayerData.MissionProgress;
import com.headhunting.data.PlayerData.DailyMissionProgress;
import com.headhunting.data.ServerAnalytics;

import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * SQLite storage provider for player data.
 * Uses a single headhunting.db file instead of per-player YAML files.
 */
public class SQLiteProvider {

    private final HeadHunting plugin;
    private Connection connection;
    private final File dbFile;

    public SQLiteProvider(HeadHunting plugin) {
        this.plugin = plugin;
        this.dbFile = new File(plugin.getDataFolder(), "headhunting.db");
    }

    /**
     * Initialize connection and create tables
     */
    public void init() throws SQLException {
        connect();
        createTables();
        plugin.getLogger().info("[SQLite] Database initialized: " + dbFile.getName());
    }

    private void connect() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            return;
        }
        connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
        // Enable WAL mode for better concurrent read performance
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("PRAGMA journal_mode=WAL");
            stmt.execute("PRAGMA synchronous=NORMAL");
        }
    }

    private void ensureConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            connect();
        }
    }

    private void createTables() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(
                "CREATE TABLE IF NOT EXISTS players (" +
                "  uuid TEXT PRIMARY KEY," +
                "  level INTEGER DEFAULT 1," +
                "  xp INTEGER DEFAULT 0," +
                "  equipped_mask TEXT," +
                "  boss_bar_enabled INTEGER DEFAULT 0," +
                "  last_daily_reset TEXT DEFAULT ''," +
                "  heads_collected INTEGER DEFAULT 0," +
                "  heads_sold INTEGER DEFAULT 0," +
                "  masks_crafted INTEGER DEFAULT 0" +
                ")"
            );

            stmt.execute(
                "CREATE TABLE IF NOT EXISTS player_masks (" +
                "  uuid TEXT NOT NULL," +
                "  mask_id TEXT NOT NULL," +
                "  owned INTEGER DEFAULT 0," +
                "  level INTEGER DEFAULT 0," +
                "  deposited_heads INTEGER DEFAULT 0," +
                "  PRIMARY KEY (uuid, mask_id)" +
                ")"
            );

            stmt.execute(
                "CREATE TABLE IF NOT EXISTS player_missions (" +
                "  uuid TEXT NOT NULL," +
                "  mission_id TEXT NOT NULL," +
                "  progress INTEGER DEFAULT 0," +
                "  completed INTEGER DEFAULT 0," +
                "  PRIMARY KEY (uuid, mission_id)" +
                ")"
            );

            stmt.execute(
                "CREATE TABLE IF NOT EXISTS player_daily_missions (" +
                "  uuid TEXT NOT NULL," +
                "  mission_id TEXT NOT NULL," +
                "  progress INTEGER DEFAULT 0," +
                "  completed INTEGER DEFAULT 0," +
                "  PRIMARY KEY (uuid, mission_id)" +
                ")"
            );

            // Indexes for leaderboard queries
            stmt.execute(
                "CREATE INDEX IF NOT EXISTS idx_players_level ON players(level DESC, xp DESC)"
            );

            // Dropped mask queue persistence (v2.1.16)
            stmt.execute(
                "CREATE TABLE IF NOT EXISTS dropped_masks (" +
                "  id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "  mask_id TEXT NOT NULL," +
                "  level INTEGER NOT NULL," +
                "  is_mystery INTEGER NOT NULL DEFAULT 0," +
                "  world TEXT NOT NULL," +
                "  x DOUBLE NOT NULL," +
                "  y DOUBLE NOT NULL," +
                "  z DOUBLE NOT NULL," +
                "  chunk_x INTEGER NOT NULL," +
                "  chunk_z INTEGER NOT NULL," +
                "  dropped_at BIGINT NOT NULL," +
                "  consumed INTEGER NOT NULL DEFAULT 0" +
                ")"
            );

            stmt.execute(
                "CREATE INDEX IF NOT EXISTS idx_dropped_masks_chunk ON dropped_masks(world, chunk_x, chunk_z)"
            );
            stmt.execute(
                "CREATE INDEX IF NOT EXISTS idx_dropped_masks_consumed ON dropped_masks(consumed)"
            );

            // Active boosters persistence (v2.1.25)
            stmt.execute(
                "CREATE TABLE IF NOT EXISTS boosters (" +
                "  id TEXT PRIMARY KEY," +
                "  booster_type TEXT NOT NULL," +
                "  scope TEXT NOT NULL," +
                "  owner_id TEXT NOT NULL," +
                "  owner_name TEXT," +
                "  multiplier REAL NOT NULL," +
                "  activated_at INTEGER NOT NULL," +
                "  expires_at INTEGER NOT NULL," +
                "  activated_by_uuid TEXT," +
                "  activated_by_name TEXT" +
                ")"
            );
        }
    }

    /**
     * Load player data from SQLite
     */
    public synchronized PlayerData loadPlayerData(UUID uuid) {
        PlayerData data = new PlayerData(uuid);
        String uuidStr = uuid.toString();

        try {
            ensureConnection();

            // Load base player data
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT * FROM players WHERE uuid = ?")) {
                ps.setString(1, uuidStr);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    data.setLevel(rs.getInt("level"));
                    data.setXp(rs.getInt("xp"));
                    data.setEquippedMask(rs.getString("equipped_mask"));
                    data.setBossBarEnabled(rs.getInt("boss_bar_enabled") == 1);
                    data.setLastDailyReset(rs.getString("last_daily_reset"));
                    data.setTotalHeadsCollected(rs.getInt("heads_collected"));
                    data.setTotalHeadsSold(rs.getInt("heads_sold"));
                    data.setTotalMasksCrafted(rs.getInt("masks_crafted"));
                }
            }

            // Load masks
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT * FROM player_masks WHERE uuid = ?")) {
                ps.setString(1, uuidStr);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    MaskData maskData = new MaskData(
                        rs.getInt("owned") == 1,
                        rs.getInt("level"),
                        rs.getInt("deposited_heads")
                    );
                    data.setMaskData(rs.getString("mask_id"), maskData);
                }
            }

            // Load missions
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT * FROM player_missions WHERE uuid = ?")) {
                ps.setString(1, uuidStr);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    MissionProgress progress = new MissionProgress(
                        rs.getInt("progress"),
                        rs.getInt("completed") == 1
                    );
                    data.getMissions().put(rs.getString("mission_id"), progress);
                }
            }

            // Load daily missions
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT * FROM player_daily_missions WHERE uuid = ?")) {
                ps.setString(1, uuidStr);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    DailyMissionProgress progress = new DailyMissionProgress();
                    progress.setProgress(rs.getInt("progress"));
                    progress.setCompleted(rs.getInt("completed") == 1);
                    data.getDailyMissions().put(rs.getString("mission_id"), progress);
                }
            }

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[SQLite] Failed to load data for " + uuid, e);
        }

        return data;
    }

    /**
     * Save player data to SQLite
     */
    public synchronized void savePlayerData(UUID uuid, PlayerData data) {
        if (data == null) return;
        String uuidStr = uuid.toString();

        try {
            ensureConnection();
            connection.setAutoCommit(false);

            // Upsert base player data
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT OR REPLACE INTO players " +
                    "(uuid, level, xp, equipped_mask, boss_bar_enabled, last_daily_reset, " +
                    "heads_collected, heads_sold, masks_crafted) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                ps.setString(1, uuidStr);
                ps.setInt(2, data.getLevel());
                ps.setInt(3, data.getXp());
                ps.setString(4, data.getEquippedMask());
                ps.setInt(5, data.isBossBarEnabled() ? 1 : 0);
                ps.setString(6, data.getLastDailyReset());
                ps.setInt(7, data.getTotalHeadsCollected());
                ps.setInt(8, data.getTotalHeadsSold());
                ps.setInt(9, data.getTotalMasksCrafted());
                ps.executeUpdate();
            }

            // Save masks - clear and re-insert
            try (PreparedStatement del = connection.prepareStatement(
                    "DELETE FROM player_masks WHERE uuid = ?")) {
                del.setString(1, uuidStr);
                del.executeUpdate();
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO player_masks (uuid, mask_id, owned, level, deposited_heads) " +
                    "VALUES (?, ?, ?, ?, ?)")) {
                for (Map.Entry<String, MaskData> entry : data.getMasks().entrySet()) {
                    MaskData md = entry.getValue();
                    ps.setString(1, uuidStr);
                    ps.setString(2, entry.getKey());
                    ps.setInt(3, md.isOwned() ? 1 : 0);
                    ps.setInt(4, md.getLevel());
                    ps.setInt(5, md.getDepositedHeads());
                    ps.addBatch();
                }
                ps.executeBatch();
            }

            // Save missions - clear and re-insert
            try (PreparedStatement del = connection.prepareStatement(
                    "DELETE FROM player_missions WHERE uuid = ?")) {
                del.setString(1, uuidStr);
                del.executeUpdate();
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO player_missions (uuid, mission_id, progress, completed) " +
                    "VALUES (?, ?, ?, ?)")) {
                for (Map.Entry<String, MissionProgress> entry : data.getMissions().entrySet()) {
                    MissionProgress mp = entry.getValue();
                    ps.setString(1, uuidStr);
                    ps.setString(2, entry.getKey());
                    ps.setInt(3, mp.getProgress());
                    ps.setInt(4, mp.isCompleted() ? 1 : 0);
                    ps.addBatch();
                }
                ps.executeBatch();
            }

            // Save daily missions - clear and re-insert
            try (PreparedStatement del = connection.prepareStatement(
                    "DELETE FROM player_daily_missions WHERE uuid = ?")) {
                del.setString(1, uuidStr);
                del.executeUpdate();
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO player_daily_missions (uuid, mission_id, progress, completed) " +
                    "VALUES (?, ?, ?, ?)")) {
                for (Map.Entry<String, DailyMissionProgress> entry : data.getDailyMissions().entrySet()) {
                    DailyMissionProgress dp = entry.getValue();
                    ps.setString(1, uuidStr);
                    ps.setString(2, entry.getKey());
                    ps.setInt(3, dp.getProgress());
                    ps.setInt(4, dp.isCompleted() ? 1 : 0);
                    ps.addBatch();
                }
                ps.executeBatch();
            }

            connection.commit();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[SQLite] Failed to save data for " + uuid, e);
            try {
                connection.rollback();
            } catch (SQLException ex) {
                plugin.getLogger().log(Level.SEVERE, "[SQLite] Failed to rollback", ex);
            }
        } finally {
            try {
                connection.setAutoCommit(true);
            } catch (SQLException ignored) {}
        }
    }

    /**
     * Load all player data (for leaderboards) using bulk queries.
     * Uses 4 queries total regardless of player count, instead of 4×N individual queries.
     */
    public synchronized Map<UUID, PlayerData> loadAllPlayerData() {
        Map<UUID, PlayerData> allData = new HashMap<>();

        try {
            ensureConnection();

            // 1. Bulk load all players
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT uuid, level, xp, equipped_mask, boss_bar_enabled, " +
                    "last_daily_reset, heads_collected, heads_sold, masks_crafted FROM players")) {
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    UUID uuid = UUID.fromString(rs.getString("uuid"));
                    PlayerData data = new PlayerData(uuid);
                    data.setLevel(rs.getInt("level"));
                    data.setXp(rs.getInt("xp"));
                    data.setEquippedMask(rs.getString("equipped_mask"));
                    data.setBossBarEnabled(rs.getInt("boss_bar_enabled") == 1);
                    data.setLastDailyReset(rs.getString("last_daily_reset"));
                    data.setTotalHeadsCollected(rs.getInt("heads_collected"));
                    data.setTotalHeadsSold(rs.getInt("heads_sold"));
                    data.setTotalMasksCrafted(rs.getInt("masks_crafted"));
                    allData.put(uuid, data);
                }
            }

            // 2. Bulk load all masks, attach to corresponding PlayerData
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT uuid, mask_id, owned, level, deposited_heads FROM player_masks")) {
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    UUID uuid = UUID.fromString(rs.getString("uuid"));
                    PlayerData data = allData.get(uuid);
                    if (data != null) {
                        MaskData maskData = new MaskData(
                            rs.getInt("owned") == 1,
                            rs.getInt("level"),
                            rs.getInt("deposited_heads")
                        );
                        data.setMaskData(rs.getString("mask_id"), maskData);
                    }
                }
            }

            // 3. Bulk load all missions, attach to corresponding PlayerData
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT uuid, mission_id, progress, completed FROM player_missions")) {
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    UUID uuid = UUID.fromString(rs.getString("uuid"));
                    PlayerData data = allData.get(uuid);
                    if (data != null) {
                        MissionProgress progress = new MissionProgress(
                            rs.getInt("progress"),
                            rs.getInt("completed") == 1
                        );
                        data.getMissions().put(rs.getString("mission_id"), progress);
                    }
                }
            }

            // 4. Bulk load all daily missions, attach to corresponding PlayerData
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT uuid, mission_id, progress, completed FROM player_daily_missions")) {
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    UUID uuid = UUID.fromString(rs.getString("uuid"));
                    PlayerData data = allData.get(uuid);
                    if (data != null) {
                        DailyMissionProgress progress = new DailyMissionProgress();
                        progress.setProgress(rs.getInt("progress"));
                        progress.setCompleted(rs.getInt("completed") == 1);
                        data.getDailyMissions().put(rs.getString("mission_id"), progress);
                    }
                }
            }

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[SQLite] Failed to load all player data", e);
        }

        return allData;
    }

    /**
     * Load lightweight leaderboard data — top N players sorted by level/xp.
     * Only loads player stats (no masks, missions, or daily missions) for maximum speed.
     */
    public synchronized List<PlayerData> loadLeaderboardData(int limit) {
        List<PlayerData> leaderboard = new ArrayList<>();

        try {
            ensureConnection();
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT uuid, level, xp, heads_collected, heads_sold, masks_crafted " +
                    "FROM players ORDER BY level DESC, xp DESC LIMIT ?")) {
                ps.setInt(1, limit);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    UUID uuid = UUID.fromString(rs.getString("uuid"));
                    PlayerData data = new PlayerData(uuid);
                    data.setLevel(rs.getInt("level"));
                    data.setXp(rs.getInt("xp"));
                    data.setTotalHeadsCollected(rs.getInt("heads_collected"));
                    data.setTotalHeadsSold(rs.getInt("heads_sold"));
                    data.setTotalMasksCrafted(rs.getInt("masks_crafted"));
                    leaderboard.add(data);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[SQLite] Failed to load leaderboard data", e);
        }

        return leaderboard;
    }

    /**
     * Check if player has data in the database
     */
    public synchronized boolean hasPlayerData(UUID uuid) {
        try {
            ensureConnection();
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT 1 FROM players WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                return ps.executeQuery().next();
            }
        } catch (SQLException e) {
            return false;
        }
    }

    /**
     * Migrate all YAML player data to SQLite
     */
    public int migrateFromYaml(DataManager yamlManager) {
        Map<UUID, PlayerData> allYaml = yamlManager.getAllPlayerData();
        int migrated = 0;

        for (Map.Entry<UUID, PlayerData> entry : allYaml.entrySet()) {
            if (!hasPlayerData(entry.getKey())) {
                savePlayerData(entry.getKey(), entry.getValue());
                migrated++;
            }
        }

        return migrated;
    }

    // ============================================
    // Dropped Mask Queue Persistence (v2.1.16)
    // ============================================

    /**
     * Data class for dropped mask DB rows.
     */
    public static class DroppedMaskRow {
        public final long id;
        public final String maskId;
        public final int level;
        public final boolean isMystery;
        public final String world;
        public final double x, y, z;
        public final int chunkX, chunkZ;
        public final long droppedAt;

        public DroppedMaskRow(long id, String maskId, int level, boolean isMystery,
                              String world, double x, double y, double z,
                              int chunkX, int chunkZ, long droppedAt) {
            this.id = id;
            this.maskId = maskId;
            this.level = level;
            this.isMystery = isMystery;
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.droppedAt = droppedAt;
        }
    }

    /**
     * Load all unconsumed dropped mask entries from the database.
     */
    public synchronized List<DroppedMaskRow> loadUnconsumedDroppedMasks() {
        List<DroppedMaskRow> results = new ArrayList<>();
        try {
            ensureConnection();
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT id, mask_id, level, is_mystery, world, x, y, z, chunk_x, chunk_z, dropped_at " +
                    "FROM dropped_masks WHERE consumed = 0")) {
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    results.add(new DroppedMaskRow(
                        rs.getLong("id"),
                        rs.getString("mask_id"),
                        rs.getInt("level"),
                        rs.getInt("is_mystery") == 1,
                        rs.getString("world"),
                        rs.getDouble("x"),
                        rs.getDouble("y"),
                        rs.getDouble("z"),
                        rs.getInt("chunk_x"),
                        rs.getInt("chunk_z"),
                        rs.getLong("dropped_at")
                    ));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[SQLite] Failed to load unconsumed dropped masks", e);
        }
        return results;
    }

    /**
     * Insert a single dropped mask entry. Returns the generated row ID, or -1 on failure.
     */
    public synchronized long insertDroppedMask(String maskId, int level, boolean isMystery,
                                                String world, double x, double y, double z,
                                                int chunkX, int chunkZ, long droppedAt) {
        try {
            ensureConnection();
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO dropped_masks (mask_id, level, is_mystery, world, x, y, z, chunk_x, chunk_z, dropped_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, maskId);
                ps.setInt(2, level);
                ps.setInt(3, isMystery ? 1 : 0);
                ps.setString(4, world);
                ps.setDouble(5, x);
                ps.setDouble(6, y);
                ps.setDouble(7, z);
                ps.setInt(8, chunkX);
                ps.setInt(9, chunkZ);
                ps.setLong(10, droppedAt);
                ps.executeUpdate();
                ResultSet keys = ps.getGeneratedKeys();
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[SQLite] Failed to insert dropped mask", e);
        }
        return -1;
    }

    /**
     * Mark specific dropped mask entries as consumed (batch).
     */
    public synchronized void markDroppedMasksConsumed(List<Long> ids) {
        if (ids.isEmpty()) return;
        try {
            ensureConnection();
            connection.setAutoCommit(false);
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE dropped_masks SET consumed = 1 WHERE id = ?")) {
                for (Long id : ids) {
                    ps.setLong(1, id);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            connection.commit();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[SQLite] Failed to mark dropped masks consumed", e);
            try { connection.rollback(); } catch (SQLException ex) { /* ignore */ }
        } finally {
            try { connection.setAutoCommit(true); } catch (SQLException ignored) {}
        }
    }

    /**
     * Delete dropped mask entries for a specific chunk (batch cleanup on chunk unload).
     */
    public synchronized void deleteDroppedMasksByChunk(String world, int chunkX, int chunkZ) {
        try {
            ensureConnection();
            try (PreparedStatement ps = connection.prepareStatement(
                    "DELETE FROM dropped_masks WHERE world = ? AND chunk_x = ? AND chunk_z = ?")) {
                ps.setString(1, world);
                ps.setInt(2, chunkX);
                ps.setInt(3, chunkZ);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[SQLite] Failed to delete dropped masks by chunk", e);
        }
    }

    /**
     * Delete all consumed entries (periodic cleanup).
     */
    public synchronized int purgeConsumedDroppedMasks() {
        try {
            ensureConnection();
            try (PreparedStatement ps = connection.prepareStatement(
                    "DELETE FROM dropped_masks WHERE consumed = 1")) {
                return ps.executeUpdate();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[SQLite] Failed to purge consumed dropped masks", e);
        }
        return 0;
    }

    // ============================================
    // Active Booster Persistence (v2.1.25)
    // ============================================

    /**
     * Data class for booster DB rows.
     */
    public static class BoosterRow {
        public final String id;
        public final BoosterType boosterType;
        public final String scope;
        public final String ownerId;
        public final String ownerName;
        public final double multiplier;
        public final long activatedAt;
        public final long expiresAt;
        public final String activatedByUuid;
        public final String activatedByName;

        public BoosterRow(String id, BoosterType boosterType, String scope, String ownerId,
                          String ownerName, double multiplier, long activatedAt, long expiresAt,
                          String activatedByUuid, String activatedByName) {
            this.id = id;
            this.boosterType = boosterType;
            this.scope = scope;
            this.ownerId = ownerId;
            this.ownerName = ownerName;
            this.multiplier = multiplier;
            this.activatedAt = activatedAt;
            this.expiresAt = expiresAt;
            this.activatedByUuid = activatedByUuid;
            this.activatedByName = activatedByName;
        }
    }

    /**
     * Save or update a booster entry.
     */
    public synchronized void saveBooster(String id, String boosterType, String scope,
                                          String ownerId, String ownerName, double multiplier,
                                          long activatedAt, long expiresAt,
                                          String activatedByUuid, String activatedByName) {
        try {
            ensureConnection();
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT OR REPLACE INTO boosters " +
                    "(id, booster_type, scope, owner_id, owner_name, multiplier, " +
                    "activated_at, expires_at, activated_by_uuid, activated_by_name) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                ps.setString(1, id);
                ps.setString(2, boosterType);
                ps.setString(3, scope);
                ps.setString(4, ownerId);
                ps.setString(5, ownerName);
                ps.setDouble(6, multiplier);
                ps.setLong(7, activatedAt);
                ps.setLong(8, expiresAt);
                ps.setString(9, activatedByUuid);
                ps.setString(10, activatedByName);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[SQLite] Failed to save booster: " + id, e);
        }
    }

    /**
     * Remove a booster entry by id.
     */
    public synchronized void removeBooster(String id) {
        try {
            ensureConnection();
            try (PreparedStatement ps = connection.prepareStatement(
                    "DELETE FROM boosters WHERE id = ?")) {
                ps.setString(1, id);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[SQLite] Failed to remove booster: " + id, e);
        }
    }

    /**
     * Load all non-expired boosters from the database.
     * Also cleans up any expired entries.
     */
    public synchronized List<BoosterRow> loadAllBoosters() {
        List<BoosterRow> results = new ArrayList<>();
        long now = System.currentTimeMillis();

        try {
            ensureConnection();

            // Delete expired boosters first
            try (PreparedStatement ps = connection.prepareStatement(
                    "DELETE FROM boosters WHERE expires_at <= ?")) {
                ps.setLong(1, now);
                int removed = ps.executeUpdate();
                if (removed > 0) {
                    plugin.getLogger().info("[SQLite] Cleaned up " + removed + " expired booster(s) from database.");
                }
            }

            // Load remaining active boosters
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT id, booster_type, scope, owner_id, owner_name, multiplier, " +
                    "activated_at, expires_at, activated_by_uuid, activated_by_name " +
                    "FROM boosters")) {
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    String typeStr = rs.getString("booster_type");
                    BoosterType type = BoosterType.fromString(typeStr);
                    if (type == null) {
                        plugin.getLogger().warning("[SQLite] Unknown booster type '" + typeStr + "' in DB, skipping.");
                        continue;
                    }
                    results.add(new BoosterRow(
                        rs.getString("id"),
                        type,
                        rs.getString("scope"),
                        rs.getString("owner_id"),
                        rs.getString("owner_name"),
                        rs.getDouble("multiplier"),
                        rs.getLong("activated_at"),
                        rs.getLong("expires_at"),
                        rs.getString("activated_by_uuid"),
                        rs.getString("activated_by_name")
                    ));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[SQLite] Failed to load boosters", e);
        }

        return results;
    }

    /**
     * Remove all expired booster entries (periodic cleanup).
     */
    public synchronized int removeExpiredBoosters() {
        try {
            ensureConnection();
            try (PreparedStatement ps = connection.prepareStatement(
                    "DELETE FROM boosters WHERE expires_at <= ?")) {
                ps.setLong(1, System.currentTimeMillis());
                return ps.executeUpdate();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[SQLite] Failed to remove expired boosters", e);
        }
        return 0;
    }

    // ============================================
    // Server Analytics (v2.1.26)
    // ============================================

    /**
     * Load aggregate server-wide analytics from SQLite.
     * Runs efficient aggregate queries — safe to call from async thread.
     */
    public synchronized ServerAnalytics loadServerAnalytics() {
        ServerAnalytics analytics = new ServerAnalytics();

        try {
            ensureConnection();

            // 1. Player count, average level, total XP, economy totals
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT COUNT(*) AS total_players, " +
                    "COALESCE(AVG(CAST(level AS REAL)), 0) AS avg_level, " +
                    "COALESCE(SUM(xp), 0) AS total_xp, " +
                    "COALESCE(SUM(heads_collected), 0) AS total_heads_collected, " +
                    "COALESCE(SUM(heads_sold), 0) AS total_heads_sold, " +
                    "COALESCE(SUM(masks_crafted), 0) AS total_masks_crafted " +
                    "FROM players")) {
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    analytics.setTotalPlayers(rs.getInt("total_players"));
                    analytics.setAverageLevel(rs.getDouble("avg_level"));
                    analytics.setTotalXp(rs.getLong("total_xp"));
                    analytics.setTotalHeadsCollected(rs.getLong("total_heads_collected"));
                    analytics.setTotalHeadsSold(rs.getLong("total_heads_sold"));
                    analytics.setTotalMasksCrafted(rs.getLong("total_masks_crafted"));
                }
            }

            // 2. Level distribution (count per level 1-14)
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT level, COUNT(*) AS cnt FROM players GROUP BY level ORDER BY level ASC")) {
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    analytics.getLevelDistribution().put(rs.getInt("level"), rs.getInt("cnt"));
                }
            }

            // 3. Most popular equipped mask
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT equipped_mask, COUNT(*) AS cnt FROM players " +
                    "WHERE equipped_mask IS NOT NULL AND equipped_mask != '' " +
                    "GROUP BY equipped_mask ORDER BY cnt DESC LIMIT 1")) {
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    analytics.setMostEquippedMask(rs.getString("equipped_mask"));
                }
            }

            // 4. Top 3 most owned masks
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT mask_id, COUNT(*) AS owner_count FROM player_masks " +
                    "WHERE owned = 1 GROUP BY mask_id ORDER BY owner_count DESC LIMIT 3")) {
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    analytics.getTopOwnedMasks().put(rs.getString("mask_id"), rs.getInt("owner_count"));
                }
            }

            // 5. Active booster count
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT COUNT(*) AS cnt FROM boosters WHERE expires_at > ?")) {
                ps.setLong(1, System.currentTimeMillis());
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    analytics.setActiveBoosters(rs.getInt("cnt"));
                }
            }

            // 6. Total missions completed
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT COUNT(*) AS cnt FROM player_missions WHERE completed = 1")) {
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    analytics.setTotalMissionsCompleted(rs.getLong("cnt"));
                }
            }

            // 7. Total daily missions completed
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT COUNT(*) AS cnt FROM player_daily_missions WHERE completed = 1")) {
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    analytics.setTotalDailyMissionsCompleted(rs.getLong("cnt"));
                }
            }

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[SQLite] Failed to load server analytics", e);
        }

        return analytics;
    }

    /**
     * Close the database connection
     */
    public synchronized void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                plugin.getLogger().info("[SQLite] Database connection closed.");
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "[SQLite] Error closing connection", e);
        }
    }
}

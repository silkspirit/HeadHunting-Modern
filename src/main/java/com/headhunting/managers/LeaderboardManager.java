package com.headhunting.managers;

import com.headhunting.HeadHunting;
import com.headhunting.data.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.*;

/**
 * Manages an async-refreshed leaderboard cache.
 * Eliminates main thread blocking when opening the leaderboard GUI.
 */
public class LeaderboardManager {

    /**
     * Lightweight leaderboard entry — stores only what the GUI needs.
     */
    public static class LeaderboardEntry {
        private final UUID uuid;
        private final String playerName;
        private final int level;
        private final int xp;
        private final int headsCollected;
        private final int headsSold;
        private final int masksCrafted;

        public LeaderboardEntry(UUID uuid, String playerName, int level, int xp,
                                int headsCollected, int headsSold, int masksCrafted) {
            this.uuid = uuid;
            this.playerName = playerName;
            this.level = level;
            this.xp = xp;
            this.headsCollected = headsCollected;
            this.headsSold = headsSold;
            this.masksCrafted = masksCrafted;
        }

        public UUID getUuid() { return uuid; }
        public String getPlayerName() { return playerName; }
        public int getLevel() { return level; }
        public int getXp() { return xp; }
        public int getHeadsCollected() { return headsCollected; }
        public int getHeadsSold() { return headsSold; }
        public int getMasksCrafted() { return masksCrafted; }
    }

    private final HeadHunting plugin;
    private final int topCount;
    private final long refreshIntervalTicks;

    /** Volatile reference swap ensures thread-safe reads from main thread. */
    private volatile List<LeaderboardEntry> cachedLeaderboard = Collections.emptyList();
    private BukkitTask refreshTask;

    public LeaderboardManager(HeadHunting plugin) {
        this.plugin = plugin;
        // Could be made configurable later; default top 10, refresh every 5 minutes
        this.topCount = 10;
        this.refreshIntervalTicks = 5 * 60 * 20L; // 5 minutes in ticks

        // Initial async refresh
        refreshAsync();

        // Schedule periodic refresh
        refreshTask = new BukkitRunnable() {
            @Override
            public void run() {
                refreshAsync();
            }
        }.runTaskTimerAsynchronously(plugin, refreshIntervalTicks, refreshIntervalTicks);
    }

    /**
     * Trigger an asynchronous refresh of the leaderboard cache.
     * Safe to call from any thread — the actual work runs on an async Bukkit thread.
     */
    public void refreshAsync() {
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    List<LeaderboardEntry> fresh = loadLeaderboard();
                    // Atomic volatile reference swap — readers see old or new, never partial
                    cachedLeaderboard = Collections.unmodifiableList(fresh);
                } catch (Exception e) {
                    plugin.getLogger().warning("[LeaderboardManager] Failed to refresh leaderboard: " + e.getMessage());
                }
            }
        }.runTaskAsynchronously(plugin);
    }

    /**
     * Returns the cached top players list instantly. Never blocks.
     */
    public List<LeaderboardEntry> getTopPlayers() {
        return cachedLeaderboard;
    }

    /**
     * Get a player's rank in the cached leaderboard.
     * @return 1-based rank, or -1 if the player is not in the top-N cache.
     */
    public int getPlayerRank(UUID uuid) {
        List<LeaderboardEntry> snapshot = cachedLeaderboard;
        for (int i = 0; i < snapshot.size(); i++) {
            if (snapshot.get(i).getUuid().equals(uuid)) {
                return i + 1;
            }
        }
        return -1;
    }

    /**
     * Cancel the refresh task on plugin disable.
     */
    public void shutdown() {
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
    }

    // ============================================
    // Internal loading — runs on async thread
    // ============================================

    private List<LeaderboardEntry> loadLeaderboard() {
        DataManager dataManager = plugin.getDataManager();
        List<LeaderboardEntry> entries = new ArrayList<>();

        if (dataManager.isUsingSqlite()) {
            // Fast path: use the optimized SQLite query
            SQLiteProvider sqlite = dataManager.getSqliteProvider();
            if (sqlite != null) {
                List<PlayerData> topData = sqlite.loadLeaderboardData(topCount);
                for (PlayerData pd : topData) {
                    String name = resolvePlayerName(pd.getUuid());
                    entries.add(new LeaderboardEntry(
                        pd.getUuid(),
                        name,
                        pd.getLevel(),
                        pd.getXp(),
                        pd.getTotalHeadsCollected(),
                        pd.getTotalHeadsSold(),
                        pd.getTotalMasksCrafted()
                    ));
                }
                return entries;
            }
        }

        // Fallback: YAML — load all player data and sort
        Map<UUID, PlayerData> allData = dataManager.getAllPlayerData();
        List<Map.Entry<UUID, PlayerData>> sorted = new ArrayList<>(allData.entrySet());
        sorted.sort((a, b) -> {
            int levelCmp = Integer.compare(b.getValue().getLevel(), a.getValue().getLevel());
            if (levelCmp != 0) return levelCmp;
            return Integer.compare(b.getValue().getXp(), a.getValue().getXp());
        });

        int count = Math.min(topCount, sorted.size());
        for (int i = 0; i < count; i++) {
            Map.Entry<UUID, PlayerData> e = sorted.get(i);
            PlayerData pd = e.getValue();
            String name = resolvePlayerName(e.getKey());
            entries.add(new LeaderboardEntry(
                e.getKey(),
                name,
                pd.getLevel(),
                pd.getXp(),
                pd.getTotalHeadsCollected(),
                pd.getTotalHeadsSold(),
                pd.getTotalMasksCrafted()
            ));
        }

        return entries;
    }

    /**
     * Resolve a player name from UUID. Safe to call from async thread
     * (Bukkit.getOfflinePlayer is thread-safe for cached/known players).
     */
    private String resolvePlayerName(UUID uuid) {
        try {
            OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
            return op.getName() != null ? op.getName() : "Unknown";
        } catch (Exception e) {
            return "Unknown";
        }
    }
}

package com.headhunting.managers;

import com.headhunting.HeadHunting;
import com.headhunting.data.LevelConfig;
import com.headhunting.data.PlayerData;
import com.headhunting.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

/**
 * Manages boss bar progress display for HeadHunting levels.
 * Uses the native Bukkit BossBar API (1.9+, fully supported in Paper 1.20.4).
 */
public class BossBarManager {

    private final HeadHunting plugin;
    private final Map<UUID, BossBar> activeBars = new HashMap<>();
    private final Set<UUID> enabledPlayers = new HashSet<>();
    private BukkitTask updateTask;

    public BossBarManager(HeadHunting plugin) {
        this.plugin = plugin;
        startUpdateTask();
    }

    /**
     * Toggle boss bar for a player
     */
    public boolean toggle(Player player) {
        UUID uuid = player.getUniqueId();
        if (enabledPlayers.contains(uuid)) {
            enabledPlayers.remove(uuid);
            removeBossBar(player);
            return false;
        } else {
            enabledPlayers.add(uuid);
            updateBossBar(player);
            return true;
        }
    }

    /**
     * Check if player has boss bar enabled
     */
    public boolean isEnabled(Player player) {
        return enabledPlayers.contains(player.getUniqueId());
    }

    /**
     * Enable boss bar for a player
     */
    public void enable(Player player) {
        enabledPlayers.add(player.getUniqueId());
        updateBossBar(player);
    }

    /**
     * Disable boss bar for a player
     */
    public void disable(Player player) {
        enabledPlayers.remove(player.getUniqueId());
        removeBossBar(player);
    }

    /**
     * Update the boss bar for a player
     */
    public void updateBossBar(Player player) {
        if (!enabledPlayers.contains(player.getUniqueId())) {
            return;
        }

        PlayerData data = plugin.getDataManager().getPlayerData(player);
        int currentLevel = data.getLevel();
        int currentXp = data.getXp();

        LevelConfig nextLevelConfig = plugin.getConfigManager().getLevelConfig(currentLevel + 1);

        String title;
        double progress;

        if (nextLevelConfig == null) {
            // Max level
            title = MessageUtil.color("&3Level " + currentLevel + " &8| &b&lMAX LEVEL");
            progress = 1.0;
        } else {
            int xpNeeded = nextLevelConfig.getXpRequired();
            LevelConfig currentLevelConfig = plugin.getConfigManager().getLevelConfig(currentLevel);
            int baseXp = currentLevelConfig != null ? currentLevelConfig.getXpRequired() : 0;

            int xpIntoLevel = currentXp - baseXp;
            int xpForLevel = xpNeeded - baseXp;

            progress = Math.max(0.0, Math.min(1.0, (double) xpIntoLevel / xpForLevel));

            String progressBar = createProgressBar((float) progress, 20);
            title = MessageUtil.color("&3Level " + currentLevel + " &8| " + progressBar + " &8| &b" + xpIntoLevel + "/" + xpForLevel + " XP");
        }

        BossBar bar = activeBars.get(player.getUniqueId());
        if (bar == null) {
            bar = Bukkit.createBossBar(title, BarColor.BLUE, BarStyle.SOLID);
            bar.addPlayer(player);
            activeBars.put(player.getUniqueId(), bar);
        } else {
            bar.setTitle(title);
        }
        bar.setProgress(progress);
    }

    /**
     * Create a text-based progress bar
     */
    private String createProgressBar(float progress, int length) {
        int filled = (int) (progress * length);
        int empty = length - filled;

        StringBuilder bar = new StringBuilder();
        bar.append("&b");
        for (int i = 0; i < filled; i++) {
            bar.append("|");
        }
        bar.append("&8");
        for (int i = 0; i < empty; i++) {
            bar.append("|");
        }

        return bar.toString();
    }

    /**
     * Remove boss bar for a player
     */
    public void removeBossBar(Player player) {
        BossBar bar = activeBars.remove(player.getUniqueId());
        if (bar != null) {
            bar.removePlayer(player);
            bar.setVisible(false);
        }
    }

    /**
     * Start the update task
     */
    private void startUpdateTask() {
        updateTask = new BukkitRunnable() {
            @Override
            public void run() {
                for (UUID uuid : new HashSet<>(enabledPlayers)) {
                    Player player = Bukkit.getPlayer(uuid);
                    if (player != null && player.isOnline()) {
                        updateBossBar(player);
                    } else {
                        enabledPlayers.remove(uuid);
                        BossBar bar = activeBars.remove(uuid);
                        if (bar != null) bar.setVisible(false);
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 20L); // Update every second
    }

    /**
     * Clean up on disable
     */
    public void cleanup() {
        if (updateTask != null) {
            updateTask.cancel();
        }
        for (Map.Entry<UUID, BossBar> entry : activeBars.entrySet()) {
            entry.getValue().setVisible(false);
        }
        activeBars.clear();
        enabledPlayers.clear();
    }

    /**
     * Load enabled players from data
     */
    public void loadPlayer(Player player) {
        PlayerData data = plugin.getDataManager().getPlayerData(player);
        if (data.isBossBarEnabled()) {
            enabledPlayers.add(player.getUniqueId());
        }
    }

    /**
     * Save player preference
     */
    public void savePlayer(Player player) {
        PlayerData data = plugin.getDataManager().getPlayerData(player);
        data.setBossBarEnabled(enabledPlayers.contains(player.getUniqueId()));
    }
}

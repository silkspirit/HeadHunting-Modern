package com.headhunting.data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Data class holding server-wide analytics computed from SQLite aggregate queries.
 */
public class ServerAnalytics {

    // Player stats
    private int totalPlayers;
    private double averageLevel;
    private long totalXp;
    private final Map<Integer, Integer> levelDistribution = new LinkedHashMap<>(); // level -> count

    // Economy
    private long totalHeadsCollected;
    private long totalHeadsSold;
    private long totalMasksCrafted;

    // Masks
    private String mostEquippedMask; // most common equipped_mask value
    private final Map<String, Integer> topOwnedMasks = new LinkedHashMap<>(); // mask_id -> owner count (top 3)

    // Progression
    private int activeBoosters;
    private long totalMissionsCompleted;
    private long totalDailyMissionsCompleted;

    public int getTotalPlayers() { return totalPlayers; }
    public void setTotalPlayers(int totalPlayers) { this.totalPlayers = totalPlayers; }

    public double getAverageLevel() { return averageLevel; }
    public void setAverageLevel(double averageLevel) { this.averageLevel = averageLevel; }

    public long getTotalXp() { return totalXp; }
    public void setTotalXp(long totalXp) { this.totalXp = totalXp; }

    public Map<Integer, Integer> getLevelDistribution() { return levelDistribution; }

    public long getTotalHeadsCollected() { return totalHeadsCollected; }
    public void setTotalHeadsCollected(long totalHeadsCollected) { this.totalHeadsCollected = totalHeadsCollected; }

    public long getTotalHeadsSold() { return totalHeadsSold; }
    public void setTotalHeadsSold(long totalHeadsSold) { this.totalHeadsSold = totalHeadsSold; }

    public long getTotalMasksCrafted() { return totalMasksCrafted; }
    public void setTotalMasksCrafted(long totalMasksCrafted) { this.totalMasksCrafted = totalMasksCrafted; }

    public String getMostEquippedMask() { return mostEquippedMask; }
    public void setMostEquippedMask(String mostEquippedMask) { this.mostEquippedMask = mostEquippedMask; }

    public Map<String, Integer> getTopOwnedMasks() { return topOwnedMasks; }

    public int getActiveBoosters() { return activeBoosters; }
    public void setActiveBoosters(int activeBoosters) { this.activeBoosters = activeBoosters; }

    public long getTotalMissionsCompleted() { return totalMissionsCompleted; }
    public void setTotalMissionsCompleted(long totalMissionsCompleted) { this.totalMissionsCompleted = totalMissionsCompleted; }

    public long getTotalDailyMissionsCompleted() { return totalDailyMissionsCompleted; }
    public void setTotalDailyMissionsCompleted(long totalDailyMissionsCompleted) { this.totalDailyMissionsCompleted = totalDailyMissionsCompleted; }
}

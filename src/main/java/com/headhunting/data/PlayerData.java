package com.headhunting.data;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Represents a player's HeadHunting data
 */
public class PlayerData {
    
    private final UUID uuid;
    private int level;
    private int xp;
    private final Map<String, MaskData> masks;
    private final Map<String, MissionProgress> missions;
    private final Map<String, DailyMissionProgress> dailyMissions;
    private String equippedMask;
    private boolean bossBarEnabled;
    private String lastDailyReset; // Track last reset date
    
    // Lifetime stats
    private int totalHeadsCollected;
    private int totalHeadsSold;
    private int totalMasksCrafted;
    
    public PlayerData(UUID uuid) {
        this.uuid = uuid;
        this.level = 1;
        this.xp = 0;
        this.masks = new HashMap<>();
        this.missions = new HashMap<>();
        this.dailyMissions = new HashMap<>();
        this.equippedMask = null;
        this.bossBarEnabled = false;
        this.lastDailyReset = "";
        this.totalHeadsCollected = 0;
        this.totalHeadsSold = 0;
        this.totalMasksCrafted = 0;
    }
    
    public boolean isBossBarEnabled() {
        return bossBarEnabled;
    }
    
    public void setBossBarEnabled(boolean enabled) {
        this.bossBarEnabled = enabled;
    }
    
    // Lifetime stats getters/setters
    public int getTotalHeadsCollected() {
        return totalHeadsCollected;
    }
    
    public void addHeadsCollected(int amount) {
        this.totalHeadsCollected += amount;
    }
    
    public int getTotalHeadsSold() {
        return totalHeadsSold;
    }
    
    public void addHeadsSold(int amount) {
        this.totalHeadsSold += amount;
    }
    
    public int getTotalMasksCrafted() {
        return totalMasksCrafted;
    }
    
    public void incrementMasksCrafted() {
        this.totalMasksCrafted++;
    }
    
    public void setTotalHeadsCollected(int total) {
        this.totalHeadsCollected = total;
    }
    
    public void setTotalHeadsSold(int total) {
        this.totalHeadsSold = total;
    }
    
    public void setTotalMasksCrafted(int total) {
        this.totalMasksCrafted = total;
    }
    
    public UUID getUuid() {
        return uuid;
    }
    
    public int getLevel() {
        return level;
    }
    
    public void setLevel(int level) {
        this.level = level;
    }
    
    public int getXp() {
        return xp;
    }
    
    public void setXp(int xp) {
        this.xp = xp;
    }
    
    public void addXp(int amount) {
        this.xp += amount;
    }
    
    public Map<String, MaskData> getMasks() {
        return masks;
    }
    
    public MaskData getMaskData(String maskId) {
        return masks.get(maskId.toLowerCase());
    }
    
    public void setMaskData(String maskId, MaskData data) {
        masks.put(maskId.toLowerCase(), data);
    }
    
    public boolean ownsMask(String maskId) {
        MaskData data = masks.get(maskId.toLowerCase());
        return data != null && data.isOwned();
    }
    
    public int getMaskLevel(String maskId) {
        MaskData data = masks.get(maskId.toLowerCase());
        return data != null ? data.getLevel() : 0;
    }
    
    public void setMaskOwned(String maskId, boolean owned) {
        MaskData data = masks.computeIfAbsent(maskId.toLowerCase(), k -> new MaskData());
        data.setOwned(owned);
    }
    
    public void setMaskLevel(String maskId, int level) {
        MaskData data = masks.computeIfAbsent(maskId.toLowerCase(), k -> new MaskData());
        data.setLevel(level);
    }
    
    public int getDepositedHeads(String maskId) {
        MaskData data = masks.get(maskId.toLowerCase());
        return data != null ? data.getDepositedHeads() : 0;
    }
    
    public void addDepositedHeads(String maskId, int amount) {
        MaskData data = masks.computeIfAbsent(maskId.toLowerCase(), k -> new MaskData());
        data.addDepositedHeads(amount);
    }
    
    public Map<String, MissionProgress> getMissions() {
        return missions;
    }
    
    public MissionProgress getMissionProgress(String missionId) {
        return missions.computeIfAbsent(missionId.toLowerCase(), k -> new MissionProgress());
    }
    
    public String getEquippedMask() {
        return equippedMask;
    }
    
    public void setEquippedMask(String maskId) {
        this.equippedMask = maskId != null ? maskId.toLowerCase() : null;
    }
    
    public boolean hasMaskEquipped() {
        return equippedMask != null;
    }
    
    // ============================================
    // DAILY MISSION METHODS
    // ============================================
    
    public Map<String, DailyMissionProgress> getDailyMissions() {
        return dailyMissions;
    }
    
    public int getDailyMissionProgress(String missionId) {
        DailyMissionProgress progress = dailyMissions.get(missionId.toLowerCase());
        return progress != null ? progress.getProgress() : 0;
    }
    
    public void setDailyMissionProgress(String missionId, int amount) {
        DailyMissionProgress progress = dailyMissions.computeIfAbsent(
            missionId.toLowerCase(), k -> new DailyMissionProgress());
        progress.setProgress(amount);
    }
    
    public boolean isDailyMissionComplete(String missionId) {
        DailyMissionProgress progress = dailyMissions.get(missionId.toLowerCase());
        return progress != null && progress.isCompleted();
    }
    
    public void setDailyMissionComplete(String missionId, boolean complete) {
        DailyMissionProgress progress = dailyMissions.computeIfAbsent(
            missionId.toLowerCase(), k -> new DailyMissionProgress());
        progress.setCompleted(complete);
    }
    
    public void resetDailyMissions() {
        dailyMissions.clear();
    }
    
    public String getLastDailyReset() {
        return lastDailyReset;
    }
    
    public void setLastDailyReset(String date) {
        this.lastDailyReset = date;
    }
    
    /**
     * Represents progress for a daily mission
     */
    public static class DailyMissionProgress {
        private int progress;
        private boolean completed;
        
        public DailyMissionProgress() {
            this.progress = 0;
            this.completed = false;
        }
        
        public int getProgress() { return progress; }
        public void setProgress(int progress) { this.progress = progress; }
        public boolean isCompleted() { return completed; }
        public void setCompleted(boolean completed) { this.completed = completed; }
    }
    
    /**
     * Represents data for a single mask
     */
    public static class MaskData {
        private boolean owned;
        private int level;
        private int depositedHeads;
        
        public MaskData() {
            this.owned = false;
            this.level = 0;
            this.depositedHeads = 0;
        }
        
        public MaskData(boolean owned, int level, int depositedHeads) {
            this.owned = owned;
            this.level = level;
            this.depositedHeads = depositedHeads;
        }
        
        public boolean isOwned() {
            return owned;
        }
        
        public void setOwned(boolean owned) {
            this.owned = owned;
            if (owned && level == 0) {
                level = 1;
            }
        }
        
        public int getLevel() {
            return level;
        }
        
        public void setLevel(int level) {
            this.level = level;
        }
        
        public int getDepositedHeads() {
            return depositedHeads;
        }
        
        public void setDepositedHeads(int depositedHeads) {
            this.depositedHeads = depositedHeads;
        }
        
        public void addDepositedHeads(int amount) {
            this.depositedHeads += amount;
        }
    }
    
    /**
     * Represents progress for a divine mission
     */
    public static class MissionProgress {
        private int progress;
        private boolean completed;
        
        public MissionProgress() {
            this.progress = 0;
            this.completed = false;
        }
        
        public MissionProgress(int progress, boolean completed) {
            this.progress = progress;
            this.completed = completed;
        }
        
        public int getProgress() {
            return progress;
        }
        
        public void setProgress(int progress) {
            this.progress = progress;
        }
        
        public void addProgress(int amount) {
            this.progress += amount;
        }
        
        public boolean isCompleted() {
            return completed;
        }
        
        public void setCompleted(boolean completed) {
            this.completed = completed;
        }
    }
}

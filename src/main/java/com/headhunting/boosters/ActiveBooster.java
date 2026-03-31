package com.headhunting.boosters;

import java.util.UUID;

/**
 * Represents an active booster (personal or faction)
 */
public class ActiveBooster {
    
    private final BoosterType type;
    private final double multiplier;
    private final long activatedAt;
    private final long expiresAt;
    private final UUID activatedBy;
    private final String activatorName;
    
    /**
     * Create a new booster (activated now).
     */
    public ActiveBooster(BoosterType type, double multiplier, long durationMs, UUID activatedBy, String activatorName) {
        this.type = type;
        this.multiplier = multiplier;
        this.activatedAt = System.currentTimeMillis();
        this.expiresAt = this.activatedAt + durationMs;
        this.activatedBy = activatedBy;
        this.activatorName = activatorName;
    }
    
    /**
     * Restore a booster from persistence (with exact epoch timestamps).
     */
    public ActiveBooster(BoosterType type, double multiplier, long activatedAt, long expiresAt, UUID activatedBy, String activatorName) {
        this.type = type;
        this.multiplier = multiplier;
        this.activatedAt = activatedAt;
        this.expiresAt = expiresAt;
        this.activatedBy = activatedBy;
        this.activatorName = activatorName;
    }
    
    public BoosterType getType() {
        return type;
    }
    
    public double getMultiplier() {
        return multiplier;
    }
    
    public long getActivatedAt() {
        return activatedAt;
    }
    
    public long getExpiresAt() {
        return expiresAt;
    }
    
    public UUID getActivatedBy() {
        return activatedBy;
    }
    
    public String getActivatorName() {
        return activatorName;
    }
    
    public boolean isExpired() {
        return System.currentTimeMillis() >= expiresAt;
    }
    
    public long getRemainingMs() {
        return Math.max(0, expiresAt - System.currentTimeMillis());
    }
    
    public String getRemainingFormatted() {
        long remaining = getRemainingMs();
        long seconds = (remaining / 1000) % 60;
        long minutes = (remaining / (1000 * 60)) % 60;
        long hours = remaining / (1000 * 60 * 60);
        
        if (hours > 0) {
            return String.format("%dh %dm %ds", hours, minutes, seconds);
        } else if (minutes > 0) {
            return String.format("%dm %ds", minutes, seconds);
        } else {
            return String.format("%ds", seconds);
        }
    }
}

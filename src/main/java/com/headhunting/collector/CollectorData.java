package com.headhunting.collector;

import org.bukkit.Location;
import org.bukkit.entity.EntityType;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents a single Head Collector block and its stored heads.
 * Uses string keys to support 1.8 sub-types (WITHER_SKELETON, ELDER_GUARDIAN, etc.)
 */
public class CollectorData {

    private final Location location;
    /** head key (e.g. "SKELETON", "WITHER_SKELETON") -> count of stored heads */
    private final Map<String, Integer> heads = new HashMap<>();

    public CollectorData(Location location) {
        this.location = location;
    }

    public Location getLocation() {
        return location;
    }

    /** Add heads by string key (supports sub-types). */
    public void addHeads(String headKey, int amount) {
        heads.merge(headKey, amount, Integer::sum);
    }

    /** Backward-compat: add heads by EntityType. */
    public void addHeads(EntityType type, int amount) {
        addHeads(type.name(), amount);
    }

    /** Remove up to {@code amount} heads of the given key and return actual removed. */
    public int removeHeads(String headKey, int amount) {
        Integer current = heads.get(headKey);
        if (current == null || current <= 0) return 0;
        int removed = Math.min(current, amount);
        if (removed == current) {
            heads.remove(headKey);
        } else {
            heads.put(headKey, current - removed);
        }
        return removed;
    }

    /** Backward-compat: remove heads by EntityType. */
    public int removeHeads(EntityType type, int amount) {
        return removeHeads(type.name(), amount);
    }

    public Map<String, Integer> getHeads() {
        return heads;
    }

    public int getTotalHeads() {
        return heads.values().stream().mapToInt(Integer::intValue).sum();
    }

    /** A serialisable string key for this collector's chunk: "world:chunkX:chunkZ" */
    public static String chunkKey(Location loc) {
        return loc.getWorld().getName()
                + ":" + loc.getBlockX()
                + ":" + loc.getBlockY()
                + ":" + loc.getBlockZ();
    }

    public String locationKey() {
        return chunkKey(location);
    }
}

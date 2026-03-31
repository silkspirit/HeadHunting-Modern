package com.headhunting.collector;

import com.headhunting.HeadHunting;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages all Head Collector block data and persistence.
 *
 * <p>Storage: plugins/HeadHunting/collectors.yml
 * <p>In-memory: a map keyed by "world:x:y:z" → CollectorData
 */
public class CollectorManager {

    private final HeadHunting plugin;
    private final File dataFile;
    private YamlConfiguration yaml;

    /** location-key → CollectorData */
    private final Map<String, CollectorData> collectors = new ConcurrentHashMap<>();

    public CollectorManager(HeadHunting plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "collectors.yml");
        load();
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /** Register a new collector at {@code loc}. */
    public CollectorData place(Location loc) {
        String key = CollectorData.chunkKey(loc);
        if (collectors.containsKey(key)) {
            return collectors.get(key);
        }
        CollectorData data = new CollectorData(loc);
        collectors.put(key, data);
        save();
        return data;
    }

    /** Remove a collector at {@code loc}. Returns true if one existed. */
    public boolean remove(Location loc) {
        String key = CollectorData.chunkKey(loc);
        boolean removed = collectors.remove(key) != null;
        if (removed) save();
        return removed;
    }

    /** Get the collector at an exact block location, or null. */
    public CollectorData getAt(Location loc) {
        return collectors.get(CollectorData.chunkKey(loc));
    }

    /** Find the collector for a chunk (used when catching item drops). */
    public CollectorData getInChunk(org.bukkit.Chunk chunk) {
        for (CollectorData cd : collectors.values()) {
            Location l = cd.getLocation();
            if (l.getWorld().equals(chunk.getWorld())
                    && l.getBlockX() >> 4 == chunk.getX()
                    && l.getBlockZ() >> 4 == chunk.getZ()) {
                return cd;
            }
        }
        return null;
    }

    public Collection<CollectorData> getAllCollectors() {
        return collectors.values();
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    public void save() {
        YamlConfiguration out = new YamlConfiguration();
        for (Map.Entry<String, CollectorData> entry : collectors.entrySet()) {
            String base = "collectors." + entry.getKey() + ".";
            Location loc = entry.getValue().getLocation();
            out.set(base + "world", loc.getWorld().getName());
            out.set(base + "x", loc.getBlockX());
            out.set(base + "y", loc.getBlockY());
            out.set(base + "z", loc.getBlockZ());
            Map<String, Object> headMap = new LinkedHashMap<>();
            for (Map.Entry<String, Integer> h : entry.getValue().getHeads().entrySet()) {
                headMap.put(h.getKey(), h.getValue());
            }
            out.set(base + "heads", headMap);
        }
        try {
            out.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save collectors.yml: " + e.getMessage());
        }
    }

    private void load() {
        if (!dataFile.exists()) return;
        yaml = YamlConfiguration.loadConfiguration(dataFile);
        if (!yaml.isConfigurationSection("collectors")) return;

        for (String key : yaml.getConfigurationSection("collectors").getKeys(false)) {
            String base = "collectors." + key + ".";
            String worldName = yaml.getString(base + "world");
            int x = yaml.getInt(base + "x");
            int y = yaml.getInt(base + "y");
            int z = yaml.getInt(base + "z");

            World world = Bukkit.getWorld(worldName);
            if (world == null) continue; // world not loaded yet – skip

            Location loc = new Location(world, x, y, z);
            CollectorData cd = new CollectorData(loc);

            // Load stored heads (string keys — supports sub-types)
            if (yaml.isConfigurationSection(base + "heads")) {
                for (String headKey : yaml.getConfigurationSection(base + "heads").getKeys(false)) {
                    int count = yaml.getInt(base + "heads." + headKey);
                    if (count > 0) cd.addHeads(headKey, count);
                }
            }

            collectors.put(cd.locationKey(), cd);
        }

        plugin.getLogger().info("Loaded " + collectors.size() + " head collector(s).");
    }
}

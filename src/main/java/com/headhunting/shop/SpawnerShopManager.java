package com.headhunting.shop;

import com.headhunting.HeadHunting;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;

/**
 * Loads and exposes the Spawner Shop configuration (spawner-shop.yml).
 */
public class SpawnerShopManager {

    private final HeadHunting plugin;

    private String guiTitle;
    private int guiSize;
    private int purchaseCooldownTicks;
    private final List<SpawnerEntry> entries = new ArrayList<>();

    public SpawnerShopManager(HeadHunting plugin) {
        this.plugin = plugin;
        load();
    }

    // ── Load ──────────────────────────────────────────────────────────────────

    public void load() {
        entries.clear();

        File file = new File(plugin.getDataFolder(), "spawner-shop.yml");
        if (!file.exists()) {
            plugin.saveResource("spawner-shop.yml", false);
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(file);

        guiTitle = config.getString("gui-title", "&5&l✦ Spawner Shop ✦");
        guiSize = config.getInt("gui-size", 54);
        purchaseCooldownTicks = config.getInt("purchase-cooldown-ticks", 60);

        ConfigurationSection section = config.getConfigurationSection("spawners");
        if (section == null) {
            plugin.getLogger().warning("[SpawnerShop] No 'spawners' section found in spawner-shop.yml!");
            return;
        }

        int nextAutoSlot = 0;
        for (String key : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(key);
            if (entry == null) continue;

            try {
                String mobType = entry.getString("mob-type", key.toUpperCase());
                String displayName = entry.getString("display-name", "&f" + key + " Spawner");
                double price = entry.getDouble("price", 0.0);
                int requiredLevel = entry.getInt("required-level", 1);
                int slot = entry.getInt("slot", -1);

                if (slot == -1) {
                    slot = nextAutoSlot++;
                }

                entries.add(new SpawnerEntry(key, mobType.toUpperCase(), displayName, price, requiredLevel, slot));
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING,
                        "[SpawnerShop] Failed to load spawner entry '" + key + "': " + e.getMessage());
            }
        }

        plugin.getLogger().info("[SpawnerShop] Loaded " + entries.size() + " spawner entries.");
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public String getGuiTitle() { return guiTitle; }
    public int getGuiSize() { return guiSize; }
    public int getPurchaseCooldownTicks() { return purchaseCooldownTicks; }

    /** Unmodifiable view of all configured spawner entries. */
    public List<SpawnerEntry> getEntries() {
        return Collections.unmodifiableList(entries);
    }
}

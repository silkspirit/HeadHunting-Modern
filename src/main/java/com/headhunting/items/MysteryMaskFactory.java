package com.headhunting.items;

import com.headhunting.HeadHunting;
import com.headhunting.data.MaskConfig;
import com.headhunting.utils.MessageUtil;
import com.headhunting.utils.NBTUtil;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.io.File;
import java.util.*;

/**
 * Factory for Mystery Mask consumables
 */
public class MysteryMaskFactory {
    
    private final HeadHunting plugin;
    private final Map<String, RarityConfig> rarities = new HashMap<>();
    
    private static final String MYSTERY_NBT_KEY = "HeadHunting_Mystery";
    
    // Rarity colors and textures
    private static final Map<String, String> RARITY_COLORS = new HashMap<>();
    private static final Map<String, String> RARITY_TEXTURES = new HashMap<>();
    
    static {
        RARITY_COLORS.put("common", "&f");
        RARITY_COLORS.put("uncommon", "&a");
        RARITY_COLORS.put("rare", "&9");
        RARITY_COLORS.put("epic", "&5");
        RARITY_COLORS.put("legendary", "&6");
        RARITY_COLORS.put("divine", "&d");
        
        // Mystery box textures - Oak Wood Question Mark
        String mysteryBoxTexture = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYmFkYzA0OGE3Y2U3OGY3ZGFkNzJhMDdkYTI3ZDg1YzA5MTY4ODFlNTUyMmVlZWQxZTNkYWYyMTdhMzhjMWEifX19";
        RARITY_TEXTURES.put("common", mysteryBoxTexture);
        RARITY_TEXTURES.put("uncommon", mysteryBoxTexture);
        RARITY_TEXTURES.put("rare", mysteryBoxTexture);
        RARITY_TEXTURES.put("epic", mysteryBoxTexture);
        RARITY_TEXTURES.put("legendary", mysteryBoxTexture);
        RARITY_TEXTURES.put("divine", mysteryBoxTexture);
    }
    
    public MysteryMaskFactory(HeadHunting plugin) {
        this.plugin = plugin;
        loadConfig();
    }
    
    public void loadConfig() {
        rarities.clear();
        
        File file = new File(plugin.getDataFolder(), "mystery-masks.yml");
        if (!file.exists()) {
            plugin.saveResource("mystery-masks.yml", false);
        }
        
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection raritiesSection = config.getConfigurationSection("rarities");
        
        if (raritiesSection != null) {
            for (String rarity : raritiesSection.getKeys(false)) {
                ConfigurationSection raritySection = raritiesSection.getConfigurationSection(rarity);
                if (raritySection != null) {
                    RarityConfig rarityConfig = new RarityConfig(rarity, raritySection);
                    rarities.put(rarity.toLowerCase(), rarityConfig);
                }
            }
        }
        
        plugin.getLogger().info("Loaded " + rarities.size() + " mystery mask rarities.");
    }
    
    /**
     * Create a mystery mask item with unique ID to prevent stacking
     */
    public ItemStack createMysteryMask(String rarity) {
        rarity = rarity.toLowerCase();
        RarityConfig config = rarities.get(rarity);
        
        if (config == null) {
            plugin.getLogger().warning("Unknown mystery mask rarity: " + rarity);
            return null;
        }
        
        // Generate unique ID for this specific mystery mask instance
        String uniqueId = generateUniqueId();
        
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        
        // Apply texture with unique UUID to prevent stacking
        String texture = RARITY_TEXTURES.getOrDefault(rarity, RARITY_TEXTURES.get("common"));
        applyTexture(meta, texture, uniqueId);
        
        // Set display name
        String color = RARITY_COLORS.getOrDefault(rarity, "&f");
        meta.setDisplayName(MessageUtil.color(color + "&l" + capitalize(rarity) + " Mystery Mask"));
        
        // Set lore
        List<String> lore = new ArrayList<>();
        lore.add(MessageUtil.color("&7"));
        lore.add(MessageUtil.color("&7Right-click to reveal a random mask!"));
        lore.add(MessageUtil.color("&7"));
        lore.add(MessageUtil.color(color + "Rarity: " + capitalize(rarity)));
        lore.add(MessageUtil.color("&7"));
        lore.add(MessageUtil.color("&8&o" + MYSTERY_NBT_KEY + ":" + rarity));
        meta.setLore(lore);
        
        item.setItemMeta(meta);
        
        // Add unique NBT tag via NMS + asCraftMirror (bypasses CraftMetaSkull serialization)
        ItemStack result = NBTUtil.addUniqueId(item);
        
        // Log NBT for debugging
        if (plugin.getConfigManager().isDebugEnabled()) {
            plugin.getLogger().info("[MysteryMaskFactory] rarity=" + rarity 
                + " class=" + result.getClass().getSimpleName());
        }
        
        return result;
    }
    
    /**
     * Generate a short unique ID for mystery mask instance
     */
    private String generateUniqueId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
    
    /**
     * Check if item is a mystery mask
     */
    public boolean isMysteryMask(ItemStack item) {
        if (item == null || item.getType() != Material.PLAYER_HEAD) {
            return false;
        }
        
        ItemMeta meta = item.getItemMeta();
        if (meta == null || meta.getLore() == null) {
            return false;
        }
        
        for (String line : meta.getLore()) {
            String stripped = org.bukkit.ChatColor.stripColor(line);
            // Check both new and old formats
            if (stripped.contains(MYSTERY_NBT_KEY) || stripped.contains("ID: mystery_mask_")) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Get rarity from mystery mask item
     */
    public String getRarity(ItemStack item) {
        if (!isMysteryMask(item)) {
            return null;
        }
        
        for (String line : item.getItemMeta().getLore()) {
            String stripped = org.bukkit.ChatColor.stripColor(line);
            
            // New format: HeadHunting_Mystery:rarity:uniqueId
            if (stripped.contains(MYSTERY_NBT_KEY)) {
                String[] parts = stripped.split(":");
                if (parts.length >= 2) {
                    return parts[1].trim();
                }
            }
            
            // Old format: ID: mystery_mask_rarity
            if (stripped.contains("ID: mystery_mask_")) {
                return stripped.replace("ID: mystery_mask_", "").trim();
            }
        }
        return null;
    }
    
    /**
     * Open a mystery mask and give random mask to player
     */
    public ItemStack openMysteryMask(Player player, String rarity) {
        RarityConfig config = rarities.get(rarity.toLowerCase());
        if (config == null) {
            return null;
        }
        
        // Roll for mask
        String maskId = config.rollMask();
        if (maskId == null) {
            return null;
        }
        
        // Determine mask level (1-3 based on rarity)
        int maskLevel = 1;
        Random random = new Random();
        if (rarity.equalsIgnoreCase("legendary") || rarity.equalsIgnoreCase("divine")) {
            maskLevel = random.nextInt(3) + 1; // 1-3
        } else if (rarity.equalsIgnoreCase("epic") || rarity.equalsIgnoreCase("rare")) {
            maskLevel = random.nextInt(2) + 1; // 1-2
        }
        
        return plugin.getMaskFactory().createMask(maskId, maskLevel);
    }
    
    public Set<String> getRarityNames() {
        return rarities.keySet();
    }
    
    /**
     * Apply texture with unique UUID to prevent stacking
     */
    private void applyTexture(SkullMeta meta, String texture, String uniqueId) {
        try {
            java.lang.reflect.Field profileField = meta.getClass().getDeclaredField("profile");
            profileField.setAccessible(true);
            
            // Use UNIQUE UUID for each mystery mask - prevents ANY stacking
            UUID uniqueUUID = UUID.nameUUIDFromBytes(("HeadHunting_Mystery_" + uniqueId).getBytes());
            com.mojang.authlib.GameProfile profile = new com.mojang.authlib.GameProfile(
                uniqueUUID, "HHMystery");
            profile.getProperties().put("textures",
                new com.mojang.authlib.properties.Property("textures", texture));
            
            profileField.set(meta, profile);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to set mystery mask texture: " + e.getMessage());
        }
    }
    
    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1).toLowerCase();
    }
    
    /**
     * Rarity configuration with mask chances
     */
    public static class RarityConfig {
        private final String name;
        private final Map<String, Double> maskChances = new HashMap<>();
        private double totalWeight = 0;
        
        public RarityConfig(String name, ConfigurationSection section) {
            this.name = name;
            
            ConfigurationSection masksSection = section.getConfigurationSection("masks");
            if (masksSection != null) {
                for (String maskId : masksSection.getKeys(false)) {
                    double chance = masksSection.getDouble(maskId);
                    maskChances.put(maskId.toLowerCase(), chance);
                    totalWeight += chance;
                }
            }
        }
        
        public String rollMask() {
            if (maskChances.isEmpty() || totalWeight <= 0) {
                return null;
            }
            
            double roll = new Random().nextDouble() * totalWeight;
            double cumulative = 0;
            
            for (Map.Entry<String, Double> entry : maskChances.entrySet()) {
                cumulative += entry.getValue();
                if (roll <= cumulative) {
                    return entry.getKey();
                }
            }
            
            // Fallback
            return maskChances.keySet().iterator().next();
        }
    }
}

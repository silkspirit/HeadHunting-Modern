package com.headhunting.items;

import com.headhunting.HeadHunting;
import com.headhunting.data.HeadConfig;
import com.headhunting.utils.MessageUtil;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Factory for creating mob head items
 */
public class HeadFactory {
    
    private final HeadHunting plugin;
    private static final String HEAD_NBT_KEY = "HeadHunting_Head";
    
    public HeadFactory(HeadHunting plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Create a mob head item by EntityType
     */
    public ItemStack createHead(EntityType entityType, int amount) {
        return createHead(entityType.name(), amount);
    }
    
    /**
     * Create a mob head item by string key (supports sub-types like WITHER_SKELETON)
     */
    public ItemStack createHead(String headKey, int amount) {
        HeadConfig config = plugin.getConfigManager().getHeadConfig(headKey);
        if (config == null) {
            return null;
        }
        
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD, amount);
        SkullMeta meta = (SkullMeta) skull.getItemMeta();
        
        // Set texture
        if (config.getTexture() != null && !config.getTexture().isEmpty()) {
            setSkullTexture(meta, config.getTexture());
        }
        
        // Set display name
        meta.setDisplayName(MessageUtil.color(config.getDisplayName()));
        
        // Set lore
        List<String> lore = new ArrayList<>();
        lore.add(MessageUtil.color("&8━━━━━━━━━━━━━━━━━"));
        lore.add(MessageUtil.color("&7Sell: &a" + plugin.getConfigManager().getCurrencySymbol() + config.getSellPrice()));
        lore.add(MessageUtil.color("&7XP: &b" + config.getXpValue()));
        lore.add(MessageUtil.color("&8━━━━━━━━━━━━━━━━━"));
        lore.add(MessageUtil.color("&8Right-click to consume"));
        if (plugin.getConfigManager().isShiftClickSell()) {
            lore.add(MessageUtil.color("&8Shift-click to sell"));
        }
        
        // Add identifier to lore (hidden) — uses headKey for sub-type support
        lore.add(MessageUtil.color("&8&o" + HEAD_NBT_KEY + ":" + config.getHeadKey()));
        
        meta.setLore(lore);
        skull.setItemMeta(meta);
        
        return skull;
    }
    
    /**
     * Create a single head by EntityType
     */
    public ItemStack createHead(EntityType entityType) {
        return createHead(entityType.name(), 1);
    }
    
    /**
     * Create a single head by string key
     */
    public ItemStack createHead(String headKey) {
        return createHead(headKey, 1);
    }
    
    /**
     * Check if an item is a HeadHunting head
     */
    public boolean isHead(ItemStack item) {
        if (item == null || item.getType() != Material.PLAYER_HEAD) {
            return false;
        }
        if (!item.hasItemMeta() || !item.getItemMeta().hasLore()) {
            return false;
        }
        
        List<String> lore = item.getItemMeta().getLore();
        for (String line : lore) {
            if (line.contains(HEAD_NBT_KEY)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Get the head key string from a head item (e.g. "SKELETON", "WITHER_SKELETON")
     */
    public String getHeadKey(ItemStack item) {
        if (!isHead(item)) {
            return null;
        }
        
        List<String> lore = item.getItemMeta().getLore();
        for (String line : lore) {
            String stripped = MessageUtil.stripColor(line);
            if (stripped.contains(HEAD_NBT_KEY)) {
                String[] parts = stripped.split(":");
                if (parts.length >= 2) {
                    return parts[1].trim();
                }
            }
        }
        return null;
    }
    
    /**
     * Get the entity type from a head item.
     * Returns null for sub-type heads (WITHER_SKELETON etc.) that aren't valid EntityTypes in 1.8.
     */
    public EntityType getHeadType(ItemStack item) {
        String key = getHeadKey(item);
        if (key == null) {
            return null;
        }
        try {
            return EntityType.valueOf(key);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
    
    /**
     * Get the head config from an item (supports sub-type heads)
     */
    public HeadConfig getHeadConfig(ItemStack item) {
        String key = getHeadKey(item);
        if (key == null) {
            return null;
        }
        return plugin.getConfigManager().getHeadConfig(key);
    }
    
    /**
     * Set skull texture using base64 string
     */
    private void setSkullTexture(SkullMeta meta, String texture) {
        try {
            GameProfile profile = new GameProfile(UUID.nameUUIDFromBytes(("HeadHunting_" + texture).getBytes()), null);
            profile.getProperties().put("textures", new Property("textures", texture));
            
            Field profileField = meta.getClass().getDeclaredField("profile");
            profileField.setAccessible(true);
            profileField.set(meta, profile);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            plugin.getLogger().warning("Failed to set skull texture: " + e.getMessage());
        }
    }
    
    /**
     * Update a head item's lore (after config changes)
     */
    public ItemStack updateHead(ItemStack item) {
        String key = getHeadKey(item);
        if (key == null) {
            return item;
        }
        
        return createHead(key, item.getAmount());
    }
}

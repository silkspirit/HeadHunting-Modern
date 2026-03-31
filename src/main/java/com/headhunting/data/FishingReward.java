package com.headhunting.data;

import com.headhunting.HeadHunting;
import com.headhunting.utils.MessageUtil;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.lang.reflect.Field;
import java.util.*;

/**
 * Represents a fishing reward with full NBT/custom item support
 */
public class FishingReward {
    
    private final String id;
    private final RewardType rewardType;
    private final double chance;
    private final String rarity;
    private final int requiredLevel;
    private final boolean requiresGuardianMask;
    
    // Display settings
    private final String titleMessage;
    private final String subtitleMessage;
    private final Sound sound;
    private final float soundPitch;
    private final float soundVolume;
    private final boolean broadcast;
    private final String broadcastMessage;
    private final List<String> commands;
    
    // Item reward settings
    private final Material material;
    private final String displayName;
    private final int amount;
    private final short data;
    private final List<String> lore;
    private final Map<Enchantment, Integer> enchantments;
    private final boolean unbreakable;
    private final boolean hideFlags;
    private final String skullTexture;
    private final String skullOwner;
    
    // HeadHunting special rewards
    private final EntityType headMobType;
    private final String maskId;
    private final int maskLevel;
    private final int essenceAmount;
    private final int xpAmount;
    
    public FishingReward(String id, ConfigurationSection section) {
        this.id = id;
        this.rewardType = RewardType.fromString(section.getString("type", "ITEM"));
        this.chance = section.getDouble("chance", 1.0);
        this.rarity = section.getString("rarity", "COMMON");
        this.requiredLevel = section.getInt("required-level", 0);
        this.requiresGuardianMask = section.getBoolean("requires-guardian-mask", false);
        
        // Display settings
        this.titleMessage = section.getString("title-message", "&6You caught something!");
        this.subtitleMessage = section.getString("subtitle-message", "&e{item}");
        this.sound = parseSound(section.getString("sound", "NOTE_PLING"));
        this.soundPitch = (float) section.getDouble("sound-pitch", 1.0);
        this.soundVolume = (float) section.getDouble("sound-volume", 1.0);
        this.broadcast = section.getBoolean("broadcast", false);
        this.broadcastMessage = section.getString("broadcast-message", "&6{player} &ecaught a &6{item}&e!");
        this.commands = section.getStringList("commands");
        
        // Item settings
        this.material = parseMaterial(section.getString("material", "DIAMOND"));
        this.displayName = section.getString("display-name", "");
        this.amount = section.getInt("amount", 1);
        this.data = (short) section.getInt("data", 0);
        this.lore = section.getStringList("lore");
        this.enchantments = parseEnchantments(section.getConfigurationSection("enchantments"));
        this.unbreakable = section.getBoolean("unbreakable", false);
        this.hideFlags = section.getBoolean("hide-flags", false);
        this.skullTexture = section.getString("skull-texture", null);
        this.skullOwner = section.getString("skull-owner", null);
        
        // HeadHunting special rewards
        this.headMobType = parseEntityType(section.getString("head-mob", null));
        this.maskId = section.getString("mask-id", null);
        this.maskLevel = section.getInt("mask-level", 1);
        this.essenceAmount = section.getInt("essence-amount", 1);
        this.xpAmount = section.getInt("xp-amount", 0);
    }
    
    private Sound parseSound(String name) {
        try {
            return Sound.valueOf(name.toUpperCase());
        } catch (Exception e) {
            return Sound.BLOCK_NOTE_BLOCK_PLING;
        }
    }
    
    private Material parseMaterial(String name) {
        try {
            return Material.valueOf(name.toUpperCase());
        } catch (Exception e) {
            return Material.DIAMOND;
        }
    }
    
    private EntityType parseEntityType(String name) {
        if (name == null) return null;
        try {
            return EntityType.valueOf(name.toUpperCase());
        } catch (Exception e) {
            return null;
        }
    }
    
    private Map<Enchantment, Integer> parseEnchantments(ConfigurationSection section) {
        Map<Enchantment, Integer> map = new HashMap<>();
        if (section == null) return map;
        
        for (String key : section.getKeys(false)) {
            Enchantment ench = Enchantment.getByName(key.toUpperCase());
            if (ench != null) {
                map.put(ench, section.getInt(key));
            }
        }
        return map;
    }
    
    // Getters
    public String getId() { return id; }
    public RewardType getRewardType() { return rewardType; }
    public double getChance() { return chance; }
    public String getRarity() { return rarity; }
    public int getRequiredLevel() { return requiredLevel; }
    public boolean requiresGuardianMask() { return requiresGuardianMask; }
    
    public String getTitleMessage() { return MessageUtil.color(titleMessage); }
    public String getSubtitleMessage() { return MessageUtil.color(subtitleMessage); }
    public Sound getSound() { return sound; }
    public float getSoundPitch() { return soundPitch; }
    public float getSoundVolume() { return soundVolume; }
    public boolean shouldBroadcast() { return broadcast; }
    public String getBroadcastMessage() { return MessageUtil.color(broadcastMessage); }
    public List<String> getCommands() { return commands; }
    
    public EntityType getHeadMobType() { return headMobType; }
    public String getMaskId() { return maskId; }
    public int getMaskLevel() { return maskLevel; }
    public int getEssenceAmount() { return essenceAmount; }
    public int getXpAmount() { return xpAmount; }
    
    /**
     * Create the reward item
     */
    public ItemStack createItemStack(HeadHunting plugin) {
        switch (rewardType) {
            case HEAD:
                if (headMobType != null && plugin != null) {
                    return plugin.getHeadFactory().createHead(headMobType, amount);
                }
                break;
                
            case MASK:
                if (maskId != null && plugin != null) {
                    return plugin.getMaskFactory().createMask(maskId, maskLevel);
                }
                break;
                
            case ESSENCE:
                if (plugin != null) {
                    return plugin.getMaskFactory().createSpiritEssence(essenceAmount);
                }
                break;
                
            case XP:
                // XP is handled separately, return a visual item
                ItemStack xpItem = new ItemStack(Material.EXPERIENCE_BOTTLE, 1);
                ItemMeta xpMeta = xpItem.getItemMeta();
                xpMeta.setDisplayName(MessageUtil.color("&b+" + xpAmount + " HeadHunting XP"));
                xpItem.setItemMeta(xpMeta);
                return xpItem;
                
            case ITEM:
            default:
                return createCustomItem();
        }
        
        return createCustomItem();
    }
    
    /**
     * Create a custom item with full NBT support
     */
    private ItemStack createCustomItem() {
        ItemStack item = new ItemStack(material, amount);
        ItemMeta meta = item.getItemMeta();
        
        // Display name
        if (displayName != null && !displayName.isEmpty()) {
            meta.setDisplayName(MessageUtil.color(displayName));
        }
        
        // Lore
        if (lore != null && !lore.isEmpty()) {
            List<String> coloredLore = new ArrayList<>();
            for (String line : lore) {
                coloredLore.add(MessageUtil.color(line));
            }
            meta.setLore(coloredLore);
        }
        
        // Unbreakable (1.8.8 method)
        if (unbreakable) {
            meta.setUnbreakable(true);
        }
        
        // Hide flags
        if (hideFlags) {
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
            meta.addItemFlags(ItemFlag.HIDE_DESTROYS);
            meta.addItemFlags(ItemFlag.HIDE_PLACED_ON);
            meta.addItemFlags(ItemFlag.HIDE_POTION_EFFECTS);
        }
        
        // Skull texture
        if (material == Material.PLAYER_HEAD) {
            if (meta instanceof SkullMeta) {
                SkullMeta skullMeta = (SkullMeta) meta;
                
                if (skullTexture != null && !skullTexture.isEmpty()) {
                    setSkullTexture(skullMeta, skullTexture);
                } else if (skullOwner != null && !skullOwner.isEmpty()) {
                    skullMeta.setOwningPlayer(org.bukkit.Bukkit.getOfflinePlayer(skullOwner));
                }
            }
        }
        
        item.setItemMeta(meta);
        
        // Enchantments (after meta is set)
        for (Map.Entry<Enchantment, Integer> entry : enchantments.entrySet()) {
            item.addUnsafeEnchantment(entry.getKey(), entry.getValue());
        }
        
        return item;
    }
    
    /**
     * Set skull texture using base64 via Paper's PlayerProfile API.
     */
    private void setSkullTexture(SkullMeta meta, String texture) {
        try {
            org.bukkit.profile.PlayerProfile profile = org.bukkit.Bukkit.createPlayerProfile(UUID.randomUUID());
            org.bukkit.profile.PlayerTextures textures = profile.getTextures();
            textures.setSkin(new java.net.URL("https://textures.minecraft.net/texture/" + texture));
            profile.setTextures(textures);
            meta.setOwnerProfile(profile);
        } catch (Exception e) {
            // texture string might be a full base64 — try GameProfile fallback
            try {
                GameProfile gp = new GameProfile(UUID.randomUUID(), null);
                gp.getProperties().put("textures", new Property("textures", texture));
                Field profileField = meta.getClass().getDeclaredField("profile");
                profileField.setAccessible(true);
                profileField.set(meta, gp);
            } catch (Exception ignored) {
                // Silent fail
            }
        }
    }
    
    /**
     * Get display name for messages
     */
    public String getDisplayName() {
        if (displayName != null && !displayName.isEmpty()) {
            return MessageUtil.color(displayName);
        }
        return material.name();
    }
    
    /**
     * Reward types
     */
    public enum RewardType {
        ITEM,       // Regular item with NBT
        HEAD,       // HeadHunting mob head
        MASK,       // HeadHunting mask
        ESSENCE,    // Spirit Essence
        XP,         // HeadHunting XP
        COMMAND;    // Command only (no item)
        
        public static RewardType fromString(String str) {
            try {
                return valueOf(str.toUpperCase());
            } catch (Exception e) {
                return ITEM;
            }
        }
    }
}

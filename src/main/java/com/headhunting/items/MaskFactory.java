package com.headhunting.items;

import com.headhunting.HeadHunting;
import com.headhunting.data.MaskConfig;
import com.headhunting.utils.MessageUtil;
import com.headhunting.utils.NBTUtil;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Factory for creating mask items
 */
public class MaskFactory {
    
    private final HeadHunting plugin;
    private static final String MASK_NBT_KEY = "HeadHunting_Mask";
    
    public MaskFactory(HeadHunting plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Create a mask item with unique ID to prevent stacking
     */
    public ItemStack createMask(String maskId, int level) {
        MaskConfig config = plugin.getConfigManager().getMaskConfig(maskId);
        if (config == null) {
            return null;
        }
        
        level = Math.max(1, Math.min(5, level));
        
        // Generate unique ID for this specific mask instance
        String uniqueId = generateUniqueId();
        
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) skull.getItemMeta();
        
        // Set texture using UNIQUE identifier so masks can't stack
        if (config.getTexture() != null && !config.getTexture().isEmpty()) {
            setSkullTexture(meta, config.getTexture(), uniqueId);
        }
        
        // Build display name
        String displayName = buildDisplayName(config, level);
        meta.setDisplayName(MessageUtil.color(displayName));
        
        // Build lore with mask ID and level
        List<String> lore = buildLore(config, level);
        lore.add(MessageUtil.color("&8&o" + MASK_NBT_KEY + ":" + maskId + ":" + level));
        meta.setLore(lore);
        
        skull.setItemMeta(meta);
        
        // Add Protection enchantment based on mask level
        // Level 1 = Prot 1, Level 2 = Prot 2, etc.
        skull.addUnsafeEnchantment(Enchantment.PROTECTION_ENVIRONMENTAL, level);
        
        // Add unique NBT tag via NMS + asCraftMirror (bypasses CraftMetaSkull serialization)
        ItemStack result = NBTUtil.addUniqueId(skull);
        
        // Log NBT for debugging
        if (plugin.getConfigManager().isDebugEnabled()) {
            String nbtDump = NBTUtil.dumpNBT(result);
            plugin.getLogger().info("[MaskFactory] mask=" + maskId + " level=" + level 
                + " class=" + result.getClass().getSimpleName()
                + " | NBT: " + (nbtDump.length() > 200 ? nbtDump.substring(0, 200) + "..." : nbtDump));
        }
        
        return result;
    }
    
    /**
     * Generate a short unique ID for mask instance
     */
    private String generateUniqueId() {
        return Long.toString(System.currentTimeMillis(), 36) + 
               Integer.toString((int)(Math.random() * 1000), 36);
    }
    
    /**
     * Build display name with level color and symbols
     */
    private String buildDisplayName(MaskConfig config, int level) {
        String levelColor = plugin.getConfigManager().getMaskLevelColor(level);
        String name = config.getDisplayName();
        
        StringBuilder displayName = new StringBuilder();
        
        // Divine prefix
        if (config.isDivine()) {
            displayName.append(plugin.getConfigManager().getDivinePrefix());
        }
        
        // Level 5 gets special symbols
        if (level == 5) {
            displayName.append("&6✦ ");
            displayName.append(name);
            displayName.append(" ✦");
        } else {
            displayName.append(levelColor);
            displayName.append(name);
        }
        
        // Divine suffix
        if (config.isDivine()) {
            displayName.append(plugin.getConfigManager().getDivineSuffix());
        }
        
        return displayName.toString();
    }
    
    /**
     * Build lore with abilities
     */
    private List<String> buildLore(MaskConfig config, int level) {
        List<String> lore = new ArrayList<>();
        
        // Level indicator
        String levelColor = plugin.getConfigManager().getMaskLevelColor(level);
        String levelSymbol = plugin.getConfigManager().getMaskLevelSymbol(level);
        String levelLine = "&8Level: " + levelColor + levelSymbol;
        if (level == 5) {
            levelLine += plugin.getConfigManager().getMaxIndicator();
        }
        lore.add(MessageUtil.color(levelLine));
        
        // Header
        lore.add(MessageUtil.color(plugin.getConfigManager().getLoreHeader()));
        
        // Abilities
        List<String> allDescriptions = config.getAllAbilityDescriptions();
        int currentUnlockLevel = 0;
        
        for (int i = 1; i <= 5; i++) {
            List<com.headhunting.data.MaskAbility> levelAbilities = config.getAbilitiesForLevel(i);
            for (com.headhunting.data.MaskAbility ability : levelAbilities) {
                String desc = ability.getDescription();
                
                // Skip if we've already shown this description (for upgraded abilities)
                boolean isDuplicate = false;
                for (int j = 1; j < i; j++) {
                    for (com.headhunting.data.MaskAbility prevAbility : config.getAbilitiesForLevel(j)) {
                        if (prevAbility.getDescription().equals(desc)) {
                            isDuplicate = true;
                            break;
                        }
                    }
                }
                
                if (isDuplicate) continue;
                
                String line;
                if (i <= level) {
                    // Unlocked
                    line = plugin.getConfigManager().getLoreAbilityUnlocked()
                        .replace("{description}", desc);
                } else {
                    // Locked
                    line = plugin.getConfigManager().getLoreAbilityLocked()
                        .replace("{description}", desc);
                }
                lore.add(MessageUtil.color(line));
            }
        }
        
        // Footer
        lore.add(MessageUtil.color(plugin.getConfigManager().getLoreFooter()));
        
        // Divine tag
        if (config.isDivine()) {
            lore.add(MessageUtil.color(plugin.getConfigManager().getDivineTag()));
        }
        
        // Equip hint
        lore.add(MessageUtil.color(plugin.getConfigManager().getEquipHint()));
        
        return lore;
    }
    
    /**
     * Check if an item is a HeadHunting mask
     */
    public boolean isMask(ItemStack item) {
        if (item == null || item.getType() != Material.PLAYER_HEAD) {
            return false;
        }
        if (!item.hasItemMeta() || !item.getItemMeta().hasLore()) {
            return false;
        }
        
        List<String> lore = item.getItemMeta().getLore();
        for (String line : lore) {
            if (line.contains(MASK_NBT_KEY)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Get the mask ID from an item
     */
    public String getMaskId(ItemStack item) {
        if (!isMask(item)) {
            return null;
        }
        
        List<String> lore = item.getItemMeta().getLore();
        for (String line : lore) {
            String stripped = MessageUtil.stripColor(line);
            if (stripped.contains(MASK_NBT_KEY)) {
                String[] parts = stripped.split(":");
                if (parts.length >= 2) {
                    return parts[1].trim().toLowerCase();
                }
            }
        }
        return null;
    }
    
    /**
     * Get the mask level from an item
     */
    public int getMaskLevel(ItemStack item) {
        if (!isMask(item)) {
            return 0;
        }
        
        List<String> lore = item.getItemMeta().getLore();
        for (String line : lore) {
            String stripped = MessageUtil.stripColor(line);
            if (stripped.contains(MASK_NBT_KEY)) {
                String[] parts = stripped.split(":");
                if (parts.length >= 3) {
                    try {
                        return Integer.parseInt(parts[2].trim());
                    } catch (NumberFormatException e) {
                        return 1;
                    }
                }
            }
        }
        return 1;
    }
    
    /**
     * Get the mask config from an item
     */
    public MaskConfig getMaskConfig(ItemStack item) {
        String maskId = getMaskId(item);
        if (maskId == null) {
            return null;
        }
        return plugin.getConfigManager().getMaskConfig(maskId);
    }
    
    /**
     * Update a mask item to a new level
     */
    public ItemStack updateMaskLevel(ItemStack item, int newLevel) {
        String maskId = getMaskId(item);
        if (maskId == null) {
            return item;
        }
        return createMask(maskId, newLevel);
    }
    
    /**
     * Set skull texture using base64 string
     * Uses UNIQUE UUID for each mask instance to prevent stacking
     */
    private void setSkullTexture(SkullMeta meta, String texture, String uniqueId) {
        try {
            // Use UNIQUE UUID for each mask - prevents ANY stacking
            UUID uniqueUUID = UUID.nameUUIDFromBytes(("HeadHunting_Mask_" + uniqueId).getBytes());
            GameProfile profile = new GameProfile(uniqueUUID, "HHMask");
            
            if (texture != null && !texture.isEmpty()) {
                profile.getProperties().put("textures", new Property("textures", texture));
            }
            
            Field profileField = meta.getClass().getDeclaredField("profile");
            profileField.setAccessible(true);
            profileField.set(meta, profile);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            plugin.getLogger().warning("Failed to set skull texture: " + e.getMessage());
        }
    }
    
    /**
     * Set skull texture using base64 string (legacy method)
     */
    private void setSkullTexture(SkullMeta meta, String texture) {
        setSkullTexture(meta, texture, "default");
    }
    
    /**
     * Create Spirit Essence item
     */
    public ItemStack createSpiritEssence(int amount) {
        ItemStack item = new ItemStack(Material.NETHER_STAR, amount);
        org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
        
        meta.setDisplayName(MessageUtil.color("&d&l✦ Spirit Essence ✦"));
        
        List<String> lore = new ArrayList<>();
        lore.add(MessageUtil.color("&8━━━━━━━━━━━━━━━━━"));
        lore.add(MessageUtil.color("&7Right-click while wearing a mask"));
        lore.add(MessageUtil.color("&7to upgrade it by &d1 level&7."));
        lore.add(MessageUtil.color("&7"));
        lore.add(MessageUtil.color("&7Consumes &d1 essence &7per use."));
        lore.add(MessageUtil.color("&8━━━━━━━━━━━━━━━━━"));
        lore.add(MessageUtil.color("&8&oHeadHunting_Essence"));
        
        meta.setLore(lore);
        
        // Add enchant glow
        meta.addEnchant(org.bukkit.enchantments.Enchantment.DURABILITY, 1, true);
        meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
        
        item.setItemMeta(meta);
        
        return item;
    }
    
    /**
     * @deprecated Use {@link #createSpiritEssence(int)} instead
     */
    @Deprecated
    public ItemStack createOmegaEssence(int amount) {
        return createSpiritEssence(amount);
    }
    
    /**
     * Check if an item is Spirit Essence
     */
    public boolean isSpiritEssence(ItemStack item) {
        if (item == null || item.getType() != Material.NETHER_STAR) {
            return false;
        }
        if (!item.hasItemMeta() || !item.getItemMeta().hasLore()) {
            return false;
        }
        
        List<String> lore = item.getItemMeta().getLore();
        for (String line : lore) {
            if (line.contains("HeadHunting_Essence") || line.contains("HeadHunting_OmegaEssence")) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * @deprecated Use {@link #isSpiritEssence(ItemStack)} instead
     */
    @Deprecated
    public boolean isOmegaEssence(ItemStack item) {
        return isSpiritEssence(item);
    }
}

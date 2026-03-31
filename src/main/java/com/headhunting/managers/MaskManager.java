package com.headhunting.managers;

import com.headhunting.HeadHunting;
import com.headhunting.data.MaskAbility;
import com.headhunting.data.MaskConfig;
import com.headhunting.data.PlayerData;
import com.headhunting.data.PlayerData.MaskData;
import com.headhunting.utils.MessageUtil;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages mask system - crafting, leveling, equipping, abilities
 */
public class MaskManager {
    
    private final HeadHunting plugin;
    
    public MaskManager(HeadHunting plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Deposit heads toward a mask
     */
    public DepositResult depositHeads(Player player, String maskId, int amount) {
        MaskConfig maskConfig = plugin.getConfigManager().getMaskConfig(maskId);
        if (maskConfig == null) {
            return DepositResult.INVALID_MASK;
        }
        
        PlayerData data = plugin.getDataManager().getPlayerData(player);
        MaskData maskData = data.getMaskData(maskId);
        
        if (maskData == null) {
            maskData = new MaskData();
            data.setMaskData(maskId, maskData);
        }
        
        // Add deposited heads
        maskData.addDepositedHeads(amount);
        
        // Check if they can now craft the mask
        if (!maskData.isOwned() && maskData.getDepositedHeads() >= maskConfig.getCraftCost()) {
            return DepositResult.CAN_CRAFT;
        }
        
        // Check if they can level up the mask
        if (maskData.isOwned() && maskData.getLevel() < 5) {
            int levelCost = maskConfig.getLevelCost(maskData.getLevel() + 1);
            if (maskData.getDepositedHeads() >= levelCost) {
                return DepositResult.CAN_LEVEL;
            }
        }
        
        return DepositResult.SUCCESS;
    }
    
    /**
     * Craft a mask for a player
     */
    public CraftResult craftMask(Player player, String maskId) {
        MaskConfig maskConfig = plugin.getConfigManager().getMaskConfig(maskId);
        if (maskConfig == null) {
            return CraftResult.INVALID_MASK;
        }
        
        // Check if divine mask requires mission
        if (maskConfig.isDivine()) {
            if (!plugin.getMissionManager().hasMissionComplete(player, maskId)) {
                return CraftResult.MISSION_INCOMPLETE;
            }
        }
        
        PlayerData data = plugin.getDataManager().getPlayerData(player);
        MaskData maskData = data.getMaskData(maskId);
        
        if (maskData != null && maskData.isOwned()) {
            return CraftResult.ALREADY_OWNED;
        }
        
        if (maskData == null) {
            maskData = new MaskData();
            data.setMaskData(maskId, maskData);
        }
        
        // Check if enough heads deposited
        if (maskData.getDepositedHeads() < maskConfig.getCraftCost()) {
            return CraftResult.NOT_ENOUGH_HEADS;
        }
        
        // Craft the mask
        maskData.setDepositedHeads(maskData.getDepositedHeads() - maskConfig.getCraftCost());
        maskData.setOwned(true);
        maskData.setLevel(1);
        
        // Track lifetime stats
        data.incrementMasksCrafted();
        
        // Give the mask item
        ItemStack maskItem = plugin.getMaskFactory().createMask(maskId, 1);
        if (player.getInventory().firstEmpty() == -1) {
            player.getWorld().dropItemNaturally(player.getLocation(), maskItem);
        } else {
            player.getInventory().addItem(maskItem);
        }
        
        // Send message
        String message = plugin.getConfigManager().getRawMessage("mask-crafted")
            .replace("{mask_name}", maskConfig.getDisplayName());
        MessageUtil.send(player, message);
        
        return CraftResult.SUCCESS;
    }
    
    /**
     * Level up a mask
     */
    public LevelResult levelUpMask(Player player, String maskId) {
        MaskConfig maskConfig = plugin.getConfigManager().getMaskConfig(maskId);
        if (maskConfig == null) {
            return LevelResult.INVALID_MASK;
        }
        
        PlayerData data = plugin.getDataManager().getPlayerData(player);
        MaskData maskData = data.getMaskData(maskId);
        
        if (maskData == null || !maskData.isOwned()) {
            return LevelResult.NOT_OWNED;
        }
        
        if (maskData.getLevel() >= 5) {
            return LevelResult.ALREADY_MAX;
        }
        
        int nextLevel = maskData.getLevel() + 1;
        int cost = maskConfig.getLevelCost(nextLevel);
        
        if (maskData.getDepositedHeads() < cost) {
            return LevelResult.NOT_ENOUGH_HEADS;
        }
        
        // Level up
        maskData.setDepositedHeads(maskData.getDepositedHeads() - cost);
        maskData.setLevel(nextLevel);
        
        // Send message
        String message = plugin.getConfigManager().getRawMessage("mask-leveled")
            .replace("{mask_name}", maskConfig.getDisplayName())
            .replace("{level}", String.valueOf(nextLevel));
        MessageUtil.send(player, message);
        
        return LevelResult.SUCCESS;
    }
    
    /**
     * Apply passive abilities for equipped mask AND held mask
     */
    public void applyPassiveAbilities(Player player) {
        PlayerData data = plugin.getDataManager().getPlayerData(player);
        
        // Track highest amplifier per potion type to avoid conflicts
        Map<PotionEffectType, Integer> highestAmplifiers = new HashMap<>();
        int totalHealthBoost = 0;
        
        // 1. Apply abilities from EQUIPPED mask (helmet)
        String equippedMask = data.getEquippedMask();
        if (equippedMask == null) {
            equippedMask = getEquippedMaskFromHelmet(player);
            if (equippedMask != null) {
                data.setEquippedMask(equippedMask);
            }
        }
        
        if (equippedMask != null) {
            MaskConfig maskConfig = plugin.getConfigManager().getMaskConfig(equippedMask);
            if (maskConfig != null) {
                int maskLevel = data.getMaskLevel(equippedMask);
                if (maskLevel < 1) maskLevel = 1;
                
                collectAbilities(maskConfig, maskLevel, highestAmplifiers);
                totalHealthBoost += collectHealthBoost(maskConfig, maskLevel);
            }
        }
        
        // 2. Apply abilities from HELD mask (main hand)
        String heldMask = getHeldMaskFromHand(player);
        if (heldMask != null && !heldMask.equals(equippedMask)) {
            MaskConfig heldConfig = plugin.getConfigManager().getMaskConfig(heldMask);
            if (heldConfig != null) {
                // Check if player owns this mask and get their level for it
                int heldLevel = data.getMaskLevel(heldMask);
                if (heldLevel < 1) {
                    // If they don't own it in data, check the item itself
                    ItemStack heldItem = player.getInventory().getItemInHand();
                    heldLevel = plugin.getMaskFactory().getMaskLevel(heldItem);
                }
                if (heldLevel < 1) heldLevel = 1;
                
                collectAbilities(heldConfig, heldLevel, highestAmplifiers);
                totalHealthBoost += collectHealthBoost(heldConfig, heldLevel);
            }
        }
        
        // Apply the highest amplifier for each potion type (with balance adjustments)
        for (Map.Entry<PotionEffectType, Integer> entry : highestAmplifiers.entrySet()) {
            int balancedAmplifier = plugin.getConfigManager().getBalancedAmplifier(entry.getValue());
            applyPermanentPotion(player, entry.getKey(), balancedAmplifier);
        }
        
        // Apply total health boost
        if (totalHealthBoost > 0) {
            applyHealthBoost(player, totalHealthBoost);
        }
    }
    
    /**
     * Collect potion abilities from a mask config into the amplifier map
     */
    private void collectAbilities(MaskConfig maskConfig, int maskLevel, Map<PotionEffectType, Integer> amplifiers) {
        List<MaskAbility> abilities = maskConfig.getAllAbilitiesUpToLevel(maskLevel);
        
        for (MaskAbility ability : abilities) {
            if (ability.getType() != MaskAbility.AbilityType.PASSIVE) continue;
            
            if (ability.getEffect() == MaskAbility.EffectType.POTION && ability.getPotionType() != null) {
                int currentHighest = amplifiers.getOrDefault(ability.getPotionType(), -1);
                if (ability.getAmplifier() > currentHighest) {
                    amplifiers.put(ability.getPotionType(), ability.getAmplifier());
                }
            }
        }
    }
    
    /**
     * Collect health boost value from a mask config
     */
    private int collectHealthBoost(MaskConfig maskConfig, int maskLevel) {
        List<MaskAbility> abilities = maskConfig.getAllAbilitiesUpToLevel(maskLevel);
        int total = 0;
        
        for (MaskAbility ability : abilities) {
            if (ability.getType() != MaskAbility.AbilityType.PASSIVE) continue;
            if (ability.getEffect() == MaskAbility.EffectType.HEALTH_BOOST) {
                total += (int) ability.getValue();
            }
        }
        
        return total;
    }
    
    /**
     * Get mask from player's main hand
     */
    public String getHeldMaskFromHand(Player player) {
        ItemStack heldItem = player.getInventory().getItemInHand();
        if (heldItem == null) return null;
        return plugin.getMaskFactory().getMaskId(heldItem);
    }
    
    /**
     * Remove all passive abilities (when unequipping mask)
     */
    public void removePassiveAbilities(Player player) {
        // Remove common mask effects
        player.removePotionEffect(PotionEffectType.SPEED);
        player.removePotionEffect(PotionEffectType.JUMP);
        player.removePotionEffect(PotionEffectType.FAST_DIGGING);
        player.removePotionEffect(PotionEffectType.INCREASE_DAMAGE);
        player.removePotionEffect(PotionEffectType.SATURATION);
        player.removePotionEffect(PotionEffectType.FIRE_RESISTANCE);
        player.removePotionEffect(PotionEffectType.WATER_BREATHING);
        player.removePotionEffect(PotionEffectType.HEALTH_BOOST);
        
        // Reset max health
        player.setMaxHealth(20.0);
    }
    
    /**
     * Apply a permanent potion effect (refreshed by task every 5 seconds).
     * Only reapplies when the remaining duration is low or amplifier changed,
     * to avoid visual flickering from constant effect reapplication.
     */
    private void applyPermanentPotion(Player player, PotionEffectType type, int amplifier) {
        for (PotionEffect active : player.getActivePotionEffects()) {
            if (active.getType().equals(type) && active.getAmplifier() == amplifier && active.getDuration() > 200) {
                return; // Still has plenty of time at correct level, skip to prevent flicker
            }
        }
        player.addPotionEffect(new PotionEffect(type, 600, amplifier, true, false), true);
    }
    
    /**
     * Apply health boost
     */
    private void applyHealthBoost(Player player, int extraHealth) {
        double newMax = 20.0 + extraHealth;
        player.setMaxHealth(newMax);
    }
    
    /**
     * Get the mask a player has equipped based on helmet
     */
    public String getEquippedMaskFromHelmet(Player player) {
        ItemStack helmet = player.getInventory().getHelmet();
        if (helmet == null) {
            return null;
        }
        
        return plugin.getMaskFactory().getMaskId(helmet);
    }
    
    /**
     * Update equipped mask tracking
     */
    public void updateEquippedMask(Player player) {
        PlayerData data = plugin.getDataManager().getPlayerData(player);
        String currentEquipped = data.getEquippedMask();
        String helmetMask = getEquippedMaskFromHelmet(player);
        
        if (currentEquipped != null && !currentEquipped.equals(helmetMask)) {
            // Mask was unequipped
            removePassiveAbilities(player);
            data.setEquippedMask(null);
            
            String message = plugin.getConfigManager().getRawMessage("mask-unequipped");
            MessageUtil.send(player, message);
        }
        
        if (helmetMask != null && !helmetMask.equals(currentEquipped)) {
            // New mask equipped
            data.setEquippedMask(helmetMask);
            
            // Sync mask level from item to PlayerData
            ItemStack helmet = player.getInventory().getHelmet();
            if (helmet != null) {
                int itemLevel = plugin.getMaskFactory().getMaskLevel(helmet);
                if (itemLevel > 0) {
                    data.setMaskOwned(helmetMask, true);
                    data.setMaskLevel(helmetMask, itemLevel);
                }
            }
            
            applyPassiveAbilities(player);
            
            MaskConfig config = plugin.getConfigManager().getMaskConfig(helmetMask);
            if (config != null) {
                String message = plugin.getConfigManager().getRawMessage("mask-equipped")
                    .replace("{mask_name}", config.getDisplayName());
                MessageUtil.send(player, message);
            }
        }
    }
    
    public enum DepositResult {
        SUCCESS,
        CAN_CRAFT,
        CAN_LEVEL,
        INVALID_MASK
    }
    
    public enum CraftResult {
        SUCCESS,
        INVALID_MASK,
        ALREADY_OWNED,
        NOT_ENOUGH_HEADS,
        MISSION_INCOMPLETE
    }
    
    public enum LevelResult {
        SUCCESS,
        INVALID_MASK,
        NOT_OWNED,
        ALREADY_MAX,
        NOT_ENOUGH_HEADS
    }
}

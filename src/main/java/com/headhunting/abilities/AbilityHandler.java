package com.headhunting.abilities;

import com.headhunting.HeadHunting;
import com.headhunting.data.MaskAbility;
import com.headhunting.data.MaskConfig;
import com.headhunting.data.PlayerData;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;

/**
 * Handles complex mask ability logic
 */
public class AbilityHandler {
    
    private final HeadHunting plugin;
    
    // Track pearl cooldowns per player
    private final Map<UUID, Long> pearlCooldowns = new HashMap<>();
    private final Map<UUID, Long> lastPearlUse = new HashMap<>();
    
    // Track low health boost cooldowns
    private final Map<UUID, Long> lowHealthBoostCooldown = new HashMap<>();
    
    // Default pearl cooldown in milliseconds (16 ticks = 800ms in vanilla, but most servers use longer)
    private static final long DEFAULT_PEARL_COOLDOWN = 16000; // 16 seconds for factions
    
    public AbilityHandler(HeadHunting plugin) {
        this.plugin = plugin;
    }
    
    // ==========================================
    // PEARL ABILITIES (Enderman Mask)
    // ==========================================
    
    /**
     * Check if player should take pearl damage
     */
    public boolean shouldTakePearlDamage(Player player) {
        if (!hasAbility(player, "NO_PEARL_DAMAGE")) {
            return true;
        }
        return false; // No pearl damage
    }
    
    /**
     * Get modified pearl cooldown for player
     */
    public long getPearlCooldown(Player player) {
        double reduction = getAbilityValue(player, "PEARL_COOLDOWN_REDUCTION");
        if (reduction <= 0) {
            return DEFAULT_PEARL_COOLDOWN;
        }
        
        double multiplier = 1.0 - (reduction / 100.0);
        return (long) (DEFAULT_PEARL_COOLDOWN * multiplier);
    }
    
    /**
     * Check if player can throw pearl (cooldown check)
     */
    public boolean canThrowPearl(Player player) {
        Long lastUse = lastPearlUse.get(player.getUniqueId());
        if (lastUse == null) {
            return true;
        }
        
        long cooldown = getPearlCooldown(player);
        return System.currentTimeMillis() - lastUse >= cooldown;
    }
    
    /**
     * Record pearl throw
     */
    public void recordPearlThrow(Player player) {
        lastPearlUse.put(player.getUniqueId(), System.currentTimeMillis());
    }
    
    /**
     * Get remaining pearl cooldown in milliseconds
     */
    public long getRemainingPearlCooldown(Player player) {
        Long lastUse = lastPearlUse.get(player.getUniqueId());
        if (lastUse == null) {
            return 0;
        }
        
        long cooldown = getPearlCooldown(player);
        long remaining = cooldown - (System.currentTimeMillis() - lastUse);
        return Math.max(0, remaining);
    }
    
    /**
     * Handle pearl land - apply invisibility if applicable
     */
    public void onPearlLand(Player player) {
        MaskAbility ability = getAbility(player, "PEARL_INVIS");
        if (ability == null) return;
        
        if (ability.shouldProc()) {
            int duration = ability.getDuration();
            if (duration <= 0) duration = 100; // 5 seconds default
            
            player.addPotionEffect(new PotionEffect(
                PotionEffectType.INVISIBILITY,
                duration,
                0,
                true,
                false
            ), true);
        }
    }
    
    // ==========================================
    // LOW HEALTH ABILITIES
    // ==========================================
    
    /**
     * Check and apply low health boost (Enderman, Skeleton Horse)
     */
    public void checkLowHealthBoost(Player player) {
        // Check cooldown (30 second cooldown)
        Long lastBoost = lowHealthBoostCooldown.get(player.getUniqueId());
        if (lastBoost != null && System.currentTimeMillis() - lastBoost < 30000) {
            return;
        }
        
        MaskAbility ability = getAbility(player, "LOW_HEALTH_BOOST");
        if (ability == null) {
            ability = getAbility(player, "SKELETON_HORSE_PASSIVE");
        }
        if (ability == null) {
            ability = getAbility(player, "SKELETON_HORSE_PASSIVE_MAX");
        }
        if (ability == null) return;
        
        // Check health threshold (default 6 = 3 hearts)
        double threshold = ability.getValue();
        if (threshold <= 0) threshold = 6;
        
        if (player.getHealth() <= threshold) {
            // Apply boost
            lowHealthBoostCooldown.put(player.getUniqueId(), System.currentTimeMillis());
            
            // Speed boost
            int speedLevel = 3; // Speed IV
            if (ability.getSpecialId() != null && ability.getSpecialId().contains("MAX")) {
                speedLevel = 4; // Speed V
            }
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 200, speedLevel, true, false), true);
            
            // Absorption
            player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 200, 2, true, false), true);
        }
    }
    
    // ==========================================
    // DEBUFF IMMUNITY
    // ==========================================
    
    /**
     * Check if player is immune to a debuff
     */
    public boolean isImmuneToDebuff(Player player, PotionEffectType effectType) {
        PlayerData data = plugin.getDataManager().getPlayerData(player);
        String equippedMask = data.getEquippedMask();
        if (equippedMask == null) return false;
        
        MaskConfig maskConfig = plugin.getConfigManager().getMaskConfig(equippedMask);
        if (maskConfig == null) return false;
        
        int maskLevel = data.getMaskLevel(equippedMask);
        List<MaskAbility> abilities = maskConfig.getAllAbilitiesUpToLevel(maskLevel);
        
        for (MaskAbility ability : abilities) {
            if (!"DEBUFF_IMMUNITY".equals(ability.getSpecialId())) continue;
            
            List<String> immuneDebuffs = ability.getDebuffs();
            if (immuneDebuffs == null) continue;
            
            for (String debuffName : immuneDebuffs) {
                PotionEffectType immuneType = PotionEffectType.getByName(debuffName);
                if (immuneType != null && immuneType.equals(effectType)) {
                    return true;
                }
            }
        }
        
        return false;
    }
    
    /**
     * Remove debuffs player is immune to
     */
    public void removeImmunizedDebuffs(Player player) {
        PotionEffectType[] debuffs = {
            PotionEffectType.SLOW,
            PotionEffectType.WEAKNESS,
            PotionEffectType.POISON,
            PotionEffectType.WITHER,
            PotionEffectType.BLINDNESS,
            PotionEffectType.CONFUSION
        };
        
        for (PotionEffectType debuff : debuffs) {
            if (player.hasPotionEffect(debuff) && isImmuneToDebuff(player, debuff)) {
                player.removePotionEffect(debuff);
            }
        }
    }
    
    // ==========================================
    // FARMING ABILITIES (Sheep Mask)
    // ==========================================
    
    /**
     * Check if player should get bonus crops
     */
    public int getBonusCrops(Player player) {
        MaskAbility ability = getAbility(player, "BONUS_CROPS");
        if (ability == null) return 0;
        
        if (ability.shouldProc()) {
            return 1; // +1 extra crop
        }
        return 0;
    }
    
    /**
     * Check if player can trample crops
     */
    public boolean canTrampleCrops(Player player) {
        return !hasAbility(player, "NO_TRAMPLE");
    }
    
    /**
     * Apply farming haste if on farmland
     */
    public void checkFarmingHaste(Player player) {
        MaskAbility ability = getAbility(player, "FARMING_HASTE");
        if (ability == null) return;
        
        // Check if standing on or near farmland
        Location loc = player.getLocation();
        Material below = loc.clone().subtract(0, 1, 0).getBlock().getType();
        
        if (below == Material.FARMLAND) {
            int amplifier = ability.getAmplifier();
            if (amplifier < 0) amplifier = 1;
            
            player.addPotionEffect(new PotionEffect(
                PotionEffectType.FAST_DIGGING,
                40, // 2 seconds
                amplifier,
                true,
                false
            ), true);
        }
    }
    
    // ==========================================
    // POTION ABILITIES (Pig Mask)
    // ==========================================
    
    /**
     * Get potion duration multiplier
     */
    public double getPotionDurationMultiplier(Player player) {
        double boost = getAbilityValue(player, "POTION_DURATION_BOOST");
        if (boost <= 0) return 1.0;
        
        return 1.0 + (boost / 100.0);
    }
    
    /**
     * Check if gapple should be returned
     */
    public boolean shouldReturnGapple(Player player) {
        MaskAbility ability = getAbility(player, "GAPPLE_RETURN");
        if (ability == null) return false;
        
        return ability.shouldProc();
    }
    
    // ==========================================
    // GRINDING ABILITIES (Blaze, Villager)
    // ==========================================
    
    /**
     * Check if stack should be preserved
     */
    public boolean shouldPreserveStack(Player player) {
        MaskAbility ability = getAbility(player, "PRESERVE_STACK");
        if (ability == null) return false;
        
        return ability.shouldProc();
    }
    
    /**
     * Check if entire stack should be killed
     */
    public boolean shouldInstantKillStack(Player player) {
        MaskAbility ability = getAbility(player, "INSTANT_STACK_KILL");
        if (ability == null) return false;
        
        return ability.shouldProc();
    }
    
    /**
     * Check if bonus head should drop
     */
    public boolean shouldDropBonusHead(Player player) {
        MaskAbility ability = getAbility(player, "BONUS_HEAD");
        if (ability == null) return false;
        
        return ability.shouldProc();
    }
    
    /**
     * Get XP multiplier from mask
     */
    public double getXpMultiplier(Player player) {
        PlayerData data = plugin.getDataManager().getPlayerData(player);
        String equippedMask = data.getEquippedMask();
        if (equippedMask == null) return 1.0;
        
        MaskConfig maskConfig = plugin.getConfigManager().getMaskConfig(equippedMask);
        if (maskConfig == null) return 1.0;
        
        int maskLevel = data.getMaskLevel(equippedMask);
        List<MaskAbility> abilities = maskConfig.getAllAbilitiesUpToLevel(maskLevel);
        
        double multiplier = 1.0;
        for (MaskAbility ability : abilities) {
            if (ability.getEffect() == MaskAbility.EffectType.BONUS_XP) {
                if (ability.shouldProc()) {
                    double abilityMultiplier = ability.getValue();
                    if (abilityMultiplier <= 0) {
                        // Check for multiplier field
                        abilityMultiplier = 1.5; // Default
                    }
                    multiplier = Math.max(multiplier, abilityMultiplier);
                }
            }
        }
        
        return multiplier;
    }
    
    // ==========================================
    // FISHING ABILITIES (Guardian, Elder Guardian)
    // ==========================================
    
    /**
     * Get fishing luck bonus
     */
    public int getFishingLuckBonus(Player player) {
        int bonus = 0;
        
        MaskAbility fishing = getAbility(player, "FISHING_LUCK");
        if (fishing != null) {
            bonus += (int) fishing.getValue();
        }
        
        MaskAbility elder = getAbility(player, "ELDER_FISHING");
        if (elder != null) {
            bonus += 25; // Elder Guardian gets big bonus
        }
        
        return bonus;
    }
    
    /**
     * Check if player has access to mask fishing rewards
     */
    public boolean hasMaskFishingRewards(Player player) {
        return hasAbility(player, "MASK_FISHING_REWARDS");
    }
    
    /**
     * Check if player should get lake regen
     */
    public void checkLakeRegen(Player player, Location lakeCenter, double radius) {
        if (!hasAbility(player, "LAKE_REGEN")) return;
        
        if (player.getLocation().distance(lakeCenter) <= radius) {
            player.addPotionEffect(new PotionEffect(
                PotionEffectType.REGENERATION,
                40,
                0,
                true,
                false
            ), true);
        }
    }
    
    // ==========================================
    // UTILITY METHODS
    // ==========================================
    
    /**
     * Check if player has a specific special ability
     */
    public boolean hasAbility(Player player, String specialId) {
        return getAbility(player, specialId) != null;
    }
    
    /**
     * Get a specific ability by special ID
     */
    public MaskAbility getAbility(Player player, String specialId) {
        PlayerData data = plugin.getDataManager().getPlayerData(player);
        String equippedMask = data.getEquippedMask();
        if (equippedMask == null) return null;
        
        MaskConfig maskConfig = plugin.getConfigManager().getMaskConfig(equippedMask);
        if (maskConfig == null) return null;
        
        int maskLevel = data.getMaskLevel(equippedMask);
        List<MaskAbility> abilities = maskConfig.getAllAbilitiesUpToLevel(maskLevel);
        
        for (MaskAbility ability : abilities) {
            if (specialId.equalsIgnoreCase(ability.getSpecialId())) {
                return ability;
            }
        }
        
        return null;
    }
    
    /**
     * Get the value of a specific ability
     */
    public double getAbilityValue(Player player, String specialId) {
        MaskAbility ability = getAbility(player, specialId);
        if (ability == null) return 0;
        return ability.getValue();
    }
    
    /**
     * Clean up player data (call on quit)
     */
    public void cleanupPlayer(UUID uuid) {
        pearlCooldowns.remove(uuid);
        lastPearlUse.remove(uuid);
        lowHealthBoostCooldown.remove(uuid);
    }
}

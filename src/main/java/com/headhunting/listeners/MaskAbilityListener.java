package com.headhunting.listeners;

import com.headhunting.HeadHunting;
import com.headhunting.data.MaskAbility;
import com.headhunting.data.MaskConfig;
import com.headhunting.data.PlayerData;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wolf;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;

/**
 * Handles mask ability procs during combat and other events
 */
public class MaskAbilityListener implements Listener {
    
    private final HeadHunting plugin;
    
    public MaskAbilityListener(HeadHunting plugin) {
        this.plugin = plugin;
    }
    
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerDamageEntity(EntityDamageByEntityEvent event) {
        Player attacker = getPlayerAttacker(event);
        if (attacker == null) {
            return;
        }
        
        if (!(event.getEntity() instanceof LivingEntity)) return;
        LivingEntity victim = (LivingEntity) event.getEntity();
        
        // Only allow ENTITY_ATTACK (melee) or PROJECTILE (bow) - block THORNS and other damage types
        EntityDamageEvent.DamageCause cause = event.getCause();
        if (cause != EntityDamageEvent.DamageCause.ENTITY_ATTACK && 
            cause != EntityDamageEvent.DamageCause.PROJECTILE) {
            return;
        }
        
        PlayerData data = plugin.getDataManager().getPlayerData(attacker);
        String equippedMask = data.getEquippedMask();
        if (equippedMask == null) {
            return;
        }
        
        MaskConfig maskConfig = plugin.getConfigManager().getMaskConfig(equippedMask);
        if (maskConfig == null) {
            return;
        }
        
        int maskLevel = data.getMaskLevel(equippedMask);
        List<MaskAbility> abilities = maskConfig.getAllAbilitiesUpToLevel(maskLevel);
        
        boolean isBowAttack = event.getDamager() instanceof Arrow;
        boolean isMeleeWeapon = !isBowAttack && isMeleeWeapon(attacker.getItemInHand());
        
        for (MaskAbility ability : abilities) {
            if (ability.getType() != MaskAbility.AbilityType.ON_HIT) continue;
            
            // Bow abilities only work with bows
            if (ability.requiresBow() && !isBowAttack) continue;
            
            // Non-bow abilities only work with swords/axes (melee)
            if (!ability.requiresBow() && !isMeleeWeapon) continue;
            
            // Check proc chance - MELEE debuffs are nerfed by 85% (only 15% of original chance)
            boolean procced;
            if (isMeleeWeapon && !ability.requiresBow() && isDebuffAbility(ability)) {
                // Melee debuff: use 15% of original chance
                double nerfedChance = ability.getChance() * 0.15;
                procced = Math.random() * 100 < nerfedChance;
            } else {
                // Bow or non-debuff: use normal chance
                procced = ability.shouldProc();
            }
            
            if (!procced) continue;
            
            applyOnHitAbility(attacker, victim, ability, event);
        }
    }
    
    /**
     * Check if item is a melee weapon (sword or axe)
     */
    private boolean isMeleeWeapon(org.bukkit.inventory.ItemStack item) {
        if (item == null) return false;
        String name = item.getType().name();
        return name.endsWith("_SWORD") || name.endsWith("_AXE");
    }
    
    /**
     * Check if ability applies debuffs to enemies (POTION debuffs or SPECIAL debuffs)
     */
    private boolean isDebuffAbility(MaskAbility ability) {
        // Check for potion debuff effects
        if (ability.getEffect() == MaskAbility.EffectType.POTION) {
            PotionEffectType potionType = ability.getPotionType();
            if (potionType != null) {
                // List of debuff potion types
                return potionType.equals(PotionEffectType.SLOW) ||
                       potionType.equals(PotionEffectType.SLOW_DIGGING) ||
                       potionType.equals(PotionEffectType.WEAKNESS) ||
                       potionType.equals(PotionEffectType.POISON) ||
                       potionType.equals(PotionEffectType.WITHER) ||
                       potionType.equals(PotionEffectType.BLINDNESS) ||
                       potionType.equals(PotionEffectType.CONFUSION) ||
                       potionType.equals(PotionEffectType.HUNGER);
            }
        }
        
        // Check for special debuff abilities
        if (ability.getEffect() == MaskAbility.EffectType.SPECIAL) {
            String specialId = ability.getSpecialId();
            if (specialId != null) {
                String upper = specialId.toUpperCase();
                return upper.contains("STRIP") || 
                       upper.contains("FREEZE") || 
                       upper.contains("DEBUFF") ||
                       upper.contains("GUARDIAN") ||
                       upper.contains("ELDER");
            }
        }
        
        return false;
    }
    
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerDamaged(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player victim = (Player) event.getEntity();
        
        PlayerData data = plugin.getDataManager().getPlayerData(victim);
        String equippedMask = data.getEquippedMask();
        if (equippedMask == null) return;
        
        MaskConfig maskConfig = plugin.getConfigManager().getMaskConfig(equippedMask);
        if (maskConfig == null) return;
        
        int maskLevel = data.getMaskLevel(equippedMask);
        List<MaskAbility> abilities = maskConfig.getAllAbilitiesUpToLevel(maskLevel);
        
        LivingEntity attacker = null;
        if (event.getDamager() instanceof LivingEntity) {
            attacker = (LivingEntity) event.getDamager();
        } else if (event.getDamager() instanceof Arrow) {
            Arrow arrow = (Arrow) event.getDamager();
            if (arrow.getShooter() instanceof LivingEntity) {
                attacker = (LivingEntity) arrow.getShooter();
            }
        }
        
        for (MaskAbility ability : abilities) {
            if (ability.getType() != MaskAbility.AbilityType.ON_DAMAGED) continue;
            
            if (!ability.shouldProc()) continue;
            
            applyOnDamagedAbility(victim, attacker, ability, event);
        }
    }
    
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onFallDamage(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL) return;
        if (!(event.getEntity() instanceof Player)) return;
        
        Player player = (Player) event.getEntity();
        PlayerData data = plugin.getDataManager().getPlayerData(player);
        
        double totalReduction = 0;
        
        // Check EQUIPPED mask
        String equippedMask = data.getEquippedMask();
        if (equippedMask != null) {
            totalReduction = Math.max(totalReduction, getFallDamageReduction(equippedMask, data));
        }
        
        // Check HELD mask (main hand) - allows fall damage protection while holding chicken mask!
        String heldMask = plugin.getMaskManager().getHeldMaskFromHand(player);
        if (heldMask != null && !heldMask.equals(equippedMask)) {
            totalReduction = Math.max(totalReduction, getFallDamageReduction(heldMask, data));
        }
        
        // Apply the highest reduction
        if (totalReduction > 0) {
            double newDamage = event.getDamage() * (1 - totalReduction / 100.0);
            event.setDamage(newDamage);
            
            if (totalReduction >= 100) {
                event.setCancelled(true);
            }
        }
    }
    
    /**
     * Get fall damage reduction percentage from a mask
     */
    private double getFallDamageReduction(String maskId, PlayerData data) {
        MaskConfig maskConfig = plugin.getConfigManager().getMaskConfig(maskId);
        if (maskConfig == null) return 0;
        
        int maskLevel = data.getMaskLevel(maskId);
        if (maskLevel < 1) maskLevel = 1;
        
        List<MaskAbility> abilities = maskConfig.getAllAbilitiesUpToLevel(maskLevel);
        double maxReduction = 0;
        
        for (MaskAbility ability : abilities) {
            if (ability.getEffect() != MaskAbility.EffectType.DAMAGE_REDUCTION) continue;
            if (!"FALL".equals(ability.getDamageType())) continue;
            
            maxReduction = Math.max(maxReduction, ability.getValue());
        }
        
        return maxReduction;
    }
    
    private void applyOnHitAbility(Player attacker, LivingEntity victim, MaskAbility ability, EntityDamageByEntityEvent event) {
        switch (ability.getEffect()) {
            case POTION:
                if (ability.getPotionType() != null) {
                    int duration = ability.getDuration();
                    if (duration == -1) duration = Integer.MAX_VALUE;
                    victim.addPotionEffect(new PotionEffect(
                        ability.getPotionType(), 
                        duration, 
                        ability.getAmplifier()
                    ), true);
                }
                break;
                
            case DAMAGE_MULTIPLIER:
                double newDamage = event.getDamage() * ability.getValue();
                event.setDamage(newDamage);
                break;
                
            case SPAWN_MINIONS:
                spawnMinions(attacker, victim, ability);
                break;
                
            case SPECIAL:
                handleSpecialOnHit(attacker, victim, ability);
                break;
                
            default:
                break;
        }
    }
    
    private void applyOnDamagedAbility(Player victim, LivingEntity attacker, MaskAbility ability, EntityDamageByEntityEvent event) {
        switch (ability.getEffect()) {
            case SPECIAL:
                handleSpecialOnDamaged(victim, attacker, ability);
                break;
                
            case DAMAGE_REDUCTION:
                double reduction = ability.getValue() / 100.0;
                double newDamage = event.getDamage() * (1 - reduction);
                event.setDamage(newDamage);
                break;
                
            default:
                break;
        }
    }
    
    private void spawnMinions(Player owner, LivingEntity target, MaskAbility ability) {
        String mobType = ability.getMobType();
        int count = ability.getCount();
        
        if ("WOLF".equalsIgnoreCase(mobType)) {
            for (int i = 0; i < count; i++) {
                Wolf wolf = owner.getWorld().spawn(owner.getLocation(), Wolf.class);
                wolf.setOwner(owner);
                wolf.setAngry(true);
                wolf.setTarget(target);
                
                int duration = ability.getDuration();
                if (duration > 0) {
                    plugin.getServer().getScheduler().runTaskLater(plugin, wolf::remove, duration * 20L);
                }
            }
        }
    }
    
    private void handleSpecialOnHit(Player attacker, LivingEntity victim, MaskAbility ability) {
        String specialId = ability.getSpecialId();
        if (specialId == null) return;

        switch (specialId.toUpperCase()) {
            case "STRIP_REGEN":
                victim.removePotionEffect(PotionEffectType.REGENERATION);
                break;
                
            case "STRIP_BUFFS":
                victim.removePotionEffect(PotionEffectType.SPEED);
                victim.removePotionEffect(PotionEffectType.INCREASE_DAMAGE);
                victim.removePotionEffect(PotionEffectType.DAMAGE_RESISTANCE);
                victim.removePotionEffect(PotionEffectType.FIRE_RESISTANCE);
                victim.removePotionEffect(PotionEffectType.ABSORPTION);
                break;
                
            case "FREEZE":
                if (victim instanceof Player) {
                    Player target = (Player) victim;
                    target.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, ability.getDuration(), 100), true);
                    target.addPotionEffect(new PotionEffect(PotionEffectType.JUMP, ability.getDuration(), 128), true);
                }
                break;
                
            case "BOW_RANDOM_DEBUFF":
                applyRandomDebuff(victim);
                break;
                
            case "GUARDIAN_DEBUFF":
                victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 100, 0), true);
                victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_DIGGING, 100, 0), true);
                break;
                
            case "ELDER_DEBUFF":
                victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 100, 2), true);
                victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_DIGGING, 100, 2), true);
                break;
        }
    }
    
    private void handleSpecialOnDamaged(Player victim, LivingEntity attacker, MaskAbility ability) {
        String specialId = ability.getSpecialId();
        if (specialId == null) return;
        
        switch (specialId.toUpperCase()) {
            case "DEBUFF_CLEANSE":
                victim.removePotionEffect(PotionEffectType.SLOW);
                victim.removePotionEffect(PotionEffectType.WEAKNESS);
                victim.removePotionEffect(PotionEffectType.POISON);
                victim.removePotionEffect(PotionEffectType.WITHER);
                victim.removePotionEffect(PotionEffectType.BLINDNESS);
                victim.removePotionEffect(PotionEffectType.CONFUSION);
                break;
                
            case "THORNS":
                if (attacker != null) {
                    attacker.damage(ability.getValue(), victim);
                }
                break;
        }
    }
    
    private void applyRandomDebuff(LivingEntity target) {
        PotionEffectType[] debuffs = {
            PotionEffectType.SLOW,
            PotionEffectType.WEAKNESS,
            PotionEffectType.POISON,
            PotionEffectType.BLINDNESS,
            PotionEffectType.CONFUSION
        };
        
        PotionEffectType randomDebuff = debuffs[(int) (Math.random() * debuffs.length)];
        target.addPotionEffect(new PotionEffect(randomDebuff, 100, 0), true);
    }
    
    private Player getPlayerAttacker(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player) {
            return (Player) event.getDamager();
        }
        if (event.getDamager() instanceof Arrow) {
            Arrow arrow = (Arrow) event.getDamager();
            if (arrow.getShooter() instanceof Player) {
                return (Player) arrow.getShooter();
            }
        }
        return null;
    }
}

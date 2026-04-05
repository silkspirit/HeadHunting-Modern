package com.headhunting.listeners;

import com.headhunting.HeadHunting;
import com.headhunting.data.HeadConfig;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Random;

/**
 * Handles mob death events for head drops and grinding abilities
 */
public class MobDeathListener implements Listener {
    
    private final HeadHunting plugin;
    private final Random random = new Random();
    
    public MobDeathListener(HeadHunting plugin) {
        this.plugin = plugin;
    }
    
    @EventHandler(priority = EventPriority.NORMAL)
    public void onMobDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        Player killer = entity.getKiller();
        
        // Only process if killed by player
        if (killer == null) {
            return;
        }
        
        // Detect 1.8 sub-types that share an EntityType
        EntityType type = entity.getType();
        String headKey = resolveHeadKey(entity, type);
        
        // Get head config by string key (supports sub-types like WITHER_SKELETON)
        HeadConfig headConfig = plugin.getConfigManager().getHeadConfig(headKey);

        // World whitelist check - skip head drops in non-whitelisted worlds
        if (!plugin.getConfigManager().isWorldAllowedForHeadDrops(entity.getWorld().getName())) {
            if (plugin.getConfigManager().isDebugEnabled()) {
                plugin.getLogger().info("[WorldWhitelist] Blocked head drop in world: " + entity.getWorld().getName());
            }
            return;
        }
        
        // Fallback: resolve by EntityType if key-based lookup failed
        if (headConfig == null) {
            headConfig = plugin.getConfigManager().getHeadConfig(entity.getType());
        }
        if (headConfig == null || !headConfig.isEnabled()) {
            return;
        }
        
        // Calculate drop chance
        double baseChance = headConfig.getDropChance();
        double multiplier = plugin.getConfigManager().getDropRateMultiplier();

        // Apply warzone multiplier if factions integration is enabled
        if (plugin.getConfigManager().isFactionsEnabled() && isInWarzone(entity)) {
            multiplier *= plugin.getConfigManager().getWarzoneDropMultiplier();
        }

        double finalChance = baseChance * multiplier;
        
        // Check for stacked mobs
        int stackSize = getStackSize(entity);
        int headsToDrop = 0;
        
        // Check for instant stack kill (Blaze mask ability)
        boolean instantKill = plugin.getAbilityHandler().shouldInstantKillStack(killer);
        
        // Check for stack preservation (Blaze mask ability)
        boolean preserveStack = plugin.getAbilityHandler().shouldPreserveStack(killer);
        
        if (instantKill && stackSize > 1) {
            // Kill entire stack - drop heads for all
            for (int i = 0; i < stackSize; i++) {
                if (random.nextDouble() * 100 < finalChance) {
                    headsToDrop++;
                }
            }
        } else if (stackSize > 1 && plugin.getConfigManager().isStackedMobsEnabled()) {
            // Normal stacked mob handling
            if (!preserveStack) {
                int maxHeads = plugin.getConfigManager().getMaxHeadsPerKill();
                for (int i = 0; i < Math.min(stackSize, maxHeads); i++) {
                    if (random.nextDouble() * 100 < finalChance) {
                        headsToDrop++;
                    }
                }
            } else {
                // Still give 1 head chance even with preservation
                if (random.nextDouble() * 100 < finalChance) {
                    headsToDrop = 1;
                }
            }
        } else {
            // Single mob
            if (random.nextDouble() * 100 < finalChance) {
                headsToDrop = 1;
            }
        }
        
        // Check for bonus head (Villager mask ability)
        if (headsToDrop > 0 && plugin.getAbilityHandler().shouldDropBonusHead(killer)) {
            headsToDrop++;
        }
        
        // Drop heads
        if (headsToDrop > 0) {
            dropHeads(event, entity, headConfig, headsToDrop);
        }
    }
    
    /**
     * Drop head items — adds to event drops so collectors can intercept
     */
    private void dropHeads(EntityDeathEvent event, LivingEntity entity, HeadConfig config, int amount) {
        ItemStack headItem = plugin.getHeadFactory().createHead(config.getHeadKey(), amount);
        if (headItem != null) {
            event.getDrops().add(headItem);
            
            if (plugin.getConfigManager().isDebugEnabled()) {
                plugin.getLogger().info("Dropped " + amount + "x " + config.getHeadKey() + " head(s)");
            }
        }
    }
    
    /**
     * Resolve the head key for an entity.
     * In 1.20.4+ all former sub-types (Wither Skeleton, Elder Guardian,
     * Skeleton Horse, Zombie Horse) are proper EntityTypes, so no special
     * detection is needed. Kept as a method for future extensibility.
     */
    private String resolveHeadKey(LivingEntity entity, EntityType type) {
        return type.name();
    }

    /**
     * Check if entity is in warzone (Factions integration, only called when enabled)
     */
    private boolean isInWarzone(LivingEntity entity) {
        try {
            Class<?> boardClass = Class.forName("com.massivecraft.factions.Board");
            Object board = boardClass.getMethod("getInstance").invoke(null);

            Class<?> flocationClass = Class.forName("com.massivecraft.factions.FLocation");
            Object flocation = flocationClass.getConstructor(org.bukkit.Location.class)
                .newInstance(entity.getLocation());

            Object faction = boardClass.getMethod("getFactionAt", flocationClass).invoke(board, flocation);
            if (faction != null) {
                try {
                    Boolean isWarzone = (Boolean) faction.getClass().getMethod("isWarZone").invoke(faction);
                    if (isWarzone != null && isWarzone) return true;
                } catch (NoSuchMethodException e) {
                    // Fall through
                }
                String tag = (String) faction.getClass().getMethod("getTag").invoke(faction);
                if (tag != null && tag.toLowerCase().contains("warzone")) return true;
            }
        } catch (Exception e) {
            // Factions not available
        }
        return false;
    }

    /**
     * Get stack size of entity (Stacker plugin integration).
     * Checks metadata keys first (reliable), then falls back to display name parsing.
     */
    private int getStackSize(LivingEntity entity) {
        try {
            // Check metadata keys used by popular stacker plugins (most reliable)
            String[] metadataKeys = {
                "stack-size",       // Generic / WildStacker
                "stacker-amount",   // RoseStacker
                "stackAmount",      // StackMob
                "quantity"          // MobStacker
            };
            for (String key : metadataKeys) {
                if (entity.hasMetadata(key)) {
                    int value = entity.getMetadata(key).get(0).asInt();
                    if (value > 1) return value;
                }
            }

            // Fallback: parse display name for common stacker formats
            // Formats: "5x Zombie", "Zombie x5", "Zombie (5)", "5 Zombie"
            String customName = entity.getCustomName();
            if (customName != null) {
                // Strip color codes for parsing
                String stripped = customName.replaceAll("§[0-9a-fk-or]", "").trim();

                // Pattern: "5x Zombie" or "5x"
                if (stripped.matches("\\d+x\\s*.*")) {
                    return Integer.parseInt(stripped.split("x")[0].trim());
                }
                // Pattern: "Zombie x5" or "x5"
                if (stripped.matches(".*x\\d+.*")) {
                    String num = stripped.replaceAll(".*x(\\d+).*", "$1");
                    return Integer.parseInt(num);
                }
                // Pattern: "Zombie (5)"
                if (stripped.matches(".*\\(\\d+\\).*")) {
                    String num = stripped.replaceAll(".*\\((\\d+)\\).*", "$1");
                    return Integer.parseInt(num);
                }
            }

            return 1;
        } catch (Exception e) {
            return 1;
        }
    }
}

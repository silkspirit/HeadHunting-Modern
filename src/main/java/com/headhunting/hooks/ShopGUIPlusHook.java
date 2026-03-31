package com.headhunting.hooks;

import com.headhunting.HeadHunting;
import com.headhunting.data.HeadConfig;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Method;
import java.util.Map;

/**
 * Hook for ShopGUIPlus integration
 * Registers HeadHunting heads as custom shop items
 */
public class ShopGUIPlusHook {
    
    private final HeadHunting plugin;
    private Object shopGuiApi;
    private boolean hooked = false;
    
    public ShopGUIPlusHook(HeadHunting plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Attempt to hook into ShopGUIPlus
     */
    public boolean hook() {
        try {
            // Check if ShopGUIPlus is present
            if (plugin.getServer().getPluginManager().getPlugin("ShopGUIPlus") == null) {
                return false;
            }
            
            // Get the API instance via reflection
            Class<?> shopGuiPlusClass = Class.forName("net.brcdev.shopgui.ShopGuiPlusApi");
            Method getPluginMethod = shopGuiPlusClass.getMethod("getPlugin");
            shopGuiApi = getPluginMethod.invoke(null);
            
            if (shopGuiApi != null) {
                hooked = true;
                plugin.getLogger().info("Successfully hooked into ShopGUIPlus!");
                return true;
            }
        } catch (ClassNotFoundException e) {
            // Try alternative API class (older versions)
            try {
                Class<?> shopGuiClass = Class.forName("net.brcdev.shopgui.ShopGuiPlugin");
                Method getInstanceMethod = shopGuiClass.getMethod("getInstance");
                shopGuiApi = getInstanceMethod.invoke(null);
                
                if (shopGuiApi != null) {
                    hooked = true;
                    plugin.getLogger().info("Successfully hooked into ShopGUIPlus (legacy)!");
                    return true;
                }
            } catch (Exception ex) {
                plugin.getLogger().warning("ShopGUIPlus found but could not hook API: " + ex.getMessage());
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to hook into ShopGUIPlus: " + e.getMessage());
        }
        
        return false;
    }
    
    /**
     * Check if item is a valid HeadHunting head for shop transactions
     */
    public boolean isValidHead(ItemStack item) {
        return plugin.getHeadFactory().isHead(item);
    }
    
    /**
     * Get the sell price for a head item
     */
    public double getSellPrice(ItemStack item) {
        HeadConfig config = plugin.getHeadFactory().getHeadConfig(item);
        if (config != null) {
            return config.getSellPrice() * item.getAmount();
        }
        return 0;
    }
    
    /**
     * Get the buy price for a head item (typically higher than sell)
     */
    public double getBuyPrice(ItemStack item) {
        HeadConfig config = plugin.getHeadFactory().getHeadConfig(item);
        if (config != null) {
            // Buy price is 2x sell price by default
            return config.getSellPrice() * 2 * item.getAmount();
        }
        return 0;
    }
    
    /**
     * Generate shop.yml entries for all heads
     * This can be used to create a ShopGUIPlus compatible shop config
     */
    public String generateShopConfig() {
        StringBuilder sb = new StringBuilder();
        sb.append("# HeadHunting Heads Shop Configuration\n");
        sb.append("# Add this to your ShopGUIPlus shops/heads.yml\n\n");
        sb.append("heads:\n");
        sb.append("  name: \"&6&lMob Heads\"\n");
        sb.append("  fillItem:\n");
        sb.append("    material: BLACK_STAINED_GLASS_PANE\n");
        sb.append("    quantity: 1\n");
        sb.append("    name: \" \"\n");
        sb.append("  size: 54\n");
        sb.append("  items:\n");
        
        Map<EntityType, HeadConfig> heads = plugin.getConfigManager().getHeadConfigs();
        int slot = 10;
        int row = 0;
        
        for (Map.Entry<EntityType, HeadConfig> entry : heads.entrySet()) {
            HeadConfig config = entry.getValue();
            if (!config.isEnabled()) continue;
            
            sb.append("    ").append(slot).append(":\n");
            sb.append("      type: item\n");
            sb.append("      item:\n");
            sb.append("        material: PLAYER_HEAD\n");
            sb.append("        quantity: 1\n");
            sb.append("        name: \"").append(config.getDisplayName()).append("\"\n");
            sb.append("        lore:\n");
            sb.append("          - \"&8━━━━━━━━━━━━━━━━━\"\n");
            sb.append("          - \"&7Sell: &a").append(plugin.getConfigManager().getCurrencySymbol()).append(config.getSellPrice()).append("\"\n");
            sb.append("          - \"&7XP: &b").append(config.getXpValue()).append("\"\n");
            sb.append("          - \"&8━━━━━━━━━━━━━━━━━\"\n");
            sb.append("      sellPrice: ").append(config.getSellPrice()).append("\n");
            sb.append("      buyPrice: ").append(config.getSellPrice() * 2).append("\n");
            sb.append("      slot: ").append(slot).append("\n\n");
            
            slot++;
            if (slot % 9 == 8) {
                slot += 2; // Skip edges
                row++;
            }
            if (row >= 4) break; // Max 4 rows
        }
        
        return sb.toString();
    }
    
    public boolean isHooked() {
        return hooked;
    }
}

package com.headhunting.gui;

import com.headhunting.HeadHunting;
import com.headhunting.data.MaskConfig;
import com.headhunting.data.PlayerData;
import com.headhunting.data.PlayerData.MaskData;
import com.headhunting.utils.MessageUtil;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * GUI for viewing mask collection
 */
public class MasksGUI implements InventoryHolder {
    
    private final HeadHunting plugin;
    private final Player player;
    private final Inventory inventory;
    private int currentPage = 0;
    
    private static final int GUI_SIZE = 54;
    private static final int[] MASK_SLOTS = {
        10, 11, 12, 13, 14, 15, 16,
        19, 20, 21, 22, 23, 24, 25,
        28, 29, 30, 31, 32, 33, 34
    };
    private static final int INFO_SLOT = 4;
    private static final int PREV_PAGE_SLOT = 45;
    private static final int NEXT_PAGE_SLOT = 53;
    private static final int DEPOSIT_BUTTON_SLOT = 49;
    
    // Mystery Box texture for locked masks
    // Oak Wood Question Mark texture for locked masks
    private static final String MYSTERY_BOX_TEXTURE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYmFkYzA0OGE3Y2U3OGY3ZGFkNzJhMDdkYTI3ZDg1YzA5MTY4ODFlNTUyMmVlZWQxZTNkYWYyMTdhMzhjMWEifX19";
    
    public MasksGUI(HeadHunting plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
        this.inventory = Bukkit.createInventory(this, GUI_SIZE, MessageUtil.color("&6&lYour Mask Collection"));
        
        setupGUI();
    }
    
    private void setupGUI() {
        // Fill background
        ItemStack glass = createItem(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < GUI_SIZE; i++) {
            inventory.setItem(i, glass);
        }
        
        // Stats info
        PlayerData data = plugin.getDataManager().getPlayerData(player);
        int ownedCount = (int) data.getMasks().values().stream().filter(MaskData::isOwned).count();
        int totalMasks = plugin.getConfigManager().getMaskConfigs().size();
        
        ItemStack info = createItem(Material.PLAYER_HEAD, "&e&lYour Collection");
        ItemMeta infoMeta = info.getItemMeta();
        List<String> infoLore = new ArrayList<>();
        infoLore.add(MessageUtil.color("&7"));
        infoLore.add(MessageUtil.color("&7Masks Owned: &a" + ownedCount + "&7/&a" + totalMasks));
        infoLore.add(MessageUtil.color("&7Progress: " + MessageUtil.progressBar(ownedCount, totalMasks)));
        infoLore.add(MessageUtil.color("&7"));
        infoMeta.setLore(infoLore);
        info.setItemMeta(infoMeta);
        inventory.setItem(INFO_SLOT, info);
        
        // Deposit button
        ItemStack depositButton = createItem(Material.CAULDRON, "&a&lOpen Mask Crafting");
        ItemMeta depositMeta = depositButton.getItemMeta();
        List<String> depositLore = new ArrayList<>();
        depositLore.add(MessageUtil.color("&7"));
        depositLore.add(MessageUtil.color("&7Click to craft or upgrade masks"));
        depositLore.add(MessageUtil.color("&7"));
        depositMeta.setLore(depositLore);
        depositButton.setItemMeta(depositMeta);
        inventory.setItem(DEPOSIT_BUTTON_SLOT, depositButton);
        
        // Load masks
        loadMasks();
    }
    
    private void loadMasks() {
        PlayerData data = plugin.getDataManager().getPlayerData(player);
        Map<String, MaskConfig> allMasks = plugin.getConfigManager().getMaskConfigs();
        
        // Sort: owned first, then by tier
        List<Map.Entry<String, MaskConfig>> sortedMasks = new ArrayList<>(allMasks.entrySet());
        sortedMasks.sort((a, b) -> {
            MaskData dataA = data.getMaskData(a.getKey());
            MaskData dataB = data.getMaskData(b.getKey());
            boolean ownedA = dataA != null && dataA.isOwned();
            boolean ownedB = dataB != null && dataB.isOwned();
            
            if (ownedA != ownedB) {
                return ownedA ? -1 : 1;
            }
            
            // Then by tier
            boolean divineA = a.getValue().isDivine();
            boolean divineB = b.getValue().isDivine();
            if (divineA != divineB) {
                return divineA ? 1 : -1;
            }
            
            // Then by associated level
            return Integer.compare(a.getValue().getAssociatedLevel(), b.getValue().getAssociatedLevel());
        });
        
        int startIndex = currentPage * MASK_SLOTS.length;
        
        for (int i = 0; i < MASK_SLOTS.length; i++) {
            int maskIndex = startIndex + i;
            int slot = MASK_SLOTS[i];
            
            if (maskIndex >= sortedMasks.size()) {
                inventory.setItem(slot, null);
                continue;
            }
            
            Map.Entry<String, MaskConfig> entry = sortedMasks.get(maskIndex);
            String maskId = entry.getKey();
            MaskConfig config = entry.getValue();
            MaskData maskData = data.getMaskData(maskId);
            
            ItemStack maskItem = createMaskDisplayItem(config, maskData, maskId);
            inventory.setItem(slot, maskItem);
        }
        
        // Navigation
        if (currentPage > 0) {
            inventory.setItem(PREV_PAGE_SLOT, createItem(Material.ARROW, "&aPrevious Page"));
        }
        
        if ((currentPage + 1) * MASK_SLOTS.length < sortedMasks.size()) {
            inventory.setItem(NEXT_PAGE_SLOT, createItem(Material.ARROW, "&aNext Page"));
        }
    }
    
    private ItemStack createMaskDisplayItem(MaskConfig config, MaskData maskData, String maskId) {
        int level = maskData != null ? maskData.getLevel() : 0;
        boolean owned = maskData != null && maskData.isOwned();
        
        ItemStack item;
        
        if (owned) {
            item = plugin.getMaskFactory().createMask(maskId, level);
        } else {
            // Locked mask - use mystery box texture
            item = createMysteryBoxSkull("&5" + config.getDisplayName() + " &8(Locked)", maskId);
            ItemMeta meta = item.getItemMeta();
            List<String> lore = new ArrayList<>();
            lore.add(MessageUtil.color("&7"));
            lore.add(MessageUtil.color("&8━━━━━━━━━━━━━━━━━"));
            
            if (config.isDivine()) {
                lore.add(MessageUtil.color("&5✦ DIVINE MASK ✦"));
                if (config.requiresMission()) {
                    lore.add(MessageUtil.color("&7Requires mission completion"));
                }
            } else {
                lore.add(MessageUtil.color("&7Tier: &f" + (config.isDivine() ? "Divine" : "Rankup")));
            }
            
            lore.add(MessageUtil.color("&7Craft Cost: &e" + config.getCraftCost() + " heads"));
            lore.add(MessageUtil.color("&8━━━━━━━━━━━━━━━━━"));
            lore.add(MessageUtil.color("&7Use /deposit to craft"));
            
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        
        return item;
    }
    
    public void handleClick(InventoryClickEvent event) {
        int slot = event.getRawSlot();
        
        // Check if clicked in GUI
        if (slot < 0 || slot >= GUI_SIZE) {
            return;
        }
        
        // Navigation
        if (slot == PREV_PAGE_SLOT && currentPage > 0) {
            currentPage--;
            setupGUI();
            return;
        }
        
        if (slot == NEXT_PAGE_SLOT) {
            currentPage++;
            setupGUI();
            return;
        }
        
        // Deposit button
        if (slot == DEPOSIT_BUTTON_SLOT) {
            player.closeInventory();
            new DepositGUI(plugin, player).open();
            return;
        }
    }
    
    public void handleClose(InventoryCloseEvent event) {
        // Nothing special needed
    }
    
    public void open() {
        player.openInventory(inventory);
    }
    
    @Override
    public Inventory getInventory() {
        return inventory;
    }
    
    private ItemStack createItem(Material material, String name) {
        ItemStack item = new ItemStack(material, 1);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(MessageUtil.color(name));
        item.setItemMeta(meta);
        return item;
    }
    
    /**
     * Create a mystery box skull for locked masks
     */
    private ItemStack createMysteryBoxSkull(String displayName, String maskId) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) skull.getItemMeta();
        
        meta.setDisplayName(MessageUtil.color(displayName));
        
        // Apply mystery box texture
        try {
            UUID typeUUID = UUID.nameUUIDFromBytes(("LockedMask_" + maskId).getBytes());
            GameProfile profile = new GameProfile(typeUUID, "LockedMask");
            profile.getProperties().put("textures", new Property("textures", MYSTERY_BOX_TEXTURE));
            
            Field profileField = meta.getClass().getDeclaredField("profile");
            profileField.setAccessible(true);
            profileField.set(meta, profile);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to set mystery box texture: " + e.getMessage());
        }
        
        skull.setItemMeta(meta);
        return skull;
    }
}

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
 * GUI for depositing heads and crafting/leveling masks
 */
public class DepositGUI implements InventoryHolder {
    
    private final HeadHunting plugin;
    private final Player player;
    private final Inventory inventory;
    private String selectedMask = null;
    private int currentPage = 0;
    
    private static final int GUI_SIZE = 54;
    private static final int[] MASK_SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
    private static final int DEPOSIT_SLOT = 49;
    private static final int INFO_SLOT = 4;
    private static final int PREV_PAGE_SLOT = 45;
    private static final int NEXT_PAGE_SLOT = 53;
    
    // Mystery Box texture for locked masks
    // Oak Wood Question Mark texture for locked masks
    private static final String MYSTERY_BOX_TEXTURE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYmFkYzA0OGE3Y2U3OGY3ZGFkNzJhMDdkYTI3ZDg1YzA5MTY4ODFlNTUyMmVlZWQxZTNkYWYyMTdhMzhjMWEifX19";
    
    public DepositGUI(HeadHunting plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
        this.inventory = Bukkit.createInventory(this, GUI_SIZE, MessageUtil.color("&6&lMask Crafting & Leveling"));
        
        setupGUI();
    }
    
    private void setupGUI() {
        // Fill background with blue/cyan theme
        ItemStack cyanGlass = createItem(Material.CYAN_STAINED_GLASS_PANE, " ");
        ItemStack grayGlass = createItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < GUI_SIZE; i++) {
            if (i < 9 || i >= 45 || i % 9 == 0 || i % 9 == 8) {
                inventory.setItem(i, cyanGlass);
            } else {
                inventory.setItem(i, grayGlass);
            }
        }
        
        // Info item
        ItemStack info = createItem(Material.BOOK, "&b&lHow To Use");
        ItemMeta infoMeta = info.getItemMeta();
        List<String> infoLore = new ArrayList<>();
        infoLore.add(MessageUtil.color("&7"));
        infoLore.add(MessageUtil.color("&71. Click a mask to select it"));
        infoLore.add(MessageUtil.color("&72. Place heads in the deposit slot"));
        infoLore.add(MessageUtil.color("&73. Craft or level up your mask!"));
        infoLore.add(MessageUtil.color("&7"));
        infoMeta.setLore(infoLore);
        info.setItemMeta(infoMeta);
        inventory.setItem(INFO_SLOT, info);
        
        // Deposit slot
        ItemStack depositSlot = createItem(Material.HOPPER, "&b&lDeposit Heads Here");
        ItemMeta depositMeta = depositSlot.getItemMeta();
        List<String> depositLore = new ArrayList<>();
        depositLore.add(MessageUtil.color("&7"));
        depositLore.add(MessageUtil.color("&7Click with heads to deposit"));
        depositLore.add(MessageUtil.color("&7"));
        depositMeta.setLore(depositLore);
        depositSlot.setItemMeta(depositMeta);
        inventory.setItem(DEPOSIT_SLOT, depositSlot);
        
        // Load masks
        loadMasks();
        
        // Navigation
        if (currentPage > 0) {
            inventory.setItem(PREV_PAGE_SLOT, createItem(Material.ARROW, "&aPrevious Page"));
        }
    }
    
    private void loadMasks() {
        PlayerData data = plugin.getDataManager().getPlayerData(player);
        Map<String, MaskConfig> allMasks = plugin.getConfigManager().getMaskConfigs();
        
        List<String> maskIds = new ArrayList<>(allMasks.keySet());
        int startIndex = currentPage * MASK_SLOTS.length;
        
        for (int i = 0; i < MASK_SLOTS.length; i++) {
            int maskIndex = startIndex + i;
            int slot = MASK_SLOTS[i];
            
            if (maskIndex >= maskIds.size()) {
                inventory.setItem(slot, null);
                continue;
            }
            
            String maskId = maskIds.get(maskIndex);
            MaskConfig config = allMasks.get(maskId);
            MaskData maskData = data.getMaskData(maskId);
            
            ItemStack maskItem = createMaskDisplayItem(config, maskData, maskId);
            inventory.setItem(slot, maskItem);
        }
        
        // Next page button
        if ((currentPage + 1) * MASK_SLOTS.length < maskIds.size()) {
            inventory.setItem(NEXT_PAGE_SLOT, createItem(Material.ARROW, "&aNext Page"));
        }
    }
    
    private ItemStack createMaskDisplayItem(MaskConfig config, MaskData maskData, String maskId) {
        int level = maskData != null ? maskData.getLevel() : 0;
        boolean owned = maskData != null && maskData.isOwned();
        int deposited = maskData != null ? maskData.getDepositedHeads() : 0;
        
        ItemStack item;
        if (owned) {
            item = plugin.getMaskFactory().createMask(maskId, level);
        } else {
            // Use mystery box texture for locked masks
            item = createMysteryBoxSkull("&5" + config.getDisplayName() + " &8(Locked)", maskId);
        }
        
        ItemMeta meta = item.getItemMeta();
        List<String> lore = meta.getLore() != null ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        
        lore.add(MessageUtil.color("&7"));
        lore.add(MessageUtil.color("&8━━━━━━━━━━━━━━━━━"));
        
        if (owned) {
            if (level < 5) {
                if (config.isEssenceOnly() || config.isDivine()) {
                    lore.add(MessageUtil.color("&7This mask cannot be leveled with heads"));
                    lore.add(MessageUtil.color("&7Use &d✦ Spirit Essence &7to upgrade"));
                } else {
                    int cost = config.getLevelCost(level + 1);
                    lore.add(MessageUtil.color("&7Deposited: &e" + deposited + "&7/&e" + cost));
                    lore.add(MessageUtil.color("&7Progress: " + MessageUtil.progressBar(deposited, cost)));
                    if (deposited >= cost) {
                        lore.add(MessageUtil.color("&a&lClick to Level Up!"));
                    }
                }
            } else {
                lore.add(MessageUtil.color("&6&lMAX LEVEL"));
            }
        } else {
            if (config.isEssenceOnly() || config.isDivine()) {
                lore.add(MessageUtil.color("&7This mask cannot be leveled with heads"));
                lore.add(MessageUtil.color("&7Use &d✦ Spirit Essence &7to upgrade"));
            } else {
                int cost = config.getCraftCost();
                lore.add(MessageUtil.color("&7Deposited: &e" + deposited + "&7/&e" + cost));
                lore.add(MessageUtil.color("&7Progress: " + MessageUtil.progressBar(deposited, cost)));
                if (deposited >= cost) {
                    lore.add(MessageUtil.color("&a&lClick to Craft!"));
                }
            }
        }
        
        if (maskId.equals(selectedMask)) {
            lore.add(MessageUtil.color("&b&l> SELECTED <"));
        } else {
            lore.add(MessageUtil.color("&7Click to select"));
        }
        
        meta.setLore(lore);
        item.setItemMeta(meta);
        
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
        
        // Deposit slot
        if (slot == DEPOSIT_SLOT) {
            handleDeposit(event);
            return;
        }
        
        // Mask selection
        for (int i = 0; i < MASK_SLOTS.length; i++) {
            if (slot == MASK_SLOTS[i]) {
                handleMaskClick(i);
                return;
            }
        }
    }
    
    private void handleMaskClick(int index) {
        Map<String, MaskConfig> allMasks = plugin.getConfigManager().getMaskConfigs();
        List<String> maskIds = new ArrayList<>(allMasks.keySet());
        int maskIndex = currentPage * MASK_SLOTS.length + index;
        
        if (maskIndex >= maskIds.size()) return;
        
        String maskId = maskIds.get(maskIndex);
        PlayerData data = plugin.getDataManager().getPlayerData(player);
        MaskData maskData = data.getMaskData(maskId);
        MaskConfig config = allMasks.get(maskId);
        
        // Check if player has reached the required level for this mask
        int playerLevel = data.getLevel();
        int requiredLevel = config.getAssociatedLevel();
        if (requiredLevel > playerLevel) {
            MessageUtil.send(player, "&cYou need to reach Level " + requiredLevel + " to craft this mask!");
            return;
        }
        
        boolean owned = maskData != null && maskData.isOwned();
        int deposited = maskData != null ? maskData.getDepositedHeads() : 0;
        
        if (owned) {
            int level = maskData.getLevel();
            if (level < 5) {
                int cost = config.getLevelCost(level + 1);
                if (deposited >= cost) {
                    // Level up
                    plugin.getMaskManager().levelUpMask(player, maskId);
                    setupGUI();
                    return;
                }
            }
        } else {
            int cost = config.getCraftCost();
            if (deposited >= cost) {
                // Craft
                plugin.getMaskManager().craftMask(player, maskId);
                setupGUI();
                return;
            }
        }
        
        // Select mask
        selectedMask = maskId;
        setupGUI();
    }
    
    private void handleDeposit(InventoryClickEvent event) {
        if (selectedMask == null) {
            MessageUtil.send(player, "&cSelect a mask first!");
            return;
        }
        
        // Check if this mask is essence-only
        MaskConfig config = plugin.getConfigManager().getMaskConfigs().get(selectedMask);
        if (config != null && (config.isEssenceOnly() || config.isDivine())) {
            MessageUtil.send(player, "&cThis mask cannot be leveled with heads! Use &d✦ Spirit Essence &cto upgrade.");
            return;
        }
        
        // Check if player has reached the required level for this mask
        if (config != null) {
            PlayerData data = plugin.getDataManager().getPlayerData(player);
            int playerLevel = data.getLevel();
            int requiredLevel = config.getAssociatedLevel();
            if (requiredLevel > playerLevel) {
                MessageUtil.send(player, "&cYou need to reach Level " + requiredLevel + " to deposit heads for this mask!");
                return;
            }
        }
        
        ItemStack cursor = event.getCursor();
        if (cursor == null || cursor.getType() == Material.AIR) {
            return;
        }
        
        // Check if it's a valid head
        if (!plugin.getHeadFactory().isHead(cursor)) {
            MessageUtil.send(player, "&cYou can only deposit mob heads!");
            return;
        }
        
        // Deposit heads
        int amount = cursor.getAmount();
        plugin.getMaskManager().depositHeads(player, selectedMask, amount);
        
        // Track mission progress
        plugin.getMissionManager().addProgress(player,
            com.headhunting.data.MissionConfig.MissionType.DEPOSIT_HEADS, amount);
        
        // Remove from cursor
        event.setCursor(null);
        
        MessageUtil.send(player, "&aDeposited &e" + amount + " &aheads toward &6" + selectedMask + "&a!");
        setupGUI();
    }
    
    public void handleClose(InventoryCloseEvent event) {
        // Nothing special needed on close
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

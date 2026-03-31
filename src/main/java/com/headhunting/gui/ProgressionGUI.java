package com.headhunting.gui;

import com.headhunting.HeadHunting;
import com.headhunting.data.LevelConfig;
import com.headhunting.data.MaskConfig;
import com.headhunting.data.PlayerData;
import com.headhunting.utils.MessageUtil;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
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
 * Progression roadmap GUI - shows level progress and upcoming unlocks
 */
public class ProgressionGUI implements InventoryHolder {
    
    // Brown Question Mark texture for locked masks
    private static final String MYSTERY_BOX_TEXTURE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNjg0OWViZjZkMmViZGU5ODVkMjkwNzg3YmNjNDEzMGI1Yjk2Y2EzYTk2MDI3YTI5Y2Q1NDVmOTExZWJmYmM2In19fQ==";
    
    private static final int PREV_PAGE_SLOT = 45;
    private static final int NEXT_PAGE_SLOT = 53;
    private static final int LEVELS_PER_PAGE = 7;
    private static final int MAX_LEVEL = 14;
    
    private final HeadHunting plugin;
    private final Player viewer;
    private final Player target;
    private final Inventory inventory;
    private int currentPage = 0;
    
    public ProgressionGUI(HeadHunting plugin, Player viewer, Player target) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.target = target;
        
        String title = viewer.equals(target) 
            ? "Hunter Progression" 
            : target.getName() + "'s Progression";
        this.inventory = Bukkit.createInventory(this, 54, MessageUtil.color("&b&l" + title));
        
        setupGUI();
    }
    
    private void setupGUI() {
        PlayerData data = plugin.getDataManager().getPlayerData(target);
        int currentLevel = data.getLevel();
        int currentXp = data.getXp();
        
        // Fill border with cyan/blue glass
        ItemStack border = createItem(Material.CYAN_STAINED_GLASS_PANE, " "); // Cyan
        ItemStack corner = createItem(Material.BLUE_STAINED_GLASS_PANE, " "); // Blue
        for (int i = 0; i < 9; i++) {
            inventory.setItem(i, border);
            inventory.setItem(45 + i, border);
        }
        for (int i = 0; i < 6; i++) {
            inventory.setItem(i * 9, border);
            inventory.setItem(i * 9 + 8, border);
        }
        // Corners blue
        inventory.setItem(0, corner);
        inventory.setItem(8, corner);
        inventory.setItem(45, corner);
        inventory.setItem(53, corner);
        
        // Player head with stats (slot 4)
        ItemStack playerHead = createPlayerHead(target, data);
        inventory.setItem(4, playerHead);
        
        // Level progression row (slots 19-25) - paginated
        int[] levelSlots = {19, 20, 21, 22, 23, 24, 25};
        int startLevel = currentPage * LEVELS_PER_PAGE + 1;
        
        for (int i = 0; i < LEVELS_PER_PAGE; i++) {
            int level = startLevel + i;
            if (level > MAX_LEVEL) {
                inventory.setItem(levelSlots[i], null);
            } else {
                ItemStack levelItem = createLevelItem(level, currentLevel, data);
                inventory.setItem(levelSlots[i], levelItem);
            }
        }
        
        // Page navigation arrows
        int totalPages = (int) Math.ceil((double) MAX_LEVEL / LEVELS_PER_PAGE);
        
        if (currentPage > 0) {
            ItemStack prevPage = createItem(Material.ARROW, "&a« Previous Page");
            ItemMeta prevMeta = prevPage.getItemMeta();
            List<String> prevLore = new ArrayList<>();
            prevLore.add(MessageUtil.color("&7Page " + currentPage + "/" + totalPages));
            prevLore.add(MessageUtil.color("&7Click to go back"));
            prevMeta.setLore(prevLore);
            prevPage.setItemMeta(prevMeta);
            inventory.setItem(PREV_PAGE_SLOT, prevPage);
        } else {
            inventory.setItem(PREV_PAGE_SLOT, createItem(Material.BLUE_STAINED_GLASS_PANE, " "));
        }
        
        if (currentPage < totalPages - 1) {
            ItemStack nextPage = createItem(Material.ARROW, "&a» Next Page");
            ItemMeta nextMeta = nextPage.getItemMeta();
            List<String> nextLore = new ArrayList<>();
            nextLore.add(MessageUtil.color("&7Page " + (currentPage + 2) + "/" + totalPages));
            nextLore.add(MessageUtil.color("&7Click to see more levels"));
            nextMeta.setLore(nextLore);
            nextPage.setItemMeta(nextMeta);
            inventory.setItem(NEXT_PAGE_SLOT, nextPage);
        } else {
            inventory.setItem(NEXT_PAGE_SLOT, createItem(Material.BLUE_STAINED_GLASS_PANE, " "));
        }
        
        // Masks row (slots 37-43) - show masks for levels on current page
        int endLevel = Math.min(startLevel + LEVELS_PER_PAGE - 1, MAX_LEVEL);
        List<MaskConfig> pageMasks = getMasksForLevelRange(startLevel, endLevel);
        int[] maskSlots = {37, 38, 39, 40, 41, 42, 43};
        
        // Clear mask slots first
        for (int slot : maskSlots) {
            inventory.setItem(slot, null);
        }
        
        for (int i = 0; i < Math.min(7, pageMasks.size()); i++) {
            MaskConfig mask = pageMasks.get(i);
            ItemStack maskItem = createMaskPreview(mask, data);
            inventory.setItem(maskSlots[i], maskItem);
        }
        
        // Section labels
        inventory.setItem(10, createItem(Material.NETHER_STAR, "&b&lLevel Roadmap"));
        
        // Mask Rewards header - enchanted gold ingot
        ItemStack maskRewardsHeader = createItem(Material.GOLD_INGOT, "&d&lMask Rewards");
        ItemMeta maskRewardsMeta = maskRewardsHeader.getItemMeta();
        maskRewardsMeta.addEnchant(org.bukkit.enchantments.Enchantment.DURABILITY, 1, true);
        maskRewardsMeta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
        maskRewardsHeader.setItemMeta(maskRewardsMeta);
        inventory.setItem(28, maskRewardsHeader);
        
        // Perks info (slot 16)
        inventory.setItem(16, createPerksItem(currentLevel));
    }
    
    private ItemStack createPlayerHead(Player target, PlayerData data) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        meta.setOwningPlayer(org.bukkit.Bukkit.getOfflinePlayer(target.getName()));
        meta.setDisplayName(MessageUtil.color("&b&l" + target.getName()));
        
        int level = data.getLevel();
        int xp = data.getXp();
        double progress = plugin.getLevelManager().getProgressPercentage(target);
        
        List<String> lore = new ArrayList<>();
        lore.add(MessageUtil.color("&7"));
        lore.add(MessageUtil.color("&7Level: &b" + level + (level >= 14 ? " &a&lMAX" : "")));
        lore.add(MessageUtil.color("&7Total XP: &b" + MessageUtil.formatNumber(xp)));
        lore.add(MessageUtil.color("&7Progress: &b" + String.format("%.1f", progress) + "%"));
        lore.add(MessageUtil.color("&7"));
        lore.add(MessageUtil.color("&7Masks Owned: &d" + countOwnedMasks(data)));
        lore.add(MessageUtil.color("&7Fishing Luck: &a+" + (level * 2) + "%"));
        
        meta.setLore(lore);
        head.setItemMeta(meta);
        return head;
    }
    
    private ItemStack createLevelItem(int level, int currentLevel, PlayerData data) {
        LevelConfig config = plugin.getConfigManager().getLevelConfig(level);
        if (config == null) {
            return createItem(Material.BARRIER, "&cInvalid Level");
        }
        
        boolean unlocked = level <= currentLevel;
        boolean isCurrent = level == currentLevel;
        boolean isNext = level == currentLevel + 1;
        
        Material material;

        if (unlocked) {
            material = Material.LIME_STAINED_GLASS_PANE;
        } else if (isNext) {
            material = Material.YELLOW_STAINED_GLASS_PANE;
        } else {
            material = Material.GRAY_STAINED_GLASS_PANE;
        }
        
        String status = unlocked ? "&a✓ Unlocked" : (isNext ? "&e➤ Next" : "&c✗ Locked");
        String displayName = (isCurrent ? "&b&l➤ " : "") + "&fLevel " + level + " " + status;
        
        ItemStack item = createItem(material, displayName);
        ItemMeta meta = item.getItemMeta();
        
        List<String> lore = new ArrayList<>();
        lore.add(MessageUtil.color("&7"));
        
        if (config.getMobDisplayName() != null) {
            lore.add(MessageUtil.color("&7Mob: &e" + config.getMobDisplayName()));
        } else if (config.getMobType() != null) {
            lore.add(MessageUtil.color("&7Mob: &e" + formatMobName(config.getMobType().name())));
        }
        
        lore.add(MessageUtil.color("&7XP Required: &b" + MessageUtil.formatNumber(config.getXpRequired())));
        lore.add(MessageUtil.color("&7Cost: &a" + MessageUtil.formatMoney(config.getMoneyCost())));
        lore.add(MessageUtil.color("&7Fishing Luck: &a+" + (level * 2) + "%"));
        
        if (isNext) {
            lore.add(MessageUtil.color("&7"));
            int xpNeeded = config.getXpRequired() - data.getXp();
            if (xpNeeded > 0) {
                lore.add(MessageUtil.color("&eNeed " + MessageUtil.formatNumber(xpNeeded) + " more XP"));
            } else {
                lore.add(MessageUtil.color("&aReady to rank up!"));
            }
        }
        
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }
    
    private ItemStack createMaskPreview(MaskConfig mask, PlayerData data) {
        PlayerData.MaskData maskData = data.getMasks().get(mask.getId());
        boolean owned = maskData != null && maskData.isOwned();
        
        ItemStack item;
        if (owned) {
            item = plugin.getMaskFactory().createMask(mask.getId(), maskData.getLevel());
        } else {
            // Mystery box for locked mask
            item = createMysteryBoxSkull("&5" + mask.getDisplayName() + " &8(Locked)", mask.getId());
        }
        
        ItemMeta meta = item.getItemMeta();
        
        List<String> lore = new ArrayList<>();
        lore.add(MessageUtil.color("&7"));
        
        if (owned) {
            lore.add(MessageUtil.color("&7Tier: &d" + mask.getTier()));
            lore.add(MessageUtil.color("&7Craft Cost: &b" + mask.getCraftCost() + " heads"));
            if (mask.getAssociatedLevel() > 0) {
                lore.add(MessageUtil.color("&7Requires Level: &e" + mask.getAssociatedLevel()));
            }
            lore.add(MessageUtil.color("&7"));
            lore.add(MessageUtil.color("&aLevel " + maskData.getLevel() + "/5"));
        } else {
            lore.add(MessageUtil.color("&8━━━━━━━━━━━━━━━━━"));
            lore.add(MessageUtil.color("&8Unlock by reaching Level " + mask.getAssociatedLevel()));
            if (mask.requiresMission()) {
                lore.add(MessageUtil.color("&8and completing the divine mission!"));
            }
            lore.add(MessageUtil.color("&8━━━━━━━━━━━━━━━━━"));
        }
        
        meta.setLore(lore);
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
    
    private ItemStack createPerksItem(int level) {
        ItemStack item = createItem(Material.BOOK, "&a&lYour Perks");
        ItemMeta meta = item.getItemMeta();
        
        List<String> lore = new ArrayList<>();
        lore.add(MessageUtil.color("&7"));
        lore.add(MessageUtil.color("&7Based on Level " + level + ":"));
        lore.add(MessageUtil.color("&7"));
        lore.add(MessageUtil.color("&a• &7Fishing Luck: &a+" + (level * 2) + "%"));
        lore.add(MessageUtil.color("&a• &7Darkzone Bonus: &a+" + (level * 5) + "%"));
        lore.add(MessageUtil.color("&a• &7Head Drop Luck: &a+" + (level) + "%"));
        lore.add(MessageUtil.color("&7"));
        lore.add(MessageUtil.color("&8Higher levels = better rewards!"));
        
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }
    
    /**
     * Get masks that unlock within a specific level range (for current page)
     */
    private List<MaskConfig> getMasksForLevelRange(int startLevel, int endLevel) {
        List<MaskConfig> masks = new ArrayList<>();
        Map<String, MaskConfig> allMasks = plugin.getConfigManager().getMaskConfigs();
        
        for (MaskConfig mask : allMasks.values()) {
            int reqLevel = mask.getAssociatedLevel();
            if (reqLevel >= startLevel && reqLevel <= endLevel) {
                masks.add(mask);
            }
        }
        
        // Sort by associated level
        masks.sort((a, b) -> a.getAssociatedLevel() - b.getAssociatedLevel());
        return masks;
    }
    
    private int countOwnedMasks(PlayerData data) {
        return (int) data.getMasks().values().stream()
            .filter(PlayerData.MaskData::isOwned)
            .count();
    }
    
    private ItemStack createItem(Material material, String name) {
        ItemStack item = new ItemStack(material, 1);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(MessageUtil.color(name));
        item.setItemMeta(meta);
        return item;
    }
    
    private String formatMobName(String name) {
        if (name == null) return "Unknown";
        String[] words = name.toLowerCase().split("_");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (word.length() > 0) {
                result.append(Character.toUpperCase(word.charAt(0)))
                      .append(word.substring(1))
                      .append(" ");
            }
        }
        return result.toString().trim();
    }
    
    public void open() {
        viewer.openInventory(inventory);
    }
    
    @Override
    public Inventory getInventory() {
        return inventory;
    }
    
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        
        int slot = event.getRawSlot();
        int totalPages = (int) Math.ceil((double) MAX_LEVEL / LEVELS_PER_PAGE);
        
        if (slot == PREV_PAGE_SLOT && currentPage > 0) {
            currentPage--;
            setupGUI();
        } else if (slot == NEXT_PAGE_SLOT && currentPage < totalPages - 1) {
            currentPage++;
            setupGUI();
        }
    }
}

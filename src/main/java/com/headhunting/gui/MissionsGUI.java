package com.headhunting.gui;

import com.headhunting.HeadHunting;
import com.headhunting.data.MaskConfig;
import com.headhunting.data.MissionConfig;
import com.headhunting.data.MissionConfig.MissionRequirement;
import com.headhunting.data.PlayerData;
import com.headhunting.managers.DailyMissionManager;
import com.headhunting.managers.DailyMissionManager.DailyMission;
import com.headhunting.utils.MessageUtil;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.lang.reflect.Field;
import java.util.*;

/**
 * GUI for viewing divine missions
 */
public class MissionsGUI implements InventoryHolder {
    
    private final HeadHunting plugin;
    private final Player player;
    private final Inventory inventory;
    
    private static final int GUI_SIZE = 54;
    
    // Divine mission slots - 2 rows of 4 for 8 missions
    private static final int[] DIVINE_MISSION_SLOTS_ROW1 = {10, 12, 14, 16};  // Row 2
    private static final int[] DIVINE_MISSION_SLOTS_ROW2 = {19, 21, 23, 25};  // Row 3
    private static final int[] DAILY_MISSION_SLOTS = {37, 39, 41, 43};        // Row 5
    
    public MissionsGUI(HeadHunting plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
        this.inventory = Bukkit.createInventory(this, GUI_SIZE, MessageUtil.color("&5&lMissions"));
        
        setupGUI();
    }
    
    private void setupGUI() {
        // Fill background with blue theme
        ItemStack cyanGlass = createItem(Material.CYAN_STAINED_GLASS_PANE, " ");
        ItemStack grayGlass = createItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        ItemStack blueGlass = createItem(Material.BLUE_STAINED_GLASS_PANE, " ");
        
        for (int i = 0; i < GUI_SIZE; i++) {
            if (i < 9 || i >= 45 || i % 9 == 0 || i % 9 == 8) {
                inventory.setItem(i, cyanGlass);
            } else {
                inventory.setItem(i, grayGlass);
            }
        }
        
        // Divine Missions Header (row 1)
        ItemStack divineHeader = createItem(Material.NETHER_STAR, "&b&l✦ Divine Missions ✦");
        ItemMeta divineHeaderMeta = divineHeader.getItemMeta();
        divineHeaderMeta.addEnchant(Enchantment.DURABILITY, 1, true);
        divineHeaderMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        List<String> divineHeaderLore = new ArrayList<>();
        divineHeaderLore.add(MessageUtil.color("&7"));
        divineHeaderLore.add(MessageUtil.color("&7Complete missions to unlock"));
        divineHeaderLore.add(MessageUtil.color("&b&lDivine Masks&7!"));
        divineHeaderLore.add(MessageUtil.color("&7"));
        divineHeaderLore.add(MessageUtil.color("&8Sorted by difficulty"));
        divineHeaderMeta.setLore(divineHeaderLore);
        divineHeader.setItemMeta(divineHeaderMeta);
        inventory.setItem(4, divineHeader);
        
        // Separator (row 4)
        for (int i = 27; i < 36; i++) {
            inventory.setItem(i, blueGlass);
        }
        
        // Daily Missions Header (slot 31)
        ItemStack dailyHeader = createItem(Material.CLOCK, "&e&l⚡ Daily Missions ⚡");
        ItemMeta dailyHeaderMeta = dailyHeader.getItemMeta();
        dailyHeaderMeta.addEnchant(Enchantment.DURABILITY, 1, true);
        dailyHeaderMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        List<String> dailyHeaderLore = new ArrayList<>();
        dailyHeaderLore.add(MessageUtil.color("&7"));
        dailyHeaderLore.add(MessageUtil.color("&7Refresh every day at midnight!"));
        dailyHeaderLore.add(MessageUtil.color("&e&lComplete for bonus rewards!"));
        dailyHeaderMeta.setLore(dailyHeaderLore);
        dailyHeader.setItemMeta(dailyHeaderMeta);
        inventory.setItem(31, dailyHeader);
        
        // Load missions
        loadMissions();
        loadDailyMissions();
    }
    
    private void loadMissions() {
        Map<String, MissionConfig> missions = plugin.getMissionManager().getAllMissions();
        PlayerData data = plugin.getDataManager().getPlayerData(player);
        
        // Sort missions by difficulty
        List<MissionConfig> missionList = new ArrayList<>(missions.values());
        missionList.sort(Comparator.comparingInt(MissionConfig::getDifficulty));
        
        // Place missions in slots (up to 8)
        int totalMissions = Math.min(8, missionList.size());
        
        for (int i = 0; i < totalMissions; i++) {
            MissionConfig mission = missionList.get(i);
            int slot;
            
            if (i < 4) {
                slot = DIVINE_MISSION_SLOTS_ROW1[i];
            } else {
                slot = DIVINE_MISSION_SLOTS_ROW2[i - 4];
            }
            
            ItemStack missionItem = createMissionItem(mission, data);
            inventory.setItem(slot, missionItem);
        }
    }
    
    private void loadDailyMissions() {
        DailyMissionManager dailyManager = plugin.getDailyMissionManager();
        if (dailyManager == null) {
            plugin.getLogger().warning("[MissionsGUI] DailyMissionManager is NULL!");
            return;
        }
        
        List<DailyMission> activeMissions = dailyManager.getActiveMissions();
        
        for (int i = 0; i < Math.min(DAILY_MISSION_SLOTS.length, activeMissions.size()); i++) {
            DailyMission mission = activeMissions.get(i);
            int slot = DAILY_MISSION_SLOTS[i];
            
            ItemStack missionItem = createDailyMissionItem(mission);
            inventory.setItem(slot, missionItem);
        }
    }
    
    private ItemStack createDailyMissionItem(DailyMission mission) {
        boolean completed = plugin.getDailyMissionManager().isComplete(player, mission.getId());
        int progress = plugin.getDailyMissionManager().getProgress(player, mission.getId());
        int required = mission.getRequired();
        double percent = (double) progress / required * 100;
        
        Material material;
        try {
            material = Material.valueOf(mission.getIcon());
        } catch (Exception e) {
            material = Material.PAPER;
        }
        
        String statusColor = completed ? "&a" : (percent >= 50 ? "&e" : "&7");
        String statusText = completed ? "&a&lCOMPLETE!" : String.format("&7%d/%d (%.0f%%)", progress, required, percent);
        
        ItemStack item = createItem(material, statusColor + mission.getDisplayName());
        ItemMeta meta = item.getItemMeta();
        
        // Add glow if completed
        if (completed) {
            meta.addEnchant(Enchantment.DURABILITY, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        
        List<String> lore = new ArrayList<>();
        lore.add(MessageUtil.color("&7"));
        lore.add(MessageUtil.color("&8" + mission.getDescription()));
        lore.add(MessageUtil.color("&7"));
        lore.add(MessageUtil.color("&7Progress: " + statusText));
        lore.add(MessageUtil.color("&7"));
        
        if (completed) {
            lore.add(MessageUtil.color("&a✓ Rewards claimed!"));
        } else {
            lore.add(MessageUtil.color("&e&lRewards:"));
            lore.add(MessageUtil.color("&7Complete to claim!"));
        }
        
        lore.add(MessageUtil.color("&7"));
        lore.add(MessageUtil.color("&8Resets at midnight"));
        
        meta.setLore(lore);
        item.setItemMeta(meta);
        
        return item;
    }
    
    private ItemStack createMissionItem(MissionConfig mission, PlayerData data) {
        boolean completed = data.getMissionProgress(mission.getId()).isCompleted();
        double progress = plugin.getMissionManager().getMissionProgress(player, mission);
        
        // Get custom icon from mission config
        ItemStack item = createMissionIcon(mission, completed, progress);
        ItemMeta meta = item.getItemMeta();
        
        // Build display name with status
        String statusColor = completed ? "&a" : (progress >= 50 ? "&e" : "&7");
        String statusText = completed ? "&a&lCOMPLETE" : String.format("&7%.1f%% Complete", progress);
        
        meta.setDisplayName(MessageUtil.color(statusColor + MessageUtil.stripColor(mission.getDisplayName())));
        
        // Add glow if specified or if completed
        if (completed || mission.hasIconGlow()) {
            meta.addEnchant(Enchantment.DURABILITY, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        
        List<String> lore = new ArrayList<>();
        
        // Difficulty indicator
        int difficulty = mission.getDifficulty();
        String difficultyStars = getDifficultyStars(difficulty);
        lore.add(MessageUtil.color("&8Difficulty: " + difficultyStars));
        lore.add(MessageUtil.color("&7"));
        lore.add(MessageUtil.color("&8" + mission.getDescription()));
        lore.add(MessageUtil.color("&7"));
        lore.add(MessageUtil.color("&7Status: " + statusText));
        lore.add(MessageUtil.color("&7"));
        lore.add(MessageUtil.color("&e&lRequirements:"));
        
        for (MissionRequirement req : mission.getRequirements()) {
            int current = plugin.getMissionManager().getPlayerProgress(player, mission, req);
            int required = req.getAmount();
            boolean reqComplete = current >= required;
            
            String checkmark = reqComplete ? "&a✓" : "&c✗";
            String progressText = reqComplete ? "&a" + required + "/" + required : "&7" + current + "/" + required;
            
            lore.add(MessageUtil.color(checkmark + " &7" + req.getDescription() + " &8[" + progressText + "&8]"));
        }
        
        lore.add(MessageUtil.color("&7"));
        
        // Get display name for the mask
        String maskId = mission.getUnlocksMask();
        String maskDisplayName = maskId;
        MaskConfig maskConfig = plugin.getConfigManager().getMaskConfig(maskId);
        if (maskConfig != null) {
            maskDisplayName = maskConfig.getDisplayName();
        }
        
        if (completed) {
            lore.add(MessageUtil.color("&a&l✓ Unlocked: &r" + maskDisplayName));
        } else {
            lore.add(MessageUtil.color("&7Unlocks: &d" + maskDisplayName));
        }
        
        meta.setLore(lore);
        item.setItemMeta(meta);
        
        return item;
    }
    
    /**
     * Create the mission icon based on config settings
     */
    private ItemStack createMissionIcon(MissionConfig mission, boolean completed, double progress) {
        // Check for custom skull texture
        String skullTexture = mission.getIconSkull();
        if (skullTexture != null && !skullTexture.isEmpty()) {
            return createCustomSkull(skullTexture, mission.getId());
        }
        
        // Use material from config or default based on progress
        Material material = mission.getIconMaterial();

        if (material == null) {
            // Fallback to progress-based materials
            if (completed) {
                material = Material.EMERALD_BLOCK;
            } else if (progress >= 75) {
                material = Material.DIAMOND_BLOCK;
            } else if (progress >= 50) {
                material = Material.GOLD_BLOCK;
            } else if (progress > 0) {
                material = Material.IRON_BLOCK;
            } else {
                material = Material.COAL_BLOCK;
            }
        }

        return new ItemStack(material, 1);
    }
    
    /**
     * Create a custom skull with base64 texture
     */
    private ItemStack createCustomSkull(String texture, String missionId) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) skull.getItemMeta();
        
        try {
            UUID typeUUID = UUID.nameUUIDFromBytes(("Mission_" + missionId).getBytes());
            GameProfile profile = new GameProfile(typeUUID, "Mission_" + missionId);
            profile.getProperties().put("textures", new Property("textures", texture));
            
            Field profileField = meta.getClass().getDeclaredField("profile");
            profileField.setAccessible(true);
            profileField.set(meta, profile);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to set mission skull texture: " + e.getMessage());
        }
        
        skull.setItemMeta(meta);
        return skull;
    }
    
    /**
     * Get difficulty stars display
     */
    private String getDifficultyStars(int difficulty) {
        StringBuilder stars = new StringBuilder();
        
        // Color based on difficulty
        String color;
        if (difficulty <= 2) {
            color = "&a"; // Green - Easy
        } else if (difficulty <= 4) {
            color = "&e"; // Yellow - Medium
        } else if (difficulty <= 6) {
            color = "&6"; // Orange - Hard
        } else {
            color = "&c"; // Red - Very Hard
        }
        
        stars.append(color);
        for (int i = 0; i < Math.min(difficulty, 8); i++) {
            stars.append("★");
        }
        for (int i = difficulty; i < 8; i++) {
            stars.append("&8☆");
        }
        
        return stars.toString();
    }
    
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        // Missions GUI is view-only
    }
    
    public void handleClose(InventoryCloseEvent event) {
        // Nothing to do
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
}

package com.headhunting.gui;

import com.headhunting.HeadHunting;
import com.headhunting.data.PlayerData;
import com.headhunting.managers.LeaderboardManager;
import com.headhunting.managers.LeaderboardManager.LeaderboardEntry;
import com.headhunting.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;

/**
 * Leaderboard GUI showing top hunters.
 * Reads from the async LeaderboardManager cache — never blocks the main thread.
 */
public class LeaderboardGUI implements InventoryHolder {
    
    private final HeadHunting plugin;
    private final Player viewer;
    private final Inventory inventory;
    
    public LeaderboardGUI(HeadHunting plugin, Player viewer) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.inventory = Bukkit.createInventory(this, 54, MessageUtil.color("&b&lHeadHunting Leaderboard"));
        
        setupGUI();
    }
    
    private void setupGUI() {
        // Fill border
        ItemStack border = createItem(Material.CYAN_STAINED_GLASS_PANE, " ");
        ItemStack corner = createItem(Material.BLUE_STAINED_GLASS_PANE, " ");
        
        for (int i = 0; i < 9; i++) {
            inventory.setItem(i, border);
            inventory.setItem(45 + i, border);
        }
        for (int i = 0; i < 6; i++) {
            inventory.setItem(i * 9, border);
            inventory.setItem(i * 9 + 8, border);
        }
        inventory.setItem(0, corner);
        inventory.setItem(8, corner);
        inventory.setItem(45, corner);
        inventory.setItem(53, corner);
        
        // Header
        ItemStack header = createItem(Material.NETHER_STAR, "&b&l✦ Top Hunters ✦");
        ItemMeta headerMeta = header.getItemMeta();
        List<String> headerLore = new ArrayList<>();
        headerLore.add(MessageUtil.color("&7"));
        headerLore.add(MessageUtil.color("&7The best HeadHunters on the server!"));
        headerMeta.setLore(headerLore);
        header.setItemMeta(headerMeta);
        inventory.setItem(4, header);
        
        // Get cached leaderboard (instant — no DB or file I/O)
        List<LeaderboardEntry> topPlayers = plugin.getLeaderboardManager().getTopPlayers();
        
        // Display slots for top 10
        int[] slots = {19, 20, 21, 22, 23, 24, 25, 28, 31, 34};
        String[] medals = {"&6&l1st", "&f&l2nd", "&c&l3rd", "&74th", "&75th", "&76th", "&77th", "&78th", "&79th", "&710th"};
        
        if (topPlayers.isEmpty()) {
            // Cache still loading — show placeholder
            ItemStack loading = createItem(Material.CLOCK, "&eLoading...");
            ItemMeta loadMeta = loading.getItemMeta();
            List<String> loadLore = new ArrayList<>();
            loadLore.add(MessageUtil.color("&7Leaderboard data is loading."));
            loadLore.add(MessageUtil.color("&7Please try again in a moment."));
            loadMeta.setLore(loadLore);
            loading.setItemMeta(loadMeta);
            inventory.setItem(22, loading);
        } else {
            for (int i = 0; i < Math.min(10, topPlayers.size()); i++) {
                LeaderboardEntry entry = topPlayers.get(i);
                String playerName = entry.getPlayerName();
                
                ItemStack head = new ItemStack(Material.PLAYER_HEAD);
                SkullMeta meta = (SkullMeta) head.getItemMeta();
                meta.setOwningPlayer(org.bukkit.Bukkit.getOfflinePlayer(playerName));
                
                String medal = medals[i];
                meta.setDisplayName(MessageUtil.color(medal + " &b" + playerName));
                
                List<String> lore = new ArrayList<>();
                lore.add(MessageUtil.color("&7"));
                lore.add(MessageUtil.color("&7Level: &b" + entry.getLevel()));
                lore.add(MessageUtil.color("&7Total XP: &b" + MessageUtil.formatNumber(entry.getXp())));
                lore.add(MessageUtil.color("&7"));
                lore.add(MessageUtil.color("&7Heads Collected: &e" + MessageUtil.formatNumber(entry.getHeadsCollected())));
                lore.add(MessageUtil.color("&7Heads Sold: &a" + MessageUtil.formatNumber(entry.getHeadsSold())));
                lore.add(MessageUtil.color("&7Masks Crafted: &d" + entry.getMasksCrafted()));
                
                meta.setLore(lore);
                head.setItemMeta(meta);
                
                inventory.setItem(slots[i], head);
            }
        }
        
        // Your rank info
        ItemStack yourRank = createItem(Material.COMPASS, "&a&lYour Ranking");
        ItemMeta yourMeta = yourRank.getItemMeta();
        
        int rank = plugin.getLeaderboardManager().getPlayerRank(viewer.getUniqueId());
        PlayerData viewerData = plugin.getDataManager().getPlayerData(viewer);
        
        List<String> yourLore = new ArrayList<>();
        yourLore.add(MessageUtil.color("&7"));
        if (rank > 0) {
            yourLore.add(MessageUtil.color("&7Your Rank: &b#" + rank));
        } else {
            yourLore.add(MessageUtil.color("&7Your Rank: &7Unranked"));
        }
        yourLore.add(MessageUtil.color("&7Your Level: &b" + viewerData.getLevel()));
        yourLore.add(MessageUtil.color("&7Your XP: &b" + MessageUtil.formatNumber(viewerData.getXp())));
        yourMeta.setLore(yourLore);
        yourRank.setItemMeta(yourMeta);
        inventory.setItem(49, yourRank);
    }
    
    private ItemStack createItem(Material material, String name) {
        ItemStack item = new ItemStack(material, 1);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(MessageUtil.color(name));
        item.setItemMeta(meta);
        return item;
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
    }
}

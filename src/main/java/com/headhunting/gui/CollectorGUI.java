package com.headhunting.gui;

import com.headhunting.HeadHunting;
import com.headhunting.collector.CollectorData;
import com.headhunting.data.HeadConfig;
import com.headhunting.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * GUI that opens when a player right-clicks a Head Collector block.
 *
 * <p>Layout (54-slot, 6 rows):
 * <pre>
 *   Row 0 (slots 0-8):   decoration / info
 *   Rows 1-4 (9-44):     head type display icons  (up to 28 types)
 *   Row 5 (45-53):       WITHDRAW (45), SELL (46), filler
 * </pre>
 */
public class CollectorGUI implements InventoryHolder {

    private static final int GUI_SIZE = 54;
    private static final int WITHDRAW_SLOT = 45;
    private static final int SELL_SLOT = 46;
    private static final int INFO_SLOT = 4;

    // Content area: rows 1-4, columns 1-7  (avoid borders)
    private static final int[] CONTENT_SLOTS = buildContentSlots();

    private static int[] buildContentSlots() {
        List<Integer> slots = new ArrayList<>();
        for (int row = 1; row <= 4; row++) {
            for (int col = 1; col <= 7; col++) {
                slots.add(row * 9 + col);
            }
        }
        int[] arr = new int[slots.size()];
        for (int i = 0; i < slots.size(); i++) arr[i] = slots.get(i);
        return arr;
    }

    private final HeadHunting plugin;
    private final Player player;
    private final CollectorData data;
    private final Inventory inventory;

    /** slot → head key for content slots */
    private final Map<Integer, String> slotKeyMap = new HashMap<>();

    public CollectorGUI(HeadHunting plugin, Player player, CollectorData data) {
        this.plugin = plugin;
        this.player = player;
        this.data = data;
        this.inventory = Bukkit.createInventory(this, GUI_SIZE,
                MessageUtil.color("&6&l✦ Head Collector ✦"));
        build();
    }

    // ── Build / Refresh ───────────────────────────────────────────────────────

    private void build() {
        inventory.clear();
        slotKeyMap.clear();

        // Borders
        ItemStack border = glass(Material.BLACK_STAINED_GLASS_PANE, " ");
        ItemStack innerBorder = glass(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < GUI_SIZE; i++) {
            int row = i / 9, col = i % 9;
            if (row == 0 || row == 5 || col == 0 || col == 8) {
                inventory.setItem(i, border);
            } else {
                inventory.setItem(i, innerBorder);
            }
        }

        // Info item
        int total = data.getTotalHeads();
        ItemStack info = makeItem(Material.PAPER,
                "&b&lHead Collector Storage",
                "&7Total heads stored: &e" + total,
                "&7",
                "&7Use &aWithdraw &7to take heads back.",
                "&7Use &6Sell &7to sell all heads for coins.");
        inventory.setItem(INFO_SLOT, info);

        // Head type icons
        List<Map.Entry<String, Integer>> entries = new ArrayList<>(data.getHeads().entrySet());
        entries.sort(Map.Entry.<String, Integer>comparingByValue().reversed());

        for (int i = 0; i < Math.min(entries.size(), CONTENT_SLOTS.length); i++) {
            Map.Entry<String, Integer> entry = entries.get(i);
            String headKey = entry.getKey();
            int count = entry.getValue();

            HeadConfig hc = plugin.getConfigManager().getHeadConfig(headKey);
            String displayName = hc != null ? hc.getDisplayName() : headKey;
            double sellPrice = hc != null ? hc.getSellPrice() * count : 0;

            ItemStack head = plugin.getHeadFactory().createHead(headKey, Math.min(count, 64));
            if (head == null) {
                head = new ItemStack(Material.PLAYER_HEAD);
            }
            ItemMeta meta = head.getItemMeta();
            meta.setDisplayName(MessageUtil.color("&e" + displayName));
            List<String> lore = new ArrayList<>();
            lore.add(MessageUtil.color("&8━━━━━━━━━━━━━━━━━"));
            lore.add(MessageUtil.color("&7Stored: &a" + count));
            lore.add(MessageUtil.color("&7Sell value: &6$" + MessageUtil.formatNumber(sellPrice)));
            lore.add(MessageUtil.color("&8━━━━━━━━━━━━━━━━━"));
            meta.setLore(lore);
            head.setItemMeta(meta);

            int slot = CONTENT_SLOTS[i];
            inventory.setItem(slot, head);
            slotKeyMap.put(slot, headKey);
        }

        // Withdraw button
        ItemStack withdraw = makeItem(Material.CHEST,
                "&a&l↑ Withdraw All",
                "&7Takes all stored heads",
                "&7back into your inventory.");
        inventory.setItem(WITHDRAW_SLOT, withdraw);

        // Sell button
        double totalValue = calculateTotalSellValue();
        ItemStack sell = makeItem(Material.GOLD_INGOT,
                "&6&l$ Sell All",
                "&7Sell all stored heads for:",
                "&a$" + MessageUtil.formatNumber(totalValue),
                "&7",
                "&eClick to sell!");
        inventory.setItem(SELL_SLOT, sell);
    }

    // ── Click handling ────────────────────────────────────────────────────────

    public void handleClick(InventoryClickEvent event) {
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= GUI_SIZE) return;

        if (slot == WITHDRAW_SLOT) {
            doWithdraw();
        } else if (slot == SELL_SLOT) {
            doSell();
        }
        // Head type icons are display-only; no action on click
    }

    public void handleClose(InventoryCloseEvent event) {
        // Nothing special needed
    }

    public void open() {
        player.openInventory(inventory);
    }

    // ── Actions ───────────────────────────────────────────────────────────────

    private void doWithdraw() {
        if (data.getTotalHeads() == 0) {
            MessageUtil.send(player, "&cThe collector is empty!");
            return;
        }

        List<Map.Entry<String, Integer>> entries = new ArrayList<>(data.getHeads().entrySet());
        int totalGiven = 0;

        for (Map.Entry<String, Integer> entry : entries) {
            String headKey = entry.getKey();
            int remaining = entry.getValue();
            if (remaining <= 0) continue;

            while (remaining > 0) {
                int stack = Math.min(remaining, 64);
                ItemStack head = plugin.getHeadFactory().createHead(headKey, stack);
                if (head == null) break;

                Map<Integer, ItemStack> leftover = player.getInventory().addItem(head);
                int added = stack;
                for (ItemStack lo : leftover.values()) {
                    added -= lo.getAmount();
                    // Drop leftovers on the ground
                    player.getWorld().dropItemNaturally(player.getLocation(), lo);
                }
                data.removeHeads(headKey, added);
                totalGiven += added;
                remaining -= stack;
            }
        }

        plugin.getCollectorManager().save();
        MessageUtil.send(player, "&aWithdrew &e" + totalGiven + " &ahead(s) from the collector.");
        build();
    }

    private void doSell() {
        if (data.getTotalHeads() == 0) {
            MessageUtil.send(player, "&cThe collector is empty, nothing to sell!");
            return;
        }

        double totalEarned = 0;
        List<Map.Entry<String, Integer>> entries = new ArrayList<>(data.getHeads().entrySet());

        for (Map.Entry<String, Integer> entry : entries) {
            String headKey = entry.getKey();
            int count = entry.getValue();
            if (count <= 0) continue;

            HeadConfig hc = plugin.getConfigManager().getHeadConfig(headKey);
            if (hc == null) continue;

            double earned = hc.getSellPrice() * count;
            totalEarned += earned;
            data.removeHeads(headKey, count);
        }

        if (totalEarned > 0) {
            plugin.getEconomy().depositPlayer(player, totalEarned);
            plugin.getCollectorManager().save();
            MessageUtil.send(player, "&aSold all heads for &6$"
                    + MessageUtil.formatNumber(totalEarned) + "&a!");
        } else {
            MessageUtil.send(player, "&cNo heads with sell prices found.");
        }

        build();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private double calculateTotalSellValue() {
        double total = 0;
        for (Map.Entry<String, Integer> entry : data.getHeads().entrySet()) {
            HeadConfig hc = plugin.getConfigManager().getHeadConfig(entry.getKey());
            if (hc != null) total += hc.getSellPrice() * entry.getValue();
        }
        return total;
    }

    private ItemStack glass(Material mat, String name) {
        ItemStack item = new ItemStack(mat, 1);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(MessageUtil.color(name));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack makeItem(Material mat, String name, String... lorelines) {
        ItemStack item = new ItemStack(mat, 1);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(MessageUtil.color(name));
        List<String> lore = new ArrayList<>();
        for (String line : lorelines) lore.add(MessageUtil.color(line));
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}

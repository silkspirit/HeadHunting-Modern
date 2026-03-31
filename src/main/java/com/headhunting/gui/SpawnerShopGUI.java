package com.headhunting.gui;

import com.headhunting.HeadHunting;
import com.headhunting.data.PlayerData;
import com.headhunting.shop.SpawnerEntry;
import com.headhunting.utils.MessageUtil;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.text.NumberFormat;
import java.util.*;
import java.util.logging.Level;

/**
 * Spawner Shop GUI — 54-slot chest inventory.
 *
 * <p>Players can browse and purchase mob spawners. Purchases are gated by
 * their HeadHunting level and their Vault balance. Spawner delivery is
 * performed via {@code spawner-stacking give <player> <MOB_TYPE> <qty>}.
 *
 * <p>The bottom row (slots 45-53) contains quantity selector buttons:
 * x1, x16, x32, x64. The selected quantity glows and multiplies the price.
 *
 * <p>Anti-dupe uses a transaction lock ({@code PURCHASING} set) instead of
 * a flat cooldown — the player is locked for ~1 tick while the command runs.
 */
public class SpawnerShopGUI implements InventoryHolder {

    // ── Quantity selector config ──────────────────────────────────────────────

    /** Available purchase quantities and their display/material config. */
    private static final int[] QUANTITIES    = {1, 16, 32, 64};
    @SuppressWarnings("deprecation")
    private static final Material[] QTY_MATS = {
            Material.STONE_BUTTON,  // x1
            Material.IRON_INGOT,    // x16
            Material.GOLD_INGOT,    // x32
            Material.DIAMOND        // x64
    };
    /** Slots in the bottom row (54-slot GUI) assigned to each quantity button. */
    private static final int[] QTY_SLOTS     = {45, 47, 49, 51};

    // ── Static state ──────────────────────────────────────────────────────────

    /**
     * Transaction lock: players currently mid-purchase.
     * Replaces the old flat 3-second cooldown map.
     */
    private static final Set<UUID> PURCHASING = new HashSet<>();

    /** Per-player selected quantity (defaults to x1 = index 0). */
    private static final Map<UUID, Integer> SELECTED_QTY = new HashMap<>();

    private static final NumberFormat MONEY_FORMAT = NumberFormat.getInstance(Locale.US);

    // ── Instance fields ───────────────────────────────────────────────────────

    private final HeadHunting plugin;
    private final Player player;
    private final Inventory inventory;

    /** slot → SpawnerEntry (populated during build) */
    private final Map<Integer, SpawnerEntry> slotMap = new HashMap<>();

    // ── Constructor ───────────────────────────────────────────────────────────

    public SpawnerShopGUI(HeadHunting plugin, Player player) {
        this.plugin = plugin;
        this.player = player;

        // Ensure default selection is x1 when opening the shop
        SELECTED_QTY.put(player.getUniqueId(), 0);

        String title = MessageUtil.color(plugin.getSpawnerShopManager().getGuiTitle());
        int size = plugin.getSpawnerShopManager().getGuiSize();
        // Clamp to valid chest sizes (9, 18, 27, 36, 45, 54)
        if (size <= 0 || size > 54) size = 54;

        this.inventory = Bukkit.createInventory(this, size, title);
        build();
    }

    // ── Build ─────────────────────────────────────────────────────────────────

    @SuppressWarnings("deprecation")
    private void build() {
        inventory.clear();
        slotMap.clear();

        ItemStack filler = makeFiller();
        int invSize = inventory.getSize();

        // Fill everything with filler first
        for (int i = 0; i < invSize; i++) {
            inventory.setItem(i, filler);
        }

        PlayerData data = plugin.getDataManager().getPlayerData(player);
        int playerLevel = (data != null) ? data.getLevel() : 1;

        List<SpawnerEntry> entries = plugin.getSpawnerShopManager().getEntries();

        for (SpawnerEntry entry : entries) {
            int slot = entry.getSlot();
            if (slot < 0 || slot >= invSize) continue; // skip out-of-range slots

            boolean unlocked = playerLevel >= entry.getRequiredLevel()
                    || player.hasPermission("headhunting.spawnershop.bypass");

            ItemStack item = buildSpawnerItem(entry, playerLevel, unlocked);
            inventory.setItem(slot, item);
            slotMap.put(slot, entry);
        }

        // Place quantity selector buttons in bottom row (only meaningful for 54-slot GUI)
        if (invSize == 54) {
            int selectedIndex = getSelectedIndex(player);
            for (int i = 0; i < QTY_SLOTS.length; i++) {
                inventory.setItem(QTY_SLOTS[i], buildQuantityButton(i, i == selectedIndex));
            }
        }
    }

    /** Rebuild only the quantity buttons (e.g. after selection change). */
    private void refreshQuantityButtons() {
        if (inventory.getSize() != 54) return;
        int selectedIndex = getSelectedIndex(player);
        for (int i = 0; i < QTY_SLOTS.length; i++) {
            inventory.setItem(QTY_SLOTS[i], buildQuantityButton(i, i == selectedIndex));
        }
    }

    // ── Item builders ─────────────────────────────────────────────────────────

    /** Black stained-glass pane used as border/filler. */
    @SuppressWarnings("deprecation")
    private static ItemStack makeFiller() {
        ItemStack pane = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = pane.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            pane.setItemMeta(meta);
        }
        return pane;
    }

    @SuppressWarnings("deprecation")
    private ItemStack buildSpawnerItem(SpawnerEntry entry, int playerLevel, boolean unlocked) {
        ItemStack item = new ItemStack(Material.SPAWNER, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.setDisplayName(MessageUtil.color(entry.getDisplayName()));

        int selectedQty = QUANTITIES[getSelectedIndex(player)];
        double totalPrice = entry.getPrice() * selectedQty;

        List<String> lore = new ArrayList<>();
        lore.add(MessageUtil.color("&7"));
        lore.add(MessageUtil.color("&7Price: &6$" + MONEY_FORMAT.format(entry.getPrice())
                + " &7each"));
        if (selectedQty > 1) {
            lore.add(MessageUtil.color("&7Total (&f×" + selectedQty + "&7): &6$"
                    + MONEY_FORMAT.format(totalPrice)));
        }
        lore.add(MessageUtil.color("&7Required Level: &e" + entry.getRequiredLevel()));

        if (unlocked) {
            lore.add(MessageUtil.color("&a✔ Unlocked"));
            lore.add(MessageUtil.color("&7"));
            lore.add(MessageUtil.color("&eClick to purchase!"));
        } else {
            int levelsNeeded = entry.getRequiredLevel() - playerLevel;
            lore.add(MessageUtil.color("&c✘ Locked"));
            lore.add(MessageUtil.color("&7Reach level &e" + entry.getRequiredLevel()
                    + " &7to unlock &7(&c" + levelsNeeded + " more&7)"));
        }

        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Build a quantity selector button.
     *
     * @param index     index into {@link #QUANTITIES} / {@link #QTY_MATS}
     * @param selected  whether this button is currently selected (gets glow)
     */
    @SuppressWarnings("deprecation")
    private ItemStack buildQuantityButton(int index, boolean selected) {
        int qty = QUANTITIES[index];
        Material mat = QTY_MATS[index];
        ItemStack item = new ItemStack(mat, 1);

        // Add glow via fake enchantment if selected
        if (selected) {
            item.addUnsafeEnchantment(Enchantment.DURABILITY, 1);
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        String state = selected ? "&a" : "&7";
        meta.setDisplayName(MessageUtil.color(state + "Quantity: &f×" + qty));

        List<String> lore = new ArrayList<>();
        if (selected) {
            lore.add(MessageUtil.color("&a▶ Selected"));
        } else {
            lore.add(MessageUtil.color("&7Click to select ×" + qty));
        }
        meta.setLore(lore);

        // Hide enchantment text but keep glow shimmer
        if (selected) {
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }

        item.setItemMeta(meta);
        return item;
    }

    // ── Click Handler ─────────────────────────────────────────────────────────

    /**
     * Called by {@link com.headhunting.listeners.GUIListener} for every
     * inventory click while this GUI is open. The event has already been
     * cancelled before this method is called.
     */
    public void handleClick(InventoryClickEvent event) {
        // Safety: ignore clicks in the player's bottom inventory
        if (event.getClickedInventory() == null
                || !event.getClickedInventory().equals(inventory)) {
            return;
        }

        int slot = event.getSlot();
        Player clicker = (Player) event.getWhoClicked();

        // Check if a quantity button was clicked
        for (int i = 0; i < QTY_SLOTS.length; i++) {
            if (slot == QTY_SLOTS[i]) {
                selectQuantity(clicker, i);
                return;
            }
        }

        // Otherwise check spawner slots
        SpawnerEntry entry = slotMap.get(slot);
        if (entry == null) return; // filler or empty slot

        attemptPurchase(clicker, entry);
    }

    /**
     * Handle drag events — always cancel to prevent item manipulation.
     */
    public void handleDrag(InventoryDragEvent event) {
        event.setCancelled(true);
    }

    // ── Quantity Selection ────────────────────────────────────────────────────

    private void selectQuantity(Player p, int index) {
        SELECTED_QTY.put(p.getUniqueId(), index);
        // Refresh quantity buttons to update glow
        refreshQuantityButtons();
        // Also refresh spawner items so price totals update
        rebuild();
    }

    /** Rebuild spawner items in place (no full clear — preserves filler). */
    @SuppressWarnings("deprecation")
    private void rebuild() {
        PlayerData data = plugin.getDataManager().getPlayerData(player);
        int playerLevel = (data != null) ? data.getLevel() : 1;

        for (Map.Entry<Integer, SpawnerEntry> e : slotMap.entrySet()) {
            boolean unlocked = playerLevel >= e.getValue().getRequiredLevel()
                    || player.hasPermission("headhunting.spawnershop.bypass");
            inventory.setItem(e.getKey(), buildSpawnerItem(e.getValue(), playerLevel, unlocked));
        }
        refreshQuantityButtons();
    }

    private static int getSelectedIndex(Player p) {
        Integer idx = SELECTED_QTY.get(p.getUniqueId());
        return (idx != null && idx >= 0 && idx < QUANTITIES.length) ? idx : 0;
    }

    // ── Purchase Logic ────────────────────────────────────────────────────────

    private void attemptPurchase(Player buyer, SpawnerEntry entry) {
        UUID uuid = buyer.getUniqueId();

        // --- Transaction lock check (replaces flat cooldown) ---
        if (PURCHASING.contains(uuid)) {
            MessageUtil.send(buyer, "&cPlease wait — your previous purchase is still processing.");
            return;
        }

        // --- Level check (bypass with permission) ---
        boolean bypass = buyer.hasPermission("headhunting.spawnershop.bypass");
        if (!bypass) {
            PlayerData data = plugin.getDataManager().getPlayerData(buyer);
            int playerLevel = (data != null) ? data.getLevel() : 1;
            if (playerLevel < entry.getRequiredLevel()) {
                MessageUtil.send(buyer, "&cYou need &eHeadHunting Level " + entry.getRequiredLevel()
                        + " &cto buy this spawner. &7(Your level: &e" + playerLevel + "&7)");
                return;
            }
        }

        // --- Balance check ---
        Economy economy = plugin.getEconomy();
        if (economy == null) {
            MessageUtil.send(buyer, "&cEconomy is not available. Please contact an administrator.");
            return;
        }

        int qty = QUANTITIES[getSelectedIndex(buyer)];
        double totalPrice = entry.getPrice() * qty;

        if (!economy.has(buyer, totalPrice)) {
            double balance = economy.getBalance(buyer);
            double needed = totalPrice - balance;
            MessageUtil.send(buyer, "&cInsufficient funds! You need &6$"
                    + MONEY_FORMAT.format(needed) + " &cmore to afford &f×" + qty + " &c"
                    + MessageUtil.color(entry.getDisplayName()) + "&c.");
            return;
        }

        // --- Withdraw funds ---
        EconomyResponse response = economy.withdrawPlayer(buyer, totalPrice);
        if (response == null || !response.transactionSuccess()) {
            String errMsg = (response != null) ? response.errorMessage : "unknown error";
            MessageUtil.send(buyer, "&cTransaction failed: " + errMsg);
            plugin.getLogger().warning("[SpawnerShop] Economy withdrawal failed for "
                    + buyer.getName() + ": " + errMsg);
            return;
        }

        // --- Lock player for the duration of this transaction ---
        PURCHASING.add(uuid);

        // --- Close GUI before delivery ---
        buyer.closeInventory();

        // --- Reset quantity selection to x1 ---
        SELECTED_QTY.put(uuid, 0);

        // --- Deliver via spawner-stacking (console command, next tick) ---
        final String playerName = buyer.getName();
        final String mobType    = entry.getMobType();
        final String command    = "spawner-stacking give " + playerName + " " + mobType + " " + qty;
        final double price      = totalPrice;

        // Configurable lock duration (default 1 tick = 50 ms)
        long lockTicks = Math.max(1L, plugin.getSpawnerShopManager().getPurchaseCooldownTicks());

        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
            } catch (Exception ex) {
                plugin.getLogger().log(Level.SEVERE,
                        "[SpawnerShop] Failed to execute command: " + command, ex);
                // Refund the player if command execution fails
                economy.depositPlayer(buyer, price);
                if (buyer.isOnline()) {
                    MessageUtil.send(buyer, "&cAn error occurred delivering your spawner. You have been refunded.");
                }
                // Release lock even on failure
                Bukkit.getScheduler().runTaskLater(plugin,
                        () -> PURCHASING.remove(uuid), lockTicks);
                return;
            }

            // --- Confirmation message ---
            String qtyLabel = qty > 1 ? " &7(&f×" + qty + "&7)" : "";
            MessageUtil.send(buyer, "&aPurchase successful! &7You bought &f"
                    + MessageUtil.color(entry.getDisplayName()) + qtyLabel
                    + " &7for &6$" + MONEY_FORMAT.format(price) + "&7.");

            // --- Console log ---
            plugin.getLogger().info(String.format(
                    "[SpawnerShop] PURCHASE | Player: %s | Spawner: %s | Qty: %d | Price: $%.2f | Time: %s",
                    playerName, mobType, qty, price, new java.util.Date()));

            // --- Release lock after configured delay ---
            Bukkit.getScheduler().runTaskLater(plugin,
                    () -> PURCHASING.remove(uuid), lockTicks);
        });
    }

    // ── InventoryHolder ───────────────────────────────────────────────────────

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    // ── Static helpers ────────────────────────────────────────────────────────

    /**
     * Clean up state for a player (e.g. on logout).
     * Removes any in-progress purchase lock and quantity selection.
     */
    public static void removePlayerState(UUID uuid) {
        PURCHASING.remove(uuid);
        SELECTED_QTY.remove(uuid);
    }

    /** @deprecated Use {@link #removePlayerState(UUID)} instead. */
    @Deprecated
    public static void removeCooldown(UUID uuid) {
        removePlayerState(uuid);
    }
}

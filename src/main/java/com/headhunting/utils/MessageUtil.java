package com.headhunting.utils;

import com.headhunting.HeadHunting;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

/**
 * Utility class for message handling.
 * Uses Paper's Adventure API for action bar; legacy color codes (&) for chat.
 */
public class MessageUtil {

    private static HeadHunting plugin;
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    public static void init(HeadHunting pluginInstance) {
        plugin = pluginInstance;
    }

    public static void reload() {
        // Reload if needed
    }

    /**
     * Translate color codes in a string
     */
    public static String color(String message) {
        if (message == null) return "";
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    /**
     * Strip color codes from a string
     */
    public static String stripColor(String message) {
        if (message == null) return "";
        return ChatColor.stripColor(color(message));
    }

    /**
     * Send a prefixed message to a player
     */
    public static void send(Player player, String message) {
        if (plugin != null) {
            String prefix = plugin.getConfigManager().getConfig().getString("messages.prefix", "&8[&6HeadHunting&8] ");
            player.sendMessage(color(prefix + message));
        } else {
            player.sendMessage(color("&8[&6HeadHunting&8] " + message));
        }
    }

    /**
     * Send a raw message to a player (no prefix)
     */
    public static void sendRaw(Player player, String message) {
        player.sendMessage(color(message));
    }

    /**
     * Send multiple messages to a player
     */
    public static void sendMultiple(Player player, String... messages) {
        for (String message : messages) {
            sendRaw(player, message);
        }
    }

    /**
     * Format a number with commas
     */
    public static String formatNumber(long number) {
        return String.format("%,d", number);
    }

    /**
     * Format a number with commas (double)
     */
    public static String formatNumber(double number) {
        if (number == (long) number) {
            return String.format("%,d", (long) number);
        }
        return String.format("%,.2f", number);
    }

    /**
     * Format money with currency symbol
     */
    public static String formatMoney(double amount) {
        String symbol = "$";
        if (plugin != null) {
            symbol = plugin.getConfigManager().getCurrencySymbol();
        }
        return symbol + formatNumber(amount);
    }

    /**
     * Create a progress bar
     */
    public static String progressBar(double current, double max, int length, String filledChar, String emptyChar) {
        double percentage = Math.min(1.0, current / max);
        int filled = (int) (length * percentage);

        StringBuilder bar = new StringBuilder();
        bar.append("&a");
        for (int i = 0; i < filled; i++) {
            bar.append(filledChar);
        }
        bar.append("&7");
        for (int i = filled; i < length; i++) {
            bar.append(emptyChar);
        }

        return color(bar.toString());
    }

    /**
     * Default progress bar
     */
    public static String progressBar(double current, double max) {
        return progressBar(current, max, 20, "█", "░");
    }

    /**
     * Send an action bar message to a player using Paper's Adventure API.
     */
    public static void sendActionBar(Player player, String message) {
        player.sendActionBar(LEGACY.deserialize(message));
    }
}

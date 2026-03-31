package com.headhunting.commands;

import com.headhunting.HeadHunting;
import com.headhunting.gui.SpawnerShopGUI;
import com.headhunting.utils.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Handles /spawners and /spawnershop commands.
 */
public class SpawnerShopCommand implements CommandExecutor {

    private final HeadHunting plugin;

    public SpawnerShopCommand(HeadHunting plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(MessageUtil.color("&cThis command can only be used by players."));
            return true;
        }

        Player player = (Player) sender;

        if (!player.hasPermission("headhunting.spawnershop")) {
            MessageUtil.send(player, "&cYou do not have permission to use the Spawner Shop.");
            return true;
        }

        // Open the GUI
        SpawnerShopGUI gui = new SpawnerShopGUI(plugin, player);
        player.openInventory(gui.getInventory());
        return true;
    }
}

package com.headhunting.commands;

import com.headhunting.HeadHunting;
import com.headhunting.gui.MissionsGUI;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /missions command - open missions GUI
 */
public class MissionsCommand implements CommandExecutor {
    
    private final HeadHunting plugin;
    
    public MissionsCommand(HeadHunting plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }
        
        Player player = (Player) sender;
        new MissionsGUI(plugin, player).open();
        
        return true;
    }
}

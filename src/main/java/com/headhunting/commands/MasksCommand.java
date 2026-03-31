package com.headhunting.commands;

import com.headhunting.HeadHunting;
import com.headhunting.gui.MasksGUI;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /masks command - open mask collection GUI
 */
public class MasksCommand implements CommandExecutor {
    
    private final HeadHunting plugin;
    
    public MasksCommand(HeadHunting plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }
        
        Player player = (Player) sender;
        new MasksGUI(plugin, player).open();
        
        return true;
    }
}

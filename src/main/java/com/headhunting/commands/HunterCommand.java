package com.headhunting.commands;

import com.headhunting.HeadHunting;
import com.headhunting.gui.ProgressionGUI;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /hunter command - View progression roadmap
 */
public class HunterCommand implements CommandExecutor {
    
    private final HeadHunting plugin;
    
    public HunterCommand(HeadHunting plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }
        
        Player player = (Player) sender;
        Player target = player;
        
        // Check if viewing another player
        if (args.length > 0) {
            target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                player.sendMessage(com.headhunting.utils.MessageUtil.color("&cPlayer not found: " + args[0]));
                return true;
            }
        }
        
        new ProgressionGUI(plugin, player, target).open();
        return true;
    }
}

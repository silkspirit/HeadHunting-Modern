package com.headhunting.commands;

import com.headhunting.HeadHunting;
import com.headhunting.utils.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /hhbar command - toggle boss bar progress display
 */
public class BossBarCommand implements CommandExecutor {
    
    private final HeadHunting plugin;
    
    public BossBarCommand(HeadHunting plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(MessageUtil.color("&cThis command can only be used by players!"));
            return true;
        }
        
        Player player = (Player) sender;
        boolean enabled = plugin.getBossBarManager().toggle(player);
        
        if (enabled) {
            player.sendMessage(MessageUtil.color("&5&l⚔ HeadHunting &8» &dProgress bar &aenabled!"));
            player.sendMessage(MessageUtil.color("&8Your level progress will now display above your hotbar."));
        } else {
            player.sendMessage(MessageUtil.color("&5&l⚔ HeadHunting &8» &dProgress bar &cdisabled."));
        }
        
        return true;
    }
}

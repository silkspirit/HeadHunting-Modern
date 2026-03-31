package com.headhunting.commands;

import com.headhunting.HeadHunting;
import com.headhunting.utils.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * /bulksell and /bulkdeposit toggle commands
 */
public class BulkCommand implements CommandExecutor {
    
    private final HeadHunting plugin;
    private final Set<UUID> bulkSellEnabled = new HashSet<>();
    private final Set<UUID> bulkDepositEnabled = new HashSet<>();
    
    public BulkCommand(HeadHunting plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }
        
        Player player = (Player) sender;
        UUID uuid = player.getUniqueId();
        
        if (label.equalsIgnoreCase("bulksell")) {
            if (bulkSellEnabled.contains(uuid)) {
                bulkSellEnabled.remove(uuid);
                MessageUtil.send(player, "&c&lBulk Sell &7disabled. Punch chests normally.");
            } else {
                bulkSellEnabled.add(uuid);
                bulkDepositEnabled.remove(uuid); // Disable deposit if enabling sell
                MessageUtil.send(player, "&a&lBulk Sell &7enabled! Punch chests to sell all heads.");
            }
        } else if (label.equalsIgnoreCase("bulkdeposit")) {
            if (bulkDepositEnabled.contains(uuid)) {
                bulkDepositEnabled.remove(uuid);
                MessageUtil.send(player, "&c&lBulk Deposit &7disabled. Punch chests normally.");
            } else {
                bulkDepositEnabled.add(uuid);
                bulkSellEnabled.remove(uuid); // Disable sell if enabling deposit
                MessageUtil.send(player, "&b&lBulk Deposit &7enabled! Punch chests to deposit all heads for XP.");
            }
        }
        
        return true;
    }
    
    public boolean isBulkSellEnabled(Player player) {
        return bulkSellEnabled.contains(player.getUniqueId());
    }
    
    public boolean isBulkDepositEnabled(Player player) {
        return bulkDepositEnabled.contains(player.getUniqueId());
    }
}

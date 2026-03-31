package com.headhunting.commands;

import com.headhunting.HeadHunting;
import com.headhunting.collector.CollectorItem;
import com.headhunting.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * /headcollector give <player> [amount]
 *
 * <p>Requires: headhunting.collector.admin
 */
public class HeadCollectorCommand implements CommandExecutor, TabCompleter {

    private final HeadHunting plugin;

    public HeadCollectorCommand(HeadHunting plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("headhunting.collector.admin")) {
            MessageUtil.send(sender instanceof Player ? (Player) sender : null,
                    "&cYou don't have permission to use this command.");
            if (sender instanceof Player) {
                MessageUtil.send((Player) sender, "&cPermission required: headhunting.collector.admin");
            } else {
                sender.sendMessage("[HeadHunting] Permission required: headhunting.collector.admin");
            }
            return true;
        }

        if (args.length < 2 || !args[0].equalsIgnoreCase("give")) {
            sender.sendMessage(MessageUtil.color("&6Usage: &e/headcollector give <player> [amount]"));
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(MessageUtil.color("&cPlayer not found: &e" + args[1]));
            return true;
        }

        int amount = 1;
        if (args.length >= 3) {
            try {
                amount = Integer.parseInt(args[2]);
                if (amount < 1) amount = 1;
                if (amount > 64) amount = 64;
            } catch (NumberFormatException e) {
                sender.sendMessage(MessageUtil.color("&cInvalid amount: &e" + args[2]));
                return true;
            }
        }

        ItemStack collector = CollectorItem.create(amount);
        Map<Integer, ItemStack> leftover = target.getInventory().addItem(collector);
        if (!leftover.isEmpty()) {
            target.getWorld().dropItemNaturally(target.getLocation(), leftover.values().iterator().next());
        }

        sender.sendMessage(MessageUtil.color("&aGave &e" + amount + "x &6Head Collector(s) &ato &b" + target.getName()));
        MessageUtil.send(target, "&aYou received &e" + amount + "x &6Head Collector(s)&a!");

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("headhunting.collector.admin")) return Collections.emptyList();

        List<String> result = new ArrayList<>();
        if (args.length == 1) {
            result.add("give");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                result.add(p.getName());
            }
        }
        return result;
    }

    private void send(CommandSender sender, String msg) {
        sender.sendMessage(MessageUtil.color("&8[&6HeadHunting&8] " + msg));
    }
}

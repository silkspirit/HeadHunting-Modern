package com.headhunting.commands;

import com.headhunting.HeadHunting;
import com.headhunting.boosters.BoosterType;
import com.headhunting.data.MaskConfig;
import com.headhunting.data.MissionConfig;
import com.headhunting.data.MissionConfig.MissionRequirement;
import com.headhunting.data.PlayerData;
import com.headhunting.data.ServerAnalytics;
import com.headhunting.managers.SQLiteProvider;
import com.headhunting.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * /headhunting admin command with full management features
 */
public class AdminCommand implements CommandExecutor, TabCompleter {
    
    private final HeadHunting plugin;
    
    public AdminCommand(HeadHunting plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("headhunting.admin")) {
            sender.sendMessage(MessageUtil.color(plugin.getConfigManager().getMessage("no-permission")));
            return true;
        }
        
        if (args.length == 0) {
            showHelp(sender);
            return true;
        }
        
        switch (args[0].toLowerCase()) {
            case "reload":
                handleReload(sender);
                break;
            case "setlevel":
                handleSetLevel(sender, args);
                break;
            case "setxp":
                handleSetXp(sender, args);
                break;
            case "addxp":
                handleAddXp(sender, args);
                break;
            case "givemask":
                handleGiveMask(sender, args);
                break;
            case "givehead":
                handleGiveHead(sender, args);
                break;
            case "giveessence":
                handleGiveEssence(sender, args);
                break;
            case "mission":
                handleMission(sender, args);
                break;
            case "resetplayer":
                handleResetPlayer(sender, args);
                break;
            case "stats":
                handleStats(sender, args);
                break;
            case "shopconfig":
                handleShopConfig(sender);
                break;
            case "givemystery":
                handleGiveMystery(sender, args);
                break;
            case "givebosshead":
                handleGiveBossHead(sender, args);
                break;
            case "givebooster":
                handleGiveBooster(sender, args);
                break;
            case "boosters":
                handleBoosterStatus(sender, args);
                break;
            case "stacktest":
                handleStackTest(sender, args);
                break;
            case "analytics":
                handleAnalytics(sender);
                break;
            default:
                showHelp(sender);
                break;
        }
        
        return true;
    }
    
    private void showHelp(CommandSender sender) {
        sender.sendMessage(MessageUtil.color("&8&m                                              "));
        sender.sendMessage(MessageUtil.color("&b&lHeadHunting &7- Admin Commands"));
        sender.sendMessage(MessageUtil.color("&8&m                                              "));
        sender.sendMessage(MessageUtil.color("&b/hunt reload &7- Reload all configurations"));
        sender.sendMessage(MessageUtil.color("&b/hunt stats <player> &7- View player stats"));
        sender.sendMessage(MessageUtil.color("&8&m                                              "));
        sender.sendMessage(MessageUtil.color("&9Level Commands:"));
        sender.sendMessage(MessageUtil.color("&b/hunt setlevel <player> <level> &7- Set player level"));
        sender.sendMessage(MessageUtil.color("&b/hunt setxp <player> <xp> &7- Set player XP"));
        sender.sendMessage(MessageUtil.color("&b/hunt addxp <player> <xp> &7- Add XP to player"));
        sender.sendMessage(MessageUtil.color("&8&m                                              "));
        sender.sendMessage(MessageUtil.color("&9Item Commands:"));
        sender.sendMessage(MessageUtil.color("&b/hunt givemask <player> <mask> [level] &7- Give mask"));
        sender.sendMessage(MessageUtil.color("&b/hunt givehead <player> <mob> [amount] &7- Give head"));
        sender.sendMessage(MessageUtil.color("&b/hunt giveessence <player> [amount] &7- Give essence"));
        sender.sendMessage(MessageUtil.color("&8&m                                              "));
        sender.sendMessage(MessageUtil.color("&9Mission Commands:"));
        sender.sendMessage(MessageUtil.color("&b/hunt mission list &7- List all missions"));
        sender.sendMessage(MessageUtil.color("&b/hunt mission progress <player> <mission> &7- View progress"));
        sender.sendMessage(MessageUtil.color("&b/hunt mission complete <player> <mission> &7- Complete mission"));
        sender.sendMessage(MessageUtil.color("&b/hunt mission reset <player> <mission> &7- Reset mission"));
        sender.sendMessage(MessageUtil.color("&b/hunt mission setprogress <player> <mission> <req> <amount>"));
        sender.sendMessage(MessageUtil.color("&8&m                                              "));
        sender.sendMessage(MessageUtil.color("&9Data Commands:"));
        sender.sendMessage(MessageUtil.color("&b/hunt resetplayer <player> [confirm] &7- Reset all data"));
        sender.sendMessage(MessageUtil.color("&8&m                                              "));
        sender.sendMessage(MessageUtil.color("&9Integration Commands:"));
        sender.sendMessage(MessageUtil.color("&b/hunt shopconfig &7- Generate ShopGUIPlus config"));
        sender.sendMessage(MessageUtil.color("&8&m                                              "));
        sender.sendMessage(MessageUtil.color("&9Crate/Reward Items:"));
        sender.sendMessage(MessageUtil.color("&b/hunt givemystery <player> <rarity> &7- Give mystery mask"));
        sender.sendMessage(MessageUtil.color("&b/hunt givebosshead <player> <boss> [amount] &7- Give boss head"));
        sender.sendMessage(MessageUtil.color("&7Rarities: common, uncommon, rare, epic, legendary, divine"));
        sender.sendMessage(MessageUtil.color("&7Bosses: wither, dragon, elder_guardian"));
        sender.sendMessage(MessageUtil.color("&8&m                                              "));
        sender.sendMessage(MessageUtil.color("&9Analytics:"));
        sender.sendMessage(MessageUtil.color("&b/hunt analytics &7- Server-wide stats dashboard"));
        sender.sendMessage(MessageUtil.color("&8&m                                              "));
    }
    
    private void handleReload(CommandSender sender) {
        plugin.reload();
        sender.sendMessage(MessageUtil.color("&a&lHeadHunting configuration reloaded!"));
        sender.sendMessage(MessageUtil.color("&7Loaded &e" + plugin.getConfigManager().getHeadConfigs().size() + " &7heads"));
        sender.sendMessage(MessageUtil.color("&7Loaded &e" + plugin.getConfigManager().getMaskConfigs().size() + " &7masks"));
        sender.sendMessage(MessageUtil.color("&7Loaded &e" + plugin.getMissionManager().getAllMissions().size() + " &7missions"));
        sender.sendMessage(MessageUtil.color("&7Loaded &e" + plugin.getFishingManager().getRewardCount() + " &7fishing rewards"));
    }
    
    private void handleSetLevel(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(MessageUtil.color("&cUsage: /hh setlevel <player> <level>"));
            return;
        }
        
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(MessageUtil.color("&cPlayer not found: " + args[1]));
            return;
        }
        
        int level;
        try {
            level = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage(MessageUtil.color("&cInvalid level: " + args[2]));
            return;
        }
        
        int maxLevel = plugin.getConfigManager().getMaxLevel();
        if (level < 1 || level > maxLevel) {
            sender.sendMessage(MessageUtil.color("&cLevel must be between 1 and " + maxLevel));
            return;
        }
        
        plugin.getLevelManager().setLevel(target, level);
        sender.sendMessage(MessageUtil.color("&aSet &e" + target.getName() + "&a's level to &6" + level));
    }
    
    private void handleSetXp(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(MessageUtil.color("&cUsage: /hh setxp <player> <xp>"));
            return;
        }
        
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(MessageUtil.color("&cPlayer not found: " + args[1]));
            return;
        }
        
        int xp;
        try {
            xp = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage(MessageUtil.color("&cInvalid XP amount: " + args[2]));
            return;
        }
        
        if (xp < 0) {
            sender.sendMessage(MessageUtil.color("&cXP cannot be negative"));
            return;
        }
        
        PlayerData data = plugin.getDataManager().getPlayerData(target);
        data.setXp(xp);
        sender.sendMessage(MessageUtil.color("&aSet &e" + target.getName() + "&a's XP to &b" + xp));
    }
    
    private void handleAddXp(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(MessageUtil.color("&cUsage: /hh addxp <player> <xp>"));
            return;
        }
        
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(MessageUtil.color("&cPlayer not found: " + args[1]));
            return;
        }
        
        int xp;
        try {
            xp = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage(MessageUtil.color("&cInvalid XP amount: " + args[2]));
            return;
        }
        
        plugin.getLevelManager().addXp(target, xp);
        sender.sendMessage(MessageUtil.color("&aAdded &b" + xp + " XP &ato &e" + target.getName()));
    }
    
    private void handleGiveMask(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(MessageUtil.color("&cUsage: /hh givemask <player> <mask> [level]"));
            return;
        }
        
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(MessageUtil.color("&cPlayer not found: " + args[1]));
            return;
        }
        
        String maskId = args[2].toLowerCase();
        if (plugin.getConfigManager().getMaskConfig(maskId) == null) {
            sender.sendMessage(MessageUtil.color("&cInvalid mask: " + maskId));
            sender.sendMessage(MessageUtil.color("&7Available: " + String.join(", ", plugin.getConfigManager().getMaskConfigs().keySet())));
            return;
        }
        
        int level = 1;
        if (args.length >= 4) {
            try {
                level = Math.max(1, Math.min(5, Integer.parseInt(args[3])));
            } catch (NumberFormatException ignored) {}
        }
        
        ItemStack mask = plugin.getMaskFactory().createMask(maskId, level);
        if (mask != null) {
            target.getInventory().addItem(mask);
            
            // Also update PlayerData so the mask level is tracked
            PlayerData data = plugin.getDataManager().getPlayerData(target);
            data.setMaskOwned(maskId, true);
            data.setMaskLevel(maskId, level);
            
            // Get display name
            MaskConfig config = plugin.getConfigManager().getMaskConfig(maskId);
            String displayName = config != null ? config.getDisplayName() : maskId;
            sender.sendMessage(MessageUtil.color("&aGave &e" + target.getName() + " &aa level &6" + level + " &a" + displayName));
        }
    }
    
    private void handleGiveHead(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(MessageUtil.color("&cUsage: /hh givehead <player> <mob> [amount]"));
            return;
        }
        
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(MessageUtil.color("&cPlayer not found: " + args[1]));
            return;
        }
        
        // Support both EntityType names and sub-type keys (e.g. WITHER_SKELETON)
        String headKey = args[2].toUpperCase();
        if (plugin.getConfigManager().getHeadConfig(headKey) == null) {
            sender.sendMessage(MessageUtil.color("&cNo head configured for: " + headKey));
            return;
        }
        
        int amount = 1;
        if (args.length >= 4) {
            try {
                amount = Math.max(1, Math.min(64, Integer.parseInt(args[3])));
            } catch (NumberFormatException ignored) {}
        }
        
        ItemStack head = plugin.getHeadFactory().createHead(headKey, amount);
        if (head != null) {
            target.getInventory().addItem(head);
            sender.sendMessage(MessageUtil.color("&aGave &e" + target.getName() + " &6" + amount + "x &a" + headKey + " head"));
        }
    }
    
    private void handleGiveEssence(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(MessageUtil.color("&cUsage: /hh giveessence <player> [amount]"));
            return;
        }
        
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(MessageUtil.color("&cPlayer not found: " + args[1]));
            return;
        }
        
        int amount = 1;
        if (args.length >= 3) {
            try {
                amount = Math.max(1, Math.min(64, Integer.parseInt(args[2])));
            } catch (NumberFormatException ignored) {}
        }
        
        ItemStack essence = plugin.getMaskFactory().createSpiritEssence(amount);
        target.getInventory().addItem(essence);
        sender.sendMessage(MessageUtil.color("&aGave &e" + target.getName() + " &6" + amount + "x &d✦ Spirit Essence ✦"));
    }
    
    private void handleMission(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(MessageUtil.color("&cUsage: /hh mission <list|progress|complete|reset|setprogress> ..."));
            return;
        }
        
        switch (args[1].toLowerCase()) {
            case "list":
                handleMissionList(sender);
                break;
            case "progress":
                handleMissionProgress(sender, args);
                break;
            case "complete":
                handleMissionComplete(sender, args);
                break;
            case "reset":
                handleMissionReset(sender, args);
                break;
            case "setprogress":
                handleMissionSetProgress(sender, args);
                break;
            default:
                sender.sendMessage(MessageUtil.color("&cUnknown mission subcommand: " + args[1]));
                break;
        }
    }
    
    private void handleMissionList(CommandSender sender) {
        sender.sendMessage(MessageUtil.color("&6&lDivine Missions:"));
        for (MissionConfig mission : plugin.getMissionManager().getAllMissions().values()) {
            sender.sendMessage(MessageUtil.color("&7- &e" + mission.getId() + " &7→ Unlocks: &d" + mission.getUnlocksMask()));
        }
    }
    
    private void handleMissionProgress(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(MessageUtil.color("&cUsage: /hh mission progress <player> <mission>"));
            return;
        }
        
        Player target = Bukkit.getPlayer(args[2]);
        if (target == null) {
            sender.sendMessage(MessageUtil.color("&cPlayer not found: " + args[2]));
            return;
        }
        
        MissionConfig mission = plugin.getMissionManager().getMission(args[3]);
        if (mission == null) {
            sender.sendMessage(MessageUtil.color("&cMission not found: " + args[3]));
            return;
        }
        
        PlayerData data = plugin.getDataManager().getPlayerData(target);
        boolean completed = data.getMissionProgress(mission.getId()).isCompleted();
        
        sender.sendMessage(MessageUtil.color("&6Mission: &e" + mission.getDisplayName()));
        sender.sendMessage(MessageUtil.color("&7Player: &f" + target.getName()));
        sender.sendMessage(MessageUtil.color("&7Status: " + (completed ? "&a&lCOMPLETE" : "&eIn Progress")));
        sender.sendMessage(MessageUtil.color("&7Requirements:"));
        
        for (MissionRequirement req : mission.getRequirements()) {
            int progress = plugin.getMissionManager().getPlayerProgress(target, mission, req);
            int required = req.getAmount();
            String status = progress >= required ? "&a✓" : "&c✗";
            sender.sendMessage(MessageUtil.color("  " + status + " &7" + req.getId() + ": &e" + progress + "/" + required));
        }
    }
    
    private void handleMissionComplete(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(MessageUtil.color("&cUsage: /hh mission complete <player> <mission>"));
            return;
        }
        
        Player target = Bukkit.getPlayer(args[2]);
        if (target == null) {
            sender.sendMessage(MessageUtil.color("&cPlayer not found: " + args[2]));
            return;
        }
        
        MissionConfig mission = plugin.getMissionManager().getMission(args[3]);
        if (mission == null) {
            sender.sendMessage(MessageUtil.color("&cMission not found: " + args[3]));
            return;
        }
        
        plugin.getMissionManager().completeMission(target, mission);
        sender.sendMessage(MessageUtil.color("&aCompleted mission &e" + mission.getId() + " &afor &e" + target.getName()));
    }
    
    private void handleMissionReset(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(MessageUtil.color("&cUsage: /hh mission reset <player> <mission>"));
            return;
        }
        
        Player target = Bukkit.getPlayer(args[2]);
        if (target == null) {
            sender.sendMessage(MessageUtil.color("&cPlayer not found: " + args[2]));
            return;
        }
        
        MissionConfig mission = plugin.getMissionManager().getMission(args[3]);
        if (mission == null) {
            sender.sendMessage(MessageUtil.color("&cMission not found: " + args[3]));
            return;
        }
        
        PlayerData data = plugin.getDataManager().getPlayerData(target);
        
        // Reset mission completion status
        data.getMissionProgress(mission.getId()).setCompleted(false);
        data.getMissionProgress(mission.getId()).setProgress(0);
        
        // Reset all requirement progress
        for (MissionRequirement req : mission.getRequirements()) {
            String progressKey = mission.getId() + ":" + req.getId();
            data.getMissionProgress(progressKey).setProgress(0);
        }
        
        sender.sendMessage(MessageUtil.color("&aReset mission &e" + mission.getId() + " &afor &e" + target.getName()));
    }
    
    private void handleMissionSetProgress(CommandSender sender, String[] args) {
        if (args.length < 6) {
            sender.sendMessage(MessageUtil.color("&cUsage: /hh mission setprogress <player> <mission> <requirement> <amount>"));
            return;
        }
        
        Player target = Bukkit.getPlayer(args[2]);
        if (target == null) {
            sender.sendMessage(MessageUtil.color("&cPlayer not found: " + args[2]));
            return;
        }
        
        MissionConfig mission = plugin.getMissionManager().getMission(args[3]);
        if (mission == null) {
            sender.sendMessage(MessageUtil.color("&cMission not found: " + args[3]));
            return;
        }
        
        String reqId = args[4];
        MissionRequirement req = null;
        for (MissionRequirement r : mission.getRequirements()) {
            if (r.getId().equalsIgnoreCase(reqId)) {
                req = r;
                break;
            }
        }
        
        if (req == null) {
            sender.sendMessage(MessageUtil.color("&cRequirement not found: " + reqId));
            sender.sendMessage(MessageUtil.color("&7Available: " + mission.getRequirements().stream()
                .map(MissionRequirement::getId).collect(Collectors.joining(", "))));
            return;
        }
        
        int amount;
        try {
            amount = Integer.parseInt(args[5]);
        } catch (NumberFormatException e) {
            sender.sendMessage(MessageUtil.color("&cInvalid amount: " + args[5]));
            return;
        }
        
        PlayerData data = plugin.getDataManager().getPlayerData(target);
        String progressKey = mission.getId() + ":" + req.getId();
        plugin.getMissionManager().setRequirementProgress(data, progressKey, amount);
        
        sender.sendMessage(MessageUtil.color("&aSet &e" + target.getName() + "&a's progress on &6" + reqId + " &ato &b" + amount));
    }
    
    private void handleResetPlayer(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(MessageUtil.color("&cUsage: /hh resetplayer <player> [confirm]"));
            return;
        }
        
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(MessageUtil.color("&cPlayer not found: " + args[1]));
            return;
        }
        
        if (args.length < 3 || !args[2].equalsIgnoreCase("confirm")) {
            sender.sendMessage(MessageUtil.color("&c&lWARNING: &cThis will reset ALL data for " + target.getName() + "!"));
            sender.sendMessage(MessageUtil.color("&cType &e/hh resetplayer " + target.getName() + " confirm &cto proceed."));
            return;
        }
        
        // Create fresh player data
        PlayerData freshData = new PlayerData(target.getUniqueId());
        plugin.getDataManager().getPlayerData(target); // Ensure cache exists
        
        // Reset by setting fresh values
        PlayerData data = plugin.getDataManager().getPlayerData(target);
        data.setLevel(1);
        data.setXp(0);
        data.setEquippedMask(null);
        data.getMasks().clear();
        data.getMissions().clear();
        
        // Remove mask effects
        plugin.getMaskManager().removePassiveAbilities(target);
        
        sender.sendMessage(MessageUtil.color("&aReset all HeadHunting data for &e" + target.getName()));
    }
    
    private void handleStats(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(MessageUtil.color("&cUsage: /hh stats <player>"));
            return;
        }
        
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(MessageUtil.color("&cPlayer not found: " + args[1]));
            return;
        }
        
        PlayerData data = plugin.getDataManager().getPlayerData(target);
        
        sender.sendMessage(MessageUtil.color("&8&m                                              "));
        sender.sendMessage(MessageUtil.color("&6&lHeadHunting Stats: &e" + target.getName()));
        sender.sendMessage(MessageUtil.color("&8&m                                              "));
        sender.sendMessage(MessageUtil.color("&7Level: &6" + data.getLevel()));
        sender.sendMessage(MessageUtil.color("&7XP: &b" + data.getXp()));
        sender.sendMessage(MessageUtil.color("&7Equipped Mask: &d" + (data.getEquippedMask() != null ? data.getEquippedMask() : "None")));
        
        long ownedMasks = data.getMasks().values().stream()
            .filter(PlayerData.MaskData::isOwned).count();
        sender.sendMessage(MessageUtil.color("&7Masks Owned: &a" + ownedMasks + "/" + plugin.getConfigManager().getMaskConfigs().size()));
        
        long completedMissions = data.getMissions().values().stream()
            .filter(PlayerData.MissionProgress::isCompleted).count();
        sender.sendMessage(MessageUtil.color("&7Missions Complete: &a" + completedMissions + "/" + plugin.getMissionManager().getAllMissions().size()));
        sender.sendMessage(MessageUtil.color("&8&m                                              "));
    }
    
    private void handleShopConfig(CommandSender sender) {
        if (plugin.getShopGUIPlusHook() == null) {
            sender.sendMessage(MessageUtil.color("&cShopGUIPlus hook not initialized."));
            return;
        }
        
        String config = plugin.getShopGUIPlusHook().generateShopConfig();
        
        // Save to file
        java.io.File outputFile = new java.io.File(plugin.getDataFolder(), "shopgui-heads.yml");
        try (java.io.FileWriter writer = new java.io.FileWriter(outputFile)) {
            writer.write(config);
            sender.sendMessage(MessageUtil.color("&aShopGUIPlus config generated!"));
            sender.sendMessage(MessageUtil.color("&7File: &e" + outputFile.getPath()));
            sender.sendMessage(MessageUtil.color("&7Copy this to your ShopGUIPlus &eshops/ &7folder."));
        } catch (java.io.IOException e) {
            sender.sendMessage(MessageUtil.color("&cFailed to write config: " + e.getMessage()));
        }
    }
    
    private void handleGiveMystery(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(MessageUtil.color("&cUsage: /hunt givemystery <player> <rarity>"));
            sender.sendMessage(MessageUtil.color("&7Rarities: common, uncommon, rare, epic, legendary, divine"));
            return;
        }
        
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(MessageUtil.color("&cPlayer not found: " + args[1]));
            return;
        }
        
        String rarity = args[2].toLowerCase();
        org.bukkit.inventory.ItemStack mysteryMask = plugin.getMysteryMaskFactory().createMysteryMask(rarity);
        
        if (mysteryMask == null) {
            sender.sendMessage(MessageUtil.color("&cInvalid rarity: " + rarity));
            sender.sendMessage(MessageUtil.color("&7Valid: common, uncommon, rare, epic, legendary, divine"));
            return;
        }
        
        target.getInventory().addItem(mysteryMask);
        sender.sendMessage(MessageUtil.color("&aGave " + rarity + " mystery mask to " + target.getName()));
        MessageUtil.send(target, "&d&l✦ &7You received a &d" + rarity + " Mystery Mask&7!");
    }
    
    private void handleGiveBossHead(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(MessageUtil.color("&cUsage: /hunt givebosshead <player> <boss> [amount]"));
            sender.sendMessage(MessageUtil.color("&7Bosses: wither, dragon, elder_guardian"));
            return;
        }
        
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(MessageUtil.color("&cPlayer not found: " + args[1]));
            return;
        }
        
        String bossType = args[2].toLowerCase();
        int amount = args.length > 3 ? parseInt(args[3], 1) : 1;
        
        org.bukkit.inventory.ItemStack bossHead = createBossHead(bossType);
        if (bossHead == null) {
            sender.sendMessage(MessageUtil.color("&cInvalid boss: " + bossType));
            sender.sendMessage(MessageUtil.color("&7Valid: wither, dragon, elder_guardian"));
            return;
        }
        
        bossHead.setAmount(amount);
        target.getInventory().addItem(bossHead);
        sender.sendMessage(MessageUtil.color("&aGave " + amount + "x " + bossType + " boss head to " + target.getName()));
        MessageUtil.send(target, "&6&l✦ &7You received a &6" + bossType + " Boss Head&7!");
    }
    
    private org.bukkit.inventory.ItemStack createBossHead(String bossType) {
        org.bukkit.inventory.ItemStack head = new org.bukkit.inventory.ItemStack(
            org.bukkit.Material.PLAYER_HEAD, 1);
        org.bukkit.inventory.meta.SkullMeta meta = 
            (org.bukkit.inventory.meta.SkullMeta) head.getItemMeta();
        
        String texture;
        String displayName;
        int xpValue;
        int sellValue;
        
        switch (bossType) {
            case "wither":
                // Same texture as Wither Mask
                texture = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNWJlY2RmOTc0N2JjZDcxZTBkZmU3N2E1NmMyMGRjOTk1ODNmMjQ1ZTQ2OWM4M2Y4NGJkMTk1ZWM2ZTJkM2VlMiJ9fX0=";
                displayName = "&8&lWither Boss Head";
                xpValue = 1000;
                sellValue = 50000;
                break;
            case "dragon":
            case "ender_dragon":
                // Same texture as Ender Dragon Mask
                texture = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZjhhYTNjNTNlNDY3NTIzYzZjM2Q4MzNiYmJlZTM3YTY2ZmMxNGYzMDUzMGYzOWE2YTljMDQ1N2ZmZTgwNWMyNSJ9fX0=";
                displayName = "&5&lEnder Dragon Boss Head";
                xpValue = 2000;
                sellValue = 100000;
                break;
            case "elder_guardian":
                // Same texture as Elder Guardian Mask
                texture = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOGRmNGZlYzNhYTM0Y2VkZGIwMjVhNDJjOWZjNDM1YTgwMjliMDYyNTk4YjMyNTMyMmJhM2NiNWU1ZjM1MWMxZiJ9fX0=";
                displayName = "&3&lElder Guardian Boss Head";
                xpValue = 500;
                sellValue = 25000;
                break;
            default:
                return null;
        }
        
        // Apply texture with consistent UUID based on boss type
        try {
            java.lang.reflect.Field profileField = meta.getClass().getDeclaredField("profile");
            profileField.setAccessible(true);
            // Use deterministic UUID based on boss type for proper texture loading
            java.util.UUID bossUUID = java.util.UUID.nameUUIDFromBytes(("HeadHunting_Boss_" + bossType).getBytes());
            com.mojang.authlib.GameProfile profile = new com.mojang.authlib.GameProfile(
                bossUUID, "HHBoss");
            profile.getProperties().put("textures",
                new com.mojang.authlib.properties.Property("textures", texture));
            profileField.set(meta, profile);
        } catch (Exception e) {
            // Ignore
        }
        
        meta.setDisplayName(MessageUtil.color(displayName));
        
        java.util.List<String> lore = new java.util.ArrayList<>();
        lore.add(MessageUtil.color("&7"));
        lore.add(MessageUtil.color("&7A trophy from a mighty boss!"));
        lore.add(MessageUtil.color("&7"));
        lore.add(MessageUtil.color("&7XP Value: &b" + xpValue));
        lore.add(MessageUtil.color("&7Sell Value: &a$" + MessageUtil.formatNumber(sellValue)));
        lore.add(MessageUtil.color("&7"));
        lore.add(MessageUtil.color("&eRight-click to consume"));
        lore.add(MessageUtil.color("&eShift-click to sell"));
        lore.add(MessageUtil.color("&7"));
        lore.add(MessageUtil.color("&8Boss Head"));
        meta.setLore(lore);
        
        head.setItemMeta(meta);
        return head;
    }
    
    /**
     * Handle givebooster command
     * /hunt givebooster <player> <xp|sell|fishing> <personal|faction> <multiplier> <duration_minutes>
     */
    private void handleGiveBooster(CommandSender sender, String[] args) {
        if (args.length < 6) {
            sender.sendMessage(MessageUtil.color("&cUsage: /hunt givebooster <player> <xp|sell|fishing> <personal|faction> <multiplier> <duration_minutes>"));
            sender.sendMessage(MessageUtil.color("&7Example: /hunt givebooster Steve xp faction 2.0 10"));
            sender.sendMessage(MessageUtil.color(""));
            sender.sendMessage(MessageUtil.color("&7Available boosters:"));
            sender.sendMessage(MessageUtil.color("&b  XP Boosters: &f1.5x, 1.75x, 2.0x, 2.5x, 3.0x"));
            sender.sendMessage(MessageUtil.color("&a  Sell Boosters: &f1.5x, 1.75x, 2.0x, 2.5x, 3.0x"));
            sender.sendMessage(MessageUtil.color("&3  Fishing Boosters: &f1.5x, 1.75x, 2.0x, 2.5x, 3.0x"));
            sender.sendMessage(MessageUtil.color("&7Durations: &f5, 10, 15 minutes"));
            return;
        }
        
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(MessageUtil.color("&cPlayer not found: " + args[1]));
            return;
        }
        
        // Parse type
        BoosterType type = BoosterType.fromString(args[2]);
        if (type == null) {
            sender.sendMessage(MessageUtil.color("&cInvalid booster type! Use: xp, sell, fishing"));
            return;
        }
        
        // Parse scope
        String scope = args[3].toLowerCase();
        if (!scope.equals("personal") && !scope.equals("faction")) {
            sender.sendMessage(MessageUtil.color("&cInvalid scope! Use: personal, faction"));
            return;
        }
        
        // Parse multiplier
        double multiplier;
        try {
            multiplier = Double.parseDouble(args[4]);
            if (multiplier < 1.0 || multiplier > 10.0) {
                sender.sendMessage(MessageUtil.color("&cMultiplier must be between 1.0 and 10.0"));
                return;
            }
        } catch (NumberFormatException e) {
            sender.sendMessage(MessageUtil.color("&cInvalid multiplier: " + args[4]));
            return;
        }
        
        // Parse duration
        int durationMinutes;
        try {
            durationMinutes = Integer.parseInt(args[5]);
            if (durationMinutes < 1 || durationMinutes > 120) {
                sender.sendMessage(MessageUtil.color("&cDuration must be between 1 and 120 minutes"));
                return;
            }
        } catch (NumberFormatException e) {
            sender.sendMessage(MessageUtil.color("&cInvalid duration: " + args[5]));
            return;
        }
        
        // Create booster ID
        String boosterId = type.name().toLowerCase() + "_" + scope + "_" + multiplier + "x_" + durationMinutes + "m";
        
        // Create and give the booster item
        ItemStack boosterItem = plugin.getBoosterManager().createBoosterItem(
            boosterId, type, scope, multiplier, durationMinutes);
        
        target.getInventory().addItem(boosterItem);
        
        String typeName = type.getDisplayName();
        String scopeName = scope.equals("faction") ? "Faction" : "Personal";
        
        sender.sendMessage(MessageUtil.color("&aGave " + target.getName() + " a " + scopeName + " " + 
            typeName + " Booster (" + multiplier + "x, " + durationMinutes + " min)"));
        target.sendMessage(MessageUtil.color("&aYou received a &e" + scopeName + " " + typeName + 
            " Booster &a(" + multiplier + "x, " + durationMinutes + " min)!"));
    }
    
    /**
     * Handle booster status command
     * /hunt boosters [player]
     */
    /**
     * /headhunting stacktest [maskid] — Creates 2 masks and compares their NMS NBT.
     * Gives both to the player and logs whether they'd stack.
     */
    private void handleStackTest(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cMust be run by a player.");
            return;
        }
        
        Player player = (Player) sender;
        String maskId = args.length >= 2 ? args[1].toLowerCase() : null;
        
        // Pick first available mask if none specified
        if (maskId == null) {
            if (!plugin.getConfigManager().getMaskConfigs().isEmpty()) {
                maskId = plugin.getConfigManager().getMaskConfigs().keySet().iterator().next();
            } else {
                sender.sendMessage("§cNo masks configured!");
                return;
            }
        }
        
        sender.sendMessage("§e=== STACK TEST: " + maskId + " ===");
        sender.sendMessage("§7Creating two identical masks...");
        
        // Create two masks of the same type
        ItemStack maskA = plugin.getMaskFactory().createMask(maskId, 1);
        ItemStack maskB = plugin.getMaskFactory().createMask(maskId, 1);
        
        if (maskA == null || maskB == null) {
            sender.sendMessage("§cFailed to create masks! Unknown ID: " + maskId);
            return;
        }
        
        sender.sendMessage("§7Mask A class: §f" + maskA.getClass().getSimpleName());
        sender.sendMessage("§7Mask B class: §f" + maskB.getClass().getSimpleName());
        
        // Dump NBT
        String nbtA = com.headhunting.utils.NBTUtil.dumpNBT(maskA);
        String nbtB = com.headhunting.utils.NBTUtil.dumpNBT(maskB);
        
        sender.sendMessage("§7A NBT: §f" + (nbtA.length() > 100 ? nbtA.substring(0, 100) + "..." : nbtA));
        sender.sendMessage("§7B NBT: §f" + (nbtB.length() > 100 ? nbtB.substring(0, 100) + "..." : nbtB));
        
        // Full comparison
        String comparison = com.headhunting.utils.NBTUtil.compareNBT(maskA, maskB);
        
        // Split comparison into multiple messages if needed
        for (int i = 0; i < comparison.length(); i += 100) {
            String chunk = comparison.substring(i, Math.min(i + 100, comparison.length()));
            sender.sendMessage("§7" + chunk);
        }
        
        // Bukkit equality checks
        boolean equals = maskA.equals(maskB);
        boolean similar = maskA.isSimilar(maskB);
        
        sender.sendMessage("§7Bukkit equals: " + (equals ? "§cTRUE (BAD!)" : "§aFALSE (good)"));
        sender.sendMessage("§7Bukkit isSimilar: " + (similar ? "§cTRUE (BAD! items will stack!)" : "§aFALSE (good)"));
        
        // Give both to player
        player.getInventory().addItem(maskA);
        player.getInventory().addItem(maskB);
        sender.sendMessage("§aGave you both masks. Check if they stacked in inventory!");
        
        // Also log to console
        plugin.getLogger().info("[STACKTEST] " + comparison);
        plugin.getLogger().info("[STACKTEST] A.NBT=" + nbtA);
        plugin.getLogger().info("[STACKTEST] B.NBT=" + nbtB);
        plugin.getLogger().info("[STACKTEST] equals=" + equals + " isSimilar=" + similar);
    }
    
    private void handleAnalytics(CommandSender sender) {
        SQLiteProvider sqlite = plugin.getDataManager().getSqliteProvider();
        if (sqlite == null) {
            sender.sendMessage(MessageUtil.color("&c&lAnalytics requires SQLite mode!"));
            sender.sendMessage(MessageUtil.color("&7Set &estorage-type: SQLITE &7in config.yml and restart."));
            return;
        }

        sender.sendMessage(MessageUtil.color("&7Loading server analytics..."));

        // Run queries async to avoid blocking the main thread
        new BukkitRunnable() {
            @Override
            public void run() {
                final ServerAnalytics analytics = sqlite.loadServerAnalytics();

                // Send results back on the main thread
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        displayAnalytics(sender, analytics);
                    }
                }.runTask(plugin);
            }
        }.runTaskAsynchronously(plugin);
    }

    private void displayAnalytics(CommandSender sender, ServerAnalytics a) {
        String line = "&8━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━";
        String thinLine = "&8&m                                              ";

        sender.sendMessage(MessageUtil.color(line));
        sender.sendMessage(MessageUtil.color(" &b&lHeadHunting Server Analytics"));
        sender.sendMessage(MessageUtil.color(line));

        // Player overview
        sender.sendMessage(MessageUtil.color(" &fPlayers: &e" + MessageUtil.formatNumber(a.getTotalPlayers())));
        sender.sendMessage(MessageUtil.color(" &fAverage Level: &e" + String.format("%.1f", a.getAverageLevel())));
        sender.sendMessage(MessageUtil.color(" &fTotal XP: &b" + MessageUtil.formatNumber(a.getTotalXp())));
        sender.sendMessage("");

        // Level distribution
        sender.sendMessage(MessageUtil.color(" &9Level Distribution:"));
        Map<Integer, Integer> dist = a.getLevelDistribution();
        int maxCount = 0;
        for (int count : dist.values()) {
            if (count > maxCount) maxCount = count;
        }

        int totalPlayers = a.getTotalPlayers();
        for (int level = 1; level <= 14; level++) {
            int count = dist.getOrDefault(level, 0);
            if (count == 0) continue; // Skip empty levels

            double pct = totalPlayers > 0 ? (count * 100.0 / totalPlayers) : 0;
            int barLen = maxCount > 0 ? (int) Math.ceil((count * 20.0) / maxCount) : 0;

            // Color coding: green for high, yellow for mid, gray for low
            String barColor;
            if (pct >= 30) {
                barColor = "&a";
            } else if (pct >= 10) {
                barColor = "&e";
            } else {
                barColor = "&7";
            }

            StringBuilder bar = new StringBuilder();
            for (int i = 0; i < barLen; i++) bar.append("█");

            String lvLabel = level < 10 ? " Lv." + level : "Lv." + level;
            sender.sendMessage(MessageUtil.color("  &f" + lvLabel + ": " + barColor + bar.toString() +
                " &f" + count + " &7(" + String.format("%.1f", pct) + "%)"));
        }

        sender.sendMessage("");
        sender.sendMessage(MessageUtil.color(thinLine));

        // Economy
        sender.sendMessage(MessageUtil.color(" &6Economy:"));
        sender.sendMessage(MessageUtil.color("  &fTotal Heads Collected: &a" + MessageUtil.formatNumber(a.getTotalHeadsCollected())));
        sender.sendMessage(MessageUtil.color("  &fTotal Heads Sold: &a" + MessageUtil.formatNumber(a.getTotalHeadsSold())));
        sender.sendMessage(MessageUtil.color("  &fTotal Masks Crafted: &d" + MessageUtil.formatNumber(a.getTotalMasksCrafted())));
        sender.sendMessage("");

        // Popular masks
        sender.sendMessage(MessageUtil.color(" &dPopular Masks:"));
        Map<String, Integer> topMasks = a.getTopOwnedMasks();
        if (topMasks.isEmpty()) {
            sender.sendMessage(MessageUtil.color("  &7No mask data available."));
        } else {
            int rank = 1;
            for (Map.Entry<String, Integer> entry : topMasks.entrySet()) {
                String maskId = entry.getKey();
                int ownerCount = entry.getValue();
                // Try to get display name from config
                MaskConfig config = plugin.getConfigManager().getMaskConfig(maskId);
                String displayName = config != null ? config.getDisplayName() : maskId;
                sender.sendMessage(MessageUtil.color("  &f" + rank + ". &e" + displayName +
                    " &7(owned by &f" + ownerCount + " &7players)"));
                rank++;
            }
        }

        String equippedMask = a.getMostEquippedMask();
        if (equippedMask != null && !equippedMask.isEmpty()) {
            MaskConfig eqConfig = plugin.getConfigManager().getMaskConfig(equippedMask);
            String eqName = eqConfig != null ? eqConfig.getDisplayName() : equippedMask;
            sender.sendMessage(MessageUtil.color("  &fMost Equipped: &d" + eqName));
        } else {
            sender.sendMessage(MessageUtil.color("  &fMost Equipped: &7None"));
        }
        sender.sendMessage("");

        sender.sendMessage(MessageUtil.color(thinLine));

        // Progression
        sender.sendMessage(MessageUtil.color(" &3Progression:"));
        sender.sendMessage(MessageUtil.color("  &fMissions Completed: &a" + MessageUtil.formatNumber(a.getTotalMissionsCompleted())));
        sender.sendMessage(MessageUtil.color("  &fDaily Missions Completed: &a" + MessageUtil.formatNumber(a.getTotalDailyMissionsCompleted())));
        sender.sendMessage(MessageUtil.color("  &fActive Boosters: &e" + a.getActiveBoosters()));

        sender.sendMessage(MessageUtil.color(line));
    }

    private void handleBoosterStatus(CommandSender sender, String[] args) {
        Player target;
        if (args.length >= 2) {
            target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage(MessageUtil.color("&cPlayer not found: " + args[1]));
                return;
            }
        } else if (sender instanceof Player) {
            target = (Player) sender;
        } else {
            sender.sendMessage(MessageUtil.color("&cUsage: /hunt boosters <player>"));
            return;
        }
        
        java.util.List<String> status = plugin.getBoosterManager().getBoosterStatus(target);
        
        sender.sendMessage(MessageUtil.color("&8&m                                              "));
        sender.sendMessage(MessageUtil.color("&b&l⚡ Active Boosters for " + target.getName()));
        sender.sendMessage(MessageUtil.color("&8&m                                              "));
        
        if (status.isEmpty()) {
            sender.sendMessage(MessageUtil.color("&7No active boosters."));
        } else {
            for (String line : status) {
                sender.sendMessage(MessageUtil.color(line));
            }
        }
        
        sender.sendMessage(MessageUtil.color("&8&m                                              "));
    }
    
    private int parseInt(String s, int def) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return def;
        }
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (!sender.hasPermission("headhunting.admin")) {
            return completions;
        }
        
        if (args.length == 1) {
            completions.addAll(Arrays.asList("reload", "setlevel", "setxp", "addxp", "givemask", 
                "givehead", "giveessence", "mission", "resetplayer", "stats", "shopconfig",
                "givemystery", "givebosshead", "givebooster", "boosters", "analytics"));
        } else if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "mission":
                    completions.addAll(Arrays.asList("list", "progress", "complete", "reset", "setprogress"));
                    break;
                case "setlevel":
                case "setxp":
                case "addxp":
                case "givemask":
                case "givehead":
                case "giveessence":
                case "resetplayer":
                case "stats":
                case "givebooster":
                case "boosters":
                    Bukkit.getOnlinePlayers().forEach(p -> completions.add(p.getName()));
                    break;
            }
        } else if (args.length == 3) {
            if (args[0].equalsIgnoreCase("givebooster")) {
                completions.addAll(Arrays.asList("xp", "sell", "fishing"));
            }
        } else if (args.length == 4) {
            if (args[0].equalsIgnoreCase("givebooster")) {
                completions.addAll(Arrays.asList("personal", "faction"));
            }
        } else if (args.length == 5) {
            if (args[0].equalsIgnoreCase("givebooster")) {
                completions.addAll(Arrays.asList("1.5", "1.75", "2.0", "2.5", "3.0"));
            }
        } else if (args.length == 6) {
            if (args[0].equalsIgnoreCase("givebooster")) {
                completions.addAll(Arrays.asList("5", "10", "15"));
            }
        }
        
        if (args.length == 3) {
            switch (args[0].toLowerCase()) {
                case "givemask":
                    completions.addAll(plugin.getConfigManager().getMaskConfigs().keySet());
                    break;
                case "givehead":
                    // Include all head keys (EntityType + sub-types like WITHER_SKELETON)
                    completions.addAll(plugin.getConfigManager().getHeadConfigsByKey().keySet());
                    break;
                case "mission":
                    if (args[1].equalsIgnoreCase("progress") || args[1].equalsIgnoreCase("complete") ||
                        args[1].equalsIgnoreCase("reset") || args[1].equalsIgnoreCase("setprogress")) {
                        Bukkit.getOnlinePlayers().forEach(p -> completions.add(p.getName()));
                    }
                    break;
            }
        } else if (args.length == 4) {
            if (args[0].equalsIgnoreCase("mission")) {
                completions.addAll(plugin.getMissionManager().getAllMissions().keySet());
            }
        } else if (args.length == 5) {
            if (args[0].equalsIgnoreCase("mission") && args[1].equalsIgnoreCase("setprogress")) {
                MissionConfig mission = plugin.getMissionManager().getMission(args[3]);
                if (mission != null) {
                    mission.getRequirements().forEach(r -> completions.add(r.getId()));
                }
            }
        }
        
        String partial = args[args.length - 1].toLowerCase();
        return completions.stream()
            .filter(s -> s.toLowerCase().startsWith(partial))
            .collect(Collectors.toList());
    }
}

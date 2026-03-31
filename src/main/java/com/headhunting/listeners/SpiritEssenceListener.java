package com.headhunting.listeners;

import com.headhunting.HeadHunting;
import com.headhunting.data.MaskConfig;
import com.headhunting.data.PlayerData;
import com.headhunting.data.PlayerData.MaskData;
import com.headhunting.utils.MessageUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Handles Spirit Essence right-click to upgrade equipped masks.
 * Right-clicking while holding Spirit Essence upgrades the worn mask by 1 level.
 * Consumes 1 essence from the stack per use.
 */
public class SpiritEssenceListener implements Listener {

    private final HeadHunting plugin;

    public SpiritEssenceListener(HeadHunting plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack heldItem = player.getItemInHand();

        // Check if held item is Spirit Essence
        if (!plugin.getMaskFactory().isSpiritEssence(heldItem)) {
            return;
        }

        // Cancel the event to prevent placing/interacting
        event.setCancelled(true);

        // Check if player has a mask equipped (helmet slot)
        String equippedMaskId = plugin.getMaskManager().getEquippedMaskFromHelmet(player);
        if (equippedMaskId == null) {
            MessageUtil.send(player, "&cYou need to be wearing a mask to use Spirit Essence!");
            return;
        }

        // Get mask config
        MaskConfig maskConfig = plugin.getConfigManager().getMaskConfig(equippedMaskId);
        if (maskConfig == null) {
            MessageUtil.send(player, "&cInvalid mask detected!");
            return;
        }

        // Get player's mask data
        PlayerData data = plugin.getDataManager().getPlayerData(player);
        MaskData maskData = data.getMaskData(equippedMaskId);

        if (maskData == null || !maskData.isOwned()) {
            MessageUtil.send(player, "&cYou need to be wearing a mask to use Spirit Essence!");
            return;
        }

        int currentLevel = maskData.getLevel();

        // Check max level
        if (currentLevel >= 5) {
            MessageUtil.send(player, "&cThis mask is already at maximum level!");
            return;
        }

        // Level up the mask
        int newLevel = currentLevel + 1;
        maskData.setLevel(newLevel);

        // Reset deposited heads for this level (since we're using essence, not heads)
        maskData.setDepositedHeads(0);

        // Consume 1 essence from the stack
        if (heldItem.getAmount() > 1) {
            heldItem.setAmount(heldItem.getAmount() - 1);
        } else {
            player.setItemInHand(null);
        }

        // Update the helmet item to reflect the new level
        ItemStack newMask = plugin.getMaskFactory().createMask(equippedMaskId, newLevel);
        player.getInventory().setHelmet(newMask);

        // Re-apply passive abilities
        plugin.getMaskManager().applyPassiveAbilities(player);

        // Send success message
        String displayName = maskConfig.getDisplayName();
        MessageUtil.send(player, "&a&l✦ &aMask upgraded! &6" + displayName + " &ais now level &e" + newLevel + "&a!");

        // Play effect
        player.getWorld().playEffect(player.getLocation(), org.bukkit.Effect.ENDER_SIGNAL, 0);
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
    }
}

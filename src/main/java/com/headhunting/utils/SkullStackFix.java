package com.headhunting.utils;

import org.bukkit.Material;

import java.util.logging.Logger;

/**
 * Skull stack fix diagnostics for HeadHunting masks.
 *
 * The anti-stack strategy is:
 * 1. Each mask gets a UNIQUE GameProfile UUID → different SkullOwner.Id → won't stack
 * 2. Each mask gets unique PDC tags (via NBTUtil)
 * 3. MaskStackListener cancels ItemMergeEvent for masks
 * 4. MaskStackListener splits any stacked masks on pickup/spawn
 */
public class SkullStackFix {

    private static Logger logger;

    public static void apply(Logger log) {
        logger = log;

        int maxStack = Material.PLAYER_HEAD.getMaxStackSize();
        logger.info("[SkullStackFix] PLAYER_HEAD maxStackSize: " + maxStack);
        logger.info("[SkullStackFix] Per-item PDC + event cancellation strategy active");
    }
}

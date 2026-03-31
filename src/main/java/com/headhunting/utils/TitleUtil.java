package com.headhunting.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.entity.Player;

import java.time.Duration;

/**
 * Utility for sending titles and action bar messages.
 * Uses Paper's Adventure API (1.18+) for proper component support.
 */
public class TitleUtil {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    /**
     * Send title and subtitle to player using Adventure API.
     * fadeIn/stay/fadeOut are in ticks.
     */
    public static void sendTitle(Player player, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        Component titleComp    = title    != null && !title.isEmpty()    ? LEGACY.deserialize(title)    : Component.empty();
        Component subtitleComp = subtitle != null && !subtitle.isEmpty() ? LEGACY.deserialize(subtitle) : Component.empty();

        Title.Times times = Title.Times.times(
            Duration.ofMillis(fadeIn * 50L),
            Duration.ofMillis(stay * 50L),
            Duration.ofMillis(fadeOut * 50L)
        );
        player.showTitle(Title.title(titleComp, subtitleComp, times));
    }

    /**
     * Send action bar message using Adventure API.
     */
    public static void sendActionBar(Player player, String message) {
        player.sendActionBar(LEGACY.deserialize(message));
    }
}

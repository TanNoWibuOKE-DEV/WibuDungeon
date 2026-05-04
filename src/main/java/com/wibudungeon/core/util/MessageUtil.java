package com.wibudungeon.core.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;

/**
 * Utility class for formatting and sending messages to players.
 * Supports legacy & color codes.
 */
public final class MessageUtil {

    private static final LegacyComponentSerializer LEGACY =
            LegacyComponentSerializer.legacyAmpersand();

    private MessageUtil() {
    }

    /**
     * Colorize a string using legacy ampersand color codes.
     *
     * @param message the raw message with color codes
     * @return the colorized Component
     */
    public static Component colorize(String message) {
        if (message == null) return Component.empty();
        return LEGACY.deserialize(message);
    }

    /**
     * Send a colorized message to a player.
     */
    public static void send(Player player, String message) {
        if (player == null || message == null) return;
        player.sendMessage(colorize(message));
    }

    /**
     * Send a colorized message with placeholder replacements.
     * Placeholders are provided as key-value pairs: key1, value1, key2, value2, ...
     */
    public static void send(Player player, String message, String... placeholders) {
        if (player == null || message == null) return;
        player.sendMessage(colorize(replace(message, placeholders)));
    }

    /**
     * Replace placeholders in a message string.
     * Placeholders are provided as key-value pairs.
     */
    public static String replace(String message, String... placeholders) {
        if (message == null) return "";
        String processed = message;
        for (int i = 0; i < placeholders.length - 1; i += 2) {
            processed = processed.replace(placeholders[i], placeholders[i + 1]);
        }
        return processed;
    }

    /**
     * Strip color codes from a string for plain text.
     */
    public static String stripColor(String message) {
        if (message == null) return "";
        return message.replaceAll("&[0-9a-fk-orA-FK-OR]", "");
    }
}

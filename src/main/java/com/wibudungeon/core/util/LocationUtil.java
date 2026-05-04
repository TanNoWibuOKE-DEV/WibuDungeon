package com.wibudungeon.core.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Utility class for serializing and deserializing Bukkit Locations.
 * Format: "world,x,y,z,yaw,pitch"
 */
public final class LocationUtil {

    private LocationUtil() {
    }

    /**
     * Serialize a Location to a string.
     *
     * @param location the location to serialize
     * @return serialized string or "null" if invalid
     */
    public static String serialize(Location location) {
        if (location == null || location.getWorld() == null) return "null";
        return location.getWorld().getName() + "," +
                location.getX() + "," +
                location.getY() + "," +
                location.getZ() + "," +
                location.getYaw() + "," +
                location.getPitch();
    }

    /**
     * Deserialize a string back to a Location.
     *
     * @param str the serialized location string
     * @return the Location, or null if invalid
     */
    public static Location deserialize(String str) {
        if (str == null || str.equalsIgnoreCase("null")) return null;
        String[] parts = str.split(",");
        if (parts.length < 4) return null;

        try {
            World world = Bukkit.getWorld(parts[0]);
            if (world == null) return null;

            double x = Double.parseDouble(parts[1]);
            double y = Double.parseDouble(parts[2]);
            double z = Double.parseDouble(parts[3]);
            float yaw = parts.length > 4 ? Float.parseFloat(parts[4]) : 0;
            float pitch = parts.length > 5 ? Float.parseFloat(parts[5]) : 0;

            return new Location(world, x, y, z, yaw, pitch);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Save a location to a config section.
     */
    public static void saveToConfig(ConfigurationSection section, String path, Location location) {
        if (section == null || location == null) return;
        section.set(path, serialize(location));
    }

    /**
     * Load a location from a config section.
     */
    public static Location loadFromConfig(ConfigurationSection section, String path) {
        if (section == null) return null;
        return deserialize(section.getString(path));
    }

    /**
     * Format a location for display purposes.
     */
    public static String format(Location location) {
        if (location == null) return "N/A";
        return String.format("%.1f, %.1f, %.1f in %s",
                location.getX(), location.getY(), location.getZ(),
                location.getWorld() != null ? location.getWorld().getName() : "unknown");
    }
}

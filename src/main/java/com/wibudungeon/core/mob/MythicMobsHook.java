package com.wibudungeon.core.mob;

import com.wibudungeon.core.util.MessageUtil;
import io.lumine.mythic.api.MythicProvider;
import io.lumine.mythic.api.mobs.MythicMob;
import io.lumine.mythic.bukkit.BukkitAdapter;
import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.core.mobs.ActiveMob;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.Plugin;

import java.util.Collection;
import java.util.Optional;
import java.util.logging.Level;

/**
 * Hook for MythicMobs integration.
 * All MythicMobs API calls are isolated here to handle the soft dependency safely.
 */
public class MythicMobsHook {

    private final Plugin plugin;
    private boolean enabled = false;

    public MythicMobsHook(Plugin plugin) {
        this.plugin = plugin;
        try {
            if (Bukkit.getPluginManager().getPlugin("MythicMobs") != null) {
                // Force class load to verify MythicMobs API is available
                Class.forName("io.lumine.mythic.bukkit.MythicBukkit");
                enabled = true;
                plugin.getLogger().info("MythicMobs integration enabled!");
            } else {
                plugin.getLogger().info("MythicMobs not found. Using vanilla mobs only.");
            }
        } catch (ClassNotFoundException e) {
            plugin.getLogger().info("MythicMobs API not available. Using vanilla mobs only.");
        }
    }

    /**
     * Check if MythicMobs is available and enabled.
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Check if a mob ID is a MythicMobs identifier.
     * MythicMobs IDs are prefixed with "mm:" in config.
     *
     * @param mobId the mob identifier string
     * @return true if this is a MythicMobs mob ID
     */
    public static boolean isMythicMob(String mobId) {
        return mobId != null && mobId.startsWith("mm:");
    }

    /**
     * Extract the MythicMobs internal name from a prefixed ID.
     * "mm:SkeletonKing" → "SkeletonKing"
     */
    public static String getMythicName(String mobId) {
        if (mobId == null || !mobId.startsWith("mm:")) return mobId;
        return mobId.substring(3);
    }

    /**
     * Check if a MythicMobs mob type exists.
     *
     * @param mythicName the MythicMobs internal mob name (without mm: prefix)
     * @return true if the mob type exists
     */
    public boolean mobExists(String mythicName) {
        if (!enabled) return false;
        try {
            Optional<MythicMob> mob = MythicBukkit.inst().getMobManager().getMythicMob(mythicName);
            return mob.isPresent();
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error checking MythicMob: " + mythicName, e);
            return false;
        }
    }

    /**
     * Spawn a MythicMobs mob at a location.
     *
     * @param mythicName the MythicMobs internal mob name (without mm: prefix)
     * @param location   where to spawn the mob
     * @param level      the mob level (scaling)
     * @return the spawned entity, or null if failed
     */
    public LivingEntity spawnMob(String mythicName, Location location, double level) {
        if (!enabled) return null;
        try {
            ActiveMob activeMob = MythicBukkit.inst().getMobManager()
                    .spawnMob(mythicName, BukkitAdapter.adapt(location), level);
            if (activeMob != null) {
                Entity entity = activeMob.getEntity().getBukkitEntity();
                if (entity instanceof LivingEntity living) {
                    return living;
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to spawn MythicMob: " + mythicName, e);
        }
        return null;
    }

    /**
     * Spawn a MythicMobs mob at a location with default level 1.
     */
    public LivingEntity spawnMob(String mythicName, Location location) {
        return spawnMob(mythicName, location, 1.0);
    }

    /**
     * Get all available MythicMobs mob type names.
     *
     * @return collection of mob type names, or empty if not enabled
     */
    public Collection<String> getMobNames() {
        if (!enabled) return java.util.Collections.emptyList();
        try {
            return MythicBukkit.inst().getMobManager().getMobNames();
        } catch (Exception e) {
            return java.util.Collections.emptyList();
        }
    }
}

package com.wibudungeon.core.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * Safe WorldEdit/FAWE integration for reading player selections.
 * Works with both WorldEdit 7.x and FastAsyncWorldEdit.
 * Falls back gracefully if neither is installed.
 *
 * @since v1.0.9
 */
public class WorldEditHook {

    private WorldEditHook() {}

    /**
     * Check if WorldEdit or FAWE is available on the server.
     */
    public static boolean isAvailable() {
        return Bukkit.getPluginManager().getPlugin("WorldEdit") != null
                || Bukkit.getPluginManager().getPlugin("FastAsyncWorldEdit") != null;
    }

    /**
     * Get the player's current WorldEdit selection as [pos1, pos2].
     * Returns null if no selection, different worlds, or WE not available.
     */
    public static Location[] getSelection(Player player) {
        if (!isAvailable()) return null;

        try {
            // Get WorldEdit session using static BukkitAdapter methods
            com.sk89q.worldedit.LocalSession session = com.sk89q.worldedit.WorldEdit.getInstance()
                    .getSessionManager()
                    .get(com.sk89q.worldedit.bukkit.BukkitAdapter.adapt(player));

            if (session == null) return null;

            com.sk89q.worldedit.regions.Region region = session.getSelection(
                    com.sk89q.worldedit.bukkit.BukkitAdapter.adapt(player.getWorld()));

            if (region == null) return null;

            // Only support cuboid selections
            if (!(region instanceof com.sk89q.worldedit.regions.CuboidRegion cuboid)) {
                return null;
            }

            com.sk89q.worldedit.math.BlockVector3 min = cuboid.getMinimumPoint();
            com.sk89q.worldedit.math.BlockVector3 max = cuboid.getMaximumPoint();

            World world = player.getWorld();
            Location pos1 = new Location(world, min.x(), min.y(), min.z());
            Location pos2 = new Location(world, max.x(), max.y(), max.z());

            return new Location[]{pos1, pos2};

        } catch (com.sk89q.worldedit.IncompleteRegionException e) {
            // Player hasn't completed selection
            return null;
        } catch (Exception e) {
            // Any reflection/version mismatch — fail gracefully
            Bukkit.getLogger().warning("[WibuDungeon] WorldEdit integration error: " + e.getMessage());
            return null;
        }
    }
}

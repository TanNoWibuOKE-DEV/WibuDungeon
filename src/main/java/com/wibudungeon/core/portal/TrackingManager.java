package com.wibudungeon.core.portal;

import com.wibudungeon.core.config.ConfigManager;
import com.wibudungeon.core.util.MessageUtil;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * GPS Tracking System v1.0.6 — ArmorStand + TextDisplay + velocity-based movement.
 *
 * Behavior:
 * - Spawns invisible ArmorStand near player with TextDisplay passenger
 * - Entity moves toward portal using velocity vectors
 * - TextDisplay shows direction + distance, updates continuously
 * - Removes on arrival (within 10 blocks), portal disappearance, or player cancel
 * - One tracker per player enforced
 */
public class TrackingManager {

    private final Plugin plugin;
    private final ConfigManager configManager;
    private final PortalManager portalManager;
    private final Map<UUID, TrackingSession> activeSessions = new HashMap<>();

    private static final double GPS_SPEED = 0.35;       // Blocks per tick velocity magnitude
    private static final double ARRIVAL_DISTANCE = 10.0; // Distance to consider "arrived"
    private static final double LEASH_DISTANCE = 15.0;   // Max distance before GPS teleports to player
    private static final double HOVER_HEIGHT = 2.5;      // Height above ground for GPS entity

    public TrackingManager(Plugin plugin, ConfigManager configManager, PortalManager portalManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.portalManager = portalManager;
    }

    /**
     * Start tracking a portal for a player using GPS entity system.
     */
    public void startTracking(Player player, UUID portalId) {
        // Prevent duplicate — clean up old session first
        if (isTracking(player.getUniqueId())) {
            stopTracking(player.getUniqueId());
        }

        DungeonPortal portal = portalManager.getPortalById(portalId);
        if (portal == null || portal.isExpired()) {
            MessageUtil.send(player, configManager.getPrefix() + "&cThis portal no longer exists!");
            return;
        }

        MessageUtil.send(player, configManager.getPrefix() + "&a⏳ GPS Tracker activated!");
        MessageUtil.send(player, configManager.getPrefix() + "&7Follow the floating guide to the portal.");
        MessageUtil.send(player, configManager.getPrefix() + "&7Use &e/wd untrack &7to stop tracking.");

        Location portalCenter = portal.getCenter();
        if (portalCenter == null) return;

        // Create session
        TrackingSession session = new TrackingSession(player.getUniqueId(), portalId);
        activeSessions.put(player.getUniqueId(), session);

        // Start GPS update task
        session.task = new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                Player p = Bukkit.getPlayer(player.getUniqueId());
                if (p == null || !p.isOnline()) {
                    cleanupSession(player.getUniqueId());
                    cancel();
                    return;
                }

                // Check portal validity every 40 ticks (2 seconds)
                if (ticks++ % 40 == 0) {
                    if (portal.isExpired() || !portal.isValid()) {
                        MessageUtil.send(p, configManager.getPrefix() + "&cThe tracked portal has disappeared!");
                        cleanupSession(player.getUniqueId());
                        cancel();
                        return;
                    }
                }


                Location portalCenter = portal.getCenter();
                if (portalCenter == null || portalCenter.getWorld() == null) {
                    cleanupSession(player.getUniqueId());
                    cancel();
                    return;
                }

                // Different world check
                if (!p.getWorld().equals(portalCenter.getWorld())) {
                    p.sendActionBar(MessageUtil.colorize("&c✘ Portal is in a different world!"));
                    return;
                }

                Location playerLoc = p.getLocation();
                double distance = playerLoc.distance(portalCenter);
                String distStr = String.format("%.1f", distance);

                // Check arrival
                if (distance <= ARRIVAL_DISTANCE) {
                    cancel();
                    cleanupSession(player.getUniqueId());
                    MessageUtil.send(p, configManager.getPrefix() + "&a&l✔ You've reached the dungeon portal!");
                    spawnFirework(p.getLocation());
                    p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
                    return;
                }

                // Calculate direction arrow
                Vector dirFromPlayer = portalCenter.toVector().subtract(playerLoc.toVector());
                double angle = Math.toDegrees(Math.atan2(dirFromPlayer.getZ(), dirFromPlayer.getX())) - 90;
                double yaw = playerLoc.getYaw();
                double relative = (angle - yaw + 540) % 360 - 180;
                String arrow = getDirectionArrow(relative);

                // Color based on distance
                String color;
                if (distance <= 20) color = "&a";
                else if (distance <= 50) color = "&e";
                else if (distance <= 100) color = "&6";
                else color = "&c";

                // Update ActionBar
                p.sendActionBar(MessageUtil.colorize(
                        "&e⏳ Portal " + arrow + " &8| " + color + distStr + " blocks"));
            }
        };
        session.task.runTaskTimer(plugin, 0L, 5L); // Update distance every 5 ticks
    }

    /**
     * Stop tracking for a player and clean up GPS entities.
     */
    public void stopTracking(UUID playerId) {
        cleanupSession(playerId);
    }

    /**
     * Check if a player is currently tracking.
     */
    public boolean isTracking(UUID playerId) {
        return activeSessions.containsKey(playerId);
    }

    /**
     * Clean up all tracking sessions.
     */
    public void cleanupAll() {
        for (UUID playerId : new java.util.ArrayList<>(activeSessions.keySet())) {
            cleanupSession(playerId);
        }
    }

    /**
     * Clean up a single tracking session — remove entities, cancel task.
     */
    private void cleanupSession(UUID playerId) {
        TrackingSession session = activeSessions.remove(playerId);
        if (session == null) return;

        if (session.task != null) {
            session.task.cancel();
        }
    }

    private void spawnFirework(Location loc) {
        Firework fw = loc.getWorld().spawn(loc, Firework.class);
        FireworkMeta meta = fw.getFireworkMeta();
        meta.addEffect(FireworkEffect.builder()
                .with(FireworkEffect.Type.BALL_LARGE)
                .withColor(Color.ORANGE, Color.PURPLE)
                .withFade(Color.WHITE)
                .flicker(true)
                .trail(true)
                .build());
        meta.setPower(0);
        fw.setFireworkMeta(meta);
        Bukkit.getScheduler().runTaskLater(plugin, fw::detonate, 2L);
    }

    private String getDirectionArrow(double relativeAngle) {
        if (relativeAngle >= -22.5 && relativeAngle < 22.5) return "⬆";
        if (relativeAngle >= 22.5 && relativeAngle < 67.5) return "⬈";
        if (relativeAngle >= 67.5 && relativeAngle < 112.5) return "➡";
        if (relativeAngle >= 112.5 && relativeAngle < 157.5) return "⬊";
        if (relativeAngle >= 157.5 || relativeAngle < -157.5) return "⬇";
        if (relativeAngle >= -157.5 && relativeAngle < -112.5) return "⬋";
        if (relativeAngle >= -112.5 && relativeAngle < -67.5) return "⬅";
        if (relativeAngle >= -67.5 && relativeAngle < -22.5) return "⬉";
        return "⬆";
    }

    /**
     * Tracking session state
     */
    private static class TrackingSession {
        final UUID playerId;
        final UUID portalId;
        BukkitRunnable task;

        TrackingSession(UUID playerId, UUID portalId) {
            this.playerId = playerId;
            this.portalId = portalId;
        }
    }
}

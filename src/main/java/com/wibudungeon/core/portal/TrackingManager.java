package com.wibudungeon.core.portal;

import com.wibudungeon.core.config.ConfigManager;
import com.wibudungeon.core.util.MessageUtil;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Portal Tracking HUD System v1.0.9
 *
 * Based on the Waypoint HUD approach — spawns a floating TextDisplay
 * that follows the player's camera view and shows directional arrows
 * pointing toward the tracked portal with distance display.
 *
 * Features:
 * - Screen-space projection using forward/right/up camera vectors
 * - Directional arrows (⇒ ⇗ ⇑ ⇖ ⇐ ⇙ ⇓ ⇘) with distance
 * - When looking directly at portal, shows marker at portal location
 * - Color-coded distance indicator (green/yellow/orange/red)
 * - Smooth teleport with interpolation (setTeleportDuration)
 * - Auto-complete on arrival, auto-cancel on portal expiry
 * - Per-player TextDisplay entity (cleaned up on stop/quit)
 *
 * @since v1.0.9
 */
public class TrackingManager {

    private final Plugin plugin;
    private final ConfigManager configManager;
    private final PortalManager portalManager;
    private final Map<UUID, TrackingSession> activeSessions = new HashMap<>();

    // HUD positioning constants
    private static final double HUD_FORWARD = 2.0;        // Distance in front of eyes
    private static final double HUD_HORIZONTAL = 3.5;      // Max horizontal screen offset
    private static final double HUD_VERTICAL = 2.5;        // Max vertical screen offset
    private static final float HUD_SCALE = 1.2f;           // TextDisplay scale
    private static final double ARRIVAL_DISTANCE = 10.0;   // Distance to auto-complete

    public TrackingManager(Plugin plugin, ConfigManager configManager, PortalManager portalManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.portalManager = portalManager;
    }

    /**
     * Start tracking a portal — spawns a HUD TextDisplay that follows the player.
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

        Location portalCenter = portal.getCenter();
        if (portalCenter == null || portalCenter.getWorld() == null) return;

        MessageUtil.send(player, configManager.getPrefix() + "&a⏳ Portal Tracker activated!");
        MessageUtil.send(player, configManager.getPrefix() + "&7Follow the &e⇔ &7marker to the portal.");
        MessageUtil.send(player, configManager.getPrefix() + "&7Use &e/wd untrack &7to stop tracking.");

        // Spawn HUD display
        TextDisplay hud = spawnHUD(player);

        // Create session
        TrackingSession session = new TrackingSession(player.getUniqueId(), portalId, hud);
        activeSessions.put(player.getUniqueId(), session);

        // Start per-tick update task
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

                // Check portal validity every 2 seconds
                if (ticks++ % 40 == 0) {
                    if (portal.isExpired() || !portal.isValid()) {
                        MessageUtil.send(p, configManager.getPrefix() + "&cThe tracked portal has disappeared!");
                        cleanupSession(player.getUniqueId());
                        cancel();
                        return;
                    }
                }

                Location target = portal.getCenter();
                if (target == null || target.getWorld() == null) {
                    cleanupSession(player.getUniqueId());
                    cancel();
                    return;
                }

                // Different world check
                if (!p.getWorld().equals(target.getWorld())) {
                    if (session.hudDisplay != null && !session.hudDisplay.isDead()) {
                        session.hudDisplay.text(
                                MessageUtil.colorize("&c✘ Different World"));
                    }
                    return;
                }

                updateHUD(p, session, target);
            }
        };
        session.task.runTaskTimer(plugin, 0L, 1L); // Every tick for smooth tracking
    }

    /**
     * Core HUD update — projects portal position onto player's screen space.
     */
    private void updateHUD(Player player, TrackingSession session, Location targetLoc) {
        Location eye = player.getEyeLocation();
        Location targetCenter = targetLoc.clone().add(0, 1.5, 0);
        double distance = eye.distance(targetCenter);

        // Respawn HUD if dead
        if (session.hudDisplay == null || session.hudDisplay.isDead()) {
            session.hudDisplay = spawnHUD(player);
        }

        TextDisplay display = session.hudDisplay;

        // Check arrival
        if (distance <= ARRIVAL_DISTANCE) {
            cleanupSession(player.getUniqueId());
            MessageUtil.send(player, configManager.getPrefix() + "&a&l✔ You've reached the dungeon portal!");
            spawnFirework(player.getLocation());
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
            return;
        }

        // Distance color
        String distColor;
        if (distance <= 20) distColor = "&a";
        else if (distance <= 50) distColor = "&e";
        else if (distance <= 100) distColor = "&6";
        else distColor = "&c";

        String distStr = (int) distance + "m";

        // Camera vectors
        Vector forward = eye.getDirection().normalize();
        Vector toTarget = targetCenter.toVector().subtract(eye.toVector()).normalize();
        double dotForward = forward.dot(toTarget);

        // Right vector (perpendicular to forward on horizontal plane)
        Vector right = new Vector(-forward.getZ(), 0, forward.getX()).normalize();
        if (right.lengthSquared() < 0.01) right = new Vector(1, 0, 0);

        // Up vector (perpendicular to forward and right)
        Vector up = forward.clone().crossProduct(right).multiply(-1).normalize();

        double dotRight = right.dot(toTarget);
        double dotUp = up.dot(toTarget);

        boolean isLookingAt = dotForward > 0.85;

        if (distance < 15.0 && isLookingAt) {
            // Close and looking at it — show marker at actual portal position
            display.teleport(targetCenter.toVector().toLocation(player.getWorld()));
            display.text(
                    MessageUtil.colorize("&e&l⚔ &fPortal\n" + distColor + distStr));
        } else {
            // Screen-space projection
            double screenX, screenY;
            String icon;

            if (dotForward > 0) {
                // Target is in front — project onto screen
                screenX = dotRight / dotForward;
                screenY = dotUp / dotForward;

                if (Math.abs(screenX) < 0.3 && Math.abs(screenY) < 0.3) {
                    icon = "⇔";  // Looking roughly at it
                } else {
                    icon = getArrow(Math.atan2(screenY, screenX));
                }
            } else {
                // Target is behind — show arrow at screen edge
                double len = Math.sqrt(dotRight * dotRight + dotUp * dotUp);
                if (len < 0.001) len = 1;
                screenX = (dotRight / len);
                screenY = (dotUp / len);
                icon = getArrow(Math.atan2(screenY, screenX));
            }

            // Clamp to screen bounds
            screenX = Math.max(-1.3, Math.min(1.3, screenX));
            screenY = Math.max(-0.9, Math.min(0.9, screenY));

            // Calculate HUD world position
            Location hudPos = eye.clone()
                    .add(forward.clone().multiply(HUD_FORWARD))
                    .add(right.clone().multiply(screenX * HUD_HORIZONTAL))
                    .add(up.clone().multiply(screenY * HUD_VERTICAL));

            display.teleport(hudPos);
            display.text(
                    MessageUtil.colorize("&f" + icon + "\n" + distColor + distStr));
        }
    }

    /**
     * Directional arrow based on screen-space angle.
     */
    private String getArrow(double angle) {
        double deg = Math.toDegrees(angle);
        if (deg >= -22.5 && deg < 22.5) return "⇒";
        if (deg >= 22.5 && deg < 67.5) return "⇗";
        if (deg >= 67.5 && deg < 112.5) return "⇑";
        if (deg >= 112.5 && deg < 157.5) return "⇖";
        if (deg >= 157.5 || deg < -157.5) return "⇐";
        if (deg >= -157.5 && deg < -112.5) return "⇙";
        if (deg >= -112.5 && deg < -67.5) return "⇓";
        if (deg >= -67.5 && deg < -22.5) return "⇘";
        return "•";
    }

    /**
     * Spawn the tracking HUD TextDisplay entity.
     */
    private TextDisplay spawnHUD(Player player) {
        return player.getWorld().spawn(player.getEyeLocation(), TextDisplay.class, e -> {
            e.setBillboard(Display.Billboard.CENTER);
            e.setBrightness(new Display.Brightness(15, 15));
            e.setTeleportDuration(2); // Smooth interpolated movement
            e.setSeeThrough(true);
            e.setShadowed(true);
            e.setBackgroundColor(Color.fromARGB(100, 0, 0, 0));
            e.setPersistent(false);
            e.setTransformation(new Transformation(
                    new Vector3f(), new AxisAngle4f(),
                    new Vector3f(HUD_SCALE, HUD_SCALE, HUD_SCALE),
                    new AxisAngle4f()
            ));
        });
    }

    /**
     * Stop tracking for a player and clean up HUD entity.
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
     * Clean up a single tracking session — remove HUD entity, cancel task.
     */
    private void cleanupSession(UUID playerId) {
        TrackingSession session = activeSessions.remove(playerId);
        if (session == null) return;

        if (session.task != null) {
            session.task.cancel();
        }
        if (session.hudDisplay != null && !session.hudDisplay.isDead()) {
            session.hudDisplay.remove();
        }
    }

    private void spawnFirework(Location loc) {
        if (loc.getWorld() == null) return;
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

    /**
     * Tracking session state — holds the HUD entity and task reference.
     */
    private static class TrackingSession {
        final UUID playerId;
        final UUID portalId;
        TextDisplay hudDisplay;
        BukkitRunnable task;

        TrackingSession(UUID playerId, UUID portalId, TextDisplay hudDisplay) {
            this.playerId = playerId;
            this.portalId = portalId;
            this.hudDisplay = hudDisplay;
        }
    }
}

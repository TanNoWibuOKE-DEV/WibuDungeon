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
 * Portal Tracking HUD System v1.0.9 — Smooth Edition
 *
 * Uses a per-player TextDisplay entity with:
 * - Client-side interpolation via setTeleportDuration(3)
 * - Server-side position smoothing (lerp) to prevent snapping
 * - Per-player visibility — only the tracker sees the HUD
 * - Screen-space camera projection for directional arrows
 * - Color-coded distance indicator
 *
 * @since v1.0.9
 */
public class TrackingManager {

    private final Plugin plugin;
    private final ConfigManager configManager;
    private final PortalManager portalManager;
    private final Map<UUID, TrackingSession> activeSessions = new HashMap<>();

    // HUD positioning
    private static final double HUD_FORWARD = 2.5;
    private static final double HUD_HORIZONTAL = 3.0;
    private static final double HUD_VERTICAL = 2.0;
    private static final float HUD_SCALE = 1.0f;
    private static final double ARRIVAL_DISTANCE = 10.0;

    // Smoothing — lower = smoother but slower to react, higher = snappier
    private static final double LERP_SPEED = 0.25;

    public TrackingManager(Plugin plugin, ConfigManager configManager, PortalManager portalManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.portalManager = portalManager;
    }

    /**
     * Start tracking a portal — spawns a HUD TextDisplay visible only to this player.
     */
    public void startTracking(Player player, UUID portalId) {
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

        // Spawn HUD — initially at eye position
        TextDisplay hud = spawnHUD(player);

        // Hide from all other online players
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!online.equals(player)) {
                online.hideEntity(plugin, hud);
            }
        }

        TrackingSession session = new TrackingSession(player.getUniqueId(), portalId, hud);
        // Initialize smoothed position at eye
        session.smoothX = player.getEyeLocation().getX();
        session.smoothY = player.getEyeLocation().getY();
        session.smoothZ = player.getEyeLocation().getZ();
        activeSessions.put(player.getUniqueId(), session);

        // Main update loop — every tick for calculation, teleport uses interpolation
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

                // Portal validity check every 2 seconds
                if (ticks % 40 == 0) {
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

                if (!p.getWorld().equals(target.getWorld())) {
                    // Different world — just update text, don't move
                    if (ticks % 20 == 0 && session.hudDisplay != null && !session.hudDisplay.isDead()) {
                        session.hudDisplay.text(MessageUtil.colorize("&c✘ Different World"));
                    }
                    ticks++;
                    return;
                }

                updateHUD(p, session, target);
                ticks++;
            }
        };
        session.task.runTaskTimer(plugin, 0L, 1L);
    }

    /**
     * Core HUD update with smoothed position.
     */
    private void updateHUD(Player player, TrackingSession session, Location targetLoc) {
        Location eye = player.getEyeLocation();
        double targetY = targetLoc.getY() + 1.5;
        double distance = eye.distance(new Location(eye.getWorld(), targetLoc.getX(), targetY, targetLoc.getZ()));

        // Respawn if dead
        if (session.hudDisplay == null || session.hudDisplay.isDead()) {
            session.hudDisplay = spawnHUD(player);
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (!online.equals(player)) {
                    online.hideEntity(plugin, session.hudDisplay);
                }
            }
            session.smoothX = eye.getX();
            session.smoothY = eye.getY();
            session.smoothZ = eye.getZ();
        }

        // Arrival check
        if (distance <= ARRIVAL_DISTANCE) {
            cleanupSession(player.getUniqueId());
            MessageUtil.send(player, configManager.getPrefix() + "&a&l✔ You've reached the dungeon portal!");
            spawnFirework(player.getLocation());
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
            return;
        }

        // Distance display
        String distColor;
        if (distance <= 20) distColor = "&a";
        else if (distance <= 50) distColor = "&e";
        else if (distance <= 100) distColor = "&6";
        else distColor = "&c";
        String distStr = (int) distance + "m";

        // === Camera vectors ===
        double eyeX = eye.getX(), eyeY = eye.getY(), eyeZ = eye.getZ();

        // Forward (eye direction, normalized)
        Vector forward = eye.getDirection(); // already normalized by Bukkit

        // Direction to target (normalized)
        double dx = targetLoc.getX() - eyeX;
        double dy = targetY - eyeY;
        double dz = targetLoc.getZ() - eyeZ;
        double distToTarget = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (distToTarget < 0.001) distToTarget = 0.001;
        double toTargetX = dx / distToTarget;
        double toTargetY = dy / distToTarget;
        double toTargetZ = dz / distToTarget;

        double fwdX = forward.getX(), fwdY = forward.getY(), fwdZ = forward.getZ();

        // Right vector = cross(up_world, forward), then normalize on XZ plane
        double rightX = -fwdZ;
        double rightZ = fwdX;
        double rightLen = Math.sqrt(rightX * rightX + rightZ * rightZ);
        if (rightLen < 0.001) { rightX = 1; rightZ = 0; rightLen = 1; }
        rightX /= rightLen;
        rightZ /= rightLen;

        // Up vector = cross(forward, right) * -1
        double upX = -(fwdY * rightZ);
        double upY = -(fwdZ * rightX - fwdX * rightZ);
        double upZ = -(- fwdY * rightX);
        double upLen = Math.sqrt(upX * upX + upY * upY + upZ * upZ);
        if (upLen < 0.001) { upX = 0; upY = 1; upZ = 0; upLen = 1; }
        upX /= upLen; upY /= upLen; upZ /= upLen;

        // Dot products
        double dotForward = fwdX * toTargetX + fwdY * toTargetY + fwdZ * toTargetZ;
        double dotRight = rightX * toTargetX + /* rightY=0 */ rightZ * toTargetZ;
        double dotUp = upX * toTargetX + upY * toTargetY + upZ * toTargetZ;

        boolean lookingAt = dotForward > 0.85;

        // === Calculate target HUD position ===
        double goalX, goalY, goalZ;
        String icon;

        if (distance < 15.0 && lookingAt) {
            // Close & looking at portal — show marker at actual position
            goalX = targetLoc.getX();
            goalY = targetY;
            goalZ = targetLoc.getZ();
            icon = null; // special label
        } else {
            // Screen-space projection
            double screenX, screenY;

            if (dotForward > 0) {
                screenX = dotRight / dotForward;
                screenY = dotUp / dotForward;
            } else {
                double edgeLen = Math.sqrt(dotRight * dotRight + dotUp * dotUp);
                if (edgeLen < 0.001) edgeLen = 1;
                screenX = dotRight / edgeLen;
                screenY = dotUp / edgeLen;
            }

            // Arrow selection based on unclamped angle
            if (Math.abs(screenX) < 0.3 && Math.abs(screenY) < 0.3 && dotForward > 0) {
                icon = "⇔";
            } else {
                icon = getArrow(Math.atan2(screenY, screenX));
            }

            // Clamp to screen bounds
            screenX = clamp(screenX, -1.3, 1.3);
            screenY = clamp(screenY, -0.9, 0.9);

            // HUD world position = eye + forward*dist + right*screenX + up*screenY
            goalX = eyeX + fwdX * HUD_FORWARD + rightX * screenX * HUD_HORIZONTAL + upX * screenY * HUD_VERTICAL;
            goalY = eyeY + fwdY * HUD_FORWARD                                    + upY * screenY * HUD_VERTICAL;
            goalZ = eyeZ + fwdZ * HUD_FORWARD + rightZ * screenX * HUD_HORIZONTAL + upZ * screenY * HUD_VERTICAL;
        }

        // === Smooth position with lerp ===
        session.smoothX = lerp(session.smoothX, goalX, LERP_SPEED);
        session.smoothY = lerp(session.smoothY, goalY, LERP_SPEED);
        session.smoothZ = lerp(session.smoothZ, goalZ, LERP_SPEED);

        // Snap if too far from goal (prevents trailing on fast turns)
        double snapDist = distSq(session.smoothX, session.smoothY, session.smoothZ, goalX, goalY, goalZ);
        if (snapDist > 25.0) { // > 5 blocks away — snap
            session.smoothX = goalX;
            session.smoothY = goalY;
            session.smoothZ = goalZ;
        }

        // === Apply position ===
        Location hudLoc = new Location(player.getWorld(), session.smoothX, session.smoothY, session.smoothZ);
        session.hudDisplay.teleport(hudLoc);

        // === Update text (only every 4 ticks to reduce packet spam) ===
        session.textTick++;
        if (session.textTick >= 4) {
            session.textTick = 0;
            if (icon == null) {
                session.hudDisplay.text(
                        MessageUtil.colorize("&e&l⚔ &fPortal\n" + distColor + distStr));
            } else {
                session.hudDisplay.text(
                        MessageUtil.colorize("&f" + icon + "\n" + distColor + distStr));
            }
        }
    }

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

    private TextDisplay spawnHUD(Player player) {
        return player.getWorld().spawn(player.getEyeLocation(), TextDisplay.class, e -> {
            e.setBillboard(Display.Billboard.CENTER);
            e.setBrightness(new Display.Brightness(15, 15));
            e.setTeleportDuration(3);   // Client interpolates over 3 ticks = ultra smooth
            e.setSeeThrough(true);
            e.setShadowed(true);
            e.setBackgroundColor(Color.fromARGB(120, 0, 0, 0));
            e.setPersistent(false);     // Don't save to disk
            e.setTransformation(new Transformation(
                    new Vector3f(), new AxisAngle4f(),
                    new Vector3f(HUD_SCALE, HUD_SCALE, HUD_SCALE),
                    new AxisAngle4f()
            ));
        });
    }

    // === Utility ===

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    private static double distSq(double x1, double y1, double z1, double x2, double y2, double z2) {
        double dx = x2 - x1, dy = y2 - y1, dz = z2 - z1;
        return dx * dx + dy * dy + dz * dz;
    }

    // === Public API ===

    public void stopTracking(UUID playerId) {
        cleanupSession(playerId);
    }

    public boolean isTracking(UUID playerId) {
        return activeSessions.containsKey(playerId);
    }

    public void cleanupAll() {
        for (UUID id : new java.util.ArrayList<>(activeSessions.keySet())) {
            cleanupSession(id);
        }
    }

    /**
     * Called when a new player joins — hide existing HUD entities from them.
     */
    public void hideAllFrom(Player joiner) {
        for (TrackingSession session : activeSessions.values()) {
            if (session.hudDisplay != null && !session.hudDisplay.isDead()
                    && !session.playerId.equals(joiner.getUniqueId())) {
                joiner.hideEntity(plugin, session.hudDisplay);
            }
        }
    }

    private void cleanupSession(UUID playerId) {
        TrackingSession session = activeSessions.remove(playerId);
        if (session == null) return;
        if (session.task != null) session.task.cancel();
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
                .flicker(true).trail(true).build());
        meta.setPower(0);
        fw.setFireworkMeta(meta);
        Bukkit.getScheduler().runTaskLater(plugin, fw::detonate, 2L);
    }

    /**
     * Session state — holds HUD entity, smoothed position, and task.
     */
    private static class TrackingSession {
        final UUID playerId;
        final UUID portalId;
        TextDisplay hudDisplay;
        BukkitRunnable task;

        // Smoothed HUD position (lerped each tick)
        double smoothX, smoothY, smoothZ;

        // Text update throttle counter
        int textTick = 0;

        TrackingSession(UUID playerId, UUID portalId, TextDisplay hudDisplay) {
            this.playerId = playerId;
            this.portalId = portalId;
            this.hudDisplay = hudDisplay;
        }
    }
}

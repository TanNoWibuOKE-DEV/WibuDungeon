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
 * Ported directly from the Waypoint plugin HUD system.
 * Uses TextDisplay with screen-space projection and setTeleportDuration
 * for smooth client-side interpolation.
 *
 * @since v1.0.9
 */
public class TrackingManager {

    private final Plugin plugin;
    private final ConfigManager configManager;
    private final PortalManager portalManager;
    private final Map<UUID, TrackingSession> activeSessions = new HashMap<>();

    private static final double HUD_FORWARD = 2.0;
    private static final double HUD_HORIZONTAL = 3.5;
    private static final double HUD_VERTICAL = 2.5;
    private static final double HUD_SCALE = 1.0;
    private static final double ARRIVAL_DISTANCE = 10.0;

    public TrackingManager(Plugin plugin, ConfigManager configManager, PortalManager portalManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.portalManager = portalManager;
    }

    /**
     * Start tracking a portal for a player.
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

        TextDisplay hud = spawnHUD(player);

        // Hide from all other players
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!online.equals(player)) {
                online.hideEntity(plugin, hud);
            }
        }

        TrackingSession session = new TrackingSession(player.getUniqueId(), portalId, hud);
        activeSessions.put(player.getUniqueId(), session);

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

                if (!p.getWorld().equals(target.getWorld())) {
                    removeHUD(session);
                    return;
                }

                updateHUD(p, session, target);
            }
        };
        session.task.runTaskTimer(plugin, 0L, 1L);
    }

    /**
     * HUD update — ported directly from Waypoint plugin.
     */
    private void updateHUD(Player p, TrackingSession session, Location targetLoc) {
        Location eye = p.getEyeLocation();
        Location portalTarget = targetLoc.clone().add(0, 1.5, 0);
        double dist = eye.distance(portalTarget);

        TextDisplay display = session.hudDisplay;
        if (display == null || display.isDead()) {
            display = spawnHUD(p);
            session.hudDisplay = display;
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (!online.equals(p)) {
                    online.hideEntity(plugin, display);
                }
            }
        }

        // Arrival check
        if (dist <= ARRIVAL_DISTANCE) {
            cleanupSession(p.getUniqueId());
            MessageUtil.send(p, configManager.getPrefix() + "&a&l✔ You've reached the dungeon portal!");
            spawnFirework(p.getLocation());
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
            return;
        }

        // Distance color
        String distColor;
        if (dist <= 20) distColor = "&a";
        else if (dist <= 50) distColor = "&e";
        else if (dist <= 100) distColor = "&6";
        else distColor = "&c";

        // === Waypoint-style screen-space projection ===
        Vector forward = eye.getDirection().normalize();
        Vector targetVec = portalTarget.toVector();
        Vector toTarget = targetVec.clone().subtract(eye.toVector()).normalize();
        double dotForward = forward.dot(toTarget);
        boolean isLookingAt = dotForward > 0.85;

        if (dist < 10.0 && isLookingAt) {
            // Close & looking at portal — show at actual position
            display.teleport(targetVec.toLocation(p.getWorld()));
            display.text(MessageUtil.colorize(
                    "&e&l⚔ &fPortal" + "\n" + distColor + (int) dist + "m"));
        } else {
            Vector right = new Vector(-forward.getZ(), 0, forward.getX()).normalize();
            if (right.lengthSquared() < 0.01) right = new Vector(1, 0, 0);
            Vector up = forward.clone().crossProduct(right).multiply(-1).normalize();

            double dotRight = right.dot(toTarget);
            double dotUp = up.dot(toTarget);

            double screenX, screenY;
            String icon;

            if (dotForward > 0) {
                screenX = dotRight / dotForward;
                screenY = dotUp / dotForward;

                if (Math.abs(screenX) < 0.3 && Math.abs(screenY) < 0.3) icon = "⇔";
                else icon = getArrow(Math.atan2(screenY, screenX));
            } else {
                double len = Math.sqrt(dotRight * dotRight + dotUp * dotUp);
                screenX = (dotRight / len) * 1;
                screenY = (dotUp / len) * 1;
                icon = getArrow(Math.atan2(screenY, screenX));
            }

            screenX = Math.max(-1.3, Math.min(1.3, screenX));
            screenY = Math.max(-0.9, Math.min(0.9, screenY));

            Location hudPos = eye.clone()
                    .add(forward.multiply(HUD_FORWARD))
                    .add(right.multiply(screenX * HUD_HORIZONTAL))
                    .add(up.multiply(screenY * HUD_VERTICAL));

            display.teleport(hudPos);
            display.text(MessageUtil.colorize(
                    "&f" + icon + "\n" + distColor + (int) dist + "m"));
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

    private TextDisplay spawnHUD(Player p) {
        return p.getWorld().spawn(p.getEyeLocation(), TextDisplay.class, e -> {
            e.setBillboard(Display.Billboard.CENTER);
            e.setBrightness(new Display.Brightness(15, 15));
            e.setTeleportDuration(2);
            e.setSeeThrough(true);
            e.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            e.setPersistent(false);
            e.setTransformation(new Transformation(
                    new Vector3f(), new AxisAngle4f(),
                    new Vector3f((float) HUD_SCALE, (float) HUD_SCALE, (float) HUD_SCALE),
                    new AxisAngle4f()
            ));
        });
    }

    private void removeHUD(TrackingSession session) {
        if (session.hudDisplay != null && !session.hudDisplay.isDead()) {
            session.hudDisplay.remove();
            session.hudDisplay = null;
        }
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

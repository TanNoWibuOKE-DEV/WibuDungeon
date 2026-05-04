package com.wibudungeon.core.dungeon;

import com.wibudungeon.core.util.MessageUtil;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.*;

/**
 * Manages visual markers during setup mode.
 *
 * v1.0.6 Rework:
 * - Region: PARTICLES (border wireframe only)
 * - Mob / Boss / Spawn markers: BlockDisplay entities (NOT particles)
 * - Clear visual distinction between marker types
 * - Only visible during setup mode
 * - Clean removal when exiting setup
 */
public class MarkerManager {

    private final Plugin plugin;
    private final SetupManager setupManager;
    private final Map<UUID, BukkitRunnable> activeTasks = new HashMap<>();
    private final Map<UUID, List<BlockDisplay>> activeMarkerEntities = new HashMap<>();
    private final Map<UUID, List<TextDisplay>> activeLabelEntities = new HashMap<>();

    public MarkerManager(Plugin plugin, SetupManager setupManager) {
        this.plugin = plugin;
        this.setupManager = setupManager;
    }

    public void startShowingMarkers(Player player) {
        if (activeTasks.containsKey(player.getUniqueId())) return;

        BukkitRunnable task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline() || !setupManager.isInSetup(player.getUniqueId())) {
                    stopShowingMarkers(player);
                    return;
                }

                SetupSession session = setupManager.getSession(player.getUniqueId());
                if (session == null) return;

                // Remove old BlockDisplay markers and recreate
                removeMarkerEntities(player.getUniqueId());

                // Draw Region with PARTICLES (border only)
                if (session.getPos1() != null && session.getPos2() != null) {
                    drawRegionBox(player, session.getPos1(), session.getPos2());
                }

                // Draw Dungeon Spawn with BlockDisplay
                if (session.getSpawnPoint() != null) {
                    spawnMarkerBlock(player, session.getSpawnPoint(), Material.BEACON, "&d&l⊕ Spawn Point", 0.4f);
                }

                // Draw Boss Spawn with BlockDisplay (if in selected wave)
                int waveNum = session.getSelectedWave();
                SetupSession.WaveData wave = session.getWave(waveNum);

                if (wave != null) {
                    if (wave.getBossSpawnPoint() != null && wave.getBossId() != null) {
                        spawnMarkerBlock(player, wave.getBossSpawnPoint(), Material.LAVA, "&4&l☠ Boss Spawn (Wave " + waveNum + ")", 0.4f);
                    }

                    // Draw Mob Spawns with BlockDisplay
                    int index = 1;
                    for (SetupSession.MobSpawnEntry entry : wave.getMobSpawns()) {
                        spawnMarkerBlock(player, entry.getLocation(), Material.REDSTONE_BLOCK,
                                "&c&lMob Node #" + index + " (Wave " + waveNum + ")", 0.35f);
                        index++;
                    }
                }
            }
        };

        task.runTaskTimer(plugin, 0L, 40L); // Refresh every 2 seconds (BlockDisplay doesn't need fast updates)
        activeTasks.put(player.getUniqueId(), task);
    }

    public void stopShowingMarkers(Player player) {
        BukkitRunnable task = activeTasks.remove(player.getUniqueId());
        if (task != null) {
            task.cancel();
        }
        removeMarkerEntities(player.getUniqueId());
    }

    public void cleanupAll() {
        for (BukkitRunnable task : activeTasks.values()) {
            task.cancel();
        }
        activeTasks.clear();
        // Remove all marker entities for all players
        for (UUID playerId : new HashSet<>(activeMarkerEntities.keySet())) {
            removeMarkerEntities(playerId);
        }
    }

    /**
     * Spawn a BlockDisplay marker at a location with a TextDisplay label.
     */
    private void spawnMarkerBlock(Player player, Location loc, Material material, String label, float scale) {
        if (loc == null || loc.getWorld() == null) return;

        UUID playerId = player.getUniqueId();

        // Spawn BlockDisplay (small floating block)
        Location markerLoc = loc.clone().add(0, 1.2, 0);
        markerLoc.setYaw(0);
        markerLoc.setPitch(0);

        BlockDisplay blockDisplay = (BlockDisplay) loc.getWorld().spawnEntity(markerLoc, EntityType.BLOCK_DISPLAY);
        blockDisplay.setBlock(material.createBlockData());
        blockDisplay.setTransformation(new Transformation(
                new Vector3f(-scale / 2, 0, -scale / 2), // Center the block
                new AxisAngle4f(0, 0, 1, 0),
                new Vector3f(scale, scale, scale),
                new AxisAngle4f(0, 0, 1, 0)
        ));
        blockDisplay.setGlowing(true);
        blockDisplay.setGlowColorOverride(getGlowColor(material));

        activeMarkerEntities.computeIfAbsent(playerId, k -> new ArrayList<>()).add(blockDisplay);

        // Spawn TextDisplay label above the block
        Location labelLoc = markerLoc.clone().add(0, scale + 0.3, 0);
        TextDisplay textDisplay = (TextDisplay) loc.getWorld().spawnEntity(labelLoc, EntityType.TEXT_DISPLAY);
        textDisplay.text(MessageUtil.colorize(label));
        textDisplay.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);
        textDisplay.setShadowed(true);
        textDisplay.setBackgroundColor(org.bukkit.Color.fromARGB(128, 0, 0, 0));

        activeLabelEntities.computeIfAbsent(playerId, k -> new ArrayList<>()).add(textDisplay);
    }

    /**
     * Get glow color based on material type for visual distinction.
     */
    private org.bukkit.Color getGlowColor(Material material) {
        return switch (material) {
            case BEACON -> org.bukkit.Color.FUCHSIA;
            case REDSTONE_BLOCK -> org.bukkit.Color.RED;
            case LAVA -> org.bukkit.Color.fromRGB(180, 50, 0);
            default -> org.bukkit.Color.WHITE;
        };
    }

    /**
     * Remove all BlockDisplay and TextDisplay marker entities for a player.
     */
    private void removeMarkerEntities(UUID playerId) {
        List<BlockDisplay> blocks = activeMarkerEntities.remove(playerId);
        if (blocks != null) {
            for (BlockDisplay bd : blocks) {
                if (bd != null && !bd.isDead()) bd.remove();
            }
        }
        List<TextDisplay> labels = activeLabelEntities.remove(playerId);
        if (labels != null) {
            for (TextDisplay td : labels) {
                if (td != null && !td.isDead()) td.remove();
            }
        }
    }

    /**
     * Draw region wireframe using particles (border only).
     * Particles are player-specific and don't leave entities in the world.
     */
    private void drawRegionBox(Player player, Location pos1, Location pos2) {
        if (!pos1.getWorld().equals(pos2.getWorld())) return;

        double minX = Math.min(pos1.getX(), pos2.getX());
        double minY = Math.min(pos1.getY(), pos2.getY());
        double minZ = Math.min(pos1.getZ(), pos2.getZ());
        double maxX = Math.max(pos1.getX(), pos2.getX()) + 1;
        double maxY = Math.max(pos1.getY(), pos2.getY()) + 1;
        double maxZ = Math.max(pos1.getZ(), pos2.getZ()) + 1;

        Particle.DustOptions dust = new Particle.DustOptions(Color.AQUA, 1.0f);

        // Draw bottom and top rectangles
        for (double x = minX; x <= maxX; x += 1) {
            player.spawnParticle(Particle.DUST, x, minY, minZ, 1, 0, 0, 0, 0, dust);
            player.spawnParticle(Particle.DUST, x, minY, maxZ, 1, 0, 0, 0, 0, dust);
            player.spawnParticle(Particle.DUST, x, maxY, minZ, 1, 0, 0, 0, 0, dust);
            player.spawnParticle(Particle.DUST, x, maxY, maxZ, 1, 0, 0, 0, 0, dust);
        }
        for (double z = minZ; z <= maxZ; z += 1) {
            player.spawnParticle(Particle.DUST, minX, minY, z, 1, 0, 0, 0, 0, dust);
            player.spawnParticle(Particle.DUST, maxX, minY, z, 1, 0, 0, 0, 0, dust);
            player.spawnParticle(Particle.DUST, minX, maxY, z, 1, 0, 0, 0, 0, dust);
            player.spawnParticle(Particle.DUST, maxX, maxY, z, 1, 0, 0, 0, 0, dust);
        }
        // Draw vertical pillars
        for (double y = minY; y <= maxY; y += 1) {
            player.spawnParticle(Particle.DUST, minX, y, minZ, 1, 0, 0, 0, 0, dust);
            player.spawnParticle(Particle.DUST, maxX, y, minZ, 1, 0, 0, 0, 0, dust);
            player.spawnParticle(Particle.DUST, minX, y, maxZ, 1, 0, 0, 0, 0, dust);
            player.spawnParticle(Particle.DUST, maxX, y, maxZ, 1, 0, 0, 0, 0, dust);
        }
    }
}

package com.wibudungeon.core.dungeon;

import com.wibudungeon.core.util.MessageUtil;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.*;

/**
 * Manages visual markers during setup mode.
 *
 * v1.0.7: Individual per-spawn markers, stronger particles, color-coded corners.
 * v1.0.8: Added PersistentDataContainer tags on mob/boss BlockDisplay markers so
 *         SetupListener can detect right-clicks and remove the corresponding spawn entry.
 *
 * PDC keys on each mob/boss marker:
 *   wd_marker_type  (STRING) → "mob" | "boss"
 *   wd_marker_owner (STRING) → player UUID
 *   wd_marker_wave  (INTEGER) → wave number
 *   wd_marker_index (INTEGER) → spawn list index (0-based)
 */
public class MarkerManager {

    // PDC keys — public so SetupListener can read them
    public static NamespacedKey KEY_MARKER_TYPE;
    public static NamespacedKey KEY_MARKER_OWNER;
    public static NamespacedKey KEY_MARKER_WAVE;
    public static NamespacedKey KEY_MARKER_INDEX;

    private final Plugin plugin;
    private final SetupManager setupManager;
    private final Map<UUID, BukkitRunnable> activeTasks = new HashMap<>();
    private final Map<UUID, List<BlockDisplay>> activeMarkerEntities = new HashMap<>();
    private final Map<UUID, List<TextDisplay>> activeLabelEntities = new HashMap<>();
    private final Map<UUID, List<org.bukkit.entity.Interaction>> activeInteractionEntities = new HashMap<>();
    private final Map<UUID, Integer> lastStateHash = new HashMap<>();

    public MarkerManager(Plugin plugin, SetupManager setupManager) {
        this.plugin = plugin;
        this.setupManager = setupManager;
        // Initialise keys using the plugin instance
        KEY_MARKER_TYPE  = new NamespacedKey(plugin, "wd_marker_type");
        KEY_MARKER_OWNER = new NamespacedKey(plugin, "wd_marker_owner");
        KEY_MARKER_WAVE  = new NamespacedKey(plugin, "wd_marker_wave");
        KEY_MARKER_INDEX = new NamespacedKey(plugin, "wd_marker_index");
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

                // Only recreate entities when state actually changes
                int currentHash = computeStateHash(session);
                Integer previousHash = lastStateHash.get(player.getUniqueId());
                if (previousHash != null && previousHash == currentHash) {
                    // State unchanged — just redraw fading particles
                    if (session.getPos1() != null && session.getPos2() != null) {
                        drawRegionBox(player, session.getPos1(), session.getPos2());
                    }
                    return;
                }
                lastStateHash.put(player.getUniqueId(), currentHash);

                // Remove old markers and recreate fresh
                removeMarkerEntities(player.getUniqueId());

                // Region particles
                if (session.getPos1() != null && session.getPos2() != null) {
                    drawRegionBox(player, session.getPos1(), session.getPos2());
                }

                // Spawn point marker
                if (session.getSpawnPoint() != null) {
                    spawnMarkerBlock(player, session.getSpawnPoint(),
                            Material.BEACON, "&d&l⊕ Spawn Point", 0.4f);
                }

                // Entry point marker (STATIC dungeons)
                if (session.isStatic() && session.getEntryPoint() != null) {
                    spawnMarkerBlock(player, session.getEntryPoint(),
                            Material.CRYING_OBSIDIAN, "&5&l⚡ Entry Point", 0.4f);
                }

                // Wave-specific markers
                int waveNum = session.getSelectedWave();
                SetupSession.WaveData wave = session.getWave(waveNum);

                if (wave != null) {
                    // Boss spawn markers — GOLD_BLOCK, tagged with PDC
                    List<SetupSession.BossSpawnEntry> bossSpawns = wave.getBossSpawns();
                    for (int i = 0; i < bossSpawns.size(); i++) {
                        SetupSession.BossSpawnEntry boss = bossSpawns.get(i);
                        if (boss.getLocation() == null) continue;
                        String label = boss.isAssigned()
                                ? "&4&l☠ Boss #" + (i + 1) + ": &c" + boss.getBossId()
                                : "&4&l☠ Boss Spawn #" + (i + 1) + " &7[Unassigned] &c(click to remove)";
                        spawnMarkerBlock(player, boss.getLocation(),
                                Material.GOLD_BLOCK, label, 0.45f);
                        // Spawn clickable Interaction entity with PDC tags
                        spawnMarkerInteraction(player, boss.getLocation(), "boss", waveNum, i);
                    }

                    // Mob spawn markers — EMERALD_BLOCK, tagged with PDC
                    List<SetupSession.MobSpawnEntry> mobSpawns = wave.getMobSpawns();
                    for (int i = 0; i < mobSpawns.size(); i++) {
                        SetupSession.MobSpawnEntry entry = mobSpawns.get(i);
                        if (entry.getLocation() == null) continue;
                        String label = entry.isAssigned()
                                ? "&a&l⬟ Mob #" + (i + 1) + ": &e" + entry.getMobId() + " x" + entry.getCount()
                                : "&a&l⬟ Mob Spawn #" + (i + 1) + " &7[Unassigned] &c(click to remove)";
                        spawnMarkerBlock(player, entry.getLocation(),
                                Material.EMERALD_BLOCK, label, 0.35f);
                        // Spawn clickable Interaction entity with PDC tags
                        spawnMarkerInteraction(player, entry.getLocation(), "mob", waveNum, i);
                    }
                }
            }
        };

        task.runTaskTimer(plugin, 0L, 20L);
        activeTasks.put(player.getUniqueId(), task);
    }

    public void stopShowingMarkers(Player player) {
        BukkitRunnable task = activeTasks.remove(player.getUniqueId());
        if (task != null) task.cancel();
        removeMarkerEntities(player.getUniqueId());
        lastStateHash.remove(player.getUniqueId());
    }

    public void cleanupAll() {
        for (BukkitRunnable task : activeTasks.values()) task.cancel();
        activeTasks.clear();
        lastStateHash.clear();
        for (UUID playerId : new HashSet<>(activeMarkerEntities.keySet())) {
            removeMarkerEntities(playerId);
        }
    }

    /**
     * Force state refresh on next tick for a player.
     * Call this after removing a spawn so the marker disappears immediately.
     */
    public void forceRefresh(UUID playerId) {
        lastStateHash.remove(playerId);
    }

    // ===== PRIVATE HELPERS =====

    private int computeStateHash(SetupSession session) {
        int hash = 17;
        hash = 31 * hash + (session.getPos1()       != null ? session.getPos1().hashCode()       : 0);
        hash = 31 * hash + (session.getPos2()       != null ? session.getPos2().hashCode()       : 0);
        hash = 31 * hash + (session.getSpawnPoint() != null ? session.getSpawnPoint().hashCode() : 0);
        hash = 31 * hash + (session.getEntryPoint() != null ? session.getEntryPoint().hashCode() : 0);
        hash = 31 * hash + session.getSelectedWave();
        SetupSession.WaveData wave = session.getWave(session.getSelectedWave());
        if (wave != null) {
            hash = 31 * hash + wave.getMobSpawns().size();
            hash = 31 * hash + wave.getBossSpawns().size();
            for (SetupSession.MobSpawnEntry e : wave.getMobSpawns()) {
                hash = 31 * hash + (e.getMobId() != null ? e.getMobId().hashCode() : 0);
                hash = 31 * hash + e.getCount();
            }
            for (SetupSession.BossSpawnEntry e : wave.getBossSpawns()) {
                hash = 31 * hash + (e.getBossId() != null ? e.getBossId().hashCode() : 0);
            }
        }
        return hash;
    }

    /**
     * Spawn a BlockDisplay marker and an associated TextDisplay label.
     * Returns the BlockDisplay so callers can set PDC tags on it.
     */
    private BlockDisplay spawnMarkerBlock(Player player, Location loc,
                                          Material material, String label, float scale) {
        if (loc == null || loc.getWorld() == null) return null;

        UUID playerId = player.getUniqueId();

        Location markerLoc = loc.clone().add(0, 1.2, 0);
        markerLoc.setYaw(0);
        markerLoc.setPitch(0);

        BlockDisplay blockDisplay = (BlockDisplay) loc.getWorld()
                .spawnEntity(markerLoc, EntityType.BLOCK_DISPLAY);
        blockDisplay.setBlock(material.createBlockData());
        blockDisplay.setTransformation(new Transformation(
                new Vector3f(-scale / 2, 0, -scale / 2),
                new AxisAngle4f(0, 0, 1, 0),
                new Vector3f(scale, scale, scale),
                new AxisAngle4f(0, 0, 1, 0)
        ));
        blockDisplay.setGlowing(true);
        blockDisplay.setGlowColorOverride(getGlowColor(material));
        blockDisplay.setPersistent(false);

        activeMarkerEntities.computeIfAbsent(playerId, k -> new ArrayList<>()).add(blockDisplay);

        // TextDisplay label
        Location labelLoc = markerLoc.clone().add(0, scale + 0.3, 0);
        TextDisplay textDisplay = (TextDisplay) loc.getWorld()
                .spawnEntity(labelLoc, EntityType.TEXT_DISPLAY);
        textDisplay.text(MessageUtil.colorize(label));
        textDisplay.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);
        textDisplay.setShadowed(true);
        textDisplay.setBackgroundColor(org.bukkit.Color.fromARGB(128, 0, 0, 0));
        textDisplay.setPersistent(false);

        activeLabelEntities.computeIfAbsent(playerId, k -> new ArrayList<>()).add(textDisplay);

        return blockDisplay;
    }

    /**
     * Spawn an invisible Interaction entity at the marker location so that
     * PlayerInteractAtEntityEvent fires when the player right-clicks it.
     * PDC tags are placed on this entity for SetupListener to read.
     */
    private void spawnMarkerInteraction(Player player, Location loc, String type, int wave, int index) {
        if (loc == null || loc.getWorld() == null) return;
        Location interLoc = loc.clone().add(0.5, 1.2, 0.5);
        org.bukkit.entity.Interaction interaction = (org.bukkit.entity.Interaction) loc.getWorld()
                .spawnEntity(interLoc, EntityType.INTERACTION);
        interaction.setInteractionWidth(0.8f);
        interaction.setInteractionHeight(1.0f);
        interaction.setPersistent(false);
        interaction.getPersistentDataContainer()
                .set(KEY_MARKER_TYPE,  PersistentDataType.STRING,  type);
        interaction.getPersistentDataContainer()
                .set(KEY_MARKER_OWNER, PersistentDataType.STRING,  player.getUniqueId().toString());
        interaction.getPersistentDataContainer()
                .set(KEY_MARKER_WAVE,  PersistentDataType.INTEGER, wave);
        interaction.getPersistentDataContainer()
                .set(KEY_MARKER_INDEX, PersistentDataType.INTEGER, index);
        activeInteractionEntities.computeIfAbsent(player.getUniqueId(), k -> new ArrayList<>()).add(interaction);
    }

    private org.bukkit.Color getGlowColor(Material material) {
        return switch (material) {
            case BEACON         -> org.bukkit.Color.FUCHSIA;
            case EMERALD_BLOCK  -> org.bukkit.Color.GREEN;
            case GOLD_BLOCK     -> org.bukkit.Color.ORANGE;
            case CRYING_OBSIDIAN -> org.bukkit.Color.PURPLE;
            default             -> org.bukkit.Color.WHITE;
        };
    }

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
        List<org.bukkit.entity.Interaction> interactions = activeInteractionEntities.remove(playerId);
        if (interactions != null) {
            for (org.bukkit.entity.Interaction inter : interactions) {
                if (inter != null && !inter.isDead()) inter.remove();
            }
        }
    }

    /**
     * Draw region wireframe with particles (border only).
     */
    private void drawRegionBox(Player player, Location pos1, Location pos2) {
        if (!pos1.getWorld().equals(pos2.getWorld())) return;

        double minX = Math.min(pos1.getX(), pos2.getX());
        double minY = Math.min(pos1.getY(), pos2.getY());
        double minZ = Math.min(pos1.getZ(), pos2.getZ());
        double maxX = Math.max(pos1.getX(), pos2.getX()) + 1;
        double maxY = Math.max(pos1.getY(), pos2.getY()) + 1;
        double maxZ = Math.max(pos1.getZ(), pos2.getZ()) + 1;

        Particle.DustOptions borderDust = new Particle.DustOptions(Color.AQUA,  1.5f);
        Particle.DustOptions pos1Dust   = new Particle.DustOptions(Color.GREEN, 2.0f);
        Particle.DustOptions pos2Dust   = new Particle.DustOptions(Color.RED,   2.0f);

        double step = 0.5;

        for (double x = minX; x <= maxX; x += step) {
            player.spawnParticle(Particle.DUST, x, minY, minZ, 1, 0,0,0,0, borderDust);
            player.spawnParticle(Particle.DUST, x, minY, maxZ, 1, 0,0,0,0, borderDust);
            player.spawnParticle(Particle.DUST, x, maxY, minZ, 1, 0,0,0,0, borderDust);
            player.spawnParticle(Particle.DUST, x, maxY, maxZ, 1, 0,0,0,0, borderDust);
        }
        for (double z = minZ; z <= maxZ; z += step) {
            player.spawnParticle(Particle.DUST, minX, minY, z, 1, 0,0,0,0, borderDust);
            player.spawnParticle(Particle.DUST, maxX, minY, z, 1, 0,0,0,0, borderDust);
            player.spawnParticle(Particle.DUST, minX, maxY, z, 1, 0,0,0,0, borderDust);
            player.spawnParticle(Particle.DUST, maxX, maxY, z, 1, 0,0,0,0, borderDust);
        }
        for (double y = minY; y <= maxY; y += step) {
            player.spawnParticle(Particle.DUST, minX, y, minZ, 1, 0,0,0,0, borderDust);
            player.spawnParticle(Particle.DUST, maxX, y, minZ, 1, 0,0,0,0, borderDust);
            player.spawnParticle(Particle.DUST, minX, y, maxZ, 1, 0,0,0,0, borderDust);
            player.spawnParticle(Particle.DUST, maxX, y, maxZ, 1, 0,0,0,0, borderDust);
        }

        // Pos1 corner — green pillar
        double p1x = pos1.getBlockX();
        double p1y = pos1.getBlockY();
        double p1z = pos1.getBlockZ();
        for (double y = p1y; y <= p1y + 3 && y <= maxY; y += 0.3) {
            player.spawnParticle(Particle.DUST, p1x + 0.5, y, p1z + 0.5, 1, 0,0,0,0, pos1Dust);
        }

        // Pos2 corner — red pillar
        double p2x = pos2.getBlockX() + 1;
        double p2y = pos2.getBlockY();
        double p2z = pos2.getBlockZ() + 1;
        for (double y = p2y; y <= p2y + 3 && y <= maxY; y += 0.3) {
            player.spawnParticle(Particle.DUST, p2x - 0.5, y, p2z - 0.5, 1, 0,0,0,0, pos2Dust);
        }
    }
}

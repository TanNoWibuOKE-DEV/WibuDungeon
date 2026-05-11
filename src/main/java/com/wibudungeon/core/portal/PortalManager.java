package com.wibudungeon.core.portal;

import com.wibudungeon.core.config.ConfigManager;
import com.wibudungeon.core.dungeon.Dungeon;
import com.wibudungeon.core.util.MessageUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Manages portal spawning, display (6×4 obsidian frame + nether portal), expiry, and tracking.
 *
 * v1.0.6: Fixed portal despawn — portals now properly clean up all display entities
 * on timeout, with isUsed tracking and defensive particle task cancellation.
 */
public class PortalManager {

    private final Plugin plugin;
    private final ConfigManager configManager;
    private final Map<UUID, DungeonPortal> activePortals = new HashMap<>();
    private BukkitTask spawnTask;
    private BukkitTask expiryTask;

    public PortalManager(Plugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    public void startScheduler() {
        int intervalTicks = configManager.getPortalSpawnInterval() * 20;
        int firstDelay = Math.max(intervalTicks / 2, 200);
        spawnTask = Bukkit.getScheduler().runTaskTimer(plugin, this::trySpawnPortal, firstDelay, intervalTicks);
        expiryTask = Bukkit.getScheduler().runTaskTimer(plugin, this::checkExpiredPortals, 100L, 100L);
        plugin.getLogger().info("Portal scheduler started! Interval: " + configManager.getPortalSpawnInterval() + "s");
    }

    public void stopScheduler() {
        if (spawnTask != null) spawnTask.cancel();
        if (expiryTask != null) expiryTask.cancel();
        removeAllPortals();
    }

    private void trySpawnPortal() {
        if (activePortals.size() >= configManager.getMaxActivePortals()) return;
        List<String> worldNames = configManager.getPortalWorlds();
        if (worldNames.isEmpty()) return;

        String worldName = worldNames.get(ThreadLocalRandom.current().nextInt(worldNames.size()));
        World world = Bukkit.getWorld(worldName);
        if (world == null) return;

        Map<String, Dungeon> dungeons = configManager.getDungeons();
        List<Dungeon> enabled = dungeons.values().stream().filter(d -> d.isEnabled() && d.isValid()).toList();
        if (enabled.isEmpty()) return;

        Dungeon dungeon = enabled.get(ThreadLocalRandom.current().nextInt(enabled.size()));
        Location spawnLoc = findRandomLocation(world);
        if (spawnLoc == null) return;

        spawnPortal(spawnLoc, dungeon.getId());
    }

    /**
     * Spawn a portal with 6-tall × 4-wide obsidian frame and nether portal center.
     */
    public DungeonPortal spawnPortal(Location location, String dungeonId) {
        long duration = configManager.getPortalDuration() * 1000L;
        DungeonPortal portal = new DungeonPortal(location, dungeonId, duration);

        World world = location.getWorld();
        if (world == null) return null;

        // Zero yaw/pitch
        location.setYaw(0);
        location.setPitch(0);

        buildPortalFrame(portal, location, world);

        // Interaction entity covering the 4×5 portal
        Location interLoc = new Location(world, location.getX() + 1.5, location.getY(), location.getZ());
        Interaction interaction = (Interaction) world.spawnEntity(interLoc, EntityType.INTERACTION);
        interaction.setInteractionWidth(4.0f);
        interaction.setInteractionHeight(5.0f);
        portal.setInteractionEntity(interaction);

        activePortals.put(portal.getPortalId(), portal);

        // Announce with clickable tracking
        if (configManager.isPortalAnnounce()) {
            announcePortal(portal, world);
        }

        spawnParticles(portal);

        plugin.getLogger().info("Portal spawned for dungeon '" + dungeonId + "' at "
                + location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ()
                + " (expires in " + configManager.getPortalDuration() + "s)");

        return portal;
    }

    /**
     * Build portal to look EXACTLY like a vanilla Nether Portal.
     *
     * [OBS][OBS][OBS][OBS]    ← y=4 (top)
     * [OBS][PRT][PRT][OBS]    ← y=3
     * [OBS][PRT][PRT][OBS]    ← y=2
     * [OBS][PRT][PRT][OBS]    ← y=1
     * [OBS][OBS][OBS][OBS]    ← y=0 (bottom)
     *
     * Obsidian = full 1×1×1 blocks
     * Portal center = NETHER_PORTAL block (purple swirl)
     */
    private void buildPortalFrame(DungeonPortal portal, Location origin, World world) {
        BlockData obsidian = Material.OBSIDIAN.createBlockData();
        BlockData netherPortal = Material.NETHER_PORTAL.createBlockData();

        // Bottom row (y=0): 4 full obsidian
        for (int x = 0; x < 4; x++) spawnObsidian(portal, world, origin, x, 0, obsidian);

        // Top row (y=4): 4 full obsidian
        for (int x = 0; x < 4; x++) spawnObsidian(portal, world, origin, x, 4, obsidian);

        // Left column (x=0, y=1..3)
        for (int y = 1; y <= 3; y++) spawnObsidian(portal, world, origin, 0, y, obsidian);

        // Right column (x=3, y=1..3)
        for (int y = 1; y <= 3; y++) spawnObsidian(portal, world, origin, 3, y, obsidian);

        // Inner portal (2 wide × 3 tall = NETHER_PORTAL blocks)
        for (int x = 1; x <= 2; x++) {
            for (int y = 1; y <= 3; y++) {
                spawnPortalBlock(portal, world, origin, x, y, netherPortal);
            }
        }
    }

    /**
     * Spawn a full-size obsidian block (1×1×1).
     */
    private BlockDisplay spawnObsidian(DungeonPortal portal, World world, Location origin,
                                        int xOff, int yOff, BlockData data) {
        Location loc = new Location(world, origin.getX() + xOff, origin.getY() + yOff, origin.getZ());
        loc.setYaw(0);
        loc.setPitch(0);
        BlockDisplay display = (BlockDisplay) world.spawnEntity(loc, EntityType.BLOCK_DISPLAY);
        display.setBlock(data);
        // Full block — no scaling needed, identity transform
        display.setTransformation(new Transformation(
                new Vector3f(0, 0, 0),
                new AxisAngle4f(0, 0, 1, 0),
                new Vector3f(1, 1, 1),
                new AxisAngle4f(0, 0, 1, 0)
        ));
        display.setGlowing(true);
        portal.addDisplayEntity(display);
        return display;
    }

    /**
     * Spawn a nether portal center block (thin, like real portal).
     */
    private BlockDisplay spawnPortalBlock(DungeonPortal portal, World world, Location origin,
                                           int xOff, int yOff, BlockData data) {
        Location loc = new Location(world, origin.getX() + xOff, origin.getY() + yOff, origin.getZ());
        loc.setYaw(0);
        loc.setPitch(0);
        BlockDisplay display = (BlockDisplay) world.spawnEntity(loc, EntityType.BLOCK_DISPLAY);
        display.setBlock(data);
        // Nether portal is naturally thin on the Z axis
        display.setTransformation(new Transformation(
                new Vector3f(0, 0, 0.25f),  // Center on Z
                new AxisAngle4f(0, 0, 1, 0),
                new Vector3f(1, 1, 0.5f),   // Half-depth like real portal
                new AxisAngle4f(0, 0, 1, 0)
        ));
        display.setGlowing(true);
        portal.addDisplayEntity(display);
        return display;
    }

    /**
     * Announce portal with clickable [CLICK TO TRACK] text using Adventure API.
     */
    private void announcePortal(DungeonPortal portal, World world) {
        int x = portal.getLocation().getBlockX();
        int y = portal.getLocation().getBlockY();
        int z = portal.getLocation().getBlockZ();

        // Title
        Component title = Component.text("⚔ DUNGEON PORTAL ⚔")
                .color(NamedTextColor.GOLD).decorate(TextDecoration.BOLD);
        Component subtitle = Component.text("x:" + x + " y:" + y + " z:" + z)
                .color(NamedTextColor.YELLOW);

        // Clickable chat message
        Component coordText = Component.text("A Dungeon Portal has spawned at ")
                .color(NamedTextColor.GOLD)
                .append(Component.text("(" + x + ", " + y + ", " + z + ") ")
                        .color(NamedTextColor.YELLOW));

        Component trackButton = Component.text("[CLICK TO TRACK]")
                .color(NamedTextColor.GREEN)
                .decorate(TextDecoration.BOLD)
                .clickEvent(ClickEvent.runCommand("/wd track " + portal.getDungeonId()))
                .hoverEvent(HoverEvent.showText(
                        Component.text("Click to track this portal!").color(NamedTextColor.AQUA)));

        Component fullMessage = coordText.append(trackButton);

        for (Player player : world.getPlayers()) {
            player.showTitle(Title.title(title, subtitle,
                    Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(4), Duration.ofMillis(1000))));
            player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_DEATH, 1.0f, 1.2f);
            player.sendMessage(fullMessage);
        }
    }

    /**
     * Spawn ambient particles around a portal.
     * Task auto-cancels when portal is expired, invalid, or removed.
     */
    private void spawnParticles(DungeonPortal portal) {
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            // Defensive checks — cancel immediately if portal is gone
            if (portal.isExpired() || !portal.isValid() || !activePortals.containsKey(portal.getPortalId())) {
                task.cancel();
                return;
            }
            Location center = portal.getCenter();
            if (center.getWorld() != null) {
                center.getWorld().spawnParticle(Particle.PORTAL, center, 40, 1.0, 1.5, 0.3, 0.1);
                center.getWorld().spawnParticle(Particle.ENCHANT, center, 15, 0.8, 1.2, 0.3, 1.0);
            }
        }, 0L, 10L);
    }

    /**
     * Check and remove expired portals.
     * Ensures ALL display entities and interaction entities are fully cleaned up.
     */
    private void checkExpiredPortals() {
        Iterator<Map.Entry<UUID, DungeonPortal>> it = activePortals.entrySet().iterator();
        while (it.hasNext()) {
            DungeonPortal portal = it.next().getValue();
            if (portal.isExpired()) {
                // v1.0.8 fix: Skip portals that are actively being used (player opened GUI)
                // to prevent race condition where portal is removed before dungeon starts.
                if (portal.isUsed()) continue;

                // Full cleanup of all entities
                portal.remove();
                it.remove();

                plugin.getLogger().info("Portal expired and removed: " + portal.getPortalId()
                        + " (dungeon: " + portal.getDungeonId() + ", used: " + portal.isUsed() + ")");

                if (configManager.isPortalAnnounce() && portal.getLocation().getWorld() != null) {
                    for (Player p : portal.getLocation().getWorld().getPlayers()) {
                        MessageUtil.send(p, configManager.getMessage("portal.expired"));
                    }
                }
            }
        }
    }

    /**
     * Mark a portal as used (player entered it).
     */
    public void markPortalUsed(UUID portalId) {
        DungeonPortal portal = activePortals.get(portalId);
        if (portal != null) {
            portal.markUsed();
        }
    }

    public void removePortal(UUID portalId) {
        DungeonPortal portal = activePortals.remove(portalId);
        if (portal != null) {
            portal.remove();
            plugin.getLogger().info("Portal manually removed: " + portalId);
        }
    }

    public void removeAllPortals() {
        for (DungeonPortal p : activePortals.values()) p.remove();
        activePortals.clear();
        plugin.getLogger().info("All portals removed.");
    }

    public DungeonPortal getPortalByInteraction(UUID entityId) {
        for (DungeonPortal portal : activePortals.values()) {
            if (portal.getInteractionEntity() != null &&
                    portal.getInteractionEntity().getUniqueId().equals(entityId))
                return portal;
        }
        return null;
    }

    public DungeonPortal getPortalById(UUID portalId) {
        return activePortals.get(portalId);
    }

    public DungeonPortal getNearbyPortal(Location playerLoc, double range) {
        for (DungeonPortal portal : activePortals.values()) {
            Location center = portal.getCenter();
            if (center.getWorld() != null && center.getWorld().equals(playerLoc.getWorld())) {
                if (center.distanceSquared(playerLoc) <= range * range) return portal;
            }
        }
        return null;
    }

    private Location findRandomLocation(World world) {
        Location spawn = world.getSpawnLocation();
        int min = configManager.getPortalMinDistance(), max = configManager.getPortalMaxDistance();
        for (int attempt = 0; attempt < 50; attempt++) {
            double angle = ThreadLocalRandom.current().nextDouble() * Math.PI * 2;
            int dist = ThreadLocalRandom.current().nextInt(min, max + 1);
            int x = spawn.getBlockX() + (int) (Math.cos(angle) * dist);
            int z = spawn.getBlockZ() + (int) (Math.sin(angle) * dist);
            if (!world.isChunkLoaded(x >> 4, z >> 4)) world.loadChunk(x >> 4, z >> 4);
            int y = world.getHighestBlockYAt(x, z);
            if (y <= 0 || y >= world.getMaxHeight() - 10) continue;
            Location ground = new Location(world, x, y, z);
            Material gt = ground.getBlock().getType();
            if (gt == Material.WATER || gt == Material.LAVA || gt.name().contains("LEAVES") || gt == Material.AIR)
                continue;
            Location loc = new Location(world, x + 0.5, y + 1, z + 0.5);
            if (loc.getBlock().getType() == Material.AIR) return loc;
        }
        return null;
    }

    public Collection<DungeonPortal> getActivePortals() {
        return Collections.unmodifiableCollection(activePortals.values());
    }

    public Plugin getPlugin() { return plugin; }
}

package com.wibudungeon.core.portal;

import com.wibudungeon.core.config.ConfigManager;
import com.wibudungeon.core.dungeon.Dungeon;
import com.wibudungeon.core.dungeon.DungeonType;
import com.wibudungeon.core.gui.JoinGUI;
import com.wibudungeon.core.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.*;

/**
 * Manages permanent entry portals for STATIC dungeons.
 *
 * Behavior:
 * - On load, spawns BlockDisplay + Interaction entities at each STATIC dungeon's entry point
 * - Entities persist until plugin disable or reload
 * - Player interacts with Interaction entity → opens JoinGUI
 * - Never despawns automatically
 *
 * @since v1.0.7
 */
public class StaticEntryManager implements Listener {

    private final Plugin plugin;
    private final ConfigManager configManager;
    private JoinGUI joinGUI;

    // Maps dungeonId → list of spawned display entities
    private final Map<String, List<Entity>> spawnedEntities = new HashMap<>();
    // Maps Interaction entity UUID → dungeonId
    private final Map<UUID, String> interactionToDungeon = new HashMap<>();

    public StaticEntryManager(Plugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    public void setJoinGUI(JoinGUI joinGUI) {
        this.joinGUI = joinGUI;
    }

    /**
     * Load all static dungeon entries — spawn display entities at entry points.
     * Call after configs are loaded.
     */
    public void loadAll() {
        // Clean up existing entities first
        cleanupAll();

        int count = 0;
        for (Dungeon dungeon : configManager.getDungeons().values()) {
            if (dungeon.getType() != DungeonType.STATIC) continue;
            if (dungeon.getEntryPoint() == null) continue;
            if (!dungeon.isEnabled()) continue;

            spawnEntry(dungeon);
            count++;
        }

        if (count > 0) {
            plugin.getLogger().info("Spawned " + count + " static dungeon entries.");
        }
    }

    /**
     * Spawn a static entry portal at a dungeon's entry point.
     */
    public void spawnEntry(Dungeon dungeon) {
        Location entryLoc = dungeon.getEntryPoint();
        if (entryLoc == null || entryLoc.getWorld() == null) return;

        // Remove old entry for this dungeon if exists
        removeEntry(dungeon.getId());

        List<Entity> entities = new ArrayList<>();

        // Spawn portal frame display (4 wide × 5 tall)
        for (int x = 0; x < 4; x++) {
            for (int y = 0; y < 5; y++) {
                boolean isFrame = x == 0 || x == 3 || y == 0 || y == 4;
                Material mat = isFrame ? Material.CRYING_OBSIDIAN : Material.NETHER_PORTAL;

                Location blockLoc = entryLoc.clone().add(x, y, 0);
                BlockDisplay display = (BlockDisplay) entryLoc.getWorld().spawnEntity(blockLoc, EntityType.BLOCK_DISPLAY);
                display.setBlock(mat.createBlockData());
                display.setTransformation(new Transformation(
                        new Vector3f(0, 0, 0),
                        new AxisAngle4f(0, 0, 1, 0),
                        new Vector3f(1, 1, 0.05f),
                        new AxisAngle4f(0, 0, 1, 0)
                ));
                display.setPersistent(false);
                if (!isFrame) {
                    display.setGlowing(true);
                    display.setGlowColorOverride(org.bukkit.Color.PURPLE);
                }
                entities.add(display);
            }
        }

        // Spawn text display label above portal
        Location labelLoc = entryLoc.clone().add(2, 5.5, 0);
        TextDisplay label = (TextDisplay) entryLoc.getWorld().spawnEntity(labelLoc, EntityType.TEXT_DISPLAY);
        String dungeonName = dungeon.getName() != null ? dungeon.getName() : dungeon.getId();
        label.text(MessageUtil.colorize("&6&l⚔ " + MessageUtil.stripColor(dungeonName) + " &7[STATIC]"));
        label.setBillboard(Display.Billboard.CENTER);
        label.setShadowed(true);
        label.setBackgroundColor(org.bukkit.Color.fromARGB(160, 0, 0, 0));
        label.setPersistent(false);
        entities.add(label);

        // Spawn Interaction entity for player click detection (centered on portal)
        Location interLoc = entryLoc.clone().add(1.5, 2.0, 0);
        Interaction interaction = (Interaction) entryLoc.getWorld().spawnEntity(interLoc, EntityType.INTERACTION);
        interaction.setInteractionWidth(4.5f);
        interaction.setInteractionHeight(5.5f);
        interaction.setPersistent(false);
        entities.add(interaction);

        // Track mappings
        spawnedEntities.put(dungeon.getId(), entities);
        interactionToDungeon.put(interaction.getUniqueId(), dungeon.getId());
    }

    /**
     * Remove the static entry portal for a dungeon.
     */
    public void removeEntry(String dungeonId) {
        List<Entity> entities = spawnedEntities.remove(dungeonId);
        if (entities != null) {
            for (Entity entity : entities) {
                if (entity instanceof Interaction) {
                    interactionToDungeon.remove(entity.getUniqueId());
                }
                if (entity != null && !entity.isDead()) {
                    entity.remove();
                }
            }
        }
    }

    /**
     * Handle player interaction with static entry portals.
     */
    @EventHandler
    public void onInteract(PlayerInteractEntityEvent event) {
        Entity clicked = event.getRightClicked();
        if (!(clicked instanceof Interaction)) return;

        String dungeonId = interactionToDungeon.get(clicked.getUniqueId());
        if (dungeonId == null) return;

        event.setCancelled(true);
        Player player = event.getPlayer();

        Dungeon dungeon = configManager.getDungeon(dungeonId);
        if (dungeon == null || !dungeon.isEnabled()) {
            MessageUtil.send(player, configManager.getPrefix() + "&cThis dungeon is not available!");
            return;
        }

        // Open the JoinGUI using a virtual DungeonPortal reference
        if (joinGUI != null) {
            joinGUI.openForStatic(player, dungeonId);
        }
    }

    /**
     * Clean up all spawned static entry entities.
     */
    public void cleanupAll() {
        for (List<Entity> entities : spawnedEntities.values()) {
            for (Entity entity : entities) {
                if (entity != null && !entity.isDead()) {
                    entity.remove();
                }
            }
        }
        spawnedEntities.clear();
        interactionToDungeon.clear();
    }

    /**
     * Reload all static entries (used on config reload).
     */
    public void reload() {
        loadAll();
    }

    /**
     * Check if a dungeon has an active static entry.
     */
    public boolean hasEntry(String dungeonId) {
        return spawnedEntities.containsKey(dungeonId);
    }
}

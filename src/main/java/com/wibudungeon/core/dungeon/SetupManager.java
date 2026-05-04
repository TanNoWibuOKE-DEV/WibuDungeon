package com.wibudungeon.core.dungeon;

import com.wibudungeon.core.config.ConfigManager;
import com.wibudungeon.core.util.ItemBuilder;
import com.wibudungeon.core.util.MessageUtil;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.*;

/**
 * Manages interactive setup sessions for dungeon configuration.
 * Admins enter setup mode to visually configure dungeons using hotbar tools.
 */
public class SetupManager {

    private final Plugin plugin;
    private final ConfigManager configManager;
    private final Map<UUID, SetupSession> activeSessions = new HashMap<>();
    private final MarkerManager markerManager;

    public SetupManager(Plugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.markerManager = new MarkerManager(plugin, this);
    }

    /**
     * Enter setup mode for a dungeon.
     */
    public boolean enterSetup(Player player, String dungeonId) {
        if (activeSessions.containsKey(player.getUniqueId())) {
            MessageUtil.send(player, configManager.getPrefix() + "&cYou're already in setup mode! Use the &eBarrier &cto exit.");
            return false;
        }

        SetupSession session = new SetupSession(player.getUniqueId(), dungeonId);

        // Load existing dungeon data if editing
        Dungeon existing = configManager.getDungeon(dungeonId);
        if (existing != null) {
            session.setPos1(existing.getPos1());
            session.setPos2(existing.getPos2());
            session.setSpawnPoint(existing.getSpawnPoint());
            // Load existing mob spawns to wave 1
            SetupSession.WaveData w1 = session.getOrCreateWave(1);
            for (var loc : existing.getMobSpawns()) {
                w1.addMobSpawn(loc, "ZOMBIE", 1);
            }
        }

        // Save and clear inventory
        session.setSavedInventory(player.getInventory().getContents().clone());
        session.setSavedGameMode(player.getGameMode());
        player.getInventory().clear();

        // Give hotbar tools
        giveHotbarTools(player);

        // Set creative for flying
        player.setGameMode(GameMode.CREATIVE);

        activeSessions.put(player.getUniqueId(), session);
        markerManager.startShowingMarkers(player);

        MessageUtil.send(player, configManager.getPrefix() + "&a&l✦ Setup Mode Activated! ✦");
        MessageUtil.send(player, configManager.getPrefix() + "&7Use the hotbar tools to configure dungeon &e" + dungeonId);
        MessageUtil.send(player, configManager.getPrefix() + "&7Right-click each tool to use it.");
        MessageUtil.send(player, configManager.getPrefix() + "&7Click &eSave &7when done, or &cBarrier &7to cancel.");
        return true;
    }

    /**
     * Exit setup mode without saving.
     */
    public void cancelSetup(Player player) {
        SetupSession session = activeSessions.remove(player.getUniqueId());
        if (session == null) return;

        markerManager.stopShowingMarkers(player);
        restorePlayer(player, session);
        MessageUtil.send(player, configManager.getPrefix() + "&cSetup cancelled. No changes saved.");
    }

    /**
     * Save and exit setup mode.
     */
    public boolean saveSetup(Player player) {
        SetupSession session = activeSessions.get(player.getUniqueId());
        if (session == null) return false;

        if (!session.isValid()) {
            MessageUtil.send(player, configManager.getPrefix() + "&cCannot save! Set pos1, pos2, spawn, and at least 1 mob spawn.");
            return false;
        }

        // Build dungeon from session
        Dungeon dungeon = new Dungeon(session.getDungeonId());
        dungeon.setName("&e" + session.getDungeonId());
        dungeon.setWorld(session.getPos1().getWorld().getName());
        dungeon.setPos1(session.getPos1());
        dungeon.setPos2(session.getPos2());
        dungeon.setSpawnPoint(session.getSpawnPoint());

        // Collect all unique mob spawn locations across all waves to satisfy the legacy global runtime
        List<org.bukkit.Location> allMobLocs = new ArrayList<>();
        for (SetupSession.WaveData wd : session.getWaves().values()) {
            for (SetupSession.MobSpawnEntry ms : wd.getMobSpawns()) {
                if (!allMobLocs.contains(ms.getLocation())) {
                    allMobLocs.add(ms.getLocation());
                }
            }
        }
        dungeon.setMobSpawns(allMobLocs);

        dungeon.setWaveSet(session.getDungeonId());
        configManager.saveDungeon(dungeon);

        // Save wave data to waves.yml to ensure runtime system functions without a rewrite
        for (var entry : session.getWaves().entrySet()) {
            SetupSession.WaveData wd = entry.getValue();
            for (var mob : wd.getMobs().entrySet()) {
                configManager.addMobToWave(session.getDungeonId(), entry.getKey(), mob.getKey(), mob.getValue());
            }
            if (wd.getBossId() != null) {
                configManager.setBossForWave(session.getDungeonId(), entry.getKey(), wd.getBossId(), wd.getBossHealth());
            }
            if (wd.getDifficulty() != null) {
                configManager.setDifficultyForWave(session.getDungeonId(), entry.getKey(), wd.getDifficulty());
            }
        }

        // Save wave data directly to dungeon file
        java.io.File dungeonFile = new java.io.File(plugin.getDataFolder(), "dungeons/" + session.getDungeonId() + ".yml");
        if (dungeonFile.exists()) {
            org.bukkit.configuration.file.YamlConfiguration config = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(dungeonFile);
            
            for (var entry : session.getWaves().entrySet()) {
                int waveNum = entry.getKey();
                SetupSession.WaveData wd = entry.getValue();
                String basePath = "waves." + waveNum + ".";
                
                // Mobs
                for (var mob : wd.getMobs().entrySet()) {
                    config.set(basePath + "mobs." + mob.getKey(), mob.getValue());
                }
                
                // Boss
                if (wd.getBossId() != null) {
                    config.set(basePath + "boss", true);
                    config.set(basePath + "boss-type", wd.getBossId());
                    config.set(basePath + "boss-health", wd.getBossHealth());
                }
                
                // Difficulty
                if (wd.getDifficulty() != null) {
                    config.set(basePath + "difficulty", wd.getDifficulty().name());
                }

                // Spawns
                List<String> mobSpawnLocs = new ArrayList<>();
                for (SetupSession.MobSpawnEntry ms : wd.getMobSpawns()) {
                    mobSpawnLocs.add(com.wibudungeon.core.util.LocationUtil.serialize(ms.getLocation()));
                }
                config.set(basePath + "mob-spawns", mobSpawnLocs);
                
                if (wd.getBossSpawnPoint() != null) {
                    config.set(basePath + "boss-spawn", com.wibudungeon.core.util.LocationUtil.serialize(wd.getBossSpawnPoint()));
                }
            }
            try {
                config.save(dungeonFile);
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to save waves to dungeon file: " + e.getMessage());
            }
        }

        // Exit setup mode
        activeSessions.remove(player.getUniqueId());
        markerManager.stopShowingMarkers(player);
        restorePlayer(player, session);

        MessageUtil.send(player, configManager.getPrefix() + "&a&l✦ Dungeon Saved Successfully! ✦");
        MessageUtil.send(player, configManager.getPrefix() + "&eDungeon &f" + session.getDungeonId() + " &eis ready.");
        return true;
    }

    private void giveHotbarTools(Player player) {
        // Slot 0: Region Wand
        player.getInventory().setItem(0, new ItemBuilder(Material.GOLDEN_AXE)
                .name("&6&l⚒ Region Wand")
                .lore("&7", "&e  Left-click &7→ Set Pos1", "&e  Right-click &7→ Set Pos2", "&7")
                .glow().build());

        // Slot 1: Start Point Tool
        player.getInventory().setItem(1, new ItemBuilder(Material.ENDER_PEARL)
                .name("&d&l⊕ Start Point Tool")
                .lore("&7", "&e  Right-click &7→ Set player spawn", "&7")
                .glow().build());

        // Slot 2: Mob Spawn Tool
        player.getInventory().setItem(2, new ItemBuilder(Material.ZOMBIE_HEAD)
                .name("&c&l☠ Mob Spawn Tool")
                .lore("&7", "&e  Right-click &7→ Add mob spawn for current wave", "&7")
                .glow().build());

        // Slot 3: Boss Spawn Tool
        player.getInventory().setItem(3, new ItemBuilder(Material.BLAZE_ROD)
                .name("&4&l🔥 Boss Spawn Tool")
                .lore("&7", "&e  Right-click &7→ Set boss spawn for current wave", "&7")
                .glow().build());

        // Slot 4: Save Wave Tool
        player.getInventory().setItem(4, new ItemBuilder(Material.NETHER_STAR)
                .name("&e&l⭐ Save Wave Tool")
                .lore("&7", "&e  Right-click &7→ Save current wave & advance to next", "&7")
                .glow().build());

        // Slot 5: Manage Wave Tool
        player.getInventory().setItem(5, new ItemBuilder(Material.BOOK)
                .name("&b&l📖 Manage Wave Tool")
                .lore("&7", "&e  Right-click &7→ Open Wave Management GUI", "&7")
                .glow().build());

        // Slot 6: Save Dungeon Tool
        player.getInventory().setItem(6, new ItemBuilder(Material.EMERALD_BLOCK)
                .name("&a&l✅ Save Dungeon Tool")
                .lore("&7", "&a  Right-click &7→ Save dungeon and finish", "&7")
                .glow().build());

        // Slot 7: Exit Setup Tool
        player.getInventory().setItem(7, new ItemBuilder(Material.BARRIER)
                .name("&c&l❌ Exit Setup Tool")
                .lore("&7", "&c  Right-click &7→ Exit without saving", "&7")
                .build());
    }

    /**
     * Restore player state after exiting setup mode.
     */
    private void restorePlayer(Player player, SetupSession session) {
        player.getInventory().clear();
        if (session.getSavedInventory() != null) {
            player.getInventory().setContents(session.getSavedInventory());
        }
        if (session.getSavedGameMode() != null) {
            player.setGameMode(session.getSavedGameMode());
        } else {
            player.setGameMode(GameMode.SURVIVAL);
        }
    }

    /**
     * Check if a player is in setup mode.
     */
    public boolean isInSetup(UUID playerId) {
        return activeSessions.containsKey(playerId);
    }

    /**
     * Get a player's setup session.
     */
    public SetupSession getSession(UUID playerId) {
        return activeSessions.get(playerId);
    }

    /**
     * Clean up all sessions (on plugin disable).
     */
    public void cleanupAll() {
        markerManager.cleanupAll();
        for (var entry : new HashMap<>(activeSessions).entrySet()) {
            Player player = org.bukkit.Bukkit.getPlayer(entry.getKey());
            if (player != null) {
                cancelSetup(player);
            }
        }
    }
}

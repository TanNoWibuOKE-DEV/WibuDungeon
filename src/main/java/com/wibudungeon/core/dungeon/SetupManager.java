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
            session.setDungeonType(existing.getType());
            session.setEntryPoint(existing.getEntryPoint());

            // v1.0.7: Load per-spawn entries from dungeon file
            java.io.File dungeonFile = new java.io.File(plugin.getDataFolder(), "dungeons/" + dungeonId + ".yml");
            if (dungeonFile.exists()) {
                org.bukkit.configuration.file.YamlConfiguration config =
                        org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(dungeonFile);

                if (config.isConfigurationSection("waves")) {
                    for (String waveKey : config.getConfigurationSection("waves").getKeys(false)) {
                        int waveNum;
                        try { waveNum = Integer.parseInt(waveKey); } catch (NumberFormatException e) { continue; }
                        SetupSession.WaveData wd = session.getOrCreateWave(waveNum);
                        String basePath = "waves." + waveKey + ".";

                        // Load per-spawn mob entries (v1.0.7 format)
                        if (config.isList(basePath + "mob-spawn-entries")) {
                            for (Object obj : config.getList(basePath + "mob-spawn-entries")) {
                                if (obj instanceof java.util.Map<?, ?> map) {
                                    Object locObj = map.get("location");
                                    Object mobObj = map.get("mob_id");
                                    Object amtObj = map.get("amount");
                                    String locStr = locObj != null ? String.valueOf(locObj) : "null";
                                    String mobId = mobObj != null ? String.valueOf(mobObj) : "";
                                    int amount = 1;
                                    try { if (amtObj != null) amount = Integer.parseInt(String.valueOf(amtObj)); }
                                    catch (NumberFormatException ignored) {}
                                    org.bukkit.Location loc = com.wibudungeon.core.util.LocationUtil.deserialize(locStr);
                                    if (mobId.isEmpty()) {
                                        wd.addMobSpawn(loc);
                                    } else {
                                        wd.addMobSpawn(loc, mobId, amount);
                                    }
                                }
                            }
                        } else if (config.isList(basePath + "mob-spawns")) {
                            // Fallback: legacy flat location list
                            for (String locStr : config.getStringList(basePath + "mob-spawns")) {
                                org.bukkit.Location loc = com.wibudungeon.core.util.LocationUtil.deserialize(locStr);
                                wd.addMobSpawn(loc);
                            }
                            // Assign mobs from legacy mobs map to unassigned spawns
                            if (config.isConfigurationSection(basePath + "mobs")) {
                                for (String mobId : config.getConfigurationSection(basePath + "mobs").getKeys(false)) {
                                    int count = config.getInt(basePath + "mobs." + mobId, 1);
                                    for (SetupSession.MobSpawnEntry ms : wd.getMobSpawns()) {
                                        if (!ms.isAssigned()) {
                                            ms.setMobId(mobId);
                                            ms.setCount(count);
                                            break;
                                        }
                                    }
                                }
                            }
                        }

                        // Load per-spawn boss entries (v1.0.7 format)
                        if (config.isList(basePath + "boss-spawn-entries")) {
                            for (Object obj : config.getList(basePath + "boss-spawn-entries")) {
                                if (obj instanceof java.util.Map<?, ?> map) {
                                    Object locObj = map.get("location");
                                    Object bossObj = map.get("boss_id");
                                    String locStr = locObj != null ? String.valueOf(locObj) : "null";
                                    String bossId = bossObj != null ? String.valueOf(bossObj) : "";
                                    org.bukkit.Location loc = com.wibudungeon.core.util.LocationUtil.deserialize(locStr);
                                    if (bossId.isEmpty()) {
                                        wd.addBossSpawn(loc);
                                    } else {
                                        wd.addBossSpawn(loc, bossId);
                                    }
                                }
                            }
                        } else {
                            // Fallback: legacy single boss spawn
                            String bossSpawnStr = config.getString(basePath + "boss-spawn");
                            String bossType = config.getString(basePath + "boss-type");
                            if (bossSpawnStr != null) {
                                org.bukkit.Location bossLoc = com.wibudungeon.core.util.LocationUtil.deserialize(bossSpawnStr);
                                if (bossType != null) {
                                    wd.addBossSpawn(bossLoc, bossType);
                                } else {
                                    wd.addBossSpawn(bossLoc);
                                }
                            }
                        }

                        // Load other wave properties
                        String diffStr = config.getString(basePath + "difficulty");
                        if (diffStr != null) {
                            try { wd.setDifficulty(Difficulty.valueOf(diffStr)); } catch (Exception ignored) {}
                        }
                        if (config.contains(basePath + "time-limit")) {
                            wd.setTimeLimit(config.getInt(basePath + "time-limit", 120));
                        }
                        if (config.contains(basePath + "bundle-id")) {
                            wd.setBundleId(config.getString(basePath + "bundle-id"));
                        }
                        if (config.contains(basePath + "chest-amount")) {
                            wd.setChestAmount(config.getInt(basePath + "chest-amount", 3));
                        }
                    }
                }
            }

            // If no waves were loaded from file, fallback to legacy mob spawns
            if (session.getWaves().isEmpty() && !existing.getMobSpawns().isEmpty()) {
                SetupSession.WaveData w1 = session.getOrCreateWave(1);
                for (var loc : existing.getMobSpawns()) {
                    w1.addMobSpawn(loc);
                }
            }
        }

        // Save and clear inventory (deep clone each ItemStack)
        ItemStack[] contents = player.getInventory().getContents();
        ItemStack[] savedContents = new ItemStack[contents.length];
        for (int i = 0; i < contents.length; i++) {
            savedContents[i] = contents[i] != null ? contents[i].clone() : null;
        }
        session.setSavedInventory(savedContents);
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

        // v1.0.7: Static dungeons have relaxed validation (no mob spawns required)
        if (session.isStatic()) {
            if (session.getPos1() == null || session.getPos2() == null || session.getSpawnPoint() == null) {
                MessageUtil.send(player, configManager.getPrefix() + "&cCannot save! Set pos1, pos2, and spawn point.");
                return false;
            }
        } else if (!session.isValid()) {
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

        // v1.0.7: Save type and entry point
        dungeon.setType(session.getDungeonType());
        dungeon.setEntryPoint(session.getEntryPoint());

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

        // v1.0.7: Save wave data directly to dungeon file with per-spawn serialization
        java.io.File dungeonFile = new java.io.File(plugin.getDataFolder(), "dungeons/" + session.getDungeonId() + ".yml");
        if (dungeonFile.exists()) {
            org.bukkit.configuration.file.YamlConfiguration config = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(dungeonFile);
            
            for (var entry : session.getWaves().entrySet()) {
                int waveNum = entry.getKey();
                SetupSession.WaveData wd = entry.getValue();
                String basePath = "waves." + waveNum + ".";
                
                // Legacy flat mobs map (for runtime backward compat)
                for (var mob : wd.getMobs().entrySet()) {
                    config.set(basePath + "mobs." + mob.getKey(), mob.getValue());
                }
                
                // Legacy boss fields (for runtime backward compat)
                if (wd.getBossId() != null) {
                    config.set(basePath + "boss", true);
                    config.set(basePath + "boss-type", wd.getBossId());
                    config.set(basePath + "boss-health", wd.getBossHealth());
                }
                
                // Difficulty
                if (wd.getDifficulty() != null) {
                    config.set(basePath + "difficulty", wd.getDifficulty().name());
                }

                // Time limit
                config.set(basePath + "time-limit", wd.getTimeLimit());

                // Bundle
                if (wd.getBundleId() != null) {
                    config.set(basePath + "bundle-id", wd.getBundleId());
                    config.set(basePath + "chest-amount", wd.getChestAmount());
                }

                // v1.0.7: Per-spawn mob entries (individual location + mob_id + amount)
                List<java.util.Map<String, Object>> mobSpawnList = new ArrayList<>();
                List<String> mobSpawnLocs = new ArrayList<>();
                for (SetupSession.MobSpawnEntry ms : wd.getMobSpawns()) {
                    java.util.Map<String, Object> spawnMap = new java.util.LinkedHashMap<>();
                    spawnMap.put("location", com.wibudungeon.core.util.LocationUtil.serialize(ms.getLocation()));
                    spawnMap.put("mob_id", ms.getMobId() != null ? ms.getMobId() : "");
                    spawnMap.put("amount", ms.getCount());
                    mobSpawnList.add(spawnMap);
                    if (ms.getLocation() != null) {
                        mobSpawnLocs.add(com.wibudungeon.core.util.LocationUtil.serialize(ms.getLocation()));
                    }
                }
                config.set(basePath + "mob-spawn-entries", mobSpawnList);
                config.set(basePath + "mob-spawns", mobSpawnLocs);

                // v1.0.7: Per-spawn boss entries (individual location + boss_id)
                List<java.util.Map<String, Object>> bossSpawnList = new ArrayList<>();
                for (SetupSession.BossSpawnEntry bs : wd.getBossSpawns()) {
                    java.util.Map<String, Object> spawnMap = new java.util.LinkedHashMap<>();
                    spawnMap.put("location", com.wibudungeon.core.util.LocationUtil.serialize(bs.getLocation()));
                    spawnMap.put("boss_id", bs.getBossId() != null ? bs.getBossId() : "");
                    bossSpawnList.add(spawnMap);
                }
                config.set(basePath + "boss-spawn-entries", bossSpawnList);
                
                // Legacy single boss spawn (first entry)
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

        // Slot 1: Start Point Tool (with entry point for STATIC)
        SetupSession session = activeSessions.get(player.getUniqueId());
        boolean isStatic = session != null && session.isStatic();
        List<String> startLore = new java.util.ArrayList<>();
        startLore.add("&7");
        startLore.add("&e  Right-click &7→ Set player spawn");
        if (isStatic) {
            startLore.add("&d  Shift+Right-click &7→ Set entry point");
        }
        startLore.add("&7");
        player.getInventory().setItem(1, new ItemBuilder(Material.ENDER_PEARL)
                .name("&d&l⊕ Start Point Tool")
                .lore(startLore.toArray(new String[0]))
                .glow().build());

        // Slot 2: Mob Spawn Tool
        player.getInventory().setItem(2, new ItemBuilder(Material.ZOMBIE_HEAD)
                .name("&c&l☠ Mob Spawn Tool")
                .lore("&7",
                      "&e  Right-click &7→ Add mob spawn for current wave",
                      "&7  Then assign in &eManage Wave GUI",
                      "&7",
                      "&c  Right-click &7a &agreen marker &7→ Remove that spawn",
                      "&7")
                .glow().build());

        // Slot 3: Boss Spawn Tool
        player.getInventory().setItem(3, new ItemBuilder(Material.BLAZE_ROD)
                .name("&4&l🔥 Boss Spawn Tool")
                .lore("&7",
                      "&e  Right-click &7→ Set boss spawn for current wave",
                      "&7  Then assign in &eManage Wave GUI",
                      "&7",
                      "&c  Right-click &7a &6gold marker &7→ Remove that spawn",
                      "&7")
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
     * v1.0.8: Force marker state refresh for a player on the next tick.
     * Called after removing a spawn point via in-world marker click or GUI.
     */
    public void forceMarkerRefresh(Player player) {
        markerManager.forceRefresh(player.getUniqueId());
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

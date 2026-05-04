package com.wibudungeon.core.config;

import com.wibudungeon.core.dungeon.Dungeon;
import com.wibudungeon.core.reward.Reward;
import com.wibudungeon.core.reward.RewardBundle;
import com.wibudungeon.core.util.ItemBuilder;
import com.wibudungeon.core.util.LocationUtil;
import com.wibudungeon.core.wave.BossWave;
import com.wibudungeon.core.wave.Wave;
import com.wibudungeon.core.dungeon.Difficulty;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffectType;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;

/**
 * Manages all configuration files for WibuDungeon.
 * Loads and provides access to config.yml, dungeons.yml, waves.yml, rewards.yml, messages.yml.
 */
public class ConfigManager {

    private final Plugin plugin;

    private FileConfiguration mainConfig;
    private FileConfiguration dungeonsConfig;
    private FileConfiguration wavesConfig;
    private FileConfiguration rewardsConfig;
    private FileConfiguration messagesConfig;

    private File dungeonsFile;
    private File wavesFile;
    private File rewardsFile;
    private File messagesFile;

    // Cached data
    private final Map<String, Dungeon> dungeons = new HashMap<>();
    private final Map<String, List<Wave>> waveSets = new HashMap<>();
    private final Map<String, Map<Integer, List<Reward>>> rewardSets = new HashMap<>();
    private final Map<String, Map<Integer, Integer>> rewardChestAmounts = new HashMap<>();
    private final Map<String, List<Reward>> completionRewards = new HashMap<>();
    private final Map<String, RewardBundle> rewardBundles = new LinkedHashMap<>();

    public ConfigManager(Plugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Load all configuration files.
     */
    public void loadAll() {
        // Main config
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        mainConfig = plugin.getConfig();

        // Custom config files
        dungeonsFile = saveAndLoad("dungeons.yml");
        wavesFile = saveAndLoad("waves.yml");
        rewardsFile = saveAndLoad("rewards.yml");
        messagesFile = saveAndLoad("messages.yml");

        dungeonsConfig = YamlConfiguration.loadConfiguration(dungeonsFile);
        wavesConfig = YamlConfiguration.loadConfiguration(wavesFile);
        rewardsConfig = YamlConfiguration.loadConfiguration(rewardsFile);
        messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);

        // Parse data
        loadDungeons();
        loadWaveSets();
        loadRewardSets();
        loadRewardBundles();
    }

    private File saveAndLoad(String fileName) {
        File file = new File(plugin.getDataFolder(), fileName);
        if (!file.exists()) {
            plugin.saveResource(fileName, false);
        }
        return file;
    }

    // ===== MAIN CONFIG ACCESSORS =====

    public String getPrefix() {
        return mainConfig.getString("prefix", "&8[&6WibuDungeon&8] &r");
    }

    public List<String> getPortalWorlds() {
        return mainConfig.getStringList("portal.worlds");
    }

    public int getPortalSpawnInterval() {
        return mainConfig.getInt("portal.spawn-interval", 300);
    }

    public int getPortalDuration() {
        return mainConfig.getInt("portal.duration", 120);
    }

    public int getPortalMinDistance() {
        return mainConfig.getInt("portal.min-distance-from-spawn", 50);
    }

    public int getPortalMaxDistance() {
        return mainConfig.getInt("portal.max-distance-from-spawn", 500);
    }

    public Material getPortalDisplayBlock() {
        String name = mainConfig.getString("portal.display-block", "NETHER_PORTAL");
        try {
            return Material.valueOf(name);
        } catch (IllegalArgumentException e) {
            return Material.NETHER_PORTAL;
        }
    }

    public int getMaxActivePortals() {
        return mainConfig.getInt("portal.max-active", 3);
    }

    public boolean isPortalAnnounce() {
        return mainConfig.getBoolean("portal.announce", true);
    }

    public int getDungeonMaxTime() {
        return mainConfig.getInt("dungeon.max-time", 600);
    }

    public int getWaveInterval() {
        return mainConfig.getInt("dungeon.wave-interval", 5);
    }

    public int getStartCountdown() {
        return mainConfig.getInt("dungeon.start-countdown", 5);
    }

    public boolean isAllowRejoin() {
        return mainConfig.getBoolean("dungeon.allow-rejoin", true);
    }

    public int getMaxPartySize() {
        return mainConfig.getInt("party.max-size", 4);
    }

    public int getInviteTimeout() {
        return mainConfig.getInt("party.invite-timeout", 60);
    }

    public boolean isAllowSolo() {
        return mainConfig.getBoolean("party.allow-solo", true);
    }

    public boolean isRestoreInventory() {
        return mainConfig.getBoolean("death.restore-inventory", true);
    }

    public boolean isKeepExp() {
        return mainConfig.getBoolean("death.keep-exp", true);
    }

    // ===== MESSAGES =====

    public String getMessage(String path) {
        return getPrefix() + messagesConfig.getString(path, "&cMissing message: " + path);
    }

    public String getRawMessage(String path) {
        return messagesConfig.getString(path, "&cMissing message: " + path);
    }

    // ===== DUNGEONS (Per-file storage in dungeons/ directory) =====

    private void loadDungeons() {
        dungeons.clear();

        // Migration: if old dungeons.yml has data, migrate to per-file
        migrateOldDungeons();

        File dungeonsDir = new File(plugin.getDataFolder(), "dungeons");
        if (!dungeonsDir.exists()) {
            dungeonsDir.mkdirs();
        }

        File[] files = dungeonsDir.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null || files.length == 0) {
            plugin.getLogger().info("No dungeon files found in dungeons/ directory.");
            return;
        }

        for (File file : files) {
            String id = file.getName().replace(".yml", "");
            try {
                FileConfiguration dc = YamlConfiguration.loadConfiguration(file);
                Dungeon dungeon = parseDungeon(id, dc);
                if (dungeon != null) {
                    dungeons.put(id, dungeon);
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to load dungeon: " + id, e);
            }
        }

        plugin.getLogger().info("Loaded " + dungeons.size() + " dungeons from dungeons/ directory.");
    }

    /**
     * Migrate old dungeons.yml to per-file format.
     */
    private void migrateOldDungeons() {
        if (dungeonsFile == null || !dungeonsFile.exists()) return;
        FileConfiguration oldConfig = YamlConfiguration.loadConfiguration(dungeonsFile);
        ConfigurationSection section = oldConfig.getConfigurationSection("dungeons");
        if (section == null || section.getKeys(false).isEmpty()) return;

        File dungeonsDir = new File(plugin.getDataFolder(), "dungeons");
        if (!dungeonsDir.exists()) dungeonsDir.mkdirs();

        int migrated = 0;
        for (String id : section.getKeys(false)) {
            File targetFile = new File(dungeonsDir, id + ".yml");
            if (targetFile.exists()) continue; // Already migrated

            ConfigurationSection ds = section.getConfigurationSection(id);
            if (ds == null) continue;

            YamlConfiguration newConfig = new YamlConfiguration();
            for (String key : ds.getKeys(true)) {
                if (!ds.isConfigurationSection(key)) {
                    newConfig.set(key, ds.get(key));
                }
            }
            // Handle lists explicitly
            if (ds.contains("mob-spawns")) {
                newConfig.set("mob-spawns", ds.getStringList("mob-spawns"));
            }

            try {
                newConfig.save(targetFile);
                migrated++;
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to migrate dungeon: " + id, e);
            }
        }

        if (migrated > 0) {
            // Backup old file
            File backup = new File(plugin.getDataFolder(), "dungeons.yml.bak");
            dungeonsFile.renameTo(backup);
            plugin.getLogger().info("Migrated " + migrated + " dungeons to per-file format. Old file backed up as dungeons.yml.bak");
        }
    }

    /**
     * Parse a dungeon from a per-file configuration.
     */
    private Dungeon parseDungeon(String id, FileConfiguration config) {
        Dungeon dungeon = new Dungeon(id);
        dungeon.setName(config.getString("name", id));
        dungeon.setEnabled(config.getBoolean("enabled", true));
        dungeon.setWorld(config.getString("world", "world"));
        dungeon.setPos1(LocationUtil.deserialize(config.getString("pos1")));
        dungeon.setPos2(LocationUtil.deserialize(config.getString("pos2")));
        dungeon.setSpawnPoint(LocationUtil.deserialize(config.getString("spawn-point")));
        dungeon.setWaveSet(config.getString("wave-set", "default"));
        dungeon.setRewardSet(config.getString("reward-set", "default"));
        dungeon.setMaxInstances(config.getInt("max-instances", 3));
        dungeon.setMinPlayers(config.getInt("min-players", 1));
        dungeon.setMaxPlayers(config.getInt("max-players", 4));

        List<String> mobSpawnStrings = config.getStringList("mob-spawns");
        List<Location> mobSpawns = new ArrayList<>();
        for (String s : mobSpawnStrings) {
            Location loc = LocationUtil.deserialize(s);
            if (loc != null) mobSpawns.add(loc);
        }
        dungeon.setMobSpawns(mobSpawns);
        return dungeon;
    }

    public Map<String, Dungeon> getDungeons() {
        return Collections.unmodifiableMap(dungeons);
    }

    public Dungeon getDungeon(String id) {
        return dungeons.get(id);
    }

    /**
     * Save a dungeon to its own file: dungeons/<id>.yml
     */
    public void saveDungeon(Dungeon dungeon) {
        File dungeonsDir = new File(plugin.getDataFolder(), "dungeons");
        if (!dungeonsDir.exists()) dungeonsDir.mkdirs();

        File file = new File(dungeonsDir, dungeon.getId() + ".yml");
        YamlConfiguration config = new YamlConfiguration();

        config.set("name", dungeon.getName());
        config.set("enabled", dungeon.isEnabled());
        config.set("world", dungeon.getWorld());
        config.set("pos1", LocationUtil.serialize(dungeon.getPos1()));
        config.set("pos2", LocationUtil.serialize(dungeon.getPos2()));
        config.set("spawn-point", LocationUtil.serialize(dungeon.getSpawnPoint()));
        config.set("wave-set", dungeon.getWaveSet());
        config.set("reward-set", dungeon.getRewardSet());
        config.set("max-instances", dungeon.getMaxInstances());
        config.set("min-players", dungeon.getMinPlayers());
        config.set("max-players", dungeon.getMaxPlayers());

        List<String> mobSpawns = new ArrayList<>();
        for (Location loc : dungeon.getMobSpawns()) {
            mobSpawns.add(LocationUtil.serialize(loc));
        }
        config.set("mob-spawns", mobSpawns);

        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save dungeon: " + dungeon.getId(), e);
        }

        dungeons.put(dungeon.getId(), dungeon);
    }

    /**
     * Delete a dungeon file.
     */
    public void deleteDungeon(String id) {
        File file = new File(plugin.getDataFolder(), "dungeons/" + id + ".yml");
        if (file.exists()) {
            file.delete();
        }
        dungeons.remove(id);
    }

    // ===== WAVE SETS =====

    private void loadWaveSets() {
        waveSets.clear();
        ConfigurationSection section = wavesConfig.getConfigurationSection("wave-sets");
        if (section == null) return;

        for (String setId : section.getKeys(false)) {
            ConfigurationSection waveSection = section.getConfigurationSection(setId + ".waves");
            if (waveSection == null) continue;

            List<Wave> waves = new ArrayList<>();

            for (String waveNumStr : waveSection.getKeys(false)) {
                int waveNum;
                try {
                    waveNum = Integer.parseInt(waveNumStr);
                } catch (NumberFormatException e) {
                    continue;
                }

                ConfigurationSection ws = waveSection.getConfigurationSection(waveNumStr);
                if (ws == null) continue;

                // Parse mobs - supports both vanilla ("ZOMBIE") and MythicMobs ("mm:MobId")
                Map<String, Integer> mobs = new LinkedHashMap<>();
                ConfigurationSection mobSection = ws.getConfigurationSection("mobs");
                if (mobSection != null) {
                    for (String mobKey : mobSection.getKeys(false)) {
                        mobs.put(mobKey, mobSection.getInt(mobKey));
                    }
                }

                // Parse scaling
                ConfigurationSection scaling = ws.getConfigurationSection("scaling");
                double healthMul = scaling != null ? scaling.getDouble("health-multiplier", 1.0) : 1.0;
                double damageMul = scaling != null ? scaling.getDouble("damage-multiplier", 1.0) : 1.0;
                double speedMul = scaling != null ? scaling.getDouble("speed-multiplier", 1.0) : 1.0;
                int timeLimit = ws.getInt("time-limit", 120);
                String diffStr = ws.getString("difficulty", "NORMAL");
                Difficulty difficulty;
                try {
                    difficulty = Difficulty.valueOf(diffStr.toUpperCase());
                } catch (IllegalArgumentException e) {
                    difficulty = Difficulty.NORMAL;
                }

                // Check if boss wave
                if (ws.getBoolean("boss", false)) {
                    // Boss type: vanilla name or "mm:MythicMobId"
                    String bossType = ws.getString("boss-type", "WITHER_SKELETON");

                    String bossName = ws.getString("boss-name", "&4&lBoss");
                    double bossHealth = ws.getDouble("boss-health", 200);

                    // Parse boss effects
                    Map<PotionEffectType, Integer> bossEffects = new HashMap<>();
                    ConfigurationSection effectSection = ws.getConfigurationSection("boss-effects");
                    if (effectSection != null) {
                        for (String effectKey : effectSection.getKeys(false)) {
                            PotionEffectType effectType = PotionEffectType.getByName(effectKey);
                            if (effectType != null) {
                                bossEffects.put(effectType, effectSection.getInt(effectKey));
                            }
                        }
                    }

                    waves.add(new BossWave(waveNum, mobs, healthMul, damageMul, speedMul,
                            timeLimit, difficulty, bossType, bossName, bossHealth, bossEffects));
                } else {
                    waves.add(new Wave(waveNum, mobs, healthMul, damageMul, speedMul, timeLimit, difficulty));
                }
            }

            // Sort by wave number
            waves.sort(Comparator.comparingInt(Wave::getWaveNumber));
            waveSets.put(setId, waves);
        }

        plugin.getLogger().info("Loaded " + waveSets.size() + " wave sets.");
    }

    public List<Wave> getWaveSet(String setId) {
        return waveSets.getOrDefault(setId, Collections.emptyList());
    }

    // ===== REWARD SETS =====

    private void loadRewardSets() {
        rewardSets.clear();
        rewardChestAmounts.clear();
        completionRewards.clear();
        ConfigurationSection section = rewardsConfig.getConfigurationSection("reward-sets");
        if (section == null) return;

        for (String setId : section.getKeys(false)) {
            ConfigurationSection setSection = section.getConfigurationSection(setId);
            if (setSection == null) continue;

            // Wave rewards
            Map<Integer, List<Reward>> waveRewards = new HashMap<>();
            Map<Integer, Integer> chestAmounts = new HashMap<>();
            ConfigurationSection waveRewardSection = setSection.getConfigurationSection("wave-rewards");
            if (waveRewardSection != null) {
                for (String waveNumStr : waveRewardSection.getKeys(false)) {
                    int waveNum;
                    try {
                        waveNum = Integer.parseInt(waveNumStr);
                    } catch (NumberFormatException e) {
                        continue;
                    }
                    ConfigurationSection wr = waveRewardSection.getConfigurationSection(waveNumStr);
                    if (wr == null) continue;
                    waveRewards.put(waveNum, parseRewards(wr));
                    chestAmounts.put(waveNum, wr.getInt("chest_amount", 1));
                }
            }
            rewardSets.put(setId, waveRewards);
            rewardChestAmounts.put(setId, chestAmounts);

            // Completion rewards
            ConfigurationSection compSection = setSection.getConfigurationSection("completion");
            if (compSection != null) {
                completionRewards.put(setId, parseRewards(compSection));
            }
        }

        plugin.getLogger().info("Loaded " + rewardSets.size() + " reward sets.");
    }

    private List<Reward> parseRewards(ConfigurationSection section) {
        List<Reward> rewards = new ArrayList<>();

        // Parse items
        List<Map<?, ?>> items = section.getMapList("items");
        for (Map<?, ?> itemMap : items) {
            Object materialObj = itemMap.get("material");
            String materialStr = materialObj != null ? String.valueOf(materialObj) : "STONE";
            int amount = itemMap.containsKey("amount") ? ((Number) itemMap.get("amount")).intValue() : 1;
            String name = itemMap.containsKey("name") ? String.valueOf(itemMap.get("name")) : null;
            double chance = itemMap.containsKey("chance") ? ((Number) itemMap.get("chance")).doubleValue() : 1.0;

            try {
                Material material = Material.valueOf(materialStr);
                ItemBuilder builder = new ItemBuilder(material, amount);
                if (name != null) {
                    builder.name(name);
                }

                // Parse lore if present
                if (itemMap.containsKey("lore")) {
                    Object loreObj = itemMap.get("lore");
                    if (loreObj instanceof List<?> loreList) {
                        List<String> lore = new ArrayList<>();
                        for (Object line : loreList) {
                            lore.add(String.valueOf(line));
                        }
                        builder.lore(lore);
                    }
                }

                rewards.add(Reward.ofItem(builder.build(), chance));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid material in rewards: " + materialStr);
            }
        }

        // Parse commands
        List<String> commands = section.getStringList("commands");
        for (String cmd : commands) {
            if (!cmd.isEmpty()) {
                rewards.add(Reward.ofCommand(cmd));
            }
        }

        // Parse money
        double money = section.getDouble("money", 0);
        if (money > 0) {
            rewards.add(Reward.ofMoney(money));
        }

        return rewards;
    }

    public List<Reward> getWaveRewards(String setId, int waveNumber) {
        Map<Integer, List<Reward>> waveMap = rewardSets.get(setId);
        if (waveMap == null) return Collections.emptyList();
        return waveMap.getOrDefault(waveNumber, Collections.emptyList());
    }

    public int getWaveChestAmount(String setId, int waveNumber) {
        Map<Integer, Integer> amtMap = rewardChestAmounts.get(setId);
        if (amtMap == null) return 1;
        return amtMap.getOrDefault(waveNumber, 1);
    }

    public List<Reward> getCompletionRewards(String setId) {
        return completionRewards.getOrDefault(setId, Collections.emptyList());
    }

    // ===== WAVE MANAGEMENT VIA COMMANDS =====

    /**
     * Add a mob to a specific wave in the waves.yml config.
     */
    public void addMobToWave(String dungeonId, int waveNum, String mobId, int count) {
        Dungeon dungeon = getDungeon(dungeonId);
        if (dungeon == null) return;
        String waveSetId = dungeon.getWaveSet();
        String path = "wave-sets." + waveSetId + ".waves." + waveNum + ".mobs." + mobId;
        int existing = wavesConfig.getInt(path, 0);
        wavesConfig.set(path, existing + count);

        // Ensure scaling defaults exist
        String scalePath = "wave-sets." + waveSetId + ".waves." + waveNum + ".scaling";
        if (!wavesConfig.contains(scalePath + ".health-multiplier")) {
            wavesConfig.set(scalePath + ".health-multiplier", 1.0);
            wavesConfig.set(scalePath + ".damage-multiplier", 1.0);
            wavesConfig.set(scalePath + ".speed-multiplier", 1.0);
        }
        if (!wavesConfig.contains("wave-sets." + waveSetId + ".waves." + waveNum + ".time-limit")) {
            wavesConfig.set("wave-sets." + waveSetId + ".waves." + waveNum + ".time-limit", 120);
        }

        saveWavesConfig();
        loadWaveSets(); // Reload cached data
    }

    /**
     * Set a boss for a specific wave in the waves.yml config.
     */
    public void setBossForWave(String dungeonId, int waveNum, String mobId, double health) {
        Dungeon dungeon = getDungeon(dungeonId);
        if (dungeon == null) return;
        String waveSetId = dungeon.getWaveSet();
        String basePath = "wave-sets." + waveSetId + ".waves." + waveNum;
        wavesConfig.set(basePath + ".boss", true);
        wavesConfig.set(basePath + ".boss-type", mobId);
        wavesConfig.set(basePath + ".boss-name", "&4&l" + mobId);
        wavesConfig.set(basePath + ".boss-health", health);

        saveWavesConfig();
        loadWaveSets();
    }

    /**
     * Set difficulty for a specific wave in the waves.yml config.
     */
    public void setDifficultyForWave(String dungeonId, int waveNum, Difficulty difficulty) {
        if (difficulty == null) return;
        Dungeon dungeon = getDungeon(dungeonId);
        if (dungeon == null) return;
        String waveSetId = dungeon.getWaveSet();
        String basePath = "wave-sets." + waveSetId + ".waves." + waveNum;
        wavesConfig.set(basePath + ".difficulty", difficulty.name());

        saveWavesConfig();
        loadWaveSets();
    }

    /**
     * Set time limit for a specific wave in the waves.yml config.
     */
    public void setTimeLimitForWave(String dungeonId, int waveNum, int seconds) {
        Dungeon dungeon = getDungeon(dungeonId);
        if (dungeon == null) return;
        String waveSetId = dungeon.getWaveSet();
        String basePath = "wave-sets." + waveSetId + ".waves." + waveNum;
        wavesConfig.set(basePath + ".time-limit", Math.max(30, seconds));

        saveWavesConfig();
        loadWaveSets();
    }

    /**
     * Get time limit for a specific wave.
     */
    public int getWaveTimeLimit(String dungeonId, int waveNum) {
        Dungeon dungeon = getDungeon(dungeonId);
        if (dungeon == null) return 120;
        String waveSetId = dungeon.getWaveSet();
        return wavesConfig.getInt("wave-sets." + waveSetId + ".waves." + waveNum + ".time-limit", 120);
    }

    /**
     * Remove a mob from a specific wave in the waves.yml config.
     */
    public void removeMobFromWave(String dungeonId, int waveNum, String mobId) {
        Dungeon dungeon = getDungeon(dungeonId);
        if (dungeon == null) return;
        String waveSetId = dungeon.getWaveSet();
        String path = "wave-sets." + waveSetId + ".waves." + waveNum + ".mobs." + mobId;
        wavesConfig.set(path, null);

        saveWavesConfig();
        loadWaveSets();
    }

    /**
     * Get the rewards config for read-only access.
     */
    public FileConfiguration getRewardsConfig() {
        return rewardsConfig;
    }

    private void saveWavesConfig() {
        try {
            wavesConfig.save(wavesFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save waves.yml", e);
        }
    }

    // ===== REWARD BUNDLES (v1.0.6) =====

    /**
     * Load reward bundles from the bundles: section of rewards.yml.
     */
    private void loadRewardBundles() {
        rewardBundles.clear();
        ConfigurationSection section = rewardsConfig.getConfigurationSection("bundles");
        if (section == null) {
            plugin.getLogger().info("No reward bundles found in rewards.yml.");
            return;
        }

        for (String bundleId : section.getKeys(false)) {
            ConfigurationSection bundleSec = section.getConfigurationSection(bundleId);
            if (bundleSec == null) continue;

            ConfigurationSection itemsSec = bundleSec.getConfigurationSection("items");
            if (itemsSec == null) continue;

            List<RewardBundle.BundleItem> items = new ArrayList<>();
            for (String itemKey : itemsSec.getKeys(false)) {
                ConfigurationSection itemSec = itemsSec.getConfigurationSection(itemKey);
                if (itemSec == null) continue;

                String materialStr = itemSec.getString("material", "STONE");
                int amount = itemSec.getInt("amount", 1);
                double chance = itemSec.getDouble("chance", 1.0);
                String displayName = itemSec.getString("name", null);

                try {
                    org.bukkit.Material material = org.bukkit.Material.valueOf(materialStr);
                    items.add(new RewardBundle.BundleItem(itemKey, material, amount, chance, displayName));
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid material in bundle '" + bundleId + "': " + materialStr);
                }
            }

            if (!items.isEmpty()) {
                rewardBundles.put(bundleId, new RewardBundle(bundleId, items));
            }
        }

        plugin.getLogger().info("Loaded " + rewardBundles.size() + " reward bundles.");
    }

    public Map<String, RewardBundle> getRewardBundles() {
        return Collections.unmodifiableMap(rewardBundles);
    }

    public RewardBundle getRewardBundle(String bundleId) {
        return rewardBundles.get(bundleId);
    }

    public List<String> getAllBundleIds() {
        return new ArrayList<>(rewardBundles.keySet());
    }

    /**
     * Get the bundle ID assigned to a specific wave.
     */
    public String getWaveBundleId(String dungeonId, int waveNum) {
        Dungeon dungeon = getDungeon(dungeonId);
        if (dungeon == null) return null;
        String waveSetId = dungeon.getWaveSet();
        return wavesConfig.getString("wave-sets." + waveSetId + ".waves." + waveNum + ".bundle", null);
    }

    /**
     * Set the bundle ID for a specific wave and save.
     */
    public void setWaveBundleId(String dungeonId, int waveNum, String bundleId) {
        Dungeon dungeon = getDungeon(dungeonId);
        if (dungeon == null) return;
        String waveSetId = dungeon.getWaveSet();
        String path = "wave-sets." + waveSetId + ".waves." + waveNum + ".bundle";
        if (bundleId != null) {
            wavesConfig.set(path, bundleId);
        } else {
            wavesConfig.set(path, null);
        }
        saveWavesConfig();
    }

    /**
     * Get the chest amount for a wave from waves.yml.
     */
    public int getWaveChestAmountFromWaves(String dungeonId, int waveNum) {
        Dungeon dungeon = getDungeon(dungeonId);
        if (dungeon == null) return 3;
        String waveSetId = dungeon.getWaveSet();
        return wavesConfig.getInt("wave-sets." + waveSetId + ".waves." + waveNum + ".chest_amount", 3);
    }

    private void saveRewardsConfig() {
        try {
            rewardsConfig.save(rewardsFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save rewards.yml", e);
        }
    }
}

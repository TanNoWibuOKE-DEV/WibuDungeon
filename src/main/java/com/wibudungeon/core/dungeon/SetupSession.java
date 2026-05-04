package com.wibudungeon.core.dungeon;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/**
 * Stores all setup state for an admin configuring a dungeon.
 *
 * v1.0.6: Added region validation (isInsideRegion) and bundle fields (bundleId, chestAmount) per wave.
 */
public class SetupSession {

    private final UUID playerId;
    private final String dungeonId;
    private Location pos1;
    private Location pos2;
    private Location spawnPoint;
    private final Map<Integer, WaveData> waves = new LinkedHashMap<>();
    private ItemStack[] savedInventory;
    private GameMode savedGameMode;
    private int selectedWave = 1;
    private String pendingMobId;

    public SetupSession(UUID playerId, String dungeonId) {
        this.playerId = playerId;
        this.dungeonId = dungeonId;
    }

    // ===== REGION =====
    public Location getPos1() { return pos1; }
    public void setPos1(Location pos1) { this.pos1 = pos1; }
    public Location getPos2() { return pos2; }
    public void setPos2(Location pos2) { this.pos2 = pos2; }
    public Location getSpawnPoint() { return spawnPoint; }
    public void setSpawnPoint(Location sp) { this.spawnPoint = sp; }

    /**
     * Check if a location is inside the defined dungeon region (pos1–pos2 bounding box).
     * Returns true if region is not yet defined (pos1/pos2 null) — allows setting positions freely.
     */
    public boolean isInsideRegion(Location loc) {
        if (pos1 == null || pos2 == null) return true; // Region not defined yet, allow
        if (loc == null) return false;
        if (pos1.getWorld() == null || !pos1.getWorld().equals(loc.getWorld())) return false;

        double minX = Math.min(pos1.getX(), pos2.getX());
        double maxX = Math.max(pos1.getX(), pos2.getX()) + 1;
        double minY = Math.min(pos1.getY(), pos2.getY());
        double maxY = Math.max(pos1.getY(), pos2.getY()) + 1;
        double minZ = Math.min(pos1.getZ(), pos2.getZ());
        double maxZ = Math.max(pos1.getZ(), pos2.getZ()) + 1;

        return loc.getX() >= minX && loc.getX() <= maxX
                && loc.getY() >= minY && loc.getY() <= maxY
                && loc.getZ() >= minZ && loc.getZ() <= maxZ;
    }

    /**
     * Check if region is defined (both pos1 and pos2 set).
     */
    public boolean hasRegion() {
        return pos1 != null && pos2 != null;
    }



    // ===== WAVES =====
    public Map<Integer, WaveData> getWaves() { return waves; }
    public WaveData getWave(int num) { return waves.get(num); }
    public WaveData getOrCreateWave(int num) {
        return waves.computeIfAbsent(num, k -> new WaveData(num));
    }
    public int getSelectedWave() { return selectedWave; }
    public void setSelectedWave(int w) { this.selectedWave = w; }
    public int getNextWaveNumber() {
        return waves.isEmpty() ? 1 : Collections.max(waves.keySet()) + 1;
    }

    // ===== INVENTORY / STATE =====
    public ItemStack[] getSavedInventory() { return savedInventory; }
    public void setSavedInventory(ItemStack[] inv) { this.savedInventory = inv; }
    public GameMode getSavedGameMode() { return savedGameMode; }
    public void setSavedGameMode(GameMode gm) { this.savedGameMode = gm; }

    // ===== PENDING =====
    public String getPendingMobId() { return pendingMobId; }
    public void setPendingMobId(String id) { this.pendingMobId = id; }

    // ===== IDS =====
    public UUID getPlayerId() { return playerId; }
    public String getDungeonId() { return dungeonId; }

    /**
     * Get setup progress summary.
     */
    public List<String> getProgressLines() {
        List<String> lines = new ArrayList<>();
        lines.add("&e Dungeon: &f" + dungeonId);
        lines.add("&e Pos1: " + (pos1 != null ? "&a✔ Set" : "&c✘ Not set"));
        lines.add("&e Pos2: " + (pos2 != null ? "&a✔ Set" : "&c✘ Not set"));
        lines.add("&e Spawn: " + (spawnPoint != null ? "&a✔ Set" : "&c✘ Not set"));
        lines.add("&e Waves: &f" + waves.size());
        for (var entry : waves.entrySet()) {
            WaveData w = entry.getValue();
            String bossStr = w.getBossId() != null ? " &c[BOSS: " + w.getBossId() + "]" : "";
            String bundleStr = w.getBundleId() != null ? " &6[Bundle: " + w.getBundleId() + "]" : "";
            lines.add("&7   Wave " + entry.getKey() + ": &f" + w.getTotalMobs() + " mobs" + bossStr + bundleStr);
        }
        return lines;
    }

    public boolean isValid() {
        return pos1 != null && pos2 != null && spawnPoint != null;
    }

    // ===== INNER CLASSES =====

    public static class MobSpawnEntry {
        private final Location location;
        private final String mobId;
        private final int count;
        public MobSpawnEntry(Location location, String mobId, int count) {
            this.location = location;
            this.mobId = mobId;
            this.count = count;
        }
        public Location getLocation() { return location; }
        public String getMobId() { return mobId; }
        public int getCount() { return count; }
    }

    public static class WaveData {
        private final int waveNumber;
        private final Map<String, Integer> mobs = new LinkedHashMap<>();
        private final List<MobSpawnEntry> mobSpawns = new ArrayList<>();
        private Location bossSpawnPoint;
        private String bossId;
        private double bossHealth = 200;
        private String bossName;
        private Difficulty difficulty;
        private int timeLimit = 120;
        private String bundleId;
        private int chestAmount = 3;

        public WaveData(int waveNumber) { this.waveNumber = waveNumber; }
        public int getWaveNumber() { return waveNumber; }
        public Map<String, Integer> getMobs() { return mobs; }
        public void addMob(String mobId, int count) {
            mobs.merge(mobId, count, Integer::sum);
        }
        public void removeMob(String mobId) { mobs.remove(mobId); }
        public int getTotalMobs() { return mobs.values().stream().mapToInt(Integer::intValue).sum(); }

        public List<MobSpawnEntry> getMobSpawns() { return mobSpawns; }
        public void addMobSpawn(Location loc, String mobId, int count) {
            mobSpawns.add(new MobSpawnEntry(loc, mobId, count));
        }

        public Location getBossSpawnPoint() { return bossSpawnPoint; }
        public void setBossSpawnPoint(Location loc) { this.bossSpawnPoint = loc; }
        public String getBossId() { return bossId; }
        public void setBossId(String id) { this.bossId = id; }
        public double getBossHealth() { return bossHealth; }
        public void setBossHealth(double hp) { this.bossHealth = hp; }
        public String getBossName() { return bossName; }
        public void setBossName(String n) { this.bossName = n; }
        public Difficulty getDifficulty() { return difficulty; }
        public void setDifficulty(Difficulty d) { this.difficulty = d; }
        public int getTimeLimit() { return timeLimit; }
        public void setTimeLimit(int t) { this.timeLimit = Math.max(30, t); }
        public String getBundleId() { return bundleId; }
        public void setBundleId(String bundleId) { this.bundleId = bundleId; }
        public int getChestAmount() { return chestAmount; }
        public void setChestAmount(int chestAmount) { this.chestAmount = Math.max(1, chestAmount); }
    }
}

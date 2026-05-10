package com.wibudungeon.core.dungeon;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/**
 * Stores all setup state for an admin configuring a dungeon.
 *
 * v1.0.6: Added region validation (isInsideRegion) and bundle fields (bundleId, chestAmount) per wave.
 * v1.0.7: Reworked MobSpawnEntry to mutable individual assignment model.
 *         Added BossSpawnEntry for per-position boss assignment.
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
    private DungeonType dungeonType = DungeonType.DYNAMIC;
    private Location entryPoint;

    // v1.0.7: Track which spawn index is being edited in GUI
    private int pendingSpawnIndex = -1;
    private boolean pendingIsBoss = false;

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

    // ===== PENDING MOB ASSIGNMENT (v1.0.7 rework) =====
    public String getPendingMobId() { return pendingMobId; }
    public void setPendingMobId(String id) { this.pendingMobId = id; }

    public int getPendingSpawnIndex() { return pendingSpawnIndex; }
    public void setPendingSpawnIndex(int idx) { this.pendingSpawnIndex = idx; }
    public boolean isPendingIsBoss() { return pendingIsBoss; }
    public void setPendingIsBoss(boolean isBoss) { this.pendingIsBoss = isBoss; }

    // ===== DUNGEON TYPE =====
    public DungeonType getDungeonType() { return dungeonType; }
    public void setDungeonType(DungeonType type) { this.dungeonType = type != null ? type : DungeonType.DYNAMIC; }
    public boolean isStatic() { return dungeonType == DungeonType.STATIC; }

    // ===== ENTRY POINT (STATIC) =====
    public Location getEntryPoint() { return entryPoint; }
    public void setEntryPoint(Location loc) { this.entryPoint = loc; }

    // ===== IDS =====
    public UUID getPlayerId() { return playerId; }
    public String getDungeonId() { return dungeonId; }

    /**
     * Get setup progress summary.
     */
    public List<String> getProgressLines() {
        List<String> lines = new ArrayList<>();
        lines.add("&e Dungeon: &f" + dungeonId);
        lines.add("&e Type: &f" + dungeonType.name());
        lines.add("&e Pos1: " + (pos1 != null ? "&a✔ Set" : "&c✘ Not set"));
        lines.add("&e Pos2: " + (pos2 != null ? "&a✔ Set" : "&c✘ Not set"));
        lines.add("&e Spawn: " + (spawnPoint != null ? "&a✔ Set" : "&c✘ Not set"));
        if (dungeonType == DungeonType.STATIC) {
            lines.add("&e Entry: " + (entryPoint != null ? "&a✔ Set" : "&c✘ Not set"));
        }
        lines.add("&e Waves: &f" + waves.size());
        for (var entry : waves.entrySet()) {
            WaveData w = entry.getValue();
            int mobSpawns = w.getMobSpawns().size();
            int bossSpawns = w.getBossSpawns().size();
            int totalMobs = w.getTotalMobs();
            String bossStr = bossSpawns > 0 ? " &c[BOSS: " + bossSpawns + " spawns]" : "";
            String bundleStr = w.getBundleId() != null ? " &6[Bundle: " + w.getBundleId() + "]" : "";
            lines.add("&7   Wave " + entry.getKey() + ": &f" + mobSpawns + " mob spawns (" + totalMobs + " total)" + bossStr + bundleStr);
        }
        return lines;
    }

    public boolean isValid() {
        return pos1 != null && pos2 != null && spawnPoint != null;
    }

    // ===== INNER CLASSES =====

    /**
     * v1.0.7: Mutable mob spawn entry — supports individual mob assignment.
     * Location is set on placement, mob ID and count are assigned later via GUI.
     */
    public static class MobSpawnEntry {
        private final Location location;
        private String mobId;   // null = not assigned yet
        private int count;      // default 1

        public MobSpawnEntry(Location location) {
            this.location = location;
            this.mobId = null;
            this.count = 1;
        }

        public MobSpawnEntry(Location location, String mobId, int count) {
            this.location = location;
            this.mobId = mobId;
            this.count = Math.max(1, count);
        }

        public Location getLocation() { return location; }
        public String getMobId() { return mobId; }
        public void setMobId(String mobId) { this.mobId = mobId; }
        public int getCount() { return count; }
        public void setCount(int count) { this.count = Math.max(1, count); }
        public boolean isAssigned() { return mobId != null && !mobId.isEmpty() && !mobId.equals("NODE"); }
    }

    /**
     * v1.0.7: Boss spawn entry — individual boss assignment per position.
     */
    public static class BossSpawnEntry {
        private final Location location;
        private String bossId;  // null = not assigned yet

        public BossSpawnEntry(Location location) {
            this.location = location;
            this.bossId = null;
        }

        public BossSpawnEntry(Location location, String bossId) {
            this.location = location;
            this.bossId = bossId;
        }

        public Location getLocation() { return location; }
        public String getBossId() { return bossId; }
        public void setBossId(String bossId) { this.bossId = bossId; }
        public boolean isAssigned() { return bossId != null && !bossId.isEmpty(); }
    }

    public static class WaveData {
        private final int waveNumber;
        private final List<MobSpawnEntry> mobSpawns = new ArrayList<>();
        private final List<BossSpawnEntry> bossSpawns = new ArrayList<>();
        private Difficulty difficulty;
        private int timeLimit = 120;
        private String bundleId;
        private int chestAmount = 3;

        public WaveData(int waveNumber) { this.waveNumber = waveNumber; }
        public int getWaveNumber() { return waveNumber; }

        // ===== MOB SPAWNS (v1.0.7 individual model) =====
        public List<MobSpawnEntry> getMobSpawns() { return mobSpawns; }
        public void addMobSpawn(Location loc) {
            mobSpawns.add(new MobSpawnEntry(loc));
        }
        public void addMobSpawn(Location loc, String mobId, int count) {
            mobSpawns.add(new MobSpawnEntry(loc, mobId, count));
        }
        public void removeMobSpawn(int index) {
            if (index >= 0 && index < mobSpawns.size()) {
                mobSpawns.remove(index);
            }
        }

        /**
         * Backward-compatible: compute mob type → total count map from spawn entries.
         * Used by save logic and old GUI displays.
         */
        public Map<String, Integer> getMobs() {
            Map<String, Integer> map = new LinkedHashMap<>();
            for (MobSpawnEntry entry : mobSpawns) {
                if (entry.isAssigned()) {
                    map.merge(entry.getMobId(), entry.getCount(), Integer::sum);
                }
            }
            return map;
        }

        /**
         * Legacy method: add a mob by ID and count (creates a spawn entry at null location).
         * @deprecated Use addMobSpawn(Location) + assign via GUI instead.
         */
        public void addMob(String mobId, int count) {
            // Check if there's an unassigned spawn we can assign to
            for (MobSpawnEntry entry : mobSpawns) {
                if (!entry.isAssigned()) {
                    entry.setMobId(mobId);
                    entry.setCount(count);
                    return;
                }
            }
            // No unassigned spawn — add as virtual entry
            mobSpawns.add(new MobSpawnEntry(null, mobId, count));
        }

        /**
         * Legacy method: remove mob by ID.
         */
        public void removeMob(String mobId) {
            mobSpawns.removeIf(e -> mobId.equals(e.getMobId()));
        }

        public int getTotalMobs() {
            return mobSpawns.stream()
                    .filter(MobSpawnEntry::isAssigned)
                    .mapToInt(MobSpawnEntry::getCount)
                    .sum();
        }

        // ===== BOSS SPAWNS (v1.0.7 individual model) =====
        public List<BossSpawnEntry> getBossSpawns() { return bossSpawns; }
        public void addBossSpawn(Location loc) {
            bossSpawns.add(new BossSpawnEntry(loc));
        }
        public void addBossSpawn(Location loc, String bossId) {
            bossSpawns.add(new BossSpawnEntry(loc, bossId));
        }
        public void removeBossSpawn(int index) {
            if (index >= 0 && index < bossSpawns.size()) {
                bossSpawns.remove(index);
            }
        }

        // Backward compat: first boss spawn point
        public Location getBossSpawnPoint() {
            return bossSpawns.isEmpty() ? null : bossSpawns.getFirst().getLocation();
        }
        public void setBossSpawnPoint(Location loc) {
            if (bossSpawns.isEmpty()) {
                bossSpawns.add(new BossSpawnEntry(loc));
            } else {
                // Replace first entry's location
                BossSpawnEntry first = bossSpawns.getFirst();
                bossSpawns.set(0, new BossSpawnEntry(loc, first.getBossId()));
            }
        }

        // Backward compat: first boss ID
        public String getBossId() {
            for (BossSpawnEntry entry : bossSpawns) {
                if (entry.isAssigned()) return entry.getBossId();
            }
            return null;
        }
        public void setBossId(String id) {
            if (bossSpawns.isEmpty()) {
                bossSpawns.add(new BossSpawnEntry(null, id));
            } else {
                bossSpawns.getFirst().setBossId(id);
            }
        }

        public double getBossHealth() { return 200; }
        public void setBossHealth(double hp) { /* no-op for compat */ }
        public String getBossName() {
            String bid = getBossId();
            return bid != null ? "&4&l" + bid : null;
        }
        public void setBossName(String n) { /* no-op for compat */ }

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

package com.wibudungeon.core.dungeon;

import org.bukkit.Location;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents a dungeon template definition.
 * Contains all the static information about a dungeon arena.
 */
public class Dungeon {

    private final String id;
    private String name;
    private boolean enabled;
    private String world;
    private Location pos1;
    private Location pos2;
    private Location spawnPoint;
    private List<Location> mobSpawns;
    private String waveSet;
    private String rewardSet;
    private int maxInstances;
    private int minPlayers;
    private int maxPlayers;
    private DungeonType type;
    private Location entryPoint;

    public Dungeon(String id) {
        this.id = id;
        this.name = id;
        this.enabled = true;
        this.mobSpawns = new ArrayList<>();
        this.waveSet = "default";
        this.rewardSet = "default";
        this.maxInstances = 3;
        this.minPlayers = 1;
        this.maxPlayers = 4;
        this.type = DungeonType.DYNAMIC;
    }

    // Getters
    public String getId() { return id; }
    public String getName() { return name; }
    public boolean isEnabled() { return enabled; }
    public String getWorld() { return world; }
    public Location getPos1() { return pos1; }
    public Location getPos2() { return pos2; }
    public Location getSpawnPoint() { return spawnPoint; }
    public List<Location> getMobSpawns() { return Collections.unmodifiableList(mobSpawns); }
    public String getWaveSet() { return waveSet; }
    public String getRewardSet() { return rewardSet; }
    public int getMaxInstances() { return maxInstances; }
    public int getMinPlayers() { return minPlayers; }
    public int getMaxPlayers() { return maxPlayers; }
    public DungeonType getType() { return type; }
    public Location getEntryPoint() { return entryPoint; }

    // Setters
    public void setName(String name) { this.name = name; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public void setWorld(String world) { this.world = world; }
    public void setPos1(Location pos1) { this.pos1 = pos1; }
    public void setPos2(Location pos2) { this.pos2 = pos2; }
    public void setSpawnPoint(Location spawnPoint) { this.spawnPoint = spawnPoint; }
    public void setWaveSet(String waveSet) { this.waveSet = waveSet; }
    public void setRewardSet(String rewardSet) { this.rewardSet = rewardSet; }
    public void setMaxInstances(int maxInstances) { this.maxInstances = maxInstances; }
    public void setMinPlayers(int minPlayers) { this.minPlayers = minPlayers; }
    public void setMaxPlayers(int maxPlayers) { this.maxPlayers = maxPlayers; }
    public void setType(DungeonType type) { this.type = type != null ? type : DungeonType.DYNAMIC; }
    public void setEntryPoint(Location entryPoint) { this.entryPoint = entryPoint; }

    public boolean isStatic() { return type == DungeonType.STATIC; }
    public boolean isDynamic() { return type == DungeonType.DYNAMIC; }

    public void setMobSpawns(List<Location> mobSpawns) {
        this.mobSpawns = mobSpawns != null ? new ArrayList<>(mobSpawns) : new ArrayList<>();
    }

    public void addMobSpawn(Location location) {
        if (location != null) mobSpawns.add(location);
    }

    /**
     * Check if the dungeon configuration is valid for use.
     * v1.0.7: No longer requires root-level mobSpawns since spawns are stored per-wave.
     */
    public boolean isValid() {
        return pos1 != null && pos2 != null && spawnPoint != null && world != null;
    }
}

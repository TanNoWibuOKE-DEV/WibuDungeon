package com.wibudungeon.core.dungeon;

import com.wibudungeon.core.config.ConfigManager;
import com.wibudungeon.core.party.Party;
import com.wibudungeon.core.party.PartyManager;
import com.wibudungeon.core.reward.RewardManager;
import com.wibudungeon.core.util.MessageUtil;
import com.wibudungeon.core.wave.WaveManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.*;

/**
 * Central manager for dungeon templates, instances, and admin wand state.
 */
public class DungeonManager {

    private final Plugin plugin;
    private final ConfigManager configManager;
    private final PartyManager partyManager;
    private final WaveManager waveManager;
    private final RewardManager rewardManager;
    private com.wibudungeon.core.reward.RewardChestManager rewardChestManager;

    // Active dungeon instances
    private final Map<String, List<DungeonInstance>> activeInstances = new HashMap<>();

    // Admin wand selection state
    private final Map<UUID, Location> pos1Selections = new HashMap<>();
    private final Map<UUID, Location> pos2Selections = new HashMap<>();
    private final Map<UUID, Location> spawnSelections = new HashMap<>();
    private final Map<UUID, List<Location>> mobSpawnSelections = new HashMap<>();
    private final Map<UUID, List<String>> mobSpawnIds = new HashMap<>();

    // Track which dungeon instance a player is in
    private final Map<UUID, DungeonInstance> playerInstances = new HashMap<>();

    public DungeonManager(Plugin plugin, ConfigManager configManager,
                          PartyManager partyManager, WaveManager waveManager,
                          RewardManager rewardManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.partyManager = partyManager;
        this.waveManager = waveManager;
        this.rewardManager = rewardManager;
    }

    /**
     * Start a dungeon for a party or solo player.
     */
    public boolean startDungeon(String dungeonId, Player starter) {
        Dungeon dungeon = configManager.getDungeon(dungeonId);
        if (dungeon == null) {
            MessageUtil.send(starter, configManager.getMessage("dungeon.not-found"));
            return false;
        }

        if (!dungeon.isEnabled() || !dungeon.isValid()) {
            MessageUtil.send(starter, configManager.getMessage("dungeon.not-found"));
            return false;
        }

        // Check max instances
        List<DungeonInstance> instances = activeInstances.getOrDefault(dungeonId, new ArrayList<>());
        if (instances.size() >= dungeon.getMaxInstances()) {
            MessageUtil.send(starter, configManager.getMessage("dungeon.max-instances"));
            return false;
        }

        // Get party members or solo
        List<UUID> members = new ArrayList<>();
        Party party = partyManager.getParty(starter.getUniqueId());

        if (party != null) {
            if (!party.isOwner(starter.getUniqueId())) {
                MessageUtil.send(starter, configManager.getMessage("party.not-owner"));
                return false;
            }
            members.addAll(party.getMembers());
        } else {
            if (!configManager.isAllowSolo()) {
                MessageUtil.send(starter, configManager.getMessage("party.not-in-party"));
                return false;
            }
            members.add(starter.getUniqueId());
        }

        // Check if any member is already in a dungeon
        for (UUID uuid : members) {
            if (playerInstances.containsKey(uuid)) {
                MessageUtil.send(starter, configManager.getMessage("dungeon.already-in-dungeon"));
                return false;
            }
        }

        // Create instance
        String instanceId = dungeonId + "_" + System.currentTimeMillis();
        DungeonInstance instance = new DungeonInstance(instanceId, dungeon, plugin, configManager);

        // Set callbacks
        instance.setOnComplete(() -> {
            rewardManager.giveCompletionRewards(dungeon.getRewardSet(),
                    new ArrayList<>(instance.getAllPlayers()));
            cleanupInstance(instance);
        });
        instance.setOnFail(() -> cleanupInstance(instance));

        // Add players
        for (UUID uuid : members) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                instance.addPlayer(player);
                playerInstances.put(uuid, instance);
            }
        }

        // Track instance
        instances.add(instance);
        activeInstances.put(dungeonId, instances);

        // Start countdown then first wave
        instance.startCountdown(() -> waveManager.startNextWave(instance));

        return true;
    }

    /**
     * Clean up a finished dungeon instance.
     */
    private void cleanupInstance(DungeonInstance instance) {
        // Remove player tracking
        for (UUID uuid : instance.getAllPlayers()) {
            playerInstances.remove(uuid);
        }

        // Remove from active instances
        List<DungeonInstance> instances = activeInstances.get(instance.getDungeon().getId());
        if (instances != null) {
            instances.remove(instance);
        }

        // Clean up reward chests for this instance
        if (rewardChestManager != null) {
            rewardChestManager.cleanupInstance(instance.getInstanceId());
        }

        // Clean up party
        Party party = null;
        for (UUID uuid : instance.getAllPlayers()) {
            party = partyManager.getParty(uuid);
            if (party != null) break;
        }
        if (party != null) {
            partyManager.cleanupParty(party);
        }
    }

    /**
     * Set the reward chest manager for cleanup integration.
     */
    public void setRewardChestManager(com.wibudungeon.core.reward.RewardChestManager manager) {
        this.rewardChestManager = manager;
    }

    /**
     * Get the dungeon instance a player is currently in.
     */
    public DungeonInstance getPlayerInstance(UUID uuid) {
        return playerInstances.get(uuid);
    }

    /**
     * Check if a player is in any dungeon.
     */
    public boolean isInDungeon(UUID uuid) {
        return playerInstances.containsKey(uuid);
    }

    /**
     * Remove a player from their current dungeon instance.
     */
    public boolean leaveDungeon(Player player) {
        DungeonInstance instance = playerInstances.get(player.getUniqueId());
        if (instance == null) return false;

        playerInstances.remove(player.getUniqueId());
        boolean dungeonEnded = instance.removePlayer(player);

        if (dungeonEnded) {
            cleanupInstance(instance);
        }

        return true;
    }

    /**
     * Clean up all active instances (for plugin disable).
     */
    public void cleanupAll() {
        for (List<DungeonInstance> instances : activeInstances.values()) {
            for (DungeonInstance instance : new ArrayList<>(instances)) {
                instance.cleanup();
            }
        }
        activeInstances.clear();
        playerInstances.clear();
    }

    /**
     * Get total count of all active dungeon instances.
     */
    public int getTotalActiveInstances() {
        return activeInstances.values().stream().mapToInt(List::size).sum();
    }

    // ===== ADMIN WAND METHODS =====

    public void setPos1(UUID player, Location loc) {
        pos1Selections.put(player, loc);
    }

    public void setPos2(UUID player, Location loc) {
        pos2Selections.put(player, loc);
    }

    public void setSpawn(UUID player, Location loc) {
        spawnSelections.put(player, loc);
    }

    public void addMobSpawn(UUID player, Location loc) {
        mobSpawnSelections.computeIfAbsent(player, k -> new ArrayList<>()).add(loc);
    }

    public Location getPos1(UUID player) { return pos1Selections.get(player); }
    public Location getPos2(UUID player) { return pos2Selections.get(player); }
    public Location getSpawn(UUID player) { return spawnSelections.get(player); }
    public List<Location> getMobSpawns(UUID player) {
        return mobSpawnSelections.getOrDefault(player, Collections.emptyList());
    }

    /**
     * Save a dungeon from the admin's selections.
     */
    public boolean saveDungeon(UUID playerId, String dungeonId) {
        Location p1 = pos1Selections.get(playerId);
        Location p2 = pos2Selections.get(playerId);
        Location spawn = spawnSelections.get(playerId);

        if (p1 == null || p2 == null || spawn == null) return false;

        Dungeon dungeon = new Dungeon(dungeonId);
        dungeon.setName("&e" + dungeonId);
        dungeon.setWorld(p1.getWorld().getName());
        dungeon.setPos1(p1);
        dungeon.setPos2(p2);
        dungeon.setSpawnPoint(spawn);
        dungeon.setMobSpawns(mobSpawnSelections.getOrDefault(playerId, new ArrayList<>()));

        configManager.saveDungeon(dungeon);

        // Clear selections
        clearSelections(playerId);
        return true;
    }

    /**
     * Clear all admin wand selections for a player.
     */
    public void clearSelections(UUID player) {
        pos1Selections.remove(player);
        pos2Selections.remove(player);
        spawnSelections.remove(player);
        mobSpawnSelections.remove(player);
        mobSpawnIds.remove(player);
    }

    public void addMobSpawnId(UUID player, String mobId) {
        mobSpawnIds.computeIfAbsent(player, k -> new ArrayList<>()).add(mobId);
    }

    public List<String> getMobSpawnIdsList(UUID player) {
        return mobSpawnIds.getOrDefault(player, Collections.emptyList());
    }

    /**
     * Get active instance count for a dungeon.
     */
    public int getActiveInstanceCount(String dungeonId) {
        return activeInstances.getOrDefault(dungeonId, Collections.emptyList()).size();
    }
}

package com.wibudungeon.core.dungeon;

import com.wibudungeon.core.config.ConfigManager;
import com.wibudungeon.core.util.MessageUtil;
import com.wibudungeon.core.wave.Wave;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Represents an active dungeon session with players, mobs, and state.
 */
public class DungeonInstance {

    public enum State {
        WAITING, COUNTDOWN, ACTIVE, COMPLETED, FAILED
    }

    private final String instanceId;
    private final Dungeon dungeon;
    private final Plugin plugin;
    private final ConfigManager configManager;

    private State state = State.WAITING;
    private int currentWaveIndex = -1;
    private final Set<UUID> alivePlayers = new LinkedHashSet<>();
    private final Set<UUID> spectators = new LinkedHashSet<>();
    private final Set<UUID> allPlayers = new LinkedHashSet<>();
    private final Map<UUID, Location> previousLocations = new HashMap<>();
    private final Map<UUID, GameMode> previousGameModes = new HashMap<>();
    private volatile List<LivingEntity> currentMobs = new CopyOnWriteArrayList<>();
    private LivingEntity bossEntity;
    private BossBar bossBar;
    private BukkitTask timerTask;
    private int timeRemaining;
    private int waveCountdown = -1; // -1 means no countdown active
    private final int totalWaves; // v1.0.9: cached at creation, never changes mid-run
    private Runnable onComplete;
    private Runnable onFail;

    public DungeonInstance(String instanceId, Dungeon dungeon, Plugin plugin, ConfigManager configManager) {
        this.instanceId = instanceId;
        this.dungeon = dungeon;
        this.plugin = plugin;
        this.configManager = configManager;
        this.timeRemaining = configManager.getDungeonMaxTime();

        // v1.0.9: Cache total waves at creation using per-dungeon count first
        this.totalWaves = configManager.getDungeonTotalWaves(dungeon.getId());

        this.bossBar = BossBar.bossBar(
                MessageUtil.colorize("&e&lPreparing Dungeon..."),
                1.0f,
                BossBar.Color.YELLOW,
                BossBar.Overlay.PROGRESS
        );
    }

    /**
     * Add a player to this dungeon instance.
     */
    public void addPlayer(Player player) {
        UUID uuid = player.getUniqueId();
        allPlayers.add(uuid);
        alivePlayers.add(uuid);
        previousLocations.put(uuid, player.getLocation().clone());
        previousGameModes.put(uuid, player.getGameMode());

        // Teleport to dungeon spawn (handle cross-world)
        Location spawnPoint = dungeon.getSpawnPoint();
        if (spawnPoint != null && spawnPoint.getWorld() != null) {
            // Ensure chunk is loaded for cross-world teleport
            if (!spawnPoint.getChunk().isLoaded()) {
                spawnPoint.getChunk().load();
            }
            player.teleport(spawnPoint);
        } else {
            // Fallback: try to resolve spawn point from stored world name
            plugin.getLogger().warning("Dungeon " + dungeon.getId()
                    + " has no valid spawn point! Player " + player.getName() + " was not teleported.");
        }
        player.setGameMode(GameMode.SURVIVAL);

        // Show bossbar
        player.showBossBar(bossBar);
    }

    /**
     * Start the dungeon countdown.
     */
    public void startCountdown(Runnable onCountdownFinish) {
        state = State.COUNTDOWN;
        int countdown = configManager.getStartCountdown();

        new org.bukkit.scheduler.BukkitRunnable() {
            int count = countdown;
            @Override
            public void run() {
                if (count <= 0) {
                    cancel();
                    state = State.ACTIVE;
                    String startMsg = configManager.getMessage("dungeon.started");
                    for (UUID uuid : alivePlayers) {
                        Player p = Bukkit.getPlayer(uuid);
                        if (p != null) MessageUtil.send(p, startMsg);
                    }
                    startTimer();
                    onCountdownFinish.run();
                    return;
                }
                String cdMsg = configManager.getMessage("dungeon.starting");
                for (UUID uuid : alivePlayers) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null) {
                        MessageUtil.send(p, cdMsg, "%seconds%", String.valueOf(count));
                    }
                }
                count--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    /**
     * Start the dungeon timer.
     */
    private void startTimer() {
        timerTask = new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                if (!isActive()) {
                    cancel();
                    return;
                }
                timeRemaining--;
                if (timeRemaining <= 0) {
                    cancel();
                    fail();
                    return;
                }
                // Update bossbar progress based on time
                float progress = (float) timeRemaining / configManager.getDungeonMaxTime();
                bossBar.progress(Math.max(0, Math.min(1, progress)));

                // Update time display on bossbar every second
                if (currentWaveIndex >= 0) {
                    List<com.wibudungeon.core.wave.Wave> waves =
                            configManager.getWaveSet(dungeon.getWaveSet());
                    if (currentWaveIndex < waves.size()) {
                        updateBossBar(waves.get(currentWaveIndex));
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    /**
     * Handle a player death.
     */
    public void onPlayerDeath(Player player) {
        UUID uuid = player.getUniqueId();
        if (!alivePlayers.contains(uuid)) return;

        alivePlayers.remove(uuid);
        spectators.add(uuid);

        // Switch to spectator mode
        player.setGameMode(GameMode.SPECTATOR);

        // Announce death
        String deathMsg = configManager.getMessage("dungeon.player-died");
        for (UUID id : allPlayers) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                MessageUtil.send(p, deathMsg, "%player%", player.getName());
            }
        }

        // Check if all players are dead
        if (alivePlayers.isEmpty()) {
            String allDeadMsg = configManager.getMessage("dungeon.all-dead");
            for (UUID id : allPlayers) {
                Player p = Bukkit.getPlayer(id);
                if (p != null) MessageUtil.send(p, allDeadMsg);
            }
            fail();
        }
    }

    /**
     * Complete the dungeon successfully.
     */
    public void complete() {
        if (state == State.COMPLETED || state == State.FAILED) return;
        state = State.COMPLETED;
        String completeMsg = configManager.getMessage("dungeon.completed");
        for (UUID uuid : allPlayers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) MessageUtil.send(p, completeMsg);
        }

        // Global broadcast
        broadcastClear();

        // Run callback BEFORE cleanup so allPlayers is still available for rewards
        if (onComplete != null) onComplete.run();
        cleanup();
    }

    /**
     * Broadcast dungeon clear to all online players with rich formatting.
     */
    private void broadcastClear() {
        // Build player names
        StringBuilder names = new StringBuilder();
        int playerCount = 0;
        for (UUID uuid : allPlayers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                if (!names.isEmpty()) names.append("&f, &b");
                names.append(p.getName());
                playerCount++;
            }
        }
        if (names.isEmpty()) return;

        int waveNum = currentWaveIndex + 1;
        int timeTaken = configManager.getDungeonMaxTime() - timeRemaining;
        int mins = timeTaken / 60;
        int secs = timeTaken % 60;
        String timeStr = mins > 0 ? mins + "m " + secs + "s" : secs + "s";

        // Build rich formatted message
        String border = "&8&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━";
        String header = "&6&l  ⚔ DUNGEON CLEARED! ⚔";
        String playerLine = "&b  " + names.toString();
        String dungeonLine = "&e  conquered &f\"&6" + dungeon.getName() + "&f\"";
        String statsLine = "&7  Wave &e" + waveNum + "&7/&e" + totalWaves
                + " &8| &7Time: &e" + timeStr
                + " &8| &7Players: &e" + playerCount;

        for (Player online : Bukkit.getOnlinePlayers()) {
            online.sendMessage(MessageUtil.colorize(border));
            online.sendMessage(MessageUtil.colorize(""));
            online.sendMessage(MessageUtil.colorize(header));
            online.sendMessage(MessageUtil.colorize(""));
            online.sendMessage(MessageUtil.colorize(playerLine));
            online.sendMessage(MessageUtil.colorize(dungeonLine));
            online.sendMessage(MessageUtil.colorize(statsLine));
            online.sendMessage(MessageUtil.colorize(""));
            online.sendMessage(MessageUtil.colorize(border));
        }

        // Title popup for all online players
        net.kyori.adventure.title.Title.Times times = net.kyori.adventure.title.Title.Times.times(
                java.time.Duration.ofMillis(300),
                java.time.Duration.ofSeconds(3),
                java.time.Duration.ofMillis(1000)
        );
        Component titleComp = MessageUtil.colorize("&6&l⚔ DUNGEON CLEARED! ⚔");
        Component subtitleComp = MessageUtil.colorize("&b" + names + " &7conquered &e" + dungeon.getName());
        net.kyori.adventure.title.Title titleObj = net.kyori.adventure.title.Title.title(titleComp, subtitleComp, times);

        for (Player online : Bukkit.getOnlinePlayers()) {
            online.showTitle(titleObj);
        }

        // Sound effects for dungeon players
        for (UUID uuid : allPlayers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                p.playSound(p.getLocation(), org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
                p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_ENDER_DRAGON_DEATH, 0.5f, 1.2f);
            }
        }
    }

    /**
     * Fail the dungeon (time ran out or all dead).
     */
    public void fail() {
        if (state == State.FAILED || state == State.COMPLETED) return;
        state = State.FAILED;
        String failMsg = configManager.getMessage("dungeon.failed");
        for (UUID uuid : allPlayers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) MessageUtil.send(p, failMsg);
        }
        // Run callback BEFORE cleanup so allPlayers is still available
        if (onFail != null) onFail.run();
        cleanup();
    }

    /**
     * Clean up the instance: teleport players back, remove mobs, cancel tasks.
     */
    public void cleanup() {
        // Cancel timer
        if (timerTask != null) {
            timerTask.cancel();
            timerTask = null;
        }

        // Remove all dungeon mobs
        for (LivingEntity mob : currentMobs) {
            if (mob != null && !mob.isDead()) mob.remove();
        }
        currentMobs.clear();

        // Teleport all players back and restore state
        for (UUID uuid : allPlayers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.hideBossBar(bossBar);
                Location prevLoc = previousLocations.get(uuid);
                if (prevLoc != null && prevLoc.getWorld() != null) {
                    if (!prevLoc.getChunk().isLoaded()) prevLoc.getChunk().load();
                    player.teleport(prevLoc);
                }
                GameMode prevMode = previousGameModes.get(uuid);
                if (prevMode != null) player.setGameMode(prevMode);
                MessageUtil.send(player, configManager.getMessage("dungeon.teleported-out"));
            }
        }

        alivePlayers.clear();
        spectators.clear();
    }

    /**
     * Update the bossbar display.
     * Format: "⚔ Wave X/Y | ⏱ MM:SS | info"
     */
    public void updateBossBar(Wave wave) {
        if (wave == null) return;
        int mins = timeRemaining / 60;
        int secs = timeRemaining % 60;
        String timeStr = String.format("%02d:%02d", mins, secs);

        String format;

        if (waveCountdown > 0) {
            // Countdown between waves
            format = "&e&l⚔ Wave " + wave.getWaveNumber() + "/" + totalWaves
                    + " &8| &f⏱ " + timeStr
                    + " &8| &a⏳ Next: " + waveCountdown + "s";
            bossBar.color(BossBar.Color.GREEN);
        } else if (wave.isBossWave() && bossEntity != null && !bossEntity.isDead()) {
            // Boss fight
            format = "&4&l☠ BOSS FIGHT &8| &f⏱ " + timeStr
                    + " &8| &c❤ " + String.format("%.0f", bossEntity.getHealth());
            bossBar.color(BossBar.Color.RED);
        } else {
            // Normal wave combat
            int mobsLeft = currentMobs.size();
            format = "&e&l⚔ Wave " + wave.getWaveNumber() + "/" + totalWaves
                    + " &8| &f⏱ " + timeStr
                    + " &8| &c" + mobsLeft + " mobs left";
            bossBar.color(BossBar.Color.YELLOW);
        }

        bossBar.name(MessageUtil.colorize(format));
    }

    // Getters and setters
    public String getInstanceId() { return instanceId; }
    public Dungeon getDungeon() { return dungeon; }
    public State getState() { return state; }
    public boolean isActive() { return state == State.ACTIVE; }
    public int getCurrentWaveIndex() { return currentWaveIndex; }
    public void setCurrentWaveIndex(int index) { this.currentWaveIndex = index; }
    public Set<UUID> getAlivePlayers() { return alivePlayers; }
    public Set<UUID> getSpectators() { return spectators; }
    public Set<UUID> getAllPlayers() { return allPlayers; }
    public List<LivingEntity> getCurrentMobs() { return currentMobs; }
    public void setCurrentMobs(List<LivingEntity> mobs) { this.currentMobs = new CopyOnWriteArrayList<>(mobs); }
    public LivingEntity getBossEntity() { return bossEntity; }
    public void setBossEntity(LivingEntity boss) { this.bossEntity = boss; }
    public int getTimeRemaining() { return timeRemaining; }
    public void setOnComplete(Runnable onComplete) { this.onComplete = onComplete; }
    public void setOnFail(Runnable onFail) { this.onFail = onFail; }
    public int getWaveCountdown() { return waveCountdown; }
    public void setWaveCountdown(int countdown) { this.waveCountdown = countdown; }

    public boolean containsPlayer(UUID uuid) {
        return allPlayers.contains(uuid);
    }

    /**
     * Remove a single player from the dungeon (for /wd leave).
     * Returns true if this was the last player (dungeon should end).
     */
    public boolean removePlayer(Player player) {
        UUID uuid = player.getUniqueId();
        if (!allPlayers.contains(uuid)) return false;

        // Hide bossbar
        player.hideBossBar(bossBar);

        // Teleport back
        Location prevLoc = previousLocations.get(uuid);
        if (prevLoc != null && prevLoc.getWorld() != null) {
            if (!prevLoc.getChunk().isLoaded()) prevLoc.getChunk().load();
            player.teleport(prevLoc);
        }
        GameMode prevMode = previousGameModes.get(uuid);
        if (prevMode != null) player.setGameMode(prevMode);

        // Remove from all sets
        allPlayers.remove(uuid);
        alivePlayers.remove(uuid);
        spectators.remove(uuid);
        previousLocations.remove(uuid);
        previousGameModes.remove(uuid);

        MessageUtil.send(player, configManager.getMessage("dungeon.teleported-out"));

        // Announce to remaining players
        for (UUID id : allPlayers) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                MessageUtil.send(p, "&e" + player.getName() + " &7left the dungeon.");
            }
        }

        // If no players left, end dungeon
        if (allPlayers.isEmpty()) {
            fail();
            return true;
        }

        // If no alive players left, fail
        if (alivePlayers.isEmpty()) {
            fail();
            return true;
        }

        return false;
    }
}

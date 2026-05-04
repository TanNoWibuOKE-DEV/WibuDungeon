package com.wibudungeon.core.wave;

import com.wibudungeon.core.config.ConfigManager;
import com.wibudungeon.core.dungeon.DungeonInstance;
import com.wibudungeon.core.mob.MobSpawner;
import com.wibudungeon.core.reward.RewardChestManager;
import com.wibudungeon.core.reward.RewardManager;
import com.wibudungeon.core.reward.Reward;
import com.wibudungeon.core.util.MessageUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Manages wave progression with visible countdown between waves.
 */
public class WaveManager {

    private final Plugin plugin;
    private final ConfigManager configManager;
    private final MobSpawner mobSpawner;
    private final RewardManager rewardManager;
    private RewardChestManager rewardChestManager;

    public WaveManager(Plugin plugin, ConfigManager configManager,
                       MobSpawner mobSpawner, RewardManager rewardManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.mobSpawner = mobSpawner;
        this.rewardManager = rewardManager;
    }

    public void setRewardChestManager(RewardChestManager manager) {
        this.rewardChestManager = manager;
    }

    /**
     * Start the next wave for a dungeon instance.
     */
    public void startNextWave(DungeonInstance instance) {
        int nextIndex = instance.getCurrentWaveIndex() + 1;
        List<Wave> waves = configManager.getWaveSet(instance.getDungeon().getWaveSet());

        if (nextIndex >= waves.size()) {
            instance.complete();
            return;
        }

        instance.setCurrentWaveIndex(nextIndex);
        instance.setWaveCountdown(-1); // Clear countdown
        Wave wave = waves.get(nextIndex);

        // Announce wave start with title
        for (UUID uuid : instance.getAlivePlayers()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                MessageUtil.send(p, configManager.getMessage("dungeon.wave-start"),
                        "%wave%", String.valueOf(wave.getWaveNumber()));

                Component title = MessageUtil.colorize("&c&l⚔ WAVE " + wave.getWaveNumber() + " ⚔");
                Component sub = wave.isBossWave()
                        ? MessageUtil.colorize("&4&lBOSS WAVE!")
                        : MessageUtil.colorize("&eDefeat all enemies!");
                p.showTitle(Title.title(title, sub,
                        Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(2), Duration.ofMillis(500))));
            }
        }

        // Boss wave announcement
        if (wave.isBossWave()) {
            for (UUID uuid : instance.getAlivePlayers()) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null) MessageUtil.send(p, configManager.getMessage("dungeon.boss-spawn"));
            }
        }

        // Spawn mobs after short delay
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!instance.isActive()) return;

                List<LivingEntity> mobs = mobSpawner.spawnWaveMobs(
                        wave, instance.getDungeon().getMobSpawns(), instance.getInstanceId());
                instance.setCurrentMobs(mobs);

                if (wave instanceof BossWave bossWave) {
                    LivingEntity boss = mobSpawner.spawnBoss(bossWave,
                            instance.getDungeon().getMobSpawns().getFirst(), instance.getInstanceId());
                    if (boss != null) {
                        instance.setBossEntity(boss);
                        instance.getCurrentMobs().add(boss);
                    }
                }

                instance.updateBossBar(wave);

                // Safety timer: check 5 seconds after spawn if all mobs are dead
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (!instance.isActive()) return;
                        // Remove dead/invalid entities
                        instance.getCurrentMobs().removeIf(m -> m == null || m.isDead() || !m.isValid());
                        if (instance.getCurrentMobs().isEmpty()) {
                            plugin.getLogger().warning("Safety timer: wave " + wave.getWaveNumber()
                                    + " had no alive mobs — auto-completing.");
                            onWaveComplete(instance);
                        }
                    }
                }.runTaskLater(plugin, 100L); // 5 seconds
            }
        }.runTaskLater(plugin, 20L);
    }

    /**
     * Called when a mob dies in a dungeon instance.
     */
    public void onMobKilled(DungeonInstance instance, LivingEntity entity) {
        instance.getCurrentMobs().remove(entity);

        // Clean up any dead/removed entities that might have been missed
        instance.getCurrentMobs().removeIf(mob -> mob == null || mob.isDead() || !mob.isValid());

        List<Wave> waves = configManager.getWaveSet(instance.getDungeon().getWaveSet());
        if (instance.getCurrentWaveIndex() >= 0 && instance.getCurrentWaveIndex() < waves.size()) {
            instance.updateBossBar(waves.get(instance.getCurrentWaveIndex()));
        }

        if (instance.getCurrentMobs().isEmpty()) {
            onWaveComplete(instance);
        }
    }

    /**
     * Called when a wave is completed. Starts countdown to next wave.
     */
    private void onWaveComplete(DungeonInstance instance) {
        List<Wave> waves = configManager.getWaveSet(instance.getDungeon().getWaveSet());
        Wave completedWave = waves.get(instance.getCurrentWaveIndex());

        // Announce wave complete
        for (UUID uuid : instance.getAlivePlayers()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                MessageUtil.send(p, configManager.getMessage("dungeon.wave-complete"),
                        "%wave%", String.valueOf(completedWave.getWaveNumber()));

                Component title = MessageUtil.colorize("&a&l✔ WAVE " + completedWave.getWaveNumber() + " CLEARED!");
                Component sub = MessageUtil.colorize("&7Prepare for the next wave...");
                p.showTitle(Title.title(title, sub,
                        Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(2), Duration.ofMillis(500))));
            }
        }

        // Clean up previous wave chests
        if (rewardChestManager != null) {
            rewardChestManager.cleanupInstance(instance.getInstanceId());

            // v1.0.6: Try bundle system first, fallback to legacy rewards
            String bundleId = configManager.getWaveBundleId(
                    instance.getDungeon().getId(), completedWave.getWaveNumber());
            com.wibudungeon.core.reward.RewardBundle bundle =
                    bundleId != null ? configManager.getRewardBundle(bundleId) : null;

            if (bundle != null) {
                // Bundle-based chest drops (v1.0.6)
                int chestAmount = configManager.getWaveChestAmountFromWaves(
                        instance.getDungeon().getId(), completedWave.getWaveNumber());
                rewardChestManager.dropRandomChestsFromBundle(
                        instance.getDungeon().getPos1(),
                        instance.getDungeon().getPos2(),
                        chestAmount,
                        bundle,
                        instance.getInstanceId()
                );
            } else {
                // Legacy reward system fallback
                List<Reward> waveRewards = configManager.getWaveRewards(
                        instance.getDungeon().getRewardSet(), completedWave.getWaveNumber());
                int chestAmount = configManager.getWaveChestAmount(
                        instance.getDungeon().getRewardSet(), completedWave.getWaveNumber());
                double diffMult = completedWave.getDifficulty().getMultiplier();

                if (!waveRewards.isEmpty() && chestAmount > 0) {
                    rewardChestManager.dropRandomChests(
                            instance.getDungeon().getPos1(),
                            instance.getDungeon().getPos2(),
                            chestAmount,
                            waveRewards,
                            diffMult,
                            instance.getInstanceId()
                    );
                }
            }
        }

        // Also give direct inventory rewards as fallback
        rewardManager.giveWaveRewards(instance.getDungeon().getRewardSet(),
                completedWave.getWaveNumber(), new ArrayList<>(instance.getAlivePlayers()));

        // Check if this was the last wave
        int nextIndex = instance.getCurrentWaveIndex() + 1;
        if (nextIndex >= waves.size()) {
            // Final wave complete — schedule dungeon completion
            new BukkitRunnable() {
                @Override
                public void run() { if (instance.isActive()) instance.complete(); }
            }.runTaskLater(plugin, 60L);
            return;
        }

        // Start visible countdown to next wave
        int countdownSecs = configManager.getWaveInterval();
        startWaveCountdown(instance, countdownSecs);
    }

    /**
     * Visible countdown between waves with title/actionbar.
     */
    private void startWaveCountdown(DungeonInstance instance, int seconds) {
        instance.setWaveCountdown(seconds);

        new BukkitRunnable() {
            int remaining = seconds;

            @Override
            public void run() {
                if (!instance.isActive()) { cancel(); return; }

                if (remaining <= 0) {
                    cancel();
                    instance.setWaveCountdown(-1);
                    startNextWave(instance);
                    return;
                }

                instance.setWaveCountdown(remaining);

                // Update bossbar to show countdown
                List<Wave> waves = configManager.getWaveSet(instance.getDungeon().getWaveSet());
                if (instance.getCurrentWaveIndex() >= 0 && instance.getCurrentWaveIndex() < waves.size()) {
                    instance.updateBossBar(waves.get(instance.getCurrentWaveIndex()));
                }

                // Show title at key moments
                if (remaining <= 5 || remaining == 10) {
                    for (UUID uuid : instance.getAlivePlayers()) {
                        Player p = Bukkit.getPlayer(uuid);
                        if (p != null) {
                            Component title = MessageUtil.colorize("&e&lNext Wave In:");
                            Component sub = MessageUtil.colorize("&c&l" + remaining + "s");
                            p.showTitle(Title.title(title, sub,
                                    Title.Times.times(Duration.ofMillis(0), Duration.ofMillis(1100), Duration.ofMillis(200))));
                        }
                    }
                }

                // Actionbar every second
                for (UUID uuid : instance.getAlivePlayers()) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null) {
                        p.sendActionBar(MessageUtil.colorize(
                                "&7⏳ Next wave in &e" + remaining + "s &7| Prepare yourself!"));
                    }
                }

                remaining--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    public int getTotalWaves(String waveSetId) {
        return configManager.getWaveSet(waveSetId).size();
    }
}

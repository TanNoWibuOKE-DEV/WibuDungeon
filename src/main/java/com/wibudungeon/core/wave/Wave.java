package com.wibudungeon.core.wave;

import com.wibudungeon.core.dungeon.Difficulty;

import java.util.Collections;
import java.util.Map;

/**
 * Represents a single wave in a dungeon with mob types, counts, and scaling.
 * Mob keys use String format:
 * - Vanilla mobs: "ZOMBIE", "SKELETON", etc.
 * - MythicMobs: "mm:MobInternalName" (prefixed with "mm:")
 */
public class Wave {

    private final int waveNumber;
    private final Map<String, Integer> mobs;
    private final double healthMultiplier;
    private final double damageMultiplier;
    private final double speedMultiplier;
    private final int timeLimit;
    private final Difficulty difficulty;

    public Wave(int waveNumber, Map<String, Integer> mobs,
                double healthMultiplier, double damageMultiplier,
                double speedMultiplier, int timeLimit, Difficulty difficulty) {
        this.waveNumber = waveNumber;
        this.mobs = mobs;
        this.healthMultiplier = healthMultiplier;
        this.damageMultiplier = damageMultiplier;
        this.speedMultiplier = speedMultiplier;
        this.timeLimit = timeLimit;
        this.difficulty = difficulty != null ? difficulty : Difficulty.NORMAL;
    }

    public int getWaveNumber() {
        return waveNumber;
    }

    /**
     * Get the mob map. Keys are either vanilla EntityType names or "mm:MythicMobId".
     */
    public Map<String, Integer> getMobs() {
        return Collections.unmodifiableMap(mobs);
    }

    public double getHealthMultiplier() {
        return healthMultiplier;
    }

    public double getDamageMultiplier() {
        return damageMultiplier;
    }

    public double getSpeedMultiplier() {
        return speedMultiplier;
    }

    public int getTimeLimit() {
        return timeLimit;
    }

    public Difficulty getDifficulty() {
        return difficulty;
    }

    /**
     * Get total number of mobs in this wave.
     */
    public int getTotalMobCount() {
        return mobs.values().stream().mapToInt(Integer::intValue).sum();
    }

    /**
     * Whether this wave is a boss wave. Overridden by BossWave.
     */
    public boolean isBossWave() {
        return false;
    }
}

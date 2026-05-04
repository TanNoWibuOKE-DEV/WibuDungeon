package com.wibudungeon.core.wave;

import com.wibudungeon.core.dungeon.Difficulty;
import org.bukkit.potion.PotionEffectType;

import java.util.Collections;
import java.util.Map;

/**
 * Represents a boss wave with special boss mob configuration.
 * Boss type uses String format: vanilla "WITHER_SKELETON" or MythicMobs "mm:BossId".
 */
public class BossWave extends Wave {

    private final String bossType;
    private final String bossName;
    private final double bossHealth;
    private final Map<PotionEffectType, Integer> bossEffects;

    public BossWave(int waveNumber, Map<String, Integer> mobs,
                    double healthMultiplier, double damageMultiplier,
                    double speedMultiplier, int timeLimit, Difficulty difficulty,
                    String bossType, String bossName, double bossHealth,
                    Map<PotionEffectType, Integer> bossEffects) {
        super(waveNumber, mobs, healthMultiplier, damageMultiplier, speedMultiplier, timeLimit, difficulty);
        this.bossType = bossType;
        this.bossName = bossName;
        this.bossHealth = bossHealth;
        this.bossEffects = bossEffects != null ? bossEffects : Collections.emptyMap();
    }

    @Override
    public boolean isBossWave() {
        return true;
    }

    /**
     * Get the boss type string. Can be vanilla EntityType name or "mm:MythicMobId".
     */
    public String getBossType() {
        return bossType;
    }

    public String getBossName() {
        return bossName;
    }

    public double getBossHealth() {
        return bossHealth;
    }

    public Map<PotionEffectType, Integer> getBossEffects() {
        return Collections.unmodifiableMap(bossEffects);
    }
}

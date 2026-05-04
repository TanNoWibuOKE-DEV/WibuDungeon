package com.wibudungeon.core.mob;

import com.wibudungeon.core.util.MessageUtil;
import com.wibudungeon.core.wave.BossWave;
import com.wibudungeon.core.wave.Wave;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Responsible for spawning mobs during dungeon waves.
 * Supports both vanilla mobs and MythicMobs (via "mm:" prefix).
 */
public class MobSpawner {

    private static final String DUNGEON_MOB_KEY = "wibudungeon_mob";
    private static final String DUNGEON_INSTANCE_KEY = "wibudungeon_instance";

    private final Plugin plugin;
    private final MythicMobsHook mythicHook;

    public MobSpawner(Plugin plugin, MythicMobsHook mythicHook) {
        this.plugin = plugin;
        this.mythicHook = mythicHook;
    }

    /**
     * Spawn all mobs for a wave at the given spawn locations.
     *
     * @param wave           the wave definition
     * @param spawnLocations available spawn locations
     * @param instanceId     the dungeon instance ID for tracking
     * @return list of spawned entities
     */
    public List<LivingEntity> spawnWaveMobs(Wave wave, List<Location> spawnLocations, String instanceId) {
        List<LivingEntity> spawned = new ArrayList<>();
        if (spawnLocations.isEmpty()) return spawned;

        int locationIndex = 0;

        for (Map.Entry<String, Integer> entry : wave.getMobs().entrySet()) {
            String mobId = entry.getKey();
            int count = entry.getValue();

            for (int i = 0; i < count; i++) {
                Location loc = spawnLocations.get(locationIndex % spawnLocations.size());
                locationIndex++;

                // Add slight random offset to avoid stacking
                Location spawnLoc = loc.clone().add(
                        (Math.random() - 0.5) * 4,
                        0,
                        (Math.random() - 0.5) * 4
                );

                LivingEntity living = spawnMob(mobId, spawnLoc);
                if (living != null) {
                    double diffMult = wave.getDifficulty().getMultiplier();
                    // Apply vanilla scaling only for non-MythicMobs (MythicMobs handles its own scaling)
                    if (!MythicMobsHook.isMythicMob(mobId)) {
                        MobScaling.applyScaling(living,
                                wave.getHealthMultiplier() * diffMult,
                                wave.getDamageMultiplier() * diffMult,
                                wave.getSpeedMultiplier());
                    }

                    // Tag as dungeon mob with wave info
                    tagDungeonMob(living, instanceId, false, wave.getWaveNumber(), mobId);
                    spawned.add(living);
                }
            }
        }

        return spawned;
    }

    /**
     * Spawn the boss mob for a boss wave.
     *
     * @param bossWave       the boss wave definition
     * @param spawnLocation  where to spawn the boss
     * @param instanceId     the dungeon instance ID
     * @return the spawned boss entity
     */
    public LivingEntity spawnBoss(BossWave bossWave, Location spawnLocation, String instanceId) {
        String bossId = bossWave.getBossType();
        LivingEntity boss;

        if (MythicMobsHook.isMythicMob(bossId)) {
            // Spawn MythicMobs boss
            String mythicName = MythicMobsHook.getMythicName(bossId);
            boss = mythicHook.spawnMob(mythicName, spawnLocation);
            if (boss != null) {
                // MythicMobs handles its own stats, but we set the custom name from config
                boss.customName(MessageUtil.colorize(bossWave.getBossName()));
                boss.setCustomNameVisible(true);
            }
        } else {
            // Spawn vanilla boss
            try {
                EntityType type = EntityType.valueOf(bossId);
                Entity entity = spawnLocation.getWorld().spawnEntity(spawnLocation, type);
                if (entity instanceof LivingEntity living) {
                    boss = living;
                    double diffMult = bossWave.getDifficulty().getMultiplier();
                    MobScaling.setBossHealth(boss, bossWave.getBossHealth() * diffMult);

                    // Apply boss effects
                    for (Map.Entry<PotionEffectType, Integer> effect : bossWave.getBossEffects().entrySet()) {
                        MobScaling.applyEffect(boss, effect.getKey(), effect.getValue());
                    }

                    boss.customName(MessageUtil.colorize(bossWave.getBossName()));
                    boss.setCustomNameVisible(true);
                } else {
                    return null;
                }
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid boss type: " + bossId);
                return null;
            }
        }

        if (boss != null) {
            tagDungeonMob(boss, instanceId, true, 0, bossId);
        }

        return boss;
    }

    /**
     * Spawn a single mob by its ID string.
     * Supports "mm:MythicMobId" for MythicMobs or vanilla EntityType names.
     * Includes chunk loading, retry logic, and spawn validation.
     *
     * @param mobId    the mob identifier
     * @param location the spawn location
     * @return the spawned LivingEntity, or null if failed after retries
     */
    public LivingEntity spawnMob(String mobId, Location location) {
        if (location == null || location.getWorld() == null) {
            plugin.getLogger().warning("Cannot spawn mob '" + mobId + "': invalid location!");
            return null;
        }

        // Force-load chunk to prevent invisible mobs
        if (!location.getChunk().isLoaded()) {
            location.getChunk().load(true);
        }

        // Retry up to 3 times on failure
        for (int attempt = 1; attempt <= 3; attempt++) {
            LivingEntity entity = attemptSpawn(mobId, location);
            if (entity != null && entity.isValid() && !entity.isDead()) {
                return entity;
            }

            // Offset location slightly for retry
            if (attempt < 3) {
                location = location.clone().add(
                        (Math.random() - 0.5) * 2,
                        0,
                        (Math.random() - 0.5) * 2
                );
                // Ensure retry chunk is also loaded
                if (!location.getChunk().isLoaded()) {
                    location.getChunk().load(true);
                }
            }
        }

        plugin.getLogger().warning("Failed to spawn mob '" + mobId + "' after 3 attempts at "
                + location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ());
        return null;
    }

    /**
     * Single spawn attempt (no retry).
     */
    private LivingEntity attemptSpawn(String mobId, Location location) {
        if (MythicMobsHook.isMythicMob(mobId)) {
            if (!mythicHook.isEnabled()) {
                plugin.getLogger().warning("Cannot spawn MythicMob '" + mobId
                        + "': MythicMobs is not installed!");
                return null;
            }
            String mythicName = MythicMobsHook.getMythicName(mobId);
            if (!mythicHook.mobExists(mythicName)) {
                plugin.getLogger().warning("MythicMob '" + mythicName + "' does not exist!");
                return null;
            }
            return mythicHook.spawnMob(mythicName, location);
        } else {
            try {
                EntityType type = EntityType.valueOf(mobId);
                Entity entity = location.getWorld().spawnEntity(location, type);
                if (entity instanceof LivingEntity living) {
                    living.setRemoveWhenFarAway(false);
                    living.setPersistent(true);
                    return living;
                }
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid mob type: " + mobId);
            }
            return null;
        }
    }

    /**
     * Tag an entity as a dungeon mob with metadata.
     */
    private void tagDungeonMob(LivingEntity entity, String instanceId, boolean isBoss, int waveNumber, String mobId) {
        entity.setMetadata(DUNGEON_MOB_KEY, new FixedMetadataValue(plugin, true));
        entity.setMetadata(DUNGEON_INSTANCE_KEY, new FixedMetadataValue(plugin, instanceId));
        if (isBoss) {
            entity.setMetadata("wibudungeon_boss", new FixedMetadataValue(plugin, true));
        }
        entity.setRemoveWhenFarAway(false);
        entity.setPersistent(true);

        // Glowing effect for all dungeon mobs
        entity.setGlowing(true);

        // Custom name: [Wave X] MobName (skip if boss already has custom name)
        if (!isBoss) {
            String cleanName = mobId.startsWith("mm:") ? mobId.substring(3) : mobId;
            cleanName = cleanName.substring(0, 1).toUpperCase() + cleanName.substring(1).toLowerCase().replace("_", " ");
            String nameFormat = "&c[Wave " + waveNumber + "] &f" + cleanName;
            entity.customName(MessageUtil.colorize(nameFormat));
            entity.setCustomNameVisible(true);
        }
    }

    /**
     * Check if a mob ID is valid (either vanilla or MythicMobs).
     */
    public boolean isValidMobId(String mobId) {
        if (MythicMobsHook.isMythicMob(mobId)) {
            String mythicName = MythicMobsHook.getMythicName(mobId);
            return mythicHook.isEnabled() && mythicHook.mobExists(mythicName);
        } else {
            try {
                EntityType.valueOf(mobId);
                return true;
            } catch (IllegalArgumentException e) {
                return false;
            }
        }
    }

    // ===== STATIC HELPERS =====

    public static boolean isDungeonMob(Entity entity) {
        return entity.hasMetadata(DUNGEON_MOB_KEY);
    }

    public static String getInstanceId(Entity entity) {
        if (!entity.hasMetadata(DUNGEON_INSTANCE_KEY)) return null;
        return entity.getMetadata(DUNGEON_INSTANCE_KEY).getFirst().asString();
    }

    public static boolean isBoss(Entity entity) {
        return entity.hasMetadata("wibudungeon_boss");
    }

    /**
     * Get the MythicMobs hook reference.
     */
    public MythicMobsHook getMythicHook() {
        return mythicHook;
    }
}

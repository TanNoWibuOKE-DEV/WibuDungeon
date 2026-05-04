package com.wibudungeon.core.mob;

import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Handles scaling of mob stats based on wave multipliers.
 */
public final class MobScaling {

    private MobScaling() {
    }

    /**
     * Apply scaling to a mob based on wave multipliers.
     *
     * @param entity           the mob entity
     * @param healthMultiplier health scaling factor
     * @param damageMultiplier damage scaling factor
     * @param speedMultiplier  speed scaling factor
     */
    public static void applyScaling(LivingEntity entity,
                                     double healthMultiplier,
                                     double damageMultiplier,
                                     double speedMultiplier) {
        // Scale health
        AttributeInstance healthAttr = entity.getAttribute(Attribute.MAX_HEALTH);
        if (healthAttr != null) {
            double newHealth = healthAttr.getBaseValue() * healthMultiplier;
            healthAttr.setBaseValue(newHealth);
            entity.setHealth(newHealth);
        }

        // Scale damage
        AttributeInstance damageAttr = entity.getAttribute(Attribute.ATTACK_DAMAGE);
        if (damageAttr != null) {
            damageAttr.setBaseValue(damageAttr.getBaseValue() * damageMultiplier);
        }

        // Scale speed
        AttributeInstance speedAttr = entity.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speedAttr != null) {
            speedAttr.setBaseValue(speedAttr.getBaseValue() * speedMultiplier);
        }
    }

    /**
     * Apply a potion effect to an entity with infinite duration.
     */
    public static void applyEffect(LivingEntity entity, PotionEffectType effectType, int amplifier) {
        entity.addPotionEffect(new PotionEffect(
                effectType,
                PotionEffect.INFINITE_DURATION,
                amplifier,
                false,  // ambient
                false,  // particles
                true    // icon
        ));
    }

    /**
     * Set the boss health to a specific value (not multiplied).
     */
    public static void setBossHealth(LivingEntity entity, double health) {
        AttributeInstance healthAttr = entity.getAttribute(Attribute.MAX_HEALTH);
        if (healthAttr != null) {
            healthAttr.setBaseValue(health);
            entity.setHealth(health);
        }
    }
}

package com.wibudungeon.core.portal;

import org.bukkit.Location;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Interaction;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Represents a dungeon portal using Display Entities.
 * Portal is built as an obsidian frame with nether portal center blocks.
 *
 * Tracks spawn time, expiry, and usage state to prevent ghost entities.
 */
public class DungeonPortal {

    private final UUID portalId;
    private final Location location;
    private final String dungeonId;
    private final long spawnTime;
    private final long expiryTime;
    private final List<BlockDisplay> displayEntities = new ArrayList<>();
    private Interaction interactionEntity;
    private boolean isUsed = false;

    public DungeonPortal(Location location, String dungeonId, long durationMillis) {
        this.portalId = UUID.randomUUID();
        this.location = location;
        this.dungeonId = dungeonId;
        this.spawnTime = System.currentTimeMillis();
        this.expiryTime = this.spawnTime + durationMillis;
    }

    public UUID getPortalId() { return portalId; }
    public Location getLocation() { return location; }
    public String getDungeonId() { return dungeonId; }
    public long getSpawnTime() { return spawnTime; }
    public long getExpiryTime() { return expiryTime; }

    public boolean isExpired() {
        return System.currentTimeMillis() >= expiryTime;
    }

    /**
     * Get remaining time in seconds before portal expires.
     */
    public int getRemainingSeconds() {
        long remaining = expiryTime - System.currentTimeMillis();
        return remaining > 0 ? (int) (remaining / 1000) : 0;
    }

    public boolean isUsed() { return isUsed; }

    /**
     * Mark this portal as used (a player entered).
     * Used portals may have different cleanup behavior.
     */
    public void markUsed() { this.isUsed = true; }

    public List<BlockDisplay> getDisplayEntities() { return displayEntities; }

    public void addDisplayEntity(BlockDisplay entity) {
        displayEntities.add(entity);
    }

    public Interaction getInteractionEntity() { return interactionEntity; }

    public void setInteractionEntity(Interaction interactionEntity) {
        this.interactionEntity = interactionEntity;
    }

    /**
     * Remove all display entities from the world.
     * Ensures no ghost entities remain.
     */
    public void remove() {
        for (BlockDisplay display : displayEntities) {
            if (display != null && !display.isDead()) {
                // v1.0.9: Ensure chunk is loaded so remove() actually works
                if (!display.getLocation().getChunk().isLoaded()) {
                    display.getLocation().getChunk().load();
                }
                display.remove();
            }
        }
        displayEntities.clear();
        if (interactionEntity != null && !interactionEntity.isDead()) {
            if (!interactionEntity.getLocation().getChunk().isLoaded()) {
                interactionEntity.getLocation().getChunk().load();
            }
            interactionEntity.remove();
            interactionEntity = null;
        }
    }

    /**
     * Check if the portal entities are still valid.
     */
    public boolean isValid() {
        if (interactionEntity == null || interactionEntity.isDead()) return false;
        return !displayEntities.isEmpty() && displayEntities.stream().anyMatch(d -> !d.isDead());
    }

    /**
     * Get the center of the portal (for proximity checks).
     * Portal is 4 wide (x: 0..3) × 5 tall (y: 0..4).
     */
    public Location getCenter() {
        return location.clone().add(1.5, 2.5, 0);
    }
}

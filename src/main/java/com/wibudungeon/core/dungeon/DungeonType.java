package com.wibudungeon.core.dungeon;

/**
 * Defines the two dungeon operation modes.
 *
 * DYNAMIC: Random portal spawn in world, instance-based, portal disappears after use.
 * STATIC:  Permanent dungeon location with fixed entry point, always available.
 *
 * @since v1.0.7
 */
public enum DungeonType {
    DYNAMIC,
    STATIC;

    /**
     * Parse a string to DungeonType, defaulting to DYNAMIC for backward compatibility.
     */
    public static DungeonType fromString(String str) {
        if (str == null) return DYNAMIC;
        try {
            return valueOf(str.toUpperCase());
        } catch (IllegalArgumentException e) {
            return DYNAMIC;
        }
    }
}

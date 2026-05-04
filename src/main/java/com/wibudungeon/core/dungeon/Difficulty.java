package com.wibudungeon.core.dungeon;

public enum Difficulty {
    NORMAL(1.0),
    HARD(2.0),
    SUPER_DIFFICULT(3.0),
    NIGHTMARE(5.0);

    private final double multiplier;

    Difficulty(double multiplier) {
        this.multiplier = multiplier;
    }

    public double getMultiplier() {
        return multiplier;
    }

    public String getDisplayName() {
        return switch (this) {
            case NORMAL -> "&aNormal";
            case HARD -> "&eHard";
            case SUPER_DIFFICULT -> "&cSuper Difficult";
            case NIGHTMARE -> "&4&lNightmare";
        };
    }
}

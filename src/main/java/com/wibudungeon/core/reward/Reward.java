package com.wibudungeon.core.reward;

import org.bukkit.inventory.ItemStack;

/**
 * Represents a single reward that can be given to a player.
 * Rewards can be items, commands, or money.
 */
public class Reward {

    public enum RewardType {
        ITEM, COMMAND, MONEY
    }

    private final RewardType type;
    private ItemStack item;
    private String command;
    private double money;
    private double chance = 1.0;

    private Reward(RewardType type) {
        this.type = type;
    }

    /**
     * Create an item reward.
     */
    public static Reward ofItem(ItemStack item) {
        Reward reward = new Reward(RewardType.ITEM);
        reward.item = item;
        return reward;
    }

    public static Reward ofItem(ItemStack item, double chance) {
        Reward reward = new Reward(RewardType.ITEM);
        reward.item = item;
        reward.chance = chance;
        return reward;
    }

    /**
     * Create a command reward. Use %player% as placeholder for player name.
     */
    public static Reward ofCommand(String command) {
        Reward reward = new Reward(RewardType.COMMAND);
        reward.command = command;
        return reward;
    }

    /**
     * Create a money reward.
     */
    public static Reward ofMoney(double money) {
        Reward reward = new Reward(RewardType.MONEY);
        reward.money = money;
        return reward;
    }

    public RewardType getType() {
        return type;
    }

    public ItemStack getItem() {
        return item != null ? item.clone() : null;
    }

    public String getCommand() {
        return command;
    }

    public double getMoney() {
        return money;
    }

    public double getChance() {
        return chance;
    }

    public void setChance(double chance) {
        this.chance = chance;
    }
}

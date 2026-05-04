package com.wibudungeon.core.reward;

import com.wibudungeon.core.config.ConfigManager;
import com.wibudungeon.core.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

/**
 * Distributes rewards to players after wave completions and dungeon clears.
 */
public class RewardManager {

    private final ConfigManager configManager;

    public RewardManager(ConfigManager configManager) {
        this.configManager = configManager;
    }

    /**
     * Give wave rewards to all party members.
     */
    public void giveWaveRewards(String rewardSet, int waveNumber, List<UUID> players) {
        List<Reward> rewards = configManager.getWaveRewards(rewardSet, waveNumber);
        if (rewards.isEmpty()) return;

        for (UUID uuid : players) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline()) continue;
            giveRewards(player, rewards);
            MessageUtil.send(player, configManager.getMessage("reward.received"),
                    "%wave%", String.valueOf(waveNumber));
        }
    }

    /**
     * Give completion rewards to all party members.
     */
    public void giveCompletionRewards(String rewardSet, List<UUID> players) {
        List<Reward> rewards = configManager.getCompletionRewards(rewardSet);
        if (rewards.isEmpty()) return;

        for (UUID uuid : players) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline()) continue;
            giveRewards(player, rewards);
            MessageUtil.send(player, configManager.getMessage("reward.completion"));
        }
    }

    /**
     * Give a list of rewards to a single player.
     */
    private void giveRewards(Player player, List<Reward> rewards) {
        for (Reward reward : rewards) {
            switch (reward.getType()) {
                case ITEM -> {
                    var leftover = player.getInventory().addItem(reward.getItem());
                    // Drop items that don't fit
                    leftover.values().forEach(item ->
                            player.getWorld().dropItemNaturally(player.getLocation(), item));
                }
                case COMMAND -> {
                    String cmd = reward.getCommand().replace("%player%", player.getName());
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
                }
                case MONEY -> {
                    // Vault integration placeholder - log for now
                    MessageUtil.send(player, configManager.getMessage("reward.money"),
                            "%amount%", String.format("%.0f", reward.getMoney()));
                }
            }
        }
    }
}

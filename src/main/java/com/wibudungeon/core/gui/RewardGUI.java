package com.wibudungeon.core.gui;

import com.wibudungeon.core.config.ConfigManager;
import com.wibudungeon.core.reward.Reward;
import com.wibudungeon.core.util.ItemBuilder;
import com.wibudungeon.core.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.List;

/**
 * Read-only GUI showing rewards configured for a specific wave.
 * Displays items from rewards.yml with material, amount, and chance.
 */
public class RewardGUI {

    public static final String TITLE = "§8§l» §6§lWave Rewards §8§l«";

    private final ConfigManager configManager;

    public RewardGUI(ConfigManager configManager) {
        this.configManager = configManager;
    }

    /**
     * Open the reward view GUI for a specific wave.
     */
    public void open(Player player, String rewardSetId, int waveNum) {
        List<Reward> rewards = configManager.getWaveRewards(rewardSetId, waveNum);
        int chestAmount = configManager.getWaveChestAmount(rewardSetId, waveNum);

        int size = Math.max(27, ((rewards.size() / 7) + 1) * 9 + 18);
        size = Math.min(54, size);

        Inventory gui = Bukkit.createInventory(null, size,
                MessageUtil.colorize(TITLE + " - Wave " + waveNum));

        // Header
        gui.setItem(4, new ItemBuilder(Material.GOLD_INGOT)
                .name("&6&lReward Info")
                .lore("&7",
                       "&e  Wave: &f" + waveNum,
                       "&e  Reward Set: &f" + rewardSetId,
                       "&e  Chest Amount: &f" + chestAmount,
                       "&e  Total Rewards: &f" + rewards.size(),
                       "&7")
                .glow().build());

        // Display rewards
        int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
        for (int i = 0; i < rewards.size() && i < slots.length; i++) {
            Reward reward = rewards.get(i);

            switch (reward.getType()) {
                case ITEM -> {
                    if (reward.getItem() != null) {
                        ItemBuilder builder = new ItemBuilder(reward.getItem().getType())
                                .amount(reward.getItem().getAmount())
                                .name("&e" + reward.getItem().getType().name());

                        String chancePct = String.format("%.0f%%", reward.getChance() * 100);
                        builder.lore("&7",
                                "&e  Amount: &f" + reward.getItem().getAmount(),
                                "&e  Chance: &f" + chancePct,
                                "&7",
                                "&7  (from rewards.yml)",
                                "&7");
                        gui.setItem(slots[i], builder.build());
                    }
                }
                case COMMAND -> {
                    gui.setItem(slots[i], new ItemBuilder(Material.COMMAND_BLOCK)
                            .name("&d&lCommand Reward")
                            .lore("&7",
                                   "&e  " + reward.getCommand(),
                                   "&7")
                            .build());
                }
                case MONEY -> {
                    gui.setItem(slots[i], new ItemBuilder(Material.SUNFLOWER)
                            .name("&a&l$ Money Reward")
                            .lore("&7",
                                   "&e  Amount: &f$" + String.format("%.0f", reward.getMoney()),
                                   "&7")
                            .build());
                }
            }
        }

        // Bottom bar
        int bottomStart = size - 9;
        gui.setItem(bottomStart + 4, new ItemBuilder(Material.ARROW)
                .name("&7&lBack")
                .build());

        // Fill empty
        for (int i = 0; i < gui.getSize(); i++) {
            if (gui.getItem(i) == null) {
                gui.setItem(i, new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name("&7").build());
            }
        }

        player.openInventory(gui);
    }
}

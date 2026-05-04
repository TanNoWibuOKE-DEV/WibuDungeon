package com.wibudungeon.core.gui;

import com.wibudungeon.core.config.ConfigManager;
import com.wibudungeon.core.reward.RewardBundle;
import com.wibudungeon.core.util.ItemBuilder;
import com.wibudungeon.core.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * GUI for selecting a reward bundle for a wave during setup mode.
 * Displays all available bundles from rewards.yml with item previews.
 */
public class BundleSelectGUI {

    public static final String TITLE = "§8§l» §6§lSelect Reward Bundle §8§l«";

    private final ConfigManager configManager;

    public BundleSelectGUI(ConfigManager configManager) {
        this.configManager = configManager;
    }

    /**
     * Open the bundle selection GUI for a specific wave.
     */
    public void open(Player player, int waveNum, String currentBundleId) {
        Inventory gui = Bukkit.createInventory(null, 54, MessageUtil.colorize(TITLE + " - Wave " + waveNum));

        // Header
        gui.setItem(4, new ItemBuilder(Material.CHEST)
                .name("&6&l🎁 Select Reward Bundle")
                .lore("&7", "&e  Current: &f" + (currentBundleId != null ? currentBundleId : "None"),
                       "&7", "&7  Click a bundle below to select", "&7")
                .glow().build());

        // Display all available bundles
        Map<String, RewardBundle> bundles = configManager.getRewardBundles();
        int[] bundleSlots = {
                10, 11, 12, 13, 14, 15, 16,
                19, 20, 21, 22, 23, 24, 25,
                28, 29, 30, 31, 32, 33, 34
        };

        int slotIdx = 0;
        for (Map.Entry<String, RewardBundle> entry : bundles.entrySet()) {
            if (slotIdx >= bundleSlots.length) break;

            String bundleId = entry.getKey();
            RewardBundle bundle = entry.getValue();
            boolean isSelected = bundleId.equals(currentBundleId);

            // Determine icon material based on bundle name
            Material icon = getBundleIcon(bundleId);

            // Build lore with item list
            List<String> lore = new ArrayList<>();
            lore.add("&7");
            lore.add("&e  Bundle ID: &f" + bundleId);
            lore.add("&e  Items: &f" + bundle.getItems().size());
            lore.add("&7");
            for (RewardBundle.BundleItem item : bundle.getItems()) {
                String materialName = item.getMaterial().name().toLowerCase().replace("_", " ");
                materialName = Character.toUpperCase(materialName.charAt(0)) + materialName.substring(1);
                lore.add("&7  &f" + item.getAmount() + "x &e" + materialName
                        + " &7(" + item.getChancePercent() + ")");
            }
            lore.add("&7");
            if (isSelected) {
                lore.add("&a  ✔ Currently Selected");
            } else {
                lore.add("&a  Click to select");
            }
            lore.add("&7");

            ItemBuilder builder = new ItemBuilder(icon)
                    .name((isSelected ? "&a&l✔ " : "&e&l") + formatBundleName(bundleId))
                    .lore(lore);
            if (isSelected) builder.glow();

            gui.setItem(bundleSlots[slotIdx], builder.build());
            slotIdx++;
        }

        // "None" option to unset bundle
        gui.setItem(48, new ItemBuilder(Material.GLASS)
                .name("&7&lNo Bundle")
                .lore("&7", "&7  Remove bundle from this wave", "&7", "&c  Click to unset", "&7")
                .build());

        // Back button
        gui.setItem(49, new ItemBuilder(Material.ARROW)
                .name("&7&lBack to Wave Edit")
                .build());

        // Fill empty
        for (int i = 0; i < gui.getSize(); i++) {
            if (gui.getItem(i) == null) {
                gui.setItem(i, new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name("&7").build());
            }
        }

        player.openInventory(gui);
    }

    /**
     * Get an icon material based on the bundle name.
     */
    private Material getBundleIcon(String bundleId) {
        String lower = bundleId.toLowerCase();
        if (lower.contains("legendary") || lower.contains("epic")) return Material.NETHER_STAR;
        if (lower.contains("rare")) return Material.DIAMOND;
        if (lower.contains("uncommon")) return Material.GOLD_INGOT;
        if (lower.contains("common")) return Material.IRON_INGOT;
        if (lower.contains("boss")) return Material.DRAGON_HEAD;
        return Material.CHEST;
    }

    /**
     * Format a bundle ID into a display name.
     * "bundle_common" → "Bundle Common"
     */
    private String formatBundleName(String bundleId) {
        String[] parts = bundleId.split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!sb.isEmpty()) sb.append(" ");
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1).toLowerCase());
        }
        return sb.toString();
    }
}

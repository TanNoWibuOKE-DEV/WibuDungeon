package com.wibudungeon.core.reward;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Represents a reward bundle — a named collection of items with drop chances.
 * Bundles are defined in rewards.yml and referenced by wave configs.
 *
 * Example YAML:
 *   bundle_common:
 *     items:
 *       diamond:
 *         material: DIAMOND
 *         amount: 2
 *         chance: 0.3
 *       gold:
 *         material: GOLD_INGOT
 *         amount: 5
 *         chance: 0.6
 */
public class RewardBundle {

    private final String id;
    private final List<BundleItem> items;

    public RewardBundle(String id, List<BundleItem> items) {
        this.id = id;
        this.items = Collections.unmodifiableList(items);
    }

    public String getId() { return id; }
    public List<BundleItem> getItems() { return items; }

    /**
     * Roll all items in this bundle based on their individual chances.
     * Returns a list of ItemStacks that passed the chance check.
     */
    public List<ItemStack> rollItems() {
        List<ItemStack> result = new ArrayList<>();
        for (BundleItem item : items) {
            double roll = ThreadLocalRandom.current().nextDouble();
            if (roll <= item.getChance()) {
                result.add(item.toItemStack());
            }
        }
        return result;
    }

    /**
     * Get all items without rolling chance (for preview/GUI display).
     */
    public List<ItemStack> getAllItems() {
        List<ItemStack> result = new ArrayList<>();
        for (BundleItem item : items) {
            result.add(item.toItemStack());
        }
        return result;
    }

    /**
     * Represents a single item entry within a reward bundle.
     */
    public static class BundleItem {
        private final String key;
        private final Material material;
        private final int amount;
        private final double chance;
        private final String displayName;

        public BundleItem(String key, Material material, int amount, double chance, String displayName) {
            this.key = key;
            this.material = material;
            this.amount = amount;
            this.chance = Math.max(0, Math.min(1.0, chance));
            this.displayName = displayName;
        }

        public String getKey() { return key; }
        public Material getMaterial() { return material; }
        public int getAmount() { return amount; }
        public double getChance() { return chance; }
        public String getDisplayName() { return displayName; }

        /**
         * Convert this bundle item to an ItemStack.
         */
        public ItemStack toItemStack() {
            ItemStack stack = new ItemStack(material, amount);
            if (displayName != null && !displayName.isEmpty()) {
                var meta = stack.getItemMeta();
                if (meta != null) {
                    meta.displayName(com.wibudungeon.core.util.MessageUtil.colorize(displayName));
                    stack.setItemMeta(meta);
                }
            }
            return stack;
        }

        /**
         * Get chance as a percentage string (e.g., "30%").
         */
        public String getChancePercent() {
            return String.format("%.0f%%", chance * 100);
        }
    }
}

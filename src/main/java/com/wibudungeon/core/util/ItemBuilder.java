package com.wibudungeon.core.util;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Fluent builder for creating ItemStacks with custom properties.
 */
public class ItemBuilder {

    private final ItemStack item;
    private final ItemMeta meta;

    public ItemBuilder(Material material) {
        this.item = new ItemStack(material);
        this.meta = item.getItemMeta();
    }

    public ItemBuilder(Material material, int amount) {
        this.item = new ItemStack(material, amount);
        this.meta = item.getItemMeta();
    }

    public ItemBuilder(ItemStack item) {
        this.item = item.clone();
        this.meta = this.item.getItemMeta();
    }

    public ItemBuilder name(String name) {
        if (meta != null) {
            meta.displayName(MessageUtil.colorize(name));
        }
        return this;
    }

    public ItemBuilder name(Component name) {
        if (meta != null) {
            meta.displayName(name);
        }
        return this;
    }

    public ItemBuilder lore(String... lines) {
        if (meta != null) {
            List<Component> lore = Arrays.stream(lines)
                    .map(MessageUtil::colorize)
                    .collect(Collectors.toList());
            meta.lore(lore);
        }
        return this;
    }

    public ItemBuilder lore(List<String> lines) {
        if (meta != null) {
            List<Component> lore = lines.stream()
                    .map(MessageUtil::colorize)
                    .collect(Collectors.toList());
            meta.lore(lore);
        }
        return this;
    }

    public ItemBuilder addLore(String line) {
        if (meta != null) {
            List<Component> lore = meta.lore();
            if (lore == null) lore = new ArrayList<>();
            lore.add(MessageUtil.colorize(line));
            meta.lore(lore);
        }
        return this;
    }

    public ItemBuilder enchant(Enchantment enchantment, int level) {
        if (meta != null) {
            meta.addEnchant(enchantment, level, true);
        }
        return this;
    }

    public ItemBuilder flags(ItemFlag... flags) {
        if (meta != null) {
            meta.addItemFlags(flags);
        }
        return this;
    }

    public ItemBuilder hideFlags() {
        if (meta != null) {
            meta.addItemFlags(ItemFlag.values());
        }
        return this;
    }

    public ItemBuilder glow() {
        if (meta != null) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        return this;
    }

    public ItemBuilder amount(int amount) {
        item.setAmount(amount);
        return this;
    }

    public ItemStack build() {
        item.setItemMeta(meta);
        return item;
    }
}

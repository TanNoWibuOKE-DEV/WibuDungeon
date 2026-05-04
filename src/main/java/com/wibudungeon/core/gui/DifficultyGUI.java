package com.wibudungeon.core.gui;

import com.wibudungeon.core.dungeon.Difficulty;
import com.wibudungeon.core.dungeon.SetupSession;
import com.wibudungeon.core.util.ItemBuilder;
import com.wibudungeon.core.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

public class DifficultyGUI {

    public static final String TITLE = "§8§l» §c§lSet Difficulty §8§l«";

    public void open(Player player, SetupSession session) {
        Inventory gui = Bukkit.createInventory(null, 9, MessageUtil.colorize(TITLE));

        int waveNum = session.getSelectedWave();
        SetupSession.WaveData wave = session.getWave(waveNum);
        Difficulty currentDiff = wave != null && wave.getDifficulty() != null ? wave.getDifficulty() : Difficulty.NORMAL;

        gui.setItem(1, createDiffItem(Difficulty.NORMAL, Material.LIME_DYE, currentDiff));
        gui.setItem(3, createDiffItem(Difficulty.HARD, Material.YELLOW_DYE, currentDiff));
        gui.setItem(5, createDiffItem(Difficulty.SUPER_DIFFICULT, Material.ORANGE_DYE, currentDiff));
        gui.setItem(7, createDiffItem(Difficulty.NIGHTMARE, Material.RED_DYE, currentDiff));

        for (int i = 0; i < gui.getSize(); i++) {
            if (gui.getItem(i) == null) {
                gui.setItem(i, new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name("&7").build());
            }
        }

        player.openInventory(gui);
    }

    private org.bukkit.inventory.ItemStack createDiffItem(Difficulty diff, Material mat, Difficulty current) {
        ItemBuilder builder = new ItemBuilder(mat)
                .name(diff.getDisplayName())
                .lore("&7",
                      "&eMultiplier: &f" + diff.getMultiplier() + "x",
                      "&7",
                      current == diff ? "&a✔ Currently Selected" : "&eClick to select",
                      "&7");
        if (current == diff) {
            builder.glow();
        }
        return builder.build();
    }
}

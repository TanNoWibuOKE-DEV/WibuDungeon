package com.wibudungeon.core.gui;

import com.wibudungeon.core.dungeon.SetupSession;
import com.wibudungeon.core.mob.MythicMobsHook;
import com.wibudungeon.core.util.ItemBuilder;
import com.wibudungeon.core.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.Collection;

/**
 * GUI for selecting mob type during setup mode.
 * Shows vanilla mobs + MythicMobs if available.
 */
public class SetupMobGUI {

    public static final String MOB_TITLE = "§8§l» §c§lSelect Mob Type §8§l«";
    public static final String BOSS_TITLE = "§8§l» §4§lSelect Boss Type §8§l«";
    public static final String COUNT_TITLE = "§8§l» §e§lSelect Count §8§l«";

    private final MythicMobsHook mythicHook;

    public SetupMobGUI(MythicMobsHook mythicHook) {
        this.mythicHook = mythicHook;
    }

    /**
     * Open mob type selection GUI.
     */
    public void openMobSelect(Player player, boolean isBoss) {
        String title = isBoss ? BOSS_TITLE : MOB_TITLE;
        Inventory gui = Bukkit.createInventory(null, 54, MessageUtil.colorize(title));

        // Row 1-2: Vanilla mobs
        int slot = 0;
        addMob(gui, slot++, Material.ZOMBIE_HEAD, "ZOMBIE", "&2Zombie", "&7Standard undead mob");
        addMob(gui, slot++, Material.SKELETON_SKULL, "SKELETON", "&fSkeleton", "&7Ranged bow attacker");
        addMob(gui, slot++, Material.SPIDER_EYE, "SPIDER", "&8Spider", "&7Fast climbing mob");
        addMob(gui, slot++, Material.CREEPER_HEAD, "CREEPER", "&aCreeeper", "&7Explosive mob");
        addMob(gui, slot++, Material.GLASS_BOTTLE, "WITCH", "&5Witch", "&7Potion-throwing mob");
        addMob(gui, slot++, Material.BLAZE_ROD, "BLAZE", "&6Blaze", "&7Fire-shooting mob");
        addMob(gui, slot++, Material.ENDER_PEARL, "ENDERMAN", "&5Enderman", "&7Teleporting mob");
        addMob(gui, slot++, Material.BONE, "WITHER_SKELETON", "&8Wither Skeleton", "&7Strong melee mob");
        addMob(gui, slot++, Material.PHANTOM_MEMBRANE, "PHANTOM", "&bPhantom", "&7Flying mob");

        slot = 9;
        addMob(gui, slot++, Material.ROTTEN_FLESH, "HUSK", "&eHusk", "&7Desert zombie variant");
        addMob(gui, slot++, Material.ARROW, "STRAY", "&bStray", "&7Frozen skeleton variant");
        addMob(gui, slot++, Material.GUNPOWDER, "CAVE_SPIDER", "&2Cave Spider", "&7Poisonous spider");
        addMob(gui, slot++, Material.GOLDEN_SWORD, "PIGLIN_BRUTE", "&6Piglin Brute", "&7Strong nether mob");
        addMob(gui, slot++, Material.IRON_SWORD, "VINDICATOR", "&7Vindicator", "&7Axe-wielding mob");
        addMob(gui, slot++, Material.BOW, "PILLAGER", "&7Pillager", "&7Crossbow mob");
        addMob(gui, slot++, Material.TOTEM_OF_UNDYING, "EVOKER", "&7Evoker", "&7Spell-casting mob");
        addMob(gui, slot++, Material.MAGMA_CREAM, "MAGMA_CUBE", "&cMagma Cube", "&7Bouncing fire mob");
        addMob(gui, slot++, Material.SLIME_BALL, "SLIME", "&aSlime", "&7Bouncing mob");

        // Row 3-4: MythicMobs if available
        if (mythicHook.isEnabled()) {
            slot = 18;
            gui.setItem(slot, new ItemBuilder(Material.NETHER_STAR)
                    .name("&d&l═══ MythicMobs ═══")
                    .lore("&7Custom mobs from MythicMobs plugin")
                    .build());
            slot = 19;
            Collection<String> mmNames = mythicHook.getMobNames();
            for (String name : mmNames) {
                if (slot >= 36) break;
                gui.setItem(slot, new ItemBuilder(Material.WITHER_SKELETON_SKULL)
                        .name("&d" + name)
                        .lore("&7", "&7  MythicMob ID: &emm:" + name, "&7",
                                "&a  Click to select", "&7")
                        .glow().build());
                slot++;
            }
        }

        // Bottom row: Cancel
        gui.setItem(49, new ItemBuilder(Material.BARRIER)
                .name("&c&lCancel").lore("&7Go back").build());

        // Fill empty
        for (int i = 0; i < gui.getSize(); i++) {
            if (gui.getItem(i) == null) {
                gui.setItem(i, new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name("&7").build());
            }
        }

        player.openInventory(gui);
    }

    /**
     * Open count selection GUI after mob type is chosen.
     */
    public void openCountSelect(Player player, String mobId) {
        Inventory gui = Bukkit.createInventory(null, 27, MessageUtil.colorize(COUNT_TITLE));

        // Header
        gui.setItem(4, new ItemBuilder(Material.ZOMBIE_HEAD)
                .name("&e&lMob: &f" + mobId)
                .lore("&7", "&7  Select how many to spawn", "&7")
                .build());

        // Count buttons
        int[] counts = {1, 2, 3, 5, 8, 10, 15, 20};
        Material[] wools = {Material.WHITE_WOOL, Material.ORANGE_WOOL, Material.YELLOW_WOOL,
                Material.LIME_WOOL, Material.GREEN_WOOL, Material.CYAN_WOOL,
                Material.BLUE_WOOL, Material.PURPLE_WOOL};
        for (int i = 0; i < counts.length; i++) {
            gui.setItem(9 + i, new ItemBuilder(wools[i], Math.min(counts[i], 64))
                    .name("&a&l" + counts[i] + " mob" + (counts[i] > 1 ? "s" : ""))
                    .lore("&7Click to add &e" + counts[i] + "x &f" + mobId)
                    .build());
        }

        // Custom count
        gui.setItem(17, new ItemBuilder(Material.ANVIL)
                .name("&e&lCustom Count")
                .lore("&7", "&7  Click then type a", "&7  number in chat.", "&7")
                .build());

        // Cancel
        gui.setItem(22, new ItemBuilder(Material.BARRIER)
                .name("&c&lCancel").lore("&7Go back").build());

        // Fill
        for (int i = 0; i < gui.getSize(); i++) {
            if (gui.getItem(i) == null) {
                gui.setItem(i, new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name("&7").build());
            }
        }

        player.openInventory(gui);
    }

    private void addMob(Inventory gui, int slot, Material icon, String mobId, String name, String desc) {
        gui.setItem(slot, new ItemBuilder(icon)
                .name(name)
                .lore("&7", "&7  " + desc, "&7  ID: &e" + mobId, "&7", "&a  Click to select", "&7")
                .build());
    }
}

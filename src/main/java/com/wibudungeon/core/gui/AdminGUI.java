package com.wibudungeon.core.gui;

import com.wibudungeon.core.config.ConfigManager;
import com.wibudungeon.core.dungeon.Dungeon;
import com.wibudungeon.core.dungeon.DungeonManager;
import com.wibudungeon.core.util.ItemBuilder;
import com.wibudungeon.core.util.LocationUtil;
import com.wibudungeon.core.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.Map;

/**
 * Admin GUI for managing dungeons — v1.0.2 rework.
 */
public class AdminGUI {

    public static final String MAIN_TITLE = "§8§l» §c§lDungeon Admin §8§l«";
    public static final String EDIT_TITLE = "§8§l» §e§lEdit Dungeon §8§l«";

    private final ConfigManager configManager;
    private final DungeonManager dungeonManager;

    public AdminGUI(ConfigManager configManager, DungeonManager dungeonManager) {
        this.configManager = configManager;
        this.dungeonManager = dungeonManager;
    }

    /**
     * Open the main admin panel.
     */
    public void openMain(Player player) {
        Map<String, Dungeon> dungeons = configManager.getDungeons();
        int size = Math.max(36, ((dungeons.size() / 7) + 1) * 9 + 18);
        size = Math.min(54, size);

        Inventory gui = Bukkit.createInventory(null, size, MessageUtil.colorize(MAIN_TITLE));

        // Top border
        for (int i = 0; i < 9; i++) {
            gui.setItem(i, new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name("&7").build());
        }

        // Header
        gui.setItem(4, new ItemBuilder(Material.DRAGON_EGG)
                .name("&c&lDungeon Admin Panel")
                .lore(
                        "&7",
                        "&7  Manage all dungeons,",
                        "&7  waves, and rewards.",
                        "&7",
                        "&e  Total Dungeons: &f" + dungeons.size(),
                        "&e  Active Instances: &f" + dungeonManager.getTotalActiveInstances(),
                        "&7"
                )
                .build());

        // List dungeons
        int slot = 10;
        for (Map.Entry<String, Dungeon> entry : dungeons.entrySet()) {
            if (slot >= size - 9) break;
            // Skip border slots
            if (slot % 9 == 0 || slot % 9 == 8) { slot++; continue; }

            Dungeon dungeon = entry.getValue();
            // v1.0.7: Use different icons per type
            Material icon;
            if (dungeon.isStatic()) {
                icon = dungeon.isEnabled() ? Material.END_PORTAL_FRAME : Material.GRAY_CONCRETE;
            } else {
                icon = dungeon.isEnabled() ? Material.NETHER_STAR : Material.RED_CONCRETE;
            }
            String typeTag = dungeon.isStatic() ? "&d[STATIC]" : "&b[DYNAMIC]";

            gui.setItem(slot, new ItemBuilder(icon)
                    .name("&e" + dungeon.getId())
                    .lore(
                            "&7",
                            "&e  Name: &f" + dungeon.getName(),
                            "&e  Type: " + typeTag,
                            "&e  Status: " + (dungeon.isEnabled() ? "&a✔ Enabled" : "&c✘ Disabled"),
                            "&e  World: &f" + dungeon.getWorld(),
                            "&e  Players: &f" + dungeon.getMinPlayers() + "-" + dungeon.getMaxPlayers(),
                            "&e  Active: &f" + dungeonManager.getActiveInstanceCount(dungeon.getId()),
                            "&e  Valid: " + (dungeon.isValid() ? "&a✔" : "&c✘ Missing config"),
                            "&7",
                            "&a  ▸ Left-click to edit",
                            "&c  ▸ Right-click to toggle",
                            "&7"
                    )
                    .build());
            slot++;
        }

        // Bottom bar
        int bottomStart = size - 9;
        for (int i = bottomStart; i < size; i++) {
            gui.setItem(i, new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name("&7").build());
        }

        // Create new dungeon
        gui.setItem(bottomStart + 2, new ItemBuilder(Material.EMERALD_BLOCK)
                .name("&a&lCreate New Dungeon")
                .lore(
                        "&7",
                        "&7  Creates a new dungeon and enters",
                        "&7  interactive setup mode.",
                        "&7",
                        "&e  Usage: /wd create <id>",
                        "&e  Then:  /wd setup <id>",
                        "&7"
                )
                .glow()
                .build());

        // Reload
        gui.setItem(bottomStart + 5, new ItemBuilder(Material.COMPASS)
                .name("&e&lReload Config")
                .lore("&7", "&7  Reload all config files.", "&7")
                .build());

        // Close
        gui.setItem(bottomStart + 8, new ItemBuilder(Material.BARRIER)
                .name("&c&lClose")
                .lore("&7Click to close")
                .build());

        // Fill empty
        for (int i = 0; i < gui.getSize(); i++) {
            if (gui.getItem(i) == null) {
                gui.setItem(i, new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name("&7").build());
            }
        }

        player.openInventory(gui);
    }

    /**
     * Open the edit GUI for a specific dungeon — v1.0.2 rework.
     */
    public void openEdit(Player player, String dungeonId) {
        Dungeon dungeon = configManager.getDungeon(dungeonId);
        if (dungeon == null) return;

        Inventory gui = Bukkit.createInventory(null, 45,
                MessageUtil.colorize(EDIT_TITLE + " §7- §e" + dungeonId));

        // Top border
        for (int i = 0; i < 9; i++) {
            gui.setItem(i, new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name("&7").build());
        }

        // Dungeon info header
        String typeTag = dungeon.isStatic() ? "&d[STATIC]" : "&b[DYNAMIC]";
        java.util.List<String> headerLore = new java.util.ArrayList<>(java.util.Arrays.asList(
                "&7",
                "&e  Name: &f" + dungeon.getName(),
                "&e  Type: " + typeTag,
                "&e  World: &f" + dungeon.getWorld(),
                "&e  Pos1: &f" + LocationUtil.format(dungeon.getPos1()),
                "&e  Pos2: &f" + LocationUtil.format(dungeon.getPos2()),
                "&e  Spawn: &f" + LocationUtil.format(dungeon.getSpawnPoint())));
        if (dungeon.isStatic()) {
            headerLore.add("&e  Entry: &f" + LocationUtil.format(dungeon.getEntryPoint()));
        }
        headerLore.add("&e  Mob Spawns: &f" + dungeon.getMobSpawns().size());
        headerLore.add("&7");

        gui.setItem(4, new ItemBuilder(Material.BOOK)
                .name("&e&l" + dungeon.getId())
                .lore(headerLore.toArray(new String[0]))
                .build());

        // Row 2: Main settings
        // Toggle enabled
        gui.setItem(10, new ItemBuilder(dungeon.isEnabled() ? Material.LIME_DYE : Material.GRAY_DYE)
                .name(dungeon.isEnabled() ? "&a&lEnabled" : "&c&lDisabled")
                .lore("&7", "&7  Click to toggle on/off.", "&7")
                .build());

        // Wave set
        gui.setItem(12, new ItemBuilder(Material.IRON_SWORD)
                .name("&e&lWave Set: &f" + dungeon.getWaveSet())
                .lore("&7", "&7  Wave configuration for this dungeon.", "&7")
                .hideFlags()
                .build());

        // Reward set
        gui.setItem(14, new ItemBuilder(Material.CHEST)
                .name("&6&lReward Set: &f" + dungeon.getRewardSet())
                .lore("&7", "&7  Reward configuration for this dungeon.", "&7")
                .build());

        // Player limits
        gui.setItem(16, new ItemBuilder(Material.PLAYER_HEAD)
                .name("&a&lPlayer Limits")
                .lore(
                        "&7",
                        "&e  Min: &f" + dungeon.getMinPlayers(),
                        "&e  Max: &f" + dungeon.getMaxPlayers(),
                        "&e  Max Instances: &f" + dungeon.getMaxInstances(),
                        "&7"
                )
                .build());

        // Row 3: Setup Mode (NEW)
        gui.setItem(20, new ItemBuilder(Material.BLAZE_ROD)
                .name("&6&l⚡ Enter Setup Mode")
                .lore(
                        "&7",
                        "&7  Opens interactive setup mode",
                        "&7  for this dungeon.",
                        "&7",
                        "&e  Set regions, spawns, mobs,",
                        "&e  bosses, and waves visually!",
                        "&7",
                        "&a  ▸ Click to enter",
                        "&7"
                )
                .glow()
                .build());

        // Teleport to dungeon
        gui.setItem(22, new ItemBuilder(Material.ENDER_PEARL)
                .name("&b&lTeleport to Dungeon")
                .lore("&7", "&7  Click to teleport to spawn.", "&7")
                .build());

        // Spawn portal for this dungeon
        gui.setItem(24, new ItemBuilder(Material.END_PORTAL_FRAME)
                .name("&d&lSpawn Portal")
                .lore("&7", "&7  Spawns a portal for this", "&7  dungeon at your location.", "&7")
                .build());

        // Bottom bar
        for (int i = 36; i < 45; i++) {
            gui.setItem(i, new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name("&7").build());
        }

        // Delete dungeon
        gui.setItem(38, new ItemBuilder(Material.LAVA_BUCKET)
                .name("&c&lDelete Dungeon")
                .lore("&7", "&c  ⚠ This cannot be undone!", "&7")
                .build());

        // Back
        gui.setItem(40, new ItemBuilder(Material.ARROW)
                .name("&7&lBack")
                .lore("&7Return to admin panel")
                .build());

        // Close
        gui.setItem(44, new ItemBuilder(Material.BARRIER)
                .name("&c&lClose")
                .lore("&7Click to close")
                .build());

        // Fill
        for (int i = 0; i < gui.getSize(); i++) {
            if (gui.getItem(i) == null) {
                gui.setItem(i, new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name("&7").build());
            }
        }

        player.openInventory(gui);
    }
}

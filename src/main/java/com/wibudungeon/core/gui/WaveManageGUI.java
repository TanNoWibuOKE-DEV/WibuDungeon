package com.wibudungeon.core.gui;

import com.wibudungeon.core.dungeon.SetupSession;
import com.wibudungeon.core.util.ItemBuilder;
import com.wibudungeon.core.util.LocationUtil;
import com.wibudungeon.core.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * GUI for managing waves during setup mode.
 * Main GUI: 54 slots, waves in slots 10-16, 19-25, 28-34, 37-43 (28 per page).
 * Edit GUI: 27 slots, actions at slots 10, 12, 14, 16.
 * Mob Manager GUI: 36 slots, shows configured mobs (display only, SHIFT+LEFT to remove).
 * Spawn Manager GUI: 54 slots, shows all placed mob/boss spawn entries individually.
 *
 * v1.0.6: Mob Manager GUI is now READ-ONLY — no "Add Mob" or "Set Boss" buttons.
 *         Mobs can only be removed via SHIFT + LEFT CLICK.
 *         Wave detail shows selected bundle name.
 * v1.0.7: Added Spawn Manager GUI for individual mob/boss spawn assignment.
 */
public class WaveManageGUI {

    public static final String MAIN_TITLE = "§8§l» §b§lWave Manager §8§l«";
    public static final String DETAIL_TITLE = "§8§l» §e§lEdit Wave ";
    public static final String MOB_MANAGER_TITLE = "§8§l» §c§lMob Manager §8§l«";
    public static final String SPAWN_MANAGER_TITLE = "§8§l» §a§lSpawn Manager §8§l«";

    // Exact slot layout: rows 2-5, columns 2-8 (7 per row × 4 rows = 28 slots)
    private static final int[] WAVE_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };
    private static final int SLOTS_PER_PAGE = WAVE_SLOTS.length; // 28

    public void openMain(Player player, SetupSession session, int page) {
        Inventory gui = Bukkit.createInventory(null, 54, MessageUtil.colorize(MAIN_TITLE + " - Page " + page));

        // Collect wave entries into a list for pagination
        List<Map.Entry<Integer, SetupSession.WaveData>> waveList = new ArrayList<>(session.getWaves().entrySet());
        int startIndex = (page - 1) * SLOTS_PER_PAGE;

        // Place waves into the defined slot pattern
        for (int i = 0; i < SLOTS_PER_PAGE; i++) {
            int dataIndex = startIndex + i;
            if (dataIndex >= waveList.size()) break;

            Map.Entry<Integer, SetupSession.WaveData> entry = waveList.get(dataIndex);
            SetupSession.WaveData wave = entry.getValue();
            boolean hasBoss = wave.getBossId() != null;
            String diffName = wave.getDifficulty() != null ? wave.getDifficulty().getDisplayName() : "&aNormal";

            ItemBuilder builder = new ItemBuilder(Material.OAK_DOOR)
                    .name("&e&lWave #" + entry.getKey() + (hasBoss ? " &4[BOSS]" : ""));

            StringBuilder lore = new StringBuilder("&7\n");
            lore.append("&7  Difficulty: ").append(diffName).append("\n");
            lore.append("&7  Time: &f").append(wave.getTimeLimit()).append("s\n");
            if (wave.getBundleId() != null) {
                lore.append("&7  Bundle: &6").append(wave.getBundleId()).append("\n");
                lore.append("&7  Chests: &f").append(wave.getChestAmount()).append("\n");
            }
            lore.append("&7\n");
            for (var mob : wave.getMobs().entrySet()) {
                lore.append("&7  &f").append(mob.getValue()).append("x &e")
                        .append(mob.getKey()).append("\n");
            }
            if (hasBoss) {
                lore.append("&7\n&4  Boss: &c").append(wave.getBossId())
                        .append("\n&4  HP: &c").append((int) wave.getBossHealth());
            }
            lore.append("\n&7\n&a  Click to edit\n&7");

            builder.lore(lore.toString().split("\n"));
            if (hasBoss) builder.glow();
            gui.setItem(WAVE_SLOTS[i], builder.build());
        }

        // Bottom navigation row (row 6: slots 45-53)
        if (page > 1) {
            gui.setItem(45, new ItemBuilder(Material.ARROW).name("&e&l◀ Previous Page").build());
        }
        gui.setItem(49, new ItemBuilder(Material.LIME_DYE).name("&a&l➕ Add Wave").glow().build());
        if (waveList.size() > startIndex + SLOTS_PER_PAGE) {
            gui.setItem(53, new ItemBuilder(Material.ARROW).name("&e&lNext Page ▶").build());
        }

        // Fill all empty slots with glass panes
        for (int i = 0; i < gui.getSize(); i++) {
            if (gui.getItem(i) == null) {
                gui.setItem(i, new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name("&7").build());
            }
        }

        player.openInventory(gui);
    }

    /**
     * Wave Edit GUI (27 slots).
     * Slot 10 → Manage Mobs
     * Slot 12 → Reward Bundle Selection
     * Slot 14 → Dungeon Time
     * Slot 16 → Difficulty Selector
     * Slot 22 → Back
     */
    public void openDetail(Player player, SetupSession session, int waveNum) {
        SetupSession.WaveData wave = session.getOrCreateWave(waveNum);
        session.setSelectedWave(waveNum);

        String title = DETAIL_TITLE + waveNum + " §8§l«";
        Inventory gui = Bukkit.createInventory(null, 27, MessageUtil.colorize(title));

        // Slot 10 — Manage Spawns (v1.0.7: per-point spawn manager)
        int mobCount = wave.getMobSpawns().size();
        int bossCount = wave.getBossSpawns().size();
        long assignedMobs = wave.getMobSpawns().stream().filter(SetupSession.MobSpawnEntry::isAssigned).count();
        long assignedBosses = wave.getBossSpawns().stream().filter(SetupSession.BossSpawnEntry::isAssigned).count();

        gui.setItem(10, new ItemBuilder(Material.SPAWNER)
                .name("\u0026a\u0026l⬟ Manage Spawns")
                .lore("\u00267",
                       "\u0026e  Mob Spawns: \u0026f" + assignedMobs + "/" + mobCount + " assigned",
                       "\u0026e  Boss Spawns: \u0026f" + assignedBosses + "/" + bossCount + " assigned",
                       "\u00267",
                       "\u0026a  Click to manage all spawn points",
                       "\u00267")
                .glow().build());

        // Slot 12 — Reward Bundle Selection (v1.0.6: shows selected bundle)
        String bundleName = wave.getBundleId() != null ? wave.getBundleId() : "None";
        gui.setItem(12, new ItemBuilder(Material.CHEST)
                .name("&6&l🎁 Reward Bundle")
                .lore("&7", "&e  Current: &f" + bundleName,
                       "&e  Chests: &f" + wave.getChestAmount(),
                       "&7", "&a  Click to select bundle", "&7")
                .glow().build());

        // Slot 14 — Dungeon Time (interactive)
        int currentTime = wave.getTimeLimit();
        gui.setItem(14, new ItemBuilder(Material.CLOCK)
                .name("&b&l⏱ Dungeon Time")
                .lore("&7",
                       "&e  Current: &f" + currentTime + "s &7(" + (currentTime / 60) + "m " + (currentTime % 60) + "s)",
                       "&7",
                       "&a  LEFT click → &f+10s",
                       "&a  RIGHT click → &f+60s",
                       "&c  SHIFT+LEFT → &f-10s",
                       "&7")
                .glow().build());

        // Slot 16 — Difficulty Selector
        String diffDisplay = wave.getDifficulty() != null ? wave.getDifficulty().getDisplayName() : "&aNormal";
        gui.setItem(16, new ItemBuilder(Material.IRON_SWORD)
                .name("&6&l⚔ Set Difficulty")
                .lore("&7", "&e  Current: " + diffDisplay, "&7", "&a  Click to change", "&7")
                .hideFlags().glow().build());

        // Slot 22 — Back
        gui.setItem(22, new ItemBuilder(Material.ARROW)
                .name("&7&lBack to Waves")
                .build());

        // Fill empty slots
        for (int i = 0; i < gui.getSize(); i++) {
            if (gui.getItem(i) == null) {
                gui.setItem(i, new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name("&7").build());
            }
        }

        player.openInventory(gui);
    }

    /**
     * Mob Manager GUI (36 slots).
     * v1.0.6: Display-only — shows mobs already set via setup tools.
     * NO "Add Mob" or "Set Boss" buttons.
     * SHIFT + LEFT CLICK on a mob to remove it.
     */
    public void openMobManager(Player player, SetupSession session, int waveNum) {
        SetupSession.WaveData wave = session.getOrCreateWave(waveNum);
        session.setSelectedWave(waveNum);

        Inventory gui = Bukkit.createInventory(null, 36,
                MessageUtil.colorize(MOB_MANAGER_TITLE + " - Wave " + waveNum));

        // Header info
        gui.setItem(4, new ItemBuilder(Material.PAPER)
                .name("&e&lWave " + waveNum + " Mobs")
                .lore("&7", "&7  Manage mobs for this wave",
                       "&7  &cSHIFT + LEFT CLICK &7to remove a mob",
                       "&7")
                .build());

        // Display configured mobs in slots 10-16, 19-25
        int[] mobSlots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};
        int slotIdx = 0;
        for (var mob : wave.getMobs().entrySet()) {
            if (slotIdx >= mobSlots.length) break;
            String mobId = mob.getKey();
            int count = mob.getValue();

            String cleanName = mobId.startsWith("mm:") ? mobId.substring(3) : mobId;
            cleanName = cleanName.substring(0, 1).toUpperCase() + cleanName.substring(1).toLowerCase().replace("_", " ");
            boolean isMythic = mobId.startsWith("mm:");

            gui.setItem(mobSlots[slotIdx], new ItemBuilder(isMythic ? Material.WITHER_SKELETON_SKULL : Material.ZOMBIE_HEAD)
                    .name("&e" + cleanName + (isMythic ? " &d[MythicMob]" : ""))
                    .lore("&7",
                           "&e  ID: &f" + mobId,
                           "&e  Count: &f" + count,
                           "&7",
                           "&c  SHIFT + LEFT CLICK to REMOVE",
                           "&7")
                    .amount(Math.min(count, 64))
                    .build());
            slotIdx++;
        }

        // Add Mob Button
        gui.setItem(27, new ItemBuilder(Material.ZOMBIE_SPAWN_EGG)
                .name("&a&l➕ Add Mob")
                .lore("&7", "&a  Click to add a new mob", "&7")
                .glow().build());

        // Boss display (if set) — also SHIFT+LEFT to remove
        if (wave.getBossId() != null) {
            gui.setItem(31, new ItemBuilder(Material.DRAGON_HEAD)
                    .name("&4&l☠ Boss: &c" + wave.getBossId())
                    .lore("&7",
                           "&4  HP: &c" + (int) wave.getBossHealth(),
                           "&7",
                           "&c  SHIFT + LEFT CLICK to remove boss",
                           "&7")
                    .glow().build());
        }

        // Add Boss Button
        gui.setItem(35, new ItemBuilder(Material.BLAZE_SPAWN_EGG)
                .name("&c&l➕ Set Boss")
                .lore("&7", "&c  Click to set the wave boss", "&7")
                .glow().build());

        // Bottom bar — Back button
        gui.setItem(33, new ItemBuilder(Material.ARROW)
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
     * Spawn Manager GUI (54 slots).
     *
     * v1.0.7: Per-spawn assignment.
     * v1.0.8: Improved display — PAPER for unconfigured spawns, mob-specific icons for assigned.
     *         GUI auto-refreshes after every action (no reopen required).
     */
    public void openSpawnManager(Player player, SetupSession session, int waveNum) {
        SetupSession.WaveData wave = session.getOrCreateWave(waveNum);
        session.setSelectedWave(waveNum);

        Inventory gui = Bukkit.createInventory(null, 54,
                MessageUtil.colorize(SPAWN_MANAGER_TITLE + " - Wave " + waveNum));

        // Header summary
        long assignedMobs   = wave.getMobSpawns().stream().filter(SetupSession.MobSpawnEntry::isAssigned).count();
        long assignedBosses = wave.getBossSpawns().stream().filter(SetupSession.BossSpawnEntry::isAssigned).count();
        gui.setItem(4, new ItemBuilder(Material.SPAWNER)
                .name("&a&lSpawn Manager — Wave " + waveNum)
                .lore("&7",
                       "&e  Mob Spawns: &f" + assignedMobs + "/" + wave.getMobSpawns().size() + " assigned",
                       "&e  Boss Spawns: &f" + assignedBosses + "/" + wave.getBossSpawns().size() + " assigned",
                       "&7",
                       "&7  Click a spawn → assign",
                       "&c  SHIFT+LEFT &7→ remove",
                       "&7")
                .build());

        // ── Mob spawn entries (slots 10-16, 19-25) ────────────────────────────
        int[] mobSlots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};
        List<SetupSession.MobSpawnEntry> mobSpawns = wave.getMobSpawns();
        for (int i = 0; i < Math.min(mobSpawns.size(), mobSlots.length); i++) {
            SetupSession.MobSpawnEntry entry = mobSpawns.get(i);
            String locStr = entry.getLocation() != null
                    ? entry.getLocation().getBlockX() + ", "
                      + entry.getLocation().getBlockY() + ", "
                      + entry.getLocation().getBlockZ()
                    : "Unknown";

            if (entry.isAssigned()) {
                // Configured → mob-specific icon, display as "MobId x5"
                Material icon = getMobIcon(entry.getMobId());
                String displayName = formatMobName(entry.getMobId()) + " x" + entry.getCount();
                gui.setItem(mobSlots[i], new ItemBuilder(icon)
                        .name("&a" + displayName)
                        .lore("&7",
                               "&e  Location: &f" + locStr,
                               "&e  Mob ID: &f" + entry.getMobId(),
                               "&e  Amount: &f" + entry.getCount(),
                               "&7",
                               "&a  Click to re-assign",
                               "&c  SHIFT+LEFT to remove",
                               "&7")
                        .amount(Math.min(entry.getCount(), 64))
                        .glow().build());
            } else {
                // Unconfigured → PAPER
                gui.setItem(mobSlots[i], new ItemBuilder(Material.PAPER)
                        .name("&7Unconfigured Spawn #" + (i + 1))
                        .lore("&7",
                               "&e  Location: &f" + locStr,
                               "&c  No mob assigned yet",
                               "&7",
                               "&a  Click to assign mob",
                               "&c  SHIFT+LEFT to remove",
                               "&7")
                        .build());
            }
        }

        // ── Separator row ──────────────────────────────────────────────────────
        for (int i = 27; i <= 35; i++) {
            gui.setItem(i, new ItemBuilder(Material.ORANGE_STAINED_GLASS_PANE).name("&7").build());
        }
        gui.setItem(31, new ItemBuilder(Material.GOLD_BLOCK)
                .name("&4&l══ Boss Spawns ══")
                .lore("&7Boss spawn points below")
                .build());

        // ── Boss spawn entries (slots 37-43) ──────────────────────────────────
        int[] bossSlots = {37, 38, 39, 40, 41, 42, 43};
        List<SetupSession.BossSpawnEntry> bossSpawns = wave.getBossSpawns();
        for (int i = 0; i < Math.min(bossSpawns.size(), bossSlots.length); i++) {
            SetupSession.BossSpawnEntry entry = bossSpawns.get(i);
            String locStr = entry.getLocation() != null
                    ? entry.getLocation().getBlockX() + ", "
                      + entry.getLocation().getBlockY() + ", "
                      + entry.getLocation().getBlockZ()
                    : "Unknown";

            if (entry.isAssigned()) {
                // Configured → boss-specific icon
                Material icon = getBossIcon(entry.getBossId());
                gui.setItem(bossSlots[i], new ItemBuilder(icon)
                        .name("&4&l☠ " + entry.getBossId())
                        .lore("&7",
                               "&e  Location: &f" + locStr,
                               "&e  Boss ID: &c" + entry.getBossId(),
                               "&7",
                               "&a  Click to re-assign boss",
                               "&c  SHIFT+LEFT to remove",
                               "&7")
                        .glow().build());
            } else {
                // Unconfigured → PAPER
                gui.setItem(bossSlots[i], new ItemBuilder(Material.PAPER)
                        .name("&7Unconfigured Boss Spawn #" + (i + 1))
                        .lore("&7",
                               "&e  Location: &f" + locStr,
                               "&c  No boss assigned yet",
                               "&7",
                               "&a  Click to assign boss",
                               "&c  SHIFT+LEFT to remove",
                               "&7")
                        .build());
            }
        }

        // Bottom bar
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

    // =========================================================================
    // ICON HELPERS (v1.0.8)
    // =========================================================================

    /**
     * Returns the best representative icon for a mob ID.
     * PAPER is returned for null/unknown to indicate unconfigured state.
     */
    private Material getMobIcon(String mobId) {
        if (mobId == null) return Material.PAPER;
        String upper = mobId.toUpperCase().replace("MM:", "").replace(":", "_");
        return switch (upper) {
            case "ZOMBIE", "ZOMBIE_VILLAGER", "DROWNED"          -> Material.ZOMBIE_HEAD;
            case "HUSK"                                           -> Material.ZOMBIE_HEAD;
            case "SKELETON", "STRAY", "BOGGED"                   -> Material.SKELETON_SKULL;
            case "WITHER_SKELETON"                                -> Material.WITHER_SKELETON_SKULL;
            case "CREEPER"                                        -> Material.CREEPER_HEAD;
            case "SPIDER", "CAVE_SPIDER"                          -> Material.SPIDER_EYE;
            case "BLAZE"                                          -> Material.BLAZE_ROD;
            case "WITCH"                                          -> Material.GLASS_BOTTLE;
            case "ENDERMAN", "ENDERMITE"                          -> Material.ENDER_PEARL;
            case "PHANTOM"                                        -> Material.PHANTOM_MEMBRANE;
            case "PIGLIN", "PIGLIN_BRUTE", "ZOMBIFIED_PIGLIN"    -> Material.GOLD_NUGGET;
            case "VINDICATOR", "EVOKER", "PILLAGER"              -> Material.TOTEM_OF_UNDYING;
            case "RAVAGER"                                        -> Material.IRON_SWORD;
            case "SLIME"                                          -> Material.SLIME_BALL;
            case "MAGMA_CUBE"                                     -> Material.MAGMA_CREAM;
            case "GHAST"                                          -> Material.GHAST_TEAR;
            case "ELDER_GUARDIAN", "GUARDIAN"                     -> Material.PRISMARINE_SHARD;
            default -> mobId.toLowerCase().startsWith("mm:") || mobId.toLowerCase().startsWith("mm_")
                    ? Material.WITHER_SKELETON_SKULL  // MythicMobs custom mob
                    : Material.ZOMBIE_HEAD;           // Generic vanilla fallback
        };
    }

    /**
     * Returns the best icon for a boss ID.
     * Uses keyword matching for common boss themes; falls back to DRAGON_HEAD.
     */
    private Material getBossIcon(String bossId) {
        if (bossId == null) return Material.PAPER;
        String lower = bossId.toLowerCase();
        if      (lower.contains("dragon"))  return Material.DRAGON_HEAD;
        else if (lower.contains("wither"))  return Material.WITHER_SKELETON_SKULL;
        else if (lower.contains("fire") || lower.contains("blaze") || lower.contains("inferno"))
                                            return Material.BLAZE_ROD;
        else if (lower.contains("ice") || lower.contains("frost") || lower.contains("snow"))
                                            return Material.PACKED_ICE;
        else if (lower.contains("undead") || lower.contains("zombie") || lower.contains("skeleton"))
                                            return Material.SKELETON_SKULL;
        else if (lower.contains("spider") || lower.contains("arachn"))
                                            return Material.SPIDER_EYE;
        else if (lower.contains("golem"))   return Material.IRON_BLOCK;
        else                                return Material.DRAGON_HEAD;
    }

    /**
     * Formats a mob ID into a human-readable display name.
     * e.g. "mm:Fire_Demon" → "Fire Demon"  |  "ZOMBIE" → "Zombie"
     */
    private String formatMobName(String mobId) {
        if (mobId == null) return "Unknown";
        String clean = mobId;
        if (clean.startsWith("mm:")) clean = clean.substring(3);
        return clean.replace("_", " ").trim();
    }
}

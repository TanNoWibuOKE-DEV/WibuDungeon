package com.wibudungeon.core.dungeon;

import com.wibudungeon.core.config.ConfigManager;
import com.wibudungeon.core.gui.BundleSelectGUI;
import com.wibudungeon.core.gui.DifficultyGUI;
import com.wibudungeon.core.gui.RewardGUI;
import com.wibudungeon.core.gui.SetupMobGUI;
import com.wibudungeon.core.gui.WaveManageGUI;
import com.wibudungeon.core.util.MessageUtil;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.Plugin;

import java.util.Map;

/**
 * Handles all interactions during setup mode:
 * hotbar tool clicks, GUI clicks, and chat input.
 *
 * v1.0.6: Region validation, mob manager read-only, bundle selection.
 */
public class SetupListener implements Listener {

    private final Plugin plugin;
    private final SetupManager setupManager;
    private final ConfigManager configManager;
    private final SetupMobGUI setupMobGUI;
    private final WaveManageGUI waveManageGUI;
    private final DifficultyGUI difficultyGUI = new DifficultyGUI();
    private final RewardGUI rewardGUI;
    private final BundleSelectGUI bundleSelectGUI;

    public SetupListener(Plugin plugin, SetupManager setupManager, ConfigManager configManager,
                         SetupMobGUI setupMobGUI, WaveManageGUI waveManageGUI) {
        this.plugin = plugin;
        this.setupManager = setupManager;
        this.configManager = configManager;
        this.setupMobGUI = setupMobGUI;
        this.waveManageGUI = waveManageGUI;
        this.rewardGUI = new RewardGUI(configManager);
        this.bundleSelectGUI = new BundleSelectGUI(configManager);
    }

    // ===== HOTBAR TOOL INTERACTIONS =====

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!setupManager.isInSetup(player.getUniqueId())) return;

        event.setCancelled(true);
        SetupSession session = setupManager.getSession(player.getUniqueId());
        if (session == null) return;

        ItemStack item = event.getItem();
        if (item == null || !item.hasItemMeta()) return;

        String name = PlainTextComponentSerializer.plainText().serialize(item.displayName());
        boolean isRight = event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK;
        boolean isLeft = event.getAction() == Action.LEFT_CLICK_AIR || event.getAction() == Action.LEFT_CLICK_BLOCK;
        Location loc = player.getLocation();

        if (name.contains("Region Wand")) {
            if (isLeft) {
                session.setPos1(loc);
                msg(player, "&aPos1 set at &e" + formatLoc(loc));
            } else if (isRight) {
                session.setPos2(loc);
                msg(player, "&aPos2 set at &e" + formatLoc(loc));
            }
        } else if (name.contains("Start Point Tool") && isRight) {
            if (!session.isInsideRegion(loc)) {
                msg(player, "&c⚠ This location is outside the dungeon region! Set pos1/pos2 first or move inside.");
                return;
            }
            session.setSpawnPoint(loc);
            msg(player, "&aDungeon spawn point set at &e" + formatLoc(loc));
        } else if (name.contains("Mob Spawn Tool") && isRight) {
            if (!session.isInsideRegion(loc)) {
                msg(player, "&c⚠ This location is outside the dungeon region! Move inside the region.");
                return;
            }
            SetupSession.WaveData wave = session.getOrCreateWave(session.getSelectedWave());
            wave.addMobSpawn(loc, "NODE", 1);
            msg(player, "&aAdded mob spawn node to Wave " + session.getSelectedWave() + " at &e" + formatLoc(loc) + " &7(Total: " + wave.getMobSpawns().size() + ")");
        } else if (name.contains("Boss Spawn Tool") && isRight) {
            if (!session.isInsideRegion(loc)) {
                msg(player, "&c⚠ This location is outside the dungeon region! Move inside the region.");
                return;
            }
            SetupSession.WaveData wave = session.getOrCreateWave(session.getSelectedWave());
            wave.setBossSpawnPoint(loc);
            msg(player, "&aBoss spawn node set for Wave " + session.getSelectedWave() + " at &e" + formatLoc(loc));
        } else if (name.contains("Save Wave Tool") && isRight) {
            int current = session.getSelectedWave();
            SetupSession.WaveData wave = session.getOrCreateWave(current);
            if (wave.getMobSpawns().isEmpty()) {
                msg(player, "&c⚠ You must set at least 1 Mob Spawn before saving the wave!");
                return;
            }
            session.setSelectedWave(current + 1);
            msg(player, "&aWave " + current + " saved! Now configuring Wave " + (current + 1));
        } else if (name.contains("Manage Wave Tool") && isRight) {
            waveManageGUI.openMain(player, session, session.getSelectedWave());
        } else if (name.contains("Save Dungeon Tool") && isRight) {
            setupManager.saveSetup(player);
        } else if (name.contains("Exit Setup Tool") && isRight) {
            setupManager.cancelSetup(player);
        }
    }

    // ===== INVENTORY PROTECTION =====

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (setupManager.isInSetup(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onSwapHand(PlayerSwapHandItemsEvent event) {
        if (setupManager.isInSetup(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onCommand(org.bukkit.event.player.PlayerCommandPreprocessEvent event) {
        if (setupManager.isInSetup(event.getPlayer().getUniqueId())) {
            if (event.getMessage().toLowerCase().startsWith("/clear")) {
                event.setCancelled(true);
                MessageUtil.send(event.getPlayer(), "&cYou cannot clear your inventory in Setup Mode.");
            }
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (setupManager.isInSetup(event.getWhoClicked().getUniqueId())) {
            // Cancel clicks in the player's own inventory to prevent moving tools
            if (event.getClickedInventory() != null && event.getClickedInventory().equals(event.getWhoClicked().getInventory())) {
                event.setCancelled(true);
            }
        }
    }

    // ===== GUI CLICK HANDLING =====

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!setupManager.isInSetup(player.getUniqueId())) return;

        String title = event.getView().title() != null
                ? PlainTextComponentSerializer.plainText().serialize(event.getView().title())
                : "";

        if (title.contains("Select Mob Type") || title.contains("Select Boss Type")) {
            handleMobSelectClick(event, player, title.contains("Boss"));
        } else if (title.contains("Select Count")) {
            handleCountClick(event, player);
        } else if (title.contains("Wave Manager")) {
            handleWaveManagerClick(event, player, title);
        } else if (title.contains("Mob Manager")) {
            handleMobManagerClick(event, player);
        } else if (title.contains("Edit Wave")) {
            handleWaveDetailClick(event, player);
        } else if (title.contains("Set Difficulty")) {
            handleDifficultyClick(event, player);
        } else if (title.contains("Wave Rewards")) {
            handleRewardGUIClick(event, player);
        } else if (title.contains("Select Reward Bundle")) {
            handleBundleSelectClick(event, player);
        }
    }

    private void handleMobSelectClick(InventoryClickEvent event, Player player, boolean isBoss) {
        event.setCancelled(true);
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.BLACK_STAINED_GLASS_PANE) return;
        if (clicked.getType() == Material.BARRIER) { player.closeInventory(); return; }
        if (clicked.getType() == Material.NETHER_STAR) return;

        String mobId = extractMobId(clicked);
        if (mobId == null) return;

        SetupSession session = setupManager.getSession(player.getUniqueId());
        if (session == null) return;

        if (isBoss) {
            int wave = session.getSelectedWave();
            SetupSession.WaveData wd = session.getOrCreateWave(wave);
            wd.setBossId(mobId);
            wd.setBossName("&4&l" + mobId);
            player.closeInventory();
            msg(player, "&4Boss set to &c" + mobId + " &4for wave &c" + wave);
        } else {
            session.setPendingMobId(mobId);
            setupMobGUI.openCountSelect(player, mobId);
        }
    }

    private void handleCountClick(InventoryClickEvent event, Player player) {
        event.setCancelled(true);
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.GRAY_STAINED_GLASS_PANE) return;
        if (clicked.getType() == Material.BARRIER) { player.closeInventory(); return; }

        SetupSession session = setupManager.getSession(player.getUniqueId());
        if (session == null) return;

        if (clicked.getType() == Material.ANVIL) {
            player.closeInventory();
            player.setMetadata("setup_custom_count", new FixedMetadataValue(plugin, true));
            msg(player, "&eType the mob count in chat:");
            return;
        }

        String name = PlainTextComponentSerializer.plainText().serialize(clicked.displayName());
        try {
            String numStr = name.replaceAll("[^0-9]", "");
            int count = Integer.parseInt(numStr);
            finalizeMobPlacement(player, session, count);
        } catch (NumberFormatException ignored) {}
    }

    private static final int[] WAVE_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    private void handleWaveManagerClick(InventoryClickEvent event, Player player, String title) {
        event.setCancelled(true);
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.BLACK_STAINED_GLASS_PANE) return;

        SetupSession session = setupManager.getSession(player.getUniqueId());
        if (session == null) return;
        int slot = event.getRawSlot();

        int currentPage = 1;
        try {
            if (title.contains("Page ")) {
                currentPage = Integer.parseInt(title.substring(title.lastIndexOf("Page ") + 5).trim());
            }
        } catch (Exception ignored) {}

        if (slot == 45 && currentPage > 1) {
            waveManageGUI.openMain(player, session, currentPage - 1);
            return;
        } else if (slot == 49) {
            int next = session.getNextWaveNumber();
            session.getOrCreateWave(next);
            waveManageGUI.openDetail(player, session, next);
            msg(player, "&aWave " + next + " created!");
            return;
        } else if (slot == 53) {
            waveManageGUI.openMain(player, session, currentPage + 1);
            return;
        }

        int slotIndex = -1;
        for (int i = 0; i < WAVE_SLOTS.length; i++) {
            if (WAVE_SLOTS[i] == slot) { slotIndex = i; break; }
        }
        if (slotIndex < 0) return;

        int dataIndex = (currentPage - 1) * WAVE_SLOTS.length + slotIndex;
        java.util.List<Map.Entry<Integer, SetupSession.WaveData>> waveList = new java.util.ArrayList<>(session.getWaves().entrySet());
        if (dataIndex >= waveList.size()) return;

        int waveNum = waveList.get(dataIndex).getKey();
        waveManageGUI.openDetail(player, session, waveNum);
    }

    private void handleWaveDetailClick(InventoryClickEvent event, Player player) {
        event.setCancelled(true);
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.GRAY_STAINED_GLASS_PANE) return;

        SetupSession session = setupManager.getSession(player.getUniqueId());
        if (session == null) return;
        int waveNum = session.getSelectedWave();
        SetupSession.WaveData wave = session.getOrCreateWave(waveNum);
        int slot = event.getRawSlot();

        switch (slot) {
            case 10 -> {
                waveManageGUI.openMobManager(player, session, waveNum);
            }
            case 12 -> {
                // v1.0.6: Open Bundle Select GUI instead of reward view
                bundleSelectGUI.open(player, waveNum, wave.getBundleId());
            }
            case 14 -> {
                int currentTime = wave.getTimeLimit();
                if (event.isShiftClick() && event.isLeftClick()) {
                    wave.setTimeLimit(currentTime - 10);
                    configManager.setTimeLimitForWave(session.getDungeonId(), waveNum, wave.getTimeLimit());
                    msg(player, "&c⏱ Time: &f" + wave.getTimeLimit() + "s &7(-10s)");
                } else if (event.isRightClick()) {
                    wave.setTimeLimit(currentTime + 60);
                    configManager.setTimeLimitForWave(session.getDungeonId(), waveNum, wave.getTimeLimit());
                    msg(player, "&a⏱ Time: &f" + wave.getTimeLimit() + "s &7(+60s)");
                } else if (event.isLeftClick()) {
                    wave.setTimeLimit(currentTime + 10);
                    configManager.setTimeLimitForWave(session.getDungeonId(), waveNum, wave.getTimeLimit());
                    msg(player, "&a⏱ Time: &f" + wave.getTimeLimit() + "s &7(+10s)");
                }
                waveManageGUI.openDetail(player, session, waveNum);
            }
            case 16 -> {
                difficultyGUI.open(player, session);
            }
            case 22 -> {
                waveManageGUI.openMain(player, session, 1);
            }
        }
    }

    /**
     * Handle clicks in the Mob Manager GUI.
     * v1.0.6: Read-only — only SHIFT + LEFT CLICK to remove mobs. No add/boss buttons.
     */
    private void handleMobManagerClick(InventoryClickEvent event, Player player) {
        event.setCancelled(true);
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.BLACK_STAINED_GLASS_PANE) return;

        SetupSession session = setupManager.getSession(player.getUniqueId());
        if (session == null) return;
        int waveNum = session.getSelectedWave();
        SetupSession.WaveData wave = session.getOrCreateWave(waveNum);
        int slot = event.getRawSlot();

        // Back button
        if (slot == 33) {
            waveManageGUI.openDetail(player, session, waveNum);
            return;
        }

        // Add Mob (slot 27)
        if (slot == 27) {
            player.setMetadata("setup_is_boss", new FixedMetadataValue(plugin, false));
            setupMobGUI.openMobSelect(player, false);
            return;
        }

        // Set Boss (slot 35)
        if (slot == 35) {
            player.setMetadata("setup_is_boss", new FixedMetadataValue(plugin, true));
            setupMobGUI.openMobSelect(player, true);
            return;
        }

        // Boss removal (slot 31) — only on SHIFT + LEFT CLICK
        if (slot == 31 && wave.getBossId() != null && event.isShiftClick() && event.isLeftClick()) {
            wave.setBossId(null);
            wave.setBossName(null);
            waveManageGUI.openMobManager(player, session, waveNum);
            msg(player, "&cBoss removed from wave " + waveNum);
            return;
        }

        // Mob slots (10-16, 19-25) — SHIFT + LEFT CLICK to remove
        if (!event.isShiftClick() || !event.isLeftClick()) return;

        int[] mobSlots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};
        int mobIdx = -1;
        for (int i = 0; i < mobSlots.length; i++) {
            if (mobSlots[i] == slot) { mobIdx = i; break; }
        }
        if (mobIdx < 0) return;

        int idx = 0;
        for (var entry : wave.getMobs().entrySet()) {
            if (idx == mobIdx) {
                String mobId = entry.getKey();
                wave.removeMob(mobId);
                configManager.removeMobFromWave(session.getDungeonId(), waveNum, mobId);
                waveManageGUI.openMobManager(player, session, waveNum);
                msg(player, "&cRemoved &e" + mobId + " &cfrom wave " + waveNum);
                return;
            }
            idx++;
        }
    }

    /**
     * Handle clicks in the Bundle Select GUI.
     */
    private void handleBundleSelectClick(InventoryClickEvent event, Player player) {
        event.setCancelled(true);
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.BLACK_STAINED_GLASS_PANE) return;

        SetupSession session = setupManager.getSession(player.getUniqueId());
        if (session == null) return;
        int waveNum = session.getSelectedWave();
        SetupSession.WaveData wave = session.getOrCreateWave(waveNum);
        int slot = event.getRawSlot();

        // Back button
        if (slot == 49) {
            waveManageGUI.openDetail(player, session, waveNum);
            return;
        }

        // "None" button
        if (slot == 48) {
            wave.setBundleId(null);
            configManager.setWaveBundleId(session.getDungeonId(), waveNum, null);
            msg(player, "&7Bundle removed from wave " + waveNum);
            waveManageGUI.openDetail(player, session, waveNum);
            return;
        }

        // Bundle slots
        int[] bundleSlots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
        int bundleIdx = -1;
        for (int i = 0; i < bundleSlots.length; i++) {
            if (bundleSlots[i] == slot) { bundleIdx = i; break; }
        }
        if (bundleIdx < 0) return;

        // Get bundle ID from the ordered map
        var bundleIds = new java.util.ArrayList<>(configManager.getRewardBundles().keySet());
        if (bundleIdx >= bundleIds.size()) return;

        String bundleId = bundleIds.get(bundleIdx);
        wave.setBundleId(bundleId);
        configManager.setWaveBundleId(session.getDungeonId(), waveNum, bundleId);
        msg(player, "&aSet bundle &e" + bundleId + " &afor wave " + waveNum);
        waveManageGUI.openDetail(player, session, waveNum);
    }

    /**
     * Handle clicks in the Reward GUI (read-only, only Back button).
     */
    private void handleRewardGUIClick(InventoryClickEvent event, Player player) {
        event.setCancelled(true);
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null) return;

        SetupSession session = setupManager.getSession(player.getUniqueId());
        if (session == null) return;

        String name = PlainTextComponentSerializer.plainText().serialize(clicked.displayName());
        if (name.contains("Back")) {
            waveManageGUI.openDetail(player, session, session.getSelectedWave());
        }
    }

    private void handleDifficultyClick(InventoryClickEvent event, Player player) {
        event.setCancelled(true);
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.BLACK_STAINED_GLASS_PANE) return;

        SetupSession session = setupManager.getSession(player.getUniqueId());
        if (session == null) return;
        int waveNum = session.getSelectedWave();
        SetupSession.WaveData wave = session.getOrCreateWave(waveNum);

        String name = PlainTextComponentSerializer.plainText().serialize(clicked.displayName());
        com.wibudungeon.core.dungeon.Difficulty diff = null;

        if (name.contains("Normal")) diff = com.wibudungeon.core.dungeon.Difficulty.NORMAL;
        else if (name.contains("Hard")) diff = com.wibudungeon.core.dungeon.Difficulty.HARD;
        else if (name.contains("Super Difficult")) diff = com.wibudungeon.core.dungeon.Difficulty.SUPER_DIFFICULT;
        else if (name.contains("Nightmare")) diff = com.wibudungeon.core.dungeon.Difficulty.NIGHTMARE;

        if (diff != null) {
            wave.setDifficulty(diff);
            waveManageGUI.openDetail(player, session, waveNum);
            msg(player, "&aSet difficulty of wave " + waveNum + " to " + name);
        }
    }

    // ===== CHAT INPUT FOR CUSTOM COUNT =====

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (!player.hasMetadata("setup_custom_count")) return;
        if (!setupManager.isInSetup(player.getUniqueId())) return;

        event.setCancelled(true);
        player.removeMetadata("setup_custom_count", plugin);

        SetupSession session = setupManager.getSession(player.getUniqueId());
        if (session == null) return;

        try {
            int count = Integer.parseInt(event.getMessage().trim());
            if (count < 1 || count > 100) {
                msg(player, "&cCount must be 1-100!");
                return;
            }
            org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> finalizeMobPlacement(player, session, count));
        } catch (NumberFormatException e) {
            msg(player, "&cInvalid number! Try again.");
        }
    }

    // ===== HELPERS =====

    private void finalizeMobPlacement(Player player, SetupSession session, int count) {
        String mobId = session.getPendingMobId();
        if (mobId == null) return;

        boolean isBoss = player.hasMetadata("setup_is_boss") &&
                player.getMetadata("setup_is_boss").getFirst().asBoolean();

        if (isBoss) {
            int wave = session.getSelectedWave();
            SetupSession.WaveData wd = session.getOrCreateWave(wave);
            wd.setBossId(mobId);
            wd.setBossHealth(200);
            player.closeInventory();
            msg(player, "&4Boss set: &c" + mobId + " &4for wave " + wave);
            waveManageGUI.openMobManager(player, session, wave);
        } else {
            int wave = session.getSelectedWave();
            SetupSession.WaveData wd = session.getOrCreateWave(wave);
            wd.addMob(mobId, count);

            player.closeInventory();
            msg(player, "&aAdded &e" + count + "x " + mobId + " &ato wave " + wave);
            waveManageGUI.openMobManager(player, session, wave);
        }

        session.setPendingMobId(null);
        player.removeMetadata("setup_is_boss", plugin);
    }

    private void addPresetWave(SetupSession session, int zombies, int skeletons, int spiders, int witches) {
        int num = session.getNextWaveNumber();
        SetupSession.WaveData wd = session.getOrCreateWave(num);
        if (zombies > 0) wd.addMob("ZOMBIE", zombies);
        if (skeletons > 0) wd.addMob("SKELETON", skeletons);
        if (spiders > 0) wd.addMob("SPIDER", spiders);
        if (witches > 0) wd.addMob("WITCH", witches);
    }

    private String extractMobId(ItemStack item) {
        if (item == null || !item.hasItemMeta() || !item.getItemMeta().hasLore()) return null;
        for (var loreLine : item.lore()) {
            String plain = PlainTextComponentSerializer.plainText().serialize(loreLine);
            if (plain.contains("ID: ")) {
                return plain.substring(plain.indexOf("ID: ") + 4).trim();
            }
            if (plain.contains("MythicMob ID: ")) {
                return plain.substring(plain.indexOf("MythicMob ID: ") + 14).trim();
            }
        }
        String name = PlainTextComponentSerializer.plainText().serialize(item.displayName());
        return name.replaceAll("[\\[\\]]", "").trim();
        }


    // Clean up on disconnect
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (setupManager.isInSetup(event.getPlayer().getUniqueId())) {
            setupManager.cancelSetup(event.getPlayer());
        }
    }

    private void msg(Player p, String m) {
        MessageUtil.send(p, configManager.getPrefix() + m);
    }

    private String formatLoc(Location l) {
        return l.getBlockX() + ", " + l.getBlockY() + ", " + l.getBlockZ();
    }

    // ===== PREVENT CHAOTIC ACTIONS IN SETUP MODE =====

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (setupManager.isInSetup(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockBreak(BlockBreakEvent event) {
        if (setupManager.isInSetup(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (event.getEntity().getShooter() instanceof Player player) {
            if (setupManager.isInSetup(player.getUniqueId())) {
                event.setCancelled(true);
            }
        }
    }


}

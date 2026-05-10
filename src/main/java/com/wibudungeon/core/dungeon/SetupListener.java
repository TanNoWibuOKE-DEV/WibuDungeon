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
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Map;

/**
 * Handles all interactions during setup mode.
 *
 * v1.0.6: Region validation, read-only mob manager, bundle selection.
 * v1.0.7: Per-spawn spawn manager, boss spawn entries.
 * v1.0.8:
 *   - Right-clicking a mob/boss BlockDisplay marker removes that spawn entry instantly.
 *   - "Save Wave Tool" validates all spawns are assigned before advancing to the next wave.
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

    // =========================================================================
    // PART 2.1 — CLICK EXISTING MARKER TO REMOVE IT
    // =========================================================================

    /**
     * v1.0.8: Right-clicking an Interaction entity tagged with WibuDungeon PDC keys
     * removes the corresponding mob or boss spawn entry from the session immediately.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onMarkerClick(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        if (!setupManager.isInSetup(player.getUniqueId())) return;
        if (!(event.getRightClicked() instanceof org.bukkit.entity.Interaction interaction)) return;

        PersistentDataContainer pdc = interaction.getPersistentDataContainer();

        // Only handle markers that belong to WibuDungeon
        if (!pdc.has(MarkerManager.KEY_MARKER_TYPE, PersistentDataType.STRING)) return;

        event.setCancelled(true);

        String type    = pdc.get(MarkerManager.KEY_MARKER_TYPE,  PersistentDataType.STRING);
        String owner   = pdc.get(MarkerManager.KEY_MARKER_OWNER, PersistentDataType.STRING);
        Integer wave   = pdc.get(MarkerManager.KEY_MARKER_WAVE,  PersistentDataType.INTEGER);
        Integer index  = pdc.get(MarkerManager.KEY_MARKER_INDEX, PersistentDataType.INTEGER);

        // Safety: only the owning player can remove their own markers
        if (owner == null || !owner.equals(player.getUniqueId().toString())) return;
        if (wave == null || index == null) return;

        SetupSession session = setupManager.getSession(player.getUniqueId());
        if (session == null) return;

        SetupSession.WaveData waveData = session.getWave(wave);
        if (waveData == null) return;

        if ("mob".equals(type)) {
            List<SetupSession.MobSpawnEntry> spawns = waveData.getMobSpawns();
            if (index >= 0 && index < spawns.size()) {
                waveData.removeMobSpawn(index);
                setupManager.forceMarkerRefresh(player);
                msg(player, "&cRemoved &eMob Spawn #" + (index + 1) + " &cfrom wave " + wave
                        + ". &7Open &eManage Wave &7to re-assign.");
            }
        } else if ("boss".equals(type)) {
            List<SetupSession.BossSpawnEntry> spawns = waveData.getBossSpawns();
            if (index >= 0 && index < spawns.size()) {
                waveData.removeBossSpawn(index);
                setupManager.forceMarkerRefresh(player);
                msg(player, "&cRemoved &eBoss Spawn #" + (index + 1) + " &cfrom wave " + wave + ".");
            }
        }
    }

    // =========================================================================
    // HOTBAR TOOL INTERACTIONS
    // =========================================================================

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
        boolean isLeft  = event.getAction() == Action.LEFT_CLICK_AIR  || event.getAction() == Action.LEFT_CLICK_BLOCK;

        // Use clicked block location for precision; fallback to player location for air clicks
        Location loc;
        if (event.getClickedBlock() != null) {
            loc = event.getClickedBlock().getLocation().add(0.5, 1.0, 0.5);
        } else {
            loc = player.getLocation();
        }

        if (name.contains("Region Wand")) {
            if (isLeft) {
                session.setPos1(loc);
                msg(player, "&aPos1 set at &e" + formatLoc(loc));
            } else if (isRight) {
                session.setPos2(loc);
                msg(player, "&aPos2 set at &e" + formatLoc(loc));
            }

        } else if (name.contains("Start Point Tool") && isRight) {
            if (player.isSneaking() && session.isStatic()) {
                session.setEntryPoint(loc);
                msg(player, "&d⚡ Static entry point set at &e" + formatLoc(loc));
                return;
            }
            if (!session.isInsideRegion(loc)) {
                msg(player, "&c⚠ This location is outside the dungeon region!");
                return;
            }
            session.setSpawnPoint(loc);
            msg(player, "&aDungeon spawn point set at &e" + formatLoc(loc));

        } else if (name.contains("Mob Spawn Tool") && isRight) {
            if (!session.isInsideRegion(loc)) {
                msg(player, "&c⚠ This location is outside the dungeon region!");
                return;
            }
            SetupSession.WaveData wave = session.getOrCreateWave(session.getSelectedWave());
            wave.addMobSpawn(loc);
            int idx = wave.getMobSpawns().size();
            msg(player, "&aMob Spawn #" + idx + " placed at &e" + formatLoc(loc)
                    + " &7(Wave " + session.getSelectedWave() + ") — assign mob in Manage Wave GUI");
            msg(player, "&7Right-click the &agreen marker &7in-world to remove it.");

        } else if (name.contains("Boss Spawn Tool") && isRight) {
            if (!session.isInsideRegion(loc)) {
                msg(player, "&c⚠ This location is outside the dungeon region!");
                return;
            }
            SetupSession.WaveData wave = session.getOrCreateWave(session.getSelectedWave());
            wave.addBossSpawn(loc);
            int idx = wave.getBossSpawns().size();
            msg(player, "&4Boss Spawn #" + idx + " placed at &e" + formatLoc(loc)
                    + " &7(Wave " + session.getSelectedWave() + ") — assign boss in Manage Wave GUI");
            msg(player, "&7Right-click the &6gold marker &7in-world to remove it.");

        } else if (name.contains("Save Wave Tool") && isRight) {
            // ===== PART 2.2 — VALIDATE BEFORE SAVING WAVE =====
            int current = session.getSelectedWave();
            SetupSession.WaveData wave = session.getOrCreateWave(current);

            if (wave.getMobSpawns().isEmpty()) {
                msg(player, "&c⚠ You must place at least 1 Mob Spawn before saving the wave!");
                return;
            }

            // Check all mob spawns have assigned mob IDs
            long unassignedMobs = wave.getMobSpawns().stream()
                    .filter(e -> !e.isAssigned()).count();
            if (unassignedMobs > 0) {
                msg(player, "&c⚠ &e" + unassignedMobs + " mob spawn(s) &care not configured yet.");
                msg(player, "&cYou must finish &eManage Mob/Boss &cbefore saving this wave.");
                msg(player, "&7Open &eManage Wave GUI &7→ Manage Spawns to assign mobs.");
                return;
            }

            // Check all boss spawns have assigned boss IDs (if any boss spawn exists)
            if (!wave.getBossSpawns().isEmpty()) {
                long unassignedBosses = wave.getBossSpawns().stream()
                        .filter(e -> !e.isAssigned()).count();
                if (unassignedBosses > 0) {
                    msg(player, "&c⚠ &e" + unassignedBosses + " boss spawn(s) &care not configured yet.");
                    msg(player, "&cYou must finish &eManage Mob/Boss &cbefore saving this wave.");
                    return;
                }
            }

            // All good — advance to next wave
            session.setSelectedWave(current + 1);
            msg(player, "&aWave &e" + current + " &asaved! Now configuring Wave &e" + (current + 1) + ".");

        } else if (name.contains("Manage Wave Tool") && isRight) {
            waveManageGUI.openMain(player, session, session.getSelectedWave());

        } else if (name.contains("Save Dungeon Tool") && isRight) {
            setupManager.saveSetup(player);

        } else if (name.contains("Exit Setup Tool") && isRight) {
            setupManager.cancelSetup(player);
        }
    }

    // =========================================================================
    // INVENTORY PROTECTION
    // =========================================================================

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

    // =========================================================================
    // GUI CLICK ROUTING
    // =========================================================================

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!setupManager.isInSetup(player.getUniqueId())) return;

        // Protect hotbar from being moved
        if (event.getClickedInventory() != null
                && event.getClickedInventory().equals(player.getInventory())) {
            event.setCancelled(true);
            return;
        }

        String title = event.getView().title() != null
                ? PlainTextComponentSerializer.plainText().serialize(event.getView().title())
                : "";

        if      (title.contains("Select Mob Type") || title.contains("Select Boss Type"))
            handleMobSelectClick(event, player, title.contains("Boss"));
        else if (title.contains("Select Count"))
            handleCountClick(event, player);
        else if (title.contains("Wave Manager"))
            handleWaveManagerClick(event, player, title);
        else if (title.contains("Spawn Manager"))
            handleSpawnManagerClick(event, player);
        else if (title.contains("Mob Manager"))
            handleMobManagerClick(event, player);
        else if (title.contains("Edit Wave"))
            handleWaveDetailClick(event, player);
        else if (title.contains("Set Difficulty"))
            handleDifficultyClick(event, player);
        else if (title.contains("Wave Rewards"))
            handleRewardGUIClick(event, player);
        else if (title.contains("Select Reward Bundle"))
            handleBundleSelectClick(event, player);
    }

    // =========================================================================
    // GUI HANDLERS
    // =========================================================================

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
        int waveNum = session.getSelectedWave();
        SetupSession.WaveData wd = session.getOrCreateWave(waveNum);
        int spawnIdx = session.getPendingSpawnIndex();

        if (isBoss || session.isPendingIsBoss()) {
            if (spawnIdx >= 0 && spawnIdx < wd.getBossSpawns().size()) {
                wd.getBossSpawns().get(spawnIdx).setBossId(mobId);
            } else {
                wd.setBossId(mobId);
            }
            player.closeInventory();
            msg(player, "&4Boss set to &c" + mobId + " &4for wave &c" + waveNum);
            // Auto-refresh Spawn Manager
            waveManageGUI.openSpawnManager(player, session, waveNum);
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
            int count = Integer.parseInt(name.replaceAll("[^0-9]", ""));
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
        java.util.List<Map.Entry<Integer, SetupSession.WaveData>> waveList =
                new java.util.ArrayList<>(session.getWaves().entrySet());
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
            case 10 -> waveManageGUI.openSpawnManager(player, session, waveNum);
            case 12 -> bundleSelectGUI.open(player, waveNum, wave.getBundleId());
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
            case 16 -> difficultyGUI.open(player, session);
            case 22 -> waveManageGUI.openMain(player, session, 1);
        }
    }

    /**
     * Mob Manager GUI — display-only, SHIFT+LEFT to remove mobs.
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

        if (slot == 33) { waveManageGUI.openDetail(player, session, waveNum); return; }

        if (slot == 27) {
            player.setMetadata("setup_is_boss", new FixedMetadataValue(plugin, false));
            setupMobGUI.openMobSelect(player, false);
            return;
        }
        if (slot == 35) {
            player.setMetadata("setup_is_boss", new FixedMetadataValue(plugin, true));
            setupMobGUI.openMobSelect(player, true);
            return;
        }

        if (slot == 31 && wave.getBossId() != null
                && event.isShiftClick() && event.isLeftClick()) {
            wave.setBossId(null);
            wave.setBossName(null);
            waveManageGUI.openMobManager(player, session, waveNum);
            msg(player, "&cBoss removed from wave " + waveNum);
            return;
        }

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
     * Spawn Manager GUI — per-spawn assignment.
     * v1.0.8: auto-refreshes after every action (already done via openSpawnManager calls).
     */
    private void handleSpawnManagerClick(InventoryClickEvent event, Player player) {
        event.setCancelled(true);
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null
                || clicked.getType() == Material.BLACK_STAINED_GLASS_PANE
                || clicked.getType() == Material.ORANGE_STAINED_GLASS_PANE) return;

        SetupSession session = setupManager.getSession(player.getUniqueId());
        if (session == null) return;
        int waveNum = session.getSelectedWave();
        SetupSession.WaveData wave = session.getOrCreateWave(waveNum);
        int slot = event.getRawSlot();

        // Back button
        if (slot == 49) { waveManageGUI.openDetail(player, session, waveNum); return; }

        // Mob spawn slots: 10-16, 19-25
        int[] mobSlots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};
        for (int i = 0; i < mobSlots.length; i++) {
            if (mobSlots[i] == slot && i < wave.getMobSpawns().size()) {
                if (event.isShiftClick() && event.isLeftClick()) {
                    wave.removeMobSpawn(i);
                    setupManager.forceMarkerRefresh(player);
                    msg(player, "&cRemoved Mob Spawn #" + (i + 1) + " from wave " + waveNum);
                    waveManageGUI.openSpawnManager(player, session, waveNum); // auto-refresh
                } else {
                    session.setPendingSpawnIndex(i);
                    session.setPendingIsBoss(false);
                    setupMobGUI.openMobSelect(player, false);
                }
                return;
            }
        }

        // Boss spawn slots: 37-43
        int[] bossSlots = {37, 38, 39, 40, 41, 42, 43};
        for (int i = 0; i < bossSlots.length; i++) {
            if (bossSlots[i] == slot && i < wave.getBossSpawns().size()) {
                if (event.isShiftClick() && event.isLeftClick()) {
                    wave.removeBossSpawn(i);
                    setupManager.forceMarkerRefresh(player);
                    msg(player, "&cRemoved Boss Spawn #" + (i + 1) + " from wave " + waveNum);
                    waveManageGUI.openSpawnManager(player, session, waveNum); // auto-refresh
                } else {
                    session.setPendingSpawnIndex(i);
                    session.setPendingIsBoss(true);
                    setupMobGUI.openMobSelect(player, true);
                }
                return;
            }
        }
    }

    private void handleBundleSelectClick(InventoryClickEvent event, Player player) {
        event.setCancelled(true);
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.BLACK_STAINED_GLASS_PANE) return;

        SetupSession session = setupManager.getSession(player.getUniqueId());
        if (session == null) return;
        int waveNum = session.getSelectedWave();
        SetupSession.WaveData wave = session.getOrCreateWave(waveNum);
        int slot = event.getRawSlot();

        if (slot == 49) { waveManageGUI.openDetail(player, session, waveNum); return; }
        if (slot == 48) {
            wave.setBundleId(null);
            configManager.setWaveBundleId(session.getDungeonId(), waveNum, null);
            msg(player, "&7Bundle removed from wave " + waveNum);
            waveManageGUI.openDetail(player, session, waveNum);
            return;
        }

        int[] bundleSlots = {10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34};
        int bundleIdx = -1;
        for (int i = 0; i < bundleSlots.length; i++) {
            if (bundleSlots[i] == slot) { bundleIdx = i; break; }
        }
        if (bundleIdx < 0) return;

        var bundleIds = new java.util.ArrayList<>(configManager.getRewardBundles().keySet());
        if (bundleIdx >= bundleIds.size()) return;

        String bundleId = bundleIds.get(bundleIdx);
        wave.setBundleId(bundleId);
        configManager.setWaveBundleId(session.getDungeonId(), waveNum, bundleId);
        msg(player, "&aSet bundle &e" + bundleId + " &afor wave " + waveNum);
        waveManageGUI.openDetail(player, session, waveNum);
    }

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
        Difficulty diff = null;
        if      (name.contains("Normal"))         diff = Difficulty.NORMAL;
        else if (name.contains("Hard"))           diff = Difficulty.HARD;
        else if (name.contains("Super Difficult")) diff = Difficulty.SUPER_DIFFICULT;
        else if (name.contains("Nightmare"))      diff = Difficulty.NIGHTMARE;

        if (diff != null) {
            wave.setDifficulty(diff);
            waveManageGUI.openDetail(player, session, waveNum);
            msg(player, "&aSet difficulty of wave " + waveNum + " to " + name);
        }
    }

    // =========================================================================
    // CHAT INPUT FOR CUSTOM COUNT
    // =========================================================================

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
            if (count < 1 || count > 100) { msg(player, "&cCount must be 1-100!"); return; }
            org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> finalizeMobPlacement(player, session, count));
        } catch (NumberFormatException e) {
            msg(player, "&cInvalid number! Try again.");
        }
    }

    // =========================================================================
    // HELPERS
    // =========================================================================

    private void finalizeMobPlacement(Player player, SetupSession session, int count) {
        String mobId = session.getPendingMobId();
        if (mobId == null) return;

        int waveNum = session.getSelectedWave();
        SetupSession.WaveData wd = session.getOrCreateWave(waveNum);
        int spawnIdx = session.getPendingSpawnIndex();

        if (session.isPendingIsBoss()) {
            if (spawnIdx >= 0 && spawnIdx < wd.getBossSpawns().size()) {
                wd.getBossSpawns().get(spawnIdx).setBossId(mobId);
            } else {
                wd.setBossId(mobId);
            }
            player.closeInventory();
            msg(player, "&4Boss set: &c" + mobId + " &4for wave " + waveNum);
        } else {
            if (spawnIdx >= 0 && spawnIdx < wd.getMobSpawns().size()) {
                SetupSession.MobSpawnEntry entry = wd.getMobSpawns().get(spawnIdx);
                entry.setMobId(mobId);
                entry.setCount(count);
            } else {
                wd.addMob(mobId, count);
            }
            player.closeInventory();
            msg(player, "&aAssigned &e" + count + "x " + mobId
                    + " &ato spawn #" + (spawnIdx + 1) + " (wave " + waveNum + ")");
        }

        // Auto-refresh Spawn Manager GUI
        waveManageGUI.openSpawnManager(player, session, waveNum);

        session.setPendingMobId(null);
        session.setPendingSpawnIndex(-1);
        session.setPendingIsBoss(false);
        player.removeMetadata("setup_is_boss", plugin);
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

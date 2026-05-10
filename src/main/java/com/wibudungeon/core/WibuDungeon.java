package com.wibudungeon.core;

import com.wibudungeon.core.command.DungeonCommand;
import com.wibudungeon.core.config.ConfigManager;
import com.wibudungeon.core.dungeon.DungeonManager;
import com.wibudungeon.core.dungeon.SetupListener;
import com.wibudungeon.core.dungeon.SetupManager;
import com.wibudungeon.core.gui.AdminGUI;
import com.wibudungeon.core.gui.GUIListener;
import com.wibudungeon.core.gui.JoinGUI;
import com.wibudungeon.core.gui.PartyGUI;
import com.wibudungeon.core.gui.SetupMobGUI;
import com.wibudungeon.core.gui.WaveManageGUI;
import com.wibudungeon.core.mob.MobSpawner;
import com.wibudungeon.core.mob.MythicMobsHook;
import com.wibudungeon.core.party.PartyManager;
import com.wibudungeon.core.portal.PortalListener;
import com.wibudungeon.core.portal.PortalManager;
import com.wibudungeon.core.reward.RewardManager;
import com.wibudungeon.core.wave.WaveManager;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

/**
 * WibuDungeon - A professional dungeon system plugin for Minecraft.
 *
 * Features:
 * - Portal spawning with Display Entities
 * - Wave-based combat with mob scaling
 * - Party system with GUI management
 * - Boss fights and configurable rewards
 * - Admin wand tools for dungeon setup
 * - BossBar progression display
 *
 * @author WibuDev
 * @version 1.0.9
 */
public class WibuDungeon extends JavaPlugin {

    private ConfigManager configManager;
    private PartyManager partyManager;
    private MobSpawner mobSpawner;
    private RewardManager rewardManager;
    private WaveManager waveManager;
    private DungeonManager dungeonManager;
    private PortalManager portalManager;
    private SetupManager setupManager;
    private com.wibudungeon.core.reward.RewardChestManager rewardChestManager;
    private com.wibudungeon.core.portal.TrackingManager trackingManager;
    private com.wibudungeon.core.portal.StaticEntryManager staticEntryManager;

    // GUIs
    private JoinGUI joinGUI;
    private PartyGUI partyGUI;
    private AdminGUI adminGUI;
    private SetupMobGUI setupMobGUI;
    private WaveManageGUI waveManageGUI;

    @Override
    public void onEnable() {
        long startTime = System.currentTimeMillis();

        getLogger().info("========================================");
        getLogger().info("  WibuDungeon v" + getDescription().getVersion());
        getLogger().info("  Initializing...");
        getLogger().info("========================================");

        try {
            // Initialize managers with dependency injection
            initializeManagers();

            // Load configurations
            configManager.loadAll();

            // Register commands
            registerCommands();

            // Register event listeners
            registerListeners();

            // Start portal scheduler (for DYNAMIC dungeons)
            portalManager.startScheduler();

            // Load static dungeon entries (v1.0.7)
            staticEntryManager.loadAll();

            long elapsed = System.currentTimeMillis() - startTime;
            getLogger().info("========================================");
            getLogger().info("  WibuDungeon enabled successfully!");
            getLogger().info("  Loaded in " + elapsed + "ms");
            getLogger().info("  Dungeons: " + configManager.getDungeons().size());
            getLogger().info("========================================");

        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Failed to enable WibuDungeon!", e);
            Bukkit.getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        getLogger().info("Disabling WibuDungeon...");

        // Stop portal scheduler and remove portals
        if (portalManager != null) {
            portalManager.stopScheduler();
        }

        // Clean up all active dungeons
        if (dungeonManager != null) {
            dungeonManager.cleanupAll();
        }

        // Clean up setup sessions
        if (setupManager != null) {
            setupManager.cleanupAll();
        }

        // Clean up reward chests
        if (rewardChestManager != null) {
            rewardChestManager.cleanupAll();
        }

        // Clean up tracking sessions
        if (trackingManager != null) {
            trackingManager.cleanupAll();
        }

        // Clean up static entries (v1.0.7)
        if (staticEntryManager != null) {
            staticEntryManager.cleanupAll();
        }

        getLogger().info("WibuDungeon disabled.");
    }

    /**
     * Initialize all managers with proper dependency injection.
     */
    private void initializeManagers() {
        // Core managers (no game dependencies)
        configManager = new ConfigManager(this);
        partyManager = new PartyManager(configManager);
        MythicMobsHook mythicHook = new MythicMobsHook(this);
        mobSpawner = new MobSpawner(this, mythicHook);
        rewardManager = new RewardManager(configManager);

        // Managers that depend on core
        waveManager = new WaveManager(this, configManager, mobSpawner, rewardManager);
        dungeonManager = new DungeonManager(this, configManager, partyManager, waveManager, rewardManager);
        portalManager = new PortalManager(this, configManager);
        setupManager = new SetupManager(this, configManager);

        // GUIs (depend on managers)
        joinGUI = new JoinGUI(configManager, partyManager, dungeonManager, portalManager);
        partyGUI = new PartyGUI(configManager, partyManager);
        adminGUI = new AdminGUI(configManager, dungeonManager);
        setupMobGUI = new SetupMobGUI(mythicHook);
        waveManageGUI = new WaveManageGUI();

        // Reward chest manager
        rewardChestManager = new com.wibudungeon.core.reward.RewardChestManager(this, configManager);
        waveManager.setRewardChestManager(rewardChestManager);
        dungeonManager.setRewardChestManager(rewardChestManager);

        // Tracking manager
        trackingManager = new com.wibudungeon.core.portal.TrackingManager(this, configManager, portalManager);

        // Static entry manager (v1.0.7)
        staticEntryManager = new com.wibudungeon.core.portal.StaticEntryManager(this, configManager);
        staticEntryManager.setJoinGUI(joinGUI);
    }

    /**
     * Register the /wd command.
     */
    private void registerCommands() {
        DungeonCommand cmd = new DungeonCommand(
                configManager, dungeonManager, partyManager, mobSpawner,
                setupManager, adminGUI, partyGUI);
        cmd.setTrackingManager(trackingManager);
        cmd.setPortalManager(portalManager);
        cmd.setStaticEntryManager(staticEntryManager);

        PluginCommand pluginCmd = getCommand("wd");
        if (pluginCmd != null) {
            pluginCmd.setExecutor(cmd);
            pluginCmd.setTabCompleter(cmd);
        } else {
            getLogger().warning("Could not register /wd command!");
        }
    }

    /**
     * Register all event listeners.
     */
    private void registerListeners() {
        // Portal interaction and combat listener
        PortalListener portalListener = new PortalListener(
                portalManager, dungeonManager, waveManager, joinGUI);
        registerListener(portalListener);

        // GUI click handler
        GUIListener guiListener = new GUIListener(
                configManager, dungeonManager, partyManager, portalManager,
                joinGUI, partyGUI, adminGUI);
        registerListener(guiListener);

        // Chat listener for party invites from GUI
        registerListener(new ChatInviteListener(partyManager, configManager));

        // Setup mode listener
        SetupListener setupListener = new SetupListener(
                this, setupManager, configManager, setupMobGUI, waveManageGUI);
        registerListener(setupListener);

        // Reward chest listener
        registerListener(rewardChestManager);

        // Static entry listener (v1.0.7)
        registerListener(staticEntryManager);
    }

    private void registerListener(Listener listener) {
        Bukkit.getPluginManager().registerEvents(listener, this);
    }

    // ===== ACCESSORS =====

    public ConfigManager getConfigManager() { return configManager; }
    public PartyManager getPartyManager() { return partyManager; }
    public DungeonManager getDungeonManager() { return dungeonManager; }
    public PortalManager getPortalManager() { return portalManager; }
    public WaveManager getWaveManager() { return waveManager; }

    /**
     * Inner listener class for handling chat-based party invites from GUI.
     */
    public static class ChatInviteListener implements Listener {

        private final PartyManager partyManager;
        private final ConfigManager configManager;

        public ChatInviteListener(PartyManager partyManager, ConfigManager configManager) {
            this.partyManager = partyManager;
            this.configManager = configManager;
        }

        @org.bukkit.event.EventHandler
        public void onChat(org.bukkit.event.player.AsyncPlayerChatEvent event) {
            org.bukkit.entity.Player player = event.getPlayer();

            if (!player.hasMetadata("wibudungeon_invite")) return;

            event.setCancelled(true);
            String targetName = event.getMessage().trim();

            // Must run sync
            Bukkit.getScheduler().runTask(
                    Bukkit.getPluginManager().getPlugin("WibuDungeon"),
                    () -> {
                        player.removeMetadata("wibudungeon_invite",
                                Bukkit.getPluginManager().getPlugin("WibuDungeon"));

                        org.bukkit.entity.Player target = Bukkit.getPlayer(targetName);
                        if (target == null) {
                            com.wibudungeon.core.util.MessageUtil.send(player,
                                    configManager.getMessage("party.player-not-found"));
                            return;
                        }

                        partyManager.invitePlayer(player, target);
                    }
            );
        }
    }
}

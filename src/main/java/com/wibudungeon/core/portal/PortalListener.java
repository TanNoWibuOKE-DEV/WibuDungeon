package com.wibudungeon.core.portal;

import com.wibudungeon.core.config.ConfigManager;
import com.wibudungeon.core.dungeon.DungeonManager;
import com.wibudungeon.core.dungeon.DungeonInstance;
import com.wibudungeon.core.gui.JoinGUI;
import com.wibudungeon.core.mob.MobSpawner;
import com.wibudungeon.core.wave.WaveManager;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Listens for portal interactions (click + walk-in), dungeon combat, and death events.
 */
public class PortalListener implements Listener {

    private final PortalManager portalManager;
    private final DungeonManager dungeonManager;
    private final WaveManager waveManager;
    private final JoinGUI joinGUI;
    private final ConfigManager configManager;
    private TrackingManager trackingManager; // v1.0.9: for per-player HUD visibility

    // Cooldown to prevent GUI spam
    private final Map<UUID, Long> portalCooldowns = new HashMap<>();

    public PortalListener(PortalManager portalManager, DungeonManager dungeonManager,
                          WaveManager waveManager, JoinGUI joinGUI, ConfigManager configManager) {
        this.portalManager = portalManager;
        this.dungeonManager = dungeonManager;
        this.waveManager = waveManager;
        this.joinGUI = joinGUI;
        this.configManager = configManager;
    }

    /** v1.0.9: Set tracking manager for per-player HUD visibility on join. */
    public void setTrackingManager(TrackingManager trackingManager) {
        this.trackingManager = trackingManager;
    }

    /**
     * Auto-open GUI when player walks near a portal.
     * v1.0.9: Skip if sneaking (shift), reduced range, removed premature markUsed.
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerMove(PlayerMoveEvent event) {
        // Skip if player hasn't actually moved position (head rotation only)
        if (!event.hasExplicitlyChangedPosition()) return;

        Player player = event.getPlayer();
        if (dungeonManager.isInDungeon(player.getUniqueId())) return;

        // Don't auto-open if sneaking — allows walking past without triggering
        if (player.isSneaking()) return;

        // Don't open if player already has an inventory open
        if (player.getOpenInventory().getType() != org.bukkit.event.inventory.InventoryType.CRAFTING) return;

        // Check cooldown (configurable, default 3 seconds)
        long cooldownMs = configManager.getPortalGuiCooldown() * 1000L;
        Long lastUse = portalCooldowns.get(player.getUniqueId());
        if (lastUse != null && System.currentTimeMillis() - lastUse < cooldownMs) return;

        // Check proximity to any portal (2 block range — tighter to avoid spam)
        DungeonPortal portal = portalManager.getNearbyPortal(player.getLocation(), 2.0);
        if (portal == null || portal.isExpired()) return;

        portalCooldowns.put(player.getUniqueId(), System.currentTimeMillis());
        joinGUI.open(player, portal);
    }

    /**
     * Handle right-click on portal Interaction entity (fallback).
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEntityEvent event) {
        Entity clicked = event.getRightClicked();
        if (!(clicked instanceof org.bukkit.entity.Interaction)) return;

        DungeonPortal portal = portalManager.getPortalByInteraction(clicked.getUniqueId());
        if (portal == null) return;

        event.setCancelled(true);
        Player player = event.getPlayer();
        if (dungeonManager.isInDungeon(player.getUniqueId())) return;

        long cooldownMs = configManager.getPortalGuiCooldown() * 1000L;
        Long lastUse = portalCooldowns.get(player.getUniqueId());
        if (lastUse != null && System.currentTimeMillis() - lastUse < cooldownMs) return;

        portalCooldowns.put(player.getUniqueId(), System.currentTimeMillis());
        portalManager.markPortalUsed(portal.getPortalId()); // v1.0.6: mark portal as used
        joinGUI.open(player, portal);
    }

    /**
     * Handle dungeon mob death.
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (!MobSpawner.isDungeonMob(entity)) return;

        String instanceId = MobSpawner.getInstanceId(entity);
        if (instanceId == null) return;

        // Find the dungeon instance directly by checking any player in the instance
        DungeonInstance instance = null;
        for (Player player : entity.getWorld().getPlayers()) {
            DungeonInstance pi = dungeonManager.getPlayerInstance(player.getUniqueId());
            if (pi != null && pi.getInstanceId().equals(instanceId)) {
                instance = pi;
                break;
            }
        }

        if (instance != null) {
            event.getDrops().clear();
            event.setDroppedExp(0);
            waveManager.onMobKilled(instance, entity);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        DungeonInstance instance = dungeonManager.getPlayerInstance(player.getUniqueId());
        if (instance == null) return;

        event.getDrops().clear();
        event.setDroppedExp(0);
        event.setKeepInventory(true);
        event.setKeepLevel(true);
    }

    @EventHandler
    public void onPlayerRespawn(org.bukkit.event.player.PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        DungeonInstance instance = dungeonManager.getPlayerInstance(player.getUniqueId());
        if (instance == null) return;

        event.setRespawnLocation(instance.getDungeon().getSpawnPoint());
        org.bukkit.Bukkit.getScheduler().runTaskLater(portalManager.getPlugin(),
                () -> instance.onPlayerDeath(player), 2L);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        portalCooldowns.remove(player.getUniqueId());
        // Properly leave dungeon instead of marking as dead
        if (dungeonManager.isInDungeon(player.getUniqueId())) {
            dungeonManager.leaveDungeon(player);
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        DungeonInstance instance = dungeonManager.getPlayerInstance(player.getUniqueId());
        if (instance != null && instance.getSpectators().contains(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    /** v1.0.9: Hide existing tracking HUD entities from joining players. */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (trackingManager != null) {
            trackingManager.hideAllFrom(event.getPlayer());
        }
    }
}

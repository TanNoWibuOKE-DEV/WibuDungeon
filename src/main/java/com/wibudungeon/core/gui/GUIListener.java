package com.wibudungeon.core.gui;

import com.wibudungeon.core.config.ConfigManager;
import com.wibudungeon.core.dungeon.Dungeon;
import com.wibudungeon.core.dungeon.DungeonManager;
import com.wibudungeon.core.party.Party;
import com.wibudungeon.core.party.PartyManager;
import com.wibudungeon.core.portal.DungeonPortal;
import com.wibudungeon.core.portal.PortalManager;
import com.wibudungeon.core.util.MessageUtil;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;

/**
 * Handles inventory click events for all GUIs.
 */
public class GUIListener implements Listener {

    private final ConfigManager configManager;
    private final DungeonManager dungeonManager;
    private final PartyManager partyManager;
    private final PortalManager portalManager;
    private final JoinGUI joinGUI;
    private final PartyGUI partyGUI;
    private final AdminGUI adminGUI;

    public GUIListener(ConfigManager configManager, DungeonManager dungeonManager,
                       PartyManager partyManager, PortalManager portalManager,
                       JoinGUI joinGUI, PartyGUI partyGUI, AdminGUI adminGUI) {
        this.configManager = configManager;
        this.dungeonManager = dungeonManager;
        this.partyManager = partyManager;
        this.portalManager = portalManager;
        this.joinGUI = joinGUI;
        this.partyGUI = partyGUI;
        this.adminGUI = adminGUI;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getCurrentItem() == null) return;

        String title = PlainTextComponentSerializer.plainText()
                .serialize(event.getView().title());

        // Route to appropriate handler
        if (title.contains("Dungeon Portal")) {
            event.setCancelled(true);
            handleJoinGUI(player, event);
        } else if (title.contains("Party Manager")) {
            event.setCancelled(true);
            handlePartyGUI(player, event);
        } else if (title.contains("Dungeon Admin")) {
            event.setCancelled(true);
            handleAdminMainGUI(player, event);
        } else if (title.contains("Edit Dungeon")) {
            event.setCancelled(true);
            handleAdminEditGUI(player, event, title);
        }
    }

    /**
     * Clean up portal metadata when any GUI is closed.
     */
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        String title = PlainTextComponentSerializer.plainText()
                .serialize(event.getView().title());
        if (title.contains("Dungeon Portal")) {
            if (player.hasMetadata("wibudungeon_portal")) {
                player.removeMetadata("wibudungeon_portal",
                        player.getServer().getPluginManager().getPlugin("WibuDungeon"));
            }
            if (player.hasMetadata("wibudungeon_static_dungeon")) {
                player.removeMetadata("wibudungeon_static_dungeon",
                        player.getServer().getPluginManager().getPlugin("WibuDungeon"));
            }
        }
    }

    private void handleJoinGUI(Player player, InventoryClickEvent event) {
        ItemStack item = event.getCurrentItem();
        if (item == null || item.getType() == Material.BLACK_STAINED_GLASS_PANE) return;

        int slot = event.getSlot();
        boolean isStatic = player.hasMetadata("wibudungeon_static_dungeon");

        switch (slot) {
            case 12 -> {
                // Create party or invite
                // v1.0.8 fix: Read metadata BEFORE closeInventory() wipes it
                boolean hasPortal12 = player.hasMetadata("wibudungeon_portal");
                String portalIdStr12 = hasPortal12
                        ? player.getMetadata("wibudungeon_portal").getFirst().asString() : null;
                String staticId12 = isStatic
                        ? player.getMetadata("wibudungeon_static_dungeon").getFirst().asString() : null;

                Party party = partyManager.getParty(player.getUniqueId());
                if (party == null) {
                    partyManager.createParty(player);
                    player.closeInventory();
                    // Reopen with updated info
                    if (isStatic && staticId12 != null) {
                        joinGUI.openForStatic(player, staticId12);
                    } else if (hasPortal12 && portalIdStr12 != null) {
                        UUID portalId = UUID.fromString(portalIdStr12);
                        DungeonPortal portal = null;
                        for (DungeonPortal p : portalManager.getActivePortals()) {
                            if (p.getPortalId().equals(portalId)) {
                                portal = p;
                                break;
                            }
                        }
                        if (portal != null) joinGUI.open(player, portal);
                    }
                } else {
                    player.closeInventory();
                    MessageUtil.send(player, configManager.getPrefix() +
                            "&eType a player name in chat to invite:");
                    player.setMetadata("wibudungeon_invite",
                            new org.bukkit.metadata.FixedMetadataValue(
                                    player.getServer().getPluginManager().getPlugin("WibuDungeon"),
                                    true));
                }
            }
            case 14 -> {
                // Start dungeon
                if (item.getType() == Material.LIME_WOOL) {
                    // v1.0.8 fix: Read ALL metadata BEFORE closeInventory()
                    // because closeInventory() fires InventoryCloseEvent which wipes metadata.
                    boolean hasPortal = player.hasMetadata("wibudungeon_portal");
                    String portalIdStr = hasPortal
                            ? player.getMetadata("wibudungeon_portal").getFirst().asString() : null;
                    String staticDungeonId = isStatic
                            ? player.getMetadata("wibudungeon_static_dungeon").getFirst().asString() : null;

                    player.closeInventory(); // fires InventoryCloseEvent → metadata cleared here

                    if (isStatic && staticDungeonId != null) {
                        // v1.0.7: Static dungeon — start directly without portal
                        dungeonManager.startDungeon(staticDungeonId, player);
                    } else if (hasPortal && portalIdStr != null) {
                        // Dynamic dungeon — existing portal flow
                        UUID portalId = UUID.fromString(portalIdStr);
                        DungeonPortal portal = null;
                        for (DungeonPortal p : portalManager.getActivePortals()) {
                            if (p.getPortalId().equals(portalId)) {
                                portal = p;
                                break;
                            }
                        }
                        if (portal != null) {
                            boolean started = dungeonManager.startDungeon(
                                    portal.getDungeonId(), player);
                            if (started) {
                                portalManager.removePortal(portalId);
                            }
                        } else {
                            // Portal expired or removed before player clicked Start
                            MessageUtil.send(player, configManager.getMessage("portal.expired"));
                        }
                    }
                }
            }
            case 22 -> player.closeInventory();
        }
    }

    private void handlePartyGUI(Player player, InventoryClickEvent event) {
        ItemStack item = event.getCurrentItem();
        if (item == null || item.getType() == Material.BLACK_STAINED_GLASS_PANE) return;

        int slot = event.getSlot();
        Party party = partyManager.getParty(player.getUniqueId());

        if (slot == 13 && party == null) {
            // Create party
            partyManager.createParty(player);
            player.closeInventory();
            partyGUI.open(player);
            return;
        }

        if (party == null) return;

        switch (item.getType()) {
            case NAME_TAG -> {
                // Invite player
                player.closeInventory();
                MessageUtil.send(player, configManager.getPrefix() +
                        "&eType a player name in chat to invite:");
                player.setMetadata("wibudungeon_invite",
                        new org.bukkit.metadata.FixedMetadataValue(
                                player.getServer().getPluginManager().getPlugin("WibuDungeon"),
                                true));
            }
            case TNT -> {
                // Disband
                player.closeInventory();
                partyManager.disbandParty(player);
            }
            case IRON_DOOR -> {
                // Leave
                player.closeInventory();
                partyManager.leaveParty(player);
            }
            case BARRIER -> player.closeInventory();
            default -> {
                // Kick member if clicking on a player head (owner only)
                if (item.getType() == Material.PLAYER_HEAD && party.isOwner(player.getUniqueId())) {
                    if (slot >= 10 && slot <= 16) {
                        // Find which member was clicked
                        int memberIndex = slot - 10;
                        UUID[] members = party.getMembers().toArray(new UUID[0]);
                        if (memberIndex < members.length) {
                            UUID targetId = members[memberIndex];
                            if (!party.isOwner(targetId)) {
                                party.removeMember(targetId);
                                partyManager.removeFromTracking(targetId);
                                Player target = player.getServer().getPlayer(targetId);
                                if (target != null) {
                                    MessageUtil.send(target, configManager.getMessage("party.kicked"));
                                }
                                partyGUI.open(player); // Refresh
                            }
                        }
                    }
                }
            }
        }
    }

    private void handleAdminMainGUI(Player player, InventoryClickEvent event) {
        ItemStack item = event.getCurrentItem();
        if (item == null || item.getType() == Material.GRAY_STAINED_GLASS_PANE) return;

        int slot = event.getSlot();
        int size = event.getInventory().getSize();

        if (item.getType() == Material.BARRIER) {
            player.closeInventory();
            return;
        }

        if (item.getType() == Material.COMPASS) {
            // Reload
            player.closeInventory();
            configManager.loadAll();
            MessageUtil.send(player, configManager.getMessage("general.reload"));
            return;
        }

        // Dungeon item clicked
        if ((item.getType() == Material.LIME_CONCRETE || item.getType() == Material.RED_CONCRETE)
                && slot >= 9 && slot < size - 9) {
            if (item.getItemMeta() == null || item.getItemMeta().displayName() == null) return;
            String displayName = PlainTextComponentSerializer.plainText()
                    .serialize(item.getItemMeta().displayName());
            String dungeonId = displayName.trim();

            if (event.isRightClick()) {
                // Toggle enabled
                Dungeon dungeon = configManager.getDungeon(dungeonId);
                if (dungeon != null) {
                    dungeon.setEnabled(!dungeon.isEnabled());
                    configManager.saveDungeon(dungeon);
                    adminGUI.openMain(player); // Refresh
                }
            } else {
                // Open edit GUI
                adminGUI.openEdit(player, dungeonId);
            }
        }
    }

    private void handleAdminEditGUI(Player player, InventoryClickEvent event, String title) {
        ItemStack item = event.getCurrentItem();
        if (item == null || item.getType() == Material.GRAY_STAINED_GLASS_PANE
                || item.getType() == Material.BLACK_STAINED_GLASS_PANE) return;

        // Extract dungeon ID from title
        String dungeonId = title.substring(title.lastIndexOf("- ") + 2).trim();

        switch (item.getType()) {
            case LIME_DYE, GRAY_DYE -> {
                // Toggle enabled
                Dungeon dungeon = configManager.getDungeon(dungeonId);
                if (dungeon != null) {
                    dungeon.setEnabled(!dungeon.isEnabled());
                    configManager.saveDungeon(dungeon);
                    adminGUI.openEdit(player, dungeonId);
                }
            }
            case BLAZE_ROD -> {
                // Enter setup mode
                player.closeInventory();
                player.performCommand("wd setup " + dungeonId);
            }
            case ENDER_PEARL -> {
                // Teleport to dungeon
                player.closeInventory();
                player.performCommand("wd tp " + dungeonId);
            }
            case END_PORTAL_FRAME -> {
                // Spawn portal
                player.closeInventory();
                player.performCommand("wd spawnportal " + dungeonId);
            }
            case LAVA_BUCKET -> {
                // Delete dungeon
                player.closeInventory();
                configManager.deleteDungeon(dungeonId);
                MessageUtil.send(player, configManager.getMessage("admin.dungeon-deleted"),
                        "%id%", dungeonId);
            }
            case ARROW -> adminGUI.openMain(player);
            case BARRIER -> player.closeInventory();
        }
    }
}

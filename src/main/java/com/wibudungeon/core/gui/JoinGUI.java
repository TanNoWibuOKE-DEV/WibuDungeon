package com.wibudungeon.core.gui;

import com.wibudungeon.core.config.ConfigManager;
import com.wibudungeon.core.dungeon.Dungeon;
import com.wibudungeon.core.dungeon.DungeonManager;
import com.wibudungeon.core.party.Party;
import com.wibudungeon.core.party.PartyManager;
import com.wibudungeon.core.portal.DungeonPortal;
import com.wibudungeon.core.portal.PortalManager;
import com.wibudungeon.core.util.ItemBuilder;
import com.wibudungeon.core.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.UUID;

/**
 * GUI shown when a player interacts with a dungeon portal.
 */
public class JoinGUI {

    public static final String GUI_TITLE = "§8§l» §6§lDungeon Portal §8§l«";

    private final ConfigManager configManager;
    private final PartyManager partyManager;
    private final DungeonManager dungeonManager;
    private final PortalManager portalManager;

    public JoinGUI(ConfigManager configManager, PartyManager partyManager,
                   DungeonManager dungeonManager, PortalManager portalManager) {
        this.configManager = configManager;
        this.partyManager = partyManager;
        this.dungeonManager = dungeonManager;
        this.portalManager = portalManager;
    }

    /**
     * Open the join GUI for a player at a portal.
     */
    public void open(Player player, DungeonPortal portal) {
        Inventory gui = Bukkit.createInventory(null, 27, MessageUtil.colorize(GUI_TITLE));

        Dungeon dungeon = configManager.getDungeon(portal.getDungeonId());
        String dungeonName = dungeon != null ? dungeon.getName() : portal.getDungeonId();

        // Dungeon info (slot 4)
        gui.setItem(4, new ItemBuilder(Material.ENDER_EYE)
                .name("&6&l" + MessageUtil.stripColor(dungeonName))
                .lore(
                        "&7",
                        "&e  Dungeon: &f" + dungeonName,
                        "&e  Max Players: &f" + (dungeon != null ? dungeon.getMaxPlayers() : "?"),
                        "&e  Active Instances: &f" + dungeonManager.getActiveInstanceCount(portal.getDungeonId()),
                        "&7"
                )
                .glow()
                .build());

        // Party info (slot 10)
        Party party = partyManager.getParty(player.getUniqueId());
        if (party != null) {
            StringBuilder memberList = new StringBuilder();
            for (UUID memberId : party.getMembers()) {
                Player member = Bukkit.getPlayer(memberId);
                String name = member != null ? member.getName() : "Offline";
                boolean isOwner = party.isOwner(memberId);
                memberList.append("&7  - ").append(isOwner ? "&e⭐ " : "&f").append(name).append("\n");
            }

            gui.setItem(10, new ItemBuilder(Material.PLAYER_HEAD)
                    .name("&a&lParty Info")
                    .lore(
                            "&7",
                            "&e  Members: &f" + party.getSize() + "/" + party.getMaxSize(),
                            "&7",
                            memberList.toString().trim(),
                            "&7"
                    )
                    .build());
        } else {
            gui.setItem(10, new ItemBuilder(Material.GRAY_DYE)
                    .name("&7No Party")
                    .lore(
                            "&7",
                            "&7  You are not in a party.",
                            "&7  Create one or go solo!",
                            "&7"
                    )
                    .build());
        }

        // Create party button (slot 12)
        if (party == null) {
            gui.setItem(12, new ItemBuilder(Material.EMERALD)
                    .name("&a&lCreate Party")
                    .lore(
                            "&7",
                            "&7  Click to create a party.",
                            "&7  Invite friends before starting!",
                            "&7"
                    )
                    .glow()
                    .build());
        } else {
            gui.setItem(12, new ItemBuilder(Material.NAME_TAG)
                    .name("&e&lInvite Player")
                    .lore(
                            "&7",
                            "&7  Type a player name in chat",
                            "&7  after clicking to invite them.",
                            "&7"
                    )
                    .build());
        }

        // Start dungeon button (slot 14)
        boolean canStart = party == null || party.isOwner(player.getUniqueId());
        // v1.0.8 fix: If solo is not allowed and player has no party, disable start button
        if (canStart && party == null && !configManager.isAllowSolo()) {
            canStart = false;
        }
        if (canStart) {
            gui.setItem(14, new ItemBuilder(Material.LIME_WOOL)
                    .name("&a&lStart Dungeon")
                    .lore(
                            "&7",
                            "&7  Click to enter the dungeon!",
                            "&7  All party members will be",
                            "&7  teleported together.",
                            "&7"
                    )
                    .glow()
                    .build());
        } else {
            gui.setItem(14, new ItemBuilder(Material.RED_WOOL)
                    .name("&c&lWaiting for Leader")
                    .lore(
                            "&7",
                            "&7  Only the party leader",
                            "&7  can start the dungeon.",
                            "&7"
                    )
                    .build());
        }

        // Close button (slot 22)
        gui.setItem(22, new ItemBuilder(Material.BARRIER)
                .name("&c&lClose")
                .lore("&7Click to close")
                .build());

        // Fill empty slots with glass
        for (int i = 0; i < gui.getSize(); i++) {
            if (gui.getItem(i) == null) {
                gui.setItem(i, new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE)
                        .name("&7")
                        .build());
            }
        }

        player.openInventory(gui);

        // Store portal reference for click handler
        player.setMetadata("wibudungeon_portal",
                new org.bukkit.metadata.FixedMetadataValue(
                        Bukkit.getPluginManager().getPlugin("WibuDungeon"),
                        portal.getPortalId().toString()));
    }

    /**
     * Open the join GUI for a STATIC dungeon (no portal required).
     * Uses dungeon ID directly instead of a portal reference.
     *
     * @since v1.0.7
     */
    public void openForStatic(Player player, String dungeonId) {
        Inventory gui = Bukkit.createInventory(null, 27, MessageUtil.colorize(GUI_TITLE));

        com.wibudungeon.core.dungeon.Dungeon dungeon = configManager.getDungeon(dungeonId);
        String dungeonName = dungeon != null ? dungeon.getName() : dungeonId;

        // Dungeon info (slot 4)
        gui.setItem(4, new ItemBuilder(Material.ENDER_EYE)
                .name("&6&l" + MessageUtil.stripColor(dungeonName))
                .lore(
                        "&7",
                        "&e  Dungeon: &f" + dungeonName,
                        "&e  Type: &d⚡ STATIC",
                        "&e  Max Players: &f" + (dungeon != null ? dungeon.getMaxPlayers() : "?"),
                        "&e  Active Instances: &f" + dungeonManager.getActiveInstanceCount(dungeonId),
                        "&7"
                )
                .glow()
                .build());

        // Party info (slot 10)
        com.wibudungeon.core.party.Party party = partyManager.getParty(player.getUniqueId());
        if (party != null) {
            StringBuilder memberList = new StringBuilder();
            for (java.util.UUID memberId : party.getMembers()) {
                Player member = Bukkit.getPlayer(memberId);
                String name = member != null ? member.getName() : "Offline";
                boolean isOwner = party.isOwner(memberId);
                memberList.append("&7  - ").append(isOwner ? "&e⭐ " : "&f").append(name).append("\n");
            }

            gui.setItem(10, new ItemBuilder(Material.PLAYER_HEAD)
                    .name("&a&lParty Info")
                    .lore("&7", "&e  Members: &f" + party.getSize() + "/" + party.getMaxSize(),
                            "&7", memberList.toString().trim(), "&7")
                    .build());
        } else {
            gui.setItem(10, new ItemBuilder(Material.GRAY_DYE)
                    .name("&7No Party")
                    .lore("&7", "&7  You are not in a party.", "&7  Create one or go solo!", "&7")
                    .build());
        }

        // Create party / Invite button (slot 12)
        if (party == null) {
            gui.setItem(12, new ItemBuilder(Material.EMERALD)
                    .name("&a&lCreate Party")
                    .lore("&7", "&7  Click to create a party.", "&7  Invite friends before starting!", "&7")
                    .glow().build());
        } else {
            gui.setItem(12, new ItemBuilder(Material.NAME_TAG)
                    .name("&e&lInvite Player")
                    .lore("&7", "&7  Type a player name in chat", "&7  after clicking to invite them.", "&7")
                    .build());
        }

        // Start dungeon button (slot 14)
        boolean canStart = party == null || party.isOwner(player.getUniqueId());
        // v1.0.8 fix: If solo is not allowed and player has no party, disable start button
        if (canStart && party == null && !configManager.isAllowSolo()) {
            canStart = false;
        }
        if (canStart) {
            gui.setItem(14, new ItemBuilder(Material.LIME_WOOL)
                    .name("&a&lStart Dungeon")
                    .lore("&7", "&7  Click to enter the dungeon!", "&7  All party members will be",
                            "&7  teleported together.", "&7")
                    .glow().build());
        } else {
            gui.setItem(14, new ItemBuilder(Material.RED_WOOL)
                    .name("&c&lWaiting for Leader")
                    .lore("&7", "&7  Only the party leader", "&7  can start the dungeon.", "&7")
                    .build());
        }

        // Close button (slot 22)
        gui.setItem(22, new ItemBuilder(Material.BARRIER)
                .name("&c&lClose").lore("&7Click to close").build());

        // Fill empty slots with glass
        for (int i = 0; i < gui.getSize(); i++) {
            if (gui.getItem(i) == null) {
                gui.setItem(i, new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name("&7").build());
            }
        }

        player.openInventory(gui);

        // Store static dungeon reference for click handler (prefix with "static:" to distinguish)
        player.setMetadata("wibudungeon_static_dungeon",
                new org.bukkit.metadata.FixedMetadataValue(
                        Bukkit.getPluginManager().getPlugin("WibuDungeon"),
                        dungeonId));
    }
}

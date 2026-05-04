package com.wibudungeon.core.gui;

import com.wibudungeon.core.config.ConfigManager;
import com.wibudungeon.core.party.Party;
import com.wibudungeon.core.party.PartyManager;
import com.wibudungeon.core.util.ItemBuilder;
import com.wibudungeon.core.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.UUID;

/**
 * GUI for managing party members, invites, and actions.
 */
public class PartyGUI {

    public static final String GUI_TITLE = "§8§l» §a§lParty Manager §8§l«";

    private final ConfigManager configManager;
    private final PartyManager partyManager;

    public PartyGUI(ConfigManager configManager, PartyManager partyManager) {
        this.configManager = configManager;
        this.partyManager = partyManager;
    }

    /**
     * Open the party GUI for a player.
     */
    public void open(Player player) {
        Inventory gui = Bukkit.createInventory(null, 36, MessageUtil.colorize(GUI_TITLE));
        Party party = partyManager.getParty(player.getUniqueId());

        if (party == null) {
            // No party - show create option
            gui.setItem(13, new ItemBuilder(Material.EMERALD)
                    .name("&a&lCreate Party")
                    .lore(
                            "&7",
                            "&7  Click to create a new party!",
                            "&7  You'll be the party leader.",
                            "&7"
                    )
                    .glow()
                    .build());
        } else {
            // Party header (slot 4)
            gui.setItem(4, new ItemBuilder(Material.GOLDEN_HELMET)
                    .name("&6&lYour Party")
                    .lore(
                            "&7",
                            "&e  Members: &f" + party.getSize() + "/" + party.getMaxSize(),
                            "&e  Leader: &f" + getPlayerName(party.getOwner()),
                            "&7"
                    )
                    .hideFlags()
                    .glow()
                    .build());

            // Show members starting at slot 10
            int slot = 10;
            for (UUID memberId : party.getMembers()) {
                if (slot > 16) break;

                Player member = Bukkit.getPlayer(memberId);
                String name = member != null ? member.getName() : "Offline";
                boolean isOwner = party.isOwner(memberId);
                boolean isSelf = memberId.equals(player.getUniqueId());

                ItemBuilder builder = new ItemBuilder(Material.PLAYER_HEAD)
                        .name((isOwner ? "&e⭐ " : "&a") + name);

                if (isOwner) {
                    builder.lore("&7", "&e  Party Leader", "&7");
                } else if (isSelf) {
                    builder.lore("&7", "&a  You", "&7", "&c  Click to leave party", "&7");
                } else if (party.isOwner(player.getUniqueId())) {
                    builder.lore("&7", "&a  Member", "&7", "&c  Click to kick", "&7");
                } else {
                    builder.lore("&7", "&a  Member", "&7");
                }

                gui.setItem(slot, builder.build());
                slot++;
            }

            // Invite button (slot 28) - only for owner
            if (party.isOwner(player.getUniqueId())) {
                gui.setItem(28, new ItemBuilder(Material.NAME_TAG)
                        .name("&e&lInvite Player")
                        .lore(
                                "&7",
                                "&7  Click then type a player",
                                "&7  name in chat to invite.",
                                "&7"
                        )
                        .glow()
                        .build());

                // Disband button (slot 34)
                gui.setItem(34, new ItemBuilder(Material.TNT)
                        .name("&c&lDisband Party")
                        .lore(
                                "&7",
                                "&c  Click to disband the party.",
                                "&c  This cannot be undone!",
                                "&7"
                        )
                        .build());
            } else {
                // Leave button (slot 31)
                gui.setItem(31, new ItemBuilder(Material.IRON_DOOR)
                        .name("&c&lLeave Party")
                        .lore(
                                "&7",
                                "&c  Click to leave the party.",
                                "&7"
                        )
                        .build());
            }
        }

        // Close button
        gui.setItem(gui.getSize() - 5, new ItemBuilder(Material.BARRIER)
                .name("&c&lClose")
                .lore("&7Click to close")
                .build());

        // Fill empty slots
        for (int i = 0; i < gui.getSize(); i++) {
            if (gui.getItem(i) == null) {
                gui.setItem(i, new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE)
                        .name("&7")
                        .build());
            }
        }

        player.openInventory(gui);
    }

    private String getPlayerName(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        return player != null ? player.getName() : "Offline";
    }
}

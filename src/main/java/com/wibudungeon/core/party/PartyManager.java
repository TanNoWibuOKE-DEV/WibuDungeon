package com.wibudungeon.core.party;

import com.wibudungeon.core.config.ConfigManager;
import com.wibudungeon.core.util.MessageUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * Manages all active parties in the plugin.
 */
public class PartyManager {

    private final ConfigManager configManager;
    private final Map<UUID, Party> playerPartyMap = new HashMap<>();
    private final List<Party> parties = new ArrayList<>();

    public PartyManager(ConfigManager configManager) {
        this.configManager = configManager;
    }

    public Party createParty(Player owner) {
        if (playerPartyMap.containsKey(owner.getUniqueId())) {
            MessageUtil.send(owner, configManager.getMessage("party.already-in-party"));
            return null;
        }
        Party party = new Party(owner.getUniqueId(), configManager.getMaxPartySize());
        playerPartyMap.put(owner.getUniqueId(), party);
        parties.add(party);
        MessageUtil.send(owner, configManager.getMessage("party.created"));
        return party;
    }

    public void disbandParty(Player player) {
        Party party = playerPartyMap.get(player.getUniqueId());
        if (party == null) {
            MessageUtil.send(player, configManager.getMessage("party.not-in-party"));
            return;
        }
        if (!party.isOwner(player.getUniqueId())) {
            MessageUtil.send(player, configManager.getMessage("party.not-owner"));
            return;
        }
        for (UUID memberId : party.getMembers()) {
            Player member = Bukkit.getPlayer(memberId);
            if (member != null) {
                MessageUtil.send(member, configManager.getMessage("party.disbanded"));
            }
            playerPartyMap.remove(memberId);
        }
        parties.remove(party);
    }

    public void invitePlayer(Player sender, Player target) {
        Party party = playerPartyMap.get(sender.getUniqueId());
        if (party == null) {
            MessageUtil.send(sender, configManager.getMessage("party.not-in-party"));
            return;
        }
        if (!party.isOwner(sender.getUniqueId())) {
            MessageUtil.send(sender, configManager.getMessage("party.not-owner"));
            return;
        }
        if (party.isFull()) {
            MessageUtil.send(sender, configManager.getMessage("party.full"));
            return;
        }
        if (playerPartyMap.containsKey(target.getUniqueId())) {
            MessageUtil.send(sender, configManager.getMessage("party.already-in-party"));
            return;
        }
        party.invite(target.getUniqueId());
        MessageUtil.send(sender, configManager.getMessage("party.invited"), "%player%", target.getName());

        String msg = configManager.getRawMessage("party.received-invite").replace("%player%", sender.getName());
        Component inviteMessage = MessageUtil.colorize(configManager.getPrefix() + msg)
                .append(Component.text(" "))
                .append(MessageUtil.colorize("&a[ACCEPT]")
                        .clickEvent(ClickEvent.runCommand("/wd party accept " + sender.getName()))
                        .hoverEvent(HoverEvent.showText(MessageUtil.colorize("&aClick to accept"))))
                .append(Component.text(" "))
                .append(MessageUtil.colorize("&c[DENY]")
                        .clickEvent(ClickEvent.runCommand("/wd party deny " + sender.getName()))
                        .hoverEvent(HoverEvent.showText(MessageUtil.colorize("&cClick to deny"))));
        target.sendMessage(inviteMessage);
    }

    public void acceptInvite(Player player, Player inviter) {
        Party party = playerPartyMap.get(inviter.getUniqueId());
        if (party == null) {
            MessageUtil.send(player, configManager.getMessage("party.invite-expired"));
            return;
        }
        long timeout = configManager.getInviteTimeout() * 1000L;
        if (!party.hasPendingInvite(player.getUniqueId(), timeout)) {
            MessageUtil.send(player, configManager.getMessage("party.invite-expired"));
            return;
        }
        if (playerPartyMap.containsKey(player.getUniqueId())) {
            MessageUtil.send(player, configManager.getMessage("party.already-in-party"));
            return;
        }
        if (!party.addMember(player.getUniqueId())) {
            MessageUtil.send(player, configManager.getMessage("party.full"));
            return;
        }
        playerPartyMap.put(player.getUniqueId(), party);
        MessageUtil.send(player, configManager.getMessage("party.invite-accepted"));
        for (UUID memberId : party.getMembers()) {
            Player member = Bukkit.getPlayer(memberId);
            if (member != null && !member.equals(player)) {
                MessageUtil.send(member, configManager.getMessage("party.joined"), "%player%", player.getName());
            }
        }
    }

    public void denyInvite(Player player, Player inviter) {
        Party party = playerPartyMap.get(inviter.getUniqueId());
        if (party == null) return;
        party.removeInvite(player.getUniqueId());
        MessageUtil.send(player, configManager.getMessage("party.invite-denied"));
    }

    public void leaveParty(Player player) {
        Party party = playerPartyMap.get(player.getUniqueId());
        if (party == null) {
            MessageUtil.send(player, configManager.getMessage("party.not-in-party"));
            return;
        }
        if (party.isOwner(player.getUniqueId())) {
            disbandParty(player);
            return;
        }
        party.removeMember(player.getUniqueId());
        playerPartyMap.remove(player.getUniqueId());
        for (UUID memberId : party.getMembers()) {
            Player member = Bukkit.getPlayer(memberId);
            if (member != null) {
                MessageUtil.send(member, configManager.getMessage("party.left"), "%player%", player.getName());
            }
        }
    }

    public Party getParty(UUID playerId) {
        return playerPartyMap.get(playerId);
    }

    public boolean isInParty(UUID playerId) {
        return playerPartyMap.containsKey(playerId);
    }

    public void removeFromTracking(UUID playerId) {
        Party party = playerPartyMap.remove(playerId);
        if (party != null && party.getSize() <= 1) {
            parties.remove(party);
        }
    }

    public void cleanupParty(Party party) {
        for (UUID memberId : party.getMembers()) {
            playerPartyMap.remove(memberId);
        }
        parties.remove(party);
    }
}

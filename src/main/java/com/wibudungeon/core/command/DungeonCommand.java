package com.wibudungeon.core.command;

import com.wibudungeon.core.config.ConfigManager;
import com.wibudungeon.core.dungeon.Dungeon;
import com.wibudungeon.core.dungeon.DungeonManager;
import com.wibudungeon.core.dungeon.SetupManager;
import com.wibudungeon.core.gui.AdminGUI;
import com.wibudungeon.core.gui.PartyGUI;
import com.wibudungeon.core.mob.MobSpawner;
import com.wibudungeon.core.party.PartyManager;
import com.wibudungeon.core.portal.TrackingManager;
import com.wibudungeon.core.util.LocationUtil;
import com.wibudungeon.core.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;

/**
 * /wd command handler for v1.0.2.
 * Includes: leave, track, untrack commands.
 */
public class DungeonCommand implements CommandExecutor, TabCompleter {

    private final ConfigManager configManager;
    private final DungeonManager dungeonManager;
    private final PartyManager partyManager;
    private final MobSpawner mobSpawner;
    private final SetupManager setupManager;
    private final AdminGUI adminGUI;
    private final PartyGUI partyGUI;
    private TrackingManager trackingManager;

    public DungeonCommand(ConfigManager configManager, DungeonManager dungeonManager,
                          PartyManager partyManager, MobSpawner mobSpawner,
                          SetupManager setupManager, AdminGUI adminGUI, PartyGUI partyGUI) {
        this.configManager = configManager;
        this.dungeonManager = dungeonManager;
        this.partyManager = partyManager;
        this.mobSpawner = mobSpawner;
        this.setupManager = setupManager;
        this.adminGUI = adminGUI;
        this.partyGUI = partyGUI;
    }

    public void setTrackingManager(TrackingManager tm) { this.trackingManager = tm; }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) { sendHelp(sender); return true; }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players!");
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "help" -> sendHelp(sender);
            case "setup" -> handleSetup(player, args);
            case "create" -> handleCreate(player, args);
            case "delete" -> handleDelete(player, args);
            case "info" -> handleInfo(player, args);
            case "toggle" -> handleToggle(player, args);
            case "tp" -> handleTp(player, args);
            case "list" -> handleList(player);
            case "admin" -> handleAdmin(player);
            case "forcestop" -> handleForceStop(player);
            case "reload" -> handleReload(player);
            case "party" -> handleParty(player, args);
            case "spawnportal" -> handleSpawnPortal(player, args);
            case "leave" -> handleLeave(player);
            case "track" -> handleTrack(player, args);
            case "untrack" -> handleUntrack(player);
            default -> msg(player, "&cUnknown command. Use &e/wd help");
        }
        return true;
    }

    private void sendHelp(CommandSender s) {
        s.sendMessage(MessageUtil.colorize("&6&l━━━━━━ WibuDungeon v1.0.6 ━━━━━━"));
        s.sendMessage(MessageUtil.colorize("&a&l✦ Dungeon Setup:"));
        s.sendMessage(MessageUtil.colorize("&a /wd setup <id> &7- Interactive setup mode"));
        s.sendMessage(MessageUtil.colorize("&a /wd create <id> &7- Create empty dungeon"));
        s.sendMessage(MessageUtil.colorize("&a /wd delete <id> &7- Delete dungeon"));
        s.sendMessage(MessageUtil.colorize(""));
        s.sendMessage(MessageUtil.colorize("&e&l✦ Management:"));
        s.sendMessage(MessageUtil.colorize("&e /wd info <id> &7- Show dungeon details"));
        s.sendMessage(MessageUtil.colorize("&e /wd toggle <id> &7- Enable/disable dungeon"));
        s.sendMessage(MessageUtil.colorize("&e /wd tp <id> &7- Teleport to dungeon"));
        s.sendMessage(MessageUtil.colorize("&e /wd list &7- List all dungeons"));
        s.sendMessage(MessageUtil.colorize("&e /wd admin &7- Open admin GUI"));
        s.sendMessage(MessageUtil.colorize("&e /wd spawnportal <id> &7- Force spawn portal"));
        s.sendMessage(MessageUtil.colorize("&e /wd forcestop &7- Stop all instances"));
        s.sendMessage(MessageUtil.colorize("&e /wd reload &7- Reload configs"));
        s.sendMessage(MessageUtil.colorize(""));
        s.sendMessage(MessageUtil.colorize("&b&l✦ Player:"));
        s.sendMessage(MessageUtil.colorize("&b /wd leave &7- Leave current dungeon"));
        s.sendMessage(MessageUtil.colorize("&b /wd untrack &7- Stop tracking portal"));
        s.sendMessage(MessageUtil.colorize("&b /wd party &7- Open party GUI"));
        s.sendMessage(MessageUtil.colorize("&b /wd party invite/accept/deny/leave <player>"));
        s.sendMessage(MessageUtil.colorize("&6&l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
    }

    private boolean requireAdmin(Player p) {
        if (!p.hasPermission("wibudungeon.admin")) { msg(p, "&cNo permission!"); return false; }
        return true;
    }

    private void msg(Player p, String m) { MessageUtil.send(p, configManager.getPrefix() + m); }

    // ===== LEAVE COMMAND =====

    private void handleLeave(Player p) {
        if (!dungeonManager.isInDungeon(p.getUniqueId())) {
            msg(p, "&cYou are not in a dungeon!");
            return;
        }
        dungeonManager.leaveDungeon(p);
        msg(p, "&aYou left the dungeon.");
    }

    // ===== TRACKING COMMANDS =====

    private void handleTrack(Player p, String[] args) {
        if (trackingManager == null) { msg(p, "&cTracking not available!"); return; }
        if (args.length < 2) { msg(p, "&cUsage: /wd track <portalId>"); return; }
        try {
            UUID portalId = UUID.fromString(args[1]);
            trackingManager.startTracking(p, portalId);
        } catch (IllegalArgumentException e) {
            msg(p, "&cInvalid portal ID!");
        }
    }

    private void handleUntrack(Player p) {
        if (trackingManager == null) { msg(p, "&cTracking not available!"); return; }
        if (trackingManager.isTracking(p.getUniqueId())) {
            trackingManager.stopTracking(p.getUniqueId());
            msg(p, "&ePortal tracking stopped.");
        } else {
            msg(p, "&7You are not tracking any portal.");
        }
    }

    // ===== DUNGEON COMMANDS =====

    private void handleSetup(Player p, String[] args) {
        if (!requireAdmin(p)) return;
        if (args.length < 2) { msg(p, "&cUsage: /wd setup <dungeon_id>"); return; }
        setupManager.enterSetup(p, args[1].toLowerCase());
    }

    private void handleCreate(Player p, String[] args) {
        if (!requireAdmin(p)) return;
        if (args.length < 2) { msg(p, "&cUsage: /wd create <id>"); return; }
        String id = args[1].toLowerCase();
        if (configManager.getDungeon(id) != null) { msg(p, "&cDungeon &e" + id + " &calready exists!"); return; }
        Dungeon d = new Dungeon(id);
        d.setName("&e" + id);
        d.setWorld(p.getWorld().getName());
        d.setPos1(p.getLocation());
        d.setPos2(p.getLocation());
        d.setSpawnPoint(p.getLocation());
        configManager.saveDungeon(d);
        msg(p, "&aDungeon &e" + id + " &acreated! Use &e/wd setup " + id + " &ato configure.");
    }

    private void handleDelete(Player p, String[] args) {
        if (!requireAdmin(p)) return;
        if (args.length < 2) { msg(p, "&cUsage: /wd delete <id>"); return; }
        String id = args[1].toLowerCase();
        if (configManager.getDungeon(id) == null) { msg(p, "&cDungeon not found!"); return; }
        configManager.deleteDungeon(id);
        msg(p, "&cDungeon &e" + id + " &cdeleted.");
    }

    private void handleInfo(Player p, String[] args) {
        if (!requireAdmin(p)) return;
        if (args.length < 2) { msg(p, "&cUsage: /wd info <id>"); return; }
        Dungeon d = configManager.getDungeon(args[1].toLowerCase());
        if (d == null) { msg(p, "&cDungeon not found!"); return; }
        p.sendMessage(MessageUtil.colorize("&6&l━━━ Dungeon: " + d.getId() + " ━━━"));
        p.sendMessage(MessageUtil.colorize("&e Name: &f" + d.getName()));
        p.sendMessage(MessageUtil.colorize("&e Enabled: " + (d.isEnabled() ? "&a✔" : "&c✘")));
        p.sendMessage(MessageUtil.colorize("&e World: &f" + d.getWorld()));
        p.sendMessage(MessageUtil.colorize("&e Pos1: &f" + LocationUtil.format(d.getPos1())));
        p.sendMessage(MessageUtil.colorize("&e Pos2: &f" + LocationUtil.format(d.getPos2())));
        p.sendMessage(MessageUtil.colorize("&e Spawn: &f" + LocationUtil.format(d.getSpawnPoint())));
        p.sendMessage(MessageUtil.colorize("&e Mob Spawns: &f" + d.getMobSpawns().size()));
        p.sendMessage(MessageUtil.colorize("&e Wave Set: &f" + d.getWaveSet()));
        p.sendMessage(MessageUtil.colorize("&e Players: &f" + d.getMinPlayers() + "-" + d.getMaxPlayers()));
        p.sendMessage(MessageUtil.colorize("&e Active: &f" + dungeonManager.getActiveInstanceCount(d.getId())));
        p.sendMessage(MessageUtil.colorize("&e Valid: " + (d.isValid() ? "&a✔" : "&c✘ (missing config)")));
        boolean mm = mobSpawner.getMythicHook().isEnabled();
        p.sendMessage(MessageUtil.colorize("&e MythicMobs: " + (mm ? "&aConnected" : "&7Not installed")));
    }

    private void handleToggle(Player p, String[] args) {
        if (!requireAdmin(p)) return;
        if (args.length < 2) { msg(p, "&cUsage: /wd toggle <id>"); return; }
        Dungeon d = configManager.getDungeon(args[1].toLowerCase());
        if (d == null) { msg(p, "&cDungeon not found!"); return; }
        d.setEnabled(!d.isEnabled());
        configManager.saveDungeon(d);
        msg(p, "&eDungeon " + d.getId() + " is now " + (d.isEnabled() ? "&aenabled" : "&cdisabled"));
    }

    private void handleTp(Player p, String[] args) {
        if (!requireAdmin(p)) return;
        if (args.length < 2) { msg(p, "&cUsage: /wd tp <id>"); return; }
        Dungeon d = configManager.getDungeon(args[1].toLowerCase());
        if (d == null || d.getSpawnPoint() == null) { msg(p, "&cDungeon not found or no spawn!"); return; }
        p.teleport(d.getSpawnPoint());
        msg(p, "&aTeleported to dungeon &e" + d.getId());
    }

    private void handleList(Player p) {
        p.sendMessage(MessageUtil.colorize("&6&l━━━━━━ Dungeons ━━━━━━"));
        if (configManager.getDungeons().isEmpty()) {
            p.sendMessage(MessageUtil.colorize("&7  No dungeons created. Use &e/wd create <id>"));
        }
        for (Dungeon d : configManager.getDungeons().values()) {
            String s = d.isEnabled() ? "&a✔" : "&c✘";
            p.sendMessage(MessageUtil.colorize("&e  " + d.getId() + " " + s
                    + " &7(" + dungeonManager.getActiveInstanceCount(d.getId()) + " active)"));
        }
        p.sendMessage(MessageUtil.colorize("&6&l━━━━━━━━━━━━━━━━━━━━"));
    }

    private void handleAdmin(Player p) {
        if (!requireAdmin(p)) return;
        adminGUI.openMain(p);
    }

    private void handleSpawnPortal(Player p, String[] args) {
        if (!requireAdmin(p)) return;
        if (args.length < 2) { msg(p, "&cUsage: /wd spawnportal <dungeon_id>"); return; }
        String id = args[1].toLowerCase();
        Dungeon d = configManager.getDungeon(id);
        if (d == null || !d.isValid()) { msg(p, "&cDungeon not found or not valid!"); return; }
        com.wibudungeon.core.portal.PortalManager pm =
                ((com.wibudungeon.core.WibuDungeon) Bukkit.getPluginManager().getPlugin("WibuDungeon")).getPortalManager();
        com.wibudungeon.core.portal.DungeonPortal portal = pm.spawnPortal(p.getLocation(), id);
        if (portal != null) {
            msg(p, "&aPortal for &e" + id + " &aspawned at your location!");
        } else {
            msg(p, "&cFailed to spawn portal!");
        }
    }

    private void handleForceStop(Player p) {
        if (!requireAdmin(p)) return;
        dungeonManager.cleanupAll();
        msg(p, "&cAll dungeon instances force stopped!");
    }

    private void handleReload(Player p) {
        if (!requireAdmin(p)) return;
        configManager.loadAll();
        msg(p, "&aConfiguration reloaded!");
    }

    private void handleParty(Player p, String[] args) {
        if (args.length < 2) { partyGUI.open(p); return; }
        switch (args[1].toLowerCase()) {
            case "create" -> partyManager.createParty(p);
            case "invite" -> {
                if (args.length < 3) { msg(p, "&cUsage: /wd party invite <player>"); return; }
                Player t = Bukkit.getPlayer(args[2]);
                if (t == null) { msg(p, "&cPlayer not found!"); return; }
                partyManager.invitePlayer(p, t);
            }
            case "accept" -> {
                if (args.length < 3) return;
                Player inv = Bukkit.getPlayer(args[2]);
                if (inv == null) { msg(p, "&cPlayer not found!"); return; }
                partyManager.acceptInvite(p, inv);
            }
            case "deny" -> {
                if (args.length < 3) return;
                Player inv = Bukkit.getPlayer(args[2]);
                if (inv != null) partyManager.denyInvite(p, inv);
            }
            case "leave" -> partyManager.leaveParty(p);
            case "disband" -> partyManager.disbandParty(p);
            default -> partyGUI.open(p);
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        List<String> c = new ArrayList<>();
        if (args.length == 1) {
            List<String> subs = Arrays.asList("help", "setup", "create", "delete", "info",
                    "toggle", "tp", "list", "admin", "spawnportal", "forcestop", "reload",
                    "party", "leave", "untrack");
            c = subs.stream().filter(s -> s.startsWith(args[0].toLowerCase())).collect(Collectors.toList());
        } else if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (Set.of("setup", "delete", "info", "toggle", "tp", "spawnportal").contains(sub)) {
                c = configManager.getDungeons().keySet().stream()
                        .filter(s -> s.startsWith(args[1].toLowerCase())).collect(Collectors.toList());
            } else if (sub.equals("party")) {
                c = Arrays.asList("create", "invite", "accept", "deny", "leave", "disband").stream()
                        .filter(s -> s.startsWith(args[1].toLowerCase())).collect(Collectors.toList());
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("party")
                && Set.of("invite", "accept", "deny").contains(args[1].toLowerCase())) {
            c = Bukkit.getOnlinePlayers().stream().map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(args[2].toLowerCase())).collect(Collectors.toList());
        }
        return c;
    }
}

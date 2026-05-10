package com.wibudungeon.core.command;

import com.wibudungeon.core.config.ConfigManager;
import com.wibudungeon.core.dungeon.Dungeon;
import com.wibudungeon.core.dungeon.DungeonType;
import com.wibudungeon.core.dungeon.DungeonManager;
import com.wibudungeon.core.dungeon.SetupManager;
import com.wibudungeon.core.gui.AdminGUI;
import com.wibudungeon.core.gui.PartyGUI;
import com.wibudungeon.core.mob.MobSpawner;
import com.wibudungeon.core.party.PartyManager;
import com.wibudungeon.core.portal.TrackingManager;
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
 * /wd command handler — v1.0.8 simplified command set.
 *
 * Core commands (shown in help + tab-complete):
 *   /wd create [static|dynamic] <id>
 *   /wd delete <id>
 *   /wd setup <id>
 *   /wd reload
 *   /wd list
 *   /wd leave
 *
 * Hidden commands (still functional, for admin GUI and power-users):
 *   info, toggle, tp, admin, spawnportal, forcestop, party, track, untrack, settype, setentry
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
    private com.wibudungeon.core.portal.PortalManager portalManager;
    private com.wibudungeon.core.portal.StaticEntryManager staticEntryManager;

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
    public void setPortalManager(com.wibudungeon.core.portal.PortalManager pm) { this.portalManager = pm; }
    public void setStaticEntryManager(com.wibudungeon.core.portal.StaticEntryManager sem) { this.staticEntryManager = sem; }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) { sendHelp(sender); return true; }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players!");
            return true;
        }
        switch (args[0].toLowerCase()) {
            // ===== CORE =====
            case "help"        -> sendHelp(sender);
            case "create"      -> handleCreate(player, args);
            case "delete"      -> handleDelete(player, args);
            case "setup"       -> handleSetup(player, args);
            case "reload"      -> handleReload(player);
            case "list"        -> handleList(player);
            case "leave"       -> handleLeave(player);
            // ===== INTERNAL (hidden from help) =====
            case "info"        -> handleInfo(player, args);
            case "toggle"      -> handleToggle(player, args);
            case "tp"          -> handleTp(player, args);
            case "admin"       -> handleAdmin(player);
            case "spawnportal" -> handleSpawnPortal(player, args);
            case "forcestop"   -> handleForceStop(player);
            case "party"       -> handleParty(player, args);
            case "track"       -> handleTrack(player, args);
            case "untrack"     -> handleUntrack(player);
            case "settype"     -> handleSetType(player, args);
            case "setentry"    -> handleSetEntry(player, args);
            default            -> msg(player, "&cUnknown command. Use &e/wd help");
        }
        return true;
    }

    // ===== HELP =====

    private void sendHelp(CommandSender s) {
        s.sendMessage(MessageUtil.colorize("&8&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
        s.sendMessage(MessageUtil.colorize("  &6&lWibuDungeon &ev1.0.9"));
        s.sendMessage(MessageUtil.colorize("&8&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
        s.sendMessage(MessageUtil.colorize("&a&l✦ Setup:"));
        s.sendMessage(MessageUtil.colorize("  &a/wd create &7[static|dynamic] <id>"));
        s.sendMessage(MessageUtil.colorize("  &a/wd delete &7<id>"));
        s.sendMessage(MessageUtil.colorize("  &a/wd setup &7<id> &8— Enter interactive setup mode"));
        s.sendMessage(MessageUtil.colorize(""));
        s.sendMessage(MessageUtil.colorize("&e&l✦ Info:"));
        s.sendMessage(MessageUtil.colorize("  &e/wd list &8— List all dungeons"));
        s.sendMessage(MessageUtil.colorize("  &e/wd reload &8— Reload configs"));
        s.sendMessage(MessageUtil.colorize(""));
        s.sendMessage(MessageUtil.colorize("&b&l✦ Player:"));
        s.sendMessage(MessageUtil.colorize("  &b/wd leave &8— Leave current dungeon"));
        s.sendMessage(MessageUtil.colorize("&8&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
    }

    private boolean requireAdmin(Player p) {
        if (!p.hasPermission("wibudungeon.admin")) { msg(p, "&cNo permission!"); return false; }
        return true;
    }

    private void msg(Player p, String m) { MessageUtil.send(p, configManager.getPrefix() + m); }

    // ===== CORE HANDLERS =====

    private void handleLeave(Player p) {
        if (!dungeonManager.isInDungeon(p.getUniqueId())) {
            msg(p, "&cYou are not in a dungeon!");
            return;
        }
        dungeonManager.leaveDungeon(p);
        msg(p, "&aYou left the dungeon.");
    }

    private void handleSetup(Player p, String[] args) {
        if (!requireAdmin(p)) return;
        if (args.length < 2) { msg(p, "&cUsage: /wd setup <dungeon_id>"); return; }
        setupManager.enterSetup(p, args[1].toLowerCase());
    }

    private void handleCreate(Player p, String[] args) {
        if (!requireAdmin(p)) return;
        if (args.length < 2) { msg(p, "&cUsage: /wd create [static|dynamic] <id>"); return; }

        DungeonType type = DungeonType.DYNAMIC;
        String id;
        if (args.length >= 3 && (args[1].equalsIgnoreCase("static") || args[1].equalsIgnoreCase("dynamic"))) {
            type = DungeonType.fromString(args[1]);
            id = args[2].toLowerCase();
        } else {
            id = args[1].toLowerCase();
        }

        if (configManager.getDungeon(id) != null) {
            msg(p, "&cDungeon &e" + id + " &calready exists!");
            return;
        }

        // v1.0.9: Read region from WorldEdit/FAWE selection
        if (!com.wibudungeon.core.util.WorldEditHook.isAvailable()) {
            msg(p, "&cWorldEdit or FastAsyncWorldEdit is required!");
            msg(p, "&7Install WorldEdit/FAWE and select a region with the wooden axe.");
            return;
        }

        org.bukkit.Location[] selection = com.wibudungeon.core.util.WorldEditHook.getSelection(p);
        if (selection == null) {
            msg(p, "&c⚠ No WorldEdit selection found!");
            msg(p, "&7Use the &ewooden axe &7to select pos1 (left-click) and pos2 (right-click) first.");
            return;
        }

        Dungeon d = new Dungeon(id);
        d.setName("&e" + id);
        d.setType(type);
        d.setWorld(p.getWorld().getName());
        d.setPos1(selection[0]);
        d.setPos2(selection[1]);
        d.setSpawnPoint(p.getLocation());
        if (type == DungeonType.STATIC) d.setEntryPoint(p.getLocation());
        configManager.saveDungeon(d);
        msg(p, "&aDungeon &e" + id + " &a[" + type.name() + "] created with WorldEdit region!");
        msg(p, "&7Use &e/wd setup " + id + " &7to configure waves and spawns.");
    }

    private void handleDelete(Player p, String[] args) {
        if (!requireAdmin(p)) return;
        if (args.length < 2) { msg(p, "&cUsage: /wd delete <id>"); return; }
        String id = args[1].toLowerCase();
        if (configManager.getDungeon(id) == null) { msg(p, "&cDungeon not found!"); return; }
        configManager.deleteDungeon(id);
        msg(p, "&cDungeon &e" + id + " &cdeleted.");
    }

    private void handleReload(Player p) {
        if (!requireAdmin(p)) return;
        configManager.loadAll();
        if (staticEntryManager != null) staticEntryManager.reload();
        msg(p, "&aConfiguration reloaded!");
    }

    private void handleList(Player p) {
        p.sendMessage(MessageUtil.colorize("&8&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
        p.sendMessage(MessageUtil.colorize("  &6&lDungeons"));
        if (configManager.getDungeons().isEmpty()) {
            p.sendMessage(MessageUtil.colorize("  &7No dungeons. Use &e/wd create <id>"));
        }
        for (Dungeon d : configManager.getDungeons().values()) {
            String status  = d.isEnabled() ? "&a✔" : "&c✘";
            String typeTag = d.isStatic() ? "&d[STATIC]" : "&b[DYNAMIC]";
            String valid   = d.isValid()  ? "" : " &c[INVALID]";
            p.sendMessage(MessageUtil.colorize("  &e" + d.getId() + " " + typeTag + " " + status
                    + " &8(" + dungeonManager.getActiveInstanceCount(d.getId()) + " active)" + valid));
        }
        p.sendMessage(MessageUtil.colorize("&8&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
    }

    // ===== INTERNAL HANDLERS =====

    private void handleInfo(Player p, String[] args) {
        if (!requireAdmin(p)) return;
        if (args.length < 2) { msg(p, "&cUsage: /wd info <id>"); return; }
        Dungeon d = configManager.getDungeon(args[1].toLowerCase());
        if (d == null) { msg(p, "&cDungeon not found!"); return; }
        p.sendMessage(MessageUtil.colorize("&6&l━━━ Dungeon: " + d.getId() + " ━━━"));
        p.sendMessage(MessageUtil.colorize("&e Name: &f" + d.getName()));
        p.sendMessage(MessageUtil.colorize("&e Type: &f" + d.getType().name()));
        p.sendMessage(MessageUtil.colorize("&e Enabled: " + (d.isEnabled() ? "&a✔" : "&c✘")));
        p.sendMessage(MessageUtil.colorize("&e World: &f" + d.getWorld()));
        p.sendMessage(MessageUtil.colorize("&e Wave Set: &f" + d.getWaveSet()));
        p.sendMessage(MessageUtil.colorize("&e Players: &f" + d.getMinPlayers() + "-" + d.getMaxPlayers()));
        p.sendMessage(MessageUtil.colorize("&e Active: &f" + dungeonManager.getActiveInstanceCount(d.getId())));
        p.sendMessage(MessageUtil.colorize("&e Valid: " + (d.isValid() ? "&a✔" : "&c✘")));
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
        if (portalManager == null) { msg(p, "&cPortal manager not available!"); return; }
        com.wibudungeon.core.portal.DungeonPortal portal = portalManager.spawnPortal(p.getLocation(), id);
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

    private void handleSetType(Player p, String[] args) {
        if (!requireAdmin(p)) return;
        if (args.length < 3) { msg(p, "&cUsage: /wd settype <id> static|dynamic"); return; }
        String id = args[1].toLowerCase();
        Dungeon d = configManager.getDungeon(id);
        if (d == null) { msg(p, "&cDungeon not found!"); return; }
        DungeonType newType = DungeonType.fromString(args[2]);
        DungeonType oldType = d.getType();
        d.setType(newType);
        configManager.saveDungeon(d);
        if (oldType != newType && staticEntryManager != null) {
            if (newType == DungeonType.STATIC && d.getEntryPoint() != null && d.isEnabled()) {
                staticEntryManager.spawnEntry(d);
            } else {
                staticEntryManager.removeEntry(id);
            }
        }
        msg(p, "&eDungeon &f" + id + " &etype changed to &f" + newType.name());
    }

    private void handleSetEntry(Player p, String[] args) {
        if (!requireAdmin(p)) return;
        if (args.length < 2) { msg(p, "&cUsage: /wd setentry <id>"); return; }
        String id = args[1].toLowerCase();
        Dungeon d = configManager.getDungeon(id);
        if (d == null) { msg(p, "&cDungeon not found!"); return; }
        d.setEntryPoint(p.getLocation());
        configManager.saveDungeon(d);
        if (d.isStatic() && d.isEnabled() && staticEntryManager != null) {
            staticEntryManager.spawnEntry(d);
        }
        msg(p, "&aEntry point for &e" + id + " &aset at your location!");
    }

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

    private void handleParty(Player p, String[] args) {
        if (args.length < 2) { partyGUI.open(p); return; }
        switch (args[1].toLowerCase()) {
            case "create"  -> partyManager.createParty(p);
            case "invite"  -> {
                if (args.length < 3) { msg(p, "&cUsage: /wd party invite <player>"); return; }
                Player t = Bukkit.getPlayer(args[2]);
                if (t == null) { msg(p, "&cPlayer not found!"); return; }
                partyManager.invitePlayer(p, t);
            }
            case "accept"  -> {
                if (args.length < 3) return;
                Player inv = Bukkit.getPlayer(args[2]);
                if (inv == null) { msg(p, "&cPlayer not found!"); return; }
                partyManager.acceptInvite(p, inv);
            }
            case "deny"    -> {
                if (args.length < 3) return;
                Player inv = Bukkit.getPlayer(args[2]);
                if (inv != null) partyManager.denyInvite(p, inv);
            }
            case "leave"   -> partyManager.leaveParty(p);
            case "disband" -> partyManager.disbandParty(p);
            default        -> partyGUI.open(p);
        }
    }

    // ===== TAB COMPLETE =====

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        List<String> c = new ArrayList<>();
        if (args.length == 1) {
            // Only expose core commands in tab-complete
            List<String> subs = Arrays.asList("create", "delete", "setup", "reload", "list", "leave", "help", "spawnportal");
            c = subs.stream().filter(s -> s.startsWith(args[0].toLowerCase())).collect(Collectors.toList());
        } else if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (Set.of("setup", "delete", "info", "toggle", "tp", "spawnportal", "settype", "setentry").contains(sub)) {
                c = configManager.getDungeons().keySet().stream()
                        .filter(s -> s.startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            } else if (sub.equals("create")) {
                c = new ArrayList<>(Arrays.asList("static", "dynamic"));
                c = c.stream().filter(s -> s.startsWith(args[1].toLowerCase())).collect(Collectors.toList());
            }
        }
        return c;
    }
}

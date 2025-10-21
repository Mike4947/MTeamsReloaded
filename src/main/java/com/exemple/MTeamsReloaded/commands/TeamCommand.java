package com.exemple.MTeamsReloaded.commands;

import com.exemple.MTeamsReloaded.MTeamsReloaded;
import com.exemple.MTeamsReloaded.gui.TeamGui;
import com.exemple.MTeamsReloaded.storage.TeamManager;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class TeamCommand implements CommandExecutor, TabCompleter {

    private final MTeamsReloaded plugin;
    private final TeamManager manager;
    private final TeamGui gui;

    public TeamCommand(MTeamsReloaded plugin, TeamManager manager, TeamGui gui) {
        this.plugin = plugin;
        this.manager = manager;
        this.gui = gui;
    }

    private void sendUsage(CommandSender s) {
        s.sendMessage("§6/team §e- open teams GUI (permission: mteams.gui)");
        s.sendMessage("§6/team create <name> §e- create a team (permission: mteams.create)");
        s.sendMessage("§6/team manage §e- manage teams you lead (permission: mteams.manage)");
        s.sendMessage("§6/team invite <player> §e- invite a player (permission: mteams.invite)");
        s.sendMessage("§6/team accept <team> §e- accept invite");
        s.sendMessage("§6/team deny <team> §e- deny invite");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players may use team commands.");
            return true;
        }
        Player p = (Player) sender;

        if (args.length == 0) {
            if (!p.hasPermission("mteams.gui")) {
                p.sendMessage("§cYou don't have permission to open the teams GUI.");
                return true;
            }
            gui.openMainMenu(p);
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "create":
                if (!p.hasPermission("mteams.create")) {
                    p.sendMessage("§cYou don't have permission to create teams.");
                    return true;
                }
                if (args.length < 2) {
                    p.sendMessage("§eUsage: /team create <name>");
                    return true;
                }
                String name = args[1];
                if (manager.teamExists(name)) {
                    p.sendMessage("§cA team with that name already exists.");
                    return true;
                }
                manager.createTeam(name, p);
                p.sendMessage("§aTeam '" + name + "' created. Use /team manage to see it or open the GUI.");
                return true;

            case "manage":
                if (!p.hasPermission("mteams.manage")) {
                    p.sendMessage("§cYou don't have permission to manage teams.");
                    return true;
                }
                gui.openManageMenu(p);
                return true;

            case "invite":
                if (!p.hasPermission("mteams.invite")) {
                    p.sendMessage("§cYou don't have permission to invite players.");
                    return true;
                }
                if (args.length < 2) {
                    p.sendMessage("§eUsage: /team invite <player>");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    p.sendMessage("§cPlayer not found or offline.");
                    return true;
                }
                // determine which team the inviter leads or is officer of
                List<String> led = manager.teamsLedBy(p);
                String chosenTeam = null;
                if (!led.isEmpty()) {
                    chosenTeam = led.get(0); // if player leads multiple, pick the first — could be improved
                } else {
                    // if inviter is officer of exactly one team, allow invite
                    for (String t : manager.listTeams()) {
                        if (manager.isOfficer(t, p.getUniqueId())) {
                            chosenTeam = t;
                            break;
                        }
                    }
                }
                if (chosenTeam == null) {
                    p.sendMessage("§cYou must be leader or officer of a team to invite players.");
                    return true;
                }
                if (manager.invitePlayer(chosenTeam, p.getUniqueId(), target.getUniqueId())) {
                    p.sendMessage("§aInvite sent to " + target.getName() + " for team " + chosenTeam);
                    target.sendMessage("§aYou have been invited to join team §6" + chosenTeam + "§a by §e" + p.getName());
                    target.sendMessage("§aUse §e/team accept " + chosenTeam + "§a to accept, or §e/team deny " + chosenTeam + "§a to deny.");
                } else {
                    p.sendMessage("§cCould not send invite (maybe they were already invited or are already a member).");
                }
                return true;

            case "accept":
                if (args.length < 2) {
                    p.sendMessage("§eUsage: /team accept <team>");
                    return true;
                }
                String teamToAccept = args[1];
                if (manager.acceptInvite(p, teamToAccept)) {
                    p.sendMessage("§aYou have joined team " + teamToAccept);
                    // notify leader if online
                    manager.getLeader(teamToAccept).ifPresent(uuid -> {
                        Player leader = Bukkit.getPlayer(uuid);
                        if (leader != null) leader.sendMessage("§a" + p.getName() + " has joined your team " + teamToAccept);
                    });
                } else {
                    p.sendMessage("§cNo invite found for team " + teamToAccept);
                }
                return true;

            case "deny":
                if (args.length < 2) {
                    p.sendMessage("§eUsage: /team deny <team>");
                    return true;
                }
                String teamToDeny = args[1];
                if (manager.denyInvite(p, teamToDeny)) {
                    p.sendMessage("§aInvite denied for team " + teamToDeny);
                } else {
                    p.sendMessage("§cNo invite found for team " + teamToDeny);
                }
                return true;

            default:
                sendUsage(p);
                return true;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> suggestions = new ArrayList<>();
        if (args.length == 1) {
            suggestions.add("create");
            suggestions.add("manage");
            suggestions.add("invite");
            suggestions.add("accept");
            suggestions.add("deny");
            suggestions.removeIf(s -> {
                if (s.equals("create") && !(sender.hasPermission("mteams.create") || sender.isOp())) return true;
                if (s.equals("manage") && !(sender.hasPermission("mteams.manage") || sender.isOp())) return true;
                if (s.equals("invite") && !(sender.hasPermission("mteams.invite") || sender.isOp())) return true;
                return false;
            });
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("invite")) {
                // suggest online player names
                for (Player p : Bukkit.getOnlinePlayers()) suggestions.add(p.getName());
            } else if (args[0].equalsIgnoreCase("accept") || args[0].equalsIgnoreCase("deny")) {
                if (sender instanceof Player) {
                    Player pl = (Player) sender;
                    List<String> invites = manager.getInvitesFor(pl.getUniqueId());
                    suggestions.addAll(invites);
                }
            }
        }
        return suggestions;
    }
}

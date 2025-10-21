package com.exemple.MTeamsReloaded.commands;

import com.exemple.MTeamsReloaded.MTeamsReloaded;
import com.exemple.MTeamsReloaded.storage.TeamManager;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class TrCommand implements CommandExecutor, TabCompleter {

    private final MTeamsReloaded plugin;
    private final TeamManager manager;

    public TrCommand(MTeamsReloaded plugin, TeamManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    private void sendHelp(CommandSender s) {
        s.sendMessage("§6=== MTeamsReloaded (tr) Help ===");
        s.sendMessage("§e/tr help §7- show this");
        s.sendMessage("§e/tr reload §7- reload plugin (permission: mteams.reload)");
        s.sendMessage("§e/tr admin list §7- list all teams (permission: mteams.admin)");
        s.sendMessage("§6==============================");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "help":
                sendHelp(sender);
                return true;

            case "reload":
                if (!sender.hasPermission("mteams.reload") && !sender.isOp()) {
                    sender.sendMessage("§cYou don't have permission to reload.");
                    return true;
                }
                plugin.reloadConfig();
                manager.load();
                sender.sendMessage("§aMTeamsReloaded reloaded.");
                return true;

            case "admin":
                if (!sender.hasPermission("mteams.admin") && !sender.isOp()) {
                    sender.sendMessage("§cYou don't have admin permission.");
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage("§eUsage: /tr admin list");
                    return true;
                }
                String a = args[1].toLowerCase();
                if (a.equals("list")) {
                    List<String> teams = manager.listTeams();
                    sender.sendMessage("§6Teams (" + teams.size() + "):");
                    for (String t : teams) {
                        sender.sendMessage(" - " + t);
                    }
                    return true;
                } else {
                    sender.sendMessage("§eUnknown admin subcommand. Use /tr admin list");
                    return true;
                }

            default:
                sendHelp(sender);
                return true;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            out.add("help");
            out.add("reload");
            out.add("admin");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("admin")) {
            out.add("list");
        }
        return out;
    }
}

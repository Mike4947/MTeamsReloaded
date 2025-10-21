package com.exemple.MTeamsReloaded.storage;

import com.exemple.MTeamsReloaded.MTeamsReloaded;
import org.bukkit.entity.Player;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class TeamManager {

    private final MTeamsReloaded plugin;
    private final File teamsFile;
    private FileConfiguration teamsConfig;

    public TeamManager(MTeamsReloaded plugin) {
        this.plugin = plugin;
        this.teamsFile = new File(plugin.getDataFolder(), "teams.yml");
    }

    public void load() {
        if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
        if (!teamsFile.exists()) {
            try {
                teamsFile.createNewFile();
                teamsConfig = YamlConfiguration.loadConfiguration(teamsFile);
                teamsConfig.set("teams", new HashMap<>());
                teamsConfig.save(teamsFile);
            } catch (IOException e) {
                plugin.getLogger().severe("Could not create teams.yml: " + e.getMessage());
            }
        }
        teamsConfig = YamlConfiguration.loadConfiguration(teamsFile);
    }

    public void save() {
        try {
            if (teamsConfig != null) teamsConfig.save(teamsFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save teams.yml: " + e.getMessage());
        }
    }

    // --- Basic team operations ---

    public boolean teamExists(String name) {
        return teamsConfig.contains("teams." + name.toLowerCase());
    }

    public boolean createTeam(String name, Player leader) {
        if (teamExists(name)) return false;
        String path = "teams." + name.toLowerCase();
        teamsConfig.set(path + ".name", name);
        teamsConfig.set(path + ".leader", leader.getUniqueId().toString());
        teamsConfig.set(path + ".members", Collections.singletonList(leader.getUniqueId().toString()));
        teamsConfig.set(path + ".officers", new ArrayList<String>()); // initially empty
        teamsConfig.set(path + ".invites", new ArrayList<String>());  // pending invites
        save();
        return true;
    }

    public boolean removeTeam(String name) {
        if (!teamExists(name)) return false;
        teamsConfig.set("teams." + name.toLowerCase(), null);
        save();
        return true;
    }

    // Members
    public boolean addMember(String name, Player player) {
        if (!teamExists(name)) return false;
        String path = "teams." + name.toLowerCase() + ".members";
        List<String> members = teamsConfig.getStringList(path);
        if (members.contains(player.getUniqueId().toString())) return false;
        members.add(player.getUniqueId().toString());
        teamsConfig.set(path, members);
        save();
        return true;
    }

    public boolean removeMember(String name, UUID playerUuid) {
        if (!teamExists(name)) return false;
        String path = "teams." + name.toLowerCase() + ".members";
        List<String> members = teamsConfig.getStringList(path);
        boolean removed = members.remove(playerUuid.toString());
        teamsConfig.set(path, members);
        // also remove from officers if present
        String offPath = "teams." + name.toLowerCase() + ".officers";
        List<String> officers = teamsConfig.getStringList(offPath);
        officers.remove(playerUuid.toString());
        teamsConfig.set(offPath, officers);
        save();
        return removed;
    }

    public Optional<UUID> getLeader(String name) {
        if (!teamExists(name)) return Optional.empty();
        String s = teamsConfig.getString("teams." + name.toLowerCase() + ".leader");
        if (s == null) return Optional.empty();
        try {
            return Optional.of(UUID.fromString(s));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public List<UUID> getMembers(String name) {
        if (!teamExists(name)) return Collections.emptyList();
        List<String> members = teamsConfig.getStringList("teams." + name.toLowerCase() + ".members");
        return members.stream().map(UUID::fromString).collect(Collectors.toList());
    }

    public List<String> listTeams() {
        if (!teamsConfig.contains("teams")) return Collections.emptyList();
        ConfigurationSection sec = teamsConfig.getConfigurationSection("teams");
        if (sec == null) return Collections.emptyList();
        Set<String> keys = sec.getKeys(false);
        List<String> out = new ArrayList<>();
        for (String k : keys) {
            String name = teamsConfig.getString("teams." + k + ".name", k);
            out.add(name);
        }
        return out;
    }

    public List<String> teamsLedBy(Player player) {
        UUID u = player.getUniqueId();
        List<String> led = new ArrayList<>();
        for (String t : listTeams()) {
            Optional<UUID> leader = getLeader(t);
            if (leader.isPresent() && leader.get().equals(u)) {
                led.add(t);
            }
        }
        return led;
    }

    // --- Officers management ---

    public boolean addOfficer(String team, UUID playerUuid) {
        if (!teamExists(team)) return false;
        String path = "teams." + team.toLowerCase() + ".officers";
        List<String> officers = teamsConfig.getStringList(path);
        if (officers.contains(playerUuid.toString())) return false;
        officers.add(playerUuid.toString());
        teamsConfig.set(path, officers);
        save();
        return true;
    }

    public boolean removeOfficer(String team, UUID playerUuid) {
        if (!teamExists(team)) return false;
        String path = "teams." + team.toLowerCase() + ".officers";
        List<String> officers = teamsConfig.getStringList(path);
        boolean removed = officers.remove(playerUuid.toString());
        teamsConfig.set(path, officers);
        save();
        return removed;
    }

    public boolean isOfficer(String team, UUID playerUuid) {
        if (!teamExists(team)) return false;
        List<String> officers = teamsConfig.getStringList("teams." + team.toLowerCase() + ".officers");
        return officers.contains(playerUuid.toString());
    }

    // Promote: set a member as officer (not leader)
    public boolean promoteToOfficer(String team, UUID playerUuid) {
        if (!teamExists(team)) return false;
        // must be member
        List<String> members = teamsConfig.getStringList("teams." + team.toLowerCase() + ".members");
        if (!members.contains(playerUuid.toString())) return false;
        return addOfficer(team, playerUuid);
    }

    public boolean demoteOfficer(String team, UUID playerUuid) {
        return removeOfficer(team, playerUuid);
    }

    // Kick member
    public boolean kickMember(String team, UUID playerUuid) {
        return removeMember(team, playerUuid);
    }

    // --- Invitation system ---

    // inviteeUuid is the player invited
    public boolean invitePlayer(String team, UUID inviterUuid, UUID inviteeUuid) {
        if (!teamExists(team)) return false;
        // only invite if invitee not already member
        List<String> members = teamsConfig.getStringList("teams." + team.toLowerCase() + ".members");
        if (members.contains(inviteeUuid.toString())) return false;
        String path = "teams." + team.toLowerCase() + ".invites";
        List<String> invites = teamsConfig.getStringList(path);
        String inviteKey = inviteeUuid.toString() + ":" + inviterUuid.toString(); // store inviter too
        // prevent duplicate invites from same inviter
        if (invites.stream().anyMatch(s -> s.startsWith(inviteeUuid.toString() + ":"))) {
            return false;
        }
        invites.add(inviteKey);
        teamsConfig.set(path, invites);
        save();
        return true;
    }

    // get list of team names that invited this player
    public List<String> getInvitesFor(UUID playerUuid) {
        List<String> out = new ArrayList<>();
        if (!teamsConfig.contains("teams")) return out;
        for (String team : listTeams()) {
            List<String> invites = teamsConfig.getStringList("teams." + team.toLowerCase() + ".invites");
            for (String entry : invites) {
                if (entry.startsWith(playerUuid.toString() + ":")) {
                    out.add(team);
                    break;
                }
            }
        }
        return out;
    }

    // accept invite: player becomes a member and invite removed
    public boolean acceptInvite(Player player, String team) {
        if (!teamExists(team)) return false;
        UUID u = player.getUniqueId();
        List<String> invites = teamsConfig.getStringList("teams." + team.toLowerCase() + ".invites");
        boolean found = false;
        String matched = null;
        for (String entry : invites) {
            if (entry.startsWith(u.toString() + ":")) {
                found = true;
                matched = entry;
                break;
            }
        }
        if (!found) return false;
        invites.remove(matched);
        teamsConfig.set("teams." + team.toLowerCase() + ".invites", invites);

        List<String> members = teamsConfig.getStringList("teams." + team.toLowerCase() + ".members");
        if (!members.contains(u.toString())) {
            members.add(u.toString());
            teamsConfig.set("teams." + team.toLowerCase() + ".members", members);
        }
        save();
        return true;
    }

    public boolean denyInvite(Player player, String team) {
        if (!teamExists(team)) return false;
        UUID u = player.getUniqueId();
        List<String> invites = teamsConfig.getStringList("teams." + team.toLowerCase() + ".invites");
        boolean removed = invites.removeIf(e -> e.startsWith(u.toString() + ":"));
        teamsConfig.set("teams." + team.toLowerCase() + ".invites", invites);
        if (removed) save();
        return removed;
    }

    // helper: get inviter for an invite (optional)
    public Optional<UUID> getInviterFor(String team, UUID invitee) {
        if (!teamExists(team)) return Optional.empty();
        List<String> invites = teamsConfig.getStringList("teams." + team.toLowerCase() + ".invites");
        for (String entry : invites) {
            if (entry.startsWith(invitee.toString() + ":")) {
                String[] parts = entry.split(":");
                if (parts.length >= 2) {
                    try {
                        return Optional.of(UUID.fromString(parts[1]));
                    } catch (IllegalArgumentException ignored) {}
                }
            }
        }
        return Optional.empty();
    }
}

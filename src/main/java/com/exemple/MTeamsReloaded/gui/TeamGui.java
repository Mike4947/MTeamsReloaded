package com.exemple.MTeamsReloaded.gui;

import com.exemple.MTeamsReloaded.MTeamsReloaded;
import com.exemple.MTeamsReloaded.storage.TeamManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.stream.Collectors;

/**
 * TeamGui - manages all team-related GUIs:
 *  - Main menu (create / manage)
 *  - Manage menu (teams the player leads)
 *  - Members menu (members of a chosen team)
 *  - Member action menu (promote/demote/kick)
 *
 * This implementation stores a per-player InventoryContext so we never rely on inventory titles.
 */
public class TeamGui implements Listener {

    private final MTeamsReloaded plugin;
    private final TeamManager manager;

    /**
     * Per-player inventory context.
     * Stored while the player has a plugin GUI open.
     */
    private final Map<UUID, InventoryContext> contexts = new HashMap<>();

    public TeamGui(MTeamsReloaded plugin, TeamManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    /* ----------------------------
       InventoryContext (small record)
       ---------------------------- */
    private static final class InventoryContext {
        public final ContextType type;
        public final String teamName; // nullable for menus that don't need a team
        public final UUID targetMember; // nullable for member action menu

        InventoryContext(ContextType type, String teamName, UUID targetMember) {
            this.type = type;
            this.teamName = teamName;
            this.targetMember = targetMember;
        }
    }

    private enum ContextType {
        MAIN,
        MANAGE,
        MEMBERS,
        MEMBER_ACTION
    }

    /* ----------------------------
       Helpers: create item helpers
       ---------------------------- */
    private ItemStack makeItem(Material mat, String name, List<String> lore) {
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore != null) meta.setLore(lore);
            it.setItemMeta(meta);
        }
        return it;
    }

    private ItemStack named(Material mat, String name) {
        return makeItem(mat, name, null);
    }

    /* ----------------------------
       Open GUIs
       ---------------------------- */

    /** Open the main menu (Create / Manage). */
    public void openMainMenu(Player p) {
        Inventory inv = Bukkit.createInventory(null, 27, ChatColor.GREEN + "Teams Menu");

        ItemStack create = makeItem(Material.NAME_TAG,
                ChatColor.YELLOW + "Create Team",
                List.of(ChatColor.GRAY + "Click to create a team", ChatColor.GRAY + "Use /team create <name>"));

        ItemStack manage = makeItem(Material.CHEST,
                ChatColor.AQUA + "Manage Teams",
                List.of(ChatColor.GRAY + "Click to manage teams you lead"));

        inv.setItem(11, create);
        inv.setItem(15, manage);

        contexts.put(p.getUniqueId(), new InventoryContext(ContextType.MAIN, null, null));
        p.openInventory(inv);
    }

    /** Open the manage menu listing teams the player leads. */
    public void openManageMenu(Player p) {
        List<String> led = manager.teamsLedBy(p);
        if (led.isEmpty()) {
            p.sendMessage(ChatColor.RED + "You don't lead any teams.");
            return;
        }

        int size = Math.max(9, Math.min(54, ((led.size() - 1) / 9 + 1) * 9));
        Inventory inv = Bukkit.createInventory(null, size, ChatColor.BLUE + "Manage Your Teams");

        int slot = 0;
        for (String team : led) {
            int membersCount = manager.getMembers(team).size();
            ItemStack item = makeItem(Material.PAPER,
                    ChatColor.GOLD + team,
                    List.of(
                            ChatColor.GRAY + "Leader: You",
                            ChatColor.GRAY + "Members: " + membersCount,
                            ChatColor.RED + "Click to manage members"
                    ));
            inv.setItem(slot++, item);
        }

        contexts.put(p.getUniqueId(), new InventoryContext(ContextType.MANAGE, null, null));
        p.openInventory(inv);
    }

    /** Open the members menu for a specific team. */
    public void openMembersMenu(Player p, String team) {
        if (!manager.teamExists(team)) {
            p.sendMessage(ChatColor.RED + "That team no longer exists.");
            return;
        }

        List<UUID> members = manager.getMembers(team);
        int size = Math.max(9, Math.min(54, ((members.size() - 1) / 9 + 1) * 9));
        Inventory inv = Bukkit.createInventory(null, size, ChatColor.DARK_PURPLE + "Members: " + team);

        int slot = 0;
        for (UUID uid : members) {
            OfflinePlayer off = Bukkit.getOfflinePlayer(uid);
            String display = off.getName() != null ? off.getName() : uid.toString();
            boolean isLeader = manager.getLeader(team).map(uuid -> uuid.equals(uid)).orElse(false);
            boolean isOfficer = manager.isOfficer(team, uid);

            ItemStack head = makeItem(Material.PLAYER_HEAD,
                    ChatColor.YELLOW + display,
                    List.of(
                            ChatColor.GRAY + "Leader: " + (isLeader ? "Yes" : "No"),
                            ChatColor.GRAY + "Officer: " + (isOfficer ? "Yes" : "No"),
                            ChatColor.GRAY + "Click to manage member"
                    ));
            inv.setItem(slot++, head);
        }

        contexts.put(p.getUniqueId(), new InventoryContext(ContextType.MEMBERS, team, null));
        p.openInventory(inv);
    }

    /** Open the action menu for a specific member (promote/demote/kick). */
    public void openMemberActionMenu(Player p, String team, UUID targetUuid) {
        OfflinePlayer off = Bukkit.getOfflinePlayer(targetUuid);
        String targetName = off.getName() != null ? off.getName() : targetUuid.toString();

        Inventory inv = Bukkit.createInventory(null, 27, ChatColor.DARK_GREEN + "Manage: " + targetName);

        // Promote
        ItemStack promote = makeItem(Material.GOLD_INGOT,
                ChatColor.GREEN + "Promote to Officer",
                List.of(ChatColor.GRAY + "Requires permission: mteams.promote"));
        inv.setItem(11, promote);

        // Demote
        ItemStack demote = makeItem(Material.IRON_INGOT,
                ChatColor.RED + "Demote Officer",
                List.of(ChatColor.GRAY + "Requires permission: mteams.demote"));
        inv.setItem(13, demote);

        // Kick
        ItemStack kick = makeItem(Material.BARRIER,
                ChatColor.DARK_RED + "Kick from Team",
                List.of(ChatColor.GRAY + "Requires permission: mteams.kick"));
        inv.setItem(15, kick);

        contexts.put(p.getUniqueId(), new InventoryContext(ContextType.MEMBER_ACTION, team, targetUuid));
        p.openInventory(inv);
    }

    /* ----------------------------
       Event handlers
       ---------------------------- */

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent ev) {
        if (!(ev.getPlayer() instanceof Player)) return;
        Player p = (Player) ev.getPlayer();
        // clear context when player closes any plugin GUI
        InventoryContext ctx = contexts.get(p.getUniqueId());
        if (ctx != null) {
            contexts.remove(p.getUniqueId());
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent ev) {
        if (!(ev.getWhoClicked() instanceof Player)) return;
        Player clicker = (Player) ev.getWhoClicked();
        UUID puid = clicker.getUniqueId();

        InventoryContext ctx = contexts.get(puid);
        if (ctx == null) return; // not a plugin-managed GUI

        ev.setCancelled(true); // we control all clicks in our GUIs

        ItemStack clicked = ev.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;
        ItemMeta meta = clicked.getItemMeta();
        if (meta == null || meta.getDisplayName() == null) return;
        String display = ChatColor.stripColor(meta.getDisplayName());

        switch (ctx.type) {
            case MAIN -> handleMainMenuClick(clicker, display);
            case MANAGE -> handleManageClick(clicker, display);
            case MEMBERS -> handleMembersClick(clicker, ctx.teamName, display);
            case MEMBER_ACTION -> handleMemberActionClick(clicker, ctx.teamName, ctx.targetMember, display);
        }
    }

    /* ----------------------------
       Click handlers for each menu
       ---------------------------- */

    private void handleMainMenuClick(Player clicker, String clickedName) {
        if (clickedName.equals("Create Team")) {
            if (!clicker.hasPermission("mteams.create")) {
                clicker.sendMessage(ChatColor.RED + "You don't have permission to create teams.");
                return;
            }
            clicker.closeInventory();
            clicker.sendMessage(ChatColor.GREEN + "To create a team: /team create <name>");
        } else if (clickedName.equals("Manage Teams")) {
            if (!clicker.hasPermission("mteams.manage")) {
                clicker.sendMessage(ChatColor.RED + "You don't have permission to manage teams.");
                return;
            }
            clicker.closeInventory();
            openManageMenu(clicker);
        }
    }

    private void handleManageClick(Player clicker, String clickedName) {
        // clickedName is the team name (we used ChatColor.GOLD + team)
        String teamName = clickedName; // already stripped colors
        if (!clicker.hasPermission("mteams.manage")) {
            clicker.sendMessage(ChatColor.RED + "You don't have permission to manage teams.");
            return;
        }
        if (!manager.teamExists(teamName)) {
            clicker.sendMessage(ChatColor.RED + "That team no longer exists.");
            clicker.closeInventory();
            contexts.remove(clicker.getUniqueId());
            return;
        }
        clicker.closeInventory();
        openMembersMenu(clicker, teamName);
    }

    private void handleMembersClick(Player clicker, String teamName, String clickedName) {
        // clickedName is member display name
        UUID targetUuid = findMemberUuidByName(teamName, clickedName);
        if (targetUuid == null) {
            clicker.sendMessage(ChatColor.RED + "Could not find that member.");
            return;
        }
        // open member action menu (promote/demote/kick)
        clicker.closeInventory();
        openMemberActionMenu(clicker, teamName, targetUuid);
    }

    private void handleMemberActionClick(Player clicker, String teamName, UUID targetUuid, String clickedName) {
        if (!manager.teamExists(teamName)) {
            clicker.sendMessage(ChatColor.RED + "That team no longer exists.");
            clicker.closeInventory();
            contexts.remove(clicker.getUniqueId());
            return;
        }
        // check permission & role (leader/officer)
        UUID clickerUuid = clicker.getUniqueId();
        boolean isLeader = manager.getLeader(teamName).map(uuid -> uuid.equals(clickerUuid)).orElse(false);
        boolean isOfficer = manager.isOfficer(teamName, clickerUuid);

        // Promote
        if (clickedName.equals("Promote to Officer")) {
            if (!clicker.hasPermission("mteams.promote") && !clicker.isOp()) {
                clicker.sendMessage(ChatColor.RED + "You don't have permission to promote.");
                return;
            }
            if (!isLeader && !isOfficer) {
                clicker.sendMessage(ChatColor.RED + "You must be leader or officer to promote.");
                return;
            }
            boolean ok = manager.promoteToOfficer(teamName, targetUuid);
            if (ok) clicker.sendMessage(ChatColor.GREEN + "Promoted player to officer.");
            else clicker.sendMessage(ChatColor.RED + "Could not promote (maybe already officer / not a member).");
            clicker.closeInventory();
            contexts.remove(clicker.getUniqueId());
            return;
        }

        // Demote
        if (clickedName.equals("Demote Officer")) {
            if (!clicker.hasPermission("mteams.demote") && !clicker.isOp()) {
                clicker.sendMessage(ChatColor.RED + "You don't have permission to demote.");
                return;
            }
            if (!isLeader && !isOfficer) {
                clicker.sendMessage(ChatColor.RED + "You must be leader or officer to demote.");
                return;
            }
            boolean ok = manager.demoteOfficer(teamName, targetUuid);
            if (ok) clicker.sendMessage(ChatColor.GREEN + "Demoted player from officer.");
            else clicker.sendMessage(ChatColor.RED + "Could not demote (maybe not an officer).");
            clicker.closeInventory();
            contexts.remove(clicker.getUniqueId());
            return;
        }

        // Kick
        if (clickedName.equals("Kick from Team")) {
            if (!clicker.hasPermission("mteams.kick") && !clicker.isOp()) {
                clicker.sendMessage(ChatColor.RED + "You don't have permission to kick.");
                return;
            }
            if (!isLeader && !isOfficer) {
                clicker.sendMessage(ChatColor.RED + "You must be leader or officer to kick.");
                return;
            }
            boolean ok = manager.kickMember(teamName, targetUuid);
            if (ok) {
                String targetName = Bukkit.getOfflinePlayer(targetUuid).getName();
                clicker.sendMessage(ChatColor.GREEN + "Kicked " + (targetName == null ? targetUuid.toString() : targetName) + " from " + teamName);
                Player online = Bukkit.getPlayer(targetUuid);
                if (online != null) online.sendMessage(ChatColor.RED + "You have been kicked from team " + teamName);
            } else {
                clicker.sendMessage(ChatColor.RED + "Could not kick player.");
            }
            clicker.closeInventory();
            contexts.remove(clicker.getUniqueId());
        }
    }

    /* ----------------------------
       Utility helpers
       ---------------------------- */

    /** Find a member UUID by display name within a team. Returns null if not found. */
    private UUID findMemberUuidByName(String teamName, String displayName) {
        List<UUID> members = manager.getMembers(teamName);
        for (UUID uid : members) {
            OfflinePlayer off = Bukkit.getOfflinePlayer(uid);
            String name = off.getName();
            if (name != null && name.equals(displayName)) return uid;
            // fallback if displayName is UUID string
            if (uid.toString().equals(displayName)) return uid;
        }
        return null;
    }
}

package com.exemple.MTeamsReloaded;

import org.bukkit.plugin.java.JavaPlugin;
import com.exemple.MTeamsReloaded.commands.TeamCommand;
import com.exemple.MTeamsReloaded.commands.TrCommand;
import com.exemple.MTeamsReloaded.gui.TeamGui;
import com.exemple.MTeamsReloaded.storage.TeamManager;

public final class MTeamsReloaded extends JavaPlugin {

    private static MTeamsReloaded instance;
    private TeamManager teamManager;
    private TeamGui teamGui;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        // storage
        teamManager = new TeamManager(this);
        teamManager.load();

        // GUI helper
        teamGui = new TeamGui(this, teamManager);

        // register commands
        getCommand("team").setExecutor(new TeamCommand(this, teamManager, teamGui));
        getCommand("team").setTabCompleter(new TeamCommand(this, teamManager, teamGui));
        getCommand("tr").setExecutor(new TrCommand(this, teamManager));
        getCommand("tr").setTabCompleter(new TrCommand(this, teamManager));

        // register events
        getServer().getPluginManager().registerEvents(teamGui, this);

        getLogger().info("MTeamsReloaded enabled.");
    }

    @Override
    public void onDisable() {
        teamManager.save();
        getLogger().info("MTeamsReloaded disabled.");
    }

    public static MTeamsReloaded getInstance() {
        return instance;
    }

    public TeamManager getTeamManager() {
        return teamManager;
    }

    public TeamGui getTeamGui() {
        return teamGui;
    }
}

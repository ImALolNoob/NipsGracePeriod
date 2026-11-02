package org.novato.hardcore_grace_period;

import org.bukkit.plugin.java.JavaPlugin;
import org.novato.hardcore_grace_period.commands.AltarCommand;
import org.novato.hardcore_grace_period.commands.GracePeriodCommands;
import org.novato.hardcore_grace_period.listeners.PlayerListener;

public class Hardcore_grace_period extends JavaPlugin {
    private static Hardcore_grace_period instance;
    private GracePeriodManager manager;
    private AltarManager altarManager;

    @Override
    public void onEnable() {
        instance = this;
        manager = new GracePeriodManager(this);
        altarManager = new AltarManager(this, manager);

        GracePeriodCommands cmd = new GracePeriodCommands(manager, altarManager);
        getCommand("graceperiod").setExecutor(cmd);
        getCommand("graceperiod").setTabCompleter(cmd);

        AltarCommand altarCmd = new AltarCommand(altarManager);
        getCommand("altar").setExecutor(altarCmd);

        getServer().getPluginManager().registerEvents(new PlayerListener(manager, altarManager, this), this);

        getLogger().info("Hardcore Grace Period enabled!");
    }

    @Override
    public void onDisable() {
        if (manager != null) {
            manager.saveData();
        }
        if (altarManager != null) {
            altarManager.saveData();
            altarManager.shutdown();
        }
        getLogger().info("Hardcore Grace Period disabled!");
    }

    public static Hardcore_grace_period getInstance() {
        return instance;
    }

    public GracePeriodManager getGracePeriodManager() {
        return manager;
    }

    public AltarManager getAltarManager() {
        return altarManager;
    }
}

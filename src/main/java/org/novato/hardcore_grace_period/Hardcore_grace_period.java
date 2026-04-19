package org.novato.hardcore_grace_period;

import org.bukkit.plugin.java.JavaPlugin;
import org.novato.hardcore_grace_period.commands.GracePeriodCommands;
import org.novato.hardcore_grace_period.listeners.PlayerListener;

public class Hardcore_grace_period extends JavaPlugin {
    private static Hardcore_grace_period instance;
    private GracePeriodManager manager;

    @Override
    public void onEnable() {
        instance = this;
        manager = new GracePeriodManager(this);

        GracePeriodCommands cmd = new GracePeriodCommands(manager);
        getCommand("graceperiod").setExecutor(cmd);
        getCommand("graceperiod").setTabCompleter(cmd);

        getServer().getPluginManager().registerEvents(new PlayerListener(manager, this), this);

        getLogger().info("Hardcore Grace Period enabled!");
    }

    @Override
    public void onDisable() {
        if (manager != null) {
            manager.saveData();
        }
        getLogger().info("Hardcore Grace Period disabled!");
    }

    public static Hardcore_grace_period getInstance() {
        return instance;
    }

    public GracePeriodManager getGracePeriodManager() {
        return manager;
    }
}

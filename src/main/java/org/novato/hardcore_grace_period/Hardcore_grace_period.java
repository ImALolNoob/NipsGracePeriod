package org.novato.hardcore_grace_period;

import net.fabricmc.api.ModInitializer;
import org.novato.hardcore_grace_period.commands.GracePeriodCommands;
import org.novato.hardcore_grace_period.events.ServerEventHandler;
import org.novato.hardcore_grace_period.network.NetworkHandler;

public class Hardcore_grace_period implements ModInitializer {

    @Override
    public void onInitialize() {
        // Initialize networking
        NetworkHandler.init();

        // Register commands
        GracePeriodCommands.register();

        // Register event handlers
        ServerEventHandler.register();

        System.out.println("Hardcore Grace Period mod initialized!");
    }
}

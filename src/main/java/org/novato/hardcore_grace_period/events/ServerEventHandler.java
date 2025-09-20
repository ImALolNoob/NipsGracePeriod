package org.novato.hardcore_grace_period.events;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import org.novato.hardcore_grace_period.GracePeriodManager;
import org.novato.hardcore_grace_period.network.NetworkHandler;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ServerEventHandler {

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(ServerEventHandler::onServerStarted);
        ServerLifecycleEvents.SERVER_STOPPING.register(ServerEventHandler::onServerStopping);
        ServerTickEvents.END_SERVER_TICK.register(ServerEventHandler::onServerTick);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> onPlayerJoin(handler.player, server));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> onPlayerLeave(handler.player, server));
    }

    private static void onServerStarted(MinecraftServer server) {
        GracePeriodManager.getInstance().setServer(server);
    }

    private static void onServerStopping(MinecraftServer server) {
        // Force save data when server is stopping
        GracePeriodManager.getInstance().saveData();
    }

    private static void onServerTick(MinecraftServer server) {
        GracePeriodManager manager = GracePeriodManager.getInstance();
        manager.tick();

        // Send periodic updates to clients (every 5 seconds)
        if (server.getTicks() % 100 == 0) {
            Map<UUID, Long> playerTimes = new HashMap<>();
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                long remaining = manager.getRemainingTime(player.getUuid());
                if (remaining > 0) {
                    playerTimes.put(player.getUuid(), remaining);
                }
            }

            // Always send updates (even if empty) to clear expired timers on client
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                NetworkHandler.sendGracePeriodUpdate(player, playerTimes);
            }
        }
    }

    private static void onPlayerJoin(ServerPlayerEntity player, MinecraftServer server) {
        GracePeriodManager manager = GracePeriodManager.getInstance();

        // Initialize player if event is started or if they're new
        if (manager.isEventStarted()) {
            manager.initializePlayer(player);
        }

        // Send current state to the newly joined player
        Map<UUID, Long> playerTimes = new HashMap<>();
        for (ServerPlayerEntity otherPlayer : server.getPlayerManager().getPlayerList()) {
            long remaining = manager.getRemainingTime(otherPlayer.getUuid());
            if (remaining > 0) {
                playerTimes.put(otherPlayer.getUuid(), remaining);
            }
        }

        if (!playerTimes.isEmpty()) {
            NetworkHandler.sendGracePeriodUpdate(player, playerTimes);
        }
    }

    private static void onPlayerLeave(ServerPlayerEntity player, MinecraftServer server) {
        GracePeriodManager.getInstance().removePlayer(player);
    }
}
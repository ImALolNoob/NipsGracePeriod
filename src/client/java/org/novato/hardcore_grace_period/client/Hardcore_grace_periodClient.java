package org.novato.hardcore_grace_period.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import org.novato.hardcore_grace_period.network.NetworkHandler;

public class Hardcore_grace_periodClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        OverheadRenderer.init();

        ClientPlayNetworking.registerGlobalReceiver(NetworkHandler.GracePeriodUpdatePayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                // Clear any players not in the updated map (their timers expired)
                java.util.Set<java.util.UUID> currentPlayers = new java.util.HashSet<>(payload.playerTimes().keySet());
                java.util.Iterator<java.util.UUID> iterator = OverheadRenderer.getPlayerTimes().keySet().iterator();
                while (iterator.hasNext()) {
                    java.util.UUID uuid = iterator.next();
                    if (!currentPlayers.contains(uuid)) {
                        iterator.remove();
                    }
                }

                // Update remaining players
                payload.playerTimes().forEach(OverheadRenderer::updatePlayerTime);
            });
        });
    }
}

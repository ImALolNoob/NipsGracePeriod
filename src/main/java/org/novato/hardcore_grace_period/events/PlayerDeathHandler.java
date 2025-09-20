package org.novato.hardcore_grace_period.events;

import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import org.novato.hardcore_grace_period.GracePeriodManager;

public class PlayerDeathHandler {

    public static void register() {
        ServerPlayerEvents.AFTER_RESPAWN.register(PlayerDeathHandler::onPlayerRespawn);
    }

    private static void onPlayerRespawn(ServerPlayerEntity oldPlayer, ServerPlayerEntity newPlayer, boolean alive) {
        // If the old player had grace period and they died (alive = false), handle the grace period
        if (!alive) {
            GracePeriodManager manager = GracePeriodManager.getInstance();

            if (manager.hasGracePeriod(oldPlayer.getUuid())) {
                // Player died but has grace period - restore them
                if (newPlayer.getServer() != null) {
                    newPlayer.getServer().execute(() -> {
                        // Restore full health and hunger
                        newPlayer.setHealth(newPlayer.getMaxHealth());
                        newPlayer.getHungerManager().setFoodLevel(20);
                        newPlayer.getHungerManager().setSaturationLevel(20.0f);

                        // Clear any negative effects
                        newPlayer.clearStatusEffects();

                        // Broadcast message
                        newPlayer.getServer().getPlayerManager().broadcast(
                            Text.literal(newPlayer.getName().getString() + " was saved by the grace period!")
                                .formatted(Formatting.YELLOW),
                            false
                        );
                    });
                }
            }
        }
    }
}
package org.novato.hardcore_grace_period.mixin;

import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.novato.hardcore_grace_period.GracePeriodManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayerEntity.class)
public class PlayerEntityMixin {

    @Inject(method = "onDeath", at = @At("HEAD"), cancellable = true)
    private void onDeath(DamageSource damageSource, CallbackInfo ci) {
        ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;
        GracePeriodManager manager = GracePeriodManager.getInstance();

        if (manager.hasGracePeriod(player.getUuid())) {
            // Cancel the death
            ci.cancel();

            // Show death screen and then respawn
            player.setHealth(0.1f); // Set to very low health to trigger death screen

            // Schedule respawn after a short delay
            if (player.getServer() != null) {
                player.getServer().execute(() -> {
                    // Respawn the player
                    player.requestRespawn();

                    // After respawn, restore health
                    player.getServer().execute(() -> {
                        player.setHealth(player.getMaxHealth());
                        player.getHungerManager().setFoodLevel(20);
                        player.getHungerManager().setSaturationLevel(20.0f);

                        // Broadcast message
                        player.getServer().getPlayerManager().broadcast(
                            Text.literal(player.getName().getString() + " was saved by the grace period!")
                                .formatted(Formatting.YELLOW),
                            false
                        );
                    });
                });
            }
        }
    }
}
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
public class ServerPlayerEntityMixin {

    @Inject(method = "onDeath", at = @At("HEAD"), cancellable = true)
    private void onDeath(DamageSource damageSource, CallbackInfo ci) {
        ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;
        GracePeriodManager manager = GracePeriodManager.getInstance();

        if (manager.hasGracePeriod(player.getUuid())) {
            // Cancel the normal death process
            ci.cancel();

            // Store death location and perform death actions
            if (player.getServer() != null) {
                player.getServer().execute(() -> {
                    // Manually drop all items at death location
                    for (int i = 0; i < player.getInventory().size(); i++) {
                        net.minecraft.item.ItemStack stack = player.getInventory().getStack(i);
                        if (!stack.isEmpty()) {
                            player.dropItem(stack, true);
                        }
                    }

                    // Drop experience
                    if (player.experienceLevel > 0) {
                        player.addExperience(-player.totalExperience);
                        net.minecraft.entity.ExperienceOrbEntity.spawn(
                            (net.minecraft.server.world.ServerWorld) player.getWorld(),
                            player.getPos(),
                            Math.min(player.experienceLevel * 7, 100)
                        );
                    }

                    // Clear inventory and effects
                    player.getInventory().clear();
                    player.clearStatusEffects();

                    // Set health to trigger death screen
                    player.setHealth(0.1f);

                    // Trigger respawn directly
                    player.requestRespawn();

                    // Schedule post-respawn healing and teleport
                    player.getServer().execute(() -> {
                        // Teleport to spawn
                        net.minecraft.util.math.BlockPos spawnPos = player.getWorld().getSpawnPos();
                        player.teleport(spawnPos.getX() + 0.5,
                                       spawnPos.getY() + 1,
                                       spawnPos.getZ() + 0.5,
                                       true);

                        // Restore health after teleport
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
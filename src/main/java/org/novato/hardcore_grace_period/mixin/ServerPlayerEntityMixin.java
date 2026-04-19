package org.novato.hardcore_grace_period.mixin;

import net.minecraft.block.BedBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.RespawnAnchorBlock;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.world.World;
import java.util.Set;
import org.novato.hardcore_grace_period.GracePeriodManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayerEntity.class)
public abstract class ServerPlayerEntityMixin {

    @Shadow
    public net.minecraft.server.MinecraftServer server;

    @Inject(method = "onDeath", at = @At("HEAD"), cancellable = true)
    private void onDeath(DamageSource damageSource, CallbackInfo ci) {
        ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;
        GracePeriodManager manager = GracePeriodManager.getInstance();

        if (manager.hasGracePeriod(player.getUuid())) {
            // Cancel the normal death process
            ci.cancel();

            // Instantly respawn the player
            if (this.server != null) {
                this.server.execute(() -> {
                    // Drop all items at death location
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
                            (ServerWorld) player.getEntityWorld(),
                            player.getBlockPos().toCenterPos(),
                            Math.min(player.experienceLevel * 7, 100)
                        );
                    }

                    // Clear inventory and effects
                    player.getInventory().clear();
                    player.clearStatusEffects();

                    // Teleport to respawn location: bed > spawn
                    teleportToRespawnLocation(player);

                    // Restore health
                    player.setHealth(player.getMaxHealth());
                    player.getHungerManager().setFoodLevel(20);
                    player.getHungerManager().setSaturationLevel(20.0f);

                    // Broadcast message
                    server.getPlayerManager().broadcast(
                        Text.literal(player.getName().getString() + " was saved by the grace period!")
                            .formatted(Formatting.YELLOW),
                        false
                    );
                });
            }
        }
    }

    /**
     * Teleports player to spawn location during grace period.
     * Prioritizes player's bed spawn, falls back to world spawn.
     * Finds a safe ground position in the appropriate dimension.
     */
    private void teleportToRespawnLocation(ServerPlayerEntity player) {
        BlockPos spawnCenter = null;
        ServerWorld targetWorld = null;

        // Try to get player's custom spawn point (bed/respawn anchor)
        ServerPlayerEntity.Respawn respawn = player.getRespawn();
        boolean useRespawnPoint = false;

        if (respawn != null) {
            GlobalPos globalPos = respawn.respawnData().globalPos();
            RegistryKey<World> spawnDimension = globalPos.dimension();
            BlockPos spawnPos = globalPos.pos();
            ServerWorld respawnWorld = server.getWorld(spawnDimension);

            if (respawnWorld != null) {
                // Check if spawn is forced OR if the bed/anchor is still valid
                if (respawn.forced()) {
                    // Forced spawn - use it regardless of block type
                    spawnCenter = spawnPos;
                    targetWorld = respawnWorld;
                    useRespawnPoint = true;
                    System.out.println("[Grace Period] Using forced spawn at " + spawnCenter +
                        " in dimension " + spawnDimension.getValue());
                } else if (isValidSpawnBlock(respawnWorld, spawnPos)) {
                    // Non-forced spawn with valid bed/anchor
                    spawnCenter = spawnPos;
                    targetWorld = respawnWorld;
                    useRespawnPoint = true;
                    System.out.println("[Grace Period] Using bed spawn at " + spawnCenter +
                        " in dimension " + spawnDimension.getValue());
                } else {
                    // Bed/anchor is missing or invalid
                    System.out.println("[Grace Period] Bed/anchor at " + spawnPos +
                        " is missing or invalid, falling back to world spawn");
                }
            }
        }

        if (!useRespawnPoint) {
            // No valid bed spawn - use world spawn
            targetWorld = server.getWorld(World.OVERWORLD);
            if (targetWorld == null) {
                return;
            }
            spawnCenter = targetWorld.getSpawnPoint().getPos();
            System.out.println("[Grace Period] Using world spawn at " + spawnCenter);
        }

        // Find a safe position on the ground at the spawn coordinates
        BlockPos safePos = findGroundPosition(targetWorld, spawnCenter);

        // Debug logging
        System.out.println("[Grace Period] Safe spawn position found: " + safePos);
        System.out.println("[Grace Period] Block at feet (Y=" + safePos.getY() + "): " +
            targetWorld.getBlockState(safePos).getBlock().getName().getString());
        System.out.println("[Grace Period] Block below (Y=" + (safePos.getY()-1) + "): " +
            targetWorld.getBlockState(safePos.down()).getBlock().getName().getString());
        System.out.println("[Grace Period] Block above (Y=" + (safePos.getY()+1) + "): " +
            targetWorld.getBlockState(safePos.up()).getBlock().getName().getString());

        // Teleport to the safe position - add 0.5 to center horizontally on the block
        double teleportY = safePos.getY();
        System.out.println("[Grace Period] Teleporting to Y=" + teleportY);

        player.teleport(targetWorld, safePos.getX() + 0.5, teleportY,
            safePos.getZ() + 0.5, Set.of(), 0.0f, 0.0f, false);
    }

    /**
     * Finds a safe ground position to spawn on.
     * Searches from Y=120 down to Y=64 to find the surface, avoiding underground caves.
     */
    private BlockPos findGroundPosition(ServerWorld world, BlockPos startPos) {
        System.out.println("[Grace Period] Searching for safe spawn from Y=120 down to Y=64");

        // Search from Y=120 down to Y=64 to find the surface
        for (int y = 120; y >= 64; y--) {
            BlockPos checkPos = new BlockPos(startPos.getX(), y, startPos.getZ());

            if (isSafeSpawnPosition(world, checkPos)) {
                System.out.println("[Grace Period] Found safe spawn at Y=" + y);
                return checkPos;
            }
        }

        // Fallback - use world spawn Y level if no safe position found
        System.out.println("[Grace Period] No safe spawn found, using fallback Y=80");
        return new BlockPos(startPos.getX(), 80, startPos.getZ());
    }

    /**
     * Checks if a position is safe for player spawning.
     * Requires a standable block below, and 2 blocks of air above.
     */
    private boolean isSafeSpawnPosition(ServerWorld world, BlockPos pos) {
        BlockPos groundPos = pos.down();

        // Check that the block below is standable (has collision, not air, not dangerous)
        net.minecraft.block.BlockState groundState = world.getBlockState(groundPos);
        boolean hasStandableGround = !groundState.isAir() &&
                                     !groundState.getBlock().equals(net.minecraft.block.Blocks.LAVA) &&
                                     !groundState.getBlock().equals(net.minecraft.block.Blocks.WATER) &&
                                     !groundState.getBlock().equals(net.minecraft.block.Blocks.FIRE) &&
                                     !groundState.getCollisionShape(world, groundPos).isEmpty();

        // Check that player position (2 blocks) is air
        boolean feetClear = world.getBlockState(pos).isAir();
        boolean headClear = world.getBlockState(pos.up()).isAir();

        boolean isSafe = hasStandableGround && feetClear && headClear;

        if (!isSafe) {
            System.out.println("[Grace Period] Position Y=" + pos.getY() + " unsafe: " +
                "standableGround=" + hasStandableGround +
                " feetClear=" + feetClear +
                " headClear=" + headClear);
        }

        return isSafe;
    }

    /**
     * Validates if a spawn point has a valid bed or respawn anchor.
     * Returns true if the block is a working bed or charged respawn anchor.
     */
    private boolean isValidSpawnBlock(ServerWorld world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        Block block = state.getBlock();

        // Check if it's a bed
        if (block instanceof BedBlock) {
            // Beds only work in Overworld (not in Nether/End where they explode)
            return BedBlock.isBedWorking(world);
        }

        // Check if it's a respawn anchor
        if (block instanceof RespawnAnchorBlock) {
            // Respawn anchors only work in the Nether and must have charges
            int charges = state.get(Properties.CHARGES);
            boolean inNether = world.getRegistryKey() == World.NETHER;
            return charges > 0 && inNether;
        }

        // Not a valid spawn block
        return false;
    }

}
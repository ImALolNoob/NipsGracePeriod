package org.novato.hardcore_grace_period.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
// WorldRenderEvents was removed in Minecraft 1.21.9/1.21.10
// TODO: Reimplement using mixins when Fabric provides a replacement
// import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Environment(EnvType.CLIENT)
public class OverheadRenderer {
    private static final Map<UUID, Long> playerTimes = new HashMap<>();

    public static void init() {
        // DISABLED: WorldRenderEvents.AFTER_ENTITIES was removed in 1.21.9/1.21.10
        // Overhead rendering is temporarily disabled until Fabric provides a replacement API
        // The core grace period functionality still works via boss bar

        /* Original code - commented out for 1.21.10 compatibility:
        WorldRenderEvents.AFTER_ENTITIES.register((context) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null || client.world == null) return;

            MatrixStack matrices = context.matrixStack();
            VertexConsumerProvider vertexConsumers = context.consumers();

            for (PlayerEntity player : client.world.getPlayers()) {
                if (player == client.player) continue; // Don't render on self

                UUID playerUuid = player.getUuid();
                Long remainingTime = playerTimes.get(playerUuid);

                if (remainingTime != null && remainingTime > 0) {
                    renderOverheadText(matrices, vertexConsumers, player, remainingTime, 1.0f);
                }
            }
        });
        */
    }

    public static void updatePlayerTime(UUID playerUuid, long remainingTime) {
        if (remainingTime > 0) {
            playerTimes.put(playerUuid, remainingTime);
        } else {
            playerTimes.remove(playerUuid);
        }
    }

    public static void removePlayer(UUID playerUuid) {
        playerTimes.remove(playerUuid);
    }

    public static Map<UUID, Long> getPlayerTimes() {
        return playerTimes;
    }

    private static void renderOverheadText(MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                                         PlayerEntity player, long remainingTime, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        TextRenderer textRenderer = client.textRenderer;

        Vec3d cameraPos = client.gameRenderer.getCamera().getPos();
        Vec3d playerPos = player.getBlockPos().toCenterPos().add(0, player.getHeight() + 0.5, 0);

        double distance = cameraPos.distanceTo(playerPos);
        if (distance > 64.0) return; // Don't render if too far

        matrices.push();

        // Translate to player position
        matrices.translate(
            playerPos.x - cameraPos.x,
            playerPos.y - cameraPos.y,
            playerPos.z - cameraPos.z
        );

        // Face the camera
        matrices.multiply(client.gameRenderer.getCamera().getRotation());
        matrices.scale(-0.03f, -0.03f, 0.03f); // Made slightly bigger for better visibility

        String timeText = formatTime(remainingTime);
        Text text = Text.literal("Grace: " + timeText).formatted(getTimeColor(remainingTime));

        Matrix4f matrix = matrices.peek().getPositionMatrix();
        float x = -textRenderer.getWidth(text) / 2.0f;
        float y = 0;

        // Render text with shadow for better visibility
        textRenderer.draw(text, x, y, 0xFFFFFF, true, matrix, vertexConsumers,
                         TextRenderer.TextLayerType.NORMAL, 0x80000000, 15728880);

        matrices.pop();
    }

    private static String formatTime(long ticks) {
        long seconds = ticks / 20;
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;

        if (hours > 0) {
            return String.format("%dh %dm", hours, minutes);
        } else if (minutes > 0) {
            return String.format("%dm %ds", minutes, secs);
        } else {
            return String.format("%ds", secs);
        }
    }

    private static Formatting getTimeColor(long remainingTime) {
        long totalTime = 3 * 60 * 60 * 20; // 3 hours in ticks, should get from config
        double ratio = (double) remainingTime / totalTime;

        if (ratio > 0.5) {
            return Formatting.GREEN;
        } else if (ratio > 0.25) {
            return Formatting.YELLOW;
        } else {
            return Formatting.RED;
        }
    }
}
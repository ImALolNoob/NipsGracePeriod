package org.novato.hardcore_grace_period.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.novato.hardcore_grace_period.GracePeriodManager;

import java.util.Collection;
import java.util.UUID;

public class GracePeriodCommands {

    public static void register() {
        CommandRegistrationCallback.EVENT.register(GracePeriodCommands::registerCommands);
    }

    private static void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher,
                                       CommandRegistryAccess registryAccess,
                                       CommandManager.RegistrationEnvironment environment) {

        dispatcher.register(CommandManager.literal("graceperiod")
            .requires(source -> source.hasPermissionLevel(2))
            .then(CommandManager.literal("start")
                .executes(GracePeriodCommands::startEvent))
            .then(CommandManager.literal("reset")
                .executes(GracePeriodCommands::resetEvent))
            .then(CommandManager.literal("check")
                .then(CommandManager.argument("player", EntityArgumentType.player())
                    .executes(GracePeriodCommands::checkPlayer)))
            .then(CommandManager.literal("set")
                .then(CommandManager.argument("player", EntityArgumentType.player())
                    .then(CommandManager.argument("hours", IntegerArgumentType.integer(0, 24))
                        .executes(GracePeriodCommands::setPlayerTimeHours)
                        .then(CommandManager.argument("minutes", IntegerArgumentType.integer(0, 59))
                            .executes(GracePeriodCommands::setPlayerTimeHoursMinutes)))))
            .then(CommandManager.literal("resetplayer")
                .then(CommandManager.argument("player", EntityArgumentType.player())
                    .executes(GracePeriodCommands::resetPlayerTime))));
    }

    private static int startEvent(CommandContext<ServerCommandSource> context) {
        GracePeriodManager manager = GracePeriodManager.getInstance();

        if (manager.isEventStarted()) {
            context.getSource().sendError(Text.literal("Grace period event is already running!"));
            return 0;
        }

        // Start the countdown
        startCountdown(context.getSource().getServer(), manager);

        context.getSource().sendFeedback(() ->
            Text.literal("Starting grace period countdown...").formatted(Formatting.YELLOW), true);

        return 1;
    }

    private static void startCountdown(net.minecraft.server.MinecraftServer server, GracePeriodManager manager) {
        // Send initial countdown message
        server.getPlayerManager().broadcast(
            Text.literal("Grace Period Starting in 10 seconds...").formatted(Formatting.GOLD, Formatting.BOLD),
            false
        );

        // Schedule countdown titles from 10 to 1
        for (int i = 10; i >= 1; i--) {
            final int countdown = i;
            scheduleTask(server, (10 - i) * 20, () -> {
                // Send title to all players
                for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                    player.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.TitleS2CPacket(
                        Text.literal(String.valueOf(countdown)).formatted(Formatting.YELLOW, Formatting.BOLD)
                    ));

                    // Play a sound for each countdown tick
                    player.playSound(
                        net.minecraft.sound.SoundEvents.BLOCK_NOTE_BLOCK_HAT.value(),
                        1.0f,
                        1.0f
                    );
                }
            });
        }

        // Schedule the actual event start at 10 seconds (200 ticks)
        scheduleTask(server, 200, () -> {
            // Get the formatted grace period time
            String gracePeriodTime = manager.getConfig().getFormattedGracePeriod();

            // Send "Grace Period Has Started" title
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                player.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.TitleS2CPacket(
                    Text.literal("Grace Period Has Started!").formatted(Formatting.GREEN, Formatting.BOLD)
                ));
                player.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.SubtitleS2CPacket(
                    Text.literal(gracePeriodTime + " Grace Period Has Started").formatted(Formatting.YELLOW)
                ));

                // Play a different sound for the start
                player.playSound(
                    net.minecraft.sound.SoundEvents.ENTITY_PLAYER_LEVELUP,
                    1.0f,
                    1.0f
                );
            }

            // Actually start the event
            manager.startEvent();
        });
    }

    private static void scheduleTask(net.minecraft.server.MinecraftServer server, long delayTicks, Runnable task) {
        // Use the server's task queue to schedule delayed execution
        final long targetTime = server.getTicks() + delayTicks;

        // Create a repeating check until we reach the target time
        scheduleRepeatingCheck(server, targetTime, task);
    }

    private static void scheduleRepeatingCheck(net.minecraft.server.MinecraftServer server, long targetTime, Runnable task) {
        server.execute(() -> {
            if (server.getTicks() >= targetTime) {
                task.run();
            } else {
                // Schedule another check next tick
                scheduleRepeatingCheck(server, targetTime, task);
            }
        });
    }

    private static int resetEvent(CommandContext<ServerCommandSource> context) {
        GracePeriodManager manager = GracePeriodManager.getInstance();
        manager.resetEvent();

        context.getSource().sendFeedback(() ->
            Text.literal("Grace period event reset!").formatted(Formatting.YELLOW), true);

        return 1;
    }

    private static int checkPlayer(CommandContext<ServerCommandSource> context) {
        try {
            ServerPlayerEntity player = EntityArgumentType.getPlayer(context, "player");
            GracePeriodManager manager = GracePeriodManager.getInstance();

            long remaining = manager.getRemainingTime(player.getUuid());
            String timeStr = formatTime(remaining);

            context.getSource().sendFeedback(() ->
                Text.literal(player.getName().getString() + " has " + timeStr + " remaining")
                    .formatted(Formatting.AQUA), false);

            return 1;
        } catch (Exception e) {
            context.getSource().sendError(Text.literal("Failed to check player: " + e.getMessage()));
            return 0;
        }
    }

    private static int setPlayerTimeHours(CommandContext<ServerCommandSource> context) {
        try {
            ServerPlayerEntity player = EntityArgumentType.getPlayer(context, "player");
            int hours = IntegerArgumentType.getInteger(context, "hours");

            GracePeriodManager manager = GracePeriodManager.getInstance();
            long ticks = hours * 60L * 60L * 20L; // Convert hours to ticks
            manager.setPlayerTime(player.getUuid(), ticks);

            context.getSource().sendFeedback(() ->
                Text.literal("Set " + player.getName().getString() + "'s grace period to " + hours + " hours")
                    .formatted(Formatting.GREEN), true);

            return 1;
        } catch (Exception e) {
            context.getSource().sendError(Text.literal("Failed to set player time: " + e.getMessage()));
            return 0;
        }
    }

    private static int setPlayerTimeHoursMinutes(CommandContext<ServerCommandSource> context) {
        try {
            ServerPlayerEntity player = EntityArgumentType.getPlayer(context, "player");
            int hours = IntegerArgumentType.getInteger(context, "hours");
            int minutes = IntegerArgumentType.getInteger(context, "minutes");

            GracePeriodManager manager = GracePeriodManager.getInstance();
            long ticks = (hours * 60L * 60L + minutes * 60L) * 20L; // Convert hours and minutes to ticks
            manager.setPlayerTime(player.getUuid(), ticks);

            String timeStr = hours > 0 ? hours + "h " + minutes + "m" : minutes + "m";
            context.getSource().sendFeedback(() ->
                Text.literal("Set " + player.getName().getString() + "'s grace period to " + timeStr)
                    .formatted(Formatting.GREEN), true);

            return 1;
        } catch (Exception e) {
            context.getSource().sendError(Text.literal("Failed to set player time: " + e.getMessage()));
            return 0;
        }
    }

    private static int resetPlayerTime(CommandContext<ServerCommandSource> context) {
        try {
            ServerPlayerEntity player = EntityArgumentType.getPlayer(context, "player");
            GracePeriodManager manager = GracePeriodManager.getInstance();
            manager.resetPlayerTime(player.getUuid());

            String defaultTime = manager.getConfig().getFormattedGracePeriod();
            context.getSource().sendFeedback(() ->
                Text.literal("Reset " + player.getName().getString() + "'s grace period to " + defaultTime)
                    .formatted(Formatting.GREEN), true);

            return 1;
        } catch (Exception e) {
            context.getSource().sendError(Text.literal("Failed to reset player time: " + e.getMessage()));
            return 0;
        }
    }

    private static String formatTime(long ticks) {
        long seconds = ticks / 20;
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;

        if (hours > 0) {
            return String.format("%dh %dm %ds", hours, minutes, secs);
        } else if (minutes > 0) {
            return String.format("%dm %ds", minutes, secs);
        } else {
            return String.format("%ds", secs);
        }
    }
}
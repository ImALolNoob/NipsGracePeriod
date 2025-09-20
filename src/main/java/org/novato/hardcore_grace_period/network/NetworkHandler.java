package org.novato.hardcore_grace_period.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.Map;
import java.util.UUID;

public class NetworkHandler {
    public static final Identifier GRACE_PERIOD_UPDATE_ID = Identifier.of("hardcore_grace_period", "grace_period_update");

    public static void init() {
        PayloadTypeRegistry.playS2C().register(GracePeriodUpdatePayload.ID, GracePeriodUpdatePayload.CODEC);
    }

    public static void sendGracePeriodUpdate(ServerPlayerEntity player, Map<UUID, Long> playerTimes) {
        ServerPlayNetworking.send(player, new GracePeriodUpdatePayload(playerTimes));
    }

    public record GracePeriodUpdatePayload(Map<UUID, Long> playerTimes) implements CustomPayload {
        public static final CustomPayload.Id<GracePeriodUpdatePayload> ID = new CustomPayload.Id<>(GRACE_PERIOD_UPDATE_ID);
        public static final PacketCodec<RegistryByteBuf, GracePeriodUpdatePayload> CODEC =
            new PacketCodec<RegistryByteBuf, GracePeriodUpdatePayload>() {
                @Override
                public GracePeriodUpdatePayload decode(RegistryByteBuf buf) {
                    return read(buf);
                }

                @Override
                public void encode(RegistryByteBuf buf, GracePeriodUpdatePayload payload) {
                    write(buf, payload);
                }
            };

        public static void write(RegistryByteBuf buf, GracePeriodUpdatePayload payload) {
            buf.writeInt(payload.playerTimes.size());
            for (Map.Entry<UUID, Long> entry : payload.playerTimes.entrySet()) {
                buf.writeUuid(entry.getKey());
                buf.writeLong(entry.getValue());
            }
        }

        public static GracePeriodUpdatePayload read(RegistryByteBuf buf) {
            int size = buf.readInt();
            Map<UUID, Long> playerTimes = new java.util.HashMap<>();
            for (int i = 0; i < size; i++) {
                UUID uuid = buf.readUuid();
                long time = buf.readLong();
                playerTimes.put(uuid, time);
            }
            return new GracePeriodUpdatePayload(playerTimes);
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }
}
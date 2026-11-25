package ru.adaptionwheel.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import ru.adaptionwheel.AdaptionWheel;

import java.util.UUID;

/**
 * Server -&gt; client: one tick of a Time Warp zone.
 *
 * <p>The client runs the same clock decimator as the server, so the packet carries the logical
 * clock and the current slow-tick verdict rather than asking the client to guess from speed alone.
 */
public record TimeWarpStatePayload(
        UUID owner,
        float speed,
        long clock,
        boolean tick,
        BlockPos centre,
        float radius,
        boolean active) implements CustomPacketPayload {

    public static final Type<TimeWarpStatePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AdaptionWheel.MODID, "time_warp_state"));

    public static final StreamCodec<FriendlyByteBuf, TimeWarpStatePayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeUUID(p.owner());
                buf.writeFloat(p.speed());
                buf.writeLong(p.clock());
                buf.writeBoolean(p.tick());
                buf.writeBlockPos(p.centre());
                buf.writeFloat(p.radius());
                buf.writeBoolean(p.active());
            },
            buf -> new TimeWarpStatePayload(
                    buf.readUUID(),
                    buf.readFloat(),
                    buf.readLong(),
                    buf.readBoolean(),
                    buf.readBlockPos(),
                    buf.readFloat(),
                    buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Sent to everybody who can see the sphere, including its owner. */
    public static void sendTo(ServerLevel level, UUID owner, float speed, long clock, boolean tick,
                              BlockPos centre, float radius, boolean active) {
        if (level == null) {
            return;
        }
        PacketDistributor.sendToPlayersNear(level, null, centre.getX(), centre.getY(), centre.getZ(),
                Math.max(64.0, radius), new TimeWarpStatePayload(owner, speed, clock, tick, centre, radius, active));
    }

    public static void sendTo(ServerPlayer player, TimeWarpStatePayload payload) {
        if (player != null) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }
}

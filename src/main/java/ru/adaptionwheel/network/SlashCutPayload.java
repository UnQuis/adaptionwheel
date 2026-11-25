package ru.adaptionwheel.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import ru.adaptionwheel.AdaptionWheel;

/**
 * Server -&gt; client: a mob was cut by a cursed slash, with the geometry of the cut plane.
 *
 * <p>The cut is server-authoritative: the target is removed there, and every client rebuilds the
 * two halves locally from this packet. Nothing about the halves is persisted or sent back.
 */
public record SlashCutPayload(int entityId, Vec3 point, Vec3 normal, Vec3 sweep, int ownerId)
        implements CustomPacketPayload {

    public static final Type<SlashCutPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AdaptionWheel.MODID, "slash_cut"));

    public static final StreamCodec<FriendlyByteBuf, SlashCutPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.entityId);
                buf.writeVec3(payload.point);
                buf.writeVec3(payload.normal);
                buf.writeVec3(payload.sweep);
                buf.writeVarInt(payload.ownerId);
            },
            buf -> new SlashCutPayload(buf.readVarInt(), buf.readVec3(), buf.readVec3(), buf.readVec3(),
                    buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void sendNear(ServerLevel level, Vec3 at, SlashCutPayload payload) {
        double radiusSq = 192.0 * 192.0;
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(at) <= radiusSq) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        }
    }

    /** Hand-off for the client FX task; the payload never mutates server state. */
    private static volatile SlashCutPayload lastReceived;

    public static void apply(SlashCutPayload payload) {
        lastReceived = payload;
    }

    public static SlashCutPayload lastReceived() {
        return lastReceived;
    }
}

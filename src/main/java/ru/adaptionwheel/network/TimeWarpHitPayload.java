package ru.adaptionwheel.network;

import java.util.UUID;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import ru.adaptionwheel.AdaptionWheel;

/**
 * Server -&gt; client: a buffered hit finally landed when a Time Warp zone ended.
 *
 * <p>Damage itself is authoritative on the server; this packet only lets the client play the
 * impact sound where the hit happened. It is deliberately small and carries no damage to apply.
 */
public record TimeWarpHitPayload(UUID owner, int targetId, float x, float y, float z,
                                 boolean critical, int hits, float damage) implements CustomPacketPayload {

    public static final Type<TimeWarpHitPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AdaptionWheel.MODID, "time_warp_hit"));

    public static final StreamCodec<FriendlyByteBuf, TimeWarpHitPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeUUID(p.owner);
                buf.writeVarInt(p.targetId);
                buf.writeFloat(p.x);
                buf.writeFloat(p.y);
                buf.writeFloat(p.z);
                buf.writeBoolean(p.critical);
                buf.writeVarInt(p.hits);
                buf.writeFloat(p.damage);
            },
            buf -> new TimeWarpHitPayload(
                    buf.readUUID(),
                    buf.readVarInt(),
                    buf.readFloat(),
                    buf.readFloat(),
                    buf.readFloat(),
                    buf.readBoolean(),
                    buf.readVarInt(),
                    buf.readFloat()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void apply(TimeWarpHitPayload payload) {
        ru.adaptionwheel.client.TimeWarpClient.playHit(payload);
    }
}

package ru.adaptionwheel.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import ru.adaptionwheel.AdaptionWheel;
import ru.adaptionwheel.client.ClientAdaption;

/**
 * Server→client: the fist's block counter, pushed the moment a block is broken.
 *
 * <p>The regular adaptation sync only goes out once a second, so a break used to sit invisible
 * in the HUD bar for 100-700 ms depending on where in the tick the sync happened to land. This
 * is four bytes and fires only on an actual break, so it makes the bar immediate without
 * dragging the (much larger) full-state payload up to break frequency.</p>
 */
public record FistProgressPayload(int done, int total) implements CustomPacketPayload {

    public static final Type<FistProgressPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AdaptionWheel.MODID, "fist_progress"));

    public static final StreamCodec<FriendlyByteBuf, FistProgressPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.done);
                buf.writeVarInt(payload.total);
            },
            buf -> new FistProgressPayload(buf.readVarInt(), buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void send(ServerPlayer player, int done, int total) {
        PacketDistributor.sendToPlayer(player, new FistProgressPayload(done, total));
    }

    public static void apply(FistProgressPayload payload) {
        ClientAdaption.fistProgressDone = payload.done();
        ClientAdaption.fistProgressTotal = payload.total();
    }
}

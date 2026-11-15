package ru.adaptionwheel.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import ru.adaptionwheel.AdaptionWheel;
import ru.adaptionwheel.server.CacheService;

/**
 * Client -&gt; server: a click on one of the cache slots drawn inside a container screen.
 */
public record CacheClickPayload(int index, int button, boolean shift) implements CustomPacketPayload {

    public static final Type<CacheClickPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(AdaptionWheel.MODID, "cache_click"));

    public static final StreamCodec<ByteBuf, CacheClickPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, CacheClickPayload::index,
            ByteBufCodecs.VAR_INT, CacheClickPayload::button,
            ByteBufCodecs.BOOL, CacheClickPayload::shift,
            CacheClickPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void send(int index, int button, boolean shift) {
        ClientPacketDistributor.sendToServer(new CacheClickPayload(index, button, shift));
    }

    public static void handle(CacheClickPayload payload, ServerPlayer player) {
        CacheService.click(player, payload.index(), payload.button(), payload.shift());
    }
}

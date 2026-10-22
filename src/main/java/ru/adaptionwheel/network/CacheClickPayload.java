package ru.adaptionwheel.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import ru.adaptionwheel.AdaptionWheel;
import ru.adaptionwheel.server.CacheService;

/**
 * Client -&gt; server: a click on one of the cache slots drawn inside a container screen.
 *
 * <p>The cache is not a real {@code Slot} in those menus, so vanilla's own click pipeline cannot
 * reach it. The client therefore sends the intent — which cell, which button, shift or not — and the
 * server performs the move against the menu's carried stack. The carried stack is server-owned, so
 * this is the same authority split as every other payload here: the client asks, the server decides.
 */
public record CacheClickPayload(int index, int button, boolean shift) implements CustomPacketPayload {

    public static final Type<CacheClickPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AdaptionWheel.MODID, "cache_click"));

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
        PacketDistributor.sendToServer(new CacheClickPayload(index, button, shift));
    }

    public static void handle(CacheClickPayload payload, ServerPlayer player) {
        CacheService.click(player, payload.index(), payload.button(), payload.shift());
    }
}

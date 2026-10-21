package ru.adaptionwheel.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import ru.adaptionwheel.AdaptionWheel;
import ru.adaptionwheel.client.DimensionImpactFX;

/**
 * Server -&gt; client: play the manga impact panel when a Dive lands.
 *
 * <p>It travels separately from the crater because the server decides when the landing happened.
 * Nearby players get it too — a slam that only punched the diver's screen would look like a
 * private effect rather than something that happened in the world.
 */
public record DiveImpactPayload() implements CustomPacketPayload {

    public static final DiveImpactPayload INSTANCE = new DiveImpactPayload();

    public static final Type<DiveImpactPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AdaptionWheel.MODID, "dive_impact"));

    public static final StreamCodec<FriendlyByteBuf, DiveImpactPayload> STREAM_CODEC =
            StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void sendNear(ServerPlayer source) {
        if (!(source.level() instanceof ServerLevel level)) {
            return;
        }
        double radiusSq = 64.0 * 64.0;
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(source) <= radiusSq) {
                PacketDistributor.sendToPlayer(player, INSTANCE);
            }
        }
    }

    public static void apply(DiveImpactPayload payload) {
        DimensionImpactFX.trigger();
    }
}

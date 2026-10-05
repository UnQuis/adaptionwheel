package ru.adaptionwheel.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import ru.adaptionwheel.AdaptionWheel;
import ru.adaptionwheel.client.DimensionImpactFX;

/**
 * Server -&gt; client: "the rifts you just fired are away", which is what plays the impact frame.
 *
 * <p>The panel used to be triggered by scanning the client level for {@code SpatialRiftProjectile}
 * instances whose owner was the local player. That can never match, because
 * {@code Projectile.getOwner()} only returns its cached owner or resolves one through a
 * {@code ServerLevel}; the entity spawn packet carries no owner, so on a client the lookup falls
 * through to {@code null}. Sending the signal explicitly is both the only thing that works and the
 * accurate one — the server is what decided the swing produced rifts.
 */
public record RiftImpactPayload() implements CustomPacketPayload {

    public static final Type<RiftImpactPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AdaptionWheel.MODID, "rift_impact"));

    public static final StreamCodec<FriendlyByteBuf, RiftImpactPayload> STREAM_CODEC =
            StreamCodec.unit(new RiftImpactPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void send(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new RiftImpactPayload());
    }

    public static void apply(RiftImpactPayload payload) {
        DimensionImpactFX.trigger();
    }
}
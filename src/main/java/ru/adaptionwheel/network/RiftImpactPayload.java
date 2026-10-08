package ru.adaptionwheel.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.adaptionwheel.AdaptionWheel;

/**
 * Server -&gt; client: this player's swing actually spawned spatial rifts, so play the impact frame.
 *
 * <p>Sent per player rather than fired from a scan of the level for {@code SpatialRiftProjectile}
 * owned by the local player. That scan could never work: {@code Projectile.getOwner()} resolves its
 * cached owner only, and the spawn packet carries no owner, so on the client it was always null and
 * the panel was dead code. A clientbound packet is also the honest signal -- the server already knows
 * which swing produced the rifts, which removes the ping-dependent guesswork entirely.
 */
public record RiftImpactPayload() implements CustomPacketPayload {

    public static final Type<RiftImpactPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(AdaptionWheel.MODID, "rift_impact"));

    public static final StreamCodec<FriendlyByteBuf, RiftImpactPayload> STREAM_CODEC =
            StreamCodec.unit(new RiftImpactPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * Best-effort by design. The rift itself has already been spawned by the time this is sent, so
     * a failure here can only cost the cosmetic panel -- never the ability.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger("adaptionwheel/rift_impact");

    public static void send(ServerPlayer player) {
        try {
            PacketDistributor.sendToPlayer(player, new RiftImpactPayload());
        } catch (RuntimeException e) {
            LOGGER.warn("Rift impact send failed for {}; the rifts themselves are unaffected",
                    player.getName().getString(), e);
        }
    }
}
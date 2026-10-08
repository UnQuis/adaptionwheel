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
 * Server -&gt; client: play the Existence cinematic for this boss.
 *
 * <p>The adaptation itself is <b>not</b> granted by this packet. The bar is already full when it
 * arrives -- the original grants at tick 540 of the sequence, but making the reward depend on a
 * client-side sequence finishing means losing it if the player logs out, dies, or the boss
 * despawns mid-cinematic. The grant stays server-side and immediate, and this is the presentation.
 *
 * @param bossEntityId entity id of the boss the lines are addressed to, or {@code -1} if it is gone
 * @param bossName     the boss's display name, so the client need not resolve a missing entity
 */
public record ExistenceCinematicPayload(int bossEntityId, String bossName) implements CustomPacketPayload {

    public static final Type<ExistenceCinematicPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(AdaptionWheel.MODID, "existence_cinematic"));

    private static final Logger LOGGER =
            LoggerFactory.getLogger("adaptionwheel/existence_cinematic");

    public static final int MAX_NAME_CHARS = 128;

    /**
     * Clamped to what the codec will actually accept, which is two limits and not one.
     *
     * <p>{@code Utf8String.write} rejects a string that is longer than the bound in UTF-16
     * <i>characters</i>, and then rejects it again if it encodes to more than
     * {@code ByteBufUtil.utf8MaxBytes(bound)} bytes. Clamping to code points satisfies neither: an
     * astral character is two UTF-16 units and up to four UTF-8 bytes, so 128 of them is 256 units
     * and 512 bytes -- over on both counts. The byte budget is read from Netty rather than written
     * out as a literal, because it is Netty's factor and guessing it is exactly the mistake.
     *
     * <p>Walks code points and stops before the one that would cross either limit, so the result
     * never ends in half a surrogate pair.
     */
    private static String clampName(String name) {
        if (name == null) {
            return "";
        }
        int maxUnits = MAX_NAME_CHARS;
        int maxBytes = io.netty.buffer.ByteBufUtil.utf8MaxBytes(MAX_NAME_CHARS);
        int units = 0;
        int bytes = 0;
        int i = 0;
        while (i < name.length()) {
            int cp = name.codePointAt(i);
            int cpUnits = Character.charCount(cp);
            int cpBytes = io.netty.buffer.ByteBufUtil.utf8Bytes(new String(Character.toChars(cp)));
            if (units + cpUnits > maxUnits || bytes + cpBytes > maxBytes) {
                break;
            }
            units += cpUnits;
            bytes += cpBytes;
            i += cpUnits;
        }
        return name.substring(0, i);
    }

    public static final StreamCodec<FriendlyByteBuf, ExistenceCinematicPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.bossEntityId());
                buf.writeUtf(clampName(payload.bossName()), MAX_NAME_CHARS);
            },
            buf -> new ExistenceCinematicPayload(buf.readVarInt(), buf.readUtf(128)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * Best-effort by design. A cinematic is cosmetic, and a failure here must never be able to take
     * a reward with it, so the send is isolated rather than trusted.
     */
    public static void send(ServerPlayer player, int bossEntityId, String bossName) {
        try {
            PacketDistributor.sendToPlayer(player,
                    new ExistenceCinematicPayload(bossEntityId, clampName(bossName)));
        } catch (RuntimeException e) {
            LOGGER.warn("Existence cinematic send failed for {}; the adaptation itself is unaffected",
                    player.getName().getString(), e);
        }
    }
}
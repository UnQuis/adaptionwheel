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
 * Progress of the punching fist, kept separate from {@link FistProgressPayload} on purpose.
 *
 * <p>That packet feeds a HUD row that is gated on the BREAKING fist being unlocked and named after
 * its tier, so sending this feature's counters through it would draw the wrong fist's row — and
 * would draw it even for a player who has never unlocked the other one. Two fists, two rows.
 */
public record CombatFistProgressPayload(int done, int total, int tier) implements CustomPacketPayload {

    public static final Type<CombatFistProgressPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AdaptionWheel.MODID, "combat_fist_progress"));

    public static final StreamCodec<FriendlyByteBuf, CombatFistProgressPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.done);
                buf.writeVarInt(payload.total);
                buf.writeVarInt(payload.tier);
            },
            buf -> new CombatFistProgressPayload(buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void send(ServerPlayer player, int done, int total, int tier) {
        PacketDistributor.sendToPlayer(player, new CombatFistProgressPayload(done, total, tier));
    }

    public static void apply(CombatFistProgressPayload payload) {
        ClientAdaption.combatFistProgressDone = payload.done();
        ClientAdaption.combatFistProgressTotal = payload.total();
        ClientAdaption.combatFistProgressTier = payload.tier();
    }
}

package ru.adaptionwheel.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import ru.adaptionwheel.AdaptionWheel;
import ru.adaptionwheel.server.FistMastery;

/** Client→server: the player flipped the Instabreak stance keybind. */
public record FistInstabreakPayload(boolean active) implements CustomPacketPayload {

    public static final Type<FistInstabreakPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(AdaptionWheel.MODID, "fist_instabreak"));

    public static final StreamCodec<FriendlyByteBuf, FistInstabreakPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> buf.writeBoolean(payload.active),
            buf -> new FistInstabreakPayload(buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void send(boolean active) {
        net.neoforged.neoforge.client.network.ClientPacketDistributor
                .sendToServer(new FistInstabreakPayload(active));
    }

    public static void handle(FistInstabreakPayload payload, ServerPlayer player) {
        // The server re-validates the unlock before accepting; a rejected toggle is answered
        // with a fresh sync carrying the authoritative stance.
        FistMastery.setInstabreak(player, payload.active());
    }
}

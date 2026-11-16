package ru.adaptionwheel.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import ru.adaptionwheel.AdaptionWheel;
import ru.adaptionwheel.server.AbilityDispatch;

/**
 * Client -&gt; server: the player clicked an ability card in the wheel menu.
 *
 * <p>Intent only. The server checks that the id is registered and that the player still qualifies
 * for it, then writes the choice into the attachment and syncs it back. The client also updates its
 * local selection optimistically so the highlight moves on the same frame.
 */
public record SelectAbilityPayload(String id) implements CustomPacketPayload {

    public static final Type<SelectAbilityPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AdaptionWheel.MODID, "select_ability"));

    public static final StreamCodec<FriendlyByteBuf, SelectAbilityPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> buf.writeUtf(payload.id() == null ? "" : payload.id()),
            buf -> new SelectAbilityPayload(buf.readUtf()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void send(String id) {
        PacketDistributor.sendToServer(new SelectAbilityPayload(id));
    }

    public static void handle(SelectAbilityPayload payload, ServerPlayer player) {
        AbilityDispatch.select(player, payload.id());
    }
}

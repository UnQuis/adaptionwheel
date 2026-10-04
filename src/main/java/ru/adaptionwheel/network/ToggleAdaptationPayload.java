package ru.adaptionwheel.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import ru.adaptionwheel.AdaptionWheel;
import ru.adaptionwheel.data.PlayerAdaption;
import ru.adaptionwheel.server.AdaptionEvents;

public record ToggleAdaptationPayload(String concept) implements CustomPacketPayload {

    public static final Type<ToggleAdaptationPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AdaptionWheel.MODID, "toggle_adaptation"));

    public static final StreamCodec<FriendlyByteBuf, ToggleAdaptationPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> buf.writeUtf(payload.concept),
            buf -> new ToggleAdaptationPayload(buf.readUtf()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void send(String concept) {
        PacketDistributor.sendToServer(new ToggleAdaptationPayload(concept));
    }

    public static void handle(ToggleAdaptationPayload payload, ServerPlayer player) {
        String concept = payload.concept();
        if (concept == null || concept.isBlank() || concept.length() > 128) {
            return;
        }
        PlayerAdaption data = AdaptionEvents.dataOf(player);
        if (!data.isAdapted(concept) && data.level(concept) <= 0) {
            return;
        }
        data.toggleEnabled(concept);
        AdaptionEvents.syncAdaption(player, data, AdaptionEvents.isWearingWheel(player));
    }
}
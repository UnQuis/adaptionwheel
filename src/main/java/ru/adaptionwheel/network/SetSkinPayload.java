package ru.adaptionwheel.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import ru.adaptionwheel.AdaptionWheel;
import ru.adaptionwheel.data.ModDataComponents;
import ru.adaptionwheel.data.WheelSkins;
import ru.adaptionwheel.server.AdaptionEvents;

public record SetSkinPayload(String skin) implements CustomPacketPayload {

    public static final Type<SetSkinPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AdaptionWheel.MODID, "set_skin"));

    public static final StreamCodec<FriendlyByteBuf, SetSkinPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> buf.writeUtf(payload.skin),
            buf -> new SetSkinPayload(buf.readUtf()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void send(String skin) {
        PacketDistributor.sendToServer(new SetSkinPayload(skin));
    }

    public static void handle(SetSkinPayload payload, ServerPlayer player) {
        if (!WheelSkins.isValid(payload.skin())) {
            return;
        }
        AdaptionEvents.getWheelStack(player)
                .ifPresent(stack -> stack.set(ModDataComponents.WHEEL_SKIN.get(), payload.skin()));
    }
}

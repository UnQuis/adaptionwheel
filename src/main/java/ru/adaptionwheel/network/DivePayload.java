package ru.adaptionwheel.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import ru.adaptionwheel.AdaptionWheel;
import ru.adaptionwheel.server.DiveAbility;

/** Client -&gt; server: the player pressed the Dive key. The server owns every condition. */
public record DivePayload() implements CustomPacketPayload {

    public static final DivePayload INSTANCE = new DivePayload();

    public static final Type<DivePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AdaptionWheel.MODID, "dive"));

    public static final StreamCodec<FriendlyByteBuf, DivePayload> STREAM_CODEC =
            StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void send() {
        PacketDistributor.sendToServer(INSTANCE);
    }

    public static void handle(DivePayload payload, ServerPlayer player) {
        DiveAbility.start(player);
    }
}

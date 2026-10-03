package ru.adaptionwheel.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import ru.adaptionwheel.AdaptionWheel;

public record FireSlashPayload() implements CustomPacketPayload {

    public static final FireSlashPayload INSTANCE = new FireSlashPayload();

    public static final Type<FireSlashPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(AdaptionWheel.MODID, "fire_slash"));

    public static final StreamCodec<FriendlyByteBuf, FireSlashPayload> STREAM_CODEC =
            StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void send() {
        net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(INSTANCE);
    }

    public static void handle(FireSlashPayload payload, ServerPlayer player) {
        ru.adaptionwheel.server.SwordOfExterminationHandler.tryFireSlashes(player);
    }
}

package ru.adaptionwheel.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import ru.adaptionwheel.AdaptionWheel;
import ru.adaptionwheel.menu.TradeMenu;

public record TradeActionPayload(Action action, int value) implements CustomPacketPayload {

    public enum Action {

        SELECT,

        EXCHANGE
    }

    public static final Type<TradeActionPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(AdaptionWheel.MODID, "trade_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TradeActionPayload> STREAM_CODEC =
            StreamCodec.of((buf, payload) -> {
                        buf.writeByte(payload.action.ordinal());
                        buf.writeVarInt(payload.value);
                    },
                    buf -> {

                        Action[] actions = Action.values();
                        int ordinal = buf.readByte() & 0xFF;
                        Action action = ordinal < actions.length ? actions[ordinal] : Action.EXCHANGE;
                        return new TradeActionPayload(action, buf.readVarInt());
                    });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void select(int index) {
        ClientPacketDistributor.sendToServer(new TradeActionPayload(Action.SELECT, index));
    }

    public static void exchange() {
        ClientPacketDistributor.sendToServer(new TradeActionPayload(Action.EXCHANGE, 0));
    }

    public static void handle(TradeActionPayload payload, ServerPlayer player) {
        if (!(player.containerMenu instanceof TradeMenu menu)) {
            return;
        }
        switch (payload.action()) {
            case SELECT -> menu.select(payload.value());
            case EXCHANGE -> menu.exchange();
        }
    }
}

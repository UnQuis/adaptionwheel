package ru.adaptionwheel.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import ru.adaptionwheel.AdaptionWheel;
import ru.adaptionwheel.menu.TradeMenu;

/**
 * Client→server: what the player wants at the Domain Stone.
 *
 * <p>Two intentions. The client never says "the row is 2 and I have 5 levels" as a claim about the
 * world — it says "row 2" and "exchange" as things it did, and the server decides what they mean.
 * Sending a state blob instead would mean trusting a client to describe the pool the server already
 * knows, and the whole reason the menu keeps its state server-side is that a row index goes stale
 * the moment the pool changes.</p>
 *
 * <p>Experience is not in here either: it is charged from the live player, not from anything a
 * client said.</p>
 */
public record TradeActionPayload(Action action, int value) implements CustomPacketPayload {

    public enum Action {
        /** Move the selection to a row. {@code value} is the index, or -1 to clear it. */
        SELECT,
        /** Perform the exchange. {@code value} is unused. */
        EXCHANGE
    }

    public static final Type<TradeActionPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AdaptionWheel.MODID, "trade_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TradeActionPayload> STREAM_CODEC =
            StreamCodec.of((buf, payload) -> {
                        buf.writeByte(payload.action.ordinal());
                        buf.writeVarInt(payload.value);
                    },
                    buf -> {
                        // Read through the array rather than values()[ordinal] on the client's
                        // word: a mismatched build would otherwise throw ArrayIndexOutOfBounds on the
                        // first click instead of failing to decode.
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
        PacketDistributor.sendToServer(new TradeActionPayload(Action.SELECT, index));
    }

    public static void exchange() {
        PacketDistributor.sendToServer(new TradeActionPayload(Action.EXCHANGE, 0));
    }

    /**
     * Runs the action against the open menu.
     *
     * <p>The type check is the whole of the validation that matters here: a player can send this
     * packet at any time, including with a different screen open or none at all, and an
     * {@code instanceof} on a container menu is what stops a stray packet from reaching a menu that
     * has nothing to do with the stone.</p>
     */
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
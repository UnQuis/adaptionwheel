package ru.adaptionwheel.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import ru.adaptionwheel.AdaptionWheel;
import ru.adaptionwheel.menu.TradeMenu;

import java.util.ArrayList;
import java.util.List;

/**
 * Server→client: the Domain Stone's whole screen state in one packet.
 *
 * <p>Three things cross: the candidate list, which row is selected, and what the trade costs in
 * items. The first two travel together because they are one decision — a row index is meaningless
 * without the list it indexes — and the screen draws a selection it cannot name otherwise.</p>
 *
 * <p>The item cost is here for the same reason: it is not a thing the client can work out. The
 * altar's price is "which mobs drop this item", read out of the server's resources, so a client that
 * asked would answer zero and never light the EXCHANGE button. Sent, not asked.</p>
 *
 * <p>The player's experience is deliberately <em>not</em> here. It is a number the client already
 * has and keeps up to date itself, and sending it would be a second source of truth for a value
 * that changes on the client's own.</p>
 *
 * <p>This is deliberately <em>not</em> a {@link net.minecraft.world.inventory.DataSlot}. A
 * {@code DataSlot} is one int, the pool is a variable-length list of arbitrary concept keys, and the
 * sync would then be two unrelated mechanisms with two unrelated failure modes.</p>
 */
public record TradeSyncPayload(List<String> candidates, int selectedIndex, int itemCost)
        implements CustomPacketPayload {

    public static final Type<TradeSyncPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(AdaptionWheel.MODID, "trade_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TradeSyncPayload> STREAM_CODEC =
            StreamCodec.of((buf, payload) -> {
                buf.writeVarInt(payload.candidates.size());
                for (String concept : payload.candidates) {
                    buf.writeUtf(concept, 256);
                }
                buf.writeVarInt(payload.selectedIndex);
                buf.writeVarInt(payload.itemCost);
            }, buf -> {
                int size = buf.readVarInt();
                List<String> candidates = new ArrayList<>(Math.max(0, Math.min(size, 256)));
                for (int i = 0; i < size; i++) {
                    candidates.add(buf.readUtf(256));
                }
                int selectedIndex = buf.readVarInt();
                return new TradeSyncPayload(candidates, selectedIndex, buf.readVarInt());
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void send(ServerPlayer player, TradeMenu menu) {
        PacketDistributor.sendToPlayer(player,
                new TradeSyncPayload(menu.candidates(), menu.selectedIndex(), menu.itemCost()));
    }

    /** Applies a sync to the open menu, if it is still the stone's. */
    public static void apply(TradeSyncPayload payload) {
        if (net.minecraft.client.Minecraft.getInstance().player instanceof net.minecraft.client.player.LocalPlayer player
                && player.containerMenu instanceof TradeMenu menu) {
            menu.acceptSync(payload.candidates(), payload.selectedIndex(), payload.itemCost());
        }
    }
}
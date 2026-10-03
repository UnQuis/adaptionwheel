package ru.adaptionwheel.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import ru.adaptionwheel.AdaptionWheel;
import ru.adaptionwheel.menu.TradeMenu;
import ru.adaptionwheel.menu.TradeMenu.TradePrice;

import java.util.ArrayList;
import java.util.List;

/**
 * Server→client: the Domain Stone's whole screen state in one packet.
 *
 * <p>Four things cross: the candidate list, one price per row, which row is selected, and whether
 * the altar takes the offering at all. The list and the selection travel together because they are
 * one decision — a row index is meaningless without the list it indexes — and the screen draws a
 * selection it cannot name otherwise.</p>
 *
 * <p>The prices are here because they are not a thing the client can work out, twice over. The item
 * half is "which mobs drop this item", read out of the server's resources, and the level half
 * depends on how many levels the <em>fed wheel</em> already holds — state the client does not have,
 * because the wheel in the slot is a menu stack and not the player's attachment. Sent, not asked,
 * and one price per row rather than one for the whole screen because a level's price depends on
 * which row it is.</p>
 *
 * <p>Whether the altar takes the item at all is here so an empty list can say which of the two very
 * different things happened: a wrong item, or a wheel that has already learned everything this
 * item opens.</p>
 *
 * <p>The player's experience is deliberately <em>not</em> here. It is a number the client already
 * has and keeps up to date itself, and sending it would be a second source of truth for a value
 * that changes on the client's own.</p>
 *
 * <p>This is deliberately <em>not</em> a {@link net.minecraft.world.inventory.DataSlot}. A
 * {@code DataSlot} is one int, the pool is a variable-length list of arbitrary concept keys, and the
 * sync would then be two unrelated mechanisms with two unrelated failure modes.</p>
 */
public record TradeSyncPayload(List<String> candidates, List<TradePrice> prices, int selectedIndex,
                                boolean offeringAccepted) implements CustomPacketPayload {

    public static final Type<TradeSyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AdaptionWheel.MODID, "trade_sync"));

    /** Upper bound on the candidate list, and so on the price list that travels beside it. */
    private static final int MAX_ROWS = 256;

    public static final StreamCodec<RegistryFriendlyByteBuf, TradeSyncPayload> STREAM_CODEC =
            StreamCodec.of((buf, payload) -> {
                buf.writeVarInt(payload.candidates.size());
                for (String concept : payload.candidates) {
                    buf.writeUtf(concept, 256);
                }
                // One price per row, in the row's own order. A price list shorter than the candidate
                // list would be read as "the rest cost nothing", which is the one answer that must
                // never be guessed, so the two are written together and read together.
                buf.writeVarInt(payload.prices.size());
                for (TradePrice price : payload.prices) {
                    buf.writeVarInt(price.items());
                    buf.writeVarInt(price.xp());
                }
                buf.writeVarInt(payload.selectedIndex);
                buf.writeBoolean(payload.offeringAccepted);
            }, buf -> {
                int size = buf.readVarInt();
                List<String> candidates = new ArrayList<>(Math.max(0, Math.min(size, MAX_ROWS)));
                for (int i = 0; i < size; i++) {
                    candidates.add(buf.readUtf(256));
                }
                int priceCount = buf.readVarInt();
                List<TradePrice> prices = new ArrayList<>(Math.max(0, Math.min(priceCount, MAX_ROWS)));
                for (int i = 0; i < priceCount; i++) {
                    prices.add(new TradePrice(buf.readVarInt(), buf.readVarInt()));
                }
                int selectedIndex = buf.readVarInt();
                return new TradeSyncPayload(candidates, prices, selectedIndex, buf.readBoolean());
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void send(ServerPlayer player, TradeMenu menu) {
        PacketDistributor.sendToPlayer(player, new TradeSyncPayload(menu.candidates(),
                menu.prices(), menu.selectedIndex(), menu.isOfferingAccepted()));
    }

    /** Applies a sync to the open menu, if it is still the altar's. */
    public static void apply(TradeSyncPayload payload) {
        if (net.minecraft.client.Minecraft.getInstance().player instanceof net.minecraft.client.player.LocalPlayer player
                && player.containerMenu instanceof TradeMenu menu) {
            menu.acceptSync(payload.candidates(), payload.prices(), payload.selectedIndex(),
                    payload.offeringAccepted());
        }
    }
}
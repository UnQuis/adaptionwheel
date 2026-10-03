package ru.adaptionwheel.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import ru.adaptionwheel.AdaptionWheel;
import ru.adaptionwheel.menu.TradeMenu;
import ru.adaptionwheel.menu.TradeMenu.TradePrice;

import java.util.ArrayList;
import java.util.List;

public record TradeSyncPayload(List<String> candidates, List<TradePrice> prices, int selectedIndex,
                                boolean offeringAccepted) implements CustomPacketPayload {

    public static final Type<TradeSyncPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(AdaptionWheel.MODID, "trade_sync"));

    private static final int MAX_ROWS = 256;

    public static final StreamCodec<RegistryFriendlyByteBuf, TradeSyncPayload> STREAM_CODEC =
            StreamCodec.of((buf, payload) -> {
                buf.writeVarInt(payload.candidates.size());
                for (String concept : payload.candidates) {
                    buf.writeUtf(concept, 256);
                }

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

    public static void apply(TradeSyncPayload payload) {
        if (net.minecraft.client.Minecraft.getInstance().player instanceof net.minecraft.client.player.LocalPlayer player
                && player.containerMenu instanceof TradeMenu menu) {
            menu.acceptSync(payload.candidates(), payload.prices(), payload.selectedIndex(),
                    payload.offeringAccepted());
        }
    }
}

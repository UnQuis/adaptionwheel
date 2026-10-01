package ru.adaptionwheel.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import ru.adaptionwheel.AdaptionWheel;
import ru.adaptionwheel.menu.TradeMenu;

import java.util.ArrayList;
import java.util.List;

/**
 * Server→client: the Domain Stone's whole screen state in one packet.
 *
 * <p>Two things cross: the candidate list and which row is selected. They travel together because
 * they are one decision — a row index is meaningless without the list it indexes — and the screen
 * draws a selection it cannot name otherwise.</p>
 *
 * <p>The player's experience is deliberately <em>not</em> here. It is a number the client already
 * has and keeps up to date itself, and sending it would be a second source of truth for a value
 * that changes on the client's own.</p>
 *
 * <p>This is deliberately <em>not</em> a {@link net.minecraft.world.inventory.DataSlot}. A
 * {@code DataSlot} is one int, the pool is a variable-length list of arbitrary concept keys, and the
 * sync would then be two unrelated mechanisms with two unrelated failure modes.</p>
 */
public record TradeSyncPayload(List<String> candidates, int selectedIndex)
        implements CustomPacketPayload {

    public static final Type<TradeSyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AdaptionWheel.MODID, "domain_stone_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TradeSyncPayload> STREAM_CODEC =
            StreamCodec.of((buf, payload) -> {
                buf.writeVarInt(payload.candidates.size());
                for (String concept : payload.candidates) {
                    buf.writeUtf(concept, 256);
                }
                buf.writeVarInt(payload.selectedIndex);
            }, buf -> {
                int size = buf.readVarInt();
                List<String> candidates = new ArrayList<>(Math.max(0, Math.min(size, 256)));
                for (int i = 0; i < size; i++) {
                    candidates.add(buf.readUtf(256));
                }
                return new TradeSyncPayload(candidates, buf.readVarInt());
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void send(ServerPlayer player, TradeMenu menu) {
        PacketDistributor.sendToPlayer(player,
                new TradeSyncPayload(menu.candidates(), menu.selectedIndex()));
    }

    /** Applies a sync to the open menu, if it is still the stone's. */
    public static void apply(TradeSyncPayload payload) {
        if (net.minecraft.client.Minecraft.getInstance().player instanceof net.minecraft.client.player.LocalPlayer player
                && player.containerMenu instanceof TradeMenu menu) {
            menu.acceptSync(payload.candidates(), payload.selectedIndex());
        }
    }
}
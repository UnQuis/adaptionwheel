package ru.adaptionwheel.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import ru.adaptionwheel.AdaptionWheel;
import ru.adaptionwheel.menu.DomainStoneMenu;

import java.util.ArrayList;
import java.util.List;

/**
 * Server→client: the Domain Stone's whole screen state in one packet.
 *
 * <p>Three things have to cross: the candidate list, which row is selected and how many levels are
 * wanted. They travel together because they are one decision — a row index is meaningless without
 * the list it indexes, and a level count is meaningless without the row that caps it — and a client
 * that received them separately could draw a slider for a row it does not have.</p>
 *
 * <p>This is deliberately <em>not</em> a {@link net.minecraft.world.inventory.DataSlot}. A
 * {@code DataSlot} is one int, the pool is a variable-length list of arbitrary concept keys, and the
 * sync would then be three unrelated mechanisms with three unrelated failure modes.</p>
 */
public record DomainStoneSyncPayload(List<String> candidates, int selectedIndex, int levels)
        implements CustomPacketPayload {

    public static final Type<DomainStoneSyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AdaptionWheel.MODID, "domain_stone_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DomainStoneSyncPayload> STREAM_CODEC =
            StreamCodec.of((buf, payload) -> {
                buf.writeVarInt(payload.candidates.size());
                for (String concept : payload.candidates) {
                    buf.writeUtf(concept, 256);
                }
                buf.writeVarInt(payload.selectedIndex);
                buf.writeVarInt(payload.levels);
            }, buf -> {
                int size = buf.readVarInt();
                List<String> candidates = new ArrayList<>(Math.max(0, Math.min(size, 256)));
                for (int i = 0; i < size; i++) {
                    candidates.add(buf.readUtf(256));
                }
                return new DomainStoneSyncPayload(candidates, buf.readVarInt(), buf.readVarInt());
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void send(ServerPlayer player, DomainStoneMenu menu) {
        PacketDistributor.sendToPlayer(player,
                new DomainStoneSyncPayload(menu.candidates(), menu.selectedIndex(), menu.levels()));
    }

    /** Applies a sync to the open menu, if it is still the stone's. */
    public static void apply(DomainStoneSyncPayload payload) {
        if (net.minecraft.client.Minecraft.getInstance().player instanceof net.minecraft.client.player.LocalPlayer player
                && player.containerMenu instanceof DomainStoneMenu menu) {
            menu.acceptSync(payload.candidates(), payload.selectedIndex(), payload.levels());
        }
    }
}
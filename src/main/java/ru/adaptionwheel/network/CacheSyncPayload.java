package ru.adaptionwheel.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import ru.adaptionwheel.AdaptionWheel;
import ru.adaptionwheel.client.ClientCache;

import java.util.List;

/**
 * Server -&gt; client: the contents of the adapted player's cache.
 */
public record CacheSyncPayload(List<ItemStack> stacks) implements CustomPacketPayload {

    public static final Type<CacheSyncPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(AdaptionWheel.MODID, "cache_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CacheSyncPayload> STREAM_CODEC =
            ItemStack.OPTIONAL_LIST_STREAM_CODEC.map(CacheSyncPayload::new, CacheSyncPayload::stacks);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void send(ServerPlayer player, List<ItemStack> stacks) {
        PacketDistributor.sendToPlayer(player, new CacheSyncPayload(List.copyOf(stacks)));
    }

    public static void apply(CacheSyncPayload payload) {
        ClientCache.set(payload.stacks());
    }
}

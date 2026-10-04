package ru.adaptionwheel.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import ru.adaptionwheel.AdaptionWheel;
import ru.adaptionwheel.category.Concepts;
import ru.adaptionwheel.config.AdaptionConfig;
import ru.adaptionwheel.menu.CacheMenuProvider;
import ru.adaptionwheel.server.AdaptionEvents;
import ru.adaptionwheel.server.CacheService;

public record OpenCachePayload() implements CustomPacketPayload {

    public static final OpenCachePayload INSTANCE = new OpenCachePayload();

    public static final Type<OpenCachePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AdaptionWheel.MODID, "open_cache"));

    public static final StreamCodec<FriendlyByteBuf, OpenCachePayload> STREAM_CODEC =
            StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void send() {
        PacketDistributor.sendToServer(INSTANCE);
    }

    public static void handle(OpenCachePayload payload, ServerPlayer player) {
        var data = AdaptionEvents.dataOf(player);
        if (!AdaptionConfig.ENABLE_INVENTORY_ADAPTATION.get()
                || !data.isEnabled(Concepts.ENV_INVENTORY)
                || !data.active(Concepts.ENV_INVENTORY)
                || player.containerMenu != player.inventoryMenu) {
            return;
        }
        CacheService.pad(data);
        player.openMenu(new CacheMenuProvider());
    }
}
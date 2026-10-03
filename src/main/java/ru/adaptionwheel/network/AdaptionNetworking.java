package ru.adaptionwheel.network;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import ru.adaptionwheel.AdaptionWheel;
import ru.adaptionwheel.client.ClientAdaption;

@EventBusSubscriber(modid = AdaptionWheel.MODID)
public class AdaptionNetworking {

    @SubscribeEvent
    public static void register(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(AdaptionSyncPayload.TYPE, AdaptionSyncPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientAdaption.onSync(payload)));
        registrar.playToClient(FistProgressPayload.TYPE, FistProgressPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> FistProgressPayload.apply(payload)));
        registrar.playToClient(TradeSyncPayload.TYPE, TradeSyncPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> TradeSyncPayload.apply(payload)));
        registrar.playToServer(TradeActionPayload.TYPE, TradeActionPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        TradeActionPayload.handle(payload, (net.minecraft.server.level.ServerPlayer) context.player())));
        registrar.playToServer(FistInstabreakPayload.TYPE, FistInstabreakPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        FistInstabreakPayload.handle(payload, (net.minecraft.server.level.ServerPlayer) context.player())));
        registrar.playToServer(FireSlashPayload.TYPE, FireSlashPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        FireSlashPayload.handle(payload, (net.minecraft.server.level.ServerPlayer) context.player())));
    }
}

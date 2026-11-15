package ru.adaptionwheel.server;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import ru.adaptionwheel.AdaptionWheel;
import ru.adaptionwheel.category.Concepts;
import ru.adaptionwheel.config.AdaptionConfig;

/**
 * Hands the client its cache mirror whenever a container opens.
 */
@EventBusSubscriber(modid = AdaptionWheel.MODID)
public final class CacheContainerSync {

    private CacheContainerSync() {
    }

    @SubscribeEvent
    public static void onOpen(PlayerContainerEvent.Open event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!AdaptionConfig.ENABLE_INVENTORY_ADAPTATION.get()) {
            return;
        }
        var data = AdaptionEvents.dataOf(player);
        if (!data.active(Concepts.ENV_INVENTORY)) {
            return;
        }
        CacheService.send(player, data);
    }
}

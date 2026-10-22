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
 *
 * <p>The overlay is drawn from that mirror, and a container opening is exactly when it is about to
 * be drawn — so this is both the refresh point and the only place the client learns the contents.
 * Sending it on a timer would push fifty-odd stacks every second for a screen the player may not
 * even have open.
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

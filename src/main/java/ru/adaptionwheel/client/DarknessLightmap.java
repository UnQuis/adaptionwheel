package ru.adaptionwheel.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import ru.adaptionwheel.AdaptionWheel;
import ru.adaptionwheel.category.Concepts;
import ru.adaptionwheel.config.AdaptionConfig;

@EventBusSubscriber(modid = AdaptionWheel.MODID, value = Dist.CLIENT)
public final class DarknessLightmap {

    private static boolean active;

    private static float floor;

    private DarknessLightmap() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {

        boolean wanted = AdaptionConfig.DARKNESS_LIGHTMAP_ENABLED.get()
                && ClientAdaption.wearingWheel
                && ClientAdaption.ADAPTED.contains(Concepts.ENV_DARKNESS);
        if (!wanted) {
            active = false;
            return;
        }
        if (!active) {
            floor = AdaptionConfig.DARKNESS_LIGHTMAP_FLOOR.get().floatValue();
        }
        active = true;
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {

        active = false;
    }

    public static float intensity() {
        return active ? floor : 0.0F;
    }

    public static boolean isActive() {
        return active;
    }
}

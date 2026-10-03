package ru.adaptionwheel.client;

import net.minecraft.client.Minecraft;
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

    private static int floorMax = 182;

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
            floorMax = Math.max(0, Math.min(255, Math.round(floor * 255.0F)));
        }
        active = true;
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {

        active = false;
    }

    public static int lift(int argb) {
        if (!active || floorMax <= 0) {
            return argb;
        }
        int a = argb >>> 24 & 0xFF;
        int r = argb >> 16 & 0xFF;
        int g = argb >> 8 & 0xFF;
        int b = argb & 0xFF;
        int max = Math.max(r, Math.max(g, b));
        if (max >= floorMax) {
            return argb;
        }
        if (max == 0) {

            return a << 24 | floorMax << 16 | floorMax << 8 | floorMax;
        }

        float scale = (float) floorMax / max;
        int nr = Math.min(255, Math.round(r * scale));
        int ng = Math.min(255, Math.round(g * scale));
        int nb = Math.min(255, Math.round(b * scale));
        return a << 24 | nr << 16 | ng << 8 | nb;
    }

    public static boolean isActive() {
        return active;
    }
}

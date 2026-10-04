package ru.adaptionwheel.client;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import ru.adaptionwheel.AdaptionWheel;
import ru.adaptionwheel.SurfaceAdaptations;
import ru.adaptionwheel.network.OpenCachePayload;

@EventBusSubscriber(modid = AdaptionWheel.MODID, value = Dist.CLIENT)
public final class AdaptionScreenOpener {

    private AdaptionScreenOpener() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        while (AdaptionKeybinds.OPEN_SCREEN_KEY.consumeClick()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && mc.gui.screen() == null && ClientAdaption.wearingWheel) {
                mc.gui.setScreen(new AdaptationScreen());
            } else if (mc.gui.screen() instanceof AdaptationScreen screen) {
                screen.onClose();
            }
        }

        while (AdaptionKeybinds.TOGGLE_INSTABREAK_KEY.consumeClick()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || !ClientAdaption.wearingWheel) {
                return;
            }
            if (!SurfaceAdaptations.instabreakUnlocked(mc.player)) {
                return;
            }

            ClientAdaption.instabreakActive = !ClientAdaption.instabreakActive;
            ru.adaptionwheel.network.FistInstabreakPayload.send(ClientAdaption.instabreakActive);
        }

        while (AdaptionKeybinds.OPEN_CACHE_KEY.consumeClick()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.gui.screen() != null) {
                continue;
            }
            if (ClientAdaption.active(ru.adaptionwheel.category.Concepts.ENV_INVENTORY)) {
                OpenCachePayload.send();
            }
        }
    }
}

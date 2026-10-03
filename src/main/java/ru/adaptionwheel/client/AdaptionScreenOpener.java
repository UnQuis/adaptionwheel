package ru.adaptionwheel.client;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import ru.adaptionwheel.AdaptionWheel;
import ru.adaptionwheel.SurfaceAdaptations;
import ru.adaptionwheel.network.FistInstabreakPayload;

@EventBusSubscriber(modid = AdaptionWheel.MODID, value = Dist.CLIENT)
public final class AdaptionScreenOpener {

    private AdaptionScreenOpener() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        while (AdaptionKeybinds.OPEN_SCREEN_KEY.consumeClick()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && mc.screen == null && ClientAdaption.wearingWheel) {
                mc.setScreen(new AdaptationScreen());
            } else if (mc.screen instanceof AdaptationScreen) {
                mc.screen.onClose();
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

            boolean next = !ClientAdaption.instabreakActive;
            ClientAdaption.instabreakActive = next;
            FistInstabreakPayload.send(next);
        }
    }
}

package ru.adaptionwheel.client;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import ru.adaptionwheel.AdaptionWheel;
import ru.adaptionwheel.SurfaceAdaptations;
import ru.adaptionwheel.ability.ActiveAbility;
import ru.adaptionwheel.category.Concepts;
import ru.adaptionwheel.network.AbilityActivatePayload;
import ru.adaptionwheel.network.FistInstabreakPayload;
import ru.adaptionwheel.network.OpenCachePayload;

@EventBusSubscriber(modid = AdaptionWheel.MODID, value = Dist.CLIENT)
public final class AdaptionScreenOpener {

    private AdaptionScreenOpener() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        while (AdaptionKeybinds.OPEN_SCREEN_KEY.consumeClick()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && mc.screen == null && ClientAdaption.wearingWheel) {
                mc.setScreen(new AdaptationWheelScreen());
            } else if (mc.screen instanceof AdaptationWheelScreen wheel) {
                if (!wheel.isSearchFocused()) {
                    wheel.onClose();
                }
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

        while (AdaptionKeybinds.OPEN_CACHE_KEY.consumeClick()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.screen != null) {
                continue;
            }
            if (ClientAdaption.active(Concepts.ENV_INVENTORY)) {
                OpenCachePayload.send();
            }
        }

        while (AdaptionKeybinds.ABILITY_KEY.consumeClick()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.screen != null || !ClientAdaption.wearingWheel) {
                continue;
            }
            ActiveAbility ability = AbilityAvailability.selected();
            if (ability == null || !ClientCooldowns.ready(ability.id())) {
                continue;
            }
            // Every ability-specific gate (ground, fluid, socket state, ...) belongs on the server:
            // the key only says "use whatever is in the slot".
            AbilityActivatePayload.send();
        }
    }
}

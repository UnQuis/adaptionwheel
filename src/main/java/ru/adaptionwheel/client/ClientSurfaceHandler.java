package ru.adaptionwheel.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import ru.adaptionwheel.AdaptionWheel;

@EventBusSubscriber(modid = AdaptionWheel.MODID, value = Dist.CLIENT)
public final class ClientSurfaceHandler {

    private ClientSurfaceHandler() {
    }

    @SubscribeEvent
    public static void onTickPost(PlayerTickEvent.Post event) {
        // Lava swimming is handled by LavaSwimMixin (travel path) for parity with the 1.21.1
        // implementation; the previous post-tick velocity hack is intentionally removed to avoid
        // double-applying movement and fighting vanilla/NeoForge travel physics on 26.3.
    }
}

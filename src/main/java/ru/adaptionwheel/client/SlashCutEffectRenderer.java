package ru.adaptionwheel.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import ru.adaptionwheel.AdaptionWheel;
import ru.adaptionwheel.client.slashcut.CapturaCorpo;
import ru.adaptionwheel.client.slashcut.Metades;
import ru.adaptionwheel.network.SlashCutPayload;

/** Drives the client-only cut halves from the level render and client tick. */
@EventBusSubscriber(modid = AdaptionWheel.MODID, value = Dist.CLIENT)
public final class SlashCutEffectRenderer {

    private SlashCutEffectRenderer() {
    }

    /** Called by networking when a {@code SlashCutPayload} arrives from the server. */
    public static void onCut(SlashCutPayload payload) {
        Metades.criar(payload);
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }
        Metades.desenhar(event.getPoseStack(), event.getPartialTick().getGameTimeDeltaPartialTick(false));
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Metades.tick();
        CapturaCorpo.prune();
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        Metades.limpar();
    }
}

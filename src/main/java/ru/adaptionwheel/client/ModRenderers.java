package ru.adaptionwheel.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import ru.adaptionwheel.AdaptionWheel;
import ru.adaptionwheel.client.render.slash.CursedSlashRenderer;
import ru.adaptionwheel.client.render.slash.SpatialRiftRenderer;
import ru.adaptionwheel.entity.ModEntities;

@EventBusSubscriber(modid = AdaptionWheel.MODID, value = Dist.CLIENT)
public final class ModRenderers {

    private ModRenderers() {
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.CURSED_SLASH.get(), CursedSlashRenderer::new);
        event.registerEntityRenderer(ModEntities.DISCIPLE.get(), DiscipleRenderer::new);
        event.registerEntityRenderer(ModEntities.SPATIAL_RIFT.get(), SpatialRiftRenderer::new);
    }
}

package ru.adaptionwheel.client;

import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import ru.adaptionwheel.AdaptionWheel;
import ru.adaptionwheel.entity.ModEntities;
import ru.adaptionwheel.item.ModItems;

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

    /**
     * The sword is a 3D blade drawn by its own renderer, not a baked item model: its placement is
     * relative to the arm, which a display transform cannot express.
     */
    @SubscribeEvent
    public static void onRegisterClientExtensions(RegisterClientExtensionsEvent event) {
        event.registerItem(new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return SwordOfExterminationRenderer.get();
            }
        }, ModItems.SWORD_OF_EXTERMINATION.get());
    }

    @SubscribeEvent
    public static void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(new SwordOfExterminationRenderer.MeshReloadListener());
    }
}

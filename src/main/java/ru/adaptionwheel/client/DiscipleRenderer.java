package ru.adaptionwheel.client;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import ru.adaptionwheel.AdaptionWheel;
import ru.adaptionwheel.entity.DiscipleEntity;

public class DiscipleRenderer extends MobRenderer<DiscipleEntity, DiscipleModel> {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(AdaptionWheel.MODID, "textures/entity/disciple.png");

    public DiscipleRenderer(EntityRendererProvider.Context context) {
        super(context, new DiscipleModel(context.bakeLayer(ModModelLayers.DISCIPLE)), 0.4F);
    }

    @Override
    public ResourceLocation getTextureLocation(DiscipleEntity entity) {
        return TEXTURE;
    }
}

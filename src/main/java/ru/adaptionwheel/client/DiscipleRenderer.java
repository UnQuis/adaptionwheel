package ru.adaptionwheel.client;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.resources.Identifier;
import ru.adaptionwheel.AdaptionWheel;
import ru.adaptionwheel.entity.DiscipleEntity;

public class DiscipleRenderer
        extends LivingEntityRenderer<DiscipleEntity, DiscipleRenderState, DiscipleModel> {

    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath(AdaptionWheel.MODID, "textures/entity/disciple.png");

    public DiscipleRenderer(EntityRendererProvider.Context context) {
        super(context, new DiscipleModel(context.bakeLayer(ModModelLayers.DISCIPLE)), 0.4F);
    }

    @Override
    public DiscipleRenderState createRenderState() {
        return new DiscipleRenderState();
    }

    @Override
    public Identifier getTextureLocation(DiscipleRenderState state) {
        return TEXTURE;
    }
}

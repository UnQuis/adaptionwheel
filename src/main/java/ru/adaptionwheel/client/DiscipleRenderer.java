package ru.adaptionwheel.client;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.resources.Identifier;
import ru.adaptionwheel.AdaptionWheel;
import ru.adaptionwheel.entity.DiscipleEntity;

/**
 * Renders a Disciple.
 *
 * <p>26.3's shape: three type parameters with a render state in the middle, a
 * {@code createRenderState()} to make the state, and {@code getTextureLocation} taking the state
 * rather than the entity because the renderer no longer has one. {@code LivingEntityRenderer} fills
 * the humanoid fields of the state in its own {@code extractRenderState}, so there is nothing to
 * override beyond the texture.</p>
 */
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

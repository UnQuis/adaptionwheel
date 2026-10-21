package ru.adaptionwheel.client;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import ru.adaptionwheel.entity.SpatialRiftProjectile;

/**
 * The visible rift blade is drawn by {@link SlashWorldRenderer}, deliberately after the
 * translucent pass so water cannot paint over it.
 */
public class SpatialRiftRenderer extends EntityRenderer<SpatialRiftProjectile> {

    public SpatialRiftRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(SpatialRiftProjectile entity) {
        return MissingTextureAtlasSprite.getLocation();
    }
}

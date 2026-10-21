package ru.adaptionwheel.client;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import ru.adaptionwheel.entity.CursedSlashProjectile;

/**
 * The visible blade is drawn by {@link SlashWorldRenderer} after the translucent block pass, so
 * this renderer only has to satisfy the entity-renderer contract and never paints from the entity
 * pass (which runs before water).
 */
public class CursedSlashRenderer extends EntityRenderer<CursedSlashProjectile> {

    public CursedSlashRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(CursedSlashProjectile entity) {
        return MissingTextureAtlasSprite.getLocation();
    }
}

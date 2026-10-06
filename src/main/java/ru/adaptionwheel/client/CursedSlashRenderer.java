package ru.adaptionwheel.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import ru.adaptionwheel.entity.CursedSlashProjectile;

public class CursedSlashRenderer extends EntityRenderer<CursedSlashProjectile> {

    private static final int[] BLADE_CYAN = {185, 245, 255};
    private static final int[] GLOW_CYAN = {38, 155, 255};

    public CursedSlashRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(CursedSlashProjectile entity) {
        // Never sampled: the blade is procedural. EntityRenderer declares this method but nothing
        // in the dispatch path calls it, and there is no longer a sprite to point at.
        return MissingTextureAtlasSprite.getLocation();
    }

    @Override
    public void render(CursedSlashProjectile entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffers, int light) {
        float age = entity.tickCount + partialTick;

        float fade = age < 24f ? 1f : Mth.clamp(1f - (age - 24f) / 12f, 0f, 1f);

        poseStack.pushPose();
        // No camera billboard: the blade now orients along its own flight path, and
        // flattening it to face the camera would erase the cross-section this mesh exists
        // to provide -- which is the whole reason it replaced the quad.
        FlyingSlashRenderer.render(poseStack, entity.getDeltaMovement(), entity.getRoll(), age,
                6.0f, 0.22f, 0.13f, BLADE_CYAN, GLOW_CYAN, fade);
        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, buffers, light);
    }
}

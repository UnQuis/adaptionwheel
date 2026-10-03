package ru.adaptionwheel.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import ru.adaptionwheel.entity.SpatialRiftProjectile;

public class SpatialRiftRenderer extends EntityRenderer<SpatialRiftProjectile> {

    private static final int[] BLADE_VIOLET = {242, 218, 255};
    private static final int[] GLOW_VIOLET = {155, 55, 255};

    public SpatialRiftRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(SpatialRiftProjectile entity) {
        return FlyingSlashRenderer.BLADE_TEXTURE;
    }

    @Override
    public void render(SpatialRiftProjectile entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffers, int light) {
        float age = entity.tickCount + partialTick;
        float lifeRatio = Mth.clamp(age / SpatialRiftProjectile.LIFETIME_TICKS, 0f, 1f);
        float fade = lifeRatio > 0.82f ? Mth.clamp((1f - lifeRatio) / 0.18f, 0f, 1f) : 1f;

        poseStack.pushPose();
        poseStack.mulPose(this.entityRenderDispatcher.camera.rotation());
        FlyingSlashRenderer.render(poseStack, buffers, entity.getDeltaMovement(), entity.getRoll(), age,
                9.0f, 7.5f, BLADE_VIOLET, GLOW_VIOLET, fade);
        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, buffers, light);
    }
}

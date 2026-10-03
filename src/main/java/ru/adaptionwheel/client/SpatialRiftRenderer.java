package ru.adaptionwheel.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import ru.adaptionwheel.entity.SpatialRiftProjectile;

public class SpatialRiftRenderer extends EntityRenderer<SpatialRiftProjectile, SlashRenderState> {

    private static final int[] BLADE_VIOLET = {242, 218, 255};
    private static final int[] GLOW_VIOLET = {155, 55, 255};

    public SpatialRiftRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public SlashRenderState createRenderState() {
        return new SlashRenderState();
    }

    @Override
    public void extractRenderState(SpatialRiftProjectile entity, SlashRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.motion = entity.getDeltaMovement();
        state.roll = entity.getRoll();
        state.age = entity.tickCount + partialTick;
    }

    @Override
    protected AABB getBoundingBoxForCulling(SpatialRiftProjectile entity, float partialTicks) {
        return super.getBoundingBoxForCulling(entity, partialTicks).inflate(8.0);
    }

    @Override
    public void submit(SlashRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
                       CameraRenderState camera) {
        float age = state.age;
        float lifeRatio = Mth.clamp(age / SpatialRiftProjectile.LIFETIME_TICKS, 0f, 1f);
        float fade = lifeRatio > 0.82f ? Mth.clamp((1f - lifeRatio) / 0.18f, 0f, 1f) : 1f;

        poseStack.pushPose();
        poseStack.rotate(camera.orientation);
        FlyingSlashRenderer.render(poseStack, collector, state.motion, state.roll, age,
                9.0f, 7.5f, BLADE_VIOLET, GLOW_VIOLET, fade);
        poseStack.popPose();
        super.submit(state, poseStack, collector, camera);
    }
}

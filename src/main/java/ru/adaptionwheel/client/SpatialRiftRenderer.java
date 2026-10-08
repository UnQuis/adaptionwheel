package ru.adaptionwheel.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import ru.adaptionwheel.entity.SpatialRiftProjectile;

/** Renders spatial rifts with the larger swept-crescent model used on 1.21.1. */
public class SpatialRiftRenderer extends EntityRenderer<SpatialRiftProjectile, SlashRenderState> {

    private static final float SPAN = 9.0f;
    private static final float GLOW_HALF = 0.33f;
    private static final float CORE_HALF = 0.20f;
    private static final int TINT_VIOLET = 0xFFF2DAFF;
    private static final int GLOW_VIOLET = 0xFF9B37FF;

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

        FlyingSlashRenderer.render(poseStack, collector, state.motion, state.roll, age,
                SPAN, GLOW_HALF, CORE_HALF, TINT_VIOLET, GLOW_VIOLET, fade);
        super.submit(state, poseStack, collector, camera);
    }
}

package ru.adaptionwheel.client.render.slash;

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
    /** 1.21.1 {@code RIFT_BLADE}: near-black violet ink. */
    private static final int TINT_VIOLET = 0xFF180A26;
    /** 1.21.1 {@code RIFT_GLOW}: violet aura. */
    private static final int GLOW_VIOLET = 0xFFBC52FF;

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
        state.seed = entity.getId();
    }

    @Override
    protected AABB getBoundingBoxForCulling(SpatialRiftProjectile entity, float partialTicks) {
        return super.getBoundingBoxForCulling(entity, partialTicks).inflate(8.0);
    }

    @Override
    public void submit(SlashRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
                       CameraRenderState camera) {
        Vec3 motion = state.motion;
        float age = state.age;
        float lifeRatio = Mth.clamp(age / SpatialRiftProjectile.LIFETIME_TICKS, 0f, 1f);
        float fade = lifeRatio > 0.82f ? Mth.clamp((1f - lifeRatio) / 0.18f, 0f, 1f) : 1f;

        Vec3 dir = motion.lengthSqr() < 1.0e-8 ? new Vec3(0, 0, 1) : motion.normalize();
        for (int i = 2; i >= 1; i--) {
            poseStack.pushPose();
            Vec3 offset = dir.scale(-i * 0.75);
            poseStack.translate(offset.x, offset.y, offset.z);
            float echoAge = Math.max(0f, age - i * 1.25f);
            float echoFade = fade * (i == 1 ? 0.26f : 0.11f);
            FlyingSlashRenderer.render(poseStack, collector, motion, state.roll + i * 0.045f, echoAge,
                    SPAN * 0.92f, GLOW_HALF * 0.78f, CORE_HALF * 0.62f, TINT_VIOLET, GLOW_VIOLET,
                    echoFade, state.seed + i * 31);
            poseStack.popPose();
        }

        FlyingSlashRenderer.render(poseStack, collector, motion, state.roll, age,
                SPAN, GLOW_HALF, CORE_HALF, TINT_VIOLET, GLOW_VIOLET, fade, state.seed);
        super.submit(state, poseStack, collector, camera);
    }
}

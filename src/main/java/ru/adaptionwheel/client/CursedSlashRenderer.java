package ru.adaptionwheel.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import ru.adaptionwheel.entity.CursedSlashProjectile;

/** Renders cursed slashes with the same swept-crescent model as the 1.21.1 branch. */
public class CursedSlashRenderer extends EntityRenderer<CursedSlashProjectile, SlashRenderState> {

    private static final float SPAN = 6.0f;
    private static final float GLOW_HALF = 0.22f;
    private static final float CORE_HALF = 0.13f;
    private static final int TINT_CYAN = 0xFFB9F5FF;
    private static final int GLOW_CYAN = 0xFF269BFF;
    private static final float FADE_START = 24f;
    private static final float FADE_END = 36f;

    public CursedSlashRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public SlashRenderState createRenderState() {
        return new SlashRenderState();
    }

    @Override
    public void extractRenderState(CursedSlashProjectile entity, SlashRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.motion = entity.getDeltaMovement();
        state.roll = entity.getRoll();
        state.age = entity.tickCount + partialTick;
    }

    @Override
    protected AABB getBoundingBoxForCulling(CursedSlashProjectile entity, float partialTicks) {
        return super.getBoundingBoxForCulling(entity, partialTicks).inflate(8.0);
    }

    @Override
    public void submit(SlashRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
                       CameraRenderState camera) {
        Vec3 motion = state.motion;
        float age = state.age;
        float fade = age < FADE_START ? 1f : Mth.clamp(1f - (age - FADE_START) / (FADE_END - FADE_START), 0f, 1f);

        FlyingSlashRenderer.render(poseStack, collector, motion, state.roll, age,
                SPAN, GLOW_HALF, CORE_HALF, TINT_CYAN, GLOW_CYAN, fade);
        super.submit(state, poseStack, collector, camera);
    }
}

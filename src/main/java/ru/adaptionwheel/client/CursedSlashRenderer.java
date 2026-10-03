package ru.adaptionwheel.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import ru.adaptionwheel.entity.CursedSlashProjectile;

public class CursedSlashRenderer extends EntityRenderer<CursedSlashProjectile, SlashRenderState> {

    private static final int[] BLADE_CYAN = {185, 245, 255};
    private static final int[] GLOW_CYAN = {38, 155, 255};

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
        float age = state.age;

        float fade = age < 24f ? 1f : Mth.clamp(1f - (age - 24f) / 12f, 0f, 1f);

        poseStack.pushPose();
        poseStack.rotate(camera.orientation);
        FlyingSlashRenderer.render(poseStack, collector, state.motion, state.roll, age,
                6.0f, 5.0f, BLADE_CYAN, GLOW_CYAN, fade);
        poseStack.popPose();
        super.submit(state, poseStack, collector, camera);
    }
}

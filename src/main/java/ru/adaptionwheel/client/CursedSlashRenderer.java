package ru.adaptionwheel.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import ru.adaptionwheel.entity.CursedSlashProjectile;

/** Textured cursed-energy flying slash fired by the normal sword mode. */
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

    /**
     * The visual blade extends far beyond the 0.5-block entity hitbox; without an
     * inflated culling box the frustum test drops the entity while its glow is
     * still on screen.
     */
    @Override
    protected AABB getBoundingBoxForCulling(CursedSlashProjectile entity) {
        return super.getBoundingBoxForCulling(entity).inflate(8.0);
    }

    @Override
    public void submit(SlashRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
                       CameraRenderState camera) {
        float age = state.age;
        // Keep the visual alive for the full block-to-block flight, with only a
        // short fade at the end rather than turning invisible after 30 ticks.
        float fade = age < 24f ? 1f : Mth.clamp(1f - (age - 24f) / 12f, 0f, 1f);

        poseStack.pushPose();
        poseStack.mulPose(camera.orientation);
        FlyingSlashRenderer.render(poseStack, collector, state.motion, state.roll, age,
                6.0f, 5.0f, BLADE_CYAN, GLOW_CYAN, fade);
        poseStack.popPose();
        super.submit(state, poseStack, collector, camera);
    }
}

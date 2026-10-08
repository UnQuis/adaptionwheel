package ru.adaptionwheel.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import ru.adaptionwheel.client.render.ModRenderTypes;
import ru.adaptionwheel.client.render.SlashBladeMesh;
import ru.adaptionwheel.entity.CursedSlashProjectile;
import ru.adaptionwheel.client.render.SlashRenderState;

/**
 * Draws the blade as a swept volume aligned with its own flight.
 *
 * <p>It used to be a camera-facing quad built per vertex by {@link FlyingSlashRenderer}; it is now a
 * real solid crescent, swept along a bowed centreline and shaded by its own pipeline. The shape is
 * resolved on the CPU and only the screentone and the travelling strokes are per-pixel, which is
 * why the mesh hands the fragment shader a coreness value instead of a position to re-derive one
 * from.
 *
 * <p>The blade is oriented along its velocity rather than towards the camera. That is the whole
 * point of a solid mesh: a camera-facing flat blade has to swing edge-on as you circle it and
 * disappears to a line, whereas this one stays a blade from every angle.
 */
public class CursedSlashRenderer extends EntityRenderer<CursedSlashProjectile, SlashRenderState> {

    private static final float SPAN = 3.2f;
    private static final float BOW = 0.22f;
    private static final float GLOW_HALF = 0.40f;
    private static final float CORE_RATIO = 0.40f;
    private static final float TAPER_POWER = 1.35f;
    private static final float UNFURL_TICKS = 2.5f;

    /** Roll is clamped well inside the server's wider +/-60 degree range: that range was picked
     *  against a world-horizontal plate, and reads as a near-total flip once applied to a mesh
     *  aligned to its own flight direction instead -- the same reasoning the 1.21.1 branch's
     *  {@code SlashBladeMesh.viewFrame} documented for its own clamp, lost in the port until now. */
    private static final float MAX_ROLL_DEGREES = 30f;

    static {
        // Sanity-checked once at class load rather than every frame: if either constant above is
        // ever retuned, this fails loudly instead of letting the tube fold through itself in
        // SlashBladeMesh.build without any visible error -- see maxGlowHalf's javadoc.
        assert GLOW_HALF <= SlashBladeMesh.maxGlowHalf(SPAN, BOW)
                : "GLOW_HALF exceeds the curve's radius of curvature for this SPAN/BOW; "
                + "the swept tube would self-intersect";
    }

    private static final int TINT = 0xFFE6D9FF;
    private static final int GLOW = 0xD97330F2;

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
        if (motion.lengthSqr() < 1.0E-8) {
            return;
        }

        float age = state.age;
        float fade = age < FADE_START ? 1f : Mth.clamp(1f - (age - FADE_START) / (FADE_END - FADE_START), 0f, 1f);
        if (fade <= 0.01f) {
            return;
        }

        poseStack.pushPose();
        poseStack.rotate(Axis.YP, (float) Mth.atan2(motion.x, motion.z));
        poseStack.rotate(Axis.XP, (float) -Mth.atan2(motion.y, motion.horizontalDistance()));
        poseStack.rotateDegrees(Axis.ZP, Mth.clamp(state.roll, -MAX_ROLL_DEGREES, MAX_ROLL_DEGREES));

        collector.submitCustomGeometry(poseStack, ModRenderTypes.slash(), (pose, consumer) ->
                SlashBladeMesh.build(consumer, pose, SPAN, BOW, GLOW_HALF, CORE_RATIO, TAPER_POWER,
                        Mth.clamp(age / UNFURL_TICKS, 0f, 1f), fade(TINT, fade), fade(GLOW, fade)));

        poseStack.popPose();
        super.submit(state, poseStack, collector, camera);
    }

    private static int fade(int argb, float fade) {
        int alpha = Math.round((argb >>> 24) * Mth.clamp(fade, 0f, 1f));
        return (alpha << 24) | (argb & 0x00FFFFFF);
    }
}
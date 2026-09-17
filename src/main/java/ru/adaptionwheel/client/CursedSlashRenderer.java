package ru.adaptionwheel.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import ru.adaptionwheel.AdaptionWheel;
import ru.adaptionwheel.entity.CursedSlashProjectile;

/**
 * Cursed energy slash, v3 — anime blade arc in WORLD space.
 *
 * <p>The crescent lies in the horizontal swing plane (flight direction x world
 * up): points sweep an ellipse, the blade width follows a sharpened sine
 * profile and is offset RADIALLY, so tips are needles and the middle is fat.
 * Each layer is five nested strips with a gaussian alpha profile — additive
 * blending fakes a smooth cross-blade falloff without textures.</p>
 *
 * <p>No camera-billboard rotation is used: the dispatcher's pose stack is
 * already view-space, so building from world axes keeps the blade glued to the
 * swing plane from any camera angle (the v2 attempt mixed the two systems and
 * smeared quads across the screen).</p>
 */
public class CursedSlashRenderer extends EntityRenderer<CursedSlashProjectile> {

    private static final int ARC_POINTS = 26;

    /** Cross-blade fake-gaussian: offsets (fraction of layer width) and weights. */
    private static final float[] PROFILE_OFFSET = {-0.72f, -0.38f, 0f, 0.38f, 0.72f};
    private static final float[] PROFILE_ALPHA = {0.16f, 0.34f, 0.62f, 0.34f, 0.16f};

    // Layer palette (cursed energy): wide azure haze -> saturated cyan body -> white-hot core.
    private static final int[] BODY_DARK_RGB = {16, 10, 30};
    private static final int[] GLOW_RGB = {70, 150, 255};
    private static final int[] BODY_RGB = {110, 210, 255};
    private static final int[] CORE_RGB = {235, 250, 255};

    public CursedSlashRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(CursedSlashProjectile entity) {
        return ResourceLocation.fromNamespaceAndPath(AdaptionWheel.MODID, "textures/entity/wheel.png");
    }

    @Override
    public void render(CursedSlashProjectile entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffers, int light) {
        float age = entity.tickCount + partialTick;
        Vec3 motion = entity.getDeltaMovement();
        if (motion.lengthSqr() < 1.0E-8) {
            return;
        }
        poseStack.pushPose();
        // The dispatcher's pose stack is already in VIEW space; multiplying by
        // the camera rotation restores WORLD axes so the blade stays glued to
        // the flight path from any camera angle.
        poseStack.mulPose(this.entityRenderDispatcher.camera.rotation());

        // Anime framing: the blade sweeps ACROSS the flight path (horizontal
        // perpendicular), arcing vertically — never edge-on to the camera.
        Vec3 dir = motion.normalize();
        Vec3 side = dir.cross(new Vec3(0, 1, 0));
        if (side.lengthSqr() < 1.0E-6) {
            side = new Vec3(1, 0, 0);
        }
        side = side.normalize();
        Vec3 up = new Vec3(0, 1, 0);

        // The whole blade rolls around the flight axis as it travels.
        double roll = entity.getRoll();
        Quaternionf rollRot = new Quaternionf().rotationAxis((float) roll,
                (float) dir.x, (float) dir.y, (float) dir.z);
        Vector3f sideRot = rollRot.transform(new Vector3f(
                (float) side.x, (float) side.y, (float) side.z));
        Vector3f upRot = rollRot.transform(new Vector3f(0, 1, 0));
        Vec3 sideR = new Vec3(sideRot.x, sideRot.y, sideRot.z);
        Vec3 upR = new Vec3(upRot.x, upRot.y, upRot.z);

        Matrix4f matrix = poseStack.last().pose();

        float lifeRatio = Mth.clamp(age / 30f, 0f, 1f);
        float fade = lifeRatio >= 0.7f ? Math.max(0f, (1f - lifeRatio) / 0.3f) : 1f;
        float unfold = Mth.clamp(age / 4.5f, 0.12f, 1f);
        float shimmer = 0.86f + 0.14f * Mth.sin(age * 1.9f);

        Vec3 backStep = dir.scale(-0.55);
        // Three crescents crossed around the flight axis: from any camera
        // angle at least two planes are face-on, so the slash never collapses
        // into an invisible edge. A single faded echo trails behind.
        for (float planeAngle : new float[]{0f, 60f, -60f}) {
            Quaternionf planeRot = new Quaternionf().rotationAxis(
                    (float) Math.toRadians(planeAngle),
                    (float) dir.x, (float) dir.y, (float) dir.z);
            Vector3f e1r = planeRot.transform(new Vector3f(
                    (float) sideR.x, (float) sideR.y, (float) sideR.z));
            Vector3f e2r = planeRot.transform(new Vector3f(
                    (float) upR.x, (float) upR.y, (float) upR.z));
            Vec3 e1 = new Vec3(e1r.x, e1r.y, e1r.z);
            Vec3 e2 = new Vec3(e2r.x, e2r.y, e2r.z);
            drawBladeArc(buffers, matrix, e1, e2, Vec3.ZERO, unfold, 1f, shimmer, fade);
        }
        drawBladeArc(buffers, matrix, sideR, upR, backStep, unfold * 0.8f, 0.72f,
                shimmer * 0.75f, fade * 0.45f);

        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, buffers, light);
    }

    /**
     * One crescent: points sweep {@code ±sweepAngle/2} across the flight path
     * (along {@code side}) with a vertical bow ({@code up}); the whole echo
     * copy slides backwards along the flight direction by {@code shift}.
     * Width offsets are radial — perpendicular to the blade edge.
     */
    private void drawBladeArc(MultiBufferSource buffers, Matrix4f matrix,
                              Vec3 side, Vec3 up, Vec3 shift, float unfold, float scale, float shimmer, float fade) {
        if (fade <= 0.01f) {
            return;
        }
        double sweepAngle = Math.toRadians(150) * unfold;
        double baseRadius = 3.1 * scale;
        float maxWidth = 0.9f * scale;

        Vec3[] pts = new Vec3[ARC_POINTS];
        Vec3[] radial = new Vec3[ARC_POINTS];
        float[] widths = new float[ARC_POINTS];
        for (int i = 0; i < ARC_POINTS; i++) {
            float t = i / (float) (ARC_POINTS - 1);
            double ang = sweepAngle * (t - 0.5);
            double radius = baseRadius * (1.0 + 0.18 * t);
            Vec3 point = side.scale(Math.cos(ang) * radius).add(up.scale(Math.sin(ang) * radius * 0.4));
            pts[i] = point.add(shift);
            radial[i] = point.subtract(shift).normalize();
            float profile = (float) Math.pow(Math.sin(Math.PI * Math.min(1f, t * 1.08f)), 0.75);
            widths[i] = Math.max(0.02f, maxWidth * profile);
        }

        int glowA = (int) (48 * shimmer * fade);
        int bodyA = (int) (110 * shimmer * fade);
        int coreA = (int) (230 * shimmer * fade);

        // Dark blade body first (visible against the bright sky, like the
        // original's black slash), then additive glow and white-hot core.
        // Each layer fetches its own buffer: requesting a different render
        // type finalizes the previous buffer, so stale consumers would throw.
        layer(RenderType.debugQuads(), buffers, matrix, pts, radial, widths, 0.95f,
                BODY_DARK_RGB, (int) (235 * fade));
        layer(RenderType.lightning(), buffers, matrix, pts, radial, widths, 3.1f, GLOW_RGB, glowA);
        layer(RenderType.lightning(), buffers, matrix, pts, radial, widths, 1.4f, BODY_RGB, bodyA);
        layer(RenderType.lightning(), buffers, matrix, pts, radial, widths, 0.34f, CORE_RGB, coreA);
    }

    /** Five nested strips per layer approximating a smooth gaussian cross-falloff. */
    private static void layer(RenderType type, MultiBufferSource buffers, Matrix4f matrix,
                              Vec3[] pts, Vec3[] radial, float[] widths, float widen, int[] rgb, int alpha) {
        if (alpha <= 2) {
            return;
        }
        VertexConsumer consumer = buffers.getBuffer(type);
        for (int k = 0; k < PROFILE_OFFSET.length; k++) {
            int a = (int) (alpha * PROFILE_ALPHA[k]);
            if (a <= 2) {
                continue;
            }
            for (int i = 0; i < pts.length - 1; i++) {
                if (pts[i].subtract(pts[i + 1]).lengthSqr() < 1.0E-8) {
                    continue;
                }
                Vec3 off0 = radial[i].scale(widen * widths[i] * PROFILE_OFFSET[k]);
                Vec3 off1 = radial[i + 1].scale(widen * widths[i + 1] * PROFILE_OFFSET[k]);
                quad(consumer, matrix,
                        pts[i].add(off0), pts[i + 1].add(off1),
                        pts[i + 1].subtract(off1), pts[i].subtract(off0), a, rgb);
            }
        }
    }

    private static void quad(VertexConsumer consumer, Matrix4f matrix,
                             Vec3 a, Vec3 b, Vec3 c, Vec3 d, int alpha, int[] rgb) {
        // RenderType.lightning has back-face culling; emit both windings so
        // the blade is visible from either side.
        vertex(consumer, matrix, a, alpha, rgb);
        vertex(consumer, matrix, b, alpha, rgb);
        vertex(consumer, matrix, c, alpha, rgb);
        vertex(consumer, matrix, d, alpha, rgb);
        vertex(consumer, matrix, a, alpha, rgb);
        vertex(consumer, matrix, d, alpha, rgb);
        vertex(consumer, matrix, c, alpha, rgb);
        vertex(consumer, matrix, b, alpha, rgb);
    }

    private static void vertex(VertexConsumer consumer, Matrix4f matrix, Vec3 pos, int alpha, int[] rgb) {
        consumer.addVertex(matrix, (float) pos.x, (float) pos.y, (float) pos.z)
                .setColor(rgb[0], rgb[1], rgb[2], alpha);
    }
}

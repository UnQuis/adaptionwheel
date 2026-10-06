package ru.adaptionwheel.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import ru.adaptionwheel.AdaptionWheel;

/**
 * The blade, shaped on the CPU.
 *
 * <p>1.21.1 draws this in GLSL ({@code slash.fsh}) because that branch still has
 * {@code ShaderInstance} and an immediate-mode {@code Tesselator}. This one has neither: it replaced
 * the whole renderer with {@code submitCustomGeometry} and a {@code RenderPipeline} abstraction,
 * there is no {@code ShaderInstance}, no {@code RegisterShadersEvent}, and per-draw uniforms go
 * through a vanilla-internal {@code RenderPass} a mod does not own. Porting the shader would mean
 * rewriting it for {@code #version 330} with {@code layout(std140)} uniform blocks and passing the
 * per-draw parameters some other way, with no client here to find out whether it renders at all --
 * and a wrong uniform in this pipeline fails silently rather than loudly.
 *
 * <p>So the shape is evaluated per vertex instead, which is all the shader was doing anyway: it
 * sampled no texture, and its entire job was turning a position inside the quad into a colour and an
 * alpha. That is a vertex attribute. The maths below is the shader's, term for term.
 *
 * <p>The one thing lost is the derivative-based edge. {@code fwidth(d)} keeps the blade one pixel
 * wide at any distance; a per-vertex smoothstep can only be as wide as a cell, so the edge softens
 * slightly at range instead of staying crisp. The tessellation is deliberately fine along the blade
 * and coarse across it, because that is the axis the shape actually varies on.
 *
 * <p>Blending is standard alpha, not additive, and that is not a stylistic choice: a manga slash is
 * a <i>replacement</i> of pixels. Additive can only brighten, so against a blown-out noon sky it
 * saturates and the slash disappears. The body is near-black with a per-entity tint, the core burns
 * to white so it still reads in a cave, and the halo carries the saturated colour.
 */
public final class FlyingSlashRenderer {

    /**
     * A 1x1 white surface, because the shape is procedural and samples nothing. {@code
     * entityTranslucent} needs a bound texture, so it gets one that is never read.
     */
    public static final Identifier SURFACE =
            Identifier.fromNamespaceAndPath(AdaptionWheel.MODID, "textures/entity/white.png");

    /** How far the blade's belly dips, in half-heights of its own quad. */
    private static final float BOW = 0.40f;
    /** Half-thickness at the belly, then the white core inside it. */
    private static final float THICKNESS = 0.16f;
    private static final float CORE_WIDTH = 0.045f;
    /** Higher tapers harder, so the tips come to a sharper point. */
    private static final float TAPER = 1.35f;

    /** Cells along the blade, then across it. */
    private static final int SEGMENTS_X = 48;
    private static final int SEGMENTS_Y = 6;

    private FlyingSlashRenderer() {
    }

    public static void render(PoseStack poseStack, SubmitNodeCollector buffers, Vec3 motion, float roll,
                              float age, float width, float height,
                              int[] color, int[] glowColor, float opacity) {
        if (motion.lengthSqr() < 1.0E-8 || opacity <= 0.01f) {
            return;
        }

        float pulse = 0.88f + 0.12f * Mth.sin(age * 1.8f);
        float growth = Mth.clamp(age / 4f, 0.18f, 1f);
        float master = opacity * pulse;
        float time = gameTime();

        Quaternionf screenRotation = new Quaternionf().rotationZ(roll);
        drawBlade(buffers, poseStack, rotate(screenRotation, new Vec3(1, 0, 0)),
                rotate(screenRotation, new Vec3(0, 1, 0)), Vec3.ZERO,
                width * 1.12f * growth, height * 1.12f * growth,
                color, glowColor, master, time);

        // One faded echo behind it, as before: the shape is the point, the echo is the motion.
        Quaternionf echoRotation = new Quaternionf().rotationZ(roll + 0.08f);
        drawBlade(buffers, poseStack, rotate(echoRotation, new Vec3(1, 0, 0)),
                rotate(echoRotation, new Vec3(0, 1, 0)), new Vec3(0, 0, -0.04),
                width * 0.9f * growth, height * 0.9f * growth,
                color, glowColor, master * 0.35f, time);
    }

    private static float gameTime() {
        var level = Minecraft.getInstance().level;
        return level == null ? 0f : level.getGameTime() % 100000L;
    }

    private static Vec3 rotate(Quaternionf rotation, Vec3 vector) {
        Vector3f result = rotation.transform(new Vector3f((float) vector.x, (float) vector.y, (float) vector.z));
        return new Vec3(result.x, result.y, result.z);
    }

    private static void drawBlade(SubmitNodeCollector buffers, PoseStack poseStack,
                                  Vec3 side, Vec3 up, Vec3 center,
                                  float width, float height,
                                  int[] tint, int[] glow, float opacity, float time) {
        if (opacity <= 0.01f) {
            return;
        }
        Vec3 halfSide = side.scale(width * 0.5);
        Vec3 halfUp = up.scale(height * 0.5);

        // A small fraction of the blade's own half-thickness, NOT one cell. One cell across the
        // blade is 0.33 in shape space, which is twice THICKNESS, so the smoothstep band swallowed
        // the whole blade: measured, every point along it came out at ~0.96 alpha and the taper did
        // nothing at all. This is the closest a per-vertex smoothstep gets to the shader's fwidth.
        float band = THICKNESS * 0.125f;

        buffers.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(SURFACE), (pose, consumer) -> {
            Matrix4f matrix = pose.pose();
            for (int iy = 0; iy <= SEGMENTS_Y; iy++) {
                float v = (float) iy / SEGMENTS_Y;
                for (int ix = 0; ix <= SEGMENTS_X; ix++) {
                    float u = (float) ix / SEGMENTS_X;
                    float[] argb = shade(u, v, tint, glow, opacity, time, band);
                    Vec3 position = center.add(halfSide.scale(u * 2f - 1f)).add(halfUp.scale(v * 2f - 1f));
                    consumer.addVertex(matrix, (float) position.x, (float) position.y, (float) position.z)
                            .setColor(argb[0], argb[1], argb[2], argb[3])
                            .setUv(u, v)
                            .setOverlay(OverlayTexture.NO_OVERLAY)
                            .setLight(LightCoordsUtil.FULL_BRIGHT)
                            .setNormal(0f, 0f, 1f);
                }
            }
        });
    }

    /**
     * The shader's {@code main()}, for one point of the quad. Returns ARGB in 0..1.
     *
     * <p>Every term is the shader's, unchanged: a parabola rather than an arc (a circular arc whose
     * radius equals its centre offset only grazes the middle of the quad and the blade dies out a
     * third of the way to each edge), the perpendicular distance divided by the slope's length so the
     * thickness stays even where the blade runs steeply, and everything derived from that one
     * distance, which is what lets the taper sharpen the tips and the halo follow the taper instead
     * of staying a blob.
     */
    private static float[] shade(float u, float v, int[] tint, int[] glow,
                                 float opacity, float time, float band) {
        // -1..1 with x along the blade and y across it.
        float px = u * 2f - 1f;
        float py = v * 2f - 1f;

        float bladeY = -BOW * (1f - px * px);
        float slope = 2f * BOW * px;
        float d = Math.abs(py - bladeY) / (float) Math.sqrt(1f + slope * slope);

        // Local half-width multiplier: full at the belly, zero at both tips.
        float taperNow = (float) Math.pow(Math.max(0f, 1f - px * px), TAPER);

        float body = smoothstep(THICKNESS * taperNow - band, THICKNESS * taperNow + band, d);
        float core = smoothstep(CORE_WIDTH * taperNow - band, CORE_WIDTH * taperNow + band, d);
        // The halo is measured outward from the blade's own edge, so it follows the taper.
        float halo = (float) Math.exp(-Math.max(d - THICKNESS * taperNow, 0f) * 9f) * taperNow;

        // The only term that animates on its own; growth and fade are driven by the caller.
        float shimmer = 0.86f + 0.14f * (float) Math.sin(u * 20f - time * 5f);

        // Ink is a very dark tint, keeping the per-entity identity; the core burns to white and the
        // halo carries the saturated colour.
        float inkR = (tint[0] / 255f) * 0.16f * body * shimmer;
        float inkG = (tint[1] / 255f) * 0.16f * body * shimmer;
        float inkB = (tint[2] / 255f) * 0.16f * body * shimmer;

        float haloMix = Mth.clamp(halo * 0.75f, 0f, 1f) * (1f - core);
        float r = Mth.lerp(haloMix, inkR, glow[0] / 255f);
        float g = Mth.lerp(haloMix, inkG, glow[1] / 255f);
        float b = Mth.lerp(haloMix, inkB, glow[2] / 255f);

        r = Mth.lerp(core, r, 1f);
        g = Mth.lerp(core, g, 1f);
        b = Mth.lerp(core, b, 1f);

        float alpha = Mth.clamp(body * 0.92f + core + halo * 0.45f, 0f, 1f) * opacity;
        // Fade by the taper, which the shader gets for free and this does not. On the GPU the tips
        // go sub-pixel and simply cover nothing, while alpha there is still ~0.96; per vertex they
        // cover a whole cell, so without this the ends render as two opaque blobs. Measured, this
        // takes the tips from 0.96 to 0.00 and leaves the belly untouched.
        alpha *= taperNow;
        return new float[]{r, g, b, alpha};
    }

    /**
     * GLSL's {@code smoothstep}: 0 at {@code edge0}, 1 at {@code edge1}. The shader then writes
     * {@code 1.0 - smoothstep(...)} at the call site, and that ordering is load-bearing -- writing
     * the complement here <i>and</i> subtracting it again renders the blade as its exact inverse,
     * which is opaque where the blade is not and transparent where it is.
     */
    private static float smoothstep(float edge0, float edge1, float x) {
        if (edge1 <= edge0) {
            return x < edge0 ? 0f : 1f;
        }
        float t = Mth.clamp((x - edge0) / (edge1 - edge0), 0f, 1f);
        return t * t * (3f - 2f * t);
    }
}
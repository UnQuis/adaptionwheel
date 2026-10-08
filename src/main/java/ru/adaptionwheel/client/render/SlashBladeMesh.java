package ru.adaptionwheel.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import org.joml.Vector3f;

/**
 * The slash crescent as an actual swept volume rather than a flat billboard.
 *
 * <p>Ported from the 1.21.1 branch's class of the same name. The core maths -- a sine arch for the
 * centreline, {@code pow(arc, taperPower)} for the taper -- is unchanged. What moved is where the
 * mesh gets oriented and where it gets coloured:
 *
 * <ul>
 *   <li><b>Orientation.</b> 1.21.1 carried its own {@code viewFrame(roll)} because that branch's
 *       {@code FlyingSlashRenderer} aimed the blade with a hand-built quaternion off the
 *       projectile's velocity. On 26.3, {@link CursedSlashRenderer} already turns the pose stack
 *       to face the travel direction with the ordinary {@code Axis.YP}/{@code Axis.XP}/{@code
 *       Axis.ZP} rotations vanilla uses everywhere else, so the mesh no longer needs its own
 *       frame -- it is built with local +Z as "forward" and lets the caller's rotation do the
 *       aiming. The axis permutation below (sideways = local X, forward = local Z, thickness =
 *       local Y) is exactly what {@code viewFrame} used to perform; it now lives in how the
 *       centreline is laid out instead of in a matrix.</li>
 *   <li><b>Colour.</b> 1.21.1 baked only a coreness value into {@code UV.y} and left the actual
 *       colouring to a GLSL fragment shader ({@code slash.fsh} via {@code SlashShaderFX}). 26.3 has
 *       no {@code ShaderInstance} to draw that shader with (see this package's
 *       {@code FlyingSlashRenderer} for the full reasoning), so the ink/glow lerp the shader used
 *       to do per pixel is done once per vertex here and written straight into the vertex
 *       colour.</li>
 * </ul>
 *
 * <p>The two rules the 1.21.1 class called out as load-bearing carry over unchanged:
 *
 * <ul>
 *   <li>The coreness thresholds are divided by the local taper, so the cross-section profile is a
 *       pure function of theta and identical at every ring -- see {@link #crossSectionProfile}.</li>
 *   <li>The caller must keep {@code glowHalf} under the curve's radius of curvature, or the tube
 *       folds through itself -- see {@link #maxGlowHalf}.</li>
 * </ul>
 */
public final class SlashBladeMesh {

    /** Along the sweep. 21 spans over a few blocks is comfortably under the taper's scale. */
    private static final int LENGTH_SEGMENTS = 22;
    /** Even, so the flattened lens is symmetric and no seam vertex is duplicated on the wrap. */
    private static final int RING_SEGMENTS = 14;
    /** True out-of-plane depth as a fraction of the width: a blade, not a pipe. */
    private static final float THICKNESS_FRACTION = 0.14f;

    private SlashBladeMesh() {
    }

    /**
     * Largest ring half-width that stays a non-self-intersecting tube on a sine arch of this bow
     * and length: {@code length / (curveAmount * PI^2)}. Exceeding it puts the inner half of the
     * blade inside its own centre of curvature.
     */
    public static float maxGlowHalf(float length, float curveAmount) {
        return length / (curveAmount * (float) (Math.PI * Math.PI));
    }

    /**
     * Coreness at each of the {@link #RING_SEGMENTS} ring vertices, identical at every point along
     * the blade because the thresholds are divided by the local taper -- see the class javadoc.
     * 1 is ink, 0 is aura.
     */
    public static float[] crossSectionProfile(float glowHalf, float coreHalf) {
        float[] profile = new float[RING_SEGMENTS];
        for (int j = 0; j < RING_SEGMENTS; j++) {
            float theta = j / (float) RING_SEGMENTS * (float) (Math.PI * 2.0);
            float normalized = glowHalf * Math.abs(Mth.cos(theta));
            profile[j] = 1f - smoothstep(coreHalf, glowHalf, normalized);
        }
        return profile;
    }

    /**
     * Emits the tube as triangles into {@code consumer}, in the local frame of {@code pose} --
     * which the caller has already rotated so local +Z points along the projectile's flight
     * direction.
     *
     * @param length      blade span tip-to-tip, in blocks
     * @param curveAmount peak-to-peak bow of the centreline as a fraction of {@code length}; 0 is
     *                    a straight blade
     * @param glowHalf    half-width of the outer aura at the belly; keep under
     *                    {@link #maxGlowHalf(float, float)} for this length/curveAmount
     * @param coreRatio   the ink's half-width as a fraction of {@code glowHalf} (0..1)
     * @param taperPower  how sharply both tips pinch to a point (1 is linear, higher is sharper)
     * @param unfurl      0..1: how much of the span is drawn, growing out from the belly. At 0 the
     *                    mesh collapses to a point at the belly instead of popping in at full size.
     * @param tintArgb    colour of the ink, ARGB, alpha already carrying any fade
     * @param glowArgb    colour of the outer aura, ARGB, alpha already carrying any fade
     */
    public static void build(VertexConsumer consumer, PoseStack.Pose pose,
                             float length, float curveAmount,
                             float glowHalf, float coreRatio, float taperPower,
                             float unfurl, int tintArgb, int glowArgb) {

        float coreHalf = glowHalf * coreRatio;
        float reach = Mth.clamp(unfurl, 0f, 1f);

        Vector3f[] centre = new Vector3f[LENGTH_SEGMENTS];
        float[] widthRadius = new float[LENGTH_SEGMENTS];

        for (int i = 0; i < LENGTH_SEGMENTS; i++) {
            float u = i / (float) (LENGTH_SEGMENTS - 1);
            // Compressed towards the belly (u=0.5) rather than truncated at the ends, so the
            // crescent grows out of its own centre as reach climbs from 0 to 1, instead of a chunk
            // at each end simply appearing once reach crosses its position.
            float uu = 0.5f + (u - 0.5f) * reach;
            float arc = Mth.sin(uu * (float) Math.PI);
            // Sideways (local X) is the old "tail to tip" axis, centred on the flight line;
            // forward (local Z) is the old arc, so the belly -- where arc peaks -- sits furthest
            // along the direction of travel and the blade leads belly-first, tips trailing on
            // each side. This is the same axis role viewFrame used to assign via a matrix.
            centre[i] = new Vector3f((uu - 0.5f) * length, 0f, arc * curveAmount * length);
            widthRadius[i] = glowHalf * (float) Math.pow(arc, taperPower);
        }

        Vector3f[][] ringPos = new Vector3f[LENGTH_SEGMENTS][RING_SEGMENTS];
        Vector3f[][] ringNormal = new Vector3f[LENGTH_SEGMENTS][RING_SEGMENTS];
        float[][] ringColor = new float[LENGTH_SEGMENTS][RING_SEGMENTS * 4];

        // Computed once: identical for every ring, which is the whole claim. See crossSectionProfile.
        float[] profile = crossSectionProfile(glowHalf, coreHalf);

        for (int i = 0; i < LENGTH_SEGMENTS; i++) {
            Vector3f prev = centre[Math.max(i - 1, 0)];
            Vector3f next = centre[Math.min(i + 1, LENGTH_SEGMENTS - 1)];
            Vector3f tangent = new Vector3f(next).sub(prev);
            if (tangent.lengthSquared() < 1e-8f) {
                tangent.set(0f, 0f, 1f);
            }
            tangent.normalize();

            // The curve only bends within the local XZ plane, so the width direction is the
            // tangent rotated 90 degrees about Y -- "across the sweep" -- staying in that plane.
            Vector3f widthAxis = new Vector3f(tangent.z, 0f, -tangent.x);
            if (widthAxis.lengthSquared() < 1e-8f) {
                widthAxis.set(1f, 0f, 0f);
            }
            widthAxis.normalize();

            float radius = widthRadius[i];

            for (int j = 0; j < RING_SEGMENTS; j++) {
                float theta = j / (float) RING_SEGMENTS * (float) (Math.PI * 2.0);
                float cosT = Mth.cos(theta);
                float sinT = Mth.sin(theta);
                float widthOffset = radius * cosT;
                float thickOffset = radius * THICKNESS_FRACTION * sinT;

                ringPos[i][j] = new Vector3f(centre[i])
                        .add(new Vector3f(widthAxis).mul(widthOffset))
                        .add(0f, thickOffset, 0f);

                // Outward radial direction of the flattened ring. Not corrected for the ellipse's
                // unequal axes -- a fine approximation here, since the surface is lit at full
                // brightness and is only ever seen as a glow, not shaded, textured geometry.
                ringNormal[i][j] = new Vector3f(widthAxis).mul(cosT).add(0f, sinT, 0f).normalize();

                writeColor(ringColor[i], j * 4, tintArgb, glowArgb, profile[j]);
            }
        }

        for (int i = 0; i < LENGTH_SEGMENTS - 1; i++) {
            for (int j = 0; j < RING_SEGMENTS; j++) {
                int jn = (j + 1) % RING_SEGMENTS;

                triangle(consumer, pose,
                        ringPos[i][j], ringNormal[i][j], ringColor[i], j * 4,
                        ringPos[i][jn], ringNormal[i][jn], ringColor[i], jn * 4,
                        ringPos[i + 1][j], ringNormal[i + 1][j], ringColor[i + 1], j * 4);

                triangle(consumer, pose,
                        ringPos[i + 1][j], ringNormal[i + 1][j], ringColor[i + 1], j * 4,
                        ringPos[i][jn], ringNormal[i][jn], ringColor[i], jn * 4,
                        ringPos[i + 1][jn], ringNormal[i + 1][jn], ringColor[i + 1], jn * 4);
            }
        }
    }

    /** Lerps ink and glow by coreness -- the lerp the shader used to do per pixel, now done once
     *  per vertex and written straight into the vertex colour. */
    private static void writeColor(float[] out, int offset, int tintArgb, int glowArgb, float coreness) {
        out[offset]     = lerpChannel(tintArgb, glowArgb, coreness, 16);
        out[offset + 1] = lerpChannel(tintArgb, glowArgb, coreness, 8);
        out[offset + 2] = lerpChannel(tintArgb, glowArgb, coreness, 0);
        out[offset + 3] = lerpChannel(tintArgb, glowArgb, coreness, 24);
    }

    private static float lerpChannel(int tintArgb, int glowArgb, float coreness, int shift) {
        float tint = ((tintArgb >>> shift) & 0xFF) / 255f;
        float glow = ((glowArgb >>> shift) & 0xFF) / 255f;
        // coreness 1 -> tint (ink), coreness 0 -> glow (aura).
        return glow + coreness * (tint - glow);
    }

    private static float smoothstep(float edge0, float edge1, float x) {
        float t = Mth.clamp((x - edge0) / Math.max(edge1 - edge0, 1e-5f), 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    /**
     * VertexConsumer has no index buffer, so each triangle repeats its shared corners -- unchanged
     * from the 1.21.1 class's cost note, still ~1700 vertices per slash, rebuilt every frame.
     */
    private static void triangle(VertexConsumer consumer, PoseStack.Pose pose,
                                 Vector3f a, Vector3f na, float[] ca, int oa,
                                 Vector3f b, Vector3f nb, float[] cb, int ob,
                                 Vector3f c, Vector3f nc, float[] cc, int oc) {
        vertex(consumer, pose, a, na, ca, oa);
        vertex(consumer, pose, b, nb, cb, ob);
        vertex(consumer, pose, c, nc, cc, oc);
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose,
                               Vector3f p, Vector3f normal, float[] color, int o) {
        consumer.addVertex(pose, p.x, p.y, p.z)
                .setColor(color[o], color[o + 1], color[o + 2], color[o + 3])
                .setUv(0f, 0f)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightCoordsUtil.FULL_BRIGHT)
                .setNormal(normal.x, normal.y, normal.z);
    }
}
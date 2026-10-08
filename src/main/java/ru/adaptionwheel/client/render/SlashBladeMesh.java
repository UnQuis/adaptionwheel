package ru.adaptionwheel.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import org.joml.Vector3f;

/**
 * The slash crescent as the swept volume used on the 1.21.1 branch, expressed in the 26.3
 * renderer's local frame. The span runs sideways across the flight direction, the crescent bows
 * forward, and the thin axis is vertical. This is the same axis permutation as the old
 * {@code viewFrame + 90-degree rotation}; only the final vertex format differs on 26.3.
 *
 * <p>The fragment shader receives the original pair of UVs: {@code u} runs along the blade and
 * {@code coreness} runs from aura to ink. Keeping those values is important; without them the
 * screen-tone and moving hatch in {@code slash.fsh} collapse to a single static colour.
 */
public final class SlashBladeMesh {

    /** Along the sweep. */
    private static final int LENGTH_SEGMENTS = 22;
    /** Even, so the flattened lens is symmetric and has no duplicated seam vertex. */
    private static final int RING_SEGMENTS = 14;
    /** True out-of-plane depth as a fraction of the width: a blade, not a pipe. */
    private static final float THICKNESS_FRACTION = 0.14f;

    private SlashBladeMesh() {
    }

    /**
     * Largest ring half-width that stays a non-self-intersecting tube on a sine arch of this bow
     * and length: {@code length / (curveAmount * PI^2)}.
     */
    public static float maxGlowHalf(float length, float curveAmount) {
        return length / (curveAmount * (float) (Math.PI * Math.PI));
    }

    /**
     * Coreness at each ring vertex, identical at every point along the blade. One is ink, zero is
     * aura. Dividing by the local taper keeps the band intact all the way to the tips.
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
     * Emits the tube into {@code consumer}. The caller supplies a pose stack already aimed along
     * the projectile's velocity.
     *
     * @param length blade span tip-to-tip, in blocks
     * @param curveAmount peak-to-peak bow as a fraction of {@code length}
     * @param glowHalf half-width of the outer aura at the belly
     * @param coreRatio ink half-width as a fraction of {@code glowHalf}
     * @param taperPower how sharply both tips pinch to a point
     * @param tintArgb ink colour; alpha already includes the current fade
     * @param glowArgb aura colour; alpha already includes its current fade
     */
    public static void build(VertexConsumer consumer, PoseStack.Pose pose,
                             float length, float curveAmount, float glowHalf, float coreRatio,
                             float taperPower, int tintArgb, int glowArgb) {
        float coreHalf = glowHalf * coreRatio;
        Vector3f[] centre = new Vector3f[LENGTH_SEGMENTS];
        float[] widthRadius = new float[LENGTH_SEGMENTS];

        for (int i = 0; i < LENGTH_SEGMENTS; i++) {
            float u = i / (float) (LENGTH_SEGMENTS - 1);
            float arc = Mth.sin(u * (float) Math.PI);
            // This is the 1.21.1 sine crescent after its viewFrame/quarter-turn: X spans the
            // travel line, Z bows forward, and Y is the thin axis.
            centre[i] = new Vector3f((u - 0.5f) * length, 0f, arc * curveAmount * length);
            widthRadius[i] = glowHalf * (float) Math.pow(arc, taperPower);
        }

        Vector3f[][] ringPos = new Vector3f[LENGTH_SEGMENTS][RING_SEGMENTS];
        float[][] ringColor = new float[LENGTH_SEGMENTS][RING_SEGMENTS * 4];
        float[] profile = crossSectionProfile(glowHalf, coreHalf);

        for (int i = 0; i < LENGTH_SEGMENTS; i++) {
            Vector3f prev = centre[Math.max(i - 1, 0)];
            Vector3f next = centre[Math.min(i + 1, LENGTH_SEGMENTS - 1)];
            Vector3f tangent = new Vector3f(next).sub(prev);
            if (tangent.lengthSquared() < 1e-8f) {
                tangent.set(0f, 0f, 1f);
            }
            tangent.normalize();

            // The curve is in XZ. Its ring width stays in that plane; the small thickness is Y.
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
                ringPos[i][j] = new Vector3f(centre[i])
                        .add(new Vector3f(widthAxis).mul(radius * cosT))
                        .add(0f, radius * THICKNESS_FRACTION * sinT, 0f);
                writeColor(ringColor[i], j * 4, tintArgb, glowArgb, profile[j]);
            }
        }

        for (int i = 0; i < LENGTH_SEGMENTS - 1; i++) {
            float u = i / (float) (LENGTH_SEGMENTS - 1);
            float nextU = (i + 1) / (float) (LENGTH_SEGMENTS - 1);
            for (int j = 0; j < RING_SEGMENTS; j++) {
                int nextJ = (j + 1) % RING_SEGMENTS;
                triangle(consumer, pose,
                        ringPos[i][j], ringColor[i], j * 4, u, profile[j],
                        ringPos[i][nextJ], ringColor[i], nextJ * 4, u, profile[nextJ],
                        ringPos[i + 1][j], ringColor[i + 1], j * 4, nextU, profile[j]);
                triangle(consumer, pose,
                        ringPos[i + 1][j], ringColor[i + 1], j * 4, nextU, profile[j],
                        ringPos[i][nextJ], ringColor[i], nextJ * 4, u, profile[nextJ],
                        ringPos[i + 1][nextJ], ringColor[i + 1], nextJ * 4, nextU, profile[nextJ]);
            }
        }
    }

    private static void writeColor(float[] out, int offset, int tintArgb, int glowArgb, float coreness) {
        out[offset] = lerpChannel(tintArgb, glowArgb, coreness, 16);
        out[offset + 1] = lerpChannel(tintArgb, glowArgb, coreness, 8);
        out[offset + 2] = lerpChannel(tintArgb, glowArgb, coreness, 0);
        out[offset + 3] = lerpChannel(tintArgb, glowArgb, coreness, 24);
    }

    private static float lerpChannel(int tintArgb, int glowArgb, float coreness, int shift) {
        float tint = ((tintArgb >>> shift) & 0xFF) / 255f;
        float glow = ((glowArgb >>> shift) & 0xFF) / 255f;
        return glow + coreness * (tint - glow);
    }

    private static float smoothstep(float edge0, float edge1, float x) {
        float t = Mth.clamp((x - edge0) / Math.max(edge1 - edge0, 1e-5f), 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    private static void triangle(VertexConsumer consumer, PoseStack.Pose pose,
                                 Vector3f a, float[] ca, int oa, float ua, float va,
                                 Vector3f b, float[] cb, int ob, float ub, float vb,
                                 Vector3f c, float[] cc, int oc, float uc, float vc) {
        vertex(consumer, pose, a, ca, oa, ua, va);
        vertex(consumer, pose, b, cb, ob, ub, vb);
        vertex(consumer, pose, c, cc, oc, uc, vc);
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose,
                               Vector3f position, float[] color, int offset, float u, float v) {
        consumer.addVertex(pose, position.x, position.y, position.z)
                .setColor(color[offset], color[offset + 1], color[offset + 2], color[offset + 3])
                .setUv(u, v);
    }
}

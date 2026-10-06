package ru.adaptionwheel.client;

import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Matrix4f;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import org.joml.Vector3f;

/**
 * The slash crescent as an actual swept volume rather than a flat billboard.
 *
 * <p>The shape is the same mathematics the flat version evaluated per pixel -- a sine arch for the
 * centreline, {@code pow(arc, taperPower)} for the taper -- but here the arch is a real curve in 3D
 * and a flattened ring is swept along it. That is the whole point: the billboard was
 * mathematically zero width viewed edge-on, so the blade vanished from half the angles a player
 * could stand at. A swept tube has a cross-section from every direction.
 *
 * <p>The ring is flattened ({@link #THICKNESS_FRACTION} of its width) so it reads as a blade and not
 * a pipe. Brightness still depends only on how far across the width a point sits -- the same rule
 * the 2D version used -- but that value is now baked into UV.y on the CPU, so the fragment shader
 * only has to colour it. {@code coreness} 1 is ink, 0 is aura.
 *
 * <p>Two rules here are load-bearing and were both found by porting this to numpy and measuring,
 * not by reading it:
 *
 * <ul>
 *   <li><b>The coreness thresholds are divided by the local taper.</b> Left absolute, every ring
 *       whose radius has shrunk below {@code coreHalf} scores 1 at every vertex -- measured 10 of
 *       22 cross-sections, 38% of the length at each end, with no aura at all and the aura
 *       appearing as a hard step rather than a gradient. Dividing both thresholds by the falloff
 *       makes {@code |offset|/falloff == glowHalf*|cos theta|}, so the cross-section profile
 *       becomes a pure function of theta and is identical at every point along the blade.</li>
 *   <li><b>The caller must keep {@code glowHalf} below the curve's radius of curvature.</b> A ring
 *       of half-width W swept along a curve of radius R folds through itself once W exceeds R,
 *       because the inner edge passes through the centre of curvature. See {@link #maxGlowHalf}.</li>
 * </ul>
 */
public final class SlashBladeMesh {

    /**
     * Maps the mesh's local axes into <b>camera</b> space, so the slash is oriented by where the
     * camera is rather than by where the projectile happens to be flying.
     *
     * <p>The caller multiplies the camera's own rotation by this, which is what makes the blade
     * ride the view: look up and the crescent tips up, turn and it turns with you. Nothing here
     * reads the velocity, so a shot that drifts off the look line still renders in the orientation
     * the player is actually looking from.
     *
     * <p>The assignment is what makes the slash read as a cut rather than as a shape:
     * <ul>
     *   <li>length along camera −Z, which is into the screen, so the blade recedes and is
     *       foreshortened to a foreshortened sliver — you see its near end, not its full span;</li>
     *   <li>the arc across camera +X, so it bows sideways on screen;</li>
     *   <li>the ribbon's face along camera −Y, so the plate presents its face to the viewer and the
     *       arc's width is the strip's visible thickness.</li>
     * </ul>
     *
     * <p>Chosen right-handed on purpose: length × arc = (0,0,−1) × (1,0,0) = (0,−1,0), which is
     * the face. Mirroring either axis instead would flip the mesh inside out and put the screentone
     * band on the wrong side of the ink.
     *
     * @param roll spin about the blade's own length axis, radians. Clamped, because the server's
     *             +/-60 degrees was chosen against a world-horizontal plate and reads as a
     *             half-turn against the camera.
     */
    public static Matrix4f viewFrame(float roll) {
        float clamped = Mth.clamp(roll, -MAX_ROLL, MAX_ROLL);
        Matrix4f frame = new Matrix4f();
        // JOML is column-major: each column is a mesh axis expressed in camera space.
        frame.set(0f,  0f, -1f, 0f,
                  1f,  0f,  0f, 0f,
                  0f, -1f,  0f, 0f,
                  0f,  0f,  0f, 1f);
        // Roll is about the length axis, so it is applied to the mesh's own X before the mapping.
        float cos = (float) Math.cos(clamped);
        float sin = (float) Math.sin(clamped);
        Matrix4f spin = new Matrix4f().set(1f, 0f, 0f, 0f,
                                          0f, cos, sin, 0f,
                                          0f, -sin, cos, 0f,
                                          0f, 0f, 0f, 1f);
        return frame.mul(spin);
    }

    /** Roll is clamped well inside the server's +/-60 degree range; see {@link #frame}. */
    public static final float MAX_ROLL = (float) Math.toRadians(30.0);

    /** Along the sweep. 21 spans over 6 blocks is 0.29 blocks each, well under the taper's scale. */
    private static final int LENGTH_SEGMENTS = 22;
    /** Even, so the flattened lens is symmetric and no seam vertex is duplicated on the wrap. */
    private static final int RING_SEGMENTS = 14;
    /** True out-of-plane depth as a fraction of the width: a blade, not a pipe. */
    private static final float THICKNESS_FRACTION = 0.14f;

    private SlashBladeMesh() {
    }

    /**
     * Largest ring half-width that stays a non-self-intersecting tube on a sine arch of this
     * bow and length, which is {@code length / (curveAmount * PI^2)}.
     *
     * <p>Worth stating because the flat version had no such constraint at all: on a quad there is
     * nothing to fold. A swept volume introduces one, and it is a real limit rather than a matter
     * of taste -- exceeding it puts the inner half of the blade inside its own centre of curvature.
     */
    public static float maxGlowHalf(float length, float curveAmount) {
        return length / (curveAmount * (float) (Math.PI * Math.PI));
    }

    /**
     * Coreness at each of the {@link #RING_SEGMENTS} ring vertices, and — this is the point —
     * <b>the same at every point along the blade</b>.
     *
     * <p>It is a pure function of theta because the thresholds are divided by the local taper: the
     * offset across the ring is {@code glowHalf * falloff * cos(theta)}, so dividing by the falloff
     * cancels it and leaves {@code glowHalf * |cos theta|}. Tapered geometry and tapered shading
     * cancel, which is why the profile can be computed once instead of per ring.
     *
     * <p>With the thresholds left absolute this is where the bug lived: a ring whose radius has
     * shrunk below {@code coreHalf} scores 1 at every vertex, so it becomes solid ink with no aura,
     * measured over 10 of 22 cross-sections and the outer 38% at each end. {@code CrossSectionTests}
     * pins the falloff-independence that rules it out.
     */
    public static float[] crossSectionProfile(float glowHalf, float coreHalf) {
        float[] profile = new float[RING_SEGMENTS];
        for (int j = 0; j < RING_SEGMENTS; j++) {
            float theta = j / (float) RING_SEGMENTS * (float) (Math.PI * 2.0);
            // |offset| / falloff, which is what the shader sees once the taper is accounted for.
            float normalized = glowHalf * Math.abs(Mth.cos(theta));
            profile[j] = 1f - smoothstep(coreHalf, glowHalf, normalized);
        }
        return profile;
    }

    /**
     * Emits the tube as triangles into {@code consumer}, in the local frame of {@code pose}.
     *
     * @param length      blade length along local +X, in blocks
     * @param curveAmount peak-to-peak bow of the centreline as a fraction of {@code length};
     *                    0 is a straight blade
     * @param glowHalf    half-width of the outer aura at the widest point of the sweep
     * @param coreHalf    half-width of the ink; must be below {@code glowHalf}
     * @param taperPower  how sharply both ends pinch to a point (1 is linear, higher is sharper)
     */
    public static void build(VertexConsumer consumer, PoseStack.Pose pose,
                             float length, float curveAmount,
                             float glowHalf, float coreHalf, float taperPower) {

        Vector3f[] centre = new Vector3f[LENGTH_SEGMENTS];
        float[] widthRadius = new float[LENGTH_SEGMENTS];

        for (int i = 0; i < LENGTH_SEGMENTS; i++) {
            float u = i / (float) (LENGTH_SEGMENTS - 1);
            float arc = Mth.sin(u * (float) Math.PI);
            centre[i] = new Vector3f(u * length, arc * curveAmount * length, 0f);
            widthRadius[i] = glowHalf * (float) Math.pow(arc, taperPower);
        }

        Vector3f[][] ringPos = new Vector3f[LENGTH_SEGMENTS][RING_SEGMENTS];
        float[][] ringCore = new float[LENGTH_SEGMENTS][RING_SEGMENTS];

        // Computed once: identical for every ring, which is the whole claim. See crossSectionProfile.
        float[] profile = crossSectionProfile(glowHalf, coreHalf);

        for (int i = 0; i < LENGTH_SEGMENTS; i++) {
            Vector3f prev = centre[Math.max(i - 1, 0)];
            Vector3f next = centre[Math.min(i + 1, LENGTH_SEGMENTS - 1)];
            Vector3f tangent = new Vector3f(next).sub(prev);
            if (tangent.lengthSquared() < 1e-8f) {
                tangent.set(1f, 0f, 0f);
            }
            tangent.normalize();

            // The curve only bends within the local XY plane, so the width direction is just the
            // tangent rotated 90 degrees about Z -- "across the sweep".
            Vector3f widthAxis = new Vector3f(-tangent.y, tangent.x, 0f);
            if (widthAxis.lengthSquared() < 1e-8f) {
                widthAxis.set(0f, 1f, 0f);
            }
            widthAxis.normalize();

            // Baked into UV.y. Because the taper cancels out of the shading, the profile is the
            // same here as it is at the belly and at the tips: the ring narrows and the two-tone
            // band narrows with it, instead of the tips flattening into solid ink.
            float radius = widthRadius[i];

            for (int j = 0; j < RING_SEGMENTS; j++) {
                float theta = j / (float) RING_SEGMENTS * (float) (Math.PI * 2.0);
                float widthOffset = radius * Mth.cos(theta);
                float thickOffset = radius * THICKNESS_FRACTION * Mth.sin(theta);

                ringPos[i][j] = new Vector3f(centre[i])
                        .add(new Vector3f(widthAxis).mul(widthOffset))
                        .add(0f, 0f, thickOffset);
                ringCore[i][j] = profile[j];
            }
        }

        for (int i = 0; i < LENGTH_SEGMENTS - 1; i++) {
            float u = i / (float) (LENGTH_SEGMENTS - 1);
            float uNext = (i + 1) / (float) (LENGTH_SEGMENTS - 1);
            for (int j = 0; j < RING_SEGMENTS; j++) {
                int jn = (j + 1) % RING_SEGMENTS;

                triangle(consumer, pose,
                        ringPos[i][j], u, ringCore[i][j],
                        ringPos[i][jn], u, ringCore[i][jn],
                        ringPos[i + 1][j], uNext, ringCore[i + 1][j]);

                triangle(consumer, pose,
                        ringPos[i + 1][j], uNext, ringCore[i + 1][j],
                        ringPos[i][jn], u, ringCore[i][jn],
                        ringPos[i + 1][jn], uNext, ringCore[i + 1][jn]);
            }
        }
    }

    private static float smoothstep(float edge0, float edge1, float x) {
        float t = Mth.clamp((x - edge0) / Math.max(edge1 - edge0, 1e-5f), 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    /**
     * VertexConsumer has no index buffer -- it is an immediate-mode sink -- so each triangle
     * repeats its shared corners. At 21 x 14 x 2 x 3 = 1764 vertices per slash that is nothing, and
     * rebuilding every frame is cheaper than the bookkeeping a cached buffer would need.
     *
     * <p>The traversal order matters and is not free to change: emitting these two triangles in
     * this order gives a surface whose normals all point outward from the axis (measured 280 of
     * 280 non-degenerate triangles agreeing), which is what makes back-face culling safe. See
     * {@link SlashShaderFX} for why that matters.
     */
    private static void triangle(VertexConsumer consumer, PoseStack.Pose pose,
                                 Vector3f a, float ua, float ca,
                                 Vector3f b, float ub, float cb,
                                 Vector3f c, float uc, float cc) {
        consumer.addVertex(pose, a.x, a.y, a.z).setUv(ua, ca);
        consumer.addVertex(pose, b.x, b.y, b.z).setUv(ub, cb);
        consumer.addVertex(pose, c.x, c.y, c.z).setUv(uc, cc);
    }
}
package ru.adaptionwheel.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Motion for the cursed slash. The blade itself is built by {@link SlashBladeMesh} and drawn by
 * {@link SlashShaderFX}; this class only decides where it points, how big it is and how far through
 * its life it is.
 *
 * <p>All of that stayed on the CPU deliberately. A {@code RenderType} batches every primitive that
 * shares it under one set of uniforms, so per-slash parameters would need per-instance vertex
 * attributes; issuing the draw immediately instead lets one program serve any number of slashes
 * with independent tints, rolls and sizes.
 */
public final class FlyingSlashRenderer {

    /**
     * Peak-to-peak bow of the centreline as a fraction of the blade's length. Derived from the flat
     * version this replaces: its arch spanned 1.1 quad-heights over a 5-block quad, i.e. 2.75 blocks
     * peak-to-peak, and {@link SlashBladeMesh} takes the same 2.75 over its 6-block length. Keeping
     * the bow means keeping the silhouette; changing it is an art call, not a conversion.
     */
    private static final float CURVE_AMOUNT = 0.4583f;
    /** Sharpness of both tips; unchanged from the flat version, which used the same exponent. */
    private static final float TAPER_POWER = 1.35f;

    private FlyingSlashRenderer() {
    }

    /**
     * The two half-widths are the ink-to-aura ratio of the flat shader, converted: it separated
     * them by 0.16 v against 0.28 v, which is 0.40 blocks against 0.70 on a 6-block blade, or
     * 0.5714 as a fraction. Passing the ratio rather than a third constant keeps the two-tone band
     * a single design decision instead of two numbers that can drift apart.
     *
     * @param glowHalf half-width of the aura at the blade's belly, in blocks
     * @param coreHalf half-width of the ink, in blocks
     */
    public static void render(PoseStack poseStack, Vec3 motion, float roll,
                              float age, float length, float glowHalf, float coreHalf,
                              int[] color, int[] glowColor, float opacity) {
        if (motion.lengthSqr() < 1.0E-8 || opacity <= 0.01f || !SlashShaderFX.ready()) {
            return;
        }

        // Swings open quickly, then holds — a cut does not ease in.
        float growth = Mth.clamp(age / 3f, 0.15f, 1f);

        float yaw = (float) Mth.atan2(motion.x, motion.z);
        float pitch = (float) Mth.atan2(motion.y, motion.horizontalDistance());

        poseStack.pushPose();
        // The mesh is built along local +X, and this chain maps it onto the flight direction.
        //
        // Both signs matter and were measured rather than reasoned about: the mesh's +X must end up
        // parallel to the velocity, and this composition gives dot = +1.000 against it. Rotating the
        // wrong way about Y (and the wrong way about X) puts the crescent behind the projectile and
        // off-axis at the same time — a blade that looks merely misaligned rather than broken, so
        // it is easy to ship by eye.
        poseStack.mulPose(Axis.YP.rotation(yaw));
        poseStack.mulPose(Axis.XP.rotation(-pitch));
        // Roll about the blade's own length axis, which is what flips the crescent's bow from one
        // side of the trail to the other. Verified not to move the length axis itself.
        poseStack.mulPose(Axis.ZP.rotationDegrees(roll));
        poseStack.mulPose(Axis.YP.rotationDegrees(-90f));

        SlashShaderFX.draw(poseStack,
                length * growth, CURVE_AMOUNT,
                glowHalf * growth, coreHalf * growth, TAPER_POWER,
                color, opacity, glowColor, 0.85f * opacity);
        poseStack.popPose();
    }
}
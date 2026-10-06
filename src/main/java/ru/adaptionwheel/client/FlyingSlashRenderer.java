package ru.adaptionwheel.client;

import com.mojang.blaze3d.vertex.PoseStack;
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
     * @param glowHalf half-width of the aura at the blade's belly, in blocks
     * @param coreHalf half-width of the ink, in blocks
     *
     * <p>The pair keeps the flat shader's ink-to-aura proportion (0.16 v against 0.28 v, about
     * 0.57) but is far narrower than that shader's, and that is deliberate: with the ribbon lying
     * flat, its arc width is exactly what a viewer behind the shot sees, so this number <em>is</em>
     * the strip's apparent thickness. The old 0.70 gave a 1.4-block band that read as a broad
     * crescent rather than a cut.
     */
    public static void render(PoseStack poseStack, Vec3 motion, float roll,
                              float age, float length, float glowHalf, float coreHalf,
                              int[] color, int[] glowColor, float opacity) {
        if (motion.lengthSqr() < 1.0E-8 || opacity <= 0.01f || !SlashShaderFX.ready()) {
            return;
        }

        // Swings open quickly, then holds — a cut does not ease in.
        float growth = Mth.clamp(age / 3f, 0.15f, 1f);

        // Heading only. The pitch is deliberately dropped: the crescent stays level however the
        // shot was aimed, so a downward slash does not tip into the ground with the projectile.
        float yaw = (float) Mth.atan2(motion.x, motion.z);

        poseStack.pushPose();
        // SlashBladeMesh owns the frame. It is built from the heading and nothing else, so there is
        // no camera term to re-orient the blade when the view turns; and it puts the ribbon flat
        // with its face to the ground, which is what makes the crescent read as a thin strip rather
        // than as a broad crescent face.
        poseStack.mulPose(SlashBladeMesh.frame(yaw, roll));

        SlashShaderFX.draw(poseStack,
                length * growth, CURVE_AMOUNT,
                glowHalf * growth, coreHalf * growth, TAPER_POWER,
                color, opacity, glowColor, 0.85f * opacity);
        poseStack.popPose();
    }
}
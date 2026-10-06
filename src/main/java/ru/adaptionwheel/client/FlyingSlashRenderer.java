package ru.adaptionwheel.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

public final class FlyingSlashRenderer {

    private static final float CURVE_AMOUNT = 0.4583f;
    private static final float TAPER_POWER = 1.35f;

    private FlyingSlashRenderer() {
    }

    public static void render(PoseStack poseStack, Vec3 direction, float roll,
                              float age, float length, float glowHalf, float coreHalf,
                              int[] color, int[] glowColor, float opacity) {
        if (opacity <= 0.01f || !SlashShaderFX.ready()) {
            return;
        }

        float growth = Mth.clamp(age / 3f, 0.15f, 1f);
        float effectiveLength = length * growth;

        poseStack.pushPose();
        poseStack.mulPose(aimRotation(direction));
        poseStack.mulPose(SlashBladeMesh.viewFrame(roll));

        // SlashBladeMesh.build() lays the centreline out as X: 0 -> length (the tail-to-tip
        // axis) and Y: 0 -> curveAmount*length (the arc, 0 at both tips, max at the belly).
        // viewFrame/aimRotation point local X at the travel direction, so without this, the
        // blade flies tip-first - one end leading - instead of belly-first.
        //
        // Swapping which axis is "forward" is a rotation about local Z by -90 degrees, derived
        // from x' = x*cos(th) - y*sin(th), y' = x*sin(th) + y*cos(th): at th = -90 degrees,
        // x' = y and y' = -x, so the arc (old y, always >= 0, max at the belly) becomes the new
        // forward-role axis with the belly correctly landing at maximum forward offset, not at
        // maximum backward offset.
        //
        // The translate has to run first (it is the innermost op, applied straight to the mesh's
        // raw local coordinates): old X ran 0..length, not -length/2..length/2, so without
        // re-centring it first, the swap dumps that whole one-sided range onto the new sideways
        // axis and the blade ends up dragged entirely to one side instead of straddling the
        // flight line with a tip swept out on each side.
        poseStack.mulPose(new Quaternionf().rotationZ((float) Math.toRadians(90.0)));
        poseStack.translate(-effectiveLength * 0.5f, 0f, 0f);

        SlashShaderFX.draw(poseStack,
                effectiveLength, CURVE_AMOUNT,
                glowHalf * growth, coreHalf * growth, TAPER_POWER,
                color, opacity, glowColor, 0.85f * opacity);
        poseStack.popPose();
    }

    /**
     * Builds the same rotation {@code Camera.setRotation} would build for a camera pointed along
     * {@code direction}, in world space, off the object's own velocity rather than off whoever is
     * watching it. Verified correct: the slash already travels the right way with this alone: the
     * remaining fix above is purely about which local axis gets aimed by it, not about this math.
     */
    private static Quaternionf aimRotation(Vec3 direction) {
        if (direction.lengthSqr() < 1.0e-6) {
            return new Quaternionf();
        }
        Vec3 d = direction.normalize();
        double horizontal = Math.sqrt(d.x * d.x + d.z * d.z);
        float yaw = (float) Mth.atan2(d.x, d.z);
        float pitch = (float) Mth.atan2(-d.y, horizontal);
        return new Quaternionf().rotationYXZ(yaw, pitch, 0f);
    }
}
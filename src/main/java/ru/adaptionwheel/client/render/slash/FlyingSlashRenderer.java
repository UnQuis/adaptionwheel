package ru.adaptionwheel.client.render.slash;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import ru.adaptionwheel.client.render.ModRenderTypes;

/**
 * Shared 26.3 adapter for the slash model used by both cursed slashes and spatial rifts.
 *
 * <p>The proportions, growth curve, orientation and fade match the working 1.21.1
 * {@code FlyingSlashRenderer}. Only the submission path is version-specific: 26.3 submits the
 * swept mesh through {@link SubmitNodeCollector} and the mod's own render pipeline.
 */
public final class FlyingSlashRenderer {

    public static final float CURVE_AMOUNT = 0.4583f;
    private static final float TAPER_POWER = 1.35f;
    private static final float GROWTH_TICKS = 2.6f;
    private static final float MIN_GROWTH = 0.16f;
    private static final float MAX_ROLL = (float) Math.toRadians(30.0);

    private FlyingSlashRenderer() {
    }

    /**
     * Submit one slash in the same orientation and proportions as the 1.21.1 branch.
     *
     * @param span full tip-to-tip length before the original spawn-growth animation
     * @param glowHalf outer aura half-width before growth
     * @param coreHalf ink half-width before growth
     * @param tintArgb ink colour, normally opaque
     * @param glowArgb aura colour, normally opaque
     * @param opacity entity lifetime fade, in {@code [0, 1]}
     * @param seed entity id, so two slashes never wobble in lockstep
     */
    public static void render(PoseStack poseStack, SubmitNodeCollector collector, Vec3 direction,
                              float roll, float age, float span, float glowHalf, float coreHalf,
                              int tintArgb, int glowArgb, float opacity, int seed) {
        if (opacity <= 0.01f || span <= 0f || glowHalf <= 0f) {
            return;
        }
        Vec3 dir = direction.lengthSqr() < 1.0e-8 ? new Vec3(0, 0, 1) : direction.normalize();

        float fade = Mth.clamp(opacity, 0f, 1f);
        float growth = Mth.clamp(age / GROWTH_TICKS, MIN_GROWTH, 1f);

        // Ported 1.21.1 animation: a slow breathing pulse on the width, a gentle wobble about the
        // flight axis, and a small length pulse. A completely static crescent looked pasted on.
        float breathe = 1f + 0.035f * Mth.sin(age * 1.8f + seed * 0.37f);
        float animatedRoll = roll + Mth.sin(age * 0.42f + seed * 0.13f) * 0.11f;
        float effectiveSpan = span * growth * (1f + 0.012f * Mth.sin(age * 2.3f + seed));

        float effectiveGlowHalf = glowHalf * growth * breathe;
        float effectiveCoreHalf = coreHalf * growth * breathe;
        float coreRatio = Mth.clamp(effectiveCoreHalf / Math.max(effectiveGlowHalf, 1e-5f), 0f, 1f);
        int effectiveTint = withOpacity(tintArgb, fade);
        int effectiveGlow = withOpacity(glowArgb, fade * 0.9f);

        double horizontal = Math.sqrt(dir.x * dir.x + dir.z * dir.z);
        float yaw = (float) Mth.atan2(dir.x, dir.z);
        float pitch = (float) Mth.atan2(-dir.y, horizontal);
        float clampedRoll = Mth.clamp(animatedRoll, -MAX_ROLL, MAX_ROLL);

        poseStack.pushPose();
        try {
            // Same Camera.setRotation-equivalent frame as 1.21.1: local +Z follows the
            // projectile. Its viewFrame is followed by a quarter-turn, which reverses the
            // apparent sign of the stored roll in this already-permuted local frame.
            poseStack.rotate(Axis.YP, yaw);
            poseStack.rotate(Axis.XP, pitch);
            poseStack.rotate(Axis.ZP, -clampedRoll);

            collector.submitCustomGeometry(poseStack, ModRenderTypes.slash(), (pose, consumer) ->
                    SlashBladeMesh.build(consumer, pose, effectiveSpan, CURVE_AMOUNT,
                            effectiveGlowHalf, coreRatio, TAPER_POWER, effectiveTint, effectiveGlow,
                            age, seed));
        } finally {
            poseStack.popPose();
        }
    }

    private static int withOpacity(int argb, float opacity) {
        int alpha = Math.round(((argb >>> 24) & 0xFF) * Mth.clamp(opacity, 0f, 1f));
        return (alpha << 24) | (argb & 0x00FFFFFF);
    }
}

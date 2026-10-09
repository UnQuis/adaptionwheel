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
    private static final float GROWTH_TICKS = 3f;
    private static final float MIN_GROWTH = 0.15f;
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
     */
    public static void render(PoseStack poseStack, SubmitNodeCollector collector, Vec3 direction,
                              float roll, float age, float span, float glowHalf, float coreHalf,
                              int tintArgb, int glowArgb, float opacity) {
        if (direction.lengthSqr() < 1.0e-8 || opacity <= 0.01f || span <= 0f || glowHalf <= 0f) {
            return;
        }

        float fade = Mth.clamp(opacity, 0f, 1f);
        float growth = Mth.clamp(age / GROWTH_TICKS, MIN_GROWTH, 1f);
        float effectiveSpan = span * growth;
        float effectiveGlowHalf = glowHalf * growth;
        float coreRatio = Mth.clamp(coreHalf / glowHalf, 0f, 1f);
        int effectiveTint = withOpacity(tintArgb, fade);
        int effectiveGlow = withOpacity(glowArgb, fade * 0.85f);

        double horizontal = Math.sqrt(direction.x * direction.x + direction.z * direction.z);
        float yaw = (float) Mth.atan2(direction.x, direction.z);
        float pitch = (float) Mth.atan2(-direction.y, horizontal);
        float clampedRoll = Mth.clamp(roll, -MAX_ROLL, MAX_ROLL);

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
                            effectiveGlowHalf, coreRatio, TAPER_POWER, effectiveTint, effectiveGlow));
        } finally {
            poseStack.popPose();
        }
    }

    private static int withOpacity(int argb, float opacity) {
        int alpha = Math.round(((argb >>> 24) & 0xFF) * Mth.clamp(opacity, 0f, 1f));
        return (alpha << 24) | (argb & 0x00FFFFFF);
    }
}

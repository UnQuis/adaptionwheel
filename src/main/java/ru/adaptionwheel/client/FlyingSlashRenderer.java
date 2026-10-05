package ru.adaptionwheel.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Motion for the cursed slash. The blade itself is drawn by {@link SlashShaderFX}; this class only
 * decides where it is, how big it is and how far through its life it is.
 *
 * <p>All of that stayed on the CPU deliberately. A {@code RenderType} batches every quad that
 * shares it under one set of uniforms, so per-slash parameters would need per-instance vertex
 * attributes; issuing the draw immediately instead lets one program serve any number of slashes
 * with independent tints, rolls and sizes.
 */
public final class FlyingSlashRenderer {

    private FlyingSlashRenderer() {
    }

    public static void render(PoseStack poseStack, Vec3 motion, float roll,
                              float age, float width, float height,
                              int[] color, int[] glowColor, float opacity) {
        if (motion.lengthSqr() < 1.0E-8 || opacity <= 0.01f || !SlashShaderFX.ready()) {
            return;
        }

        Matrix4f matrix = poseStack.last().pose();
        // Swings open quickly, then holds — a cut does not ease in.
        float growth = Mth.clamp(age / 3f, 0.15f, 1f);

        Quaternionf screenRotation = new Quaternionf().rotationZ(roll);
        Vec3 side = rotate(screenRotation, new Vec3(1, 0, 0));
        Vec3 up = rotate(screenRotation, new Vec3(0, 1, 0));

        SlashShaderFX.draw(matrix, side, up, Vec3.ZERO,
                width * growth, height * growth,
                color, opacity, glowColor, 0.85f * opacity);
    }

    private static Vec3 rotate(Quaternionf rotation, Vec3 vector) {
        Vector3f result = rotation.transform(new Vector3f((float) vector.x, (float) vector.y, (float) vector.z));
        return new Vec3(result.x, result.y, result.z);
    }
}
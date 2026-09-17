package ru.adaptionwheel.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import ru.adaptionwheel.AdaptionWheel;

/**
 * Shared textured renderer for the two Sword of Extermination projectiles.
 *
 * <p>The textures are layered on three planes around the flight axis. This
 * keeps the slash readable when the projectile is viewed edge-on, while the
 * original ready-made alpha shapes provide the irregular, flying-blade
 * silhouette instead of a procedural strip of quads.</p>
 */
public final class FlyingSlashRenderer {

    public static final ResourceLocation BLADE_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(AdaptionWheel.MODID, "textures/entity/flying_slash.png");
    private static final ResourceLocation SOFT_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(AdaptionWheel.MODID, "textures/entity/flying_slash_soft.png");

    private static final float[] PLANE_ANGLES = {0f, 60f, -60f};

    private FlyingSlashRenderer() {
    }

    /**
     * Draws a layered flying slash at the entity origin. The caller must first
     * rotate the pose stack by the camera rotation, as normal for entity
     * billboards; all vectors below are then in that local render space.
     *
     * @param motion projectile velocity, used as the slash's forward axis
     * @param roll projectile-specific roll, so simultaneous slashes do not stack
     * @param age projectile age including the current partial tick
     * @param width horizontal size of the imported slash shape
     * @param height vertical size of the imported slash shape
     * @param color RGB tint for the solid blade
     * @param glowColor RGB tint for its soft halo
     * @param opacity overall opacity, normally a lifetime fade value
     */
    public static void render(PoseStack poseStack, MultiBufferSource buffers, Vec3 motion, float roll,
                              float age, float width, float height,
                              int[] color, int[] glowColor, float opacity) {
        if (motion.lengthSqr() < 1.0E-8 || opacity <= 0.01f) {
            return;
        }

        Vec3 forward = motion.normalize();
        Vec3 side = forward.cross(new Vec3(0, 1, 0));
        if (side.lengthSqr() < 1.0E-6) {
            side = new Vec3(1, 0, 0);
        }
        side = side.normalize();
        Vec3 up = forward.cross(side).normalize();

        Quaternionf rollRotation = new Quaternionf().rotationAxis(roll,
                (float) forward.x, (float) forward.y, (float) forward.z);
        side = rotate(rollRotation, side);
        up = rotate(rollRotation, up);

        Matrix4f matrix = poseStack.last().pose();
        float pulse = 0.88f + 0.12f * Mth.sin(age * 1.8f);
        float growth = Mth.clamp(age / 4f, 0.18f, 1f);

        // The imported soft shape is deliberately drawn first. The sharp white
        // alpha shape on top gives the slash a hot core without procedural noise.
        for (int i = 0; i < PLANE_ANGLES.length; i++) {
            float planeAlpha = i == 0 ? 1f : 0.72f;
            Quaternionf planeRotation = new Quaternionf().rotationAxis(
                    (float) Math.toRadians(PLANE_ANGLES[i]),
                    (float) forward.x, (float) forward.y, (float) forward.z);
            Vec3 planeSide = rotate(planeRotation, side);
            Vec3 planeUp = rotate(planeRotation, up);
            drawSprite(buffers, matrix, planeSide, planeUp, Vec3.ZERO,
                    width * 1.12f * growth, height * 1.12f * growth,
                    SOFT_TEXTURE, glowColor, (int) (82f * pulse * opacity * planeAlpha));
            drawSprite(buffers, matrix, planeSide, planeUp, Vec3.ZERO,
                    width * growth, height * growth,
                    BLADE_TEXTURE, color, (int) (226f * pulse * opacity * planeAlpha));
        }

        // A dim after-image makes motion visible without leaving a permanent
        // trail entity behind the projectile.
        drawSprite(buffers, matrix, side, up, forward.scale(-0.65f),
                width * 0.9f * growth, height * 0.9f * growth,
                SOFT_TEXTURE, glowColor, (int) (42f * opacity));
    }

    private static Vec3 rotate(Quaternionf rotation, Vec3 vector) {
        Vector3f result = rotation.transform(new Vector3f((float) vector.x, (float) vector.y, (float) vector.z));
        return new Vec3(result.x, result.y, result.z);
    }

    private static void drawSprite(MultiBufferSource buffers, Matrix4f matrix,
                                   Vec3 side, Vec3 up, Vec3 center,
                                   float width, float height, ResourceLocation texture,
                                   int[] color, int alpha) {
        if (alpha <= 2) {
            return;
        }
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityTranslucent(texture));
        Vec3 halfSide = side.scale(width * 0.5);
        Vec3 halfUp = up.scale(height * 0.5);
        Vec3 topLeft = center.subtract(halfSide).add(halfUp);
        Vec3 bottomLeft = center.subtract(halfSide).subtract(halfUp);
        Vec3 bottomRight = center.add(halfSide).subtract(halfUp);
        Vec3 topRight = center.add(halfSide).add(halfUp);

        vertex(consumer, matrix, topLeft, 0f, 0f, color, alpha);
        vertex(consumer, matrix, bottomLeft, 0f, 1f, color, alpha);
        vertex(consumer, matrix, bottomRight, 1f, 1f, color, alpha);
        vertex(consumer, matrix, topRight, 1f, 0f, color, alpha);
    }

    private static void vertex(VertexConsumer consumer, Matrix4f matrix, Vec3 position,
                               float u, float v, int[] color, int alpha) {
        consumer.addVertex(matrix, (float) position.x, (float) position.y, (float) position.z)
                .setColor(color[0], color[1], color[2], alpha)
                .setUv(u, v)
                .setLight(0xF000F0)
                .setNormal(0f, 1f, 0f);
    }
}

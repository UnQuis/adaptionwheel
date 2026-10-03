package ru.adaptionwheel.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import ru.adaptionwheel.AdaptionWheel;

public final class FlyingSlashRenderer {

    public static final ResourceLocation BLADE_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(AdaptionWheel.MODID, "textures/entity/flying_slash.png");
    private static final ResourceLocation SOFT_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(AdaptionWheel.MODID, "textures/entity/flying_slash_soft.png");

    private FlyingSlashRenderer() {
    }

    public static void render(PoseStack poseStack, MultiBufferSource buffers, Vec3 motion, float roll,
                              float age, float width, float height,
                              int[] color, int[] glowColor, float opacity) {
        if (motion.lengthSqr() < 1.0E-8 || opacity <= 0.01f) {
            return;
        }

        Matrix4f matrix = poseStack.last().pose();
        float pulse = 0.88f + 0.12f * Mth.sin(age * 1.8f);
        float growth = Mth.clamp(age / 4f, 0.18f, 1f);

        Quaternionf screenRotation = new Quaternionf().rotationZ(roll);
        Vec3 side = rotate(screenRotation, new Vec3(1, 0, 0));
        Vec3 up = rotate(screenRotation, new Vec3(0, 1, 0));

        drawSprite(buffers, matrix, side, up, Vec3.ZERO,
                width * 1.12f * growth, height * 1.12f * growth,
                SOFT_TEXTURE, glowColor, (int) (82f * pulse * opacity));
        drawSprite(buffers, matrix, side, up, Vec3.ZERO,
                width * growth, height * growth,
                BLADE_TEXTURE, color, (int) (226f * pulse * opacity));

        Quaternionf echoRotation = new Quaternionf().rotationZ(roll + 0.08f);
        drawSprite(buffers, matrix, rotate(echoRotation, new Vec3(1, 0, 0)),
                rotate(echoRotation, new Vec3(0, 1, 0)), new Vec3(0, 0, -0.04),
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
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(0f, 0f, 1f);
    }
}

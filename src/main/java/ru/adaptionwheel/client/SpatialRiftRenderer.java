package ru.adaptionwheel.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import ru.adaptionwheel.AdaptionWheel;
import ru.adaptionwheel.entity.SpatialRiftProjectile;

import java.util.List;

/**
 * Dimension Destroy visual, v3 — a violent horizontal tear across space,
 * built entirely in WORLD axes (no camera-billboard mixing).
 *
 * <p>The tear is a wall lying along the flight direction, widened along the
 * horizontal perpendicular of the path (like the original's giant slash
 * sprite sweeping sideways), tilted by the rift's roll around the flight axis.
 * Three additive layers with jagged noise edges: wide violet haze, saturated
 * violet body, pulsing white-hot core. Short bright "crack" shards flicker
 * alongside the main line on a deterministic schedule so they dance without
 * strobing.</p>
 */
public class SpatialRiftRenderer extends EntityRenderer<SpatialRiftProjectile> {

    private static final float[] JAG_OFFSET = {-0.78f, -0.4f, 0f, 0.4f, 0.78f};
    private static final float[] JAG_ALPHA = {0.14f, 0.32f, 0.6f, 0.32f, 0.14f};

    private static final int[] WALL_DARK_RGB = {12, 6, 24};
    private static final int[] HAZE_RGB = {120, 90, 255};
    private static final int[] BODY_RGB = {170, 70, 255};
    private static final int[] CORE_RGB = {255, 255, 255};
    private static final int[] CRACK_RGB = {225, 200, 255};

    public SpatialRiftRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(SpatialRiftProjectile entity) {
        return ResourceLocation.fromNamespaceAndPath(AdaptionWheel.MODID, "textures/entity/wheel.png");
    }

    @Override
    public void render(SpatialRiftProjectile entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffers, int light) {
        List<Vec3> trail = entity.getTrailSnapshot();
        if (trail.size() < 2) {
            return;
        }
        float age = entity.tickCount + partialTick;

        poseStack.pushPose();
        // Restore WORLD axes inside the dispatcher's view-space pose stack.
        poseStack.mulPose(this.entityRenderDispatcher.camera.rotation());
        Matrix4f matrix = poseStack.last().pose();

        Vec3 tail = toLocalSpace(entity, trail.get(0), 0f);
        Vec3 head = toLocalSpace(entity, trail.get(trail.size() - 1), partialTick);
        Vec3 dir = head.subtract(tail);
        if (dir.lengthSqr() < 1.0E-6) {
            poseStack.popPose();
            return;
        }
        dir = dir.normalize();

        // The tear itself is a wall ACROSS the flight path (the original's
        // second collision line: 150-long, 600-wide), vertical with a slight
        // roll tilt around the flight axis.
        Vec3 side = dir.cross(new Vec3(0, 1, 0));
        if (side.lengthSqr() < 1.0E-6) {
            side = new Vec3(1, 0, 0);
        }
        side = side.normalize();
        Quaternionf rollRot = new Quaternionf().rotationAxis((float) entity.getRoll(),
                (float) dir.x, (float) dir.y, (float) dir.z);
        Vector3f upRot = rollRot.transform(new Vector3f(0, 1, 0));
        Vec3 up = new Vec3(upRot.x, upRot.y, upRot.z);

        long seed = entity.getUUID().getLeastSignificantBits() ^ (entity.tickCount / 5);

        float unfold = Mth.clamp(age / 4f, 0.1f, 1f);
        float lifeRatio = Mth.clamp(age / SpatialRiftProjectile.LIFETIME_TICKS, 0f, 1f);
        float fade = lifeRatio > 0.8f ? (1f - lifeRatio) / 0.2f : 1f;
        float halfSpan = (2.2f + entity.getBladeHalfWidth() * 1.4f) * unfold;

        // Crossed 3D rift: a wall ACROSS the path plus a wall ALONG it —
        // from any camera angle one of them is face-on.
        Vec3 wallFrom = head.subtract(side.scale(halfSpan)).add(up.scale(-1.4f));
        Vec3 wallTo = head.add(side.scale(halfSpan)).add(up.scale(1.6f));
        drawTear(buffers, matrix, wallFrom, wallTo, up, seed, fade);

        Vec3 alongFrom = tail.add(up.scale(-1.2f));
        Vec3 alongTo = head.add(up.scale(1.8f));
        drawTear(buffers, matrix, alongFrom, alongTo, side, seed + 0x51, fade);

        crackShards(buffers, matrix, head, up, side, dir, seed, fade, age, halfSpan);

        // Thin bright tail along the path behind the rift.
        strip(buffers, matrix, tail, head, dir, 0.18f, (int) (200 * fade), CORE_RGB);

        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, buffers, light);
    }

    /** The main tear: three jagged layers around a clean pulsing core. */
    private void drawTear(MultiBufferSource buffers, Matrix4f matrix,
                          Vec3 from, Vec3 to, Vec3 n, long seed, float fade) {
        if (fade <= 0.01f) {
            return;
        }
        float pulse = 0.72f + 0.28f * Mth.sin(seed * 0.61f + System.currentTimeMillis() * 0.02f);

        // Dark wall body first — reads against any sky, like the original.
        jaggedLayer(RenderType.debugQuads(), buffers, matrix, from, to, n, 0.55f, WALL_DARK_RGB,
                (int) (240 * fade), seed, 17, 0.55f);
        jaggedLayer(RenderType.lightning(), buffers, matrix, from, to, n, 1.1f, HAZE_RGB,
                (int) (58 * fade), seed, 17, 0.55f);
        jaggedLayer(RenderType.lightning(), buffers, matrix, from, to, n, 0.6f, BODY_RGB,
                (int) (125 * fade), seed, 31, 0.8f);
        jaggedLayer(RenderType.lightning(), buffers, matrix, from, to, n, 0.28f, BODY_RGB,
                (int) (185 * fade), seed, 43, 0.5f);
        // Core stays straight: a razor line of severed space (plus a soft halo).
        strip(buffers, matrix, from, to, n, 0.35f, (int) (110 * pulse * fade), CORE_RGB);
        strip(buffers, matrix, from, to, n, 0.14f, (int) (255 * pulse * fade), CORE_RGB);
    }

    /** One layer whose edge offset is perturbed by layered sine noise. */
    private static void jaggedLayer(RenderType type, MultiBufferSource buffers, Matrix4f matrix,
                                    Vec3 from, Vec3 to, Vec3 n, float halfWidth, int[] rgb, int alpha,
                                    long seed, double frequency, double jagAmount) {
        if (alpha <= 2) {
            return;
        }
        VertexConsumer consumer = buffers.getBuffer(type);
        Vec3 dir = to.subtract(from).normalize();
        int segments = 24;
        for (int k = 0; k < JAG_OFFSET.length; k++) {
            int a = (int) (alpha * JAG_ALPHA[k]);
            if (a <= 2) {
                continue;
            }
            Vec3 prevUp = null;
            Vec3 prevDown = null;
            for (int i = 0; i <= segments; i++) {
                float t = i / (float) segments;
                Vec3 base = from.add(to.subtract(from).scale(t));
                // Taper both ends so the tear pinches into reality.
                float taper = (float) Math.sin(Math.PI * Math.min(1f, Math.max(0.03f, t)));
                double jag = Mth.sin(t * (float) frequency + seed * 0.37f) * jagAmount
                        + Mth.sin(t * (float) (frequency * 2.7) - seed * 0.11f) * jagAmount * 0.5;
                float halfHere = halfWidth * taper + (float) jag * halfWidth * 0.35f;
                double off = halfWidth * JAG_OFFSET[k] * taper;
                Vec3 up = base.add(n.scale(off + halfHere));
                Vec3 down = base.add(n.scale(off - halfHere));
                if (prevUp != null) {
                    quad(consumer, matrix, prevUp, up, down, prevDown, a, rgb);
                }
                prevUp = up;
                prevDown = down;
            }
        }
    }

    /** Short bright shards bursting in random 3D directions from the rift —
     * escaping energy that reads as volume from any angle. */
    private static void crackShards(MultiBufferSource buffers, Matrix4f matrix,
                                    Vec3 center, Vec3 up, Vec3 side, Vec3 dir,
                                    long seed, float fade, float age, float halfSpan) {
        if (fade <= 0.01f) {
            return;
        }
        int count = 12;
        for (int s = 0; s < count; s++) {
            long h = seed * 0x9E3779B97F4A7C15L + s * 0xBF58476D1CE4E5B9L;
            h ^= h >>> 29;
            h ^= h >>> 15;
            float rx = (Math.abs(h % 2000) / 1000f - 1f);
            h = h * 0x9E3779B97F4A7C15L + 0x6A09E667F3BCC909L;
            h ^= h >>> 27;
            float ry = (Math.abs(h % 2000) / 1000f - 1f);
            h = h * 0xBF58476D1CE4E5B9L + 0x94D049BB133111EBL;
            h ^= h >>> 25;
            float rz = (Math.abs(h % 2000) / 1000f - 1f);

            Vec3 burst = side.scale(rx).add(up.scale(ry)).add(dir.scale(rz)).normalize();
            float len = 0.7f + Math.abs(h % 13) / 13f * 1.5f;
            float out = halfSpan * (0.35f + Math.abs(h % 11) / 11f * 0.5f);
            float flicker = 0.3f + 0.7f * Math.abs(Mth.sin(age * 0.5f + s * 2.1f));

            Vec3 base = center.add(burst.scale(out));
            Vec3 tip = base.add(burst.scale(len));
            strip(buffers, matrix, base, tip, burst, 0.1f, (int) (235 * flicker * fade), CRACK_RGB);
        }
    }

    private static void strip(MultiBufferSource buffers, Matrix4f matrix,
                              Vec3 from, Vec3 to, Vec3 n, float halfWidth, int alpha, int[] rgb) {
        if (alpha <= 2) {
            return;
        }
        VertexConsumer consumer = buffers.getBuffer(RenderType.lightning());
        Vec3 off = n.scale(halfWidth);
        quad(consumer, matrix,
                from.add(off), to.add(off), to.subtract(off), from.subtract(off),
                alpha, rgb);
    }

    private static Vec3 toLocalSpace(SpatialRiftProjectile entity, Vec3 worldPos, float partialTick) {
        double x = Mth.lerp(partialTick, entity.xOld, entity.getX()) - worldPos.x;
        double y = Mth.lerp(partialTick, entity.yOld, entity.getY()) - worldPos.y;
        double z = Mth.lerp(partialTick, entity.zOld, entity.getZ()) - worldPos.z;
        return new Vec3(x, y, z);
    }

    private static void quad(VertexConsumer consumer, Matrix4f matrix,
                             Vec3 a, Vec3 b, Vec3 c, Vec3 d, int alpha, int[] rgb) {
        // lightning render type culls back faces — emit both windings.
        vertex(consumer, matrix, a, alpha, rgb);
        vertex(consumer, matrix, b, alpha, rgb);
        vertex(consumer, matrix, c, alpha, rgb);
        vertex(consumer, matrix, d, alpha, rgb);
        vertex(consumer, matrix, a, alpha, rgb);
        vertex(consumer, matrix, d, alpha, rgb);
        vertex(consumer, matrix, c, alpha, rgb);
        vertex(consumer, matrix, b, alpha, rgb);
    }

    private static void vertex(VertexConsumer consumer, Matrix4f matrix, Vec3 pos, int alpha, int[] rgb) {
        consumer.addVertex(matrix, (float) pos.x, (float) pos.y, (float) pos.z)
                .setColor(rgb[0], rgb[1], rgb[2], alpha);
    }
}

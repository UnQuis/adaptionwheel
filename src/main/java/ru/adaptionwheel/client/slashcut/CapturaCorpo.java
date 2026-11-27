package ru.adaptionwheel.client.slashcut;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import ru.adaptionwheel.client.TimeWarpClient;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Captures a living entity's model quads while it is still being rendered.
 *
 * <p>The dispatcher mixin marks the entity being drawn; the first {@code ModelPart.render} of that
 * draw walks the model with {@link ModelPart#visit}, reads the real cube polygons and stores them in
 * world space. The texture is captured at the same moment, so both the cut halves and the time-warp
 * afterimages can be drawn with the mob's own skin.
 */
public final class CapturaCorpo {

    /** Beyond this only a time-warp zone can make a body worth capturing. */
    private static final double NEARBY_SQR = 64.0 * 64.0;
    private static final long STALE_MS = 5000L;

    private static final Map<Integer, Corpo> CACHE = new HashMap<>();
    private static final Map<Integer, Long> HIDDEN = new HashMap<>();
    private static final Map<Integer, Long> SEEN = new HashMap<>();
    private static LivingEntity current;
    private static boolean rootSeen;

    private static final Field CUBE_POLYGONS = field(ModelPart.Cube.class, "polygons");
    private static final Class<?> POLYGON = CUBE_POLYGONS == null ? null
            : CUBE_POLYGONS.getType().getComponentType();
    private static final Field POLYGON_VERTICES = field(POLYGON, "vertices");
    private static final Field POLYGON_NORMAL = field(POLYGON, "normal");
    private static final Class<?> VERTEX = POLYGON_VERTICES == null ? null
            : POLYGON_VERTICES.getType().getComponentType();
    private static final Field VERTEX_POS = field(VERTEX, "pos");
    private static final Field VERTEX_U = field(VERTEX, "u");
    private static final Field VERTEX_V = field(VERTEX, "v");

    private CapturaCorpo() {
    }

    public static void begin(LivingEntity entity) {
        current = entity != null && worthCapturing(entity) ? entity : null;
        rootSeen = false;
    }

    public static void end() {
        current = null;
        rootSeen = false;
    }

    /** Snapshot for a packet's entity id; the server may already have discarded the entity. */
    public static Corpo cached(int id) {
        return CACHE.get(id);
    }

    /** The public capture entry point. A removed entity or an empty mesh is not a cut target. */
    public static Corpo capturar(LivingEntity entity, float partial) {
        if (entity == null || entity.isRemoved() || !entity.isAlive()) {
            return null;
        }
        Corpo corpo = CACHE.get(entity.getId());
        return corpo == null || corpo.quadCount() == 0 ? null : corpo;
    }

    public static void hide(int id) {
        HIDDEN.put(id, Util.getMillis());
        CACHE.remove(id);
        SEEN.remove(id);
    }

    public static boolean isHidden(int id) {
        Long at = HIDDEN.get(id);
        if (at == null) {
            return false;
        }
        if (Util.getMillis() - at < 250L) {
            return true;
        }
        HIDDEN.remove(id);
        return false;
    }

    public static void clear() {
        CACHE.clear();
        HIDDEN.clear();
        SEEN.clear();
        current = null;
        rootSeen = false;
    }

    /** Drops snapshots of entities that stopped rendering, so a long session cannot leak bodies. */
    public static void prune() {
        long now = Util.getMillis();
        CACHE.entrySet().removeIf(entry -> {
            Long seen = SEEN.get(entry.getKey());
            return seen == null || now - seen > STALE_MS;
        });
        SEEN.entrySet().removeIf(entry -> now - entry.getValue() > STALE_MS);
        HIDDEN.entrySet().removeIf(entry -> now - entry.getValue() >= 250L);
    }

    /** Called from the ModelPart mixin on the first part of a marked entity render. */
    public static void captureRootIfNeeded(ModelPart part, PoseStack pose, int packedLight, int color) {
        if (current == null || rootSeen || !current.isAlive() || current.isRemoved()) {
            return;
        }
        rootSeen = true;
        LivingEntity entity = current;
        ResourceLocation texture = textureOf(entity);
        if (texture == null) {
            return;
        }

        List<Fonte> vertices = new ArrayList<>();
        List<int[]> quads = new ArrayList<>();
        var cameraView = Minecraft.getInstance().gameRenderer.getMainCamera();
        Matrix4f camera = new Matrix4f().translation(
                (float) cameraView.getPosition().x,
                (float) cameraView.getPosition().y,
                (float) cameraView.getPosition().z);
        // ModelPart poses are camera-relative, so their normals are too. Undo the camera rotation
        // once here; the renderer applies the current view rotation again when the copy is drawn.
        Quaternionf viewInverse = new Quaternionf(cameraView.rotation()).conjugate();

        // The pose at the root's render HEAD already carries the entity's translation, body yaw,
        // crouch/scale and all renderer transforms. Visiting with that same pose stores the body
        // exactly where the frame drew it, relative to the camera.
        part.visit(pose, (partPose, name, index, cube) -> {
            Matrix4f transform = new Matrix4f(camera).mul(partPose.pose());
            if (POLYGON_VERTICES == null) {
                addFallbackCube(vertices, quads, transform, partPose, viewInverse, cube, color,
                        packedLight);
                return;
            }
            try {
                Object[] polygons = (Object[]) CUBE_POLYGONS.get(cube);
                for (Object polygon : polygons) {
                    Object[] polygonVertices = (Object[]) POLYGON_VERTICES.get(polygon);
                    Vector3f normal = new Vector3f((Vector3f) POLYGON_NORMAL.get(polygon));
                    Vector3f worldNormal = partPose.transformNormal(normal, new Vector3f())
                            .rotate(viewInverse);
                    int base = vertices.size();
                    for (Object vertex : polygonVertices) {
                        Vector3f pos = (Vector3f) VERTEX_POS.get(vertex);
                        Vector3f world = transform.transformPosition(
                                pos.x() / 16f, pos.y() / 16f, pos.z() / 16f, new Vector3f());
                        vertices.add(new Fonte(world, VERTEX_U.getFloat(vertex), VERTEX_V.getFloat(vertex),
                                worldNormal, color, packedLight));
                    }
                    quads.add(range(base, polygonVertices.length));
                }
            } catch (ReflectiveOperationException ignored) {
                // A future model class without these fields still gets its cube bounds below.
                addFallbackCube(vertices, quads, transform, partPose, viewInverse, cube, color,
                        packedLight);
            }
        });

        if (!quads.isEmpty()) {
            CACHE.put(entity.getId(), Corpo.of(vertices, quads, texture));
            SEEN.put(entity.getId(), Util.getMillis());
        }
    }

    private static boolean worthCapturing(LivingEntity entity) {
        if (entity.isRemoved() || !entity.isAlive()) {
            return false;
        }
        Player player = Minecraft.getInstance().player;
        return TimeWarpClient.afeta(entity)
                || (player != null && entity.distanceToSqr(player) <= NEARBY_SQR);
    }

    @SuppressWarnings("unchecked")
    private static ResourceLocation textureOf(LivingEntity entity) {
        EntityRenderer<LivingEntity> renderer = (EntityRenderer<LivingEntity>)
                Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(entity);
        return renderer == null ? null : renderer.getTextureLocation(entity);
    }

    private static int[] range(int base, int length) {
        int[] indices = new int[length];
        for (int i = 0; i < length; i++) {
            indices[i] = base + i;
        }
        return indices;
    }

    /** Used only if reflection ever stops matching a future model class. */
    private static void addFallbackCube(List<Fonte> vertices, List<int[]> quads, Matrix4f transform,
                                        PoseStack.Pose pose, Quaternionf viewInverse,
                                        ModelPart.Cube cube, int color, int light) {
        for (var face : net.minecraft.core.Direction.values()) {
            float[][] corners = switch (face) {
                case DOWN -> new float[][]{{cube.minX, cube.minY, cube.minZ}, {cube.maxX, cube.minY, cube.minZ},
                        {cube.maxX, cube.minY, cube.maxZ}, {cube.minX, cube.minY, cube.maxZ}};
                case UP -> new float[][]{{cube.minX, cube.maxY, cube.minZ}, {cube.maxX, cube.maxY, cube.minZ},
                        {cube.maxX, cube.maxY, cube.maxZ}, {cube.minX, cube.maxY, cube.maxZ}};
                case NORTH -> new float[][]{{cube.minX, cube.minY, cube.minZ}, {cube.maxX, cube.minY, cube.minZ},
                        {cube.maxX, cube.maxY, cube.minZ}, {cube.minX, cube.maxY, cube.minZ}};
                case SOUTH -> new float[][]{{cube.minX, cube.minY, cube.maxZ}, {cube.maxX, cube.minY, cube.maxZ},
                        {cube.maxX, cube.maxY, cube.maxZ}, {cube.minX, cube.maxY, cube.maxZ}};
                case WEST -> new float[][]{{cube.minX, cube.minY, cube.minZ}, {cube.minX, cube.minY, cube.maxZ},
                        {cube.minX, cube.maxY, cube.maxZ}, {cube.minX, cube.maxY, cube.minZ}};
                case EAST -> new float[][]{{cube.maxX, cube.minY, cube.minZ}, {cube.maxX, cube.minY, cube.maxZ},
                        {cube.maxX, cube.maxY, cube.maxZ}, {cube.maxX, cube.maxY, cube.minZ}};
            };
            Vector3f normal = pose.transformNormal(face.step(), new Vector3f()).rotate(viewInverse);
            int base = vertices.size();
            for (int i = 0; i < 4; i++) {
                float[] c = corners[i];
                Vector3f p = transform.transformPosition(c[0] / 16f, c[1] / 16f, c[2] / 16f,
                        new Vector3f());
                vertices.add(new Fonte(p, (i == 1 || i == 2) ? 1f : 0f,
                        (i == 2 || i == 3) ? 1f : 0f, normal, color, light));
            }
            quads.add(new int[]{base, base + 1, base + 2, base + 3});
        }
    }

    private static Field field(Class<?> owner, String name) {
        if (owner == null) {
            return null;
        }
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException unavailable) {
            return null;
        }
    }
}

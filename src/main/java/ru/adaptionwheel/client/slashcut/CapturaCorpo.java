package ru.adaptionwheel.client.slashcut;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.Util;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Captures a living entity's model quads while it is still being rendered.
 *
 * <p>The dispatcher mixin marks the entity being drawn; the first {@code ModelPart.render} of that
 * draw walks the model with {@link ModelPart#visit} and rebuilds every cube face from its public
 * bounds. No private MixinExtras field access is needed, and a mob with no model (or one already
 * removed by the server) simply leaves no snapshot.
 */
public final class CapturaCorpo {

    private static final Map<Integer, Corpo> CACHE = new HashMap<>();
    private static final Map<Integer, Long> HIDDEN = new HashMap<>();
    private static LivingEntity current;
    private static boolean rootSeen;

    private CapturaCorpo() {
    }

    public static void begin(LivingEntity entity) {
        current = entity;
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
    }

    public static boolean isHidden(int id) {
        Long at = HIDDEN.get(id);
        return at != null && Util.getMillis() - at < 250L;
    }

    public static void clear() {
        CACHE.clear();
        HIDDEN.clear();
        current = null;
        rootSeen = false;
    }

    /** Called from the ModelPart mixin on the first part of a marked entity render. */
    public static void captureRootIfNeeded(ModelPart part) {
        if (current == null || rootSeen || !current.isAlive() || current.isRemoved()) {
            return;
        }
        rootSeen = true;
        List<Fonte> vertices = new ArrayList<>();
        List<int[]> quads = new ArrayList<>();
        Matrix4f world = new Matrix4f()
                .translation((float) current.getX(), (float) current.getY(), (float) current.getZ())
                .rotateY(-current.getYRot() * Mth.DEG_TO_RAD);
        part.visit(new PoseStack(), (pose, name, index, cube) -> {
            Matrix4f transform = new Matrix4f(world).mul(pose.pose());
            addFace(vertices, quads, transform, pose, cube.minX, cube.minY, cube.minZ,
                    cube.maxX, cube.maxY, cube.maxZ, Direction.DOWN);
            addFace(vertices, quads, transform, pose, cube.minX, cube.minY, cube.minZ,
                    cube.maxX, cube.maxY, cube.maxZ, Direction.UP);
            addFace(vertices, quads, transform, pose, cube.minX, cube.minY, cube.minZ,
                    cube.maxX, cube.maxY, cube.maxZ, Direction.NORTH);
            addFace(vertices, quads, transform, pose, cube.minX, cube.minY, cube.minZ,
                    cube.maxX, cube.maxY, cube.maxZ, Direction.SOUTH);
            addFace(vertices, quads, transform, pose, cube.minX, cube.minY, cube.minZ,
                    cube.maxX, cube.maxY, cube.maxZ, Direction.WEST);
            addFace(vertices, quads, transform, pose, cube.minX, cube.minY, cube.minZ,
                    cube.maxX, cube.maxY, cube.maxZ, Direction.EAST);
        });
        if (!quads.isEmpty()) {
            CACHE.put(current.getId(), Corpo.of(vertices, quads));
        }
    }

    private static void addFace(List<Fonte> vertices, List<int[]> quads, Matrix4f transform,
                                PoseStack.Pose pose, float minX, float minY, float minZ,
                                float maxX, float maxY, float maxZ, Direction face) {
        float[][] corners = switch (face) {
            case DOWN -> new float[][]{{minX, minY, minZ}, {maxX, minY, minZ},
                    {maxX, minY, maxZ}, {minX, minY, maxZ}};
            case UP -> new float[][]{{minX, maxY, minZ}, {maxX, maxY, minZ},
                    {maxX, maxY, maxZ}, {minX, maxY, maxZ}};
            case NORTH -> new float[][]{{minX, minY, minZ}, {maxX, minY, minZ},
                    {maxX, maxY, minZ}, {minX, maxY, minZ}};
            case SOUTH -> new float[][]{{minX, minY, maxZ}, {maxX, minY, maxZ},
                    {maxX, maxY, maxZ}, {minX, maxY, maxZ}};
            case WEST -> new float[][]{{minX, minY, minZ}, {minX, minY, maxZ},
                    {minX, maxY, maxZ}, {minX, maxY, minZ}};
            case EAST -> new float[][]{{maxX, minY, minZ}, {maxX, minY, maxZ},
                    {maxX, maxY, maxZ}, {maxX, maxY, minZ}};
        };
        Vector3f normal = pose.transformNormal(face.step(), new Vector3f());
        int base = vertices.size();
        for (int i = 0; i < 4; i++) {
            float[] c = corners[i];
            Vector3f p = transform.transformPosition(c[0] / 16f, c[1] / 16f, c[2] / 16f,
                    new Vector3f());
            vertices.add(new Fonte(p, (i == 1 || i == 2) ? 1f : 0f,
                    (i == 2 || i == 3) ? 1f : 0f, normal, 0xFFFFFFFF, 0xF000F0));
        }
        quads.add(new int[]{base, base + 1, base + 2, base + 3});
    }
}

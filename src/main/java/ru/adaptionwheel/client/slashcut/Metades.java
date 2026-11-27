package ru.adaptionwheel.client.slashcut;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.levelgen.Heightmap;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import ru.adaptionwheel.network.SlashCutPayload;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Client-only halves of a cut mob: clip the captured quads against the packet's plane, cap the two
 * open shells, then let them fall and fade. Nothing here is saved or sent back to the server.
 */
public final class Metades {

    private static final int MAX_GROUPS = 64;
    private static final int LIFE_TICKS = 90;
    private static final double NUDGE = 0.15;
    private static final double AREA_MIN = 1.0e-4;
    private static final List<Metade> ATIVAS = new ArrayList<>();

    private Metades() {
    }

    private static final class Metade {
        final List<Fonte[]> faces;
        final Vec3 origin;
        final ResourceLocation texture;
        Vec3 pos;
        Vec3 vel;
        final int tint;
        int age;

        Metade(List<Fonte[]> faces, Vec3 origin, Vec3 vel, int tint, ResourceLocation texture) {
            this.faces = faces;
            this.origin = origin;
            this.pos = origin;
            this.vel = vel;
            this.tint = tint;
            this.texture = texture;
        }
    }

    /** Builds the two halves from the snapshot taken before the server removed the entity. */
    public static void criar(SlashCutPayload p) {
        Corpo corpo = CapturaCorpo.cached(p.entityId());
        if (corpo == null || corpo.quadCount() == 0) {
            return;
        }
        if (ATIVAS.size() >= MAX_GROUPS) {
            ATIVAS.remove(0);
        }
        CapturaCorpo.hide(p.entityId());

        Vec3 normal = p.normal();
        if (normal.lengthSqr() < 1.0e-8) {
            Vec3 sweep = p.sweep();
            normal = sweep.lengthSqr() > 1.0e-8
                    ? sweep.cross(new Vec3(0.0, 1.0, 0.0)) : new Vec3(1.0, 0.0, 0.0);
        }
        if (normal.lengthSqr() < 1.0e-8) {
            normal = new Vec3(1.0, 0.0, 0.0);
        }
        normal = normal.normalize();

        Vec3 point = p.point();
        double plane = dot(point, normal);
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < corpo.vertexCount(); i++) {
            double f = dot(corpo.vertex(i).pos(), normal);
            min = Math.min(min, f);
            max = Math.max(max, f);
        }
        if (max - min < 1.0e-6) {
            return;
        }
        // A tangent plane leaves one side empty: nudge it 15% into the mesh so both halves exist.
        double low = min + NUDGE * (max - min);
        double high = max - NUDGE * (max - min);
        double target = plane < low ? low : (plane > high ? high : plane);
        point = point.add(normal.scale(target - plane));
        plane = target;

        List<Fonte[]> plus = new ArrayList<>();
        List<Fonte[]> minus = new ArrayList<>();
        List<Fonte> rim = new ArrayList<>();
        for (int q = 0; q < corpo.quadCount(); q++) {
            Fonte[] quad = corpo.vertices(corpo.quad(q));
            addPolygon(clip(quad, normal, plane, true), plus, rim);
            addPolygon(clip(quad, normal, plane, false), minus, rim);
        }
        if (plus.isEmpty() || minus.isEmpty()) {
            return;
        }
        plus = largestShell(plus);
        minus = largestShell(minus);
        if (plus.isEmpty() || minus.isEmpty()) {
            return;
        }

        List<Fonte[]> cap = cap(rim, normal);
        Vec3 center = Vec3.ZERO;
        for (Fonte f : rim) {
            center = center.add(vec(f.pos()));
        }
        center = rim.isEmpty() ? point : center.scale(1.0 / rim.size());
        Vec3 sweep = p.sweep();
        double jitter = (p.entityId() % 7) * 0.003;
        ATIVAS.add(new Metade(append(plus, cap), center,
                normal.scale(0.06 + jitter).add(sweep.scale(0.04)), 0xFFB8E6FF,
                corpo.texture()));
        ATIVAS.add(new Metade(append(minus, cap), center,
                normal.scale(-0.06 - jitter).add(sweep.scale(0.04)), 0xFF9FD4FF,
                corpo.texture()));
    }

    private static void addPolygon(List<Fonte> poly, List<Fonte[]> out, List<Fonte> rim) {
        if (poly.size() < 3) {
            return;
        }
        out.add(poly.toArray(new Fonte[0]));
        for (Fonte f : poly) {
            rim.add(f);
        }
    }

    private static List<Fonte[]> append(List<Fonte[]> faces, List<Fonte[]> cap) {
        List<Fonte[]> all = new ArrayList<>(faces);
        all.addAll(cap);
        return all;
    }

    private static Vec3 vec(Vector3fc v) {
        return new Vec3(v.x(), v.y(), v.z());
    }

    private static double dot(Vec3 a, Vec3 b) {
        return a.x * b.x + a.y * b.y + a.z * b.z;
    }

    private static double dot(Vector3fc a, Vec3 b) {
        return a.x() * b.x + a.y() * b.y + a.z() * b.z;
    }

    /** Sutherland–Hodgman against one plane; interpolated vertices keep UV/normal/colour. */
    private static List<Fonte> clip(Fonte[] poly, Vec3 normal, double plane, boolean positive) {
        List<Fonte> out = new ArrayList<>();
        for (int i = 0; i < poly.length; i++) {
            Fonte a = poly[i];
            Fonte b = poly[(i + 1) % poly.length];
            double da = dot(a.pos(), normal) - plane;
            double db = dot(b.pos(), normal) - plane;
            boolean inA = positive ? da >= 0.0 : da <= 0.0;
            boolean inB = positive ? db >= 0.0 : db <= 0.0;
            if (inA) {
                out.add(a);
            }
            if (inA != inB) {
                double t = da / (da - db);
                out.add(a.lerp(b, Mth.clamp((float) t, 0f, 1f)));
            }
        }
        return out;
    }

    /** Drop disconnected debris: keep the largest shell by shared (quantised) vertices. */
    private static List<Fonte[]> largestShell(List<Fonte[]> faces) {
        Map<String, Integer> ids = new HashMap<>();
        int[] root = new int[faces.size() * 4 + 4];
        for (int i = 0; i < root.length; i++) {
            root[i] = i;
        }
        List<String> keys = new ArrayList<>();
        List<int[]> groups = new ArrayList<>();
        for (Fonte[] face : faces) {
            int[] group = new int[face.length];
            for (int i = 0; i < face.length; i++) {
                String key = key(face[i].pos());
                Integer id = ids.get(key);
                if (id == null) {
                    id = keys.size();
                    ids.put(key, id);
                    keys.add(key);
                }
                group[i] = id;
            }
            for (int i = 1; i < group.length; i++) {
                union(root, group[0], group[i]);
            }
            groups.add(group);
        }
        Map<Integer, Integer> counts = new HashMap<>();
        for (int[] group : groups) {
            int r = find(root, group[0]);
            counts.merge(r, 1, Integer::sum);
        }
        Integer best = counts.entrySet().stream()
                .max(Comparator.comparingInt(Map.Entry::getValue))
                .map(Map.Entry::getKey).orElse(null);
        if (best == null) {
            return faces;
        }
        List<Fonte[]> kept = new ArrayList<>();
        for (int i = 0; i < groups.size(); i++) {
            if (find(root, groups.get(i)[0]) == best) {
                kept.add(faces.get(i));
            }
        }
        return kept.isEmpty() ? faces : kept;
    }

    private static String key(Vector3fc p) {
        return Math.round(p.x() * 1000.0) + "/" + Math.round(p.y() * 1000.0) + "/"
                + Math.round(p.z() * 1000.0);
    }

    private static int find(int[] roots, int i) {
        while (roots[i] != i) {
            roots[i] = roots[roots[i]];
            i = roots[i];
        }
        return i;
    }

    private static void union(int[] roots, int a, int b) {
        int ra = find(roots, a);
        int rb = find(roots, b);
        if (ra != rb) {
            roots[rb] = ra;
        }
    }

    /** Convex hull cap in the cut plane; polygons under the area guard are skipped. */
    private static List<Fonte[]> cap(List<Fonte> rim, Vec3 normal) {
        if (rim.size() < 3) {
            return List.of();
        }
        Vec3 u = normal.cross(new Vec3(0.0, 1.0, 0.0));
        if (u.lengthSqr() < 1.0e-8) {
            u = normal.cross(new Vec3(1.0, 0.0, 0.0));
        }
        u = u.normalize();
        Vec3 v = normal.cross(u).normalize();
        Vec3 axisU = u;
        Vec3 axisV = v;
        Map<String, Fonte> unique = new HashMap<>();
        for (Fonte f : rim) {
            unique.putIfAbsent(key(f.pos()), f);
        }
        List<Fonte> points = new ArrayList<>(unique.values());
        points.sort(Comparator.comparingDouble((Fonte f) -> dot(f.pos(), axisU))
                .thenComparingDouble(f -> dot(f.pos(), axisV)));
        if (points.size() < 3) {
            return List.of();
        }
        List<Fonte> lower = new ArrayList<>();
        for (Fonte f : points) {
            while (lower.size() >= 2
                    && cross2(lower.get(lower.size() - 2), lower.get(lower.size() - 1), f, u, v) <= 0.0) {
                lower.remove(lower.size() - 1);
            }
            lower.add(f);
        }
        List<Fonte> upper = new ArrayList<>();
        for (int i = points.size() - 1; i >= 0; i--) {
            Fonte f = points.get(i);
            while (upper.size() >= 2
                    && cross2(upper.get(upper.size() - 2), upper.get(upper.size() - 1), f, u, v) <= 0.0) {
                upper.remove(upper.size() - 1);
            }
            upper.add(f);
        }
        lower.remove(lower.size() - 1);
        upper.remove(upper.size() - 1);
        List<Fonte> hull = new ArrayList<>(lower);
        hull.addAll(upper);
        if (hull.size() < 3) {
            return List.of();
        }
        double area = 0.0;
        for (int i = 0; i < hull.size(); i++) {
            Fonte a = hull.get(i);
            Fonte b = hull.get((i + 1) % hull.size());
            area += dot(a.pos(), u) * dot(b.pos(), v) - dot(b.pos(), u) * dot(a.pos(), v);
        }
        if (Math.abs(area) * 0.5 < AREA_MIN) {
            return List.of();
        }
        List<Fonte[]> triangles = new ArrayList<>();
        for (int i = 1; i + 1 < hull.size(); i++) {
            triangles.add(new Fonte[]{hull.get(0), hull.get(i), hull.get(i + 1)});
        }
        return triangles;
    }

    private static double cross2(Fonte a, Fonte b, Fonte c, Vec3 u, Vec3 v) {
        double ax = dot(a.pos(), u), ay = dot(a.pos(), v);
        double bx = dot(b.pos(), u), by = dot(b.pos(), v);
        double cx = dot(c.pos(), u), cy = dot(c.pos(), v);
        return (bx - ax) * (cy - ay) - (by - ay) * (cx - ax);
    }

    /** Per-tick kinematics: gravity, a simple ground stop and a hard lifetime. */
    public static void tick() {
        if (ATIVAS.isEmpty()) {
            return;
        }
        ClientLevel level = Minecraft.getInstance().level;
        for (int i = ATIVAS.size() - 1; i >= 0; i--) {
            Metade m = ATIVAS.get(i);
            m.age++;
            if (m.age > LIFE_TICKS) {
                ATIVAS.remove(i);
                continue;
            }
            m.vel = new Vec3(m.vel.x, m.vel.y - 0.02, m.vel.z);
            m.pos = m.pos.add(m.vel);
            if (level != null) {
                int floor = level.getHeight(Heightmap.Types.MOTION_BLOCKING,
                        Mth.floor(m.pos.x), Mth.floor(m.pos.z));
                if (m.pos.y < floor + 0.05) {
                    m.pos = new Vec3(m.pos.x, floor + 0.05, m.pos.z);
                    m.vel = new Vec3(m.vel.x * 0.5, 0.0, m.vel.z * 0.5);
                }
            }
        }
    }

    /** Textured triangles in world space; the level stage pose carries the camera rotation only. */
    public static void desenhar(PoseStack pose, float partialTick) {
        if (ATIVAS.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        Vec3 camera = mc.gameRenderer.getMainCamera().getPosition();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        PoseStack.Pose view = pose.last();
        for (Metade m : ATIVAS) {
            float fade = Mth.clamp(1f - Math.max(0, m.age - 55) / (float) (LIFE_TICKS - 55), 0f, 1f);
            int alpha = (int) (0xFF * fade);
            Vec3 offset = m.pos.subtract(m.origin);
            RenderType type = RenderType.entityTranslucent(m.texture);
            VertexConsumer buffer = buffers.getBuffer(type);
            for (Fonte[] face : m.faces) {
                for (int i = 1; i + 1 < face.length; i++) {
                    emit(buffer, view, face[0], offset, camera, m.tint, alpha);
                    emit(buffer, view, face[i], offset, camera, m.tint, alpha);
                    emit(buffer, view, face[i + 1], offset, camera, m.tint, alpha);
                }
            }
            buffers.endBatch(type);
        }
    }

    /** Draws an untrimmed captured body; used by the Time Warp afterimages. */
    public static void desenharCorpo(VertexConsumer buffer, PoseStack.Pose view, Corpo corpo,
                                     Vec3 camera, Vec3 offset, int tint, int alpha) {
        for (int q = 0; q < corpo.quadCount(); q++) {
            int[] quad = corpo.quad(q);
            for (int i = 1; i + 1 < quad.length; i++) {
                emit(buffer, view, corpo.vertex(quad[0]), offset, camera, tint, alpha);
                emit(buffer, view, corpo.vertex(quad[i]), offset, camera, tint, alpha);
                emit(buffer, view, corpo.vertex(quad[i + 1]), offset, camera, tint, alpha);
            }
        }
    }

    private static void emit(VertexConsumer buffer, PoseStack.Pose view, Fonte f, Vec3 offset,
                             Vec3 camera, int tint, int alpha) {
        Vec3 p = vec(f.pos()).add(offset).subtract(camera);
        int r = (tint >> 16) & 0xFF;
        int g = (tint >> 8) & 0xFF;
        int b = tint & 0xFF;
        Vector3f normal = view.transformNormal(f.normal(), new Vector3f());
        buffer.addVertex(view.pose(), (float) p.x, (float) p.y, (float) p.z)
                .setColor((alpha << 24) | (r << 16) | (g << 8) | b)
                .setUv(f.u(), f.v())
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(f.light())
                .setNormal(normal.x(), normal.y(), normal.z());
    }

    public static void limpar() {
        ATIVAS.clear();
        CapturaCorpo.clear();
    }
}

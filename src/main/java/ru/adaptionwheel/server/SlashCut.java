package ru.adaptionwheel.server;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import ru.adaptionwheel.network.SlashCutPayload;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Server-authoritative cut geometry for a cursed slash sweep. */
public final class SlashCut {

    // shortcut: these come from AdaptionConfig in the lead's follow-up; config is off-limits in this zone.
    private static final float FRAC_MIN = 0.15f;
    private static final float FRAC_MAX = 0.85f;
    private static final float SCRATCH_DAMAGE = 4f;
    private static final int SCRATCH_FIRE_TICKS = 4 * 20;
    private static final int SCRATCH_THROTTLE_TICKS = 10;
    private static final double NOTIFY_RADIUS_SQ = 192.0 * 192.0;
    private static final Set<String> EXCLUDED_IDS = new HashSet<>();

    private static final Map<UUID, Long> LAST_SCRATCH = new HashMap<>();

    private SlashCut() {
    }

    /**
     * Where the cut plane falls inside the target's AABB, projected onto {@code normal}, 0..1.
     */
    public static float fraction(LivingEntity target, Vec3 eye, Vec3 normal) {
        AABB box = target.getBoundingBox();
        double dmin = Double.POSITIVE_INFINITY;
        double dmax = Double.NEGATIVE_INFINITY;
        for (double x : new double[]{box.minX, box.maxX}) {
            for (double y : new double[]{box.minY, box.maxY}) {
                for (double z : new double[]{box.minZ, box.maxZ}) {
                    double d = new Vec3(x, y, z).subtract(eye).dot(normal);
                    dmin = Math.min(dmin, d);
                    dmax = Math.max(dmax, d);
                }
            }
        }
        return dmax - dmin < 1e-6 ? 0.5f : (float) (-dmin / (dmax - dmin));
    }

    /** Attempt a cut. Returns true when the target was removed by the cut. */
    public static boolean cut(ServerLevel level, LivingEntity target, LivingEntity owner, Vec3 from, Vec3 to) {
        if (!(target instanceof Mob) || target instanceof PartEntity<?> || target instanceof Player) {
            return false;
        }
        if (target.isDeadOrDying() || target.isRemoved() || !target.isAlive()) {
            return false;
        }
        if (EXCLUDED_IDS.contains(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
                .getKey(target.getType()).toString())) {
            return false;
        }

        Vec3 eye = owner.getEyePosition();
        Vec3 dir = to.subtract(from);
        Vec3 normal = from.subtract(eye).cross(to.subtract(eye));
        if (from.distanceToSqr(to) < 0.0064 || normal.lengthSqr() < 1e-6) {
            // A slash that did not move still needs a plane. Use sweep x up, or a fixed axis if the
            // sweep is straight up/down.
            normal = dir.cross(new Vec3(0, 1, 0));
            if (normal.lengthSqr() < 1e-6) {
                normal = new Vec3(1, 0, 0);
            }
        }
        normal = normal.normalize();

        // The swept triangle (eye, from, to) has to cross the target box.
        if (!intersectsBox(eye, from, to, target.getBoundingBox())) {
            return false;
        }

        float frac = fraction(target, eye, normal);
        if (frac < FRAC_MIN || frac > FRAC_MAX) {
            scratch(level, target, owner);
            return false;
        }

        Vec3 center = target.getBoundingBox().getCenter();
        Vec3 point = center.subtract(normal.scale(center.subtract(eye).dot(normal)));
        SlashCutPayload.sendNear(level, target.position(),
                new SlashCutPayload(target.getId(), point, normal, dir, owner.getId()));

        // Loot, XP and Curios contents must exist before the entity is removed. die() is the
        // vanilla choke point for all three and fires LivingDeathEvent, which the mob-wheel save
        // hook uses to write the attachment back onto the stack Curios drops.
        target.setLastHurtByPlayer(owner instanceof ServerPlayer player ? player : null);
        DamageSource source = Wearers.damageSource(owner);
        target.setHealth(0f);
        target.die(source);
        target.discard();
        return true;
    }

    private static void scratch(ServerLevel level, LivingEntity target, LivingEntity owner) {
        long now = level.getGameTime();
        Long last = LAST_SCRATCH.get(target.getUUID());
        if (last != null && now - last < SCRATCH_THROTTLE_TICKS) {
            return;
        }
        LAST_SCRATCH.put(target.getUUID(), now);
        target.hurt(Wearers.damageSource(owner), SCRATCH_DAMAGE);
        target.setRemainingFireTicks(SCRATCH_FIRE_TICKS);
    }

    /** Sutherland-Hodgman clip of the sweep triangle against all six AABB planes. */
    private static boolean intersectsBox(Vec3 eye, Vec3 a, Vec3 b, AABB box) {
        List<Vec3> polygon = new ArrayList<>(3);
        polygon.add(eye);
        polygon.add(a);
        polygon.add(b);
        for (int axis = 0; axis < 3 && !polygon.isEmpty(); axis++) {
            polygon = clip(polygon, axis, coord(box, axis, false), true);
            polygon = clip(polygon, axis, coord(box, axis, true), false);
        }
        return !polygon.isEmpty();
    }

    private static double coord(AABB box, int axis, boolean max) {
        if (axis == 0) return max ? box.maxX : box.minX;
        if (axis == 1) return max ? box.maxY : box.minY;
        return max ? box.maxZ : box.minZ;
    }

    private static double coord(Vec3 v, int axis) {
        if (axis == 0) return v.x;
        if (axis == 1) return v.y;
        return v.z;
    }

    private static List<Vec3> clip(List<Vec3> polygon, int axis, double limit, boolean keepAbove) {
        List<Vec3> out = new ArrayList<>();
        for (int i = 0; i < polygon.size(); i++) {
            Vec3 current = polygon.get(i);
            Vec3 previous = polygon.get((i + polygon.size() - 1) % polygon.size());
            double cv = coord(current, axis);
            double pv = coord(previous, axis);
            boolean insideCurrent = keepAbove ? cv >= limit : cv <= limit;
            boolean insidePrevious = keepAbove ? pv >= limit : pv <= limit;
            if (insideCurrent) {
                if (!insidePrevious) {
                    out.add(intersect(previous, current, pv, cv, limit));
                }
                out.add(current);
            } else if (insidePrevious) {
                out.add(intersect(previous, current, pv, cv, limit));
            }
        }
        return out;
    }

    private static Vec3 intersect(Vec3 a, Vec3 b, double av, double bv, double limit) {
        double t = (limit - av) / (bv - av);
        return new Vec3(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t, a.z + (b.z - a.z) * t);
    }
}

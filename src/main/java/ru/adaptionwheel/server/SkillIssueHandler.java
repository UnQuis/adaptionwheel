package ru.adaptionwheel.server;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ArrowLooseEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import ru.adaptionwheel.AdaptionWheel;
import ru.adaptionwheel.category.Concepts;
import ru.adaptionwheel.config.AdaptionConfig;

@EventBusSubscriber(modid = AdaptionWheel.MODID)
public final class SkillIssueHandler {

    private SkillIssueHandler() {
    }

    @SubscribeEvent
    public static void onArrowLoose(ArrowLooseEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.level().isClientSide()) {
            return;
        }
        if (!AdaptionConfig.ENABLE_COMBAT.get() || !AdaptionEvents.isWearingWheel(player)) {
            return;
        }
        AdaptionEvents.startOrAccelerate(player, AdaptionEvents.dataOf(player),
                Concepts.COMBAT_SKILL_ISSUE,
                (int) (AdaptionConfig.OFFENSE_ANALYSIS_SECONDS.get() * 20), true);
    }

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof AbstractArrow arrow)
                || !(arrow.level() instanceof ServerLevel level)
                || arrow.tickCount > 400) {
            return;
        }
        if (!(arrow.getOwner() instanceof ServerPlayer shooter) || shooter.level().isClientSide()) {
            return;
        }
        if (!AdaptionConfig.SKILL_ISSUE_ENABLED.get()
                || !AdaptionEvents.isWearingWheel(shooter)
                || !AdaptionEvents.dataOf(shooter).active(Concepts.COMBAT_SKILL_ISSUE)) {
            return;
        }

        Vec3 motion = arrow.getDeltaMovement();
        double speed = motion.length();
        if (speed < 0.05) {
            return;
        }
        Vec3 dir = motion.scale(1.0 / speed);

        LivingEntity target = findPathCandidate(arrow, shooter, level, dir);
        if (target == null) {
            return;
        }

        Vec3 aim = target.position().add(0, target.getBbHeight() * 0.5, 0).subtract(arrow.position()).normalize();
        double strength = AdaptionConfig.SKILL_ISSUE_STRENGTH.get();
        Vec3 steered = dir.scale(1.0 - strength).add(aim.scale(strength));
        if (steered.lengthSqr() < 1.0E-4) {
            return;
        }
        arrow.setDeltaMovement(steered.normalize().scale(speed));
        arrow.syncVelocity = true;
    }

    private static LivingEntity findPathCandidate(AbstractArrow arrow, ServerPlayer shooter,
                                                  ServerLevel level, Vec3 dir) {
        double radius = AdaptionConfig.SKILL_ISSUE_RADIUS.get();
        double maxDistance = AdaptionConfig.SKILL_ISSUE_MAX_DISTANCE.get();

        LivingEntity best = null;
        double bestPerp = radius;
        var box = arrow.getBoundingBox().inflate(maxDistance);
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, box, e -> isValidTarget(e, shooter))) {
            Vec3 rel = entity.position().add(0, entity.getBbHeight() * 0.5, 0).subtract(arrow.position());
            double along = rel.dot(dir);
            if (along < -0.5 || along > maxDistance) {
                continue;
            }
            double perp = rel.subtract(dir.scale(along)).length()
                    - Math.max(0.35, entity.getBbWidth() * 0.5);
            if (perp < bestPerp) {
                bestPerp = perp;
                best = entity;
            }
        }
        return best;
    }

    private static boolean isValidTarget(Entity entity, ServerPlayer shooter) {
        if (!(entity instanceof LivingEntity living)
                || !living.isAlive()
                || living.isSpectator()
                || living == shooter
                || living.isAlliedTo(shooter)) {
            return false;
        }

        if (living instanceof OwnableEntity owned && owned.getOwner() == shooter) {
            return false;
        }
        return true;
    }
}

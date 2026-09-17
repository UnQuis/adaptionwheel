package ru.adaptionwheel.server;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.EnderDragonPart;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.projectile.Projectile;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.entity.PartEntity;

/**
 * Resolves boss entities from damage sources, projectiles and multi-part bodies.
 * Covers vanilla (EnderDragonPart), NeoForge generic parts (used by Draconic
 * Evolution's Chaos Guardian) and projectile owners.
 */
public final class BossHelper {

    private BossHelper() {
    }

    public static boolean isBoss(LivingEntity entity) {
        if (entity instanceof WitherBoss || entity instanceof EnderDragon || entity instanceof Warden) {
            return true;
        }
        if (DraconicCompat.isGuardian(entity)) {
            return true;
        }
        return entity.getType().is(Tags.EntityTypes.BOSSES);
    }

    /**
     * Unwraps multi-part bodies and projectiles down to the underlying LivingEntity.
     * Handles the vanilla EnderDragonPart, any NeoForge {@link PartEntity} subclass
     * (e.g. DraconicGuardianPartEntity) and projectile owners. Returns null when
     * no living root can be found.
     */
    public static LivingEntity resolveLiving(Entity entity) {
        if (entity == null) {
            return null;
        }
        if (entity instanceof EnderDragonPart part) {
            return part.parentMob;
        }
        if (entity instanceof PartEntity<?> part && part.getParent() instanceof LivingEntity parent) {
            return parent;
        }
        if (entity instanceof Projectile projectile) {
            return resolveLiving(projectile.getOwner());
        }
        return entity instanceof LivingEntity living ? living : null;
    }

    /** Like {@link #resolveLiving} but only returns entities recognized as bosses. */
    public static LivingEntity resolveBoss(Entity entity) {
        LivingEntity living = resolveLiving(entity);
        return living != null && isBoss(living) ? living : null;
    }

    public static LivingEntity resolveBossFromSource(DamageSource source) {
        LivingEntity direct = resolveBoss(source.getDirectEntity());
        if (direct != null) {
            return direct;
        }
        return resolveBoss(source.getEntity());
    }
}

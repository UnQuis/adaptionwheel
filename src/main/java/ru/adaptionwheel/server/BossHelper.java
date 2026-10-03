package ru.adaptionwheel.server;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragonPart;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.projectile.Projectile;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.entity.PartEntity;

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
        return entity.getType().builtInRegistryHolder().is(Tags.EntityTypes.BOSSES);
    }

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

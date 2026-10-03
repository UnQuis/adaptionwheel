package ru.adaptionwheel.entity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import ru.adaptionwheel.sound.ModSounds;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class CursedSlashProjectile extends Projectile {

    private static final EntityDataAccessor<Float> ROLL = SynchedEntityData.defineId(
            CursedSlashProjectile.class, EntityDataSerializers.FLOAT);

    private float damage = 20f;
    private int maxHits = 2;
    private static final int TRAIL_LENGTH = 14;
    private final Set<UUID> alreadyHit = new HashSet<>();

    private final ArrayDeque<Vec3> trailPositions = new ArrayDeque<>();

    private float traveled;

    public CursedSlashProjectile(EntityType<? extends CursedSlashProjectile> type, Level level) {
        super(type, level);
        this.noPhysics = false;
    }

    public CursedSlashProjectile(Level level, LivingEntity owner, Vec3 velocity, float damage, int maxHits, float roll) {
        this(ModEntities.CURSED_SLASH.get(), level);
        setOwner(owner);
        setPos(owner.getX(), owner.getEyeY() - 0.2, owner.getZ());
        setDeltaMovement(velocity);
        this.damage = damage;
        this.maxHits = maxHits;
        entityData.set(ROLL, roll);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(ROLL, 0f);
    }

    public float getRoll() {
        return entityData.get(ROLL);
    }

    @Override
    public void tick() {
        super.tick();
        Vec3 motion = getDeltaMovement();

        if (level().isClientSide()) {
            trailPositions.addLast(position());
            while (trailPositions.size() > TRAIL_LENGTH) {
                trailPositions.removeFirst();
            }
        }
        traveled += (float) motion.length();

        HitResult hitResult = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
        if (hitResult.getType() != HitResult.Type.MISS) {
            onHit(hitResult);
            if (isRemoved()) {
                return;
            }
        }

        setPos(getX() + motion.x, getY() + motion.y, getZ() + motion.z);

        hitTargets();
    }

    public List<Vec3> getTrailSnapshot() {
        return new ArrayList<>(trailPositions);
    }

    public float getTraveled() {
        return traveled;
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        discard();
    }

    private void hitTargets() {
        Vec3 center = position();
        AABB box = new AABB(center, center).inflate(1.6);
        for (LivingEntity target : level().getEntitiesOfClass(LivingEntity.class, box, this::canHitEntity)) {
            if (!alreadyHit.add(target.getUUID())) {
                continue;
            }
            DamageSource source = damageSources().mobProjectile(this, getOwner() instanceof LivingEntity living ? living : null);
            if (level() instanceof ServerLevel serverLevel) {
                target.hurtServer(serverLevel, source, damage);
            }
            level().playSound(null, target.blockPosition(), ModSounds.SOE_HIT_2.get(), SoundSource.PLAYERS, 0.8f,
                    1f + (random.nextFloat() - 0.5f) * 0.2f);
            if (alreadyHit.size() >= maxHits) {
                discard();
                return;
            }
        }
    }

    @Override
    protected boolean canHitEntity(Entity target) {
        return target instanceof LivingEntity && target.isAlive() && target != getOwner()
                && !(target instanceof net.minecraft.world.entity.player.Player)
                && !alreadyHit.contains(target.getUUID());
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {

    }

    @Override
    public boolean isNoGravity() {
        return true;
    }

    @Override
    protected void readAdditionalSaveData(ValueInput tag) {
        super.readAdditionalSaveData(tag);
        damage = tag.getFloatOr("Damage", 20f);
        maxHits = tag.getIntOr("MaxHits", 2);
        entityData.set(ROLL, tag.getFloatOr("Roll", 0f));
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput tag) {
        super.addAdditionalSaveData(tag);
        tag.putFloat("Roll", entityData.get(ROLL));
        tag.putFloat("Damage", damage);
        tag.putInt("MaxHits", maxHits);
    }
}

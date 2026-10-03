package ru.adaptionwheel.entity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import ru.adaptionwheel.sound.ModSounds;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class SpatialRiftProjectile extends Projectile {

    public static final int LIFETIME_TICKS = 240;
    public static final int MAX_TARGETS = 5;

    private static final EntityDataAccessor<Float> ROLL = SynchedEntityData.defineId(
            SpatialRiftProjectile.class, EntityDataSerializers.FLOAT);

    private float bladeHalfWidth = 5f;

    private final Set<UUID> severed = new HashSet<>();

    private final ArrayDeque<Vec3> trailPositions = new ArrayDeque<>();
    private Vec3 lastPosition;
    private boolean playedCutSound;

    public SpatialRiftProjectile(EntityType<? extends SpatialRiftProjectile> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public SpatialRiftProjectile(Level level, LivingEntity owner, Vec3 velocity, float roll) {
        this(ModEntities.SPATIAL_RIFT.get(), level);
        setOwner(owner);
        setPos(owner.getX(), owner.getEyeY() - 0.2, owner.getZ());
        setDeltaMovement(velocity);
        this.lastPosition = position();
        entityData.set(ROLL, roll);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(ROLL, 0f);
    }

    public float getRoll() {
        return entityData.get(ROLL);
    }

    public float getBladeHalfWidth() {
        return bladeHalfWidth;
    }

    public List<Vec3> getTrailSnapshot() {
        return new ArrayList<>(trailPositions);
    }

    @Override
    public void tick() {
        super.tick();
        if (tickCount > LIFETIME_TICKS) {
            discard();
            return;
        }
        Vec3 motion = getDeltaMovement();

        if (level().isClientSide()) {
            trailPositions.addLast(position());
            while (trailPositions.size() > 22) {
                trailPositions.removeFirst();
            }
            setPos(getX() + motion.x, getY() + motion.y, getZ() + motion.z);
            return;
        }

        Vec3 from = lastPosition != null ? lastPosition : position();
        severAlongLine(from, position());

        lastPosition = position();
        setPos(getX() + motion.x, getY() + motion.y, getZ() + motion.z);

        ServerLevel serverLevel = (ServerLevel) level();
        if (random.nextInt(3) == 0) {
            serverLevel.sendParticles(ParticleTypes.ASH,
                    getX() + (random.nextDouble() - 0.5),
                    getY() + (random.nextDouble() - 0.5),
                    getZ() + (random.nextDouble() - 0.5),
                    1, 0.4, 0.4, 0.4, 0.01);
        }
        if (random.nextInt(12) == 0) {
            serverLevel.sendParticles(ParticleTypes.END_ROD, getX(), getY(), getZ(), 1, 0, 0, 0, 0);
        }
    }

    private void severAlongLine(Vec3 from, Vec3 to) {
        AABB sweep = new AABB(from, to).inflate(bladeHalfWidth);
        for (LivingEntity target : level().getEntitiesOfClass(LivingEntity.class, sweep, this::canSever)) {
            if (!severed.add(target.getUUID())) {
                continue;
            }
            performSingularitySever(target);
            if (severed.size() >= MAX_TARGETS) {
                break;
            }
        }
    }

    private void performSingularitySever(LivingEntity target) {
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        Entity owner = getOwner();
        var source = damageSources().playerAttack(owner instanceof net.minecraft.server.level.ServerPlayer sp ? sp : null);

        target.setInvulnerableTime(0);
        target.setHealth(0f);
        target.die(source);
        if (owner instanceof net.minecraft.server.level.ServerPlayer killer && target.getHealth() <= 0f) {
            killer.killedEntity(serverLevel, target, source);
        }

        serverLevel.sendParticles(ParticleTypes.SONIC_BOOM,
                target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(), 1, 0, 0, 0, 0);
        serverLevel.sendParticles(ParticleTypes.SQUID_INK,
                target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
                40, target.getBbWidth() * 0.8, target.getBbHeight() * 0.5, target.getBbWidth() * 0.8, 0.35);

        if (!playedCutSound) {
            playedCutSound = true;
            level().playSound(null, target.blockPosition(), ModSounds.DIMENSION_CUT.get(),
                    SoundSource.PLAYERS, 1.6f, 1.25f);
        }
    }

    private boolean canSever(LivingEntity target) {
        return target.isAlive()
                && !(target instanceof net.minecraft.world.entity.player.Player)
                && target != getOwner()
                && !severed.contains(target.getUUID());
    }

    @Override
    public boolean isNoGravity() {
        return true;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(ValueInput tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(ROLL, tag.getFloatOr("Roll", 0f));
        bladeHalfWidth = tag.getFloatOr("BladeHalfWidth", bladeHalfWidth);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput tag) {
        super.addAdditionalSaveData(tag);
        tag.putFloat("Roll", entityData.get(ROLL));
        tag.putFloat("BladeHalfWidth", bladeHalfWidth);
    }
}

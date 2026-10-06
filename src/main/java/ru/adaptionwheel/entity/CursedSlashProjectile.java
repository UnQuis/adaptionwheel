package ru.adaptionwheel.entity;

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
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
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

    /** Where the slash was at the start of this tick, used to sweep the whole segment it just
     *  travelled instead of only checking a bubble around where it ended up. */
    private Vec3 lastPosition;

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

    @Override
    public void tick() {
        super.tick();
        Vec3 motion = getDeltaMovement();
        boolean clientSide = level().isClientSide();

        if (clientSide) {
            // Purely cosmetic: just keep the trail for rendering. Everything that decides who
            // got hit lives in the server-only branch below.
            trailPositions.addLast(position());
            while (trailPositions.size() > TRAIL_LENGTH) {
                trailPositions.removeFirst();
            }
        }

        // Deterministic given the synced velocity, so it's safe (and necessary for the client's
        // own trail-length effects) to update this on both sides.
        traveled += (float) motion.length();

        // Block collision is deterministic from already-synced chunk data, so letting the client
        // predict it too just makes the slash stop against a wall without network lag.
        HitResult hitResult = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
        if (hitResult.getType() != HitResult.Type.MISS) {
            onHit(hitResult);
            if (isRemoved()) {
                return;
            }
        }

        Vec3 from = lastPosition != null ? lastPosition : position();
        Vec3 to = position();
        lastPosition = to;

        setPos(getX() + motion.x, getY() + motion.y, getZ() + motion.z);

        if (!clientSide) {
            // Server-authoritative on purpose: entity positions can differ slightly between the
            // client and the server, so letting both sides decide who got hit used to produce a
            // doubled hit sound and the slash occasionally vanishing a tick early on the client
            // while the server kept simulating it.
            hitTargets(from, to);
        }
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

    private void hitTargets(Vec3 from, Vec3 to) {
        // Sweep the segment travelled since the last tick, not just a bubble around the
        // endpoint: a fast slash moves further in one tick than a 1.6-block bubble ever covered,
        // so anything standing between the two points used to be skipped over entirely.
        AABB box = new AABB(from, to).inflate(1.6);
        for (LivingEntity target : level().getEntitiesOfClass(LivingEntity.class, box, this::canHitEntity)) {
            if (!alreadyHit.add(target.getUUID())) {
                continue;
            }
            DamageSource source = damageSources().mobProjectile(this, getOwner() instanceof LivingEntity living ? living : null);
            // This branch still has LivingEntity.hurt(DamageSource, float); hurtServer is 26.3's
            // replacement and does not exist here.
            if (!level().isClientSide()) {
                target.hurt(source, damage);
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
        // Intentionally empty: entity hits are resolved by the line sweep in hitTargets(), not
        // by the raycast. The raycast only exists here for block collision.
    }

    @Override
    public boolean isNoGravity() {
        return true;
    }

    @Override
    protected void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        damage = tag.getFloat("Damage");
        maxHits = tag.getInt("MaxHits");
        entityData.set(ROLL, tag.getFloat("Roll"));
    }

    @Override
    protected void addAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putFloat("Roll", entityData.get(ROLL));
        tag.putFloat("Damage", damage);
        tag.putInt("MaxHits", maxHits);
    }
}

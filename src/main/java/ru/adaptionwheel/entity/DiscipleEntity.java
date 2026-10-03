package ru.adaptionwheel.entity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import ru.adaptionwheel.AdaptionWheel;
import ru.adaptionwheel.data.PlayerAdaption;
import ru.adaptionwheel.server.AdaptionEvents;

import java.util.UUID;

public class DiscipleEntity extends Monster {

    public static final int SCALING_CAP = 40;

    private static final double BASE_HEALTH = 20.0D;
    private static final double HEALTH_PER_ADAPTATION = 1.5D;
    private static final double BASE_DAMAGE = 3.0D;
    private static final double DAMAGE_PER_ADAPTATION = 0.35D;
    private static final double ARMOUR_PER_ADAPTATION = 0.6D;

    private static final float FIRST_RESISTANCE = 0.25F;
    private static final float RESISTANCE_STEP = 0.15F;
    private static final float MAX_RESISTANCE = 0.80F;

    private String adaptedTo;
    private float resistance;

    public DiscipleEntity(EntityType<? extends DiscipleEntity> entityType, Level level) {
        super(entityType, level);
        this.xpReward = 20;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, BASE_HEALTH)
                .add(Attributes.ATTACK_DAMAGE, BASE_DAMAGE)
                .add(Attributes.MOVEMENT_SPEED, 0.26D)
                .add(Attributes.FOLLOW_RANGE, 32.0D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0D, true));
        this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.9D));
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers());
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    public void rescale() {
        ServerPlayer wearer = nearestWearer();
        int adaptations = wearer == null ? 0
                : scaledAdaptations(AdaptionEvents.dataOf(wearer).getAdaptCount());
        applyAttributes(adaptations);
    }

    private void applyAttributes(int adaptations) {
        var health = this.getAttribute(Attributes.MAX_HEALTH);
        if (health != null) {
            health.setBaseValue(healthFor(adaptations));
        }
        var damage = this.getAttribute(Attributes.ATTACK_DAMAGE);
        if (damage != null) {
            damage.setBaseValue(damageFor(adaptations));
        }
        var armour = this.getAttribute(Attributes.ARMOR);
        if (armour != null) {
            armour.setBaseValue(armourFor(adaptations));
        }
    }

    public static int scaledAdaptations(int adaptCount) {
        return Math.max(0, Math.min(SCALING_CAP, adaptCount));
    }

    public static double healthFor(int adaptations) {
        return BASE_HEALTH + HEALTH_PER_ADAPTATION * scaledAdaptations(adaptations);
    }

    public static double damageFor(int adaptations) {
        return BASE_DAMAGE + DAMAGE_PER_ADAPTATION * scaledAdaptations(adaptations);
    }

    public static double armourFor(int adaptations) {
        return ARMOUR_PER_ADAPTATION * scaledAdaptations(adaptations);
    }

    public static float nextResistance(float current, boolean sameType) {
        if (!sameType) {
            return FIRST_RESISTANCE;
        }
        return Math.min(MAX_RESISTANCE, current + RESISTANCE_STEP);
    }

    private ServerPlayer nearestWearer() {
        if (!(this.level() instanceof ServerLevel level)) {
            return null;
        }
        ServerPlayer best = null;
        double bestSq = 32.0D * 32.0D;
        for (ServerPlayer player : level.getPlayers(p -> p.isAlive())) {
            if (!ru.adaptionwheel.SurfaceAdaptations.wearingWheel(player)) {
                continue;
            }
            double distSq = this.distanceToSqr(player);
            if (distSq < bestSq) {
                bestSq = distSq;
                best = player;
            }
        }
        return best;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {

        if (!this.isInvulnerableTo(level, source)) {
            String type = source.typeHolder().getRegisteredName();
            if (type != null) {
                this.resistance = nextResistance(this.resistance, type.equals(this.adaptedTo));
                if (!type.equals(this.adaptedTo)) {
                    this.adaptedTo = type;
                    announceAdapting(type);
                }
                if (this.resistance > 0.0F) {
                    amount *= 1.0F - this.resistance;
                }
            }
        }
        return super.hurtServer(level, source, amount);
    }

    private void announceAdapting(String damageType) {
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        Vec3 centre = this.position().add(0.0D, this.getBbHeight() * 0.5D, 0.0D);
        level.sendParticles(ParticleTypes.ENCHANT, centre.x, centre.y, centre.z,
                20, 0.4D, 0.5D, 0.4D, 0.05D);
        level.playSound(null, this.blockPosition(), SoundEvents.BEACON_ACTIVATE,
                SoundSource.HOSTILE, 0.5F, 1.6F);
    }

    public String adaptedTo() {
        return this.adaptedTo;
    }

    public float resistance() {
        return this.resistance;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (this.level() instanceof net.minecraft.server.level.ServerLevel && this.tickCount % 20 == 0) {
            rescale();
        }
    }
}

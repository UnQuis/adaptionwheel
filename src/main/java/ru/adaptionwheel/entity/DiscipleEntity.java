package ru.adaptionwheel.entity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
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

/**
 * Disciple of the Domain: the reason late game still has a fight that means something.
 *
 * <p>Until this existed the mod's answer to difficulty was entirely passive — a boss reflection, an
 * existence immunity — and none of it asked anything of the player. Worse, the wheel's whole
 * trajectory is upward: a player at two hundred adaptations is enormously stronger than the same
 * player at twenty, but the world they are in is exactly as dangerous as it was before. A Disciple
 * closes that. It measures the nearest wheel-wearer and scales to them, so a fresh player walks
 * past one and an experienced one has a real fight.</p>
 *
 * <p>It also <b>adapts</b>, which is the part that makes it a Disciple rather than a tougher
 * zombie. Every meaningful hit teaches it to shrug off that damage type, and it announces that it
 * has learned — so a fight that starts as a normal brawl turns into something where the player
 * has to change what they are throwing at it. That is the mod's own idea turned around and pointed
 * at the player, and it costs one field and one override.</p>
 *
 * <p>Scaling is capped. A wheel at five hundred adaptations would otherwise produce a monster
 * nothing in the game can scratch, which is not a harder fight, it is an unreachable one.</p>
 */
public class DiscipleEntity extends Monster {

    /** Adaptation count at which the scaling stops. Beyond this a harder fight is not reachable. */
    public static final int SCALING_CAP = 40;

    private static final double BASE_HEALTH = 20.0D;
    private static final double HEALTH_PER_ADAPTATION = 1.5D;
    private static final double BASE_DAMAGE = 3.0D;
    private static final double DAMAGE_PER_ADAPTATION = 0.35D;
    private static final double ARMOUR_PER_ADAPTATION = 0.6D;

    /** How much of a hit an already-learned damage type loses, and how fast that grows. */
    private static final float FIRST_RESISTANCE = 0.25F;
    private static final float RESISTANCE_STEP = 0.15F;
    private static final float MAX_RESISTANCE = 0.80F;

    /** The damage type this one has learned to shrug off, by registered id. Not synced. */
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

    /**
     * Measures the nearest wheel-wearer and sizes itself to them.
     *
     * <p>Called on spawn and once a second, because a player can wander into or out of range
     * without the Disciple being anything but a Disciple — and a Disciple that is suddenly a
     * pushover because the adapted player walked away would undo the whole point.</p>
     */
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

    // ---- the scaling itself, as pure functions so it can be pinned without a world

    /**
     * Adaptation count to scale against, clamped.
     *
     * <p>The cap is the whole reason a Disciple stays a fight rather than becoming an unreachable
     * one: the wheel goes past four hundred, and an uncapped monster would be several thousand
     * hit points of nothing happening.</p>
     */
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

    /**
     * Resistance after being hit by the same damage type it has already learned, or after
     * learning a new one.
     *
     * <p>Rebuilt rather than accumulated on a new type, so switching damage types resets the
     * conversation and the player is rewarded for noticing.</p>
     */
    public static float nextResistance(float current, boolean sameType) {
        if (!sameType) {
            return FIRST_RESISTANCE;
        }
        return Math.min(MAX_RESISTANCE, current + RESISTANCE_STEP);
    }

    /**
     * The closest player wearing a wheel within a generous radius, or {@code null}.
     *
     * <p>{@code getPlayers} is on ServerLevel rather than Level, so a client-side mob has no
     * business here at all — hence the early return rather than a cast that would throw.</p>
     */
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

    /**
     * Learns the damage type that keeps hitting it, and shrugs off that type from then on.
     *
     * <p>Resistance is rebuilt for each new type rather than accumulating, so a fight is a
     * conversation: keep throwing fire at it and it stops mattering, switch to something else and
     * it starts counting again. The announcement is the important half — a player who is not told
     * the Disciple adapted cannot know to change, and would just conclude the mod is broken.</p>
     */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!this.level().isClientSide && !this.isInvulnerableTo(source)) {
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
        return super.hurt(source, amount);
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

    /** The damage type it has learned, for a tooltip or a future renderer. */
    public String adaptedTo() {
        return this.adaptedTo;
    }

    public float resistance() {
        return this.resistance;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!this.level().isClientSide && this.tickCount % 20 == 0) {
            rescale();
        }
    }
}

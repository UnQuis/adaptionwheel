package ru.adaptionwheel.server;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import ru.adaptionwheel.AdaptionWheel;
import ru.adaptionwheel.ability.ActiveAbility;
import ru.adaptionwheel.category.Concepts;
import ru.adaptionwheel.config.AdaptionConfig;
import ru.adaptionwheel.data.PlayerAdaption;
import ru.adaptionwheel.network.AbilityCooldownPayload;
import ru.adaptionwheel.network.DiveImpactPayload;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Dive — the first active ability.
 *
 * <p>It is deliberately a server-authoritative state machine rather than a one-shot impulse. A
 * single negative velocity is immediately rewritten by the client's movement packet and by
 * vanilla gravity, so the player would get a short hop instead of a slam. The tick handler keeps
 * the downward velocity pinned until the player actually lands, cancels the fall distance every
 * tick (the ability is unlocked by adapting to falls, so the landing itself must not hurt), and
 * only then carves the crater. The impact frame is sent as a packet, because the client cannot
 * know when the server decided the landing happened.
 */
@EventBusSubscriber(modid = AdaptionWheel.MODID)
public final class DiveAbility {

    public static final String ID = "dive";

    private static final int MAX_TICKS = 120;

    private static final Map<UUID, Integer> ACTIVE = new HashMap<>();
    private static final Map<UUID, Long> READY_AT = new HashMap<>();

    private DiveAbility() {
    }

    public static ActiveAbility definition() {
        return new ActiveAbility(
                ID,
                Component.translatable("adaptionwheel.ability.dive.name"),
                Component.translatable("adaptionwheel.ability.dive.desc"),
                () -> new ItemStack(Items.MACE),
                0xFFB984FF,
                100,
                "R",
                List.of(Concepts.ENV_FALL, Concepts.MUTATION_FLIGHT));
    }

    /**
     * Whether this player may dive at all. One definition, used by {@link #start} and by the synced
     * ability list the HUD draws from, so the card and the server can never disagree about it.
     */
    public static boolean unlocked(PlayerAdaption data) {
        return data.active(Concepts.ENV_FALL) || data.active(Concepts.MUTATION_FLIGHT);
    }

    /** Starts a dive, returning false when a condition or the cooldown refuses it. */
    public static boolean start(ServerPlayer player) {
        if (!AdaptionConfig.DIVE_ENABLED.get()) {
            return false;
        }
        if (player.isSpectator() || player.isDeadOrDying() || player.isPassenger()
                || player.onGround() || player.onClimbable()
                || player.isInWaterOrBubble() || player.isInLava()) {
            return false;
        }
        if (!AdaptionEvents.isWearingWheel(player)) {
            return false;
        }
        PlayerAdaption data = AdaptionEvents.dataOf(player);
        boolean flight = data.active(Concepts.MUTATION_FLIGHT);
        if (!unlocked(data)) {
            return false;
        }
        if (!flight && player.fallDistance < AdaptionConfig.DIVE_MIN_FALL.get()) {
            return false;
        }

        long now = player.level().getGameTime();
        Long ready = READY_AT.get(player.getUUID());
        if (ready != null && now < ready) {
            return false;
        }
        READY_AT.put(player.getUUID(), now + AdaptionConfig.DIVE_COOLDOWN_TICKS.get());
        AbilityCooldownPayload.send(player, ID, AdaptionConfig.DIVE_COOLDOWN_TICKS.get());

        // A flying player has to give up the hover flag explicitly, or vanilla keeps applying
        // creative-style flight after the velocity impulse.
        if (player.getAbilities().flying) {
            player.getAbilities().flying = false;
            player.onUpdateAbilities();
        }

        ACTIVE.put(player.getUUID(), 0);
        pushDown(player);
        player.displayClientMessage(Component.translatable("adaptionwheel.dive.start"), true);
        player.level().playSound(null, player.blockPosition(), SoundEvents.WIND_CHARGE_BURST.value(),
                SoundSource.PLAYERS, 1.0f, 0.65f);
        return true;
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.level().isClientSide) {
            return;
        }
        Integer ticks = ACTIVE.get(player.getUUID());
        if (ticks == null) {
            return;
        }

        if (player.isDeadOrDying() || player.isSpectator() || !AdaptionEvents.isWearingWheel(player)) {
            clear(player);
            return;
        }
        if (player.isInWaterOrBubble() || player.isInLava() || player.onClimbable()) {
            clear(player);
            return;
        }
        if (player.onGround()) {
            clear(player);
            impact(player);
            return;
        }
        if (ticks >= MAX_TICKS) {
            clear(player);
            return;
        }

        pushDown(player);
        ACTIVE.put(player.getUUID(), ticks + 1);
    }

    /**
     * Pins the player's velocity downward and mirrors it to their own client.
     *
     * <p>{@code setDeltaMovement} alone does nothing for a player: movement is client-authoritative,
     * so the server's velocity is only read by the server's own prediction. The vanilla path that
     * pushes a velocity to the owning client is the motion packet, which is why it is sent directly
     * here instead of through {@code hurtMarked} — that flag is consumed once per tick by
     * {@code ServerEntity}, and relying on it would tie the dive to entity-tracking details.
     */
    private static void pushDown(ServerPlayer player) {
        player.setDeltaMovement(0.0, -AdaptionConfig.DIVE_SPEED.get(), 0.0);
        player.resetFallDistance();
        player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(player));
    }

    private static void impact(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        double radius = AdaptionConfig.DIVE_RADIUS.get();
        if (!Double.isFinite(radius) || radius <= 0.0) {
            return;
        }

        double x = player.getX();
        double y = player.getY();
        double z = player.getZ();
        int carved = carve(level, player, radius);

        float damage = (float) Math.max(0.0, AdaptionConfig.DIVE_DAMAGE.get());
        if (damage > 0.0f) {
            List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class,
                    player.getBoundingBox().inflate(radius),
                    entity -> entity != player && entity.isAlive() && !entity.isAlliedTo(player));
            for (LivingEntity target : targets) {
                if (target.isRemoved() || !target.isAlive()) {
                    continue;
                }
                if (target.hurt(player.damageSources().playerAttack(player), damage)) {
                    Vec3 away = target.position().subtract(player.position());
                    double horizontal = Math.max(0.25, Math.sqrt(away.x * away.x + away.z * away.z));
                    target.push(away.x / horizontal * 1.4, 0.55, away.z / horizontal * 1.4);
                }
            }
        }

        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, x, y + 0.2, z,
                2, 0.0, 0.0, 0.0, 0.0);
        level.sendParticles(ParticleTypes.CLOUD, x, y + 0.3, z,
                36, radius * 0.35, 0.25, radius * 0.35, 0.12);
        level.sendParticles(ParticleTypes.POOF, x, y + 0.2, z,
                24, radius * 0.45, 0.15, radius * 0.45, 0.06);
        level.sendParticles(ParticleTypes.CRIT, x, y + 0.5, z,
                50, radius * 0.4, 0.5, radius * 0.4, 0.35);

        BlockState dust = level.getBlockState(player.blockPosition().below());
        if (dust.isAir()) {
            dust = Blocks.DIRT.defaultBlockState();
        }
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, dust),
                x, y + 0.15, z, Math.min(120, 30 + carved * 3),
                radius * 0.45, 0.25, radius * 0.45, 0.18);

        level.playSound(null, player.blockPosition(), SoundEvents.MACE_SMASH_GROUND_HEAVY,
                SoundSource.PLAYERS, 1.6f, 0.8f);
        level.playSound(null, player.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(),
                SoundSource.PLAYERS, 0.8f, 0.55f);

        DiveImpactPayload.sendNear(player);
    }

    private static int carve(ServerLevel level, ServerPlayer player, double radius) {
        int max = AdaptionConfig.DIVE_MAX_BLOCKS.get();
        int r = (int) Math.ceil(radius);
        BlockPos center = player.blockPosition();
        double radiusSq = radius * radius;

        List<BlockPos> inside = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(
                center.offset(-r, -r, -r), center.offset(r, r, r))) {
            double dx = pos.getX() + 0.5 - player.getX();
            double dy = pos.getY() + 0.5 - player.getY();
            double dz = pos.getZ() + 0.5 - player.getZ();
            if (dx * dx + dy * dy + dz * dz <= radiusSq) {
                inside.add(pos.immutable());
            }
        }
        // Nearest-first, so a radius that lands more blocks than the cap allows still produces a
        // round bite out of the middle instead of whichever slice of the bounding box the
        // iterator reached before the budget ran out.
        inside.sort(Comparator.comparingDouble(pos ->
                pos.distToCenterSqr(player.getX(), player.getY(), player.getZ())));

        int carved = 0;
        for (BlockPos pos : inside) {
            BlockState state = level.getBlockState(pos);
            if (state.isAir() || !state.getFluidState().isEmpty()) {
                continue;
            }
            if (state.getDestroySpeed(level, pos) < 0.0f) {
                continue;
            }
            // Block entities (chests, spawners, the altar's neighbours) are left standing: a
            // crater should not silently delete a container and everything inside it.
            if (state.hasBlockEntity()) {
                continue;
            }
            if (level.destroyBlock(pos, false, player)) {
                carved++;
                if (carved >= max) {
                    break;
                }
            }
        }
        return carved;
    }

    private static void clear(ServerPlayer player) {
        ACTIVE.remove(player.getUUID());
    }

    public static void forget(UUID uuid) {
        ACTIVE.remove(uuid);
        READY_AT.remove(uuid);
    }

    public static boolean isDiving(ServerPlayer player) {
        return ACTIVE.containsKey(player.getUUID());
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            forget(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            forget(player.getUUID());
        }
    }
}

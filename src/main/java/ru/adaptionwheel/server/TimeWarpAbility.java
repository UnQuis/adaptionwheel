package ru.adaptionwheel.server;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import ru.adaptionwheel.AdaptionWheel;
import ru.adaptionwheel.ability.ActiveAbility;
import ru.adaptionwheel.config.AdaptionConfig;
import ru.adaptionwheel.data.PlayerAdaption;
import ru.adaptionwheel.network.AbilityCooldownPayload;
import ru.adaptionwheel.network.TimeWarpHitPayload;
import ru.adaptionwheel.network.TimeWarpStatePayload;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Time Warp — an active ability that decimates entity ticks inside a sphere around the wearer.
 *
 * <p>The world clock is logical milliseconds. A "slow tick" happens when that clock crosses a
 * 1000-unit boundary, so at speed 0.2 an entity inside the zone ticks once every five server
 * ticks while the owner keeps moving normally. Damage is buffered and applied when the zone ends;
 * neither the world's game speed nor {@code DimensionType} is touched.
 */
@EventBusSubscriber(modid = AdaptionWheel.MODID)
public final class TimeWarpAbility {

    public static final String ID = "time_warp";

    private static final class State {
        final UUID owner;
        final ServerLevel level;
        final BlockPos centre;
        final float radius;
        final float speed;
        final long endsAt;
        long clock;
        boolean tick;

        State(UUID owner, ServerLevel level, BlockPos centre, float radius, float speed, long endsAt) {
            this.owner = owner;
            this.level = level;
            this.centre = centre.immutable();
            this.radius = radius;
            this.speed = speed;
            this.endsAt = endsAt;
        }
    }

    private record PendingHit(float damage, boolean critical) {
    }

    private record PendingExplosion(Vec3 centre, float radius, Entity source,
                                    Level.ExplosionInteraction interaction) {
    }

    private static final Map<UUID, State> STATES = new HashMap<>();
    private static final Map<UUID, Long> READY_AT = new HashMap<>();
    private static final Map<UUID, Map<Integer, PendingHit>> PENDING_HITS = new HashMap<>();
    private static final Map<UUID, List<PendingExplosion>> PENDING_EXPLOSIONS = new HashMap<>();
    private static final Map<ResourceKey<Level>, Long> DAY_BEFORE = new HashMap<>();
    private static final Map<ResourceKey<Level>, Double> DAY_REST = new HashMap<>();

    private static Set<Integer> TICARAM = new HashSet<>();
    private static Set<Integer> TICARAM_ANTES = new HashSet<>();
    private static long clock;
    private static float velocity = 1f;
    private static boolean tickLento;
    private static boolean applying;

    private TimeWarpAbility() {
    }

    public static ActiveAbility definition() {
        return new ActiveAbility(
                ID,
                Component.translatable("adaptionwheel.ability.time_warp.name"),
                Component.translatable("adaptionwheel.ability.time_warp.desc"),
                () -> new ItemStack(Items.CLOCK),
                0xFF8FD8FF,
                Math.max(0, AdaptionConfig.TIME_WARP_COOLDOWN_SECONDS.get()) * 20,
                "R",
                List.of());
    }

    /** Deliberately open for now: the adaptation gate replaces this body only. */
    public static boolean unlocked(PlayerAdaption data) {
        return true;
    }

    public static boolean start(ServerPlayer player) {
        if (!AdaptionConfig.TIME_WARP_ENABLED.get() || player.isSpectator() || player.isDeadOrDying()
                || !AdaptionEvents.isWearingWheel(player)) {
            return false;
        }
        long now = player.level().getGameTime();
        if (isOnCooldown(player.getUUID(), now)) {
            return false;
        }
        int cooldown = Math.max(0, AdaptionConfig.TIME_WARP_COOLDOWN_SECONDS.get()) * 20;
        READY_AT.put(player.getUUID(), now + cooldown);

        float speed = clampSpeed((float) (double) AdaptionConfig.TIME_WARP_SPEED.get());
        float radius = (float) Math.max(1.0, AdaptionConfig.TIME_WARP_RADIUS.get());
        long duration = Math.max(1, AdaptionConfig.TIME_WARP_DURATION_SECONDS.get()) * 20L;
        activate(player.getUUID(), (ServerLevel) player.level(), player.blockPosition(),
                radius, speed, now + duration);

        AbilityCooldownPayload.send(player, ID, cooldown);
        player.displayClientMessage(Component.translatable("adaptionwheel.time_warp.start"), true);
        return true;
    }

    /** Shared by {@link #start} and the gametests, which cannot log in a mock player with Curios. */
    public static void activate(UUID owner, ServerLevel level, BlockPos centre,
                                float radius, float speed, long endsAt) {
        State previous = STATES.remove(owner);
        if (previous != null) {
            applyBuffered(owner, previous);
        }
        STATES.put(owner, new State(owner, level, centre, radius, clampSpeed(speed), endsAt));
        PENDING_HITS.put(owner, new HashMap<>());
        PENDING_EXPLOSIONS.put(owner, new ArrayList<>());
        velocity = clampSpeed(speed);
        clock = 0L;
        tickLento = false;
        TICARAM.clear();
        TICARAM_ANTES.clear();
        DAY_BEFORE.clear();
        DAY_REST.clear();
        if (level != null) {
            State state = STATES.get(owner);
            TimeWarpStatePayload.sendTo(level, owner, state.speed, state.clock, state.tick,
                    state.centre, state.radius, true);
        }
    }

    public static boolean isActive(UUID owner) {
        return owner != null && STATES.containsKey(owner);
    }

    public static boolean isOnCooldown(UUID owner, long now) {
        Long ready = owner == null ? null : READY_AT.get(owner);
        return ready != null && now < ready;
    }

    /** Ends one zone, applying its buffered damage once before the state is forgotten. */
    public static void clear(UUID owner) {
        State state = STATES.remove(owner);
        if (state == null) {
            PENDING_HITS.remove(owner);
            PENDING_EXPLOSIONS.remove(owner);
            return;
        }
        applyBuffered(owner, state);
        PENDING_HITS.remove(owner);
        PENDING_EXPLOSIONS.remove(owner);
        if (STATES.isEmpty()) {
            clock = 0L;
            tickLento = false;
            TICARAM.clear();
            TICARAM_ANTES.clear();
            DAY_BEFORE.clear();
            DAY_REST.clear();
        }
    }

    /** Applies any hits accumulated while the owner's zone was active. */
    public static void applyBuffered(UUID owner) {
        State state = STATES.get(owner);
        if (state != null) {
            applyBuffered(owner, state);
        }
    }

    private static void applyBuffered(UUID owner, State state) {
        if (applying || state.level == null) {
            return;
        }
        Map<Integer, PendingHit> hits = PENDING_HITS.get(owner);
        List<PendingExplosion> explosions = PENDING_EXPLOSIONS.get(owner);
        if ((hits == null || hits.isEmpty()) && (explosions == null || explosions.isEmpty())) {
            return;
        }
        ServerPlayer source = state.level.getServer().getPlayerByUUID(owner);
        applying = true;
        try {
            if (explosions != null) {
                for (PendingExplosion blast : new ArrayList<>(explosions)) {
                    Vec3 at = blast.centre();
                    state.level.explode(blast.source(), at.x(), at.y(), at.z(), blast.radius(),
                            false, blast.interaction());
                }
                explosions.clear();
            }
            if (hits != null && source != null) {
                for (Map.Entry<Integer, PendingHit> hit : new ArrayList<>(hits.entrySet())) {
                    Entity target = state.level.getEntity(hit.getKey());
                    if (target instanceof LivingEntity living && living.isAlive()) {
                        PendingHit pending = hit.getValue();
                        if (living.hurt(source.damageSources().playerAttack(source), pending.damage())) {
                            // Damage is authoritative; this only carries the impact sound to clients.
                            PacketDistributor.sendToPlayersNear(state.level, null,
                                    living.getX(), living.getY(), living.getZ(), 64.0,
                                    new TimeWarpHitPayload(owner, living.getId(),
                                            (float) living.getX(), (float) living.getY(), (float) living.getZ(),
                                            pending.critical(), 1, pending.damage()));
                        }
                    }
                }
                hits.clear();
            }
        } finally {
            applying = false;
        }
    }

    /** Buffers one hit that would have landed inside an active zone. */
    public static void buffer(UUID owner, int targetId, float damage, boolean critical) {
        if (owner == null || damage <= 0f) {
            return;
        }
        PENDING_HITS.computeIfAbsent(owner, key -> new HashMap<>()).merge(targetId,
                new PendingHit(damage, critical),
                (oldHit, next) -> new PendingHit(oldHit.damage() + next.damage(),
                        oldHit.critical() || next.critical()));
    }

    /** Buffers one explosion whose centre is inside an active zone. */
    public static void bufferExplosion(UUID owner, Explosion explosion) {
        if (owner == null || explosion == null) {
            return;
        }
        Vec3 centre = explosion.center();
        PENDING_EXPLOSIONS.computeIfAbsent(owner, key -> new ArrayList<>()).add(new PendingExplosion(
                new Vec3(centre.x(), centre.y(), centre.z()), explosion.radius(),
                explosion.getDirectSourceEntity(),
                explosion.interactsWithBlocks() ? Level.ExplosionInteraction.BLOCK : Level.ExplosionInteraction.NONE));
    }

    public static void onServerTick(ServerTickEvent.Pre event) {
        Set<Integer> swap = TICARAM_ANTES;
        TICARAM_ANTES = TICARAM;
        TICARAM = swap;
        TICARAM.clear();

        float configured = clampSpeed((float) (double) AdaptionConfig.TIME_WARP_SPEED.get());
        float target = STATES.isEmpty() ? 1f : configured;
        float step = Math.max(0f, (1f - configured) / 4f);
        velocity = target < velocity ? Math.max(target, velocity - step)
                : Math.min(target, velocity + step);

        long before = clock;
        clock = advanceClock(clock, velocity);
        tickLento = clock / 1000L != before / 1000L;

        long now = event.getServer().getGameTime();
        for (UUID owner : new ArrayList<>(STATES.keySet())) {
            State state = STATES.get(owner);
            if (state == null) {
                continue;
            }
            if (now >= state.endsAt) {
                clear(owner);
                continue;
            }
            state.clock = clock;
            state.tick = tickLento;
            if (state.level != null) {
                TimeWarpStatePayload.sendTo(state.level, owner, state.speed, state.clock,
                        state.tick, state.centre, state.radius, true);
            }
        }

        for (ServerLevel level : event.getServer().getAllLevels()) {
            ResourceKey<Level> dimension = level.dimension();
            if (!level.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT)) {
                DAY_BEFORE.remove(dimension);
                DAY_REST.remove(dimension);
                continue;
            }
            long previous = DAY_BEFORE.getOrDefault(dimension, Long.MIN_VALUE);
            long current = level.getDayTime();
            if (previous != Long.MIN_VALUE && current - previous == 1L) {
                double rest = DAY_REST.getOrDefault(dimension, 0.0) + velocity;
                long whole = (long) Math.floor(rest);
                DAY_REST.put(dimension, rest - whole);
                level.setDayTime(previous + whole);
            }
            DAY_BEFORE.put(dimension, level.getDayTime());
        }
    }

    /** Adds {@code speed} logical milliseconds, clamped to the 0..1 band. */
    public static long advanceClock(long clock, float speed) {
        return clock + Math.round(clampSpeed(speed) * 1000f);
    }

    /** True when the logical clock just crossed a whole second. */
    public static boolean slowTick(long clock) {
        return clock > 0L && clock % 1000L == 0L;
    }

    /** Smooth per-frame progress inside the current logical second. */
    public static float partial(long clock, float speed, float partialTick) {
        float p = Float.isNaN(partialTick) ? 0f : Math.max(0f, Math.min(1f, partialTick));
        float value = (float) Math.floorMod(clock, 1000L) / 1000f + p * clampSpeed(speed);
        return Math.max(0f, Math.min(1f, value));
    }

    /** True when this entity loses the current server tick. */
    public static boolean pular(Entity entity) {
        if (entity == null || STATES.isEmpty() || !tickLento) {
            return false;
        }
        if (entity instanceof Player && !AdaptionConfig.TIME_WARP_PLAYERS.get()) {
            return false;
        }
        if (!inZone(entity)) {
            return false;
        }
        TICARAM.add(entity.getId());
        return true;
    }

    /** 0 = outside, 1 = ticked last round (force send), 2 = did not tick (delay send). */
    public static int estadoEnvio(Entity entity) {
        if (entity == null || STATES.isEmpty()
                || (entity instanceof Player && !AdaptionConfig.TIME_WARP_PLAYERS.get())
                || !inZone(entity)) {
            return 0;
        }
        return TICARAM_ANTES.contains(entity.getId()) ? 1 : 2;
    }

    private static boolean inZone(Entity entity) {
        for (State state : STATES.values()) {
            if (state.owner.equals(entity.getUUID())) {
                continue;
            }
            double dx = entity.getX() - state.centre.getX();
            double dy = entity.getY() - state.centre.getY();
            double dz = entity.getZ() - state.centre.getZ();
            if (dx * dx + dy * dy + dz * dz <= state.radius * state.radius) {
                return true;
            }
        }
        return false;
    }

    private static float clampSpeed(float speed) {
        if (Float.isNaN(speed)) {
            return 1f;
        }
        return Math.max(0f, Math.min(1f, speed));
    }

    /** Owner of the zone containing this entity, or null when the entity is not slowed. */
    private static UUID zoneOwner(Entity entity) {
        for (State state : STATES.values()) {
            if (state.owner.equals(entity.getUUID())) {
                return null;
            }
            double dx = entity.getX() - state.centre.getX();
            double dy = entity.getY() - state.centre.getY();
            double dz = entity.getZ() - state.centre.getZ();
            if (dx * dx + dy * dy + dz * dz <= state.radius * state.radius) {
                return state.owner;
            }
        }
        return null;
    }

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (applying || event.getEntity().level().isClientSide) {
            return;
        }
        LivingEntity target = event.getEntity();
        UUID owner = zoneOwner(target);
        if (owner == null) {
            return;
        }
        event.setCanceled(true);
        // 1.21.1's DamageSource has no critical flag; the payload keeps the field for future use.
        buffer(owner, target.getId(), event.getAmount(), false);
    }

    @SubscribeEvent
    public static void onExplosionStart(ExplosionEvent.Start event) {
        if (applying || !(event.getLevel() instanceof ServerLevel)) {
            return;
        }
        Explosion explosion = event.getExplosion();
        Vec3 centre = explosion.center();
        for (State state : STATES.values()) {
            if (centre.distanceToSqr(new Vec3(state.centre.getX(), state.centre.getY(), state.centre.getZ()))
                    <= state.radius * state.radius) {
                event.setCanceled(true);
                bufferExplosion(state.owner, explosion);
                return;
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        clear(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        clear(event.getEntity().getUUID());
    }
}

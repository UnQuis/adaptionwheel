package ru.adaptionwheel.client;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Deque;
import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import ru.adaptionwheel.AdaptionWheel;
import ru.adaptionwheel.network.TimeWarpHitPayload;
import ru.adaptionwheel.network.TimeWarpStatePayload;

/**
 * Client mirror of all Time Warp zones visible in the current dimension.
 * The server remains authoritative; this state is used only for rendering and tick decimation.
 */
@EventBusSubscriber(modid = AdaptionWheel.MODID, value = Dist.CLIENT)
public final class TimeWarpClient {

    private record Zone(float speed, long clock, BlockPos centre, float radius, long lastSeenTick) {
    }

    private static final int MAX_PENDING_TICKS = 10;
    private static final Map<UUID, Zone> ZONES = new HashMap<>();
    private static final Deque<Boolean> PENDING_TICKS = new ArrayDeque<>();
    private static ResourceKey<Level> dimension;
    private static long lastQueuedClock = Long.MIN_VALUE;
    private static boolean tickAgora;
    private static long clientTick;

    private TimeWarpClient() {
    }

    public static boolean isActive() {
        return !ZONES.isEmpty();
    }

    public static void onState(TimeWarpStatePayload payload) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        ResourceKey<Level> current = mc.level.dimension();
        if (dimension != null && !dimension.equals(current)) {
            ZONES.clear();
            PENDING_TICKS.clear();
            lastQueuedClock = Long.MIN_VALUE;
            tickAgora = false;
        }
        dimension = current;
        if (payload.active()) {
            ZONES.put(payload.owner(), new Zone(payload.speed(), payload.clock(),
                    payload.centre(), payload.radius(), clientTick));
        } else {
            ZONES.remove(payload.owner());
        }
        // All zones share one logical clock. Multiple owner packets for the same clock must
        // contribute only one verdict, otherwise a client with several zones advances too fast.
        if (payload.clock() != lastQueuedClock) {
            if (PENDING_TICKS.size() == MAX_PENDING_TICKS) {
                PENDING_TICKS.removeFirst();
            }
            PENDING_TICKS.addLast(payload.tick());
            lastQueuedClock = payload.clock();
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Pre event) {
        clientTick++;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || (dimension != null && !dimension.equals(mc.level.dimension()))) {
            ZONES.clear();
            PENDING_TICKS.clear();
            lastQueuedClock = Long.MIN_VALUE;
            dimension = mc.level == null ? null : mc.level.dimension();
            tickAgora = false;
        } else {
            // Zone updates are sent only to nearby players. Expire a mirror if its owner moves
            // out of tracking range so stale zones cannot affect a client that later returns.
            ZONES.entrySet().removeIf(entry -> clientTick - entry.getValue().lastSeenTick() > 10L);
            if (ZONES.isEmpty()) {
                PENDING_TICKS.clear();
                lastQueuedClock = Long.MIN_VALUE;
                tickAgora = false;
            }
        }
        // Minecraft may execute several catch-up client ticks after one packet drain.
        // Consume exactly one server verdict per client tick, rather than reusing the latest one.
        tickAgora = !PENDING_TICKS.isEmpty() && PENDING_TICKS.removeFirst();
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        reset();
    }

    private static void reset() {
        ZONES.clear();
        PENDING_TICKS.clear();
        dimension = null;
        lastQueuedClock = Long.MIN_VALUE;
        tickAgora = false;
        clientTick = 0L;
    }

    /** Substitute partialTick in [0,1] for the zone affecting the supplied entity. */
    public static float parcial(float partialTick) {
        Zone zone = ZONES.values().stream().findFirst().orElse(null);
        return zone == null ? Mth.clamp(partialTick, 0f, 1f)
                : parcial(zone, partialTick);
    }

    private static float parcial(Zone zone, float partialTick) {
        if (zone.speed() >= 1f) {
            return Mth.clamp(partialTick, 0f, 1f);
        }
        float phase = (float) (zone.clock() % 1000L) / 1000f;
        return Mth.clamp(phase + partialTick * zone.speed(), 0f, 1f);
    }

    private static Zone zoneFor(Entity entity) {
        if (entity == null || ZONES.isEmpty()) {
            return null;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || entity.level() != mc.level) {
            return null;
        }
        for (Map.Entry<UUID, Zone> entry : ZONES.entrySet()) {
            Zone zone = entry.getValue();
            if (entry.getKey().equals(entity.getUUID())) {
                continue;
            }
            if (entity.position().distanceToSqr(Vec3.atCenterOf(zone.centre())) <= zone.radius() * zone.radius()) {
                return zone;
            }
        }
        return null;
    }

    /** True when the entity is inside an active zone but is not the owner or a player. */
    public static boolean afeta(Entity entity) {
        if (entity == null || entity instanceof Player || entity.getVehicle() instanceof Player) {
            return false;
        }
        return zoneFor(entity) != null;
    }

    /** Client-side tick decimation: mirror the shared server slow-tick verdict. */
    public static boolean pular(Entity entity) {
        return afeta(entity) && !tickAgora;
    }

    public static boolean pularParticula(Vec3 position) {
        if (ZONES.isEmpty() || !tickAgora) {
            return !ZONES.isEmpty() && !tickAgora && insideAnyZone(position);
        }
        return false;
    }

    private static boolean insideAnyZone(Vec3 position) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || (dimension != null && !dimension.equals(mc.level.dimension()))) {
            return false;
        }
        for (Zone zone : ZONES.values()) {
            if (position.distanceToSqr(Vec3.atCenterOf(zone.centre())) <= zone.radius() * zone.radius()) {
                return true;
            }
        }
        return false;
    }

    /** Only slow entities get the substituted partialTick; the owner keeps vanilla movement. */
    public static float parcialPara(Entity entity, float partialTick) {
        Zone zone = zoneFor(entity);
        return zone == null ? partialTick : parcial(zone, partialTick);
    }

    /** Cheap stand-in for the original's OpenAL low-pass: quieter, deeper sound in a zone. */
    public static float somVolume(float volume) {
        float speed = ZONES.values().stream().map(Zone::speed).min(Float::compare).orElse(1f);
        return volume * Mth.clamp(speed, 0.2f, 1f);
    }

    public static float somTom(float pitch) {
        float speed = ZONES.values().stream().map(Zone::speed).min(Float::compare).orElse(1f);
        return pitch * Mth.clamp(speed, 0.5f, 1f);
    }

    /** A buffered hit finally landed; play its impact sound where the server says it happened. */
    public static void playHit(TimeWarpHitPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        float volume = Mth.clamp(payload.damage() / 10f * Math.max(1, payload.hits()), 0.1f, 1f);
        float pitch = payload.critical() ? 1.3f : 1f;
        mc.level.playSound(mc.player, payload.x(), payload.y(), payload.z(),
                SoundEvents.PLAYER_HURT, SoundSource.PLAYERS, volume, pitch);
    }
}

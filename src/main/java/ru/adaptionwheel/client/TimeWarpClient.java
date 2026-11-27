package ru.adaptionwheel.client;

import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
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
 * Client half of Time Warp: mirrors the server's logical clock, mirrors its decimator so the
 * client does not render entity ticks the server skipped, and substitutes {@code partialTick} for
 * smoothed movement. Everything here is display-only; the server owns the real state.
 */
@EventBusSubscriber(modid = AdaptionWheel.MODID, value = Dist.CLIENT)
public final class TimeWarpClient {

    private static final int MAX_PENDENTES = 4;

    private static UUID owner;
    private static float speed = 1f;
    private static long clock;
    private static BlockPos centre;
    private static float radius;
    private static boolean active;
    private static int pendentes;
    private static boolean tickAgora;

    private TimeWarpClient() {
    }

    public static boolean isActive() {
        return active;
    }

    public static void onState(TimeWarpStatePayload payload) {
        UUID newOwner = payload.owner();
        if (owner != null && !owner.equals(newOwner)) {
            reset();
        }
        owner = newOwner;
        speed = payload.speed();
        clock = payload.clock();
        centre = payload.centre();
        radius = payload.radius();
        active = payload.active();
        if (payload.tick()) {
            pendentes = Math.min(MAX_PENDENTES, pendentes + 1);
        }
        if (!active) {
            pendentes = 0;
            tickAgora = false;
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Pre event) {
        tickAgora = active && pendentes > 0;
        if (tickAgora) {
            pendentes--;
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        reset();
    }

    private static void reset() {
        owner = null;
        speed = 1f;
        clock = 0L;
        centre = null;
        radius = 0f;
        active = false;
        pendentes = 0;
        tickAgora = false;
    }

    /** Substitute partialTick in {@code [0,1]}; at real-time speed it is the vanilla value. */
    public static float parcial(float partialTick) {
        if (!active || speed >= 1f) {
            return Mth.clamp(partialTick, 0f, 1f);
        }
        float phase = (float) (clock % 1000L) / 1000f;
        return Mth.clamp(phase + partialTick * speed, 0f, 1f);
    }

    /** True when the entity is inside an active zone but is not the owner or a player. */
    public static boolean afeta(Entity entity) {
        if (!active || entity == null || centre == null) {
            return false;
        }
        if (owner != null && owner.equals(entity.getUUID())) {
            return false;
        }
        if (entity instanceof Player || entity.getVehicle() instanceof Player) {
            return false;
        }
        return entity.position().distanceToSqr(Vec3.atCenterOf(centre)) <= radius * radius;
    }

    /** Client-side tick decimation: mirror the server's slow ticks for displayed entities. */
    public static boolean pular(Entity entity) {
        return afeta(entity) && !tickAgora;
    }

    public static boolean pularParticula(Vec3 position) {
        return active && !tickAgora && centre != null
                && position.distanceToSqr(Vec3.atCenterOf(centre)) <= radius * radius;
    }

    /** Only slow entities get the substituted partialTick; the owner keeps vanilla movement. */
    public static float parcialPara(Entity entity, float partialTick) {
        return afeta(entity) ? parcial(partialTick) : partialTick;
    }

    /** Cheap stand-in for the original's OpenAL low-pass: quieter, deeper sound in a zone. */
    public static float somVolume(float volume) {
        return active ? volume * Mth.clamp(speed, 0.2f, 1f) : volume;
    }

    public static float somTom(float pitch) {
        return active ? pitch * Mth.clamp(speed, 0.5f, 1f) : pitch;
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

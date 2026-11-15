package ru.adaptionwheel.client;

import net.minecraft.Util;
import net.minecraft.util.Mth;
import ru.adaptionwheel.network.AbilityCooldownPayload;

import java.util.HashMap;
import java.util.Map;

/**
 * Client mirror of the server's ability cooldowns.
 *
 * <p>It stores an arrival time plus a duration instead of a tick counter: the server and the client
 * tick independently, and a counter decremented per frame would run at the frame rate. Everything
 * here is display state — the server re-checks every activation — so a missing packet costs a grey
 * bar, never a free ability.
 */
public final class ClientCooldowns {

    private static final Map<String, Long> READY_AT = new HashMap<>();
    private static final Map<String, Long> DURATION = new HashMap<>();

    private ClientCooldowns() {
    }

    public static void apply(AbilityCooldownPayload payload) {
        long duration = Math.max(0, payload.ticks()) * 50L;
        DURATION.put(payload.id(), duration);
        READY_AT.put(payload.id(), Util.getMillis() + duration);
    }

    public static boolean ready(String id) {
        Long readyAt = READY_AT.get(id);
        return readyAt == null || Util.getMillis() >= readyAt;
    }

    /** {@code 0} when the ability is ready, {@code 1} right after it was used. */
    public static float fraction(String id) {
        Long readyAt = READY_AT.get(id);
        if (readyAt == null) {
            return 0f;
        }
        long remaining = readyAt - Util.getMillis();
        if (remaining <= 0L) {
            return 0f;
        }
        long total = Math.max(1L, DURATION.getOrDefault(id, 1L));
        return Mth.clamp(remaining / (float) total, 0f, 1f);
    }

    public static void clear() {
        READY_AT.clear();
        DURATION.clear();
    }
}

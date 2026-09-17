package ru.adaptionwheel.client;

import ru.adaptionwheel.data.AdaptionTask;
import ru.adaptionwheel.network.AdaptionSyncPayload;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Client-side mirror of the server's adaptation state. */
public final class ClientAdaption {

    public static boolean wearingWheel;
    public static boolean adversityActive;
    public static int adaptedCount;
    public static int adversityTimer;
    public static int adversityCooldownTimer;
    public static float wheelRotation;
    public static final List<AdaptionTask> TASKS = new ArrayList<>();
    public static final Map<String, Integer> LEVELS = new HashMap<>();
    public static final Set<String> ADAPTED = new HashSet<>();
    public static final List<String> HISTORY = new ArrayList<>();
    /** Boss path -> accumulated ticks for existence adaptation progress. */
    public static final Map<String, Integer> EXISTENCE_PROGRESS = new HashMap<>();
    /** The tick threshold for existence adaptation completion (from config). */
    public static int existenceThreshold;

    /** Game time when the last server sync was received; used for smooth HUD progress. */
    private static long syncedAtGameTime;
    /** Rising edge of adversityActive: when the current adversity run started (for the face-flash overlay). */
    public static long adversityTriggeredAtGameTime = Long.MIN_VALUE;

    private ClientAdaption() {
    }

    public static void onSync(AdaptionSyncPayload payload) {
        if (payload.adversityActive() && !adversityActive) {
            adversityTriggeredAtGameTime = currentGameTime();
        }
        wearingWheel = payload.wearingWheel();
        adversityActive = payload.adversityActive();
        adaptedCount = payload.adaptedCount();
        adversityTimer = payload.adversityTimer();
        adversityCooldownTimer = payload.adversityCooldownTimer();
        wheelRotation = payload.wheelRotation();
        TASKS.clear();
        TASKS.addAll(payload.tasks());
        LEVELS.clear();
        LEVELS.putAll(payload.levels());
        ADAPTED.clear();
        ADAPTED.addAll(payload.adapted());
        HISTORY.clear();
        HISTORY.addAll(payload.history());
        EXISTENCE_PROGRESS.clear();
        EXISTENCE_PROGRESS.putAll(payload.existenceProgress());
        existenceThreshold = payload.existenceThreshold();
        syncedAtGameTime = currentGameTime();
    }

    public static boolean isAdapted(String concept) {
        return ADAPTED.contains(concept);
    }

    /** Smooth task progress: advances locally between 1 Hz server syncs. */
    public static float taskProgress(AdaptionTask task) {
        int elapsed = elapsedTicksSinceSync();
        int remaining = Math.max(0, task.timer - elapsed);
        return 1f - (float) remaining / Math.max(1, task.maxTimer);
    }

    /** Smooth adversity countdown progress. */
    public static float adversityProgress() {
        int elapsed = elapsedTicksSinceSync();
        int remaining = Math.max(0, adversityTimer - elapsed);
        return 1f - (float) remaining / 480f;
    }

    /** Adversity ticks remaining, advanced locally between 1 Hz server syncs. */
    public static int smoothedAdversityTimer() {
        return Math.max(0, adversityTimer - elapsedTicksSinceSync());
    }

    public static long gameTime() {
        return currentGameTime();
    }

    /** Smooth existence adaptation progress for a boss. */
    public static float existenceProgress(String bossPath) {
        Integer ticks = EXISTENCE_PROGRESS.get(bossPath);
        if (ticks == null || existenceThreshold <= 0) {
            return 0f;
        }
        int current = ticks + elapsedTicksSinceSync();
        return Math.min(1f, (float) current / existenceThreshold);
    }

    private static int elapsedTicksSinceSync() {
        long current = currentGameTime();
        return (int) Math.max(0L, current - syncedAtGameTime);
    }

    private static long currentGameTime() {
        var mc = net.minecraft.client.Minecraft.getInstance();
        return mc.level != null ? mc.level.getGameTime() : syncedAtGameTime;
    }
}

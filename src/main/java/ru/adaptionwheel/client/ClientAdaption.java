package ru.adaptionwheel.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import ru.adaptionwheel.AdaptionWheel;
import ru.adaptionwheel.data.AdaptionTask;
import ru.adaptionwheel.network.AdaptionSyncPayload;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@EventBusSubscriber(modid = AdaptionWheel.MODID, value = Dist.CLIENT)
public final class ClientAdaption {

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
    }

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

    public static final Map<String, Integer> EXISTENCE_PROGRESS = new HashMap<>();

    public static int existenceThreshold;

    public static boolean instabreakActive;

    public static int fistProgressDone;
    public static int fistProgressTotal;

    private static long syncedAtGameTime;

    public static long adversityTriggeredAtGameTime = Long.MIN_VALUE;

    private ClientAdaption() {
    }

    public static void clear() {
        wearingWheel = false;
        adversityActive = false;
        adaptedCount = 0;
        adversityTimer = 0;
        adversityCooldownTimer = 0;
        wheelRotation = 0.0F;
        TASKS.clear();
        LEVELS.clear();
        ADAPTED.clear();
        HISTORY.clear();
        EXISTENCE_PROGRESS.clear();
        existenceThreshold = 0;
        instabreakActive = false;
        fistProgressDone = 0;
        fistProgressTotal = 0;
        syncedAtGameTime = currentGameTime();
        adversityTriggeredAtGameTime = Long.MIN_VALUE;
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
        instabreakActive = payload.instabreakActive();
        fistProgressDone = payload.fistProgressDone();
        fistProgressTotal = payload.fistProgressTotal();
        syncedAtGameTime = currentGameTime();
    }

    public static boolean isAdapted(String concept) {
        return ADAPTED.contains(concept);
    }

    public static int fistTier() {
        if (!wearingWheel || !ADAPTED.contains(ru.adaptionwheel.category.Concepts.MUTATION_FIST)) {
            return -1;
        }
        return ru.adaptionwheel.category.FistTiers.reachTier(
                tier -> LEVELS.getOrDefault(ru.adaptionwheel.category.FistTiers.concept(tier), 0));
    }

    public static float taskProgress(AdaptionTask task) {
        int elapsed = progressElapsedTicks();
        int remaining = Math.max(0, task.timer - elapsed);
        return 1f - (float) remaining / Math.max(1, task.maxTimer);
    }

    public static float adversityProgress() {
        int elapsed = elapsedTicksSinceSync();
        int remaining = Math.max(0, adversityTimer - elapsed);
        return 1f - (float) remaining / 480f;
    }

    public static int smoothedAdversityTimer() {
        return Math.max(0, adversityTimer - elapsedTicksSinceSync());
    }

    public static long gameTime() {
        return currentGameTime();
    }

    public static float existenceProgress(String bossPath) {
        Integer ticks = EXISTENCE_PROGRESS.get(bossPath);
        if (ticks == null || existenceThreshold <= 0) {
            return 0f;
        }
        int current = ticks + progressElapsedTicks();
        return Math.min(1f, (float) current / existenceThreshold);
    }

    private static int progressElapsedTicks() {
        return adversityActive ? 0 : elapsedTicksSinceSync();
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

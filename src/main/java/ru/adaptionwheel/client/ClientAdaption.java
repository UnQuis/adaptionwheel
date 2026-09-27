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

/**
 * Client-side mirror of the server's adaptation state.
 *
 * <p>An event subscriber purely so it can wipe itself on disconnect. The mirror is all static and
 * nothing else resets it, which is the whole bug: see {@link #clear()}.</p>
 */
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
    /** Boss path -> accumulated ticks for existence adaptation progress. */
    public static final Map<String, Integer> EXISTENCE_PROGRESS = new HashMap<>();
    /** The tick threshold for existence adaptation completion (from config). */
    public static int existenceThreshold;
    /** Server-authoritative Instabreak stance (Fist Mastery's terminal level). */
    public static boolean instabreakActive;
    /** Blocks of the current fist tier's own material counted toward the next level. */
    public static int fistProgressDone;
    public static int fistProgressTotal;

    /** Game time when the last server sync was received; used for smooth HUD progress. */
    private static long syncedAtGameTime;
    /** Rising edge of adversityActive: when the current adversity run started (for the face-flash overlay). */
    public static long adversityTriggeredAtGameTime = Long.MIN_VALUE;

    private ClientAdaption() {
    }

    /**
     * Wipes the whole mirror back to "no world, no wheel, no adaptations".
     *
     * <p>Called on disconnect. Without it the mirror outlives the world it described, because
     * nothing else ever resets it: the server only pushes a sync while the player is wearing the
     * wheel, so a world where they are not wearing one sends nothing and the previous world's
     * levels sit in these static collections indefinitely. The HUD gates on
     * {@link #wearingWheel}, which was stale-true, so a freshly created world opened with the
     * previous world's HUD — a fist progress bar for a material the new player has never touched.
     * That is the report this exists to fix.</p>
     *
     * <p>Everything mutable is listed explicitly rather than looped, so a field added later
     * cannot be quietly forgotten: a new field that is not cleared here is exactly the same bug
     * again, one field narrower.</p>
     */
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

    /**
     * Highest fist tier reachable on the client mirror, or {@code -1} when the fist is locked.
     * Shares one implementation with the server so the two can never disagree.
     */
    public static int fistTier() {
        if (!wearingWheel || !ADAPTED.contains(ru.adaptionwheel.category.Concepts.MUTATION_FIST)) {
            return -1;
        }
        return ru.adaptionwheel.category.FistTiers.reachTier(
                tier -> LEVELS.getOrDefault(ru.adaptionwheel.category.FistTiers.concept(tier), 0));
    }

    /**
     * Smooth task progress: advances locally between 1 Hz server syncs.
     *
     * <p>Frozen while Adversity is running, matching the server, which stops decrementing task
     * timers. Without the guard the client kept filling the bar locally and then snapped it
     * backwards on the next sync — the "bars keep moving during adversity, then jump back"
     * report.</p>
     */
    public static float taskProgress(AdaptionTask task) {
        int elapsed = progressElapsedTicks();
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
        int current = ticks + progressElapsedTicks();
        return Math.min(1f, (float) current / existenceThreshold);
    }

    /** Task and existence progress are both frozen while Adversity is active. */
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

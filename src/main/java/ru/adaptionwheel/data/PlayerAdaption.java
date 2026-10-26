package ru.adaptionwheel.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class PlayerAdaption {

    public static final int MAX_LEVEL = 8;
    public static final int HISTORY_LIMIT = 30;

    public static final Codec<PlayerAdaption> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.unboundedMap(Codec.STRING, Codec.INT).fieldOf("levels").forGetter(d -> d.levels),
            Codec.STRING.listOf().fieldOf("adapted").forGetter(d -> new ArrayList<>(d.adapted)),
            AdaptionTask.CODEC.listOf().fieldOf("tasks").forGetter(d -> d.tasks),
            Codec.STRING.listOf().fieldOf("history").forGetter(d -> d.history),
            Codec.STRING.listOf().fieldOf("existence").forGetter(d -> new ArrayList<>(d.existenceAdapted)),
            Codec.unboundedMap(Codec.STRING, Codec.INT)
                    .optionalFieldOf("killCounts", new HashMap<>()).forGetter(d -> d.killCounts),
            Codec.unboundedMap(Codec.STRING, Codec.INT)
                    .optionalFieldOf("existenceProgress", new HashMap<>()).forGetter(d -> d.bossCombatTicks),
            Codec.INT.fieldOf("healingTimer").forGetter(d -> d.healingTimer),
            Codec.INT.fieldOf("adversityCooldown").forGetter(d -> d.adversityCooldownTimer),
            Codec.INT.fieldOf("adversityTimer").forGetter(d -> d.adversityTimer),
            Codec.BOOL.fieldOf("adversityActive").forGetter(d -> d.adversityActive),
            Codec.FLOAT.fieldOf("targetRotation").forGetter(d -> d.targetRotation),
            Codec.FLOAT.fieldOf("wheelRotation").forGetter(d -> d.wheelRotation),
            Codec.BOOL.optionalFieldOf("wasWearing", false).forGetter(d -> d.wasWearing),
            Codec.INT.optionalFieldOf("shedCount", 0).forGetter(d -> d.shedCount),
            Extras.CODEC.optionalFieldOf("extras", Extras.EMPTY)
                    .forGetter(d -> new Extras(new ArrayList<>(d.disabled), new ArrayList<>(d.cache),
                            Map.copyOf(d.progress), d.instabreak))
    ).apply(inst, PlayerAdaption::new));

    public final Map<String, Integer> levels = new HashMap<>();
    public final Set<String> adapted = new HashSet<>();
    public final List<AdaptionTask> tasks = new ArrayList<>();
    public final List<String> history = new ArrayList<>();
    public final Set<String> existenceAdapted = new HashSet<>();
    public final Set<String> disabled = new HashSet<>();

    public final List<net.minecraft.world.item.ItemStack> cache = new ArrayList<>();

    /**
     * Earned-but-not-yet-spent progress toward the next level, keyed by {@link Extras} helpers.
     *
     * <p>Persisted rather than held in a static map, because it is something the player earned and
     * would otherwise lose on every rejoin. Cleared by {@link #reset()} along with everything else.
     */
    public final Map<String, Integer> progress = new HashMap<>();

    /**
     * Whether the permanent Instabreak stance is switched on. Persisted rather than kept in a static
     * map: the stance is a player choice, and losing it on every rejoin is the bug this field fixes.
     */
    public boolean instabreak;

    public final Map<String, Integer> killCounts = new HashMap<>();

    public final Map<String, Integer> bossCombatTicks = new HashMap<>();

    public int healingTimer;
    public int adversityCooldownTimer;
    public int adversityTimer;
    public boolean adversityActive;
    public float targetRotation;
    public float wheelRotation;

    public boolean wasWearing;

    public transient net.minecraft.world.item.ItemStack equippedStack;

    public transient int thermalHealingTimer;

    /** Game time of the last Env_Void rescue; transient so a rejoin can rescue immediately. */
    public transient long lastVoidRescueTick = Long.MIN_VALUE;

    public int shedCount;

    public transient final java.util.Set<String> recentlyShed = new java.util.HashSet<>();

    public transient int lastTierAnnounced = -1;

    public transient float impactLastFallDistance;

    public transient boolean impactWasOnGround = true;

    private int cachedAdaptCount = -1;

    public PlayerAdaption() {
    }

    public PlayerAdaption(Map<String, Integer> levels, List<String> adapted, List<AdaptionTask> tasks,
                          List<String> history, List<String> existence, Map<String, Integer> killCounts,
                          Map<String, Integer> existenceProgress,
                          int healingTimer, int adversityCooldownTimer,
                          int adversityTimer, boolean adversityActive, float targetRotation, float wheelRotation,
                          boolean wasWearing, int shedCount, Extras extras) {
        this.shedCount = shedCount;
        this.levels.putAll(levels);
        this.adapted.addAll(adapted);
        this.tasks.addAll(tasks);
        this.history.addAll(history);
        this.existenceAdapted.addAll(existence);
        this.killCounts.putAll(killCounts);
        this.bossCombatTicks.putAll(existenceProgress);
        this.disabled.addAll(extras.disabled());
        this.cache.addAll(extras.cache());
        this.progress.putAll(extras.progress());
        this.instabreak = extras.instabreak();
        this.healingTimer = healingTimer;
        this.adversityCooldownTimer = adversityCooldownTimer;
        this.adversityTimer = adversityTimer;
        this.adversityActive = adversityActive;
        this.targetRotation = targetRotation;
        this.wheelRotation = wheelRotation;
        this.wasWearing = wasWearing;
    }

    public int kills(String concept) {
        return killCounts.getOrDefault(concept, 0);
    }

    public int level(String concept) {
        return levels.getOrDefault(concept, 0);
    }

    public boolean isEnabled(String concept) {
        return !disabled.contains(concept);
    }

    public boolean toggleEnabled(String concept) {
        if (!disabled.remove(concept)) {
            disabled.add(concept);
            return false;
        }
        return true;
    }

    public int levelOrZero(String concept) {
        return isEnabled(concept) ? level(concept) : 0;
    }

    public boolean active(String concept) {
        return isEnabled(concept) && adapted.contains(concept);
    }

    public boolean isAdapted(String concept) {
        return adapted.contains(concept);
    }

    public int getAdaptCount() {
        if (cachedAdaptCount < 0) {
            // Count concepts, not storage locations. A few keys (notably the debuff adaptations
            // granted by the All Adaptation item) live in both `adapted` and `levels`, and summing
            // the two collections counted those twice: the menu showed 483 while the HUD showed
            // 503 for the same wheel. The union is the number the player actually has.
            cachedAdaptCount = (int) java.util.stream.Stream.concat(
                    adapted.stream(),
                    levels.entrySet().stream()
                            .filter(entry -> entry.getValue() > 0)
                            .map(Map.Entry::getKey))
                    .distinct()
                    .count();
        }
        return cachedAdaptCount;
    }

    public void invalidateAdaptCount() {
        cachedAdaptCount = -1;
    }

    public void addHistory(String concept) {
        history.remove(concept);
        history.add(0, concept);
        while (history.size() > HISTORY_LIMIT) {
            history.remove(history.size() - 1);
        }
    }

    public void reset() {
        levels.clear();
        adapted.clear();
        tasks.clear();
        history.clear();
        existenceAdapted.clear();
        killCounts.clear();
        bossCombatTicks.clear();
        disabled.clear();
        cache.clear();
        progress.clear();
        instabreak = false;
        healingTimer = 0;
        adversityCooldownTimer = 0;
        adversityTimer = 0;
        adversityActive = false;
        invalidateAdaptCount();
    }

    /**
     * Resets the wheel-bound state for an unequip or a wheel swap, but keeps the four pieces of
     * state that belong to the player rather than to the item.
     *
     * <p>{@link #reset()} is the right thing for death and for an explicit debug reset, and it was
     * the wrong thing here: {@code cache}, {@code disabled}, {@code progress} and {@code instabreak}
     * are not stored in {@code WheelData}, so clearing them on unequip destroyed a full 54-slot cache,
     * every per-concept off switch, both fist counters and the Instabreak stance with nothing written
     * anywhere. The item cannot give them back on the next equip because they were never on it.
     *
     * <p>A wheel swap is the same exposure: the player is still wearing a wheel, and the new one may
     * grant neither {@code Env_Inventory} nor {@code Fist_Netherite}, but the cache and the counters
     * are still the player's. Keeping them here means the reward survives a swap and the switch stays
     * where the player put it.
     */
    public void resetForUnequip() {
        Extras preserved = new Extras(new ArrayList<>(disabled), new ArrayList<>(cache),
                new HashMap<>(progress), instabreak);
        reset();
        disabled.addAll(preserved.disabled());
        cache.addAll(preserved.cache());
        progress.putAll(preserved.progress());
        instabreak = preserved.instabreak();
    }

}

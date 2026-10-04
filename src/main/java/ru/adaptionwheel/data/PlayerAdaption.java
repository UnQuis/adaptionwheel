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
            Codec.STRING.listOf().optionalFieldOf("disabled", List.of()).forGetter(d -> new ArrayList<>(d.disabled))
    ).apply(inst, PlayerAdaption::new));

    public final Map<String, Integer> levels = new HashMap<>();
    public final Set<String> adapted = new HashSet<>();
    public final List<AdaptionTask> tasks = new ArrayList<>();
    public final List<String> history = new ArrayList<>();
    public final Set<String> existenceAdapted = new HashSet<>();
    public final Set<String> disabled = new HashSet<>();

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
                          boolean wasWearing, int shedCount, List<String> disabled) {
        this.shedCount = shedCount;
        this.levels.putAll(levels);
        this.adapted.addAll(adapted);
        this.tasks.addAll(tasks);
        this.history.addAll(history);
        this.existenceAdapted.addAll(existence);
        this.killCounts.putAll(killCounts);
        this.bossCombatTicks.putAll(existenceProgress);
        this.disabled.addAll(disabled);
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
            cachedAdaptCount = adapted.size() + (int) levels.values().stream().filter(l -> l > 0).count();
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
        healingTimer = 0;
        adversityCooldownTimer = 0;
        adversityTimer = 0;
        adversityActive = false;
        invalidateAdaptCount();
    }

    public void copyFrom(PlayerAdaption other) {
        levels.clear();
        levels.putAll(other.levels);
        adapted.clear();
        adapted.addAll(other.adapted);
        tasks.clear();
        tasks.addAll(other.tasks);
        history.clear();
        history.addAll(other.history);
        existenceAdapted.clear();
        existenceAdapted.addAll(other.existenceAdapted);
        killCounts.clear();
        killCounts.putAll(other.killCounts);
        bossCombatTicks.clear();
        bossCombatTicks.putAll(other.bossCombatTicks);
        disabled.clear();
        disabled.addAll(other.disabled);
        healingTimer = other.healingTimer;
        adversityCooldownTimer = other.adversityCooldownTimer;
        adversityTimer = other.adversityTimer;
        adversityActive = other.adversityActive;
        wasWearing = other.wasWearing;
        invalidateAdaptCount();
    }
}

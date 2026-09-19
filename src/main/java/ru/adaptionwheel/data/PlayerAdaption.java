package ru.adaptionwheel.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * World-persisted adaptation state of a player (stored as a NeoForge attachment).
 * Holds permanent levels, one-time adaptations, active analysis tasks,
 * adversity state, existence adaptation progress, and the wheel's rotation.
 */
public class PlayerAdaption {

    public static final int MAX_LEVEL = 8;
    public static final int HISTORY_LIMIT = 30;

    public static final MapCodec<PlayerAdaption> MAP_CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
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
            Codec.BOOL.optionalFieldOf("wasWearing", false).forGetter(d -> d.wasWearing)
    ).apply(inst, PlayerAdaption::new));

    public static final Codec<PlayerAdaption> CODEC = MAP_CODEC.codec();

    public final Map<String, Integer> levels = new HashMap<>();
    public final Set<String> adapted = new HashSet<>();
    public final List<AdaptionTask> tasks = new ArrayList<>();
    public final List<String> history = new ArrayList<>();
    public final Set<String> existenceAdapted = new HashSet<>();
    /** Kills per Drop_NPC_ concept, driving drop-rate levels. */
    public final Map<String, Integer> killCounts = new HashMap<>();
    /** Accumulated combat ticks per boss entity type for existence adaptation. */
    public final Map<String, Integer> bossCombatTicks = new HashMap<>();

    public int healingTimer;
    public int adversityCooldownTimer;
    public int adversityTimer;
    public boolean adversityActive;
    public float targetRotation;
    public float wheelRotation;

    /** Tracks whether the player was wearing the wheel last tick (for item-bound load/save). */
    public boolean wasWearing;

    /** Runtime identity of the currently equipped wheel stack — detects wheel swaps. Not serialized. */
    public transient net.minecraft.world.item.ItemStack equippedStack;

    /** Tick accumulator for the Thermal Mastery heat-scaled regeneration. Not serialized. */
    public transient int thermalHealingTimer;

    /** Previous-tick fall distance for Impact Mastery stomp detection. Not serialized. */
    public transient float impactLastFallDistance;
    /** Whether the player was grounded last tick (Impact Mastery landing edge). Not serialized. */
    public transient boolean impactWasOnGround = true;

    private int cachedAdaptCount = -1;

    PlayerAdaption() {
    }

    public PlayerAdaption(Map<String, Integer> levels, List<String> adapted, List<AdaptionTask> tasks,
                          List<String> history, List<String> existence, Map<String, Integer> killCounts,
                          Map<String, Integer> existenceProgress,
                          int healingTimer, int adversityCooldownTimer,
                          int adversityTimer, boolean adversityActive, float targetRotation, float wheelRotation,
                          boolean wasWearing) {
        this.levels.putAll(levels);
        this.adapted.addAll(adapted);
        this.tasks.addAll(tasks);
        this.history.addAll(history);
        this.existenceAdapted.addAll(existence);
        this.killCounts.putAll(killCounts);
        this.bossCombatTicks.putAll(existenceProgress);
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
        healingTimer = other.healingTimer;
        adversityCooldownTimer = other.adversityCooldownTimer;
        adversityTimer = other.adversityTimer;
        adversityActive = other.adversityActive;
        wasWearing = other.wasWearing;
        invalidateAdaptCount();
    }
}

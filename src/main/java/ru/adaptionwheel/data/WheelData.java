package ru.adaptionwheel.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Persistent adaptation data stored on the Mahoraga Wheel item via Data Component.
 * When the item is given to another player, they inherit these adaptations.
 * Includes running analysis tasks so progress survives death/unequip cycles.
 */
public record WheelData(
        Map<String, Integer> levels,
        List<String> adapted,
        List<String> existenceAdapted,
        Map<String, Integer> killCounts,
        List<String> history,
        List<AdaptionTask> tasks
) {
    public static final Codec<WheelData> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.unboundedMap(Codec.STRING, Codec.INT)
                    .optionalFieldOf("levels", new HashMap<>()).forGetter(WheelData::levels),
            Codec.STRING.listOf()
                    .optionalFieldOf("adapted", new ArrayList<>()).forGetter(WheelData::adapted),
            Codec.STRING.listOf()
                    .optionalFieldOf("existence", new ArrayList<>()).forGetter(WheelData::existenceAdapted),
            Codec.unboundedMap(Codec.STRING, Codec.INT)
                    .optionalFieldOf("killCounts", new HashMap<>()).forGetter(WheelData::killCounts),
            Codec.STRING.listOf()
                    .optionalFieldOf("history", new ArrayList<>()).forGetter(WheelData::history),
            AdaptionTask.CODEC.listOf()
                    .optionalFieldOf("tasks", new ArrayList<>()).forGetter(WheelData::tasks)
    ).apply(inst, WheelData::new));

    public static final WheelData EMPTY = new WheelData(
            Map.of(), List.of(), List.of(), Map.of(), List.of(), List.of());

    public int adaptCount() {
        return adapted.size() + (int) levels.values().stream().filter(l -> l > 0).count();
    }

    /** Copy persistent data FROM PlayerAdaption into a new WheelData. */
    public static WheelData fromPlayer(PlayerAdaption data) {
        return new WheelData(
                new HashMap<>(data.levels),
                new ArrayList<>(data.adapted),
                new ArrayList<>(data.existenceAdapted),
                new HashMap<>(data.killCounts),
                new ArrayList<>(data.history),
                new ArrayList<>(data.tasks)
        );
    }

    /** Load persistent data FROM this WheelData INTO a PlayerAdaption (runtime). */
    public void loadInto(PlayerAdaption data) {
        data.levels.clear();
        data.levels.putAll(levels);
        data.adapted.clear();
        data.adapted.addAll(adapted);
        data.existenceAdapted.clear();
        data.existenceAdapted.addAll(existenceAdapted);
        data.killCounts.clear();
        data.killCounts.putAll(killCounts);
        data.history.clear();
        data.history.addAll(history);
        data.tasks.clear();
        data.tasks.addAll(tasks);
        data.invalidateAdaptCount();
    }
}

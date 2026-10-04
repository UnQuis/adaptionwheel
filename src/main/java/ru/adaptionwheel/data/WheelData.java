package ru.adaptionwheel.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    public void loadInto(PlayerAdaption data) {
        levels.forEach((concept, level) -> data.levels.merge(concept, level, Math::max));
        data.adapted.addAll(adapted);
        data.existenceAdapted.addAll(existenceAdapted);
        killCounts.forEach((mob, count) -> data.killCounts.merge(mob, count, Math::max));
        for (String entry : history) {
            if (!data.history.contains(entry)) {
                data.history.add(entry);
            }
        }
        for (AdaptionTask task : tasks) {
            boolean running = false;
            for (AdaptionTask existing : data.tasks) {
                if (existing.concept.equals(task.concept)) {
                    running = true;
                    break;
                }
            }
            if (!running) {
                data.tasks.add(task);
            }
        }
        data.invalidateAdaptCount();
        migrateLegacyConcepts(data);
    }

    private static void migrateLegacyConcepts(PlayerAdaption data) {
        Integer copper = data.levels.remove("Fist_Copper");
        if (copper == null) {
            return;
        }
        int merged = Math.min(ru.adaptionwheel.data.PlayerAdaption.MAX_LEVEL,
                data.level("Fist_Iron") + copper);
        data.levels.put("Fist_Iron", merged);
    }
}

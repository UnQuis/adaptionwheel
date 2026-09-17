package ru.adaptionwheel.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** An active analysis task, mirroring the original mod's task system. */
public class AdaptionTask {

    public static final Codec<AdaptionTask> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.fieldOf("concept").forGetter(t -> t.concept),
            Codec.INT.fieldOf("timer").forGetter(t -> t.timer),
            Codec.INT.fieldOf("maxTimer").forGetter(t -> t.maxTimer)
    ).apply(inst, AdaptionTask::new));

    public String concept;
    public int timer;
    public int maxTimer;

    public AdaptionTask(String concept, int timer, int maxTimer) {
        this.concept = concept;
        this.timer = timer;
        this.maxTimer = maxTimer;
    }

    public float progress() {
        return 1f - (float) timer / maxTimer;
    }
}
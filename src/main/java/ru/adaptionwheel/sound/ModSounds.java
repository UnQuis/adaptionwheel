package ru.adaptionwheel.sound;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;
import ru.adaptionwheel.AdaptionWheel;

public final class ModSounds {

    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(Registries.SOUND_EVENT, AdaptionWheel.MODID);

    public static final DeferredHolder<SoundEvent, SoundEvent> ADAPT_VOICE =
            register("adapt_voice");

    public static final DeferredHolder<SoundEvent, SoundEvent> DIMENSION_CUT =
            register("dimension_cut");

    public static final DeferredHolder<SoundEvent, SoundEvent> SWING =
            register("swing");

    public static final DeferredHolder<SoundEvent, SoundEvent> SOE_HIT_1 =
            register("soe_hit1");

    public static final DeferredHolder<SoundEvent, SoundEvent> SOE_HIT_2 =
            register("soe_hit2");

    public static final DeferredHolder<SoundEvent, SoundEvent> REF =
            register("ref");

    private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(
                ResourceLocation.fromNamespaceAndPath(AdaptionWheel.MODID, name)));
    }

    private ModSounds() {
    }
}

package ru.adaptionwheel.effect;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import ru.adaptionwheel.AdaptionWheel;

public final class ModEffects {

    public static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create(Registries.MOB_EFFECT, AdaptionWheel.MODID);

    public static final DeferredHolder<MobEffect, WildReleaseEffect> WILD_RELEASE =
            EFFECTS.register("wild_release", WildReleaseEffect::new);

    public static final DeferredHolder<MobEffect, ResonanceEffect> RESONANCE =
            EFFECTS.register("resonance", ResonanceEffect::new);

    private ModEffects() {
    }
}

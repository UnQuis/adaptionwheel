package ru.adaptionwheel.effect;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import ru.adaptionwheel.AdaptionWheel;

/**
 * Status effects the mod adds.
 *
 * <p>Its own registry rather than a field on the mod class, matching {@code ModSounds} and
 * {@code ModItems}: the effect needs a class rather than a value, and keeping it here means the
 * mod class stays a list of registrations.</p>
 */
public final class ModEffects {

    public static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create(Registries.MOB_EFFECT, AdaptionWheel.MODID);

    /** The burst that shedding an adaptation pays out. */
    public static final DeferredHolder<MobEffect, WildReleaseEffect> WILD_RELEASE =
            EFFECTS.register("wild_release", WildReleaseEffect::new);

    /** Stacking buff for adapted players standing near each other. */
    public static final DeferredHolder<MobEffect, ResonanceEffect> RESONANCE =
            EFFECTS.register("resonance", ResonanceEffect::new);

    private ModEffects() {
    }
}

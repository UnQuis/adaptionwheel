package ru.adaptionwheel.advancement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import ru.adaptionwheel.category.WheelTier;
import ru.adaptionwheel.data.PlayerAdaption;

import java.util.Optional;

public class AdaptationTrigger extends SimpleCriterionTrigger<AdaptationTrigger.Instance> {

    public static final net.neoforged.neoforge.registries.DeferredRegister<CriterionTrigger<?>>
            TRIGGERS = net.neoforged.neoforge.registries.DeferredRegister.create(
                    net.minecraft.core.registries.Registries.TRIGGER_TYPE,
                    ru.adaptionwheel.AdaptionWheel.MODID);

    public static final net.neoforged.neoforge.registries.DeferredHolder<
            CriterionTrigger<?>, AdaptationTrigger> INSTANCE =
            TRIGGERS.register("adaptation", AdaptationTrigger::new);

    public static final String ID = "adaptation";

    public static void evaluate(net.minecraft.server.level.ServerPlayer player,
                                PlayerAdaption data) {

        INSTANCE.get().trigger(player, instance -> instance.matches(player, data));
    }

    @Override
    public Codec<Instance> codec() {
        return Instance.CODEC;
    }

    public record Instance(Optional<String> concept,
                           Optional<Integer> minLevel,
                           Optional<Integer> minAdaptCount,
                           Optional<Integer> minTier,
                           Optional<Integer> minSheds,
                           Optional<Boolean> anySynergy,
                           Optional<Boolean> anyResonance) implements SimpleInstance {

        public static final Codec<Instance> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                Codec.STRING.optionalFieldOf("concept").forGetter(Instance::concept),
                Codec.INT.optionalFieldOf("min_level").forGetter(Instance::minLevel),
                Codec.INT.optionalFieldOf("min_adapt_count").forGetter(Instance::minAdaptCount),
                Codec.INT.optionalFieldOf("min_tier").forGetter(Instance::minTier),
                Codec.INT.optionalFieldOf("min_sheds").forGetter(Instance::minSheds),
                Codec.BOOL.optionalFieldOf("any_synergy").forGetter(Instance::anySynergy),
                Codec.BOOL.optionalFieldOf("any_resonance").forGetter(Instance::anyResonance)
        ).apply(inst, Instance::new));

        public boolean matches(net.minecraft.server.level.ServerPlayer player,
                                PlayerAdaption data) {
            if (minAdaptCount.isPresent() && data.getAdaptCount() < minAdaptCount.get()) {
                return false;
            }
            if (minTier.isPresent() && WheelTier.forCount(data.getAdaptCount()) < minTier.get()) {
                return false;
            }
            if (concept.isPresent() && !holds(data, concept.get())) {
                return false;
            }
            if (minLevel.isPresent() && !levelReached(data)) {
                return false;
            }
            if (minSheds.isPresent() && data.shedCount < minSheds.get()) {
                return false;
            }
            if (anySynergy.orElse(false)
                    && ru.adaptionwheel.category.Synergies.activeIds(data).isEmpty()) {
                return false;
            }

            if (anyResonance.orElse(false)
                    && player.getEffect(ru.adaptionwheel.effect.ModEffects.RESONANCE) == null) {
                return false;
            }
            return true;
        }

        private boolean levelReached(PlayerAdaption data) {
            if (concept.isPresent()) {
                return data.level(concept.get()) >= minLevel.get();
            }
            for (int level : data.levels.values()) {
                if (level >= minLevel.get()) {
                    return true;
                }
            }
            return false;
        }

        private static boolean holds(PlayerAdaption data, String concept) {
            if (data.isAdapted(concept) || data.level(concept) > 0) {
                return true;
            }
            if (!concept.endsWith("_")) {
                return false;
            }
            for (String held : data.adapted) {
                if (held.startsWith(concept)) {
                    return true;
                }
            }
            for (String key : data.levels.keySet()) {
                if (data.levels.get(key) > 0 && key.startsWith(concept)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public Optional<net.minecraft.advancements.critereon.ContextAwarePredicate> player() {

            return Optional.empty();
        }
    }
}

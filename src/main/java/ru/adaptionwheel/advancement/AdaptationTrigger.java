package ru.adaptionwheel.advancement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import ru.adaptionwheel.category.WheelTier;
import ru.adaptionwheel.data.PlayerAdaption;

import java.util.Optional;

/**
 * One criterion for the whole mod's progression.
 *
 * <p>An advancement tree needs criteria that can ask about adaptation state, and vanilla has
 * nothing for "hold at least a hundred adaptations" or "light Resonant". Without this, every one of
 * those questions would need its own trigger class, and the tree would be a list of triggers rather
 * than a set of questions. Each part here is optional, so a single trigger expresses the whole
 * tree — from "adapt to fire" to "reach the top tier" — and the JSON says which parts it cares
 * about.</p>
 *
 * <p>Built on vanilla's {@link SimpleCriterionTrigger} rather than hand-rolling a listener map.
 * That base class keys listeners by {@code PlayerAdvancements} and fires through a loot context
 * that already resolves the optional player predicate; doing it by hand meant reimplementing both,
 * and the {@code PlayerAdvancements} field has no public accessor to key a map by player id in the
 * first place.</p>
 *
 * <p>Evaluated once a second from the wearer tick rather than at the moment something happens. That
 * sounds lazy and is the point: completion, a tier crossing, a shed, a transfer, logging in
 * already deep, and picking the wheel up after ten minutes offline are six different events, and
 * polling the state once a second answers all six for the cost of one short loop. Anything fired at
 * a moment has to be fired at six moments, and the seventh is the bug.</p>
 */
public class AdaptationTrigger extends SimpleCriterionTrigger<AdaptationTrigger.Instance> {

    /**
     * The trigger itself, as a deferred registry entry.
     *
     * <p>A {@code DeferredRegister} and not {@code CriteriaTriggers.register} in the mod
     * constructor: the constructor runs after the registries have been frozen, and
     * {@code CriteriaTriggers.register} writes straight into the frozen static registry, which
     * fails with "Registry is already frozen" and takes the whole mod down with it. It also
     * namespace-resolves its argument, so passing a bare "adaptation" registers it as
     * {@code minecraft:adaptation} and every advancement referring to {@code adaptionwheel:}
     * then fails to load.</p>
     */
    public static final net.neoforged.neoforge.registries.DeferredRegister<CriterionTrigger<?>>
            TRIGGERS = net.neoforged.neoforge.registries.DeferredRegister.create(
                    net.minecraft.core.registries.Registries.TRIGGER_TYPE,
                    ru.adaptionwheel.AdaptionWheel.MODID);

    public static final net.neoforged.neoforge.registries.DeferredHolder<
            CriterionTrigger<?>, AdaptationTrigger> INSTANCE =
            TRIGGERS.register("adaptation", AdaptationTrigger::new);

    /** The id every advancement in this tree uses: {@code adaptionwheel:adaptation}. */
    public static final String ID = "adaptation";

    /**
     * Awards every criterion this player now meets.
     *
     * <p>Called on the 1 Hz tick, so the common case -- nobody listening -- returns before any of
     * the state is read.</p>
     */
    public static void evaluate(net.minecraft.server.level.ServerPlayer player,
                                PlayerAdaption data) {
        // Through the holder, not the class: the trigger object only exists once the deferred
        // register has fired, and this is called from the wearer tick long after that.
        INSTANCE.get().trigger(player, instance -> instance.matches(player, data));
    }

    @Override
    public Codec<Instance> codec() {
        return Instance.CODEC;
    }

    /**
     * The condition.
     *
     * <p>Concept matching is a prefix test, so a criterion written against {@code "Existence_"} is
     * satisfied by any boss rather than needing one advancement per boss id — the concepts are
     * generated per boss, and an exact match here would be unusable. A concept ending in {@code _}
     * is therefore a family; anything else must match exactly, which keeps a mistyped key from
     * silently matching everything.</p>
     *
     * @param minLevel with no concept named, means "the wheel holds anything at this level"
     */
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
            // Asked of the player rather than the attachment, because resonance is a live buff
            // and not a stored adaptation: it is on while other adapted players are nearby and off
            // the moment they are not, so nothing about the attachment can answer it. Evaluated
            // immediately after Resonance.tick, so it reflects this second and not the last.
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
            // No player predicate. Every criterion in this tree is about the wearer's own state, so
            // there is nothing for a ContextAwarePredicate to match and the base class is free to
            // skip the loot-context check entirely.
            return Optional.empty();
        }
    }
}

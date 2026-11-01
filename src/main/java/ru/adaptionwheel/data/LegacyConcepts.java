package ru.adaptionwheel.data;

import ru.adaptionwheel.category.CombatFistTiers;
import ru.adaptionwheel.category.Concepts;
import ru.adaptionwheel.category.FistTiers;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Renames of concepts that used to be spelled differently, applied to data loaded from disk.
 *
 * <p>Every rule lives here and nowhere else, and both load paths call this same method: the player
 * attachment (a `PlayerAdaption`) and the wheel item's own component (a `WheelData`). Calling it from
 * the attachment alone would not be enough, because {@code loadInto} merges the item's stored keys
 * back in on every equip and would undo it, and calling it from the item alone would leave an
 * attachment-only player unmigrated.
 *
 * <p>Registries are read while resolving a debuff namespace, so this must only run once the effect
 * registry is populated — never from a static initialiser.
 */
public final class LegacyConcepts {

    /** The old spelling of {@link Concepts#ENV_FIRE}; it promised a level ladder fire no longer has. */
    private static final String LEGACY_FIRE = "Type_FIRE";

    private LegacyConcepts() {
    }

    public static void migrate(PlayerAdaption data) {
        migrate(data.levels, data.adapted, data.history);
        // The panel switch is part of the adaptation's state: a player who switched fire off must
        // not have it switched back on by the rename.
        if (data.disabled.remove(LEGACY_FIRE)) {
            data.disabled.add(Concepts.ENV_FIRE);
        }
        data.disabled.removeAll(Concepts.RETIRED_TYPES);
        data.progress.keySet().removeAll(Concepts.RETIRED_TYPES);
    }

    /**
     * Renames legacy keys <b>in place</b>, so the collections must be mutable.
     *
     * <p>That constraint has already broken once: this was called on a {@code WheelData}'s own
     * collections, which arrived from a Data Component as an immutable map, and the unconditional
     * {@code levels.remove(...)} threw on every equip. Only call it with collections you own and
     * intend to change.
     */
    public static void migrate(Map<String, Integer> levels, Collection<String> adapted, Collection<String> history) {

        // There was no copper tier: copper owns no tool band, so a literal ladder had a rung that
        // granted nothing. It was folded into Iron, which is where a copper pickaxe actually sits.
        Integer copper = levels.remove("Fist_Copper");
        if (copper != null) {
            mergeInto(levels, FistTiers.concept(2), copper);
        }

        // The punching fist used to be one flat leveled concept and is now a five-stage ladder.
        // Everything it earned goes into the first stage, which is the same position on that ladder.
        Integer flat = levels.remove("Combat_FistDamage");
        if (flat != null) {
            mergeInto(levels, CombatFistTiers.concept(0), flat);
        }

        // Fire used to be Type_FIRE. It has been a one-time adaptation since the fire/lava split,
        // but older saves still carry the leveled spelling (and pre-split levels). Both halves are
        // renamed, not dropped: a level above zero earned the adaptation, and an adapted/history
        // key is the same fact under the new name. The Type_ prefix fallback would otherwise treat
        // the old key as an eight-level ladder again on the very next load.
        Integer fire = levels.remove(LEGACY_FIRE);
        if (fire != null && fire > 0) {
            adapted.add(Concepts.ENV_FIRE);
        }
        if (adapted.remove(LEGACY_FIRE)) {
            adapted.add(Concepts.ENV_FIRE);
        }
        renameInPlace(history, LEGACY_FIRE, Concepts.ENV_FIRE);

        // The five hazards below (and CONTACT) are full-protection Env_* adaptations now, so their
        // old leveled keys are not just inert -- they would reappear in the panel as concepts that
        // promise a reduction and never apply it. Strip them from both load paths here.
        for (String retired : Concepts.RETIRED_TYPES) {
            levels.remove(retired);
            adapted.remove(retired);
            history.remove(retired);
        }

        renameDebuffs(levels, adapted, history);
    }

    private static void mergeInto(Map<String, Integer> levels, String concept, int extra) {
        int merged = Math.min(PlayerAdaption.MAX_LEVEL, levels.getOrDefault(concept, 0) + extra);
        levels.put(concept, merged);
    }

    /**
     * Renames one key inside a collection, preserving its position and never duplicating the
     * target. {@code adapted} is a Set and {@code history} a List, and {@code clear} plus
     * {@code addAll} is the rewrite both support.
     */
    private static void renameInPlace(Collection<String> concepts, String from, String to) {
        if (!concepts.contains(from)) {
            return;
        }
        Set<String> rebuilt = new LinkedHashSet<>();
        for (String concept : concepts) {
            rebuilt.add(concept.equals(from) ? to : concept);
        }
        concepts.clear();
        concepts.addAll(rebuilt);
    }

    /**
     * Rewrites bare {@code Debuff_<path>} keys as {@code Debuff_<namespace>:<path>}.
     *
     * <p>The bare spelling dropped the namespace, so two mods shipping an effect with the same path
     * shared one adaptation. Rebuilding the key can only rename, never change what it means: the
     * renamed key is the one the runtime now looks up, so an adaptation earned under the old spelling
     * keeps denying exactly the effect it always did.
     */
    private static void renameDebuffs(Map<String, Integer> levels, Collection<String> adapted, Collection<String> history) {
        Map<String, String> levelRenames = renameAll(levels.keySet());
        if (levelRenames.isEmpty() && firstBare(adapted) == null && firstBare(history) == null) {
            return;
        }

        if (!levelRenames.isEmpty()) {
            Map<String, Integer> rebuilt = new HashMap<>();
            for (Map.Entry<String, Integer> entry : levels.entrySet()) {
                rebuilt.put(levelRenames.getOrDefault(entry.getKey(), entry.getKey()), entry.getValue());
            }
            levels.clear();
            levels.putAll(rebuilt);
        }
        renameAllInPlace(adapted);
        renameAllInPlace(history);
    }

    private static String firstBare(Iterable<String> concepts) {
        for (String concept : concepts) {
            if (isBareDebuff(concept)) {
                return concept;
            }
        }
        return null;
    }

    private static boolean isBareDebuff(String concept) {
        if (concept == null || !concept.startsWith(Concepts.DEBUFF_PREFIX)) {
            return false;
        }
        String rest = concept.substring(Concepts.DEBUFF_PREFIX.length());
        return !rest.isEmpty() && rest.indexOf(':') < 0;
    }

    private static Map<String, String> renameAll(Iterable<String> concepts) {
        Map<String, String> renames = new HashMap<>();
        for (String concept : concepts) {
            if (isBareDebuff(concept)) {
                renames.put(concept, Concepts.debuff(concept.substring(Concepts.DEBUFF_PREFIX.length())));
            }
        }
        return renames;
    }

    /**
     * One implementation for both collection types, because {@code PlayerAdaption.adapted} is a Set
     * and {@code WheelData.adapted} is a List — the same rename has to run on both, and {@code clear}
     * plus {@code addAll} is the only rewrite both support. Order is preserved where it matters.
     */
    private static void renameAllInPlace(Collection<String> concepts) {
        Map<String, String> renames = renameAll(concepts);
        if (renames.isEmpty()) {
            return;
        }
        Set<String> rebuilt = new LinkedHashSet<>();
        for (String concept : concepts) {
            rebuilt.add(renames.getOrDefault(concept, concept));
        }
        concepts.clear();
        concepts.addAll(rebuilt);
    }
}

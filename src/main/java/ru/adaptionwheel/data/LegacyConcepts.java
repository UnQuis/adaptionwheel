package ru.adaptionwheel.data;

import ru.adaptionwheel.category.CombatFistTiers;
import ru.adaptionwheel.category.Concepts;
import ru.adaptionwheel.category.FistTiers;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
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

    private LegacyConcepts() {
    }

    public static void migrate(PlayerAdaption data) {
        migrate(data.levels, data.adapted, data.history);
    }

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

        renameDebuffs(levels, adapted, history);
    }

    private static void mergeInto(Map<String, Integer> levels, String concept, int extra) {
        int merged = Math.min(PlayerAdaption.MAX_LEVEL, levels.getOrDefault(concept, 0) + extra);
        levels.put(concept, merged);
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

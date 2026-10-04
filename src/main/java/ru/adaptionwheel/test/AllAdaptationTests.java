package ru.adaptionwheel.test;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffects;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import ru.adaptionwheel.AdaptionWheel;
import ru.adaptionwheel.adapt.AdaptationDefinition;
import ru.adaptionwheel.adapt.AdaptationRegistry;
import ru.adaptionwheel.category.CombatFistTiers;
import ru.adaptionwheel.category.Concepts;
import ru.adaptionwheel.data.LegacyConcepts;
import ru.adaptionwheel.data.PlayerAdaption;

import java.util.ArrayList;
import java.util.List;

/**
 * Pins the guarantees behind "eat the All Adaptation item and you are adapted to everything".
 *
 * <p>Every test here exists because the opposite was true and nothing noticed. The item used to carry
 * a hand-written copy of the core concept list, so a concept added anywhere else simply did not
 * appear in it; and the debuff half had two live spellings, so levitation denial was granted under a
 * key the flight gate never asked about. Both failures are silent — the item reports success — so they
 * are pinned here rather than left to a manual re-read of the list.
 */
@GameTestHolder(AdaptionWheel.MODID)
@PrefixGameTestTemplate(false)
public class AllAdaptationTests {

    private static final List<String> REGISTERED = new ArrayList<>();

    private static List<String> registeredConcepts() {
        if (REGISTERED.isEmpty()) {
            for (AdaptationDefinition def : AdaptationRegistry.allDefinitions()) {
                REGISTERED.add(def.concept());
            }
        }
        return REGISTERED;
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void everyRegisteredConceptIsSomethingTheAllAdaptationItemGrants(GameTestHelper helper) {

        helper.assertTrue(!registeredConcepts().isEmpty(),
                "the registry is empty, so this test would pass while granting nothing");

        // The item walks the registry, so this is the shape of the loop in grantAllAdaptations.
        PlayerAdaption data = new PlayerAdaption();
        for (AdaptationDefinition def : AdaptationRegistry.allDefinitions()) {
            if (def.leveled()) {
                data.levels.put(def.concept(), def.maxLevel());
            } else {
                data.adapted.add(def.concept());
            }
            data.addHistory(def.concept());
        }

        List<String> missed = new ArrayList<>();
        for (String concept : registeredConcepts()) {
            boolean granted = Concepts.isLevelBased(concept)
                    ? data.levelOrZero(concept) >= PlayerAdaption.MAX_LEVEL
                    : data.active(concept);
            if (!granted) {
                missed.add(concept);
            }
        }
        helper.assertTrue(missed.isEmpty(),
                "these registered concepts are not adapted after a full adaptation: " + missed);
        helper.succeed();
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void theConceptsThatWereMissingAreNowCovered(GameTestHelper helper) {

        // Named one by one because the failure this guards against is silent: the item succeeded and
        // the player was still not adapted. Each of these was absent from the hand-written list.
        String[] mustBeGranted = {
                Concepts.ENV_INVENTORY,
                Concepts.MUTATION_SEA_EYE,
                Concepts.MUTATION_FLIGHT,
                CombatFistTiers.concept(0),
                CombatFistTiers.concept(1),
                CombatFistTiers.concept(2),
                CombatFistTiers.concept(3),
                CombatFistTiers.concept(4)
        };

        PlayerAdaption data = new PlayerAdaption();
        for (AdaptationDefinition def : AdaptationRegistry.allDefinitions()) {
            if (def.leveled()) {
                data.levels.put(def.concept(), def.maxLevel());
            } else {
                data.adapted.add(def.concept());
            }
        }

        for (String concept : mustBeGranted) {
            helper.assertTrue(AdaptationRegistry.isRegistered(concept),
                    concept + " is not even registered, so the item could never grant it");
            helper.assertTrue(Concepts.isLevelBased(concept)
                            ? data.levelOrZero(concept) >= PlayerAdaption.MAX_LEVEL
                            : data.active(concept),
                    concept + " is still missing after a full adaptation");
        }
        helper.succeed();
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void aDebuffKeyIsTheSameWhicheverFormTheCallerHolds(GameTestHelper helper) {

        String fromFullId = Concepts.debuff("minecraft:levitation");
        String fromBarePath = Concepts.debuff("levitation");

        helper.assertTrue(fromFullId.equals(fromBarePath),
                "the runtime and a caller holding a bare path must mint one key, got "
                        + fromFullId + " and " + fromBarePath);
        helper.assertTrue(fromFullId.contains(":"),
                "a debuff key must carry a namespace, or two mods shipping the same effect path share"
                        + " one adaptation and the display name cannot resolve: " + fromFullId);
        helper.succeed();
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void levitationDeniedByTheItemIsTheLevitationTheFlightGateAsksFor(GameTestHelper helper) {

        // The concrete failure: the item granted Debuff_levitation, flight asked for
        // Debuff_minecraft:levitation, and neither one nor the other was wrong on its own.
        String levitation = Concepts.debuff(MobEffects.LEVITATION);
        helper.assertTrue(levitation.equals("Debuff_minecraft:levitation"),
                "the levitation concept the game mints is " + levitation);

        PlayerAdaption data = new PlayerAdaption();
        data.adapted.add(levitation);

        helper.assertTrue(data.isAdapted(Concepts.debuff("minecraft:levitation")),
                "granting the item's levitation key must satisfy the flight gate's identical key");
        helper.succeed();
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void anOldBareDebuffKeyIsRenamedRatherThanLeftAsASecondSpelling(GameTestHelper helper) {

        PlayerAdaption data = new PlayerAdaption();
        data.adapted.add("Debuff_levitation");
        data.levels.put("Debuff_poison", PlayerAdaption.MAX_LEVEL);
        data.history.add("Debuff_levitation");

        LegacyConcepts.migrate(data);

        helper.assertTrue(!data.adapted.contains("Debuff_levitation"),
                "the bare key is still there next to the new one, so the panel lists both");
        helper.assertTrue(data.adapted.contains("Debuff_minecraft:levitation"),
                "the bare levitation key was dropped instead of renamed, losing the adaptation: "
                        + data.adapted);
        helper.assertTrue(data.levels.containsKey("Debuff_minecraft:poison"),
                "the bare poison level was not renamed: " + data.levels.keySet());
        helper.assertTrue(data.history.contains("Debuff_minecraft:levitation"),
                "history still holds the old spelling: " + data.history);
        helper.assertTrue(!data.history.contains("Debuff_levitation"),
                "history kept the old spelling alongside the new one");
        helper.succeed();
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void migratingNeverCostsAKnownedLevel(GameTestHelper helper) {

        // The merging rules have to compose: copper folded into Iron and the flat punching fist folded
        // into the wooden stage must not silently discard what the other already earned.
        PlayerAdaption data = new PlayerAdaption();
        data.levels.put("Fist_Copper", 3);
        data.levels.put("Fist_Iron", 2);
        data.levels.put("Combat_FistDamage", 4);

        LegacyConcepts.migrate(data);

        helper.assertTrue(!data.levels.containsKey("Fist_Copper"),
                "the removed copper tier is still stored: " + data.levels.keySet());
        helper.assertTrue(data.level("Fist_Iron") == 5,
                "copper 3 plus iron 2 should give iron 5, got " + data.level("Fist_Iron"));
        helper.assertTrue(!data.levels.containsKey("Combat_FistDamage"),
                "the removed flat fist concept is still stored: " + data.levels.keySet());
        helper.assertTrue(data.level(CombatFistTiers.concept(0)) == 4,
                "the flat fist's 4 levels should land on the wooden stage, got "
                        + data.level(CombatFistTiers.concept(0)));
        helper.succeed();
    }
}

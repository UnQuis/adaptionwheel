package ru.adaptionwheel.test;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import ru.adaptionwheel.category.AdaptionCategory;
import ru.adaptionwheel.category.Concepts;
import ru.adaptionwheel.category.FistTiers;
import ru.adaptionwheel.category.Synergies;
import ru.adaptionwheel.data.PlayerAdaption;

/**
 * Synergies: the named combinations that make the adaptation pool interact.
 *
 * <p>These are almost entirely pure logic, which is the right place for them — an effect that only
 * fires inside a damage event cannot be reached here (mock players are broken while Curios is
 * installed), so what can be pinned is the matching, and matching is where a typo in a concept key
 * silently disables a synergy forever.</p>
 */
@GameTestHolder(ru.adaptionwheel.AdaptionWheel.MODID)
@PrefixGameTestTemplate(false)
public class SynergyTests {

    /**
     * A fresh attachment with nothing adapted.
     *
     * <p>Built through the canonical record constructor rather than the package-private no-arg
     * one, which is the same shape {@code FistGameTests} uses — and the only way to make one from
     * outside {@code ru.adaptionwheel.data}.</p>
     */
    private static PlayerAdaption empty() {
        return new PlayerAdaption(
                new java.util.HashMap<>(), java.util.List.of(), java.util.List.of(),
                java.util.List.of(), java.util.List.of(), new java.util.HashMap<>(),
                new java.util.HashMap<>(), 0, 0, 0, false, 0f, 0f, false, 0);
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void nothingIsActiveOnAnEmptyWheel(GameTestHelper helper) {
        PlayerAdaption data = empty();
        for (Synergies.Synergy synergy : Synergies.ALL) {
            helper.assertTrue(!Synergies.satisfied(data, synergy),
                    synergy.id() + " must not be satisfied by an empty adaptation set");
        }
        helper.assertTrue(Synergies.activeIds(data).isEmpty(), "an empty set yields no synergies");
        helper.succeed();
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void everyRosterEntrySatisfiesItself(GameTestHelper helper) {
        // The one that catches a mistyped concept key: grant exactly what each synergy asks for and
        // it must light up. Without this, a synergy whose requirement names a key nothing ever
        // grants is dead content that still shows in the browser.
        for (Synergies.Synergy synergy : Synergies.ALL) {
            PlayerAdaption data = empty();
            for (String[] requirement : synergy.requires()) {
                String concept = requirement[0];
                if (ru.adaptionwheel.category.Synergies.Requirement.MAXED.name()
                        .equals(requirement[1])) {
                    data.levels.put(concept, PlayerAdaption.MAX_LEVEL);
                } else {
                    // Satisfy a family requirement by granting one member of the family.
                    String concrete = concreteMember(concept);
                    if (data.isAdapted(concrete) || data.level(concrete) > 0) {
                        // two requirements in the same family
                        continue;
                    }
                    data.levels.put(concrete, 1);
                }
            }
            helper.assertTrue(Synergies.satisfied(data, synergy),
                    synergy.id() + " is not satisfied by exactly its own requirements ("
                            + synergy.requires() + ")");
        }
        helper.succeed();
    }

    /**
     * A prefix requirement has to be satisfiable by something that can actually be granted.
     *
     * <p>Prefix requirements are how a boss-agnostic synergy exists at all — the concept keys are
     * generated per boss — so a family whose members nothing grants would make those synergies
     * permanently inert.</p>
     */
    private static String concreteMember(String requirement) {
        if (requirement.equals(Concepts.EXISTENCE_PREFIX)) {
            return Concepts.existence("minecraft:ender_dragon");
        }
        if (requirement.equals(Concepts.CONTACT_PREFIX)) {
            return Concepts.contact("minecraft:zombie");
        }
        if (requirement.startsWith("Offense_")) {
            return Concepts.offense("minecraft:zombie");
        }
        if (requirement.startsWith("Drop_NPC_")) {
            return "Drop_NPC_minecraft:zombie";
        }
        return requirement;
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void aFamilyRequirementMatchesAnyMember(GameTestHelper helper) {
        // Both of Unmaker's requirements, so the assertion is about the family match and not
        // about a missing half.
        PlayerAdaption data = empty();
        data.levels.put(Concepts.ENV_VOID, 1);
        data.levels.put(Concepts.existence("minecraft:wither"), 1);
        helper.assertTrue(Synergies.satisfied(data, Synergies.UNMAKER),
                "Env_Void plus one boss must light Unmaker");

        // A boss-agnostic requirement is satisfied by ANY boss, not only one specific id.
        PlayerAdaption other = empty();
        other.levels.put(Concepts.ENV_VOID, 1);
        other.levels.put(Concepts.existence("someothermod:the_final_boss"), 1);
        helper.assertTrue(Synergies.satisfied(other, Synergies.UNMAKER),
                "a modded boss must satisfy an Existence requirement just as a vanilla one does");

        // And Env_Void with no boss at all must not.
        PlayerAdaption alone = empty();
        alone.levels.put(Concepts.ENV_VOID, 1);
        helper.assertTrue(!Synergies.satisfied(alone, Synergies.UNMAKER),
                "Env_Void alone must not satisfy Unmaker");

        // An unrelated concept must not be mistaken for a family member.
        PlayerAdaption unrelated = empty();
        unrelated.levels.put(Concepts.ENV_VOID, 1);
        unrelated.levels.put(Concepts.type(AdaptionCategory.FIRE), PlayerAdaption.MAX_LEVEL);
        helper.assertTrue(!Synergies.satisfied(unrelated, Synergies.UNMAKER),
                "an unrelated concept must not satisfy an Existence requirement");
        helper.succeed();
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void aMaxedRequirementIsNotSatisfiedByAPartialLevel(GameTestHelper helper) {
        PlayerAdaption data = empty();
        data.levels.put(Concepts.type(AdaptionCategory.FIRE), PlayerAdaption.MAX_LEVEL);
        data.levels.put(Concepts.MUTATION_THERMAL, PlayerAdaption.MAX_LEVEL);
        data.levels.put(FistTiers.concept(2), PlayerAdaption.MAX_LEVEL - 1);
        helper.assertTrue(Synergies.satisfied(data, Synergies.ASHWALKER),
                "Ashwalker does not require a fist, so it must already be active");
        helper.assertTrue(!Synergies.satisfied(data, Synergies.GOLIATH),
                "Goliath needs a maxed fist, and the fist is one short");

        data.levels.put(FistTiers.concept(2), PlayerAdaption.MAX_LEVEL);
        helper.assertTrue(Synergies.satisfied(data, Synergies.GOLIATH),
                "Goliath must light once the fist is actually maxed");
        helper.succeed();
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void idsAreUniqueAndResolvable(GameTestHelper helper) {
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (Synergies.Synergy synergy : Synergies.ALL) {
            helper.assertTrue(seen.add(synergy.id()), "duplicate synergy id " + synergy.id());
            helper.assertTrue(Synergies.byId(synergy.id()) == synergy,
                    synergy.id() + " does not resolve back to itself");
            helper.assertTrue(!synergy.requires().isEmpty(),
                    synergy.id() + " has no requirements, so it is always on");
            helper.assertTrue(!synergy.blurb().isBlank(), synergy.id() + " has no blurb");
        }
        helper.assertTrue(Synergies.byId("nope") == null, "an unknown id must not resolve");
        helper.succeed();
    }
}

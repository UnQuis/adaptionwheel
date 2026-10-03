package ru.adaptionwheel.test;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import ru.adaptionwheel.category.Concepts;
import ru.adaptionwheel.category.WheelTier;

@GameTestHolder(ru.adaptionwheel.AdaptionWheel.MODID)
@PrefixGameTestTemplate(false)
public class WheelTierTests {

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void tiersAreMonotonic(GameTestHelper helper) {
        int previous = -1;
        for (int count = 0; count <= 400; count++) {
            int tier = WheelTier.forCount(count);
            helper.assertTrue(tier >= previous,
                    "tier went backwards at " + count + " adaptations");
            helper.assertTrue(tier >= 0 && tier <= WheelTier.maxTier(),
                    "tier " + tier + " out of range at " + count + " adaptations");
            previous = tier;
        }
        helper.succeed();
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void everyThresholdIsReachable(GameTestHelper helper) {

        for (int tier = 1; tier <= WheelTier.maxTier(); tier++) {
            int threshold = WheelTier.nextThreshold(tier - 1);
            helper.assertTrue(threshold > 0,
                    "tier " + tier + " has no threshold");
            helper.assertTrue(threshold <= 400,
                    "tier " + tier + " needs " + threshold + " adaptations, out of reach");
            helper.assertTrue(WheelTier.forCount(threshold) == tier,
                    "threshold " + threshold + " does not actually land on tier " + tier);
        }
        helper.assertTrue(WheelTier.nextThreshold(WheelTier.maxTier()) == -1,
                "the top tier must report no next threshold");
        helper.succeed();
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void environmentsAreCore(GameTestHelper helper) {

        String[] environments = {
                Concepts.ENV_LIQUID, Concepts.ENV_DROWN, Concepts.ENV_LAVA, Concepts.ENV_FALL,
                Concepts.ENV_VOID, Concepts.ENV_STARVE, Concepts.ENV_ICE, Concepts.ENV_SLIME,
                Concepts.ENV_THORNS, Concepts.ENV_KNOCKBACK, Concepts.ENV_SUFFOCATE,
                Concepts.ENV_DARKNESS, Concepts.ENV_COBWEB,
        };
        helper.assertTrue(environments.length == 13,
                "expected the 13 Env_ concepts Concepts declares, this list has "
                        + environments.length + " -- check Concepts for one that was added");
        for (String concept : environments) {
            helper.assertTrue(concept != null && !concept.isBlank(),
                    "an environment constant is missing from the list, so it is not really covered");
            helper.assertTrue(WheelTier.familyUnlocked(concept, 0),
                    concept + " must be analysable from a brand-new wheel; it was gated once and"
                            + " the symptom was that water adaptation never started");
        }

        helper.assertTrue(!WheelTier.familyUnlocked(Concepts.contact("minecraft:zombie"), 0),
                "per-mob contact must still be something a tier reveals");
        helper.succeed();
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void theCoreIsAlwaysAvailable(GameTestHelper helper) {

        String[] core = {
                Concepts.type(ru.adaptionwheel.category.AdaptionCategory.FIRE),
                Concepts.type(ru.adaptionwheel.category.AdaptionCategory.FALL),
                Concepts.debuff("poison"),
                Concepts.MINE_LABOR,
                Concepts.COMBAT_COOLDOWN,
                Concepts.MOVE_HONEY,
                Concepts.PERCEP_STEADY_GAZE,
                Concepts.MUTATION_FIST,
                ru.adaptionwheel.category.FistTiers.concept(0),
                Concepts.ADVERSITY,
                Concepts.SELF_DAMAGE,
                Concepts.DIMENSION_DESTROY,
        };
        for (String concept : core) {
            helper.assertTrue(WheelTier.familyUnlocked(concept, 0),
                    concept + " must be available at tier 0");
        }
        helper.succeed();
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void everyGatedFamilyOpensAtItsTier(GameTestHelper helper) {

        int[] opensAt = {1, 2, 3, 4};
        String[] probes = {"minecraft:zombie", "draconicevolution:draconic_guardian"};
        for (String path : probes) {
            String[] keys = {
                    Concepts.contact(path), Concepts.offense(path),
                    "Drop_NPC_" + path, Concepts.existence(path),
            };
            for (int family = 0; family < keys.length; family++) {
                String concept = keys[family];
                helper.assertTrue(opensAt[family] >= 1 && opensAt[family] <= WheelTier.maxTier(),
                        "the gate table names a tier outside the ladder: " + opensAt[family]);
                helper.assertTrue(!WheelTier.familyUnlocked(concept, opensAt[family] - 1),
                        concept + " must still be locked at tier " + (opensAt[family] - 1));
                helper.assertTrue(WheelTier.familyUnlocked(concept, opensAt[family]),
                        concept + " must be open at tier " + opensAt[family]);

                for (int earlier = 0; earlier < opensAt[family] - 1; earlier++) {
                    helper.assertTrue(!WheelTier.familyUnlocked(concept, earlier),
                            concept + " must not be open yet at tier " + earlier);
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void laterTiersAreNeverWorse(GameTestHelper helper) {

        for (int tier = 1; tier <= WheelTier.maxTier(); tier++) {
            helper.assertTrue(WheelTier.statBonus(tier) > WheelTier.statBonus(tier - 1),
                    "tier " + tier + " must be strictly stronger than tier " + (tier - 1));
        }
        helper.succeed();
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void everyTierIsNamedAndColoured(GameTestHelper helper) {
        for (int tier = 0; tier <= WheelTier.maxTier(); tier++) {
            helper.assertTrue(!WheelTier.name(tier).isBlank(),
                    "tier " + tier + " has no name");
            helper.assertTrue(WheelTier.nameKey(tier).startsWith("adaptionwheel.tier."),
                    "tier " + tier + " has a malformed translation key");
            int color = WheelTier.color(tier);
            helper.assertTrue((color >>> 24) == 0xFF,
                    "tier " + tier + " colour is not opaque");
        }

        helper.assertTrue(WheelTier.forCount(Integer.MAX_VALUE) == WheelTier.maxTier(),
                "an absurd adaptation count must clamp to the top tier");
        helper.assertTrue(WheelTier.forCount(-5) == 0, "a negative count must clamp to tier 0");
        helper.succeed();
    }
}

package ru.adaptionwheel.test;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import ru.adaptionwheel.category.Concepts;
import ru.adaptionwheel.category.WheelTier;

/**
 * Wheel awakening: the progression spine.
 *
 * <p>Mostly pure arithmetic, and that is the point. The gate itself is one line inside
 * {@code AdaptionEvents.startOrAccelerate}, which no test can reach without a player (mock players
 * are broken while Curios is installed), so what is pinned here is the table that decides it —
 * because a wrong threshold or a wrong prefix is invisible until a player notices a family they
 * have owned for an hour never started analysing.</p>
 */
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
        // A threshold above the top would make that tier dead content, and one that no plausible
        // count reaches would make the family it gates unobtainable.
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

    /**
     * The environments are core, and this test exists because they were not.
     *
     * <p>Wheel awakening originally gated {@code Env_} behind its first tier, on the reasoning
     * that it is one of the deep families. It is not: the gated families are the ones that scale
     * with the world — one concept per mob and per boss, hundreds of them — while there are
     * thirteen environments and they are as basic as damage types. The consequence was that a
     * player could not begin adapting to water at all until they held twelve adaptations, which
     * reads as a feature being switched off rather than as progression. Hence this test, and
     * hence "core" in the name.</p>
     */
    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void environmentsAreCore(GameTestHelper helper) {
        // Every Env_ concept Concepts declares. Written out rather than derived, because Concepts
        // has no registry of them; the count is asserted so a new environment added there without
        // being added here fails instead of going quietly untested.
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
        // And the per-mob families, which is what the gate is actually for.
        helper.assertTrue(!WheelTier.familyUnlocked(Concepts.contact("minecraft:zombie"), 0),
                "per-mob contact must still be something a tier reveals");
        helper.succeed();
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void theCoreIsAlwaysAvailable(GameTestHelper helper) {
        // Everything reachable without the wheel ever waking. If any of these got gated, a new
        // player's first ten adaptations would silently do nothing.
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
        // Built through the same helpers the rest of the mod uses, so a family whose prefix
        // changed in Concepts cannot keep working here and fail in game.
        // The gate is a strict ladder: Contact 1, Offense 2, Plunder 3, Existence 4, and nothing
        // in between. Each family is locked at the tier below its own and open at its own, which
        // is what makes the chain readable from the message alone.
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
                // And it must not be open early, which is what the ladder means.
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
        // The design constraint, stated as a test so it cannot be quietly broken: a tier only
        // ever adds, and never costs. Omnipotence is the point of the wheel.
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
        // Out-of-range must clamp, not throw: the renderer and the HUD both read this from
        // synced values that a desynced payload could push anywhere.
        helper.assertTrue(WheelTier.forCount(Integer.MAX_VALUE) == WheelTier.maxTier(),
                "an absurd adaptation count must clamp to the top tier");
        helper.assertTrue(WheelTier.forCount(-5) == 0, "a negative count must clamp to tier 0");
        helper.succeed();
    }
}

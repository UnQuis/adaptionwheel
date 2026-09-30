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
        String[] probes = {"minecraft:zombie", "draconicevolution:draconic_guardian"};
        for (String path : probes) {
            String contact = Concepts.contact(path);
            helper.assertTrue(!WheelTier.familyUnlocked(contact, 1),
                    contact + " must still be locked at tier 1");
            helper.assertTrue(WheelTier.familyUnlocked(contact, 2),
                    contact + " must be open at tier 2");

            String offense = Concepts.offense(path);
            helper.assertTrue(!WheelTier.familyUnlocked(offense, 2),
                    offense + " must still be locked at tier 2");
            helper.assertTrue(WheelTier.familyUnlocked(offense, 3),
                    offense + " must be open at tier 3");

            String existence = Concepts.existence(path);
            helper.assertTrue(!WheelTier.familyUnlocked(existence, WheelTier.maxTier() - 1),
                    existence + " must still be locked one tier below the top");
            helper.assertTrue(WheelTier.familyUnlocked(existence, WheelTier.maxTier()),
                    existence + " must be open at the top tier");
        }
        String env = Concepts.ENV_LAVA;
        helper.assertTrue(!WheelTier.familyUnlocked(env, 0), "Env_ must be locked at tier 0");
        helper.assertTrue(WheelTier.familyUnlocked(env, 1), "Env_ must be open at tier 1");
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

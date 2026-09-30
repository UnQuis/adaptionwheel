package ru.adaptionwheel.test;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import ru.adaptionwheel.category.Concepts;
import ru.adaptionwheel.category.WheelTier;
import ru.adaptionwheel.config.AdaptionConfig;
import ru.adaptionwheel.data.PlayerAdaption;
import ru.adaptionwheel.effect.WildReleaseEffect;
import ru.adaptionwheel.server.Shedding;

/**
 * Shedding: giving an adaptation up on purpose.
 *
 * <p>The effect itself needs a player and cannot be reached here, so what is pinned is the
 * arithmetic and — the part that actually matters — the tier protection. A shed that quietly costs
 * a player a whole family of adaptations is the one way this feature could contradict the mod's
 * whole premise, and it would do so silently: nothing errors, the numbers just get worse.</p>
 */
@GameTestHolder(ru.adaptionwheel.AdaptionWheel.MODID)
@PrefixGameTestTemplate(false)
public class SheddingTests {

    private static PlayerAdaption empty() {
        return new PlayerAdaption(
                new java.util.HashMap<>(), java.util.List.of(), java.util.List.of(),
                java.util.List.of(), java.util.List.of(), new java.util.HashMap<>(),
                new java.util.HashMap<>(), 0, 0, 0, false, 0f, 0f, false);
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void aOneTimeShedStillPaysOut(GameTestHelper helper) {
        // A one-time adaptation has no level to scale by, so it must not be treated as level 0 and
        // land on the weakest rung -- shedding a whole environment should feel like something.
        helper.assertTrue(Shedding.releaseAmplifier(Concepts.ENV_LAVA, 0) >= 1,
                "a one-time shed must pay at least the second rung");
        helper.assertTrue(Shedding.releaseTicks(0) > 0, "the burst must last a positive time");
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void theRampIsMonotonicAndCapped(GameTestHelper helper) {
        int previous = 0;
        for (int level = 0; level <= PlayerAdaption.MAX_LEVEL; level++) {
            int amp = Shedding.releaseAmplifier(Concepts.type(
                    ru.adaptionwheel.category.AdaptionCategory.FIRE), level);
            helper.assertTrue(amp >= previous, "the burst got weaker at level " + level);
            helper.assertTrue(amp <= WildReleaseEffect.MAX_AMPLIFIER,
                    "the burst exceeded the effect's declared maximum at level " + level);
            previous = amp;
        }
        helper.assertTrue(Shedding.releaseAmplifier(
                        Concepts.type(ru.adaptionwheel.category.AdaptionCategory.FIRE),
                        PlayerAdaption.MAX_LEVEL) == WildReleaseEffect.MAX_AMPLIFIER,
                "a maxed adaptation must reach the top rung");
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void aHigherLevelLastsLonger(GameTestHelper helper) {
        int base = Shedding.releaseTicks(1);
        for (int level = 2; level <= PlayerAdaption.MAX_LEVEL; level++) {
            helper.assertTrue(Shedding.releaseTicks(level) >= base,
                    "the burst shrank going from level 1 to " + level);
        }
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void sheddingIsNotAPrice(GameTestHelper helper) {
        // The design constraint, stated as a test: a shed concept must re-adapt FASTER, or the
        // feature is a way to lose progress and this mod does not do that to a player.
        PlayerAdaption data = empty();
        String concept = Concepts.type(ru.adaptionwheel.category.AdaptionCategory.FIRE);
        helper.assertTrue(Shedding.reattachFactor(data, concept) == 1.0D,
                "an un-shed concept must re-adapt at the normal rate");
        data.recentlyShed.add(concept);
        double factor = Shedding.reattachFactor(data, concept);
        helper.assertTrue(factor > 0.0D && factor <= 1.0D,
                "the re-attach factor must be a fraction, got " + factor);
        helper.assertTrue(factor < 1.0D,
                "a shed concept must re-adapt faster, got " + factor
                        + " (config: " + AdaptionConfig.SHEDDING_REATTACH_TIMER_FACTOR.get() + ")");
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void aShedThatCostsAFamilyIsRefused(GameTestHelper helper) {
        // Exactly on a threshold, holding one adaptation. Shedding it drops a tier and re-locks
        // that whole family, so it must be refused.
        int threshold = WheelTier.nextThreshold(1);
        PlayerAdaption lone = empty();
        lone.levels.put(Concepts.ENV_LAVA, 1);
        while (lone.getAdaptCount() < threshold) {
            lone.levels.put("Filler_" + lone.getAdaptCount(), 1);
        }
        helper.assertTrue(WheelTier.forCount(lone.getAdaptCount()) == 2,
                "test setup: expected to sit on tier 2");
        helper.assertTrue(Shedding.wouldDropTier(lone, "Filler_0"),
                "shedding the last adaptation of a tier must be detected");

        // And it must NOT fire when the same concept is shed with plenty of siblings left.
        PlayerAdaption crowded = empty();
        crowded.levels.put(Concepts.ENV_LAVA, 1);
        for (int i = 0; i < threshold + 20; i++) {
            crowded.levels.put("Filler_" + i, 1);
        }
        helper.assertTrue(!Shedding.wouldDropTier(crowded, "Filler_0"),
                "a shed that leaves the tier intact must be allowed");
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void sheddingNeverDropsBelowTheFloor(GameTestHelper helper) {
        // A wheel with a single adaptation sits at tier 0, and shedding it would take the wearer to
        // zero. There is no tier below zero, so the arithmetic must clamp rather than go negative.
        PlayerAdaption lone = empty();
        lone.levels.put(Concepts.ENV_LAVA, 1);
        helper.assertTrue(!Shedding.wouldDropTier(lone, Concepts.ENV_LAVA),
                "tier 0 is the floor; shedding the last adaptation cannot drop below it");
    }
}

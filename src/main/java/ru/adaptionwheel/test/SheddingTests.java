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

@GameTestHolder(ru.adaptionwheel.AdaptionWheel.MODID)
@PrefixGameTestTemplate(false)
public class SheddingTests {

    private static PlayerAdaption empty() {
        return new PlayerAdaption(
                new java.util.HashMap<>(), java.util.List.of(), java.util.List.of(),
                java.util.List.of(), java.util.List.of(), new java.util.HashMap<>(),
                new java.util.HashMap<>(), 0, 0, 0, false, 0f, 0f, false, 0, java.util.List.of());
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void aOneTimeShedStillPaysOut(GameTestHelper helper) {

        helper.assertTrue(Shedding.releaseAmplifier(Concepts.ENV_LAVA, 0) >= 1,
                "a one-time shed must pay at least the second rung");
        helper.assertTrue(Shedding.releaseTicks(0) > 0, "the burst must last a positive time");
        helper.succeed();
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
        helper.succeed();
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void aHigherLevelLastsLonger(GameTestHelper helper) {
        int base = Shedding.releaseTicks(1);
        for (int level = 2; level <= PlayerAdaption.MAX_LEVEL; level++) {
            helper.assertTrue(Shedding.releaseTicks(level) >= base,
                    "the burst shrank going from level 1 to " + level);
        }
        helper.succeed();
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void sheddingIsNotAPrice(GameTestHelper helper) {

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
        helper.succeed();
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void aShedThatCostsAFamilyIsRefused(GameTestHelper helper) {

        int threshold = WheelTier.nextThreshold(1);
        PlayerAdaption lone = empty();
        lone.levels.put(Concepts.ENV_LAVA, 1);

        for (int i = 1; i < threshold; i++) {
            lone.levels.put("Filler_" + i, 1);
        }
        lone.invalidateAdaptCount();
        helper.assertTrue(lone.getAdaptCount() == threshold,
                "test setup: expected exactly " + threshold + " adaptations, got "
                        + lone.getAdaptCount());
        int before = WheelTier.forCount(lone.getAdaptCount());
        helper.assertTrue(before == WheelTier.forCount(threshold - 1) + 1,
                "test setup: " + threshold + " must be the first count of a new tier");

        helper.assertTrue(lone.levels.containsKey(Concepts.ENV_LAVA),
                "test setup: the concept being shed must be one the player holds");
        helper.assertTrue(Shedding.wouldDropTier(lone, Concepts.ENV_LAVA),
                "shedding the last adaptation of a tier must be detected, holding "
                        + lone.getAdaptCount() + " at tier " + before);

        PlayerAdaption crowded = empty();
        crowded.levels.put(Concepts.ENV_LAVA, 1);
        for (int i = 0; i < threshold + 20; i++) {
            crowded.levels.put("Filler_" + i, 1);
        }
        crowded.invalidateAdaptCount();
        helper.assertTrue(!Shedding.wouldDropTier(crowded, "Filler_0"),
                "a shed that leaves the tier intact must be allowed");
        helper.succeed();
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void sheddingNeverDropsBelowTheFloor(GameTestHelper helper) {

        PlayerAdaption lone = empty();
        lone.levels.put(Concepts.ENV_LAVA, 1);
        helper.assertTrue(!Shedding.wouldDropTier(lone, Concepts.ENV_LAVA),
                "tier 0 is the floor; shedding the last adaptation cannot drop below it");
        helper.succeed();
    }
}

package ru.adaptionwheel.test;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import ru.adaptionwheel.category.Concepts;
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
                new java.util.HashMap<>(), 0, 0, 0, false, 0f, 0f, false, 0, ru.adaptionwheel.data.Extras.EMPTY);
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

}

package ru.adaptionwheel.test;

import java.util.HashMap;
import java.util.List;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import ru.adaptionwheel.AdaptionWheel;
import ru.adaptionwheel.category.CombatFistTiers;
import ru.adaptionwheel.category.FistTiers;
import ru.adaptionwheel.config.AdaptionConfig;
import ru.adaptionwheel.data.Extras;
import ru.adaptionwheel.data.PlayerAdaption;

/**
 * Pins the punching fist's shape, which the player reported as a single wooden fist.
 *
 * <p>The ladder itself existed — five stages of eight levels — but three things made it read as one
 * flat progression: the tag said {@code Lv.8 > 9}, a level that was never reachable because
 * maxing a stage hands over to the next material at level 1; the bar never showed that handover; and
 * the adapt-count multiplier was {@code 0.04}, so even 150 adaptations only reached x7 while a
 * single Diamond stage was worth x8. All three made the tier system pointless.
 */
@GameTestHolder(AdaptionWheel.MODID)
@PrefixGameTestTemplate(false)
public class CombatFistTests {

    /** Wood > Stone > Iron > Diamond > Netherite, eight levels each. */
    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void fiveStagesOfEight(GameTestHelper helper) {

        helper.assertTrue(CombatFistTiers.TIER_COUNT == 5,
                "the punching fist must be a five stage ladder, got " + CombatFistTiers.TIER_COUNT);
        helper.assertTrue(CombatFistTiers.TIER_COUNT == FistTiers.TIER_COUNT,
                "the two fists must have the same number of stages, or \"stage N\" means two"
                        + " different materials depending on which row you read");

        java.util.Set<String> names = new java.util.HashSet<>();
        for (int t = 0; t < CombatFistTiers.TIER_COUNT; t++) {
            String concept = CombatFistTiers.concept(t);
            helper.assertTrue(names.add(concept), "duplicate stage concept at index " + t);
            helper.assertTrue(CombatFistTiers.color(t) != CombatFistTiers.color((t + 1)
                    % CombatFistTiers.TIER_COUNT),
                    "adjacent stages share a colour at index " + t + ", so the bar cannot show a"
                            + " material change");
        }
        helper.assertTrue(PlayerAdaption.MAX_LEVEL == 8,
                "a stage is eight levels, got " + PlayerAdaption.MAX_LEVEL);
        helper.succeed();
    }

    /**
     * Maxing a stage must hand over, never reach a ninth level.
     *
     * <p>Pinned because the tag used to render {@code Lv.8 > 9} — a state the game cannot reach,
     * and one that made the ladder look like a flat single-material grind.
     */
    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void maxingAStageHandsOver(GameTestHelper helper) {

        PlayerAdaption data = empty();
        data.levels.put(CombatFistTiers.concept(0), PlayerAdaption.MAX_LEVEL);
        data.levels.put(CombatFistTiers.concept(1), 1);
        data.invalidateAdaptCount();

        helper.assertTrue(ru.adaptionwheel.server.HardFist.currentTier(data) == 1,
                "with wood maxed and stone at level 1, the player is a Stone fighter, got tier "
                        + ru.adaptionwheel.server.HardFist.currentTier(data));
        helper.assertTrue(ru.adaptionwheel.server.HardFist.isMaxed(data, 0),
                "wood must still read as maxed, so the handover does not erase what was earned");
        helper.assertTrue(!ru.adaptionwheel.server.HardFist.isMaxed(data, 1),
                "stone at level 1 must not read as maxed");
        helper.succeed();
    }

    /**
     * The adapt-count bonus has to be worth something at the top end.
     *
     * <p>At the old 0.04, 150 adaptations gave x7 while the Diamond stage multiplier alone is x8 —
     * so the curve that scales with how much you have adapted was weaker than one material tier.
     * This pins that a developed wheel out-earns a single top stage on breadth.
     */
    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void adaptCountOutEarnsASingleTopStage(GameTestHelper helper) {

        double perAdaptation = AdaptionConfig.FIST_DAMAGE_PER_ADAPTATION.get();
        double diamond = AdaptionConfig.fistDamageTierMultiplier(3);
        double netherite = AdaptionConfig.fistDamageTierMultiplier(4);

        helper.assertTrue(perAdaptation > 0.05,
                "damagePerAdaptation is " + perAdaptation + ": at that value even 150 adaptations"
                        + " stay below the Diamond stage multiplier of " + diamond
                        + ", which makes the adapt count decorative");
        helper.assertTrue(1 + 100 * perAdaptation >= netherite,
                "at 100 adaptations the multiplier is only x"
                        + String.format("%.1f", 1 + 100 * perAdaptation) + ", below Netherite's x"
                        + netherite + "; a well-developed wheel should reach the top stage on breadth");
        helper.succeed();
    }

    private static PlayerAdaption empty() {
        return new PlayerAdaption(new HashMap<>(), List.of(), List.of(), List.of(), List.of(),
                new HashMap<>(), new HashMap<>(), 0, 0, 0, false, 0f, 0f, false, 0, Extras.EMPTY);
    }
}

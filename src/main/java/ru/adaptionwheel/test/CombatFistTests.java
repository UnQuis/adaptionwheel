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

        helper.assertTrue(ru.adaptionwheel.server.MartialArts.currentTier(data) == 1,
                "with wood maxed and stone at level 1, the player is a Stone fighter, got tier "
                        + ru.adaptionwheel.server.MartialArts.currentTier(data));
        helper.assertTrue(ru.adaptionwheel.server.MartialArts.isMaxed(data, 0),
                "wood must still read as maxed, so the handover does not erase what was earned");
        helper.assertTrue(!ru.adaptionwheel.server.MartialArts.isMaxed(data, 1),
                "stone at level 1 must not read as maxed");
        helper.succeed();
    }

    /**
     * The handover marker is a gradient on exactly one level, and on opposite levels for the two
     * fists because their rows count differently.
     *
     * <p>The request was "when the player closes the eighth level of the wooden fist, one half
     * should be brown and the other grey, because he is moving to stone". The breaking fist's row
     * shows the <em>reach</em> tier, so that instant is its next material at level 0; the punching
     * fist's row shows the highest <em>trained</em> tier, so the same instant is wood at level 8.
     * Both must split, and nothing in between may.
     */
    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void barSplitsOnlyAtTheHandover(GameTestHelper helper) {
        int max = PlayerAdaption.MAX_LEVEL;

        // The breaking fist crosses at level 0: the row has already moved to the new material.
        helper.assertTrue(FistTiers.showsHandover(0, true, false),
                "the breaking fist must split on the level 0 row of the material it just reached");
        helper.assertTrue(!FistTiers.showsHandover(0, false, false),
                "level 0 with nothing maxed before it is a fresh ladder, not a handover");

        // The punching fist crosses at level 8, while the next material is still untouched.
        helper.assertTrue(FistTiers.showsHandover(max, false, true),
                "the punching fist must split on the last level of the material it is leaving");
        helper.assertTrue(!FistTiers.showsHandover(max, true, false),
                "a finished ladder has no next material to hand over to and must not split");

        // Every level strictly inside a stage is not a boundary, whatever its neighbours are.
        for (int level = 1; level < max; level++) {
            for (boolean previousMaxed : new boolean[] {false, true}) {
                for (boolean nextUntrained : new boolean[] {false, true}) {
                    helper.assertTrue(!FistTiers.showsHandover(level, previousMaxed, nextUntrained),
                            "level " + level + " split the bar (previousMaxed=" + previousMaxed
                                    + ", nextUntrained=" + nextUntrained + "); only the levels on"
                                    + " either side of a material boundary may split");
                }
            }
        }

        // Both fists agree, and a full row has exactly one split per boundary between materials.
        int splits = 0;
        for (int tier = 1; tier < CombatFistTiers.TIER_COUNT; tier++) {
            helper.assertTrue(CombatFistTiers.showsHandover(0, true, false)
                            == FistTiers.showsHandover(0, true, false),
                    "the two fists must agree about the handover");
            splits += FistTiers.showsHandover(0, true, false) ? 1 : 0;
        }
        helper.assertTrue(splits == CombatFistTiers.TIER_COUNT - 1,
                "expected one handover per material boundary, got " + splits);
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

    /**
     * A maxed stage must hand its kills to the next one, not throw them away.
     *
     * <p>Pinned because the row and the accounting disagreed: the row reported the next stage's cost
     * ("0/6 kills", which is what the player sees and complained about) while {@code onFistKill}
     * early-returned on the maxed stage and banked nothing. A hundred mobs against a bar that never
     * moved, and the display was, on paper, correct.
     */
    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void aMaxedStageHandsItsKillsToTheNext(GameTestHelper helper) {

        PlayerAdaption data = empty();
        data.levels.put(CombatFistTiers.concept(0), PlayerAdaption.MAX_LEVEL);
        data.invalidateAdaptCount();

        helper.assertTrue(ru.adaptionwheel.server.MartialArts.currentTier(data) == 0,
                "the stage reached is still Wood, which is permanent");
        helper.assertTrue(ru.adaptionwheel.server.MartialArts.trainingTier(data) == 1,
                "but the kills being trained are Stone's, got tier "
                        + ru.adaptionwheel.server.MartialArts.trainingTier(data));

        // A finished ladder has nothing to hand over to.
        PlayerAdaption done = empty();
        for (int t = 0; t < CombatFistTiers.TIER_COUNT; t++) {
            done.levels.put(CombatFistTiers.concept(t), PlayerAdaption.MAX_LEVEL);
        }
        done.invalidateAdaptCount();
        helper.assertTrue(ru.adaptionwheel.server.MartialArts.trainingTier(data) != -1,
                "an unfinished ladder still has a stage to train");
        helper.assertTrue(ru.adaptionwheel.server.MartialArts.trainingTier(done)
                        == CombatFistTiers.TIER_COUNT - 1,
                "Netherite maxed is the end of the ladder, so there is no stage after it, got "
                        + ru.adaptionwheel.server.MartialArts.trainingTier(done));

        // An unfinished stage trains itself.
        PlayerAdaption mid = empty();
        mid.levels.put(CombatFistTiers.concept(2), 4);
        mid.invalidateAdaptCount();
        helper.assertTrue(ru.adaptionwheel.server.MartialArts.trainingTier(mid) == 2,
                "Iron at level 4 is still being trained, got "
                        + ru.adaptionwheel.server.MartialArts.trainingTier(mid));
        helper.succeed();
    }

    /**
     * Every stage must resolve to its own material colour.
     *
     * <p>Only Wood ever did. The lookup was {@code startsWith(CombatFistTiers.CONCEPTS[0])}, i.e.
     * against the whole word {@code "Combat_FistWood"}, so Stone, Iron, Diamond and Netherite all
     * missed it and fell through to the generic combat colour -- red. Iron and Stone therefore drew
     * red text on a red bar while the split halves beside them used the correct tier table, so the
     * row contradicted itself: two colours for the same stage on one line.
     */
    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void everyStageHasItsOwnMaterialColour(GameTestHelper helper) {

        java.util.Set<Integer> seen = new java.util.HashSet<>();
        for (int t = 0; t < CombatFistTiers.TIER_COUNT; t++) {
            String concept = CombatFistTiers.concept(t);
            int resolved = ru.adaptionwheel.category.Concepts.color(concept);
            int expected = CombatFistTiers.color(t);
            helper.assertTrue(resolved == expected,
                    concept + " resolved to " + Integer.toHexString(resolved)
                            + " instead of its material colour " + Integer.toHexString(expected)
                            + "; anything equal to the generic combat colour means the lookup missed"
                            + " this stage");
            seen.add(resolved);

            // The breaking fist's ladder must resolve the same way, or the two rows disagree.
            int breaking = ru.adaptionwheel.category.Concepts.color(
                    ru.adaptionwheel.category.FistTiers.concept(t));
            helper.assertTrue(breaking == ru.adaptionwheel.category.FistTiers.color(t),
                    "the breaking fist's " + ru.adaptionwheel.category.FistTiers.concept(t)
                            + " resolved to " + Integer.toHexString(breaking));
        }
        helper.assertTrue(seen.size() == CombatFistTiers.TIER_COUNT,
                "five stages but only " + seen.size() + " distinct colours, so two materials look"
                        + " identical and the split cannot show a change");
        helper.succeed();
    }

    private static PlayerAdaption empty() {
        return new PlayerAdaption(new HashMap<>(), List.of(), List.of(), List.of(), List.of(),
                new HashMap<>(), new HashMap<>(), 0, 0, 0, false, 0f, 0f, false, 0, Extras.EMPTY);
    }
}

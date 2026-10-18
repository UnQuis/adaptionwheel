package ru.adaptionwheel.test;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import ru.adaptionwheel.AdaptionWheel;
import ru.adaptionwheel.server.AdaptationProgression;

@GameTestHolder(AdaptionWheel.MODID)
@PrefixGameTestTemplate(false)
public class AdaptationProgressionTests {

    private static final double EPSILON = 1.0e-9;

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void anEmptyWheelGetsNoBonus(GameTestHelper helper) {
        helper.assertTrue(AdaptationProgression.progress(0) == 0.0, "an empty wheel is at 0 progress");
        helper.assertTrue(AdaptationProgression.movementSpeedBonus(0) == 0.0,
                "and takes no speed from a count of zero");
        helper.assertTrue(AdaptationProgression.jumpStrengthBonus(0) == 0.0,
                "and no jump either, so a fresh wheel is byte-for-byte the vanilla character");
        helper.succeed();
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void progressionIsLinearAndCapped(GameTestHelper helper) {
        int cap = AdaptationProgression.DEVELOPMENT_CAP;
        helper.assertTrue(Math.abs(AdaptationProgression.progress(cap) - 1.0) < EPSILON,
                "the cap is full progression");
        helper.assertTrue(AdaptationProgression.progress(cap) == AdaptationProgression.progress(cap * 10),
                "and it does not move past the cap: an infinite wheel is not an infinite speed bonus");
        helper.assertTrue(AdaptationProgression.progress(cap / 2)
                        == AdaptationProgression.progress(cap) * 0.5,
                "half the count is half the progress, so the curve stays linear");
        helper.assertTrue(AdaptationProgression.progress(-25) == 0.0,
                "a negative count clamps to zero instead of going backwards");

        helper.assertTrue(Math.abs(AdaptationProgression.movementSpeedBonus(cap)
                        - AdaptationProgression.MAX_MOVEMENT_SPEED_BONUS) < EPSILON,
                "at the cap the speed bonus reaches its declared maximum");
        helper.assertTrue(Math.abs(AdaptationProgression.jumpStrengthBonus(cap)
                        - AdaptationProgression.MAX_JUMP_STRENGTH_BONUS) < EPSILON,
                "and the jump bonus reaches its own");
        helper.succeed();
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void bothBonusesGrowMonotonically(GameTestHelper helper) {
        double previousSpeed = -1.0;
        double previousJump = -1.0;
        for (int count = 0; count <= AdaptationProgression.DEVELOPMENT_CAP; count += 10) {
            double speed = AdaptationProgression.movementSpeedBonus(count);
            double jump = AdaptationProgression.jumpStrengthBonus(count);
            helper.assertTrue(speed >= previousSpeed, "speed never drops as the wheel fills, at count " + count);
            helper.assertTrue(jump >= previousJump, "and neither does jump, at count " + count);
            previousSpeed = speed;
            previousJump = jump;
        }
        helper.assertTrue(previousSpeed <= AdaptationProgression.MAX_MOVEMENT_SPEED_BONUS,
                "the speed bonus is bounded by its constant");
        helper.assertTrue(previousJump <= AdaptationProgression.MAX_JUMP_STRENGTH_BONUS,
                "the jump bonus is bounded by its constant");
        helper.succeed();
    }
}

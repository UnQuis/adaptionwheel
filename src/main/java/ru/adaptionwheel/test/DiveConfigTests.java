package ru.adaptionwheel.test;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import ru.adaptionwheel.AdaptionWheel;
import ru.adaptionwheel.config.AdaptionConfig;

/**
 * Pins the two Dive numbers whose defaults are load-bearing rather than cosmetic.
 *
 * <p>Both are the kind of value that reads fine and is wrong: a crater radius the block cap cannot
 * actually carve leaves a small ball in a big hole, and a "slam" slower than gravity is just a fall
 * with particles. Neither shows up as an exception, so they are pinned here.
 */
@GameTestHolder(AdaptionWheel.MODID)
@PrefixGameTestTemplate(false)
public class DiveConfigTests {

    /** Vanilla free fall settles at {@code 0.08 / (1 - 0.98)} blocks per tick. */
    private static final double TERMINAL_VELOCITY = 0.08D / (1.0D - 0.98D);

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void theDiveFallsFasterThanGravity(GameTestHelper helper) {
        double speed = AdaptionConfig.DIVE_SPEED.get();
        helper.assertTrue(speed > TERMINAL_VELOCITY,
                "a dive at " + speed + " blocks/tick is slower than terminal velocity ("
                        + TERMINAL_VELOCITY + "), so it would read as a normal fall");
        helper.succeed();
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void theBlockCapCanCarveTheConfiguredRadius(GameTestHelper helper) {
        double radius = AdaptionConfig.DIVE_RADIUS.get();
        int cap = AdaptionConfig.DIVE_MAX_BLOCKS.get();
        double volume = 4.0D / 3.0D * Math.PI * radius * radius * radius;
        helper.assertTrue(cap >= volume,
                "a radius of " + radius + " needs " + (int) volume
                        + " blocks, but the cap is " + cap + ", so the crater would be truncated");
        helper.succeed();
    }
}

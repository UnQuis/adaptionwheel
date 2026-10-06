package ru.adaptionwheel.test;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import ru.adaptionwheel.AdaptionWheel;
import ru.adaptionwheel.AdaptionTimings;

/**
 * Pins the tick-rate conversion for durations ported from the original.
 *
 * <p>Terraria runs at 60 ticks a second, Minecraft at 20, so a number copied across unchanged is
 * three times too long. Three separate mechanics were caught by this after being reported as
 * gameplay bugs:
 *
 * <ul>
 *   <li>the Lv8 defence capstone gave 6 seconds of invulnerability where the original gives 2;
 *   <li>the existence reflection gave half a second where the original gives about 33 ms;
 *   <li>the adversity countdown ran 24 seconds where the original runs 8.
 * </ul>
 *
 * <p>The original's own HUD is the proof of the rate and is worth quoting in the failure message,
 * because it is the fact that settles the argument: it renders remaining adversity time as
 * {@code AdversityTimer / 60}, so 60 ticks is one second there.
 */
@GameTestHolder(AdaptionWheel.MODID)
@PrefixGameTestTemplate(false)
public class AdaptionTimingTests {

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void adversityIsEightSecondsLikeTheOriginal(GameTestHelper helper) {

        int ticks = AdaptionTimings.ADVERSITY_TICKS;
        // 480 Terraria ticks at 60 tps is 8 seconds, so it must be 160 here.
        helper.assertTrue(ticks == 160,
                "the adversity countdown is " + ticks + " ticks = " + (ticks / 20.0)
                        + "s. The original is 480 Terraria ticks, and its own HUD divides that by 60"
                        + " to render seconds, so it is 8s there and 160 ticks here. Got "
                        + (ticks / 20.0) + "s, which is " + ((ticks / 20.0) / 8.0) + "x the original.");

        // The property that actually broke: a duration long enough that its own consumers misbehave.
        helper.assertTrue(ticks >= 20,
                "the countdown must be long enough to be a fight, not a single tick");
        helper.succeed();
    }

    /**
     * The i-frame windows are the same class of mistake, and they are config values rather than
     * constants, so they are pinned here too — a default quietly reverted in the config file would
     * bring permanent immunity straight back.
     */
    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void iFrameWindowsStayShorterThanAMeleeInterval(GameTestHelper helper) {

        int lv8 = ru.adaptionwheel.config.AdaptionConfig.DEFENSE_LV8_IFRAMES_TICKS.get();
        // The original's own 2 seconds is deliberately NOT the default here. A melee mob swings about
        // every 20 ticks in both games, so a 40-tick window re-arms before it lapses and an Lv8
        // "capstone" silently becomes permanent immunity to that damage category -- which is how it
        // was reported. What has to hold is the property, not the original's number.
        helper.assertTrue(lv8 < 20,
                "the Lv8 i-frame window is " + lv8 + " ticks, which is not shorter than a melee"
                        + " attack interval, so sustained melee can never make it lapse and the"
                        + " capstone is permanent immunity rather than i-frames");

        int reflect = ru.adaptionwheel.config.AdaptionConfig.EXISTENCE_REFLECT_IFRAMES_TICKS.get();
        helper.assertTrue(reflect <= 2,
                "reflected hits grant " + reflect + " ticks; the original grants max(immuneTime, 2),"
                        + " about 33ms, because reflection denies the boss its attack rather than"
                        + " shielding the player");
        helper.succeed();
    }
}
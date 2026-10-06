package ru.adaptionwheel.test;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import ru.adaptionwheel.AdaptionWheel;
import ru.adaptionwheel.AdaptionTimings;
import ru.adaptionwheel.config.AdaptionConfig;

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

    /**
     * The corrected default is not enough on its own -- a stored value has to be migrated.
     *
     * <p>NeoForge's config tracker writes the default only into a file that lacks a value and keeps
     * whatever is already stored, so changing 40 to 10 in code left every install that generated its
     * file in between still granting 40 ticks. That is two seconds, which outlasts a melee swing, so
     * those users kept the exact behaviour that had been reported as a bug and nothing in the log
     * said why.
     *
     * <p>The rewrite is deliberately narrow: only the old default is touched, because 40 is a value
     * the config comment offers a player who wants the original's two seconds, and a player who
     * typed it themselves must keep it.
     */
    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void theLegacyStoredValueIsMigrated(GameTestHelper helper) {

        // The value that was shipped, and therefore what an old config file actually contains.
        helper.assertTrue(AdaptionConfig.migrateLegacyLv8IFrames(AdaptionConfig.LEGACY_LV8_IFRAMES_TICKS)
                        == AdaptionConfig.DEFAULT_LV8_IFRAMES_TICKS,
                "a stored " + AdaptionConfig.LEGACY_LV8_IFRAMES_TICKS + " must migrate to "
                        + AdaptionConfig.DEFAULT_LV8_IFRAMES_TICKS + ", got "
                        + AdaptionConfig.migrateLegacyLv8IFrames(AdaptionConfig.LEGACY_LV8_IFRAMES_TICKS));

        // A fresh install reads the corrected default, not the legacy one.
        helper.assertTrue(AdaptionConfig.DEFAULT_LV8_IFRAMES_TICKS == 10,
                "the default is " + AdaptionConfig.DEFAULT_LV8_IFRAMES_TICKS + "; a fresh config would still be"
                        + " granting a window that outlasts a melee interval");

        // Everything else is a player choice and must survive untouched.
        int[] untouched = {0, 1, 5, 9, 11, 20, 39, 41, 60, 1200};
        for (int stored : untouched) {
            helper.assertTrue(AdaptionConfig.migrateLegacyLv8IFrames(stored) == stored,
                    "migrateLegacyLv8IFrames(" + stored + ") returned "
                            + AdaptionConfig.migrateLegacyLv8IFrames(stored) + "; only the old shipped default"
                            + " may be rewritten, and 0 must keep meaning no i-frames at all");
        }

        // And the migration must be one-shot, or a player who deliberately asks for 40 loses it on
        // every start. Only the flag's EXISTENCE is asserted, never its value: starting the gametest
        // server fires the migration for real, which sets and saves the flag, so asserting "still
        // false" would only pass on a machine that had never run the mod -- and would then fail for
        // everyone else. The value is the migration's business, not an invariant.
        helper.assertTrue(AdaptionConfig.LV8_IFRAMES_MIGRATION_DONE != null,
                "without a stored flag the rewrite would repeat forever and take away a player's"
                        + " deliberate choice");
        helper.succeed();
    }
}

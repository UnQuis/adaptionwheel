package ru.adaptionwheel.test;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import ru.adaptionwheel.cinematic.ExistenceCinematicTiming;
import ru.adaptionwheel.cinematic.ExistenceCinematicTiming;

/**
 * The Existence cinematic's schedule is read by the server (for the wheel's 45-degree clicks) and
 * by the client (for the sprites, the bars and the lines), so it has to hold at 20 tps as well as at
 * 60. The original counted Terraria ticks; copying those numbers raw would have run the whole
 * sequence in 27 seconds instead of 9.
 *
 * <p>Vanilla's {@code GameTestHelper} has no {@code assertEquals} — only {@code assertTrue} and
 * {@code assertFalse} — so equality is expressed as a comparison inside the condition, which keeps
 * the failure message carrying the numbers that were wrong.
 */
@GameTestHolder(ru.adaptionwheel.AdaptionWheel.MODID)
@PrefixGameTestTemplate(false)
public class ExistenceCinematicTests {

    /** 540 Terraria ticks at 60 tps is 9 seconds. */
    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void scheduleIsNineSeconds(GameTestHelper helper) {
        helper.assertTrue(ExistenceCinematicTiming.TOTAL_MS == 9_000,
                "total duration should be 9000ms, was " + ExistenceCinematicTiming.TOTAL_MS);
        helper.assertTrue(ExistenceCinematicTiming.LEVEL_MS[0] == 1_667,
                "first level at 1667ms, was " + ExistenceCinematicTiming.LEVEL_MS[0]);
        helper.assertTrue(ExistenceCinematicTiming.FINALE_MS == 7_500,
                "finale at 7500ms, was " + ExistenceCinematicTiming.FINALE_MS);
        helper.assertTrue(ExistenceCinematicTiming.LINE_OPENING_MS == 1_000,
                "opening line at 1000ms, was " + ExistenceCinematicTiming.LINE_OPENING_MS);
        helper.assertTrue(ExistenceCinematicTiming.LINE_MIDDLE_MS == 4_000,
                "middle line at 4000ms, was " + ExistenceCinematicTiming.LINE_MIDDLE_MS);
        helper.succeed();
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void boundariesAreOrdered(GameTestHelper helper) {
        helper.assertTrue(ExistenceCinematicTiming.LEVEL_MS.length == 7,
                "seven levels, was " + ExistenceCinematicTiming.LEVEL_MS.length);
        for (int i = 1; i < ExistenceCinematicTiming.LEVEL_MS.length; i++) {
            int previous = ExistenceCinematicTiming.LEVEL_MS[i - 1];
            int current = ExistenceCinematicTiming.LEVEL_MS[i];
            helper.assertTrue(current > previous,
                    "level " + i + " (" + current + ") must follow level " + (i - 1) + " (" + previous + ")");
        }
        helper.assertTrue(ExistenceCinematicTiming.FINALE_MS > ExistenceCinematicTiming.LEVEL_MS[6],
                "the finale must follow the last level");
        helper.assertTrue(ExistenceCinematicTiming.FINALE_MS < ExistenceCinematicTiming.TOTAL_MS,
                "the finale must precede the end");
        helper.succeed();
    }

    /**
     * The wheel click and the sprite both come from {@code levelsPassed}, so a boundary has to be
     * counted exactly once however the frame happened to land relative to it.
     */
    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void everyLevelIsCountedExactlyOnce(GameTestHelper helper) {
        helper.assertTrue(ExistenceCinematicTiming.levelsPassed(0) == 0,
                "nothing has passed at t=0");
        for (int i = 0; i < ExistenceCinematicTiming.LEVEL_MS.length; i++) {
            int at = ExistenceCinematicTiming.LEVEL_MS[i];
            int onBoundary = ExistenceCinematicTiming.levelsPassed(at);
            helper.assertTrue(onBoundary == i + 1,
                    "boundary " + i + " should read " + (i + 1) + " levels, read " + onBoundary);
            // A dropped frame that skips over a boundary must still count it exactly once.
            int afterDrop = ExistenceCinematicTiming.levelsPassed(at + 40);
            helper.assertTrue(afterDrop == i + 1,
                    "past boundary " + i + " it must still read " + (i + 1) + ", read " + afterDrop);
        }
        helper.assertTrue(ExistenceCinematicTiming.levelsPassed(ExistenceCinematicTiming.FINALE_MS) == 7,
                "all seven levels must have passed by the finale");
        helper.assertTrue(ExistenceCinematicTiming.finaleDue(ExistenceCinematicTiming.FINALE_MS),
                "the finale must be due at its own time");
        helper.assertFalse(ExistenceCinematicTiming.finaleDue(ExistenceCinematicTiming.FINALE_MS - 1),
                "the finale must not be due a moment earlier");
        helper.succeed();
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void barsRampInAndOut(GameTestHelper helper) {
        helper.assertTrue(ExistenceCinematicTiming.barFraction(0) < 0.01f,
                "no bars at the very start");
        helper.assertTrue(ExistenceCinematicTiming.barFraction(4_000) > 0.99f,
                "bars at full height mid-sequence");
        helper.assertTrue(ExistenceCinematicTiming.barFraction(ExistenceCinematicTiming.TOTAL_MS) < 0.01f,
                "bars must retract by the end");
        helper.assertTrue(ExistenceCinematicTiming.BAR_MAX_FRACTION > 0f
                        && ExistenceCinematicTiming.BAR_MAX_FRACTION < 0.5f,
                "the bar is a fraction of the screen, not the screen");
        helper.succeed();
    }

    /**
     * A long or multibyte boss name must not cost the player the reward.
     *
     * <p>{@code FriendlyByteBuf.writeUtf(String, int)} throws rather than truncating, on two
     * separate counts: UTF-16 length, and encoded UTF-8 bytes against
     * {@code ByteBufUtil.utf8MaxBytes}. Clamping to code points satisfies neither -- an astral
     * character is two units and up to four bytes -- so the payload clamps against both limits and
     * these are the inputs that used to throw an {@code EncoderException} instead of sending.
     */
    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void multibyteBossNamesStillEncode(GameTestHelper helper) {

        String[] names = {
                "Warden",
                "\uD83D\uDE00".repeat(200),                        // 200 emoji: 400 units, 800 bytes
                "\uD83D\uDE00".repeat(64),                         // exactly at the unit limit
                "\u0416\u043B\u0435\u0437\u043D\u044b\u0439".repeat(40), // Cyrillic, 2 bytes each
                "\u30C9\u30E9\u30DE".repeat(60),                  // CJK, 3 bytes each
                "x".repeat(500),                                    // plain but far too long
                "",
        };
        for (String name : names) {
            var buf = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
            String note = "name of " + name.codePointCount(0, name.length()) + " code points threw";
            try {
                ru.adaptionwheel.network.ExistenceCinematicPayload.STREAM_CODEC.encode(buf,
                        new ru.adaptionwheel.network.ExistenceCinematicPayload(7, name));
                var back = ru.adaptionwheel.network.ExistenceCinematicPayload.STREAM_CODEC.decode(buf);
                helper.assertTrue(back.bossEntityId() == 7,
                        "the entity id must survive alongside the clamped name");
                helper.assertTrue(back.bossName().length() <= 128,
                        "a decoded name of " + back.bossName().length()
                                + " units would not have been readable back, " + note);
                helper.assertTrue(io.netty.buffer.ByteBufUtil.utf8Bytes(back.bossName())
                                <= io.netty.buffer.ByteBufUtil.utf8MaxBytes(128),
                        "the decoded name still exceeds the byte budget, " + note);
            } catch (RuntimeException e) {
                helper.fail(note + ": " + e);
            } finally {
                buf.release();
            }
        }

        // The clamped prefix must still be the name, not a mangled half of a surrogate pair.
        var buf2 = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try {
            ru.adaptionwheel.network.ExistenceCinematicPayload.STREAM_CODEC.encode(buf2,
                    new ru.adaptionwheel.network.ExistenceCinematicPayload(1, "\uD83D\uDE00".repeat(200)));
            var back = ru.adaptionwheel.network.ExistenceCinematicPayload.STREAM_CODEC.decode(buf2);
            helper.assertTrue(back.bossName().codePoints().allMatch(
                            cp -> cp == 0x1F600),
                    "truncation must cut on a code point boundary, not split an emoji");
        } finally {
            buf2.release();
        }
        helper.succeed();
    }

    /**
     * The converted schedule must actually be non-zero, and in ascending order.
     *
     * <p>{@code TICK_MS} was declared after every field that calls {@code toMs(...)}. Static
     * initialisers run in declaration order, so each conversion read it while it was still zero:
     * every constant came out zero, the levels all passed at once, the finale was permanently due,
     * the sprite lifetimes collapsed and {@code barFraction(0)} returned NaN. Nothing threw and the
     * class loaded, so it failed as a cinematic that plays instantly and wrongly rather than as an
     * error -- which is exactly why these are asserted rather than eyeballed.
     *
     * <p>Values are compared inside the condition because vanilla's {@code GameTestHelper} has no
     * {@code assertEquals}.
     */
    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void convertedScheduleIsInitialisedAndOrdered(GameTestHelper helper) {

        helper.assertTrue(ExistenceCinematicTiming.TOTAL_MS == 9_000,
                "TOTAL_MS is " + ExistenceCinematicTiming.TOTAL_MS + ", expected 9000 (540 Terraria ticks at 60 tps)");

        int[] levels = ExistenceCinematicTiming.LEVEL_MS;
        helper.assertTrue(levels.length == ExistenceCinematicTiming.LEVEL_TICKS.length,
                "LEVEL_MS has " + levels.length + " entries for " + ExistenceCinematicTiming.LEVEL_TICKS.length + " ticks");
        for (int i = 0; i < levels.length; i++) {
            helper.assertTrue(levels[i] > 0,
                    "LEVEL_MS[" + i + "] is " + levels[i] + "; a converted constant of zero means"
                            + " TICK_MS was read before it was initialised");
            helper.assertTrue(levels[i] < ExistenceCinematicTiming.TOTAL_MS,
                    "LEVEL_MS[" + i + "] (" + levels[i] + ") is not inside the " + ExistenceCinematicTiming.TOTAL_MS
                            + "ms sequence");
            if (i > 0) {
                helper.assertTrue(levels[i] > levels[i - 1],
                        "LEVEL_MS is not ascending at " + i + ": " + levels[i - 1] + " then "
                                + levels[i] + "; the original's boundaries are strictly increasing");
            }
        }

        helper.assertTrue(ExistenceCinematicTiming.FINALE_MS > levels[levels.length - 1],
                "the finale at " + ExistenceCinematicTiming.FINALE_MS + "ms must come after the last level boundary at "
                        + levels[levels.length - 1] + "ms, or the burst lands before the last level");
        helper.assertTrue(ExistenceCinematicTiming.FINALE_MS < ExistenceCinematicTiming.TOTAL_MS,
                "the finale at " + ExistenceCinematicTiming.FINALE_MS + "ms must fall inside the sequence");

        helper.assertTrue(ExistenceCinematicTiming.LINE_OPENING_MS > 0 && ExistenceCinematicTiming.LINE_MIDDLE_MS > ExistenceCinematicTiming.LINE_OPENING_MS,
                "the floating lines are at " + ExistenceCinematicTiming.LINE_OPENING_MS + " and " + ExistenceCinematicTiming.LINE_MIDDLE_MS
                        + "; both must be non-zero and the later one later");
        helper.assertTrue(ExistenceCinematicTiming.BAR_RAMP_MS > 0 && ExistenceCinematicTiming.BAR_OUT_START_MS > ExistenceCinematicTiming.BAR_RAMP_MS,
                "the bar ramps over " + ExistenceCinematicTiming.BAR_RAMP_MS + "ms and leaves at " + ExistenceCinematicTiming.BAR_OUT_START_MS
                        + "ms; both must be non-zero and in order");
        helper.assertTrue(ExistenceCinematicTiming.GAKON_LIFE_MS > 0 && ExistenceCinematicTiming.GAKON_FINALE_LIFE_MS > ExistenceCinematicTiming.GAKON_LIFE_MS,
                "sprite lifetimes are " + ExistenceCinematicTiming.GAKON_LIFE_MS + " and " + ExistenceCinematicTiming.GAKON_FINALE_LIFE_MS
                        + "ms; a zero lifetime means the sprite never draws");

        // 0/0 must never be reachable now that the divisors are real.
        float bar = ExistenceCinematicTiming.barFraction(0);
        helper.assertTrue(!Float.isNaN(bar),
                "barFraction(0) is NaN, which means it is still dividing by a zero duration");
        helper.assertTrue(bar == 0f, "barFraction(0) should be 0, got " + bar);
        // The bars ramp IN and back OUT, so zero at both ends and full in the middle. That is the
        // original's shape: min(1, t/30) while opening, then max(0, (540 - t)/30) once past 510.
        helper.assertTrue(ExistenceCinematicTiming.barFraction(ExistenceCinematicTiming.TOTAL_MS) == 0f,
                "the bars must be gone by the end of the sequence, got "
                        + ExistenceCinematicTiming.barFraction(ExistenceCinematicTiming.TOTAL_MS));
        helper.assertTrue(ExistenceCinematicTiming.barFraction(ExistenceCinematicTiming.TOTAL_MS / 2) == 1f,
                "the bars must be at full height through the middle of the sequence, got "
                        + ExistenceCinematicTiming.barFraction(ExistenceCinematicTiming.TOTAL_MS / 2));

        helper.assertTrue(ExistenceCinematicTiming.levelsPassed(0) == 0,
                "no level should have been passed at t=0, got " + ExistenceCinematicTiming.levelsPassed(0));
        helper.assertTrue(ExistenceCinematicTiming.levelsPassed(ExistenceCinematicTiming.TOTAL_MS) == ExistenceCinematicTiming.LEVEL_TICKS.length,
                "every level should have been passed by the end, got " + ExistenceCinematicTiming.levelsPassed(ExistenceCinematicTiming.TOTAL_MS));
        helper.assertTrue(!ExistenceCinematicTiming.finaleDue(0),
                "the finale must not be due at t=0, or the whole sequence fires on the first frame");
        helper.succeed();
    }
}

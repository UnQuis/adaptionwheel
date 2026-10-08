package ru.adaptionwheel.cinematic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Existence cinematic's schedule is read by the server (for the wheel's clicks) and by the client
 * (for the sprites, the bars and the lines), so it has to hold at 20 tps as well as at 60. The
 * original counted Terraria ticks; copying those numbers raw would have run the whole sequence in 27
 * seconds instead of 9.
 *
 * <p>These were gametests on the 1.21.1 branch. 26.3 removed that harness -- no {@code @GameTestHolder},
 * no {@code @PrefixGameTestTemplate}, and no registry left to register a test with -- so the
 * assertions are unchanged and only the harness underneath them is different. They run in
 * {@code ./gradlew test}, which this branch did not have before.
 */
class ExistenceCinematicTimingTest {

    /** 540 Terraria ticks at 60 tps is 9 seconds. */
    @Test
    void scheduleIsNineSeconds() {
        assertEquals(9_000, ExistenceCinematicTiming.TOTAL_MS, "total duration");
        assertEquals(1_667, ExistenceCinematicTiming.LEVEL_MS[0], "first level");
        assertEquals(7_500, ExistenceCinematicTiming.FINALE_MS, "finale");
        assertEquals(1_000, ExistenceCinematicTiming.LINE_OPENING_MS, "opening line");
        assertEquals(4_000, ExistenceCinematicTiming.LINE_MIDDLE_MS, "middle line");
    }

    @Test
    void boundariesAreOrdered() {
        assertEquals(7, ExistenceCinematicTiming.LEVEL_MS.length, "seven levels");
        for (int i = 1; i < ExistenceCinematicTiming.LEVEL_MS.length; i++) {
            assertTrue(ExistenceCinematicTiming.LEVEL_MS[i] > ExistenceCinematicTiming.LEVEL_MS[i - 1],
                    "level " + i + " (" + ExistenceCinematicTiming.LEVEL_MS[i]
                            + ") must follow level " + (i - 1) + " (" + ExistenceCinematicTiming.LEVEL_MS[i - 1] + ")");
        }
        assertTrue(ExistenceCinematicTiming.FINALE_MS > ExistenceCinematicTiming.LEVEL_MS[6],
                "the finale must follow the last level");
        assertTrue(ExistenceCinematicTiming.FINALE_MS < ExistenceCinematicTiming.TOTAL_MS,
                "the finale must precede the end");
    }

    /**
     * The wheel click and the sprite both come from {@code levelsPassed}, so a boundary has to be
     * counted exactly once however the frame happened to land relative to it.
     */
    @Test
    void everyLevelIsCountedExactlyOnce() {
        assertEquals(0, ExistenceCinematicTiming.levelsPassed(0), "nothing has passed at t=0");
        for (int i = 0; i < ExistenceCinematicTiming.LEVEL_MS.length; i++) {
            int at = ExistenceCinematicTiming.LEVEL_MS[i];
            assertEquals(i + 1, ExistenceCinematicTiming.levelsPassed(at),
                    "boundary " + i + " sits exactly on its own level count");
            // A dropped frame that skips over a boundary must still count it exactly once.
            assertEquals(i + 1, ExistenceCinematicTiming.levelsPassed(at + 40),
                    "past boundary " + i + " it must still read " + (i + 1));
        }
        assertEquals(7, ExistenceCinematicTiming.levelsPassed(ExistenceCinematicTiming.FINALE_MS),
                "all seven levels must have passed by the finale");
        assertTrue(ExistenceCinematicTiming.finaleDue(ExistenceCinematicTiming.FINALE_MS),
                "the finale must be due at its own time");
        assertFalse(ExistenceCinematicTiming.finaleDue(ExistenceCinematicTiming.FINALE_MS - 1),
                "the finale must not be due a moment earlier");
    }

    @Test
    void barsRampInAndOut() {
        assertTrue(ExistenceCinematicTiming.barFraction(0) < 0.01f, "no bars at the very start");
        assertTrue(ExistenceCinematicTiming.barFraction(4_000) > 0.99f, "bars at full height mid-sequence");
        assertTrue(ExistenceCinematicTiming.barFraction(ExistenceCinematicTiming.TOTAL_MS) < 0.01f,
                "bars must retract by the end");
        assertTrue(ExistenceCinematicTiming.BAR_MAX_FRACTION > 0f
                        && ExistenceCinematicTiming.BAR_MAX_FRACTION < 0.5f,
                "the bar is a fraction of the screen, not the screen");
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
     */
    @Test
    void convertedScheduleIsInitialisedAndOrdered() {
        assertEquals(9_000, ExistenceCinematicTiming.TOTAL_MS,
                "TOTAL_MS is " + ExistenceCinematicTiming.TOTAL_MS
                        + ", expected 9000 (540 Terraria ticks at 60 tps)");

        int[] levels = ExistenceCinematicTiming.LEVEL_MS;
        assertEquals(ExistenceCinematicTiming.LEVEL_TICKS.length, levels.length,
                "LEVEL_MS has " + levels.length + " entries for "
                        + ExistenceCinematicTiming.LEVEL_TICKS.length + " ticks");
        for (int i = 0; i < levels.length; i++) {
            assertTrue(levels[i] > 0,
                    "LEVEL_MS[" + i + "] is " + levels[i] + "; a converted constant of zero means"
                            + " TICK_MS was read before it was initialised");
            assertTrue(levels[i] < ExistenceCinematicTiming.TOTAL_MS,
                    "LEVEL_MS[" + i + "] (" + levels[i] + ") is not inside the "
                            + ExistenceCinematicTiming.TOTAL_MS + "ms sequence");
            if (i > 0) {
                assertTrue(levels[i] > levels[i - 1],
                        "LEVEL_MS is not ascending at " + i + ": " + levels[i - 1]
                                + " then " + levels[i] + "; the original's boundaries are strictly increasing");
            }
        }

        assertTrue(ExistenceCinematicTiming.FINALE_MS > levels[levels.length - 1],
                "the finale at " + ExistenceCinematicTiming.FINALE_MS
                        + "ms must come after the last level boundary at " + levels[levels.length - 1]
                        + "ms, or the burst lands before the last level");
        assertTrue(ExistenceCinematicTiming.FINALE_MS < ExistenceCinematicTiming.TOTAL_MS,
                "the finale at " + ExistenceCinematicTiming.FINALE_MS + "ms must fall inside the sequence");

        assertTrue(ExistenceCinematicTiming.LINE_OPENING_MS > 0
                        && ExistenceCinematicTiming.LINE_MIDDLE_MS > ExistenceCinematicTiming.LINE_OPENING_MS,
                "the floating lines are at " + ExistenceCinematicTiming.LINE_OPENING_MS + " and "
                        + ExistenceCinematicTiming.LINE_MIDDLE_MS + "; both must be non-zero and the later one later");
        assertTrue(ExistenceCinematicTiming.BAR_RAMP_MS > 0
                        && ExistenceCinematicTiming.BAR_OUT_START_MS > ExistenceCinematicTiming.BAR_RAMP_MS,
                "the bar ramps over " + ExistenceCinematicTiming.BAR_RAMP_MS + "ms and leaves at "
                        + ExistenceCinematicTiming.BAR_OUT_START_MS + "ms; both must be non-zero and in order");
        assertTrue(ExistenceCinematicTiming.GAKON_LIFE_MS > 0
                        && ExistenceCinematicTiming.GAKON_FINALE_LIFE_MS > ExistenceCinematicTiming.GAKON_LIFE_MS,
                "sprite lifetimes are " + ExistenceCinematicTiming.GAKON_LIFE_MS + " and "
                        + ExistenceCinematicTiming.GAKON_FINALE_LIFE_MS
                        + "ms; a zero lifetime means the sprite never draws");

        // 0/0 must never be reachable now that the divisors are real.
        float bar = ExistenceCinematicTiming.barFraction(0);
        assertFalse(Float.isNaN(bar),
                "barFraction(0) is NaN, which means it is still dividing by a zero duration");
        assertEquals(0f, bar, "barFraction(0)");
        // The bars ramp IN and back OUT, so zero at both ends and full in the middle. That is the
        // original's shape: min(1, t/30) while opening, then max(0, (540 - t)/30) once past 510.
        assertEquals(0f, ExistenceCinematicTiming.barFraction(ExistenceCinematicTiming.TOTAL_MS),
                "the bars must be gone by the end of the sequence");
        assertEquals(1f, ExistenceCinematicTiming.barFraction(ExistenceCinematicTiming.TOTAL_MS / 2),
                "the bars must be at full height through the middle of the sequence");

        assertEquals(0, ExistenceCinematicTiming.levelsPassed(0), "no level passed at t=0");
        assertEquals(ExistenceCinematicTiming.LEVEL_TICKS.length,
                ExistenceCinematicTiming.levelsPassed(ExistenceCinematicTiming.TOTAL_MS),
                "every level passed by the end");
        assertFalse(ExistenceCinematicTiming.finaleDue(0),
                "the finale must not be due at t=0, or the whole sequence fires on the first frame");
    }
}
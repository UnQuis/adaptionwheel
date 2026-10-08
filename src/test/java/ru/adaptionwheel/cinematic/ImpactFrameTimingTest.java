package ru.adaptionwheel.cinematic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The impact panel's arithmetic.
 *
 * <p>Two of these exist because the failure is invisible rather than loud. A panel whose strength
 * decayed per frame still looks like a panel, on the machine you tested it on. And a flash whose
 * rate was derived from the frame counter still looks like a flash at 60 fps and becomes a strobe at
 * 240. Both are pinned here so a later edit has to argue with a test instead of with a screenshot.
 */
class ImpactFrameTimingTest {

    @Test
    void thePanelLastsFourHundredAndFiftyMilliseconds() {
        assertEquals(0.45f, ImpactFrameTiming.TOTAL, 1e-6f, "hold plus fade");
        assertTrue(ImpactFrameTiming.playing(0f), "playing at the hit");
        assertTrue(ImpactFrameTiming.playing(ImpactFrameTiming.TOTAL - 0.001f), "playing just before the end");
        assertFalse(ImpactFrameTiming.playing(ImpactFrameTiming.TOTAL), "over at the end");
        assertFalse(ImpactFrameTiming.playing(Float.MAX_VALUE), "parked when nothing is playing");
    }

    @Test
    void strengthSnapsThenFallsOffACliff() {
        assertEquals(1f, ImpactFrameTiming.strengthAt(0f), 1e-6f, "full at the hit");
        assertEquals(1f, ImpactFrameTiming.strengthAt(ImpactFrameTiming.HOLD), 1e-6f, "still full after the hold");
        assertEquals(0f, ImpactFrameTiming.strengthAt(ImpactFrameTiming.TOTAL), 1e-6f, "gone at the end");

        // The point of the cubic: most of the life is spent near full, rather than spending the whole
        // fade half faded, which is what makes a panel read as a dissolve instead of a hit.
        assertTrue(ImpactFrameTiming.strengthAt(ImpactFrameTiming.HOLD + ImpactFrameTiming.FADE / 2f) > 0.8f,
                "still strong halfway through the fade, was "
                        + ImpactFrameTiming.strengthAt(ImpactFrameTiming.HOLD + ImpactFrameTiming.FADE / 2f));

        // Monotone: never rises, so the panel can only ever lose the frame.
        float previous = Float.MAX_VALUE;
        for (int i = 0; i <= 100; i++) {
            float strength = ImpactFrameTiming.strengthAt(ImpactFrameTiming.TOTAL * i / 100f);
            assertTrue(strength <= previous, "strength rose at " + (ImpactFrameTiming.TOTAL * i / 100f) + "s");
            assertTrue(strength >= 0f && strength <= 1f, "strength out of range: " + strength);
            previous = strength;
        }
    }

    /**
     * The mode alternation is the punch, so its tempo is pinned.
     *
     * <p>Two failure modes matter and neither is loud. A step far shorter than a frame at 60 fps
     * would put several mode cuts inside one rendered frame, which is invisible -- the panel just
     * looks like one mode with a slightly wrong colour. A step far longer than {@link
     * ImpactFrameTiming#TOTAL} would never cut at all, and the effect silently degrades to the flat
     * colour grade it was built to replace.
     */
    @Test
    void modesCutFastEnoughToReadAsAnImpactFrame() {
        assertEquals(0.045f, ImpactFrameTiming.MODE_STEP, 1e-6f, "the reference tempo");

        // The panel must contain several cuts, not one or none.
        int cuts = 0;
        int previous = ImpactFrameTiming.modeAt(0f);
        for (int i = 1; i <= 100; i++) {
            int mode = ImpactFrameTiming.modeAt(ImpactFrameTiming.TOTAL * i / 100f);
            if (mode != previous) {
                cuts++;
                previous = mode;
            }
        }
        assertTrue(cuts >= 4, "expected the mode to cut repeatedly over the panel, got " + cuts);

        // The two modes are inverses of each other, which is the whole reason this pair works.
        assertTrue(ImpactFrameTiming.MODE_A != ImpactFrameTiming.MODE_B, "must alternate, not hold");
        assertEquals(ImpactFrameTiming.MODE_A, ImpactFrameTiming.modeAt(0f), "starts on the first mode");
        assertEquals(ImpactFrameTiming.MODE_B, ImpactFrameTiming.modeAt(ImpactFrameTiming.MODE_STEP + 1e-4f),
                "cuts to the second mode one step in");

        // No age returns "no frame": the strength envelope handles appearing and disappearing, and a
        // gap mid-hold would read as a dropped frame rather than as an impact.
        for (int i = 0; i <= 100; i++) {
            int mode = ImpactFrameTiming.modeAt(ImpactFrameTiming.TOTAL * i / 100f);
            assertTrue(mode == ImpactFrameTiming.MODE_A || mode == ImpactFrameTiming.MODE_B,
                    "unexpected mode " + mode);
        }
        assertEquals(ImpactFrameTiming.MODE_A, ImpactFrameTiming.modeAt(-1f), "before the hit");
    }

    /**
     * A config-driven mode has to survive being typed by hand.
     *
     * <p>Modulo rather than clamping, so an out-of-range value still produces a live frame. A clamp
     * would leave a user who typed 40 looking at mode 35 forever with no idea why.
     */
    @Test
    void anOutOfRangeModeStillProducesALiveFrame() {
        assertEquals(0, ImpactFrameTiming.modeAt(0f, 36, 0), "one past the end wraps to the first mode");
        assertEquals(35, ImpactFrameTiming.modeAt(0f, -1, 0), "negative wraps into range");
        for (int mode = -50; mode < 50; mode++) {
            int resolved = ImpactFrameTiming.modeAt(0f, mode, mode);
            assertTrue(resolved >= 0 && resolved < ImpactFrameTiming.MODE_COUNT,
                    "mode " + mode + " resolved out of range to " + resolved);
        }
    }

    @Test
    void progressRunsZeroToOne() {
        assertEquals(0f, ImpactFrameTiming.progressAt(0f), 1e-6f);
        assertEquals(0.5f, ImpactFrameTiming.progressAt(ImpactFrameTiming.TOTAL / 2f), 1e-6f);
        assertEquals(1f, ImpactFrameTiming.progressAt(ImpactFrameTiming.TOTAL), 1e-6f);
        // Pinned rather than throwing: the panel is a fade, so an overshooting age should still read
        // as "finished" and must never come back down.
        assertEquals(1f, ImpactFrameTiming.progressAt(ImpactFrameTiming.TOTAL * 3f), 1e-6f);
    }

    /**
     * The flash rate is the number that actually matters, so it is pinned for the shipped defaults.
     *
     * <p>20 changes/second sits inside the 3-25 Hz band where flicker reads as motion rather than as
     * a discrete flash, which is the band the original mod's own defaults live in. That is a
     * deliberate match, not an endorsement: a user who raises the frame count without shortening the
     * duration walks straight out of it, which is what {@link #raisingFramesWithoutShorteningSpedsUp}
     * pins down.
     */
    @Test
    void shippedFlashRateIsTwentyPerSecond() {
        assertEquals(20f, ImpactFrameTiming.flashRate(5, 250), 1e-4f, "5 steps over 250ms");
    }

    @Test
    void raisingFramesWithoutShorteningSpedsUp() {
        // This is the trap in the config: frames and length are coupled through the rate, so
        // "double the frames" does not lengthen the flash, it doubles its frequency.
        assertEquals(40f, ImpactFrameTiming.flashRate(10, 250), 1e-4f);
        assertEquals(20f, ImpactFrameTiming.flashRate(10, 500), 1e-4f, "more frames over twice the time is the same rate");
    }

    @Test
    void flashIndexWalksItsStepsAndStops() {
        assertEquals(0, ImpactFrameTiming.flashIndex(0f, 5, 250), "first step");
        assertEquals(4, ImpactFrameTiming.flashIndex(0.249f, 5, 250), "last step before the cut");
        assertEquals(-1, ImpactFrameTiming.flashIndex(0.25f, 5, 250), "nothing after the duration");
        assertEquals(-1, ImpactFrameTiming.flashIndex(-1f, 5, 250), "nothing before the start");

        // Every step must be reachable, or the sequence silently skips colours and the flash is not
        // the one that was designed.
        boolean[] seen = new boolean[5];
        for (int i = 0; i < 200; i++) {
            seen[ImpactFrameTiming.flashIndex(i * 0.00125f, 5, 250)] = true;
        }
        for (int i = 0; i < seen.length; i++) {
            assertTrue(seen[i], "flash step " + i + " is never produced");
        }
    }

    /** A zero-length or negative flash must not divide by zero into a NaN step index. */
    @Test
    void degenerateFlashConfigDoesNotBlowUp() {
        assertEquals(0, ImpactFrameTiming.flashIndex(0f, 0, 0), "clamped to one step, not NaN");
        assertTrue(Float.isFinite(ImpactFrameTiming.flashRate(5, 0)), "rate stays finite");
    }
}
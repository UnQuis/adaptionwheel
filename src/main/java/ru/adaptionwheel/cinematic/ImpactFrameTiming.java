package ru.adaptionwheel.cinematic;

/**
 * Pure arithmetic for the impact panel and its flash.
 *
 * <p>Deliberately free of any rendering, entity or world state: given an age in seconds (and,
 * for the flash, a frame count and duration in milliseconds) it answers "what should be on
 * screen right now", and nothing else. That makes it cheap to call every frame and cheap to
 * pin down in tests instead of screenshots.
 */
public final class ImpactFrameTiming {

    /** How long the panel sits at full strength before the fade begins. */
    public static final float HOLD = 0.1f;

    /** How long the fade itself takes, once it starts. */
    public static final float FADE = 0.35f;

    /** Total lifetime of the panel: hold plus fade. */
    public static final float TOTAL = HOLD + FADE;

    /**
     * How long each impact mode is held before the next one cuts in.
     *
     * <p>This is the tempo of the whole effect. A single fixed look reads as a colour grade laid
     * over the world; cutting between several looks on a fast fixed clock is what reads as a drawn
     * impact frame. 45 ms is the value the reference implementation uses and it is deliberate:
     * at 60 fps that is roughly every third frame, so the cut lands between rendered frames rather
     * than on them, and at 20 changes/second it is animation rather than a strobe.
     */
    public static final float MODE_STEP = 0.045f;

    /** Highest mode index the shader implements. Used to clamp a config value safely. */
    public static final int MODE_COUNT = 36;

    /**
     * The two modes this mod alternates between.
     *
     * <p>Chosen as a pair that reads as one idea rather than as two looks. Mode 0 is the plainest
     * manga frame there is -- black shapes on white paper -- and mode 1 is its exact inverse, white
     * shapes on black. Alternating an image with its negative is the oldest impact-frame trick in
     * the medium and it survives being cut to at 45 ms, which a pair of unrelated palettes does
     * not: two different colour worlds in alternation read as a slideshow, not as a hit.
     *
     * <p>The full set of 36 lives in the shader. These two are the ones this mod ships with, and
     * {@link #modeAt} is where a config-driven choice would slot in.
     */
    public static final int MODE_A = 0;
    public static final int MODE_B = 1;

    private ImpactFrameTiming() {
    }

    /**
     * Which mode should be on screen at the given age.
     *
     * <p>Alternates {@link #MODE_A} and {@link #MODE_B} every {@link #MODE_STEP}, and holds the last
     * one through the fade. It does not return "no frame" at any age: the panel's own strength
     * envelope already handles appearing and disappearing, and a gap in the middle of a hold would
     * read as a dropped frame rather than as an impact.
     */
    public static int modeAt(float ageSeconds, int modeA, int modeB) {
        int a = clampMode(modeA);
        int b = clampMode(modeB);
        if (ageSeconds < 0f) {
            return a;
        }
        int step = (int) (ageSeconds / MODE_STEP);
        return step % 2 == 0 ? a : b;
    }

    /**
     * Folds a configured mode index into the range the shader actually implements.
     *
     * <p>Modulo rather than a clamp: an out-of-range config value would then land on some real
     * mode instead of sticking to whichever end it was nearest, and a user who typed 40 gets
     * something other than a panel frozen on mode 35.
     */
    private static int clampMode(int mode) {
        int m = Math.floorMod(mode, MODE_COUNT);
        return m;
    }

    /** {@link #modeAt} with the shipped defaults. */
    public static int modeAt(float ageSeconds) {
        return modeAt(ageSeconds, MODE_A, MODE_B);
    }

    /**
     * Whether the panel should still be drawn at the given age. {@code false} both before the
     * panel starts and once it has run past {@link #TOTAL}, so a stray huge age (nothing playing)
     * reads as "not playing" rather than as an error.
     */
    public static boolean playing(float ageSeconds) {
        return ageSeconds >= 0f && ageSeconds < TOTAL;
    }

    /**
     * Strength of the panel at the given age: 1 for the whole hold, then a cubic fall to 0 over
     * the fade.
     *
     * <p>The cubic is {@code 1 - p^3} rather than {@code (1 - p)^3}: the former stays close to 1
     * for most of {@code p} and only drops hard near the end, which is what makes the panel read
     * as a hit that is cut off rather than as a smooth dissolve.
     */
    public static float strengthAt(float ageSeconds) {
        if (ageSeconds <= HOLD) {
            return 1f;
        }
        if (ageSeconds >= TOTAL) {
            return 0f;
        }
        float p = (ageSeconds - HOLD) / FADE;
        return 1f - p * p * p;
    }

    /**
     * Linear progress through the panel's whole lifetime, clamped to {@code [0, 1]}. Clamped
     * rather than left to overshoot, because the panel is a fade: an age past {@link #TOTAL}
     * still means "finished", not "wrapped around".
     */
    public static float progressAt(float ageSeconds) {
        float p = ageSeconds / TOTAL;
        if (p < 0f) {
            return 0f;
        }
        if (p > 1f) {
            return 1f;
        }
        return p;
    }

    /**
     * How many flash-colour changes happen per second for the given config.
     *
     * <p>Frame count and duration are coupled through this rate on purpose: it is what the
     * original defaults (5 frames / 250ms = 20 Hz) rely on. The trap is that raising the frame
     * count without raising the duration to match does not lengthen the flash, it speeds it up.
     */
    public static float flashRate(int frameCount, int durationMs) {
        int frames = Math.max(frameCount, 1);
        int duration = Math.max(durationMs, 1);
        return frames / (duration / 1000f);
    }

    /**
     * Which flash step should be shown at the given age, or {@code -1} if the flash has not
     * started yet or has already finished.
     *
     * <p>Frame count and duration are both clamped to at least 1 before anything is divided by
     * them, so a misconfigured (zero or negative) flash degrades to a single, well-defined step
     * instead of producing {@code NaN} or throwing.
     */
    public static int flashIndex(float ageSeconds, int frameCount, int durationMs) {
        if (ageSeconds < 0f) {
            return -1;
        }
        int frames = Math.max(frameCount, 1);
        int duration = Math.max(durationMs, 1);
        float durationSeconds = duration / 1000f;
        if (ageSeconds >= durationSeconds) {
            return -1;
        }
        float stepLength = durationSeconds / frames;
        int index = (int) (ageSeconds / stepLength);
        return Math.min(index, frames - 1);
    }
}
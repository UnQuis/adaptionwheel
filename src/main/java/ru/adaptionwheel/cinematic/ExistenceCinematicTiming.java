package ru.adaptionwheel.cinematic;

import net.minecraft.util.Mth;

/**
 * The Existence cinematic's schedule, converted from the original mod's Terraria ticks.
 *
 * <p>Everything here is <b>wall-clock milliseconds</b>, never ticks. The original counted
 * {@code 540} ticks at Terraria's 60 tps, which is 9 seconds; a Minecraft copy that kept the raw
 * numbers would run the whole thing in 27 seconds at 20 tps. Same reasoning as the impact frame's
 * wall-clock rewrite: a per-frame or per-tick schedule silently changes length with the player's
 * hardware.
 *
 * <p>Lives outside {@code client} because the server drives the wheel rotation from the same
 * numbers — two copies of a schedule is exactly how the two sides drift apart, and you get a wheel
 * that clicks at the wrong moments.
 */
public final class ExistenceCinematicTiming {

    /**
     * Terraria ran at 60 tps, Minecraft at 20; every schedule here is stated in the original's ticks.
     *
     * <p>Declared FIRST, and that is load-bearing rather than tidiness. Static initialisers run in
     * declaration order, so every {@code toMs(...)} below reads this field while it is still being
     * initialised. With it declared after them, {@code TICK_MS} was still {@code 0.0f} during all
     * eight conversions: every converted constant came out zero, so {@code levelsPassed} reported
     * every level immediately, {@code finaleDue} was permanently true, the sprites' lifetimes
     * collapsed, and {@code barFraction(0)} evaluated {@code 0/0} and returned NaN.
     *
     * <p>None of it throws, and the class loads, so it fails as a cinematic that plays instantly and
     * wrongly rather than as an error.
     */
    private static final float TICK_MS = 1000f / 60f;

    /** 540 Terraria ticks at 60 tps. */
    public static final int TOTAL_MS = 9_000;

    /**
     * The seven escalating level boundaries, in Terraria ticks: {@code {100, 140, 180, 220, 260,
     * 300, 340}}. Converted at 1000/60 ms per tick.
     */
    public static final int[] LEVEL_TICKS = {100, 140, 180, 220, 260, 300, 340};

    /** Where each boundary lands in ms. Same length as {@link #LEVEL_TICKS}. */
    public static final int[] LEVEL_MS = toMs(LEVEL_TICKS);

    /** The finale: tick 450, where the big burst and the punch happen. */
    public static final int FINALE_MS = toMs(450);

    public static final int[] LEVEL_BOUNDARIES_MS = buildBoundaries();

    private static int[] buildBoundaries() {
        int[] boundaries = new int[LEVEL_MS.length + 1];
        System.arraycopy(LEVEL_MS, 0, boundaries, 0, LEVEL_MS.length);
        boundaries[LEVEL_MS.length] = FINALE_MS;
        return boundaries;
    }

    /** The original's floating lines, at ticks 60, 240 and 450. */
    public static final int LINE_OPENING_MS = toMs(60);
    public static final int LINE_MIDDLE_MS = toMs(240);

    /** Cinematic bars: 12% of screen height, ramping in over 30 ticks and out over the last 30. */
    public static final float BAR_MAX_FRACTION = 0.12f;
    public static final int BAR_RAMP_MS = toMs(30);
    public static final int BAR_OUT_START_MS = toMs(510);

    /** How long each rising Gakon sprite lives, and the finale's longer one. */
    public static final int GAKON_LIFE_MS = toMs(35);
    public static final int GAKON_FINALE_LIFE_MS = toMs(60);

    private ExistenceCinematicTiming() {
    }

    private static int toMs(int terrariaTick) {
        return Math.round(terrariaTick * TICK_MS);
    }

    private static int[] toMs(int[] terrariaTicks) {
        int[] ms = new int[terrariaTicks.length];
        for (int i = 0; i < terrariaTicks.length; i++) {
            ms[i] = toMs(terrariaTicks[i]);
        }
        return ms;
    }

    /**
     * How many of the seven level boundaries have passed by {@code elapsedMs}. Drives both the
     * wheel's 45-degree click and the sprites, which is why they must not be counted separately.
     */
    public static int levelsPassed(int elapsedMs) {
        int passed = 0;
        for (int boundary : LEVEL_MS) {
            if (elapsedMs >= boundary) {
                passed++;
            }
        }
        return passed;
    }

    public static int existenceLevel(int elapsedMs) {
        int level = 0;
        for (int boundary : LEVEL_BOUNDARIES_MS) {
            if (elapsedMs >= boundary) level++;
        }
        return level;
    }

    /**
     * Fractional fill, 0..1, of the CURRENT level's segment: 0 right after the previous cue, 1
     * right as the next one fires, held at 1 once all eight have played. This is what makes the
     * bar hit exactly full on every sound instead of merely crawling under wall clock.
     */
    public static float existenceLevelFraction(int elapsedMs) {
        int level = existenceLevel(elapsedMs);
        if (level >= LEVEL_BOUNDARIES_MS.length) return 1f;
        int segmentStart = level == 0 ? 0 : LEVEL_BOUNDARIES_MS[level - 1];
        int segmentEnd = LEVEL_BOUNDARIES_MS[level];
        if (segmentEnd <= segmentStart) return 1f;
        return Mth.clamp((elapsedMs - segmentStart) / (float) (segmentEnd - segmentStart), 0f, 1f);
    }

    /** True once the finale burst is due. */
    public static boolean finaleDue(int elapsedMs) {
        return elapsedMs >= FINALE_MS;
    }

    /**
     * Bar coverage, 0..1: ramps in over the first half second, holds, then ramps back out over the
     * last one. The original computed this off the same timer as the rest of the sequence.
     */
    public static float barFraction(int elapsedMs) {
        float rise = Math.min(1f, elapsedMs / (float) BAR_RAMP_MS);
        float fall = Math.min(1f, Math.max(0f, (TOTAL_MS - elapsedMs) / (float) BAR_RAMP_MS));
        return Math.min(rise, fall);
    }
}
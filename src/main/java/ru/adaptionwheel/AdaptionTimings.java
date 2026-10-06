package ru.adaptionwheel;

/**
 * Durations ported from the original, converted to Minecraft's tick rate.
 *
 * <p>Terraria runs at 60 ticks a second and Minecraft at 20, so a number copied across unchanged is
 * silently three times too long. That is not a rounding detail: anything that is a <i>duration</i>
 * measured in ticks becomes three times longer, and anything gated on "did the timer lapse between
 * two events" stops lapsing at all.
 *
 * <p>This has now bitten three separate mechanics, all of which looked like gameplay bugs rather
 * than translation errors:
 *
 * <ul>
 *   <li>the Lv8 defence capstone granted 120 Minecraft ticks of invulnerability where the original's
 *       {@code immuneTime = 120} is two <i>seconds</i> — six seconds here. A mob in melee range
 *       swings about every 20 ticks, so the window re-armed before it could lapse and the capstone
 *       became permanent immunity to that damage category;
 *   <li>the existence reflection granted 10 ticks where the original grants two Terraria ticks,
 *       which is about 33 ms and therefore nothing;
 *   <li>the adversity countdown ran 480 ticks where the original's 480 is eight seconds, making
 *       every adversity twenty-four seconds long.
 * </ul>
 *
 * <p>The original's own HUD is the proof of the rate: it renders the remaining time as
 * {@code AdversityTimer / 60}, so 60 ticks is one second there.
 *
 * <p>It lives outside {@code client} because the server writes the timer and the client divides by
 * it, and two copies of one duration is how they drift apart again.
 */
public final class AdaptionTimings {

    /**
     * The adversity countdown: the original's 480 Terraria ticks, which is 8 seconds, so 160 here.
     */
    public static final int ADVERSITY_TICKS = 160;

    private AdaptionTimings() {
    }
}
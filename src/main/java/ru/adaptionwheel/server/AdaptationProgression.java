package ru.adaptionwheel.server;

/** Maps the adaptation count to the two capped, passive character-stat bonuses. */
public final class AdaptationProgression {

    /** Full character progression is reached at 400 counted adaptations. */
    public static final int DEVELOPMENT_CAP = 400;

    /** Speed IV is four 20% total movement-speed modifiers: +80% at the cap. */
    public static final double MAX_MOVEMENT_SPEED_BONUS = 0.80;

    /**
     * Player jump strength starts at 0.42. Adding 0.49 reaches 0.91, which gives an approximately
     * five-block vertical jump with vanilla 26.3 gravity and air drag.
     */
    public static final double MAX_JUMP_STRENGTH_BONUS = 0.49;

    private AdaptationProgression() {
    }

    public static double progress(int adaptationCount) {
        int cappedCount = Math.max(0, Math.min(adaptationCount, DEVELOPMENT_CAP));
        return cappedCount / (double) DEVELOPMENT_CAP;
    }

    public static double movementSpeedBonus(int adaptationCount) {
        return MAX_MOVEMENT_SPEED_BONUS * progress(adaptationCount);
    }

    public static double jumpStrengthBonus(int adaptationCount) {
        return MAX_JUMP_STRENGTH_BONUS * progress(adaptationCount);
    }
}

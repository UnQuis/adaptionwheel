package ru.adaptionwheel.server;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AdaptationProgressionTest {

    @Test
    void progressionScalesLinearlyAndStopsAtFourHundredAdaptations() {
        assertEquals(0.0, AdaptationProgression.progress(-10), 1.0e-9);
        assertEquals(0.0, AdaptationProgression.progress(0), 1.0e-9);
        assertEquals(0.5, AdaptationProgression.progress(200), 1.0e-9);
        assertEquals(1.0, AdaptationProgression.progress(400), 1.0e-9);
        assertEquals(1.0, AdaptationProgression.progress(900), 1.0e-9);
    }

    @Test
    void maximumRunSpeedMatchesSpeedFour() {
        assertEquals(0.0, AdaptationProgression.movementSpeedBonus(0), 1.0e-9);
        assertEquals(0.4, AdaptationProgression.movementSpeedBonus(200), 1.0e-9);
        assertEquals(0.8, AdaptationProgression.movementSpeedBonus(400), 1.0e-9);
        assertEquals(0.8, AdaptationProgression.movementSpeedBonus(800), 1.0e-9);
    }

    @Test
    void maximumJumpStrengthProducesAnApproximatelyFiveBlockVanillaJump() {
        assertEquals(0.245, AdaptationProgression.jumpStrengthBonus(200), 1.0e-9);
        assertEquals(0.49, AdaptationProgression.jumpStrengthBonus(400), 1.0e-9);
        assertEquals(0.49, AdaptationProgression.jumpStrengthBonus(800), 1.0e-9);

        // In 26.3, jump strength starts at 0.42, gravity is 0.08, and airborne vertical
        // velocity is damped by 0.98 each tick. This is the unobstructed jump height.
        double velocity = 0.42 + AdaptationProgression.jumpStrengthBonus(400);
        double height = 0.0;
        while (velocity > 0.0) {
            height += velocity;
            velocity = (velocity - 0.08) * 0.98;
        }
        assertEquals(5.0, height, 0.02);
    }
}

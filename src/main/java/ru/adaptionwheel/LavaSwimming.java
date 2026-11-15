package ru.adaptionwheel;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * One answer shared by both sides of the lava swim.
 *
 * <p>The question is deliberately not "is the player in lava" — every lava entity is. It is
 * "has this player adapted to lava", and on the client that has to go through the per-concept
 * switch exactly like every other movement adaptation, or turning the adaptation off would keep
 * the water physics running while the panel says otherwise.
 */
public final class LavaSwimming {

    private LavaSwimming() {
    }

    public static boolean active(LivingEntity entity) {
        return entity instanceof Player player
                && player.isInLava()
                && SurfaceAdaptations.swimsInLava(player);
    }

    /** The horizontal share of water pace, from the server config. */
    public static double horizontalScale() {
        return ru.adaptionwheel.config.AdaptionConfig.LAVA_SWIM_SPEED_SCALE.get();
    }
}

package ru.adaptionwheel.server;

import net.minecraft.world.entity.LivingEntity;
import ru.adaptionwheel.config.AdaptionConfig;

/**
 * Entry point for attacks that bypass the normal damage pipeline by writing health
 * directly ({@code LivingEntity.setHealth}). Currently that is only Draconic
 * Evolution's fully charged Chaos Guardian laser (see {@code GuardianLaserMixin}).
 *
 * Called from a mixin, so this class must stay free of any Draconic Evolution
 * references.
 */
public final class GuardianDirectDamage {

    /**
     * Replaces a direct {@code setHealth} call. Everything that is not an actual
     * health REDUCTION on a wheel-wearing player is passed through untouched.
     */
    public static void apply(LivingEntity target, float newHealth) {
        if (AdaptionConfig.ENABLE_CHAOS_GUARDIAN.get()) {
            AdaptionEvents.handleDirectHealthReduction(target, newHealth);
        } else {
            target.setHealth(newHealth);
        }
    }

    private GuardianDirectDamage() {
    }
}

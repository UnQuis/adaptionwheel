package ru.adaptionwheel.server;

import net.minecraft.world.entity.LivingEntity;
import ru.adaptionwheel.config.AdaptionConfig;

public final class GuardianDirectDamage {

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

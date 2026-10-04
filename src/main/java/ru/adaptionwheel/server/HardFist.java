package ru.adaptionwheel.server;

import net.minecraft.server.level.ServerPlayer;
import ru.adaptionwheel.category.Concepts;
import ru.adaptionwheel.category.FistTiers;
import ru.adaptionwheel.config.AdaptionConfig;
import ru.adaptionwheel.data.PlayerAdaption;

public final class HardFist {

    private HardFist() {
    }

    public static float bonus(ServerPlayer player, PlayerAdaption data) {
        int level = data.level(Concepts.COMBAT_FIST_DAMAGE);
        if (level <= 0 || !AdaptionConfig.FIST_DAMAGE_ENABLED.get()) {
            return 0f;
        }
        float base = (float) (AdaptionConfig.FIST_DAMAGE_BASE.get()
                + AdaptionConfig.FIST_DAMAGE_PER_LEVEL.get() * level);
        return base * (1f + data.getAdaptCount()
                * AdaptionConfig.FIST_DAMAGE_PER_ADAPTATION.get().floatValue());
    }

    public static boolean trains(ServerPlayer player) {
        if (!AdaptionConfig.FIST_DAMAGE_ENABLED.get()) {
            return false;
        }
        return !FistTiers.dealsExtraAttackDamage(player.getMainHandItem());
    }

    public static int analysisTicks() {
        return (int) (AdaptionConfig.FIST_DAMAGE_ANALYSIS_SECONDS.get() * 20);
    }
}
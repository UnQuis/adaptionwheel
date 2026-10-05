package ru.adaptionwheel.server;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import ru.adaptionwheel.category.Concepts;
import ru.adaptionwheel.config.AdaptionConfig;
import ru.adaptionwheel.data.PlayerAdaption;
import ru.adaptionwheel.effect.ModEffects;
import ru.adaptionwheel.effect.WildReleaseEffect;
import ru.adaptionwheel.sound.ModSounds;

public final class Shedding {

    private Shedding() {
    }

    public enum Refusal {
        OK,
        DISABLED,
        NOT_HELD,

        STILL_ANALYSING,
        DURING_ADVERSITY
    }

    public static Refusal canShed(ServerPlayer player, PlayerAdaption data, String concept) {
        if (!AdaptionConfig.SHEDDING_ENABLED.get()) {
            return Refusal.DISABLED;
        }
        if (data.adversityActive) {
            return Refusal.DURING_ADVERSITY;
        }
        boolean held = data.isAdapted(concept) || data.level(concept) > 0;
        if (!held) {

            return isAnalysing(data, concept) ? Refusal.STILL_ANALYSING : Refusal.NOT_HELD;
        }
        return Refusal.OK;
    }

    public static Refusal shed(ServerPlayer player, PlayerAdaption data, String concept) {
        Refusal refusal = canShed(player, data, concept);
        if (refusal != Refusal.OK) {
            return refusal;
        }
        int level = data.level(concept);
        int amplifier = releaseAmplifier(concept, level);
        int ticks = releaseTicks(level);

        if (!AdaptionEvents.debugUngrant(player, concept)) {
            return Refusal.NOT_HELD;
        }

        data.recentlyShed.add(concept);
        data.shedCount++;
        if (level > 0) {

            data.levels.put(concept, 0);
        }

        applyRelease(player, amplifier, ticks);
        player.displayClientMessage(Component.translatable("adaptionwheel.msg.shed",
                        Concepts.chatName(concept))
                .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD), false);
        player.displayClientMessage(Component.translatable("adaptionwheel.msg.shed_release",
                        percent(amplifier), Math.max(1, ticks / 20))
                .withStyle(ChatFormatting.GRAY), false);
        player.level().playSound(null, player.blockPosition(), ModSounds.REF.get(),
                SoundSource.PLAYERS, 0.8f, 0.8f);

        return Refusal.OK;
    }

    public static int releaseAmplifier(String concept, int level) {
        if (level <= 0) {
            return 1;
        }
        return Math.min(WildReleaseEffect.MAX_AMPLIFIER, (level + 1) / 2);
    }

    public static int releaseTicks(int level) {
        return Math.max(1, AdaptionConfig.SHEDDING_RELEASE_BASE_TICKS.get()
                + Math.max(0, level) * AdaptionConfig.SHEDDING_RELEASE_TICKS_PER_LEVEL.get());
    }

    public static double reattachFactor(PlayerAdaption data, String concept) {
        if (!AdaptionConfig.SHEDDING_ENABLED.get() || !data.recentlyShed.contains(concept)) {
            return 1.0D;
        }
        return AdaptionConfig.SHEDDING_REATTACH_TIMER_FACTOR.get();
    }

    private static void applyRelease(ServerPlayer player, int amplifier, int ticks) {

        if (player.getEffect(ModEffects.WILD_RELEASE) != null) {
            player.removeEffect(ModEffects.WILD_RELEASE);
        }
        player.addEffect(new MobEffectInstance(ModEffects.WILD_RELEASE, ticks, amplifier,
                true, true, true));
    }

    private static String percent(int amplifier) {
        return "+" + ((amplifier + 1) * 20) + "%";
    }

    private static boolean isAnalysing(PlayerAdaption data, String concept) {
        for (ru.adaptionwheel.data.AdaptionTask task : data.tasks) {
            if (task.concept.equals(concept)) {
                return true;
            }
        }
        return false;
    }
}

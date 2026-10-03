package ru.adaptionwheel.server;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import ru.adaptionwheel.config.AdaptionConfig;
import ru.adaptionwheel.effect.ModEffects;
import ru.adaptionwheel.effect.ResonanceEffect;

public final class Resonance {

    private Resonance() {
    }

    public static int neighbours(ServerPlayer player, double range) {
        int count = 0;

        for (ServerPlayer other : serverLevel(player).getPlayers(p -> true)) {
            if (other == player || !other.isAlive() || other.isSpectator()) {
                continue;
            }

            if (other.level() != player.level()) {
                continue;
            }
            if (!ru.adaptionwheel.SurfaceAdaptations.wearingWheel(other)) {
                continue;
            }
            if (other.distanceToSqr(player) <= range * range) {
                count++;
            }
        }
        return count;
    }

    public static int amplifierFor(int neighbours) {
        if (neighbours <= 0) {
            return 0;
        }
        return Math.min(ResonanceEffect.MAX_AMPLIFIER, (neighbours + 1) / 2);
    }

    public static void tick(ServerPlayer player, RitualAuras.Auras auras) {
        int amplifier = 0;
        if (AdaptionConfig.RESONANCE_ENABLED.get()) {
            amplifier = amplifierFor(neighbours(player, AdaptionConfig.RESONANCE_BLOCKS.get()));
            if (amplifier > 0 && auras.altar() && AdaptionConfig.ALTAR_ENABLED.get()) {

                amplifier = Math.min(ResonanceEffect.MAX_AMPLIFIER, amplifier + 1);
            }
        }
        if (amplifier <= 0) {
            if (player.getEffect(ModEffects.RESONANCE) != null) {
                player.removeEffect(ModEffects.RESONANCE);
            }
            return;
        }
        boolean rungRose = player.getEffect(ModEffects.RESONANCE) == null
                || player.getEffect(ModEffects.RESONANCE).getAmplifier() < amplifier;
        MobEffectInstance current = player.getEffect(ModEffects.RESONANCE);
        if (current != null && current.getAmplifier() >= amplifier
                && current.getDuration() > 60) {
            return;
        }
        player.addEffect(new MobEffectInstance(ModEffects.RESONANCE, 100, amplifier,
                true, true, true));
        if (rungRose && AdaptionConfig.RESONANCE_PARTICLES.get()) {

            serverLevel(player).sendParticles(net.minecraft.core.particles.ParticleTypes.ENCHANT,
                    player.getX(), player.getY(0.1), player.getZ(), 12, 0.4, 0.2, 0.4, 0.01);
        }
    }

    private static net.minecraft.server.level.ServerLevel serverLevel(ServerPlayer player) {
        return (net.minecraft.server.level.ServerLevel) player.level();
    }
}

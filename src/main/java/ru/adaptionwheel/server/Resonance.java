package ru.adaptionwheel.server;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import ru.adaptionwheel.config.AdaptionConfig;
import ru.adaptionwheel.effect.ModEffects;
import ru.adaptionwheel.effect.ResonanceEffect;

/**
 * Resonance: adapted players standing together strengthen each other.
 *
 * <p>Ten adaptations, four synergies and a fist, and every single one of them is something you
 * gained alone by being hurt enough times. There was no reason in the mod to bring anyone with
 * you, which is a strange gap for a wheel that adapts to everything. This is that gap, filled with
 * the cheapest thing that works: proximity and a stacking buff.</p>
 *
 * <p>The rung counts <em>other</em> adapted players within range, capped at the effect's declared
 * maximum, so the incentive saturates instead of scaling without bound — a group of twelve in a
 * dungeon should not be twelve times stronger than a group of two.</p>
 *
 * <p>Refreshed on the 1 Hz tick rather than every tick, and applied as an ordinary effect instance
 * so the game cleans up the attribute modifiers. Leaving the range has to actually remove it, so
 * the buff is re-evaluated (and removed) on the same cadence rather than left to time out —
 * {@code setDuration} on a permanent-length instance would never expire it.</p>
 */
public final class Resonance {

    private Resonance() {
    }

    /**
     * How many adapted players are within resonance range, excluding the one asking.
     *
     * <p>Public and static so it can be tested without a world, and because the browser wants to
     * show the number next to the buff.</p>
     */
    public static int neighbours(ServerPlayer player, double range) {
        int count = 0;
        // getPlayers lives on ServerLevel, not Level, so the cast is unavoidable; serverLevel()
        // keeps it in one place instead of at each use.
        for (ServerPlayer other : serverLevel(player).getPlayers(p -> true)) {
            if (other == player || !other.isAlive() || other.isSpectator()) {
                continue;
            }
            // Different level first: a cross-dimension distance is meaningless and the
            // squared-distance compare below would happily accept it.
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

    /**
     * The rung for a given number of neighbours.
     *
     * <p>Zero neighbours is zero: no buff at all, rather than the bottom rung. A permanent faint
     * "resonance" icon on a lone player would be a lie about a mechanic that does nothing.</p>
     */
    public static int amplifierFor(int neighbours) {
        if (neighbours <= 0) {
            return 0;
        }
        return Math.min(ResonanceEffect.MAX_AMPLIFIER, (neighbours + 1) / 2);
    }

    /**
     * Re-applies or clears the buff.
     *
     * <p>Called from the 1 Hz block of the wearer tick. A weaker instance is left alone rather than
     * replaced, because vanilla already keeps the better of two same-effect instances and
     * re-adding a longer one every second is what would actually cause the flicker.</p>
     */
    public static void tick(ServerPlayer player, RitualAuras.Auras auras) {
        int amplifier = 0;
        if (AdaptionConfig.RESONANCE_ENABLED.get()) {
            amplifier = amplifierFor(neighbours(player, AdaptionConfig.RESONANCE_BLOCKS.get()));
            if (amplifier > 0 && auras.altar() && AdaptionConfig.ALTAR_ENABLED.get()) {
                // One extra rung from the altar, and only if there is already a rung to raise:
                // an altar in an empty world should not manufacture the buff out of nothing.
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
            // Only on the way up, so a stationary group is not emitting particles forever.
            serverLevel(player).sendParticles(net.minecraft.core.particles.ParticleTypes.ENCHANT,
                    player.getX(), player.getY(0.1), player.getZ(), 12, 0.4, 0.2, 0.4, 0.01);
        }
    }

    private static net.minecraft.server.level.ServerLevel serverLevel(ServerPlayer player) {
        return (net.minecraft.server.level.ServerLevel) player.level();
    }
}

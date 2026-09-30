package ru.adaptionwheel.server;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import ru.adaptionwheel.block.AdaptationBrazierBlock;
import ru.adaptionwheel.block.ResonanceAltarBlock;
import ru.adaptionwheel.block.WheelTotemBlock;
import ru.adaptionwheel.config.AdaptionConfig;
import ru.adaptionwheel.data.AdaptionTask;
import ru.adaptionwheel.data.PlayerAdaption;


/**
 * The four ritual blocks' effects, run as one pass over the players who can see them.
 *
 * <p>Deliberately not block tickers and not {@code randomTick}. A ticker per block would run
 * forever in a world full of them, and randomTick fires on roughly three blocks in a hundred per
 * tick, which is far too sparse for an aura a player is standing in and expecting to work. Walking
 * the handful of wheel-wearing players instead inverts the cost: it is proportional to the people
 * who care, and a chest full of braziers in a storage room costs nothing at all.</p>
 *
 * <p>The three auras are gathered in a single cube scan and share the result, because the Resonance
 * altar's effect is read by a different class on the same tick and scanning the volume twice for
 * the same block is exactly the sort of redundancy that later makes one of the two scans
 * unreachable.</p>
 */
public final class RitualAuras {

    private RitualAuras() {
    }

    /** What is within reach of one player. */
    public record Auras(int braziers, boolean totem, boolean altar) {
        public boolean any() {
            return braziers > 0 || totem || altar;
        }
    }

    /**
     * Scans around a player for the three aura blocks.
     *
     * <p>Two things keep the volume from costing anything: air is rejected on the first check and
     * the scan returns as soon as all three auras have been seen, so a player standing in the
     * middle of a fully furnished room pays for a few blocks rather than for the cube. It is still
     * a cube — a 4913-block walk once a second for one player — which is the price of having no
     * block entities and no tickers, and is the reason this runs for wheel-wearers only.</p>
     */
    public static Auras scan(ServerPlayer player) {
        int radius = (int) Math.ceil(Math.max(AdaptationBrazierBlock.RANGE,
                Math.max(WheelTotemBlock.RANGE, ResonanceAltarBlock.RANGE)));
        if (radius <= 0) {
            return new Auras(0, false, false);
        }
        BlockPos origin = player.blockPosition();
        var level = player.level();
        int braziers = 0;
        boolean totem = false;
        boolean altar = false;
        double totemSq = WheelTotemBlock.RANGE * WheelTotemBlock.RANGE;
        double altarSq = ResonanceAltarBlock.RANGE * ResonanceAltarBlock.RANGE;
        double brazierSq = AdaptationBrazierBlock.RANGE * AdaptationBrazierBlock.RANGE;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dy = -radius; dy <= radius; dy++) {
                    BlockState state = level.getBlockState(origin.offset(dx, dy, dz));
                    if (state.isAir()) {
                        continue;
                    }
                    double distSq = dx * dx + dy * dy + dz * dz;
                    if (AdaptationBrazierBlock.isBrazier(state) && distSq <= brazierSq) {
                        braziers++;
                    } else if (WheelTotemBlock.isTotem(state) && distSq <= totemSq) {
                        totem = true;
                    } else if (ResonanceAltarBlock.isAltar(state) && distSq <= altarSq) {
                        altar = true;
                    }
                    if (braziers > 0 && totem && altar) {
                        return new Auras(braziers, true, true);
                    }
                }
            }
        }
        return new Auras(braziers, totem, altar);
    }

    /**
     * Applies the healing and the analysis acceleration.
     *
     * <p>Healing is a flat amount per second per brazier rather than a timed effect, which means no
     * per-player timer to keep, no state to save and nothing to clean up on logout — the cost of
     * that choice is that it is not affected by regeneration, which for a small constant aura is
     * the right trade.</p>
     */
    public static void apply(ServerPlayer player, PlayerAdaption data, Auras auras) {
        if (auras.braziers() > 0 && AdaptionConfig.BRAZIER_HEAL_ENABLED.get()) {
            double amount = AdaptionConfig.BRAZIER_HEAL_PER_SECOND.get() * auras.braziers();
            // Capped at the missing health so a full-health player in a field of braziers is not
            // wasting the effect, and so the number on the tooltip stays honest.
            player.heal((float) Math.min(amount, player.getMaxHealth() - player.getHealth()));
        }
        if (auras.totem() && AdaptionConfig.TOTEM_ENABLED.get()
                && !data.tasks.isEmpty() && !data.adversityActive) {
            int ticks = AdaptionConfig.TOTEM_ACCELERATION_TICKS.get();
            if (ticks > 0) {
                for (AdaptionTask task : data.tasks) {
                    task.timer = Math.max(1, task.timer - ticks);
                }
                // The task list is about to change on screen, so the 1 Hz sync is not soon enough.
                sync(player, data);
            }
        }
    }

    private static void sync(ServerPlayer player, PlayerAdaption data) {
        AdaptionEvents.syncAdaption(player, data,
                ru.adaptionwheel.SurfaceAdaptations.wearingWheel(player));
    }
}

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

public final class RitualAuras {

    private RitualAuras() {
    }

    public record Auras(int braziers, boolean totem, boolean altar) {
        public boolean any() {
            return braziers > 0 || totem || altar;
        }
    }

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

    public static void apply(ServerPlayer player, PlayerAdaption data, Auras auras) {
        if (auras.braziers() > 0 && AdaptionConfig.BRAZIER_HEAL_ENABLED.get()) {
            double amount = AdaptionConfig.BRAZIER_HEAL_PER_SECOND.get() * auras.braziers();

            player.heal((float) Math.min(amount, player.getMaxHealth() - player.getHealth()));
        }
        if (auras.totem() && AdaptionConfig.TOTEM_ENABLED.get()
                && !data.tasks.isEmpty() && !data.adversityActive) {
            int ticks = AdaptionConfig.TOTEM_ACCELERATION_TICKS.get();
            if (ticks > 0) {
                for (AdaptionTask task : data.tasks) {
                    task.timer = Math.max(1, task.timer - ticks);
                }

                sync(player, data);
            }
        }
    }

    private static void sync(ServerPlayer player, PlayerAdaption data) {
        AdaptionEvents.syncAdaption(player, data,
                ru.adaptionwheel.SurfaceAdaptations.wearingWheel(player));
    }
}

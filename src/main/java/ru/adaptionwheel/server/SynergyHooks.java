package ru.adaptionwheel.server;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import ru.adaptionwheel.AdaptionWheel;

@EventBusSubscriber(modid = AdaptionWheel.MODID)
public final class SynergyHooks {

    private SynergyHooks() {
    }

    @SubscribeEvent
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        if (!(event.getEntity() instanceof Mob mob) || mob.level().isClientSide()) {
            return;
        }
        LivingEntity target = event.getNewAboutToBeSetTarget();
        if (!(target instanceof ServerPlayer player)) {
            return;
        }
        if (SynergyEffects.refuseTarget(player, target)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onBlockDrops(BlockDropsEvent event) {
        if (!(event.getBreaker() instanceof ServerPlayer player) || player.level().isClientSide()) {
            return;
        }

        if (ru.adaptionwheel.SurfaceAdaptations.fistTier(player) < 0) {
            return;
        }
        SynergyEffects.smeltDrops(player, event.getDrops());
    }
}

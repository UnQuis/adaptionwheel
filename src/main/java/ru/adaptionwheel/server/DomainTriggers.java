package ru.adaptionwheel.server;

import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import ru.adaptionwheel.AdaptionWheel;
import ru.adaptionwheel.category.Concepts;
import ru.adaptionwheel.config.AdaptionConfig;

@EventBusSubscriber(modid = AdaptionWheel.MODID)
public final class DomainTriggers {

    private DomainTriggers() {
    }

    @SubscribeEvent
    public static void onBlockBreak(BreakBlockEvent event) {
        if (!(event.getPlayer() instanceof net.minecraft.server.level.ServerPlayer player)
                || player.level().isClientSide()) {
            return;
        }
        if (!AdaptionConfig.ENABLE_MINING.get() || !AdaptionEvents.isWearingWheel(player)) {
            return;
        }

        if (player.isCreative() || player.isSpectator()) {
            return;
        }
        AdaptionEvents.startOrAccelerate(player, AdaptionEvents.dataOf(player),
                Concepts.MINE_LABOR,
                (int) (AdaptionConfig.DEFENSE_ANALYSIS_SECONDS.get() * 20), true);

        if (AdaptionConfig.FIST_ENABLED.get()
                && ru.adaptionwheel.category.FistTiers.usableWith(player.getMainHandItem(),
                        event.getState())) {
            ru.adaptionwheel.server.FistMastery.onHandBreak(player, AdaptionEvents.dataOf(player),
                    event.getState());
        }

        if (!player.isCreative() && !player.isSpectator()) {
            ru.adaptionwheel.server.SynergyEffects.onBlockBroken(player, event.getState());
        }
    }

    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        if (!(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)
                || player.level().isClientSide()) {
            return;
        }
        if (!AdaptionConfig.ENABLE_COMBAT.get() || !AdaptionEvents.isWearingWheel(player)) {
            return;
        }
        AdaptionEvents.startOrAccelerate(player, AdaptionEvents.dataOf(player),
                Concepts.COMBAT_COOLDOWN,
                (int) (AdaptionConfig.OFFENSE_ANALYSIS_SECONDS.get() * 20), true);
    }

    @SubscribeEvent
    public static void onShieldBlock(LivingShieldBlockEvent event) {
        if (!(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)
                || player.level().isClientSide()) {
            return;
        }
        if (!AdaptionConfig.ENABLE_COMBAT.get() || !AdaptionEvents.isWearingWheel(player)) {
            return;
        }

        if (!(event.getDamageSource().getDirectEntity() instanceof LivingEntity attacker)
                || !attacker.getWeaponItem().is(ItemTags.AXES)) {
            return;
        }
        AdaptionEvents.startTask(player, AdaptionEvents.dataOf(player), Concepts.COMBAT_SHIELD_LOCK,
                (int) (AdaptionConfig.ENV_ANALYSIS_SECONDS.get() * 20));
    }
}

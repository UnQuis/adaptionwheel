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

/**
 * Adaptation to Discomfort — mining/combat/perception-domain triggers.
 *
 * <ul>
 *   <li>{@code Mine_Labor}: breaking blocks trains a permanent mining-speed bonus.</li>
 *   <li>{@code Combat_Cooldown}: landing melee hits trains away the 1.9 attack-cooldown penalty.</li>
 *   <li>{@code Combat_ShieldLock}: blocking an axe wielder trains shield-disable immunity.</li>
 * </ul>
 *
 * Perception ({@code Percep_SteadyGaze}) is triggered from the damage pipeline in
 * {@link AdaptionEvents} since it already owns every hit on the wearer.
 */
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
        // Creative and spectator players generate no adaptation progress from mining.
        if (player.isCreative() || player.isSpectator()) {
            return;
        }
        AdaptionEvents.startOrAccelerate(player, AdaptionEvents.dataOf(player),
                Concepts.MINE_LABOR,
                (int) (AdaptionConfig.DEFENSE_ANALYSIS_SECONDS.get() * 20), true);

        // Fist Mastery: a hand-held break is the whole unlock ritual and the tier's training
        // signal, so it is handled separately from the Labor analysis. Holding a non-tool item (a
        // block, food) still counts; only tools and weapons take the fist out of play.
        if (AdaptionConfig.FIST_ENABLED.get()
                && ru.adaptionwheel.category.FistTiers.usableWith(player.getMainHandItem(),
                        event.getState())) {
            ru.adaptionwheel.server.FistMastery.onHandBreak(player, AdaptionEvents.dataOf(player),
                    event.getState());
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
        // Only an axe-wielding attacker teaches shield-lock adaptation.
        if (!(event.getDamageSource().getDirectEntity() instanceof LivingEntity attacker)
                || !attacker.getWeaponItem().is(ItemTags.AXES)) {
            return;
        }
        AdaptionEvents.startTask(player, AdaptionEvents.dataOf(player), Concepts.COMBAT_SHIELD_LOCK,
                (int) (AdaptionConfig.ENV_ANALYSIS_SECONDS.get() * 20));
    }
}

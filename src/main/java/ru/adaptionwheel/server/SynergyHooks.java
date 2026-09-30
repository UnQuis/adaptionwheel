package ru.adaptionwheel.server;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import ru.adaptionwheel.AdaptionWheel;

/**
 * The synergy hooks that are not damage events.
 *
 * <p>Kept apart from {@link SynergyEffects} so that class stays the "what does it do" half and this
 * is the "which event reaches it" half — which is the distinction that matters when one of them
 * turns out to be wired to the wrong place and the effects are all silently inert.</p>
 */
@EventBusSubscriber(modid = AdaptionWheel.MODID)
public final class SynergyHooks {

    private SynergyHooks() {
    }

    /**
     * Unseen: a mob cannot decide you are the thing it wants.
     *
     * <p>Cancelling is the whole implementation. A mob that already had you keeps you, so this
     * denies new lock-ons rather than clearing the current one — which is what makes it a reward
     * for holding the pair instead of a cloak.</p>
     */
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

    /**
     * Goliath: the fist smelts what it breaks.
     *
     * <p>On {@code BlockDropsEvent}, the same event Fist Luck uses, so the two compose: the ore
     * becomes an ingot first and the tier's drop multiplier then applies to the ingots. Done in
     * either order the result would differ, and doing it here rather than by replacing the block
     * means fortune and silk touch are already baked in.</p>
     */
    @SubscribeEvent
    public static void onBlockDrops(BlockDropsEvent event) {
        if (!(event.getBreaker() instanceof ServerPlayer player) || player.level().isClientSide()) {
            return;
        }
        // Only the fist smelts, not a bare hand: without the fist there is nothing to attribute
        // the heat to, and every player would be cooking ore with their hands.
        if (ru.adaptionwheel.SurfaceAdaptations.fistTier(player) < 0) {
            return;
        }
        SynergyEffects.smeltDrops(player, event.getDrops());
    }
}

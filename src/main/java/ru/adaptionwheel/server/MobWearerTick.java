package ru.adaptionwheel.server;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import ru.adaptionwheel.AdaptionWheel;
import ru.adaptionwheel.data.PlayerAdaption;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Drives {@link AdaptionEvents#tickWearer} for mobs that wear a wheel in a Curios {@code wheel} slot.
 *
 * <p>Players are driven from {@code PlayerTickEvent.Post} in AdaptionEvents; this class deliberately
 * skips them so no player can be ticked twice. Everything a mob needs is already in the shared
 * wearer tick: equip/unequip/swap merging, active analysis timers, environment effects and
 * regeneration. The only extra bookkeeping here is remembering which mobs were seen wearing so
 * their UUID-keyed caches can be dropped when they die, despawn or take the wheel off.
 */
@EventBusSubscriber(modid = AdaptionWheel.MODID)
public final class MobWearerTick {

    private MobWearerTick() {
    }

    private static final Set<UUID> SEEN_WEARERS = new HashSet<>();

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity entity) || entity instanceof Player) {
            return;
        }
        if (entity.level().isClientSide) {
            return;
        }

        UUID id = entity.getUUID();
        boolean wearing = Wearers.isWearer(entity);
        if (!wearing) {
            if (SEEN_WEARERS.remove(id)) {
                AdaptionEvents.clearWearerCaches(id);
            }
            return;
        }

        PlayerAdaption data = Wearers.data(entity);
        if (data == null) {
            return;
        }
        if (!entity.isAlive() || entity.isRemoved() || entity.isDeadOrDying()) {
            // One last pass runs the death branch in tickWearer, which writes the mob's analyses
            // back to the wheel it is about to drop, before its caches are dropped.
            if (SEEN_WEARERS.remove(id)) {
                AdaptionEvents.tickWearer(entity, data, true);
                AdaptionEvents.clearWearerCaches(id);
            }
            return;
        }

        SEEN_WEARERS.add(id);
        AdaptionEvents.tickWearer(entity, data, true);
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof LivingEntity entity) || entity instanceof Player) {
            return;
        }
        if (SEEN_WEARERS.remove(entity.getUUID())) {
            AdaptionEvents.clearWearerCaches(entity.getUUID());
        }
    }
}

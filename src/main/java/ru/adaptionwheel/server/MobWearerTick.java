package ru.adaptionwheel.server;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
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
    /** Scanning every LivingEntity every tick is needless: only already-seen wearers need a per-tick check. */
    private static final int NEW_WEARER_SCAN_INTERVAL_TICKS = 20;
    private static long lastScanTick = Long.MIN_VALUE;
    private static boolean scanDueThisTick;

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity entity) || entity instanceof Player) {
            return;
        }
        if (entity.level().isClientSide) {
            return;
        }

        UUID id = entity.getUUID();
        long now = entity.level().getGameTime();
        if (now != lastScanTick) {
            lastScanTick = now;
            scanDueThisTick = true;
        }
        if (!SEEN_WEARERS.contains(id) && !scanDueThisTick) {
            return;
        }
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
    public static void onServerTick(ServerTickEvent.Post event) {
        scanDueThisTick = false;
    }

    /** EntityTickEvent.Post never runs for an entity in an unloaded chunk; this hook does. */
    @SubscribeEvent
    public static void onEntityLeaveLevel(EntityLeaveLevelEvent event) {
        if (!(event.getEntity() instanceof LivingEntity entity) || entity instanceof Player) {
            return;
        }
        if (event.getLevel().isClientSide()) {
            return;
        }
        UUID id = entity.getUUID();
        if (SEEN_WEARERS.remove(id)) {
            AdaptionEvents.clearWearerCaches(id);
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof LivingEntity entity) || entity instanceof Player) {
            return;
        }
        // Curios drops the actual stack object, so write the attachment's latest analyses back onto
        // it BEFORE the drop event runs. Without this, everything since the last 1 Hz save dies with
        // the mob, which looks exactly like losing a trained wheel.
        var stack = ru.adaptionwheel.item.MahoragaWheelItem.equippedWheel(entity);
        if (stack != null && !entity.level().isClientSide) {
            AdaptionEvents.saveToStack(stack, Wearers.data(entity));
        }
        if (SEEN_WEARERS.remove(entity.getUUID())) {
            AdaptionEvents.clearWearerCaches(entity.getUUID());
        }
    }
}

package ru.adaptionwheel.server;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import ru.adaptionwheel.data.AttachmentTypes;
import ru.adaptionwheel.config.AdaptionConfig;
import ru.adaptionwheel.data.PlayerAdaption;
import ru.adaptionwheel.entity.CursedSlashProjectile;
import ru.adaptionwheel.item.SwordOfExterminationItem;
import ru.adaptionwheel.sound.ModSounds;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber(modid = ru.adaptionwheel.AdaptionWheel.MODID)
public final class SwordOfExterminationHandler {

    private static final int SLASH_COOLDOWN_TICKS = 10;

    private static final int RIFT_COOLDOWN_TICKS = 60;
    private static final float BASE_SLASH_DAMAGE = 20f;

    private static final Map<UUID, Long> LAST_SLASH_TIME = new HashMap<>();
    private static final Map<UUID, Long> LAST_RIFT_TIME = new HashMap<>();

    private SwordOfExterminationHandler() {
    }

    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        tryFireSlashes(event.getEntity());
    }

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        tryFireSlashes(event.getEntity());
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST_SLASH_TIME.remove(event.getEntity().getUUID());
        LAST_RIFT_TIME.remove(event.getEntity().getUUID());
    }

    public static void tryFireSlashes(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        ItemStack held = player.getMainHandItem();
        if (!(held.getItem() instanceof SwordOfExterminationItem) || !SwordOfExterminationItem.isCursed(held)) {
            return;
        }

        long now = player.level().getGameTime();
        Long last = LAST_SLASH_TIME.get(player.getUUID());
        if (last != null && now - last < SLASH_COOLDOWN_TICKS) return;
        LAST_SLASH_TIME.put(player.getUUID(), now);

        PlayerAdaption data = serverPlayer.getData(AttachmentTypes.ADAPTION);
        int adaptCount = data.getAdaptCount();
        float damage = BASE_SLASH_DAMAGE * (1f + adaptCount * 0.005f);
        float speed = Math.min(1.2f + adaptCount * 0.04f, 4f);
        int count = 1 + Math.min(adaptCount / 100, 9);
        int maxHits = 2 + adaptCount / 30;

        Vec3 direction = serverPlayer.getLookAngle().normalize().scale(speed);
        var random = serverPlayer.getRandom();
        for (int i = 0; i < count; i++) {
            float roll = (float) Math.toRadians(random.nextFloat() * 120f - 60f);
            serverPlayer.level().addFreshEntity(
                    new CursedSlashProjectile(serverPlayer.level(), serverPlayer, direction, damage, maxHits, roll));
        }
        serverPlayer.level().playSound(null, serverPlayer.blockPosition(),
                ModSounds.SWING.get(), SoundSource.PLAYERS, 0.8f, 1.4f);

        tryFireSpatialRifts(serverPlayer);
    }

    private static void tryFireSpatialRifts(ServerPlayer player) {
        if (!AdaptionConfig.DIMENSION_DESTROY_ENABLED.get()) {
            return;
        }
        PlayerAdaption data = player.getData(AttachmentTypes.ADAPTION);
        if (!data.active(ru.adaptionwheel.category.Concepts.DIMENSION_DESTROY)) {
            return;
        }
        long now = player.level().getGameTime();
        Long last = LAST_RIFT_TIME.get(player.getUUID());
        if (last != null && now - last < RIFT_COOLDOWN_TICKS) {
            return;
        }
        LAST_RIFT_TIME.put(player.getUUID(), now);

        Vec3 base = player.getLookAngle().normalize().scale(1.2);
        for (int i = -1; i <= 1; i++) {
            double angle = Math.toRadians(i * 5);
            Vec3 spread = rotateY(base, angle);
            player.level().addFreshEntity(new ru.adaptionwheel.entity.SpatialRiftProjectile(
                    player.level(), player, spread, (float) (-angle)));
        }
        player.level().playSound(null, player.blockPosition(),
                ModSounds.SWING.get(), SoundSource.PLAYERS, 1.2f, 0.6f);

        player.sendOverlayMessage(net.minecraft.network.chat.Component.literal("DESTROY THE DIMENSION")
                .withStyle(net.minecraft.ChatFormatting.BLACK, net.minecraft.ChatFormatting.BOLD));
    }

    private static Vec3 rotateY(Vec3 vec, double angle) {
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        return new Vec3(vec.x * cos + vec.z * sin, vec.y, -vec.x * sin + vec.z * cos);
    }

    @SubscribeEvent
    public static void onDamage(LivingDamageEvent.Pre event) {
        Entity attacker = event.getSource().getEntity();
        if (!(attacker instanceof ServerPlayer player)) return;
        LivingEntity target = event.getEntity();
        ItemStack held = player.getMainHandItem();
        if (!(held.getItem() instanceof SwordOfExterminationItem) || SwordOfExterminationItem.isCursed(held)) {
            return;
        }

        event.setNewDamage(event.getNewDamage() + target.getHealth() * 0.01f);
        if (player.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.END_ROD,
                    target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(),
                    5, 0.3, 0.4, 0.3, 0.05);
        }
    }
}

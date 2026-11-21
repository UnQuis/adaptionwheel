package ru.adaptionwheel.server;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import ru.adaptionwheel.data.AttachmentTypes;
import ru.adaptionwheel.data.PlayerAdaption;
import ru.adaptionwheel.item.MahoragaWheelItem;

/**
 * Small facade over "who is wearing a wheel and what does that mean".
 *
 * <p>Kept free of {@link AdaptionEvents} on purpose: that class is the primary consumer of this
 * facade, and a back-reference would make the ownership of the wheel state ambiguous again. The
 * wheel lookup reads the dedicated Curios slot directly, so an equip/unequip in the same tick is
 * visible immediately.
 */
public final class Wearers {

    private Wearers() {
    }

    public static boolean isWearer(LivingEntity e) {
        // Read the slot directly: Curios' findFirstCurio/isEquipped cache their answer for the
        // current game tick, which makes an equip immediately followed by an unequip look like the
        // wheel is still worn. The dedicated slot is the only legal place for this item anyway.
        return MahoragaWheelItem.equippedWheel(e) != null;
    }

    @Nullable
    public static ItemStack wheelStack(LivingEntity e) {
        return MahoragaWheelItem.equippedWheel(e);
    }

    public static PlayerAdaption data(LivingEntity e) {
        return e.getData(AttachmentTypes.ADAPTION.get());
    }

    @Nullable
    public static ServerPlayer asPlayer(LivingEntity e) {
        return e instanceof ServerPlayer player ? player : null;
    }

    public static DamageSource damageSource(LivingEntity e) {
        if (e instanceof ServerPlayer player) {
            return e.damageSources().playerAttack(player);
        }
        if (e instanceof Mob mob) {
            return e.damageSources().mobAttack(mob);
        }
        // mobAttack takes any LivingEntity on 1.21.1; generic() would lose the attacker.
        return e.damageSources().mobAttack(e);
    }

    public static void message(LivingEntity e, Component c) {
        ServerPlayer player = asPlayer(e);
        if (player != null) {
            player.sendSystemMessage(c);
        }
    }
}

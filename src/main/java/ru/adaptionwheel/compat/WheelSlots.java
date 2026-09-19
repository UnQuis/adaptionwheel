package ru.adaptionwheel.compat;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import ru.adaptionwheel.item.ModItems;

import java.util.Optional;

/**
 * Where the Mahoraga Wheel can be "worn". With Curios installed the dedicated {@code wheel}
 * curio slot is authoritative. Without Curios (the default on 26.3 until Curios ships a build)
 * the wheel counts as worn when it is in the off-hand or anywhere in the main inventory.
 */
public final class WheelSlots {

    private WheelSlots() {
    }

    /** Returns the worn wheel stack, if any. Works on both logical sides. */
    public static Optional<ItemStack> findWorn(Player player) {
        Item wheel = ModItems.MAHORAGA_WHEEL.get();
        if (CuriosCompat.isLoaded()) {
            return CuriosCompat.findFirst(player, wheel);
        }
        ItemStack offhand = player.getOffhandItem();
        if (offhand.is(wheel)) {
            return Optional.of(offhand);
        }
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.is(wheel)) {
                return Optional.of(stack);
            }
        }
        return Optional.empty();
    }

    public static boolean isWorn(Player player) {
        if (CuriosCompat.isLoaded()) {
            return CuriosCompat.isEquipped(player, ModItems.MAHORAGA_WHEEL.get());
        }
        return findWorn(player).isPresent();
    }
}

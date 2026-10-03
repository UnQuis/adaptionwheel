package ru.adaptionwheel.compat;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import ru.adaptionwheel.item.ModItems;

import java.util.Optional;

public final class WheelSlots {

    private WheelSlots() {
    }

    public static Optional<ItemStack> findWorn(Player player) {
        Item wheel = ModItems.MAHORAGA_WHEEL.get();
        if (CuriosCompat.isUsable()) {

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

        return findWorn(player).isPresent();
    }
}

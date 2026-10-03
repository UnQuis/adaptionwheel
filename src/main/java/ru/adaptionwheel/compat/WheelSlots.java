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
        if (CuriosCompat.isUsable()) {
            //Curios owns the answer while it is loaded and its API bound: the wheel is worn exactly
            //while it sits in its slot. Falling through to the inventory here would make the slot
            //meaningless — a wheel taken back out of it would keep working out of the player's pocket,
            //which is the same defect as a wheel that cannot be taken out at all, only quieter.
            return CuriosCompat.findFirst(player, wheel);
        }
        //No Curios, or a Curios whose API could not be bound: the off-hand and the inventory both
        //count, so a drifted Curios API can never make the wheel silently dead
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
        //One definition of "worn", so this cannot disagree with findWorn about which of the two rules
        //applies — and it costs one Curios lookup per call rather than two.
        return findWorn(player).isPresent();
    }
}

package ru.adaptionwheel.client;

import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * The client's mirror of the adapted player's cache.
 *
 * <p>It exists because the cache is player data, not menu data: a chest or crafting table screen has
 * no slot that knows about it, so the overlay cannot ask the menu what is in it. The server pushes
 * this on every container open and after every click; nothing here is authoritative.
 */
public final class ClientCache {

    private static List<ItemStack> stacks = List.of();

    private ClientCache() {
    }

    public static void set(List<ItemStack> incoming) {
        List<ItemStack> copy = new ArrayList<>(incoming.size());
        for (ItemStack stack : incoming) {
            copy.add(stack == null ? ItemStack.EMPTY : stack.copy());
        }
        stacks = copy;
    }

    public static ItemStack get(int index) {
        if (index < 0 || index >= stacks.size()) {
            return ItemStack.EMPTY;
        }
        return stacks.get(index);
    }

    public static boolean isEmpty() {
        for (ItemStack stack : stacks) {
            if (!stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    public static void clear() {
        stacks = List.of();
    }
}

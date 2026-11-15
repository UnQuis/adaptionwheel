package ru.adaptionwheel.client;

import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Client-side cache mirror stub (26.3 integration placeholder). */
public final class ClientCache {
    private static final List<ItemStack> STACKS = new ArrayList<>();

    private ClientCache() {}

    public static void set(List<ItemStack> stacks) {
        STACKS.clear();
        if (stacks != null) {
            STACKS.addAll(stacks);
        }
    }

    public static List<ItemStack> get() {
        return List.copyOf(STACKS);
    }
}

package ru.adaptionwheel.ability;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

public record ActiveAbility(
        String id,
        Component name,
        Component description,
        Supplier<ItemStack> icon,
        int color,
        int cooldownTicks,
        String keyLabel,
        List<String> unlockAny) {

    public ActiveAbility {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(icon, "icon");
        keyLabel = keyLabel == null ? "" : keyLabel;
        cooldownTicks = Math.max(0, cooldownTicks);
        unlockAny = unlockAny == null ? List.of() : List.copyOf(unlockAny);
    }

    public ItemStack iconStack() {
        ItemStack stack = icon.get();
        return stack == null ? ItemStack.EMPTY : stack;
    }
}

package ru.adaptionwheel.ability;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * A selectable active ability shown by the wheel menu and the small HUD card.
 *
 * <p>There are deliberately no built-in abilities yet. This record is the seam the menu's
 * "Active abilities - coming soon" card and {@code AbilityHud} are written against, so adding the
 * first real ability is a registration rather than a second UI. The card is not drawn while
 * {@link ActiveAbilities#isEmpty()} is true, which is what keeps an empty HUD from appearing
 * before there is anything to put in it.
 *
 * @param id            stable id, used to restore the last selection
 * @param name          display name
 * @param description   one-line description for the menu card
 * @param icon          creates the icon stack; a supplier so each caller gets its own stack
 * @param color         ARGB accent, usually the ability's school colour
 * @param cooldownTicks cooldown length; zero means no cooldown bar
 * @param keyLabel      the key shown in the HUD card, e.g. {@code "R"}; empty hides the badge
 * @param unlockAny     concepts of which the player needs at least one, checked on the server; the
 *                      client never evaluates them, it draws from the synced ability list
 */
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

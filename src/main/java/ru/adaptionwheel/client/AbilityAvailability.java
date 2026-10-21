package ru.adaptionwheel.client;

import org.jetbrains.annotations.Nullable;
import ru.adaptionwheel.ability.ActiveAbility;
import ru.adaptionwheel.ability.ActiveAbilities;

import java.util.ArrayList;
import java.util.List;

/**
 * Client-side gate for the ability registry.
 *
 * <p>An ability may name one or more concepts in {@link ActiveAbility#unlockAny()}; having any of
 * them makes the ability available. The server validates every activation independently, so this
 * is a display filter rather than a security boundary. It exists because the menu used to render
 * every registered ability, which would have advertised Dive to a player who had not adapted to
 * falling yet — the HUD would then have promised a key that does nothing.
 */
public final class AbilityAvailability {

    private AbilityAvailability() {
    }

    public static boolean unlocked(@Nullable ActiveAbility ability) {
        if (ability == null) {
            return false;
        }
        if (ability.unlockAny().isEmpty()) {
            return true;
        }
        for (String concept : ability.unlockAny()) {
            if (ClientAdaption.active(concept) || ClientAdaption.levelOrZero(concept) > 0) {
                return true;
            }
        }
        return false;
    }

    public static List<ActiveAbility> unlocked() {
        List<ActiveAbility> result = new ArrayList<>();
        for (ActiveAbility ability : ActiveAbilities.all()) {
            if (unlocked(ability)) {
                result.add(ability);
            }
        }
        return result;
    }

    /** The selected ability if it is unlocked, otherwise the first unlocked one. */
    @Nullable
    public static ActiveAbility selected() {
        ActiveAbility selected = ActiveAbilities.selected();
        if (unlocked(selected)) {
            return selected;
        }
        List<ActiveAbility> available = unlocked();
        return available.isEmpty() ? null : available.get(0);
    }

    /**
     * The key shown on the card. A definition only knows its default label; the live binding is the
     * truth, so the HUD and the menu both ask here rather than drawing a key that no longer does
     * anything after the player rebinds it.
     */
    public static String keyLabel(ActiveAbility ability) {
        if (ru.adaptionwheel.server.DiveAbility.ID.equals(ability.id())) {
            String bound = AdaptionKeybinds.DIVE_KEY.getTranslatedKeyMessage().getString();
            if (!bound.isBlank()) {
                return bound;
            }
        }
        return ability.keyLabel();
    }
}

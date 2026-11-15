package ru.adaptionwheel.client;

import org.jetbrains.annotations.Nullable;
import ru.adaptionwheel.ability.ActiveAbility;
import ru.adaptionwheel.ability.ActiveAbilities;

import java.util.ArrayList;
import java.util.List;

/**
 * Client-side view of which abilities the player may use (26.3 port).
 * The answer comes from the server sync payload.
 */
public final class AbilityAvailability {

    private AbilityAvailability() {
    }

    public static boolean unlocked(@Nullable ActiveAbility ability) {
        return ability != null && ClientAdaption.hasAbility(ability.id());
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
            if (AdaptionKeybinds.DIVE_KEY != null) {
                String bound = AdaptionKeybinds.DIVE_KEY.getTranslatedKeyMessage().getString();
                if (bound != null && !bound.isBlank()) {
                    return bound;
                }
            }
        }
        return ability.keyLabel() == null ? "" : ability.keyLabel();
    }
}

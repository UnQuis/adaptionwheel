package ru.adaptionwheel.client;

import org.jetbrains.annotations.Nullable;
import ru.adaptionwheel.ability.ActiveAbility;
import ru.adaptionwheel.ability.ActiveAbilities;

import java.util.ArrayList;
import java.util.List;

/**
 * Client-side view of which abilities the player may use.
 *
 * <p>The answer comes from the server, in the sync payload, and is not re-derived here. It used to
 * be computed from the client's copy of the adaptation sets, which lags the attachment by up to a
 * second (and can name a concept whose switch is off), so the card advertised a key the server then
 * refused — the ability appeared "even though the adaptations are not there".
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
            String bound = AdaptionKeybinds.DIVE_KEY.getTranslatedKeyMessage().getString();
            if (!bound.isBlank()) {
                return bound;
            }
        }
        return ability.keyLabel();
    }
}

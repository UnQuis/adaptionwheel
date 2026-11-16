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

    /**
     * The ability in the single slot. The server's persisted choice wins; the local selection is a
     * fallback for the first second before the sync arrives, and for a pick the server refused.
     */
    @Nullable
    public static ActiveAbility selected() {
        String chosen = ClientAdaption.selectedAbility;
        if (chosen != null && !chosen.isBlank()) {
            for (ActiveAbility ability : ActiveAbilities.all()) {
                if (ability.id().equals(chosen) && unlocked(ability)) {
                    return ability;
                }
            }
        }
        ActiveAbility selected = ActiveAbilities.selected();
        if (unlocked(selected)) {
            return selected;
        }
        List<ActiveAbility> available = unlocked();
        return available.isEmpty() ? null : available.get(0);
    }

    /**
     * The key shown on the card. There is one key for the whole slot, so every card asks for the
     * live binding of that key rather than drawing its old per-ability label after a rebind.
     */
    public static String keyLabel(ActiveAbility ability) {
        String bound = AdaptionKeybinds.ABILITY_KEY.getTranslatedKeyMessage().getString();
        return bound.isBlank() ? ability.keyLabel() : bound;
    }
}

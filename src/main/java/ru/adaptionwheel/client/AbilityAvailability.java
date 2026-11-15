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

    /** Live key label when a dedicated keybind exists, else the ability's configured label. */
    public static String keyLabel(ActiveAbility ability) {
        return ability.keyLabel() == null ? "" : ability.keyLabel();
    }
}

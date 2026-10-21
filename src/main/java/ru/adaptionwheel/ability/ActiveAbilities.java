package ru.adaptionwheel.ability;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The active-ability registry.
 *
 * <p>The menu renders its reserved "coming soon" card while this is empty, and the HUD card returns
 * without drawing. Both read the same list, so registering an ability lights up both surfaces at
 * once instead of leaving one of them behind. Dive is the first entry, registered from the common
 * setup hook.
 *
 * <p>Registration is open and may happen from any mod-loading phase; copies are returned so a
 * caller cannot mutate the registry by holding the list.
 */
public final class ActiveAbilities {

    private static final List<ActiveAbility> ALL = new CopyOnWriteArrayList<>();

    private static volatile String selectedId;

    private ActiveAbilities() {
    }

    public static void register(ActiveAbility ability) {
        if (ability == null || ability.id().isBlank()) {
            return;
        }
        for (ActiveAbility existing : ALL) {
            if (existing.id().equals(ability.id())) {
                ALL.set(ALL.indexOf(existing), ability);
                return;
            }
        }
        ALL.add(ability);
    }

    public static List<ActiveAbility> all() {
        return List.copyOf(ALL);
    }

    public static boolean isEmpty() {
        return ALL.isEmpty();
    }

    /** The last explicitly selected ability, or the first registered one. */
    @Nullable
    public static ActiveAbility selected() {
        if (ALL.isEmpty()) {
            return null;
        }
        String id = selectedId;
        if (id != null) {
            for (ActiveAbility ability : ALL) {
                if (ability.id().equals(id)) {
                    return ability;
                }
            }
        }
        return ALL.get(0);
    }

    /** The selected ability's id, or {@code null} while the registry is empty. */
    @Nullable
    public static String selectedId() {
        ActiveAbility ability = selected();
        return ability == null ? null : ability.id();
    }

    public static void select(String id) {
        selectedId = id;
    }

    /**
     * Convenience for future ability code that needs a deterministic snapshot for one frame.
     * Kept here rather than in the renderer so the menu and the HUD cannot disagree about order.
     */
    public static List<ActiveAbility> snapshot() {
        return new ArrayList<>(ALL);
    }
}

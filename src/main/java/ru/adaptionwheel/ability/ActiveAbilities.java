package ru.adaptionwheel.ability;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

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

    @Nullable
    public static String selectedId() {
        ActiveAbility ability = selected();
        return ability == null ? null : ability.id();
    }

    public static void select(String id) {
        selectedId = id;
    }

    public static List<ActiveAbility> snapshot() {
        return new ArrayList<>(ALL);
    }
}

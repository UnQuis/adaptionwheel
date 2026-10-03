package ru.adaptionwheel.category;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class Synergies {

    public enum Requirement {

        PRESENT,

        MAXED,

        PRESENT_OR_FAMILY
    }

    public record Synergy(String id, List<String[]> requires, String blurb) {
        public Synergy {
            requires = List.copyOf(requires);
        }
    }

    private static final String[] REQUIRE = {null, null};

    private Synergies() {
    }

    private static String[] exact(String concept) {
        return new String[]{concept, Requirement.PRESENT.name()};
    }

    private static String[] maxed(String concept) {
        return new String[]{concept, Requirement.MAXED.name()};
    }

    private static String[] family(String prefix) {
        return new String[]{prefix, Requirement.PRESENT_OR_FAMILY.name()};
    }

    public static final Synergy ASHWALKER = new Synergy("ashwalker",
            List.of(exact(Concepts.type(AdaptionCategory.FIRE)), exact(Concepts.MUTATION_THERMAL)),
            "Your attacks set the target alight.");

    public static final Synergy GLACIERBLOOD = new Synergy("glacierblood",
            List.of(exact(Concepts.type(AdaptionCategory.FREEZE)), exact(Concepts.ENV_ICE)),
            "Your attacks slow whatever they land on.");

    public static final Synergy DROWNED_WALTZ = new Synergy("drownedwaltz",
            List.of(exact(Concepts.ENV_LIQUID), exact(Concepts.MUTATION_AQUATIC)),
            "Submerged, you no longer need air and move faster still.");

    public static final Synergy SKYBREAKER = new Synergy("skybreaker",
            List.of(exact(Concepts.ENV_FALL), exact(Concepts.MUTATION_IMPACT)),
            "Your landing shockwave is far larger and triggers from much lower.");

    public static final Synergy UNMAKER = new Synergy("unmaker",
            List.of(exact(Concepts.ENV_VOID), family(Concepts.EXISTENCE_PREFIX)),
            "Void damage heals you instead of hurting you.");

    public static final Synergy UNSEEN = new Synergy("unseen",
            List.of(exact(Concepts.PERCEP_STEADY_GAZE), exact(Concepts.ENV_DARKNESS)),
            "Mobs lose track of you far more quickly.");

    public static final Synergy GRAVEBLOOM = new Synergy("gravebloom",
            List.of(exact(Concepts.ENV_THORNS), family(Concepts.CONTACT_PREFIX)),
            "Damage you take from a mob is partly returned to it.");

    public static final Synergy GOLIATH = new Synergy("goliath",
            List.of(maxed(ru.adaptionwheel.category.FistTiers.concept(2)),
                    exact(Concepts.MUTATION_THERMAL)),
            "Your bare hand burns what it breaks, and cooks what it touches.");

    public static final Synergy STORMCALL = new Synergy("stormcall",
            List.of(exact(Concepts.type(AdaptionCategory.LIGHTNING)), family(Concepts.EXISTENCE_PREFIX)),
            "Your damage arcs to a second and third target.");

    public static final Synergy ASTRAL_MINE = new Synergy("astralmine",
            List.of(maxed(Concepts.MINE_LABOR), exact(Concepts.MUTATION_FIST)),
            "Breaking blocks feeds your recovery, and the fist never slows.");

    public static final List<Synergy> ALL = Collections.unmodifiableList(Arrays.asList(
            ASHWALKER, GLACIERBLOOD, DROWNED_WALTZ, SKYBREAKER, UNMAKER, UNSEEN,
            GRAVEBLOOM, GOLIATH, STORMCALL, ASTRAL_MINE));

    public static final int COUNT = ALL.size();

    private static final java.util.Map<String, Synergy> BY_ID = new java.util.HashMap<>();

    static {
        for (Synergy s : ALL) {
            BY_ID.put(s.id(), s);
        }
    }

    public static Synergy byId(String id) {
        return BY_ID.get(id);
    }

    public static List<String> activeIds(ru.adaptionwheel.data.PlayerAdaption data) {
        List<String> out = new ArrayList<>();
        for (Synergy s : ALL) {
            if (satisfied(data, s)) {
                out.add(s.id());
            }
        }
        return out;
    }

    public static boolean isActive(ru.adaptionwheel.data.PlayerAdaption data, Synergy synergy) {
        return satisfied(data, synergy);
    }

    public static boolean satisfied(ru.adaptionwheel.data.PlayerAdaption data, Synergy synergy) {
        return satisfied(data.levels, data.adapted, synergy);
    }

    public static boolean satisfied(Map<String, Integer> levels, java.util.Collection<String> adapted,
                                    Synergy synergy) {
        int max = ru.adaptionwheel.data.PlayerAdaption.MAX_LEVEL;
        for (String[] requirement : synergy.requires()) {
            String concept = requirement[0];
            Requirement how = Requirement.valueOf(requirement[1]);
            int level = levels.getOrDefault(concept, 0);
            boolean present = adapted.contains(concept) || level > 0;
            switch (how) {
                case MAXED -> {
                    if (level < max) {
                        return false;
                    }
                }
                case PRESENT_OR_FAMILY -> {
                    if (!present && !hasAnyInFamily(levels, adapted, concept)) {
                        return false;
                    }
                }
                default -> {
                    if (!present) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private static boolean hasAnyInFamily(Map<String, Integer> levels,
                                          java.util.Collection<String> adapted, String prefix) {
        for (String held : adapted) {
            if (held.startsWith(prefix)) {
                return true;
            }
        }
        for (Map.Entry<String, Integer> entry : levels.entrySet()) {
            if (entry.getValue() > 0 && entry.getKey().startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    public static Set<String> componentsOf(Synergy synergy) {
        java.util.LinkedHashSet<String> out = new java.util.LinkedHashSet<>();
        for (String[] requirement : synergy.requires()) {
            out.add(requirement[0]);
        }
        return out;
    }
}

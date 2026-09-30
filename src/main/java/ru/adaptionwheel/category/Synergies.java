package ru.adaptionwheel.category;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Named combinations of adaptations that do something none of them does alone.
 *
 * <p>Thirty independent adaptations are a list, not a build. A synergy is what makes the pool
 * interact: holding two specific adaptations unlocks a named third thing, so the set you end up
 * with is a set of <em>combinations</em> rather than a tally. They are also the natural answer to
 * "more of the same" — each one is a behaviour, not another flat stat.</p>
 *
 * <p><b>Purely additive, like everything else here.</b> No synergy can take anything away, and
 * there is no downside tier. The wheel is meant to be omnipotent, so the interesting question is
 * "what does this pairing become" and never "what does it cost".</p>
 *
 * <p>Requirements are matched on <em>the adaptation being present at any level</em>, not maxed,
 * with one exception where a synergy is specifically about a finished tier: {@link #GOLIATH}
 * wants Fist levels. Matching a family prefix also counts, so a synergy written against
 * {@code Existence_*} is satisfied by any boss the wearer has adapted to, rather than having to be
 * enumerated per boss id — the concepts here are generated per mob and per boss, so an exact match
 * would mean a synergy per boss.</p>
 *
 * <p>Evaluated once per second from the wearer tick, not per damage event: the set can only change
 * when an adaptation completes.</p>
 */
public final class Synergies {

    /** How an adaptation is counted towards a synergy requirement. */
    public enum Requirement {
        /** Any level at all, or a one-time grant. */
        PRESENT,
        /** Must be at full level. */
        MAXED,
        /** Any level at all, and a family prefix counts (so "any boss" works). */
        PRESENT_OR_FAMILY
    }

    /**
     * One named combination.
     *
     * @param id       stable key, also the translation suffix
     * @param requires pairs of (concept, how strictly it is required)
     * @param blurb    short human-readable summary, used in the browser
     */
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

    // ------------------------------------------------------------------ the roster

    /** Type_Fire + Mutation_Thermal: your hits leave the target burning. */
    public static final Synergy ASHWALKER = new Synergy("ashwalker",
            List.of(exact(Concepts.type(AdaptionCategory.FIRE)), exact(Concepts.MUTATION_THERMAL)),
            "Your attacks set the target alight.");

    /** Type_FREEZE + Env_Ice: your hits chill what they land on. */
    public static final Synergy GLACIERBLOOD = new Synergy("glacierblood",
            List.of(exact(Concepts.type(AdaptionCategory.FREEZE)), exact(Concepts.ENV_ICE)),
            "Your attacks slow whatever they land on.");

    /** Env_Liquid + Mutation_Aquatic: the water stops being a place you survive and becomes fast. */
    public static final Synergy DROWNED_WALTZ = new Synergy("drownedwaltz",
            List.of(exact(Concepts.ENV_LIQUID), exact(Concepts.MUTATION_AQUATIC)),
            "Submerged, you no longer need air and move faster still.");

    /** Env_FallDamage + Mutation_Impact: the shockwave becomes the point. */
    public static final Synergy SKYBREAKER = new Synergy("skybreaker",
            List.of(exact(Concepts.ENV_FALL), exact(Concepts.MUTATION_IMPACT)),
            "Your landing shockwave is far larger and triggers from much lower.");

    /** Env_Void + any boss: the void that killed everything else feeds you. */
    public static final Synergy UNMAKER = new Synergy("unmaker",
            List.of(exact(Concepts.ENV_VOID), family(Concepts.EXISTENCE_PREFIX)),
            "Void damage heals you instead of hurting you.");

    /** Percep_SteadyGaze + Env_Darkness: they stop being able to find you. */
    public static final Synergy UNSEEN = new Synergy("unseen",
            List.of(exact(Concepts.PERCEP_STEADY_GAZE), exact(Concepts.ENV_DARKNESS)),
            "Mobs lose track of you far more quickly.");

    /** Env_Thorns + any Contact: what they do to you comes back out. */
    public static final Synergy GRAVEBLOOM = new Synergy("gravebloom",
            List.of(exact(Concepts.ENV_THORNS), family(Concepts.CONTACT_PREFIX)),
            "Damage you take from a mob is partly returned to it.");

    /** Maxed Fist_Iron + Mutation_Thermal: the fist smelts. */
    public static final Synergy GOLIATH = new Synergy("goliath",
            List.of(maxed(ru.adaptionwheel.category.FistTiers.concept(2)),
                    exact(Concepts.MUTATION_THERMAL)),
            "Your bare hand burns what it breaks, and cooks what it touches.");

    /** Type_LIGHTNING + any boss: every strike finds the next one. */
    public static final Synergy STORMCALL = new Synergy("stormcall",
            List.of(exact(Concepts.type(AdaptionCategory.LIGHTNING)), family(Concepts.EXISTENCE_PREFIX)),
            "Your damage arcs to a second and third target.");

    /** Mine_Labor at max + Mutation_Fist: the fist harvests the block as well. */
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

    /** Ids of every synergy the given adaptation set satisfies, in roster order. */
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

    /** Whether the wearer holds everything a synergy asks for. */
    public static boolean satisfied(ru.adaptionwheel.data.PlayerAdaption data, Synergy synergy) {
        return satisfied(data.levels, data.adapted, synergy);
    }

    /**
     * The same question asked of a bare level map and adapted set.
     *
     * <p>This overload exists so the client can answer it too, from its synced mirror, without
     * having to fabricate a {@code PlayerAdaption} to hold two collections it already has. The
     * browser calls exactly this; a second copy of the matching rules on the client would be a
     * second thing to keep in step, and the two disagreeing is invisible — a synergy would simply
     * never appear for a player who has it.</p>
     */
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

    /**
     * Any held concept beginning with {@code prefix}.
     *
     * <p>This is what lets a boss-agnostic synergy exist at all. The concept keys are generated per
     * boss id, so an exact-match requirement would need one synergy registered per boss; a prefix
     * match against the held set answers the same question for all of them.</p>
     */
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

    /** The concepts a synergy names, for the browser row. */
    public static Set<String> componentsOf(Synergy synergy) {
        java.util.LinkedHashSet<String> out = new java.util.LinkedHashSet<>();
        for (String[] requirement : synergy.requires()) {
            out.add(requirement[0]);
        }
        return out;
    }
}

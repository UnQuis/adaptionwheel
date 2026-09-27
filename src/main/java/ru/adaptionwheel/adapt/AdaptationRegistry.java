package ru.adaptionwheel.adapt;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central registry of adaptation definitions. This is the extension point for
 * both built-in concepts and third-party code: registering a definition adds
 * GUI/HUD metadata without touching storage, networking or save format.
 *
 * <p>Concept keys that have no registered definition (dynamic ones like
 * {@code Contact_minecraft:zombie}) still work everywhere — they simply fall
 * back to {@link #domainOf(String)}'s prefix rules.</p>
 */
public final class AdaptationRegistry {

    private static final Map<String, AdaptationDefinition> DEFINITIONS = new ConcurrentHashMap<>();

    /** Env_ ids that are physically about forces rather than hazards. */
    private static final Set<String> PHYSICS_ENV_IDS = Set.of(
            "Env_Ice", "Env_Slime", "Env_Cobweb", "Env_Knockback", "Env_FallDamage");

    static {
        registerBuiltins();
    }

    private AdaptationRegistry() {
    }

    private static void registerBuiltins() {
        // ---- Movement / block-imposed restrictions (Adaptation to Discomfort) ----
        register(AdaptationDefinition.oneTime("Move_SoulSand", AdaptationDomain.MOVEMENT));
        register(AdaptationDefinition.oneTime("Move_Honey", AdaptationDomain.MOVEMENT));
        register(AdaptationDefinition.oneTime("Move_PowderSnow", AdaptationDomain.MOVEMENT));
        register(AdaptationDefinition.oneTime("Move_BerryBush", AdaptationDomain.MOVEMENT));
        register(AdaptationDefinition.oneTime("Move_BubbleColumn", AdaptationDomain.MOVEMENT));

        // ---- Mining / Combat / Perception domains (Adaptation to Discomfort) ----
        register(AdaptationDefinition.leveled("Mine_Labor", AdaptationDomain.MINING));
        register(AdaptationDefinition.leveled("Combat_Cooldown", AdaptationDomain.COMBAT));
        register(AdaptationDefinition.oneTime("Combat_ShieldLock", AdaptationDomain.COMBAT));
        register(AdaptationDefinition.oneTime("Combat_SkillIssue", AdaptationDomain.COMBAT));
        register(AdaptationDefinition.oneTime("Percep_SteadyGaze", AdaptationDomain.PERCEPTION));

        // ---- Fist Mastery: the adaptation to breaking, plus its material tiers ----
        for (int i = 0; i < ru.adaptionwheel.category.FistTiers.TIER_COUNT; i++) {
            register(AdaptationDefinition.leveled(ru.adaptionwheel.category.FistTiers.concept(i),
                    AdaptationDomain.MINING));
        }

        // ---- Environmental one-time adaptations ----
        for (String id : new String[]{
                "Env_Lava", "Env_Drowning", "Env_Thorns", "Env_Darkness",
                "Env_Suffocate", "Env_Void", "Env_Starve", "Env_Liquid"}) {
            register(AdaptationDefinition.oneTime(id, AdaptationDomain.ENVIRONMENT));
        }
        // Physics-flavored env ids keep their legacy keys but group under PHYSICS.
        for (String id : PHYSICS_ENV_IDS) {
            register(AdaptationDefinition.oneTime(id, AdaptationDomain.PHYSICS));
        }

        // ---- Damage categories ----
        for (ru.adaptionwheel.category.AdaptionCategory category : ru.adaptionwheel.category.AdaptionCategory.values()) {
            register(AdaptationDefinition.leveled(ru.adaptionwheel.category.Concepts.type(category), AdaptationDomain.DAMAGE));
        }

        // ---- Special ----
        register(new AdaptationDefinition("Self_Damage", AdaptationDomain.SPECIAL, true, AdaptationDefinition.DEFAULT_MAX_LEVEL));
        register(AdaptationDefinition.oneTime("ADBERSITY", AdaptationDomain.SPECIAL));
        register(AdaptationDefinition.oneTime("Mutation_Thermal", AdaptationDomain.SPECIAL));
        register(AdaptationDefinition.oneTime("Mutation_Aquatic", AdaptationDomain.SPECIAL));
        register(AdaptationDefinition.oneTime("Mutation_Impact", AdaptationDomain.SPECIAL));
        register(AdaptationDefinition.oneTime(ru.adaptionwheel.category.Concepts.MUTATION_FIST,
                AdaptationDomain.SPECIAL));
        register(AdaptationDefinition.oneTime("Dimension_Destroy", AdaptationDomain.SPECIAL));
    }

    /**
     * Registers a definition. Later registrations with the same key replace
     * earlier ones (mods may refine built-in metadata). Safe to call during
     * mod construction or any time before the GUI is opened.
     */
    public static void register(AdaptationDefinition definition) {
        if (definition == null || definition.concept() == null || definition.concept().isEmpty()) {
            return;
        }
        DEFINITIONS.put(definition.concept(), definition);
    }

    @Nullable
    public static AdaptationDefinition get(String concept) {
        return concept == null ? null : DEFINITIONS.get(concept);
    }

    public static boolean isRegistered(String concept) {
        return DEFINITIONS.containsKey(concept);
    }

    /** All registered definitions sorted by domain, then concept id. */
    public static List<AdaptationDefinition> allDefinitions() {
        List<AdaptationDefinition> list = new ArrayList<>(DEFINITIONS.values());
        list.sort(Comparator.comparingInt((AdaptationDefinition d) -> d.domain().ordinal())
                .thenComparing(AdaptationDefinition::concept));
        return list;
    }

    /**
     * Resolves the organizational domain of any concept key — including dynamic
     * per-entity/per-effect keys that were never explicitly registered.
     */
    public static AdaptationDomain domainOf(String concept) {
        AdaptationDefinition def = get(concept);
        if (def != null) {
            return def.domain();
        }
        if (concept == null) {
            return AdaptationDomain.SPECIAL;
        }
        if (concept.startsWith("Type_") || concept.startsWith("Proj_") || concept.startsWith("DamageClass_")) {
            return AdaptationDomain.DAMAGE;
        }
        if (concept.startsWith("Debuff_")) return AdaptationDomain.EFFECT;
        if (concept.startsWith("Move_")) return AdaptationDomain.MOVEMENT;
        if (concept.startsWith("Phys_")) return AdaptationDomain.PHYSICS;
        if (concept.startsWith("Mine_")) return AdaptationDomain.MINING;
        if (concept.startsWith("Combat_")) return AdaptationDomain.COMBAT;
        if (concept.startsWith("Percep_")) return AdaptationDomain.PERCEPTION;
        if (concept.startsWith("Contact_") || concept.startsWith("Offense_NPC_") || concept.startsWith("Drop_NPC_")) {
            return AdaptationDomain.ENTITY;
        }
        if (concept.startsWith("Existence_")) return AdaptationDomain.EXISTENCE;
        if (concept.startsWith("Env_")) {
            String id = concept.substring("Env_".length());
            return PHYSICS_ENV_IDS.contains(id) ? AdaptationDomain.PHYSICS : AdaptationDomain.ENVIRONMENT;
        }
        return AdaptationDomain.SPECIAL;
    }

    /**
     * Prefix used by {@link ru.adaptionwheel.category.Concepts#color} and HUD sorting:
     * returns the organizational family tag of a concept, or "" for unknown.
     */
    public static Collection<AdaptationDefinition> definitionsIn(AdaptationDomain domain) {
        List<AdaptationDefinition> out = new ArrayList<>();
        for (AdaptationDefinition def : DEFINITIONS.values()) {
            if (def.domain() == domain) {
                out.add(def);
            }
        }
        out.sort(Comparator.comparing(d -> d.concept().toLowerCase(Locale.ROOT)));
        return out;
    }
}

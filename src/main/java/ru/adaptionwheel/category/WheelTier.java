package ru.adaptionwheel.category;

import java.util.Locale;

/**
 * The wheel's own awakening, derived from how many adaptations the wearer holds.
 *
 * <p>Until this existed the mod's progression had no spine at all: thirty independent concepts,
 * each of which is a flat "survive this and stop caring about it", with nothing that says the wheel
 * itself is getting stronger. The wheel's tier is the thing that says it — every tier
 * <em>reveals another family of adaptations</em>, so the pool of what you can possibly adapt to
 * grows instead of the pool of what you have already survived.</p>
 *
 * <p><b>Revealing, never restricting.</b> A tier only ever adds a family; it never takes one away,
 * and it never imposes a cost. The point of the wheel is omnipotence, so a build is never made
 * worse by having reached a later tier — it is only made larger. That is a deliberate design
 * constraint, not an oversight.</p>
 *
 * <p>Derived, never stored. {@code getAdaptCount()} is already the quantity everything else scales
 * with, so deriving from it means an existing save wakes up at the right tier on the first tick and
 * a wheel handed to another player arrives pre-awakened, with no migration and nothing to keep in
 * sync. The one thing that <em>is</em> kept is a transient "last announced" marker, purely so the
 * tier-up message fires once rather than every tick.</p>
 *
 * <p>See {@code server/AdaptionEvents#startOrAccelerate} for the single choke point this gates —
 * because every analysis in the mod funnels through it, one check here covers all of them.</p>
 */
public final class WheelTier {

    /** Adaptation count at which each tier begins. Index == tier number. */
    private static final int[] THRESHOLDS = {0, 12, 35, 75, 140, 240};

    private static final String[] NAMES = {
            "Dormant", "Stirring", "Resonant", "Dominant", "Apex", "Infinite"
    };

    /** Display colour per tier: the 3D wheel, the HUD row and the tier-up flash all use it. */
    private static final int[] COLORS = {
            0xFF4A4A4A, // Dormant  — dull iron
            0xFF6E8B3D, // Stirring — moss
            0xFF3D8B8B, // Resonant — teal
            0xFF8B5A2D, // Dominant — bronze
            0xFFB03D8B, // Apex     — violet
            0xFFFFFFFF, // Infinite — white
    };

    private static final int MAX_TIER = THRESHOLDS.length - 1;

    private WheelTier() {
    }

    public static int maxTier() {
        return MAX_TIER;
    }

    /** Highest tier reached at this adaptation count. Never below 0, never above {@link #maxTier()}. */
    public static int forCount(int adaptCount) {
        int tier = 0;
        for (int i = 0; i < THRESHOLDS.length; i++) {
            if (adaptCount >= THRESHOLDS[i]) {
                tier = i;
            }
        }
        return tier;
    }

    /**
     * Adaptation count needed for the next tier, or {@code -1} at the top.
     *
     * <p>Shown in the HUD as the progress towards waking further, so a player at tier 2 always has
     * a number to work towards rather than a vague sense of "more".</p>
     */
    public static int nextThreshold(int tier) {
        return tier >= MAX_TIER ? -1 : THRESHOLDS[tier + 1];
    }

    public static String name(int tier) {
        return NAMES[clamp(tier)];
    }

    /** Translation key for the tier's display name. */
    public static String nameKey(int tier) {
        return "adaptionwheel.tier." + name(tier).toLowerCase(Locale.ROOT);
    }

    public static int color(int tier) {
        return COLORS[clamp(tier)];
    }

    /**
     * Per-tier bonus applied to max health and armour, as a fraction of the base value.
     *
     * <p>Deliberately generous and unconditional. There is no cost anywhere in this mod, so an
     * awakening must feel like a real step up rather than a bookkeeping milestone.</p>
     */
    public static double statBonus(int tier) {
        return clamp(tier) * 0.08;
    }

    /**
     * Whether a concept's family has been revealed at this tier.
     *
     * <p>Prefix-based, so the dynamic concept keys this mod invents per mob and per boss
     * ({@code Contact_minecraft:zombie}, {@code Existence_draconicevolution:draconic_guardian}) are
     * covered without registering any of them, exactly like the display-name and colour lookups.</p>
     *
     * <p>Order matters: the longest prefixes are tested first, because {@code Offense_NPC_} and
     * {@code Drop_NPC_} both start with something that is also a family of its own.</p>
     */
    public static boolean familyUnlocked(String concept, int tier) {
        if (concept == null || concept.isEmpty()) {
            return true;
        }
        int required = requiredTierFor(concept);
        return required < 0 || tier >= required;
    }

    /** Family a concept belongs to, for the "revealed" message. {@code null} for the core family. */
    public static String familyName(String concept) {
        if (concept == null) {
            return null;
        }
        if (concept.startsWith(Concepts.EXISTENCE_PREFIX)) return "Existence";
        if (concept.startsWith(Concepts.OFFENSE_PREFIX)) return "Offense";
        if (concept.startsWith(Concepts.DROP_PREFIX)) return "Plunder";
        if (concept.startsWith(Concepts.CONTACT_PREFIX)) return "Contact";
        if (concept.startsWith(Concepts.ENV_PREFIX)) return "Environment";
        return null;
    }

    /**
     * Tier at which this family appears, or {@code -1} for the always-available core.
     *
     * <p>Core is the whole damage-type ladder, debuffs, <b>the environments</b>, the
     * movement/mining/combat/perception discomforts, the fist and the mutations — everything a
     * player can reach without the wheel ever waking.
     *
     * <p><b>{@code Env_} is core, and that is a correction.</b> It was originally gated behind the
     * first tier, on the reasoning that it is one of the "deep" families. That reasoning was
     * wrong. The gated families are the ones that scale with the world — one concept per mob and
     * per boss, hundreds of them — while there are only thirteen environments and they are as
     * basic as damage types. Gating them meant a player with fewer than a dozen adaptations could
     * not begin adapting to water at all, which is not progression, it is a feature switched off.
     * The gate now starts where the content actually deepens: with the per-mob families.</p>
     */
    private static int requiredTierFor(String concept) {
        if (concept.startsWith(Concepts.EXISTENCE_PREFIX)) return 4;
        if (concept.startsWith(Concepts.OFFENSE_PREFIX)) return 2;
        if (concept.startsWith(Concepts.DROP_PREFIX)) return 3;
        if (concept.startsWith(Concepts.CONTACT_PREFIX)) return 1;
        return -1;
    }

    private static int clamp(int tier) {
        return Math.max(0, Math.min(MAX_TIER, tier));
    }
}

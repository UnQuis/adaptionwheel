package ru.adaptionwheel.category;

import java.util.Locale;

public final class WheelTier {

    private static final int[] THRESHOLDS = {0, 12, 35, 75, 140, 240};

    private static final String[] NAMES = {
            "Dormant", "Stirring", "Resonant", "Dominant", "Apex", "Infinite"
    };

    private static final int[] COLORS = {
            0xFF4A4A4A,
            0xFF6E8B3D,
            0xFF3D8B8B,
            0xFF8B5A2D,
            0xFFB03D8B,
            0xFFFFFFFF,
    };

    private static final int MAX_TIER = THRESHOLDS.length - 1;

    private WheelTier() {
    }

    public static int maxTier() {
        return MAX_TIER;
    }

    public static int forCount(int adaptCount) {
        int tier = 0;
        for (int i = 0; i < THRESHOLDS.length; i++) {
            if (adaptCount >= THRESHOLDS[i]) {
                tier = i;
            }
        }
        return tier;
    }

    public static int nextThreshold(int tier) {
        return tier >= MAX_TIER ? -1 : THRESHOLDS[tier + 1];
    }

    public static String name(int tier) {
        return NAMES[clamp(tier)];
    }

    public static String nameKey(int tier) {
        return "adaptionwheel.tier." + name(tier).toLowerCase(Locale.ROOT);
    }

    public static int color(int tier) {
        return COLORS[clamp(tier)];
    }

    public static double statBonus(int tier) {
        return clamp(tier) * 0.08;
    }

    public static boolean familyUnlocked(String concept, int tier) {
        if (concept == null || concept.isEmpty()) {
            return true;
        }
        int required = requiredTierFor(concept);
        return required < 0 || tier >= required;
    }

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

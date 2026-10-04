package ru.adaptionwheel.category;

/**
 * The five material stages of the punching fist, mirroring {@link FistTiers} for breaking.
 *
 * <p>Same shape on purpose: eight levels a stage, and the ladder is walked by doing the thing the
 * stage is named for. What differs is the training material — blocks for the breaking fist, bare
 * -handed kills of what you are punching for this one.
 */
public final class CombatFistTiers {

    public static final int TIER_COUNT = 5;

    public static final String[] CONCEPTS = {
            "Combat_FistWood", "Combat_FistStone", "Combat_FistIron",
            "Combat_FistDiamond", "Combat_FistNetherite"
    };

    private static final String[] MATERIALS = {"wooden", "stone", "iron", "diamond", "netherite"};

    public static final int[] COLORS = {
            0xFFA9784A,
            0xFF9A9A9A,
            0xFFDCDCDC,
            0xFF4FE3D8,
            0xFF6B4A78
    };

    private CombatFistTiers() {
    }

    public static String suffix(int tier) {
        return switch (tier) {
            case 0 -> "wood";
            case 1 -> "stone";
            case 2 -> "iron";
            case 3 -> "diamond";
            case 4 -> "netherite";
            default -> throw new IllegalArgumentException("bad fist tier " + tier);
        };
    }

    public static String material(int tier) {
        return MATERIALS[tier];
    }

    public static String concept(int tier) {
        return CONCEPTS[tier];
    }

    public static int color(int tier) {
        return COLORS[tier];
    }
}

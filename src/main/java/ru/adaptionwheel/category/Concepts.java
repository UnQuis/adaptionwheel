package ru.adaptionwheel.category;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Concept keys mirror the original Terraria mod's adaptation naming scheme:
 * Type_*, Contact_*, Offense_NPC_*, Drop_NPC_* are level-based (Lv1-8);
 * Env_*, Debuff_*, ADBERSITY are one-time. Drop_NPC_ levels come from kill counts.
 */
public final class Concepts {

    public static final String SELF_DAMAGE = "Self_Damage";
    public static final String ADVERSITY = "ADBERSITY";

    /** Combo mutations unlocked by conditions over other completed adaptations. One-time. */
    public static final String MUTATION_THERMAL = "Mutation_Thermal";
    public static final String MUTATION_AQUATIC = "Mutation_Aquatic";
    public static final String MUTATION_IMPACT = "Mutation_Impact";

    /**
     * Fist Mastery: the adaptation to breaking itself. Unlocked by maxing
     * {@link #MINE_LABOR} and breaking a stone block bare-handed; it then grants a
     * bare-handed fist that harvests blocks without any tool, and opens the
     * {@code Fist_*} tier progression (see {@link FistTiers}).
     */
    public static final String MUTATION_FIST = "Mutation_Fist";

    /**
     * Transcendence-tier ultimate from the original mod ("Dimension Destroy"):
     * unlocked past the adaptation-count threshold; Sword of Extermination
     * swings then fire spatial rifts that sever lives outright.
     */
    public static final String DIMENSION_DESTROY = "Dimension_Destroy";

    public static final String ENV_FALL = "Env_FallDamage";
    public static final String ENV_LAVA = "Env_Lava";
    public static final String ENV_DROWN = "Env_Drowning";
    public static final String ENV_THORNS = "Env_Thorns";
    public static final String ENV_DARKNESS = "Env_Darkness";
    public static final String ENV_KNOCKBACK = "Env_Knockback";
    public static final String ENV_SUFFOCATE = "Env_Suffocate";
    public static final String ENV_VOID = "Env_Void";
    public static final String ENV_STARVE = "Env_Starve";
    public static final String ENV_LIQUID = "Env_Liquid";
    public static final String ENV_ICE = "Env_Ice";
    public static final String ENV_SLIME = "Env_Slime";
    public static final String ENV_COBWEB = "Env_Cobweb";

    /**
     * Adaptation to Discomfort: block-imposed movement restrictions.
     * These are one-time adaptations triggered by sustained exposure,
     * following the same analysis-task flow as the Env_* family.
     */
    public static final String MOVE_SOUL_SAND = "Move_SoulSand";
    public static final String MOVE_HONEY = "Move_Honey";
    public static final String MOVE_POWDER_SNOW = "Move_PowderSnow";
    public static final String MOVE_BERRY_BUSH = "Move_BerryBush";
    public static final String MOVE_BUBBLE_COLUMN = "Move_BubbleColumn";

    /**
     * Adaptation to Discomfort: mining, combat and perception domains.
     * Mine_Labor / Combat_Cooldown are leveled; the other two are one-time.
     */
    public static final String MINE_LABOR = "Mine_Labor";
    public static final String COMBAT_COOLDOWN = "Combat_Cooldown";
    public static final String COMBAT_SHIELD_LOCK = "Combat_ShieldLock";
    public static final String COMBAT_SKILL_ISSUE = "Combat_SkillIssue";
    public static final String PERCEP_STEADY_GAZE = "Percep_SteadyGaze";

    /** HUD / chat bar colors per concept family, matching the original mod. */
    public static final int COLOR_CONTACT = 0xFF55FF55;
    public static final int COLOR_OFFENSE = 0xFFFF5555;
    public static final int COLOR_DROP = 0xFFB464FF;
    public static final int COLOR_PROJECTILE = 0xFFFFAFC8;
    public static final int COLOR_DEBUFF = 0xFFFFFF00;
    public static final int COLOR_ENV = 0xFF00FFFF;
    public static final int COLOR_MOVEMENT = 0xFF90EE90;
    public static final int COLOR_MINING = 0xFFB0B0B0;
    public static final int COLOR_COMBAT = 0xFFFF6B6B;
    public static final int COLOR_PERCEPTION = 0xFFE0B0FF;
    public static final int COLOR_INJURE = 0xFFFF1493;
    public static final int COLOR_DAMAGE_CLASS = 0xFFFFA500;
    public static final int COLOR_SPECIAL = 0xFF9370DB;
    public static final int COLOR_MUTATION = 0xFFFF8C00;
    public static final int COLOR_EXISTENCE = 0xFFFFFFFF; // rainbow — computed dynamically in HUD
    public static final int COLOR_GENERIC = 0xFFFFFFFF;

    private Concepts() {
    }

    public static String type(AdaptionCategory category) {
        return "Type_" + category.name();
    }

    public static String contact(String mobPath) {
        return "Contact_" + mobPath;
    }

    public static String offense(String mobPath) {
        return "Offense_NPC_" + mobPath;
    }

    public static String drop(String mobPath) {
        return "Drop_NPC_" + mobPath;
    }

    public static String debuff(String effectPath) {
        return "Debuff_" + effectPath;
    }

    public static String existence(String mobPath) {
        return "Existence_" + mobPath;
    }

    /**
     * Leveled vs one-time resolution is registry-first so mixed families
     * (e.g. leveled Combat_Cooldown + one-time Combat_ShieldLock) work,
     * with the legacy prefix rules as fallback for dynamic keys.
     */
    public static boolean isLevelBased(String concept) {
        ru.adaptionwheel.adapt.AdaptationDefinition def = ru.adaptionwheel.adapt.AdaptationRegistry.get(concept);
        if (def != null) {
            return def.leveled();
        }
        return concept.startsWith("Type_") || concept.startsWith("Contact_")
                || concept.startsWith("Offense_") || concept.startsWith("Drop_NPC_")
                || concept.equals(SELF_DAMAGE);
    }

    public static boolean isDrop(String concept) {
        return concept.startsWith("Drop_NPC_");
    }

    public static boolean isOneTime(String concept) {
        ru.adaptionwheel.adapt.AdaptationDefinition def = ru.adaptionwheel.adapt.AdaptationRegistry.get(concept);
        if (def != null) {
            return !def.leveled();
        }
        return concept.startsWith("Env_") || concept.startsWith("Debuff_")
                || concept.startsWith("Existence_") || concept.startsWith("Mutation_")
                || concept.startsWith("Move_") || concept.startsWith("Percep_")
                || concept.equals(ADVERSITY);
    }

    public static boolean isOffense(String concept) {
        return concept.startsWith("Offense_");
    }

    /** Bar/text color for a concept, mirroring the original HUD colors. */
    public static int color(String concept) {
        if (concept.startsWith("Contact_")) return COLOR_CONTACT;
        if (concept.startsWith("Offense_")) return COLOR_OFFENSE;
        if (concept.startsWith("Drop_NPC_")) return COLOR_DROP;
        if (concept.startsWith("Proj_")) return COLOR_PROJECTILE;
        if (concept.startsWith("Debuff_")) return COLOR_DEBUFF;
        if (concept.startsWith("Env_")) return COLOR_ENV;
        if (concept.startsWith("Move_")) return COLOR_MOVEMENT;
        if (concept.startsWith("Mine_")) return COLOR_MINING;
        if (concept.startsWith("Fist_")) return fistColor(concept.substring("Fist_".length()));
        if (concept.startsWith("Combat_")) return COLOR_COMBAT;
        if (concept.startsWith("Percep_")) return COLOR_PERCEPTION;
        if (concept.startsWith("Mutation_")) return COLOR_MUTATION;
        if (concept.startsWith("Dimension_")) return COLOR_SPECIAL;
        if (concept.startsWith("DamageClass_")) return COLOR_DAMAGE_CLASS;
        if (concept.equals(SELF_DAMAGE)) return COLOR_INJURE;
        if (concept.startsWith("Existence_") || concept.equals(ADVERSITY)) return COLOR_EXISTENCE;
        return COLOR_GENERIC;
    }

    /**
     * Name shown in chat/HUD, mirroring the original mod's finalName logic:
     * env concepts are uppercased (FALLDAMAGE, LAVA...), injuries become "Injure",
     * debuffs/mobs use their localized names. Unstyled: callers apply colors.
     */
    public static Component chatName(String concept) {
        if (concept.equals(SELF_DAMAGE)) {
            return Component.literal("Injure");
        }
        if (concept.startsWith("Env_")) {
            return Component.literal(concept.substring(4).toUpperCase());
        }
        if (concept.startsWith("Mutation_")) {
            return mutationName(concept.substring("Mutation_".length()));
        }
        if (concept.startsWith("Debuff_")) {
            return debuffName(concept.substring(7));
        }
        if (concept.startsWith("Move_")) {
            return Component.translatable("adaptionwheel.concept.move." + concept);
        }
        if (concept.startsWith("Mine_")) {
            return Component.translatable("adaptionwheel.concept.mine." + concept);
        }
        if (concept.startsWith("Fist_")) {
            return Component.translatable("adaptionwheel.concept.fist." + concept);
        }
        if (concept.startsWith("Combat_")) {
            return Component.translatable("adaptionwheel.concept.combat." + concept);
        }
        if (concept.startsWith("Percep_")) {
            return Component.translatable("adaptionwheel.concept.percep." + concept);
        }
        if (concept.equals(DIMENSION_DESTROY)) {
            return Component.translatable("adaptionwheel.concept.special." + concept);
        }
        if (concept.startsWith("Contact_")) {
            return entityName(concept.substring("Contact_".length()), " (Contact)");
        }
        if (concept.startsWith("Offense_NPC_")) {
            return entityName(concept.substring("Offense_NPC_".length()), "");
        }
        if (concept.startsWith("Drop_NPC_")) {
            return entityName(concept.substring("Drop_NPC_".length()), " (DropRate)");
        }
        if (concept.startsWith("Existence_")) {
            return entityName(concept.substring("Existence_".length()), "");
        }
        if (concept.startsWith("DamageClass_")) {
            return Component.literal(concept.replace("DamageClass_", "").replace("DamageClass", "") + " Damage");
        }
        return displayName(concept);
    }

    /** Client/server-safe display name for a concept key. Unstyled. */
    public static Component displayName(String concept) {
        if (concept.equals(SELF_DAMAGE)) {
            return Component.translatable("adaptionwheel.concept.self_damage");
        }
        if (concept.equals(ADVERSITY)) {
            return Component.translatable("adaptionwheel.concept.adversity");
        }
        if (concept.startsWith("Type_")) {
            return Component.translatable("adaptionwheel.concept.type." + concept.substring(5).toLowerCase());
        }
        if (concept.startsWith("Env_")) {
            return Component.translatable("adaptionwheel.concept.env." + concept);
        }
        if (concept.startsWith("Mutation_")) {
            return mutationName(concept.substring("Mutation_".length()));
        }
        if (concept.startsWith("Debuff_")) {
            return debuffName(concept.substring(7));
        }
        if (concept.startsWith("Move_")) {
            return Component.translatable("adaptionwheel.concept.move." + concept);
        }
        if (concept.startsWith("Mine_")) {
            return Component.translatable("adaptionwheel.concept.mine." + concept);
        }
        if (concept.startsWith("Fist_")) {
            return Component.translatable("adaptionwheel.concept.fist." + concept);
        }
        if (concept.startsWith("Combat_")) {
            return Component.translatable("adaptionwheel.concept.combat." + concept);
        }
        if (concept.startsWith("Percep_")) {
            return Component.translatable("adaptionwheel.concept.percep." + concept);
        }
        if (concept.equals(DIMENSION_DESTROY)) {
            return Component.translatable("adaptionwheel.concept.special." + concept);
        }
        if (concept.startsWith("Contact_")) {
            return entityName(concept.substring("Contact_".length()), "");
        }
        if (concept.startsWith("Offense_NPC_")) {
            return entityName(concept.substring("Offense_NPC_".length()), "");
        }
        if (concept.startsWith("Drop_NPC_")) {
            return entityName(concept.substring("Drop_NPC_".length()), " (DropRate)");
        }
        if (concept.startsWith("Existence_")) {
            return entityName(concept.substring("Existence_".length()), "");
        }
        return Component.literal(concept);
    }

    /**
     * Organizational domain of a concept (GUI/HUD grouping). Delegates to
     * {@link ru.adaptionwheel.adapt.AdaptationRegistry#domainOf(String)} so
     * dynamically registered concepts resolve consistently on both sides.
     */
    public static ru.adaptionwheel.adapt.AdaptationDomain domain(String concept) {
        return ru.adaptionwheel.adapt.AdaptationRegistry.domainOf(concept);
    }

    private static Component mutationName(String name) {
        return Component.translatable("adaptionwheel.concept.mutation." + name);
    }

    /** Per-material color for a {@code Fist_*} concept; falls back to the mining gray. */
    private static int fistColor(String tier) {
        for (int i = 0; i < FistTiers.TIER_COUNT; i++) {
            if (FistTiers.concept(i).equals("Fist_" + tier)) {
                return FistTiers.color(i);
            }
        }
        return COLOR_MINING;
    }

    private static Component debuffName(String effectPath) {
        ResourceLocation id = ResourceLocation.tryParse(effectPath);
        if (id != null && BuiltInRegistries.MOB_EFFECT.containsKey(id)) {
            return Component.translatable(BuiltInRegistries.MOB_EFFECT.get(id).getDescriptionId());
        }
        return Component.literal(effectPath);
    }

    private static Component entityName(String entityPart, String suffix) {
        ResourceLocation id = ResourceLocation.tryParse(entityPart);
        if (id != null && BuiltInRegistries.ENTITY_TYPE.containsKey(id)) {
            return Component.translatable(BuiltInRegistries.ENTITY_TYPE.get(id).getDescriptionId()).append(suffix);
        }
        return Component.literal(entityPart).append(suffix);
    }
}
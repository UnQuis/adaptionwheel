package ru.adaptionwheel.category;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

public final class Concepts {

    public static final String SELF_DAMAGE = "Self_Damage";
    public static final String ADVERSITY = "ADBERSITY";

    public static final String CONTACT_PREFIX = "Contact_";
    public static final String OFFENSE_PREFIX = "Offense_NPC_";
    public static final String DROP_PREFIX = "Drop_NPC_";
    public static final String EXISTENCE_PREFIX = "Existence_";
    public static final String ENV_PREFIX = "Env_";
    public static final String FIST_PREFIX = "Fist_";
    public static final String MINE_PREFIX = "Mine_";
    public static final String MOVE_PREFIX = "Move_";
    public static final String COMBAT_PREFIX = "Combat_";
    public static final String PERCEP_PREFIX = "Percep_";
    public static final String MUTATION_PREFIX = "Mutation_";
    public static final String TYPE_PREFIX = "Type_";
    public static final String DEBUFF_PREFIX = "Debuff_";
    public static final String PROJ_PREFIX = "Proj_";
    public static final String DIMENSION_PREFIX = "Dimension_";
    public static final String DAMAGE_CLASS_PREFIX = "DamageClass_";

    public static final String MUTATION_THERMAL = "Mutation_Thermal";
    public static final String MUTATION_AQUATIC = "Mutation_Aquatic";
    public static final String MUTATION_IMPACT = "Mutation_Impact";
    public static final String MUTATION_SEA_EYE = "Mutation_SeaEye";
    public static final String MUTATION_FLIGHT = "Mutation_Flight";

    public static final String MUTATION_FIST = "Mutation_Fist";

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
    public static final String ENV_INVENTORY = "Env_Inventory";

    public static final String MOVE_SOUL_SAND = "Move_SoulSand";
    public static final String MOVE_HONEY = "Move_Honey";
    public static final String MOVE_POWDER_SNOW = "Move_PowderSnow";
    public static final String MOVE_BERRY_BUSH = "Move_BerryBush";
    public static final String MOVE_BUBBLE_COLUMN = "Move_BubbleColumn";

    public static final String MINE_LABOR = "Mine_Labor";
    public static final String COMBAT_COOLDOWN = "Combat_Cooldown";
    public static final String COMBAT_SHIELD_LOCK = "Combat_ShieldLock";
    public static final String COMBAT_SKILL_ISSUE = "Combat_SkillIssue";
    public static final String PERCEP_STEADY_GAZE = "Percep_SteadyGaze";

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
    public static final int COLOR_EXISTENCE = 0xFFFFFFFF;
    public static final int COLOR_GENERIC = 0xFFFFFFFF;

    private Concepts() {
    }

    public static String type(AdaptionCategory category) {
        return "Type_" + category.name();
    }

    public static String contact(String mobPath) {
        return CONTACT_PREFIX + mobPath;
    }

    public static String offense(String mobPath) {
        return OFFENSE_PREFIX + mobPath;
    }

    public static String drop(String mobPath) {
        return DROP_PREFIX + mobPath;
    }

    /**
     * The concept key for a status effect.
     *
     * <p>Always namespaced, and a bare path is resolved against the effect registry so that callers
     * holding either form get the same key. Namespacing is not cosmetic: {@code getPath()} drops the
     * namespace, so {@code othermod:poison} and {@code minecraft:poison} both used to mint
     * {@code Debuff_poison} - two different effects silently sharing one adaptation - and the display
     * name only resolves for the namespaced form, because {@link Identifier#tryParse} rejects a
     * string with no colon and the bare key fell through to a raw lowercase literal.
     *
     * <p>This is the single place the shape of the key is decided. Two forms used to coexist: the
     * runtime minted bare keys and the flight gate and the altar named {@code Debuff_minecraft:...},
     * so the All Adaptation item granted levitation denial under one key while flight asked for the
     * other and never saw it. Normalising here rather than at each call site is what keeps that from
     * coming back.
     */
    public static String debuff(String effectId) {
        if (effectId.indexOf(':') >= 0) {
            return DEBUFF_PREFIX + effectId;
        }
        return DEBUFF_PREFIX + resolveEffectPath(effectId);
    }

    /**
     * Turns a bare effect path back into a full id by looking it up across every namespace.
     *
     * <p>Needed only for keys that were minted before debuff keys were namespaced, where the
     * namespace was already lost and has to be recovered. {@code minecraft} wins a tie so the common
     * case lands on the effect everyone means; an unknown path is namespaced to {@code minecraft}
     * anyway, because leaving it bare would keep it a second, unreachable spelling of the same key.
     */
    private static String resolveEffectPath(String path) {
        String fallback = null;
        for (MobEffect effect : BuiltInRegistries.MOB_EFFECT) {
            Identifier key = BuiltInRegistries.MOB_EFFECT.getKey(effect);
            if (key == null || !path.equals(key.getPath())) {
                continue;
            }
            if ("minecraft".equals(key.getNamespace())) {
                return key.toString();
            }
            if (fallback == null) {
                fallback = key.toString();
            }
        }
        return fallback != null ? fallback : Identifier.fromNamespaceAndPath("minecraft", path).toString();
    }

    /** The namespaced effect id for a live effect, the form every debuff concept key is built from. */
    public static String debuffId(Holder<MobEffect> effect) {
        Identifier key = effect.unwrapKey().map(ResourceKey::identifier).orElse(null);
        return key != null ? key.toString() : "minecraft:unknown";
    }

    /** The concept key for a live effect holder, the form the runtime analysis mints. */
    public static String debuff(Holder<MobEffect> effect) {
        return debuff(debuffId(effect));
    }

    public static String existence(String mobPath) {
        return EXISTENCE_PREFIX + mobPath;
    }

    public static boolean isLevelBased(String concept) {
        ru.adaptionwheel.adapt.AdaptationDefinition def = ru.adaptionwheel.adapt.AdaptationRegistry.get(concept);
        if (def != null) {
            return def.leveled();
        }
        return concept.startsWith(TYPE_PREFIX) || concept.startsWith(CONTACT_PREFIX)
                || concept.startsWith(OFFENSE_PREFIX) || concept.startsWith(DROP_PREFIX)
                || concept.equals(SELF_DAMAGE);
    }

    public static boolean isDrop(String concept) {
        return concept.startsWith(DROP_PREFIX);
    }

    public static boolean isOneTime(String concept) {
        ru.adaptionwheel.adapt.AdaptationDefinition def = ru.adaptionwheel.adapt.AdaptationRegistry.get(concept);
        if (def != null) {
            return !def.leveled();
        }
        return concept.startsWith(ENV_PREFIX) || concept.startsWith(DEBUFF_PREFIX)
                || concept.startsWith(EXISTENCE_PREFIX) || concept.startsWith(MUTATION_PREFIX)
                || concept.startsWith(MOVE_PREFIX) || concept.startsWith(PERCEP_PREFIX)
                || concept.equals(ADVERSITY);
    }

    public static boolean isOffense(String concept) {
        return concept.startsWith(OFFENSE_PREFIX);
    }

    private static int fistColor(String tier) {
        for (int i = 0; i < ru.adaptionwheel.category.FistTiers.TIER_COUNT; i++) {
            if (ru.adaptionwheel.category.FistTiers.concept(i).equals("Fist_" + tier)) {
                return ru.adaptionwheel.category.FistTiers.color(i);
            }
        }
        return COLOR_MINING;
    }

    public static int color(String concept) {
        if (concept.startsWith(CONTACT_PREFIX)) return COLOR_CONTACT;
        if (concept.startsWith(OFFENSE_PREFIX)) return COLOR_OFFENSE;
        if (concept.startsWith(DROP_PREFIX)) return COLOR_DROP;
        if (concept.startsWith(PROJ_PREFIX)) return COLOR_PROJECTILE;
        if (concept.startsWith(DEBUFF_PREFIX)) return COLOR_DEBUFF;
        if (concept.startsWith(ENV_PREFIX)) return COLOR_ENV;
        if (concept.startsWith(MOVE_PREFIX)) return COLOR_MOVEMENT;
        if (concept.startsWith(MINE_PREFIX)) return COLOR_MINING;
        if (concept.startsWith(FIST_PREFIX)) return fistColor(concept.substring("Fist_".length()));
        if (concept.startsWith(CombatFistTiers.CONCEPTS[0])) return combatFistColor(concept);
        if (concept.startsWith(COMBAT_PREFIX)) return COLOR_COMBAT;
        if (concept.startsWith(PERCEP_PREFIX)) return COLOR_PERCEPTION;
        if (concept.startsWith(MUTATION_PREFIX)) return COLOR_MUTATION;
        if (concept.startsWith(DIMENSION_PREFIX)) return COLOR_SPECIAL;
        if (concept.startsWith(DAMAGE_CLASS_PREFIX)) return COLOR_DAMAGE_CLASS;
        if (concept.equals(SELF_DAMAGE)) return COLOR_INJURE;
        if (concept.startsWith(EXISTENCE_PREFIX) || concept.equals(ADVERSITY)) return COLOR_EXISTENCE;
        return COLOR_GENERIC;
    }

    public static Component chatName(String concept) {
        if (concept.equals(SELF_DAMAGE)) {
            return Component.literal("Injure");
        }
        if (concept.startsWith(ENV_PREFIX)) {
            return Component.literal(concept.substring(4).toUpperCase());
        }
        if (concept.startsWith(MUTATION_PREFIX)) {
            return mutationName(concept.substring("Mutation_".length()));
        }
        if (concept.startsWith(DEBUFF_PREFIX)) {
            return debuffName(concept.substring(7));
        }
        if (concept.startsWith(MOVE_PREFIX)) {
            return Component.translatable("adaptionwheel.concept.move." + concept);
        }
        if (concept.startsWith(MINE_PREFIX)) {
            return Component.translatable("adaptionwheel.concept.mine." + concept);
        }
        if (concept.startsWith(COMBAT_PREFIX)) {
            return Component.translatable("adaptionwheel.concept.combat." + concept);
        }
        if (concept.startsWith(PERCEP_PREFIX)) {
            return Component.translatable("adaptionwheel.concept.percep." + concept);
        }
        if (concept.equals(DIMENSION_DESTROY)) {
            return Component.translatable("adaptionwheel.concept.special." + concept);
        }
        if (concept.startsWith(CONTACT_PREFIX)) {
            return entityName(concept.substring("Contact_".length()), " (Contact)");
        }
        if (concept.startsWith(OFFENSE_PREFIX)) {
            return entityName(concept.substring("Offense_NPC_".length()), "");
        }
        if (concept.startsWith(DROP_PREFIX)) {
            return entityName(concept.substring("Drop_NPC_".length()), " (DropRate)");
        }
        if (concept.startsWith(EXISTENCE_PREFIX)) {
            return entityName(concept.substring("Existence_".length()), "");
        }
        if (concept.startsWith(DAMAGE_CLASS_PREFIX)) {
            return Component.literal(concept.replace("DamageClass_", "").replace("DamageClass", "") + " Damage");
        }
        return displayName(concept);
    }

    public static Component displayName(String concept) {
        if (concept.equals(SELF_DAMAGE)) {
            return Component.translatable("adaptionwheel.concept.self_damage");
        }
        if (concept.equals(ADVERSITY)) {
            return Component.translatable("adaptionwheel.concept.adversity");
        }
        if (concept.startsWith(TYPE_PREFIX)) {
            return Component.translatable("adaptionwheel.concept.type." + concept.substring(5).toLowerCase());
        }
        if (concept.startsWith(ENV_PREFIX)) {
            return Component.translatable("adaptionwheel.concept.env." + concept);
        }
        if (concept.startsWith(MUTATION_PREFIX)) {
            return mutationName(concept.substring("Mutation_".length()));
        }
        if (concept.startsWith(DEBUFF_PREFIX)) {
            return debuffName(concept.substring(7));
        }
        if (concept.startsWith(MOVE_PREFIX)) {
            return Component.translatable("adaptionwheel.concept.move." + concept);
        }
        if (concept.startsWith(MINE_PREFIX)) {
            return Component.translatable("adaptionwheel.concept.mine." + concept);
        }
        if (concept.startsWith(COMBAT_PREFIX)) {
            return Component.translatable("adaptionwheel.concept.combat." + concept);
        }
        if (concept.startsWith(PERCEP_PREFIX)) {
            return Component.translatable("adaptionwheel.concept.percep." + concept);
        }
        if (concept.equals(DIMENSION_DESTROY)) {
            return Component.translatable("adaptionwheel.concept.special." + concept);
        }
        if (concept.startsWith(CONTACT_PREFIX)) {
            return entityName(concept.substring("Contact_".length()), "");
        }
        if (concept.startsWith(OFFENSE_PREFIX)) {
            return entityName(concept.substring("Offense_NPC_".length()), "");
        }
        if (concept.startsWith(DROP_PREFIX)) {
            return entityName(concept.substring("Drop_NPC_".length()), " (DropRate)");
        }
        if (concept.startsWith(EXISTENCE_PREFIX)) {
            return entityName(concept.substring("Existence_".length()), "");
        }
        return Component.literal(concept);
    }

    public static ru.adaptionwheel.adapt.AdaptationDomain domain(String concept) {
        return ru.adaptionwheel.adapt.AdaptationRegistry.domainOf(concept);
    }

    private static Component mutationName(String name) {
        return Component.translatable("adaptionwheel.concept.mutation." + name);
    }

    private static int combatFistColor(String concept) {
        for (int i = 0; i < CombatFistTiers.TIER_COUNT; i++) {
            if (CombatFistTiers.concept(i).equals(concept)) {
                return CombatFistTiers.color(i);
            }
        }
        return COLOR_COMBAT;
    }

    private static Component debuffName(String effectPath) {
        Identifier id = Identifier.tryParse(effectPath);
        if (id != null && BuiltInRegistries.MOB_EFFECT.containsKey(id)) {
            return Component.translatable(BuiltInRegistries.MOB_EFFECT.getValue(id).getDescriptionId());
        }
        return Component.literal(effectPath);
    }

    private static Component entityName(String entityPart, String suffix) {
        Identifier id = Identifier.tryParse(entityPart);
        if (id != null && BuiltInRegistries.ENTITY_TYPE.containsKey(id)) {
            return Component.translatable(BuiltInRegistries.ENTITY_TYPE.getValue(id).getDescriptionId()).append(suffix);
        }
        return Component.literal(entityPart).append(suffix);
    }
}

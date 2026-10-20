package ru.adaptionwheel.client;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import ru.adaptionwheel.adapt.AdaptationDomain;
import ru.adaptionwheel.adapt.AdaptationRegistry;
import ru.adaptionwheel.category.AdaptionCategory;
import ru.adaptionwheel.category.CombatFistTiers;
import ru.adaptionwheel.category.Concepts;
import ru.adaptionwheel.category.FistTiers;

import java.util.HashMap;
import java.util.Map;

/**
 * Representative vanilla icons for adaptation concepts.
 *
 * <p>The menu is meant to be read at a glance, so an icon says what kind of thing an adaptation is
 * before the two lines of text do. Spawn eggs are used for the per-entity families because the egg
 * is already a picture of the mob, and the domain fallback guarantees that a concept minted at
 * runtime (a debuff, a modded mob) still gets a usable tile rather than an empty square.
 *
 * <p>Only {@link Item}s are cached, never {@link ItemStack}s: a stack is mutable and handing the
 * same one to the renderer every frame is the kind of shared state that eventually grows a count.
 */
public final class ConceptIcons {

    private static final Map<String, Item> CACHE = new HashMap<>();

    private ConceptIcons() {
    }

    public static ItemStack icon(String concept) {
        Item item = item(concept);
        return item == null || item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
    }

    private static Item item(String concept) {
        if (concept == null || concept.isEmpty()) {
            return Items.NETHER_STAR;
        }
        return CACHE.computeIfAbsent(concept, ConceptIcons::resolve);
    }

    private static Item resolve(String concept) {
        if (concept.startsWith(Concepts.CONTACT_PREFIX)) {
            Item egg = egg(concept.substring(Concepts.CONTACT_PREFIX.length()));
            if (egg != null) {
                return egg;
            }
        }
        if (concept.startsWith(Concepts.OFFENSE_PREFIX)) {
            Item egg = egg(concept.substring(Concepts.OFFENSE_PREFIX.length()));
            if (egg != null) {
                return egg;
            }
        }
        if (concept.startsWith(Concepts.DROP_PREFIX)) {
            Item egg = egg(concept.substring(Concepts.DROP_PREFIX.length()));
            if (egg != null) {
                return egg;
            }
        }
        if (concept.startsWith(Concepts.EXISTENCE_PREFIX)) {
            String id = concept.substring(Concepts.EXISTENCE_PREFIX.length());
            return id.endsWith("ender_dragon") ? Items.DRAGON_EGG : Items.NETHER_STAR;
        }
        if (concept.startsWith(Concepts.DEBUFF_PREFIX)) {
            return debuff(concept.substring(Concepts.DEBUFF_PREFIX.length()));
        }
        if (concept.startsWith(Concepts.TYPE_PREFIX)) {
            return type(concept.substring(Concepts.TYPE_PREFIX.length()));
        }
        Item explicit = explicit(concept);
        if (explicit != null) {
            return explicit;
        }
        if (concept.startsWith(Concepts.FIST_PREFIX)) {
            for (int tier = 0; tier < FistTiers.TIER_COUNT; tier++) {
                if (ru.adaptionwheel.category.FistTiers.concept(tier).equals(concept)) {
                    return switch (tier) {
                        case 0 -> Items.WOODEN_PICKAXE;
                        case 1 -> Items.STONE_PICKAXE;
                        case 2 -> Items.IRON_PICKAXE;
                        case 3 -> Items.DIAMOND_PICKAXE;
                        default -> Items.NETHERITE_PICKAXE;
                    };
                }
            }
        }
        if (concept.startsWith("Combat_Fist")) {
            for (int tier = 0; tier < ru.adaptionwheel.category.CombatFistTiers.TIER_COUNT; tier++) {
                if (ru.adaptionwheel.category.CombatFistTiers.concept(tier).equals(concept)) {
                    return switch (tier) {
                        case 0 -> Items.OAK_PLANKS;
                        case 1 -> Items.COBBLESTONE;
                        case 2 -> Items.IRON_INGOT;
                        case 3 -> Items.DIAMOND;
                        default -> Items.NETHERITE_INGOT;
                    };
                }
            }
        }
        return fallback(AdaptationRegistry.domainOf(concept));
    }

    private static Item explicit(String concept) {
        return switch (concept) {
            case Concepts.SELF_DAMAGE -> Items.REDSTONE;
            case Concepts.ADVERSITY -> Items.TOTEM_OF_UNDYING;
            case Concepts.ENV_LAVA -> Items.LAVA_BUCKET;
            case Concepts.ENV_DROWN -> Items.WATER_BUCKET;
            case Concepts.ENV_THORNS -> Items.SWEET_BERRIES;
            case Concepts.ENV_DARKNESS -> Items.TORCH;
            case Concepts.ENV_SUFFOCATE -> Items.SAND;
            case Concepts.ENV_VOID -> Items.ENDER_PEARL;
            case Concepts.ENV_STARVE -> Items.ROTTEN_FLESH;
            case Concepts.ENV_LIQUID -> Items.WATER_BUCKET;
            case Concepts.ENV_ICE -> Items.ICE;
            case Concepts.ENV_SLIME -> Items.SLIME_BALL;
            case Concepts.ENV_COBWEB -> Items.COBWEB;
            case Concepts.ENV_KNOCKBACK -> Items.PISTON;
            case Concepts.ENV_FALL -> Items.FEATHER;
            case Concepts.ENV_INVENTORY -> Items.CHEST;
            case Concepts.MOVE_SOUL_SAND -> Items.SOUL_SAND;
            case Concepts.MOVE_HONEY -> Items.HONEY_BOTTLE;
            case Concepts.MOVE_POWDER_SNOW -> Items.POWDER_SNOW_BUCKET;
            case Concepts.MOVE_BERRY_BUSH -> Items.SWEET_BERRIES;
            case Concepts.MOVE_BUBBLE_COLUMN -> Items.KELP;
            case Concepts.MINE_LABOR -> Items.IRON_PICKAXE;
            case Concepts.COMBAT_COOLDOWN -> Items.IRON_SWORD;
            case Concepts.COMBAT_SHIELD_LOCK -> Items.SHIELD;
            case Concepts.COMBAT_SKILL_ISSUE -> Items.BOW;
            case Concepts.PERCEP_STEADY_GAZE -> Items.ENDER_EYE;
            case Concepts.MUTATION_THERMAL -> Items.MAGMA_CREAM;
            case Concepts.MUTATION_AQUATIC -> Items.HEART_OF_THE_SEA;
            case Concepts.MUTATION_IMPACT -> Items.ANVIL;
            case Concepts.MUTATION_SEA_EYE -> Items.PRISMARINE_SHARD;
            case Concepts.MUTATION_FLIGHT -> Items.PHANTOM_MEMBRANE;
            case Concepts.MUTATION_FIST -> Items.NETHERITE_INGOT;
            case Concepts.DIMENSION_DESTROY -> Items.END_CRYSTAL;
            default -> null;
        };
    }

    private static Item type(String category) {
        AdaptionCategory parsed;
        try {
            parsed = AdaptionCategory.valueOf(category.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return Items.NETHER_STAR;
        }
        return switch (parsed) {
            case FALL -> Items.FEATHER;
            case WITHER -> Items.WITHER_ROSE;
            case FIRE -> Items.BLAZE_POWDER;
            case DROWN -> Items.WATER_BUCKET;
            case FREEZE -> Items.POWDER_SNOW_BUCKET;
            case LIGHTNING -> Items.LIGHTNING_ROD;
            case EXPLOSION -> Items.TNT;
            case PROJECTILE -> Items.SPECTRAL_ARROW;
            case MAGIC -> Items.ENCHANTED_BOOK;
            case MOB -> Items.IRON_SWORD;
            case PLAYER -> Items.PLAYER_HEAD;
            case CONTACT -> Items.CACTUS;
            case STARVE -> Items.ROTTEN_FLESH;
            case SUFFOCATE -> Items.SAND;
            case VOID -> Items.ENDER_PEARL;
            case OTHER -> Items.NETHER_STAR;
        };
    }

    private static Item debuff(String effectId) {
        ResourceLocation id = ResourceLocation.tryParse(effectId);
        String path = id == null ? effectId : id.getPath();
        return switch (path) {
            case "poison" -> Items.SPIDER_EYE;
            case "wither" -> Items.WITHER_ROSE;
            case "levitation" -> Items.SHULKER_SHELL;
            case "slowness" -> Items.SOUL_SAND;
            case "weakness" -> Items.FERMENTED_SPIDER_EYE;
            case "blindness" -> Items.INK_SAC;
            case "darkness" -> Items.SCULK_SENSOR;
            case "nausea" -> Items.PUFFERFISH;
            case "hunger" -> Items.ROTTEN_FLESH;
            case "mining_fatigue" -> Items.WOODEN_PICKAXE;
            case "instant_damage" -> Items.REDSTONE;
            case "regeneration" -> Items.GLISTERING_MELON_SLICE;
            case "fire_resistance" -> Items.MAGMA_CREAM;
            case "water_breathing" -> Items.TURTLE_HELMET;
            case "absorption" -> Items.GOLDEN_APPLE;
            case "resistance" -> Items.SHIELD;
            case "jump_boost" -> Items.RABBIT_FOOT;
            case "speed" -> Items.SUGAR;
            case "haste" -> Items.GOLDEN_PICKAXE;
            default -> Items.GLASS_BOTTLE;
        };
    }

    private static Item fallback(AdaptationDomain domain) {
        return switch (domain) {
            case DAMAGE -> Items.IRON_SWORD;
            case EFFECT -> Items.GLASS_BOTTLE;
            case MOVEMENT -> Items.LEATHER_BOOTS;
            case PHYSICS -> Items.PISTON;
            case MINING -> Items.IRON_PICKAXE;
            case ENVIRONMENT -> Items.GRASS_BLOCK;
            case COMBAT -> Items.IRON_SWORD;
            case PERCEPTION -> Items.ENDER_EYE;
            case ENTITY -> Items.ZOMBIE_SPAWN_EGG;
            case EXISTENCE -> Items.NETHER_STAR;
            case SPECIAL -> Items.NETHER_STAR;
        };
    }

    private static Item egg(String entityId) {
        ResourceLocation id = ResourceLocation.tryParse(entityId);
        if (id == null) {
            return null;
        }
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(id);
        if (type == null) {
            return null;
        }
        SpawnEggItem egg = SpawnEggItem.byId(type);
        return egg == null ? null : egg;
    }
}

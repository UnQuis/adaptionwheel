package ru.adaptionwheel.category;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.IntUnaryOperator;

@SuppressWarnings("unchecked")
public final class FistTiers {

    public static final int TIER_COUNT = 5;

    public static final String[] CONCEPTS = {
            "Fist_Wood", "Fist_Stone", "Fist_Iron", "Fist_Diamond", "Fist_Netherite"
    };

    private static final TagKey<Block>[] TAGS = new TagKey[TIER_COUNT];

    private static final String[] MATERIALS = {"wooden", "stone", "iron", "diamond", "netherite"};

    private static final String[] TOOL_CLASSES = {"pickaxe", "axe", "shovel", "hoe"};

    private static final float[] LUCK = {1.0f, 2.0f, 3.0f, 5.0f, 10.0f};

    private static final float[][] DECLARED_SPEED = new float[TIER_COUNT][TOOL_CLASSES.length];

    private static final BlockState REFERENCE_BLOCK =
            net.minecraft.world.level.block.Blocks.STONE.defaultBlockState();

    private static final TagKey<Block> LUCK_TAG = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(ru.adaptionwheel.AdaptionWheel.MODID, "fist_luck"));

    public static final int[] COLORS = {
            0xFFA9784A,
            0xFF9A9A9A,
            0xFFDCDCDC,
            0xFF4FE3D8,
            0xFF6B4A78
    };

    static {
        for (int i = 0; i < TIER_COUNT; i++) {
            TAGS[i] = TagKey.create(Registries.BLOCK,
                    ResourceLocation.fromNamespaceAndPath(ru.adaptionwheel.AdaptionWheel.MODID,
                            "fist_" + suffix(i)));
        }
    }

    private FistTiers() {
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

    public static String toolId(int tier, String toolClass) {
        return "minecraft:" + MATERIALS[tier] + "_" + toolClass;
    }

    public static String toolId(int tier) {
        return toolId(tier, "pickaxe");
    }

    public static String concept(int tier) {
        return CONCEPTS[tier];
    }

    public static TagKey<Block> tag(int tier) {
        return TAGS[tier];
    }

    /**
     * Whether the bar should split into the previous stage's colour at this point.
     *
     * <p>The rule lives here, and not in the HUD, so a gametest can pin it without a client
     * {@code Font}. It is deliberately narrow: a stage is eight levels and the next begins at one, so
     * level 1 of a stage whose predecessor is maxed is the single level on which the player has
     * crossed a material boundary. Every other level returns false -- and that half of the rule is
     * the part that matters, because a bar that is always two-tone is just a second colour scheme
     * and marks nothing.
     *
     * <p>Both fists use this. They share the stage count (pinned by {@code CombatFistTests}), and a
     * rule duplicated across the two would be one that could eventually disagree about which of
     * them is five stages.
     *
     * @param level         the current stage's level, 1-based
     * @param previousLevel the previous stage's level, or 0 when there is no previous stage
     * @return true only on the level where a stage hands over to the next
     */
    public static boolean showsHandover(int level, int previousLevel) {
        return level == 1 && previousLevel >= ru.adaptionwheel.data.PlayerAdaption.MAX_LEVEL;
    }

    public static int color(int tier) {
        return COLORS[tier];
    }

    public static float declaredSpeed(Item tool) {
        Tool component = tool.getDefaultInstance().get(DataComponents.TOOL);
        if (component == null) {
            return 1.0f;
        }
        float best = 0f;
        for (Tool.Rule rule : component.rules()) {
            best = Math.max(best, rule.speed().orElse(0f));
        }
        return best > 0f ? best : component.defaultMiningSpeed();
    }

    public static float vanillaMiningSpeed(int tier) {
        return vanillaMiningSpeed(tier, REFERENCE_BLOCK);
    }

    public static float vanillaMiningSpeed(int tier, BlockState target) {
        if (tier < 0 || tier >= TIER_COUNT) {
            return 1.0f;
        }
        float best = 0f;
        for (int c = 0; c < TOOL_CLASSES.length; c++) {
            Item tool = toolOf(tier, c);
            if (tool == null) {
                continue;
            }

            if (tool.getDestroySpeed(tool.getDefaultInstance(), target) <= 1.0f) {
                continue;
            }
            best = Math.max(best, declaredSpeedOf(tier, c));
        }
        if (best > 1.0f) {
            return best;
        }

        float own = declaredSpeedOf(tier, 0);
        if (own > 1.0f) {
            return own;
        }

        return tier == 0 ? 1.0f : vanillaMiningSpeed(tier - 1, target);
    }

    private static Item toolOf(int tier, int classIndex) {
        return BuiltInRegistries.ITEM
                .getOptional(ResourceLocation.parse(toolId(tier, TOOL_CLASSES[classIndex])))
                .orElse(null);
    }

    private static float declaredSpeedOf(int tier, int classIndex) {
        float cached = DECLARED_SPEED[tier][classIndex];
        if (cached > 0f) {
            return cached;
        }
        Item tool = toolOf(tier, classIndex);
        float speed = tool == null ? 1.0f : declaredSpeed(tool);
        DECLARED_SPEED[tier][classIndex] = speed;
        return speed;
    }

    public static int luckMultiplier(int tier) {
        if (tier < 0 || tier >= TIER_COUNT) {
            return 1;
        }
        return (int) LUCK[tier];
    }

    public static TagKey<Block> luckTag() {
        return LUCK_TAG;
    }

    public static int tierOf(BlockState state) {
        for (int i = 0; i < TIER_COUNT; i++) {
            if (state.is(TAGS[i])) {
                return i;
            }
        }
        return -1;
    }

    public static boolean canHarvest(BlockState state, int tier) {
        int required = tierOf(state);
        return required < 0 || required <= tier;
    }

    public static int reachTier(IntUnaryOperator levelOf) {
        int maxLevel = ru.adaptionwheel.data.PlayerAdaption.MAX_LEVEL;
        int progression = 0;
        while (progression < TIER_COUNT - 1 && levelOf.applyAsInt(progression) >= maxLevel) {
            progression++;
        }
        int granted = 0;
        for (int i = TIER_COUNT - 1; i >= 0; i--) {
            if (levelOf.applyAsInt(i) > 0) {
                granted = i;
                break;
            }
        }
        return Math.max(progression, granted);
    }

    public static boolean usableWith(ItemStack stack, BlockState target) {
        if (stack.isEmpty()) {
            return true;
        }
        if (stack.get(DataComponents.TOOL) != null) {
            return false;
        }
        if (dealsExtraAttackDamage(stack)) {
            return false;
        }
        return stack.getItem().getDestroySpeed(stack, target) <= 1.0f;
    }

    public static boolean dealsExtraAttackDamage(ItemStack stack) {
        boolean[] weapon = {false};
        stack.forEachModifier(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
            if (attribute.is(Attributes.ATTACK_DAMAGE) && modifier.amount() > 0.0) {
                weapon[0] = true;
            }
        });
        return weapon[0];
    }
}

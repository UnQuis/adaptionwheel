package ru.adaptionwheel;

import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import ru.adaptionwheel.category.Concepts;
import ru.adaptionwheel.server.AdaptionEvents;

/**
 * Cross-side adaptation checks used by mixins.
 * Client state lives in {@link ru.adaptionwheel.client.ClientAdaption} and is
 * touched only through {@link ClientChecks} so the class is never loaded on the server.
 */
public final class SurfaceAdaptations {

    private SurfaceAdaptations() {
    }

    /** True when this ice/slime block should behave like normal ground for the player. */
    public static boolean wantsGroundFriction(Player player, BlockState state) {
        if (!player.onGround()) {
            return false;
        }
        if (state.is(BlockTags.ICE)) {
            return has(player, Concepts.ENV_ICE);
        }
        if (state.is(Blocks.SLIME_BLOCK)) {
            return has(player, Concepts.ENV_SLIME);
        }
        return false;
    }

    public static boolean ignoresSlimePenalty(Player player) {
        return has(player, Concepts.ENV_SLIME);
    }

    public static boolean movesThroughWebs(Player player) {
        return has(player, Concepts.ENV_COBWEB);
    }

    /** Env_Liquid: swim like water-native — also removes the underwater mining penalty. */
    public static boolean hasLiquidAdaptation(Player player) {
        return has(player, Concepts.ENV_LIQUID);
    }

    // ================= Adaptation to Discomfort (movement domain) =================

    /**
     * Returns a neutralized speed factor for the given block, or null to keep
     * vanilla behavior. Extend this mapping to cover modded blocks: register a
     * new Move_* concept and map its block here.
     */
    public static Float neutralizedSpeedFactor(Player player, net.minecraft.world.level.block.Block block) {
        if (block == Blocks.SOUL_SAND) {
            return has(player, Concepts.MOVE_SOUL_SAND) ? Float.valueOf(1.0F) : null;
        }
        if (block == Blocks.HONEY_BLOCK) {
            return has(player, Concepts.MOVE_HONEY) ? Float.valueOf(1.0F) : null;
        }
        return null;
    }

    /** Honey also dampens jump height; adapted players keep their full jump. */
    public static boolean keepsFullJump(Player player, net.minecraft.world.level.block.Block block) {
        return block == Blocks.HONEY_BLOCK && has(player, Concepts.MOVE_HONEY);
    }

    /** Move_PowderSnow: walk on top of powder snow instead of sinking into it. */
    public static boolean walksOnPowderSnow(Player player) {
        return has(player, Concepts.MOVE_POWDER_SNOW);
    }

    /** Move_BerryBush: bushes neither snag nor cut the adapted player. */
    public static boolean movesThroughBerryBush(Player player) {
        return has(player, Concepts.MOVE_BERRY_BUSH);
    }

    /** Move_BubbleColumn: columns cannot drag down or launch the adapted player. */
    public static boolean controlsBubbleColumns(Player player) {
        return has(player, Concepts.MOVE_BUBBLE_COLUMN);
    }

    /** Mutation_Aquatic: dolphin-grade swim speed and faster underwater mining. */
    public static boolean hasAquaticMastery(Player player) {
        return has(player, Concepts.MUTATION_AQUATIC);
    }

    // ================= Mining / Combat / Perception domain checks =================

    /**
     * Level of a leveled concept, resolved on whichever side is asking
     * (client reads the synced mirror; server reads the authoritative data).
     * Returns 0 when the wheel is not worn.
     */
    public static int conceptLevel(Player player, String concept) {
        if (player.level().isClientSide) {
            return ClientChecks.level(concept);
        }
        return ru.adaptionwheel.server.AdaptionEvents.conceptLevel(player, concept);
    }

    /** Combat_ShieldLock: the wearer's shield can no longer be disabled (axes included). */
    public static boolean keepsShieldUp(Player player) {
        return has(player, Concepts.COMBAT_SHIELD_LOCK);
    }

    private static boolean has(Player player, String concept) {
        if (player.level().isClientSide) {
            return ClientChecks.has(concept);
        }
        return AdaptionEvents.hasAdaptation(player, concept);
    }
}

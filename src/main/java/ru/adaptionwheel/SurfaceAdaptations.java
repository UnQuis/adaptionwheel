package ru.adaptionwheel;

import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import ru.adaptionwheel.category.Concepts;
import ru.adaptionwheel.server.AdaptionEvents;

public final class SurfaceAdaptations {

    private SurfaceAdaptations() {
    }

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

    public static boolean hasLiquidAdaptation(Player player) {
        return has(player, Concepts.ENV_LIQUID);
    }

    public static Float neutralizedSpeedFactor(Player player, net.minecraft.world.level.block.Block block) {
        if (block == Blocks.SOUL_SAND) {
            return has(player, Concepts.MOVE_SOUL_SAND) ? Float.valueOf(1.0F) : null;
        }
        if (block == Blocks.HONEY_BLOCK) {
            return has(player, Concepts.MOVE_HONEY) ? Float.valueOf(1.0F) : null;
        }
        return null;
    }

    public static boolean keepsFullJump(Player player, net.minecraft.world.level.block.Block block) {
        return block == Blocks.HONEY_BLOCK && has(player, Concepts.MOVE_HONEY);
    }

    public static boolean walksOnPowderSnow(Player player) {
        return has(player, Concepts.MOVE_POWDER_SNOW);
    }

    public static boolean movesThroughBerryBush(Player player) {
        return has(player, Concepts.MOVE_BERRY_BUSH);
    }

    public static boolean controlsBubbleColumns(Player player) {
        return has(player, Concepts.MOVE_BUBBLE_COLUMN);
    }

    public static boolean hasAquaticMastery(Player player) {
        return has(player, Concepts.MUTATION_AQUATIC);
    }

    public static int conceptLevel(Player player, String concept) {
        if (player.level().isClientSide) {
            return ClientChecks.level(concept);
        }
        return ru.adaptionwheel.server.AdaptionEvents.conceptLevel(player, concept);
    }

    public static boolean keepsShieldUp(Player player) {
        return has(player, Concepts.COMBAT_SHIELD_LOCK);
    }

    public static boolean wearingWheel(Player player) {
        if (player.level().isClientSide) {
            return ru.adaptionwheel.client.ClientAdaption.wearingWheel;
        }
        return ru.adaptionwheel.server.AdaptionEvents.isWearingWheel(player);
    }

    public static int fistTier(Player player) {
        if (player.level().isClientSide) {
            return ru.adaptionwheel.client.ClientAdaption.fistTier();
        }
        if (!(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)) {
            return -1;
        }
        return ru.adaptionwheel.server.FistMastery.currentTier(
                ru.adaptionwheel.server.AdaptionEvents.dataOf(serverPlayer));
    }

    public static int fistLevel(Player player, int tier) {
        if (tier < 0) {
            return 0;
        }
        return conceptLevel(player, ru.adaptionwheel.category.FistTiers.concept(tier));
    }

    public static boolean instabreakUnlocked(Player player) {
        if (!ru.adaptionwheel.config.AdaptionConfig.FIST_INSTABREAK_ENABLED.get()) {
            return false;
        }
        if (!has(player, Concepts.MUTATION_FIST)) {
            return false;
        }
        int last = ru.adaptionwheel.category.FistTiers.TIER_COUNT - 1;
        return conceptLevel(player, ru.adaptionwheel.category.FistTiers.concept(last))
                >= ru.adaptionwheel.data.PlayerAdaption.MAX_LEVEL;
    }

    public static boolean instabreakActive(Player player) {
        if (player.level().isClientSide) {
            return ru.adaptionwheel.client.ClientAdaption.instabreakActive;
        }
        return player instanceof net.minecraft.server.level.ServerPlayer serverPlayer
                && ru.adaptionwheel.server.FistMastery.instabreakStance(serverPlayer);
    }

    private static boolean has(Player player, String concept) {
        if (player.level().isClientSide) {
            return ClientChecks.has(concept);
        }
        return AdaptionEvents.hasAdaptation(player, concept);
    }
}

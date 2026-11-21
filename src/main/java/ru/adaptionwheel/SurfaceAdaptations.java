package ru.adaptionwheel;

import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import ru.adaptionwheel.category.Concepts;
import ru.adaptionwheel.server.AdaptionEvents;

public final class SurfaceAdaptations {

    private SurfaceAdaptations() {
    }

    public static boolean wantsGroundFriction(LivingEntity entity, BlockState state) {
        if (!entity.onGround()) {
            return false;
        }
        if (state.is(BlockTags.ICE)) {
            return has(entity, Concepts.ENV_ICE);
        }
        if (state.is(Blocks.SLIME_BLOCK)) {
            return has(entity, Concepts.ENV_SLIME);
        }
        return false;
    }

    public static boolean ignoresSlimePenalty(LivingEntity entity) {
        return has(entity, Concepts.ENV_SLIME);
    }

    public static boolean movesThroughWebs(LivingEntity entity) {
        return has(entity, Concepts.ENV_COBWEB);
    }

    public static boolean hasLiquidAdaptation(LivingEntity entity) {
        return has(entity, Concepts.ENV_LIQUID);
    }

    /**
     * Whether lava should behave like water for this wearer.
     *
     * <p>This is the {@code Env_Lava} adaptation, and only that one: liquid adaptation is about
     * water and Aquatic Mastery, and neither of them was ever the ability to swim in lava.
     */
    public static boolean swimsInLava(LivingEntity entity) {
        return has(entity, Concepts.ENV_LAVA);
    }

    public static Float neutralizedSpeedFactor(LivingEntity entity, net.minecraft.world.level.block.Block block) {
        if (block == Blocks.SOUL_SAND) {
            return has(entity, Concepts.MOVE_SOUL_SAND) ? Float.valueOf(1.0F) : null;
        }
        if (block == Blocks.HONEY_BLOCK) {
            return has(entity, Concepts.MOVE_HONEY) ? Float.valueOf(1.0F) : null;
        }
        return null;
    }

    public static boolean keepsFullJump(LivingEntity entity, net.minecraft.world.level.block.Block block) {
        return block == Blocks.HONEY_BLOCK && has(entity, Concepts.MOVE_HONEY);
    }

    public static boolean walksOnPowderSnow(LivingEntity entity) {
        return has(entity, Concepts.MOVE_POWDER_SNOW);
    }

    public static boolean movesThroughBerryBush(LivingEntity entity) {
        return has(entity, Concepts.MOVE_BERRY_BUSH);
    }

    public static boolean controlsBubbleColumns(LivingEntity entity) {
        return has(entity, Concepts.MOVE_BUBBLE_COLUMN);
    }

    public static boolean hasAquaticMastery(LivingEntity entity) {
        return has(entity, Concepts.MUTATION_AQUATIC);
    }

    public static int conceptLevel(LivingEntity entity, String concept) {
        if (entity.level().isClientSide) {
            // Only the local player has a client mirror. A mob is never rendered through it, so it
            // answers zero instead of borrowing whichever player happens to be in the client.
            return entity instanceof Player ? ClientChecks.level(concept) : 0;
        }
        return AdaptionEvents.conceptLevel(entity, concept);
    }

    public static boolean keepsShieldUp(LivingEntity entity) {
        return has(entity, Concepts.COMBAT_SHIELD_LOCK);
    }

    public static boolean wearingWheel(LivingEntity entity) {
        if (entity.level().isClientSide) {
            return entity instanceof Player && ru.adaptionwheel.client.ClientAdaption.wearingWheel;
        }
        return AdaptionEvents.isWearingWheel(entity);
    }

    public static int fistTier(LivingEntity entity) {
        if (!(entity instanceof Player player)) {
            // The two fists are player progression; a mob has neither ladder.
            return -1;
        }
        if (entity.level().isClientSide) {
            // Mirror the server branch exactly: this is an EFFECT (it feeds FistMastery.breakSpeed),
            // so the switch is applied here. ClientAdaption.fistTier() itself stays raw because the
            // HUD row must keep showing a switched-off stage. Without these gates the client kept
            // mining at full fist speed while the server applied none, so the client predicted the
            // break and the ack snapped every block back into place (client-faster-than-server).
            if (!ru.adaptionwheel.client.ClientAdaption.active(Concepts.MUTATION_FIST)) {
                return -1;
            }
            int tier = ru.adaptionwheel.client.ClientAdaption.fistTier();
            // The gate on the tier concept MUST be isEnabled(), not active(): the stage is a
            // LEVELED concept, leveled concepts live only in the levels map and never enter the
            // adapted set (see AdaptionEvents.applyGrant), so active(Fist_*) is always false.
            // active() is only correct for the one-time mutation checked above.
            return tier >= 0
                    && ru.adaptionwheel.client.ClientAdaption.isEnabled(
                            ru.adaptionwheel.category.FistTiers.concept(tier)) ? tier : -1;
        }
        // Same split as FistLuck: currentTier is the permanent "which stage was reached" question,
        // and this is an effect, so the switch is applied here. The gate is isEnabled() and NOT
        // active(): the tier is leveled and active(Fist_*) is always false, so this check used to
        // return -1 on the server no matter what the player held while the raw client ran at full
        // fist speed — the client-faster-than-server shape that made every break snap back.
        if (!(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)) {
            return -1;
        }
        var data = AdaptionEvents.dataOf(serverPlayer);
        int tier = ru.adaptionwheel.server.FistMastery.currentTier(data);
        return tier >= 0 && data.isEnabled(ru.adaptionwheel.category.FistTiers.concept(tier)) ? tier : -1;
    }

    public static boolean instabreakUnlocked(LivingEntity entity) {
        if (!(entity instanceof Player)) {
            return false;
        }
        if (!ru.adaptionwheel.config.AdaptionConfig.FIST_INSTABREAK_ENABLED.get()) {
            return false;
        }
        if (!has(entity, Concepts.MUTATION_FIST)) {
            return false;
        }
        int last = ru.adaptionwheel.category.FistTiers.TIER_COUNT - 1;
        return conceptLevel(entity, ru.adaptionwheel.category.FistTiers.concept(last))
                >= ru.adaptionwheel.data.PlayerAdaption.MAX_LEVEL;
    }

    public static boolean instabreakActive(LivingEntity entity) {
        if (!(entity instanceof Player player)) {
            return false;
        }
        if (entity.level().isClientSide) {
            return ru.adaptionwheel.client.ClientAdaption.instabreakActive;
        }
        return player instanceof net.minecraft.server.level.ServerPlayer serverPlayer
                && ru.adaptionwheel.server.FistMastery.instabreakStance(serverPlayer);
    }

    private static boolean has(LivingEntity entity, String concept) {
        if (entity.level().isClientSide) {
            // The client mirror follows the local player only; a mob has no mirror to read.
            return entity instanceof Player && ClientChecks.has(concept);
        }
        return AdaptionEvents.hasAdaptation(entity, concept);
    }
}

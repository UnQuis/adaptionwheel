package ru.adaptionwheel.server;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import ru.adaptionwheel.AdaptionWheel;
import ru.adaptionwheel.category.Concepts;
import ru.adaptionwheel.config.AdaptionConfig;
import ru.adaptionwheel.data.PlayerAdaption;

@EventBusSubscriber(modid = AdaptionWheel.MODID)
public final class MovementTriggers {

    private MovementTriggers() {
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)
                || player.level().isClientSide || player.isDeadOrDying()) {
            return;
        }
        if (!AdaptionConfig.ENABLE_MOVEMENT.get()) {
            return;
        }
        if (!AdaptionEvents.isWearingWheel(player)) {
            return;
        }
        if (player.tickCount % 4 != 0) {
            return;
        }

        PlayerAdaption data = AdaptionEvents.dataOf(player);
        int envTicks = (int) (AdaptionConfig.ENV_ANALYSIS_SECONDS.get() * 20);

        BlockState below = player.level().getBlockState(player.getBlockPosBelowThatAffectsMyMovement());
        if (below.is(BlockTags.SOUL_SPEED_BLOCKS)) {
            AdaptionEvents.startTask(player, data, Concepts.MOVE_SOUL_SAND, envTicks);
        } else if (below.is(Blocks.HONEY_BLOCK) || player.level().getBlockState(player.blockPosition()).is(Blocks.HONEY_BLOCK)) {
            AdaptionEvents.startTask(player, data, Concepts.MOVE_HONEY, envTicks);
        }
        if (player.isInPowderSnow) {
            AdaptionEvents.startTask(player, data, Concepts.MOVE_POWDER_SNOW, envTicks);
        }
        if (isInBerryBush(player)) {
            AdaptionEvents.startTask(player, data, Concepts.MOVE_BERRY_BUSH, envTicks);
        }
        if (isInBubbleColumn(player)) {
            AdaptionEvents.startTask(player, data, Concepts.MOVE_BUBBLE_COLUMN, envTicks);
        }
    }

    private static boolean isInBerryBush(net.minecraft.server.level.ServerPlayer player) {
        AABB bb = player.getBoundingBox();
        for (BlockPos pos : BlockPos.betweenClosed(
                Mth.floor(bb.minX + 1.0E-7), Mth.floor(bb.minY + 1.0E-7), Mth.floor(bb.minZ + 1.0E-7),
                Mth.floor(bb.maxX - 1.0E-7), Mth.floor(bb.maxY - 1.0E-7), Mth.floor(bb.maxZ - 1.0E-7))) {
            if (player.level().getBlockState(pos).is(Blocks.SWEET_BERRY_BUSH)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isInBubbleColumn(net.minecraft.server.level.ServerPlayer player) {
        return player.level().getBlockState(player.blockPosition()).is(Blocks.BUBBLE_COLUMN)
                || player.level().getBlockState(BlockPos.containing(player.getEyePosition())).is(Blocks.BUBBLE_COLUMN);
    }
}

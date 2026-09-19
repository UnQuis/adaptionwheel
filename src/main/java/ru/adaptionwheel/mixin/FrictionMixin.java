package ru.adaptionwheel.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import ru.adaptionwheel.SurfaceAdaptations;

/**
 * Adapted players treat ice and slime like normal ground: the surface friction
 * is clamped to 0.6 so vanilla acceleration and momentum math applies unchanged.
 */
@Mixin(LivingEntity.class)
public abstract class FrictionMixin {

    @WrapOperation(
            method = "travelInAir",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/state/BlockState;getFriction(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/Entity;)F"))
    private float adaptionwheel$groundFriction(BlockState state, LevelReader level, BlockPos pos, Entity entity,
                                               Operation<Float> original) {
        float friction = original.call(state, level, pos, entity);
        if (friction > 0.6F && entity instanceof Player player
                && SurfaceAdaptations.wantsGroundFriction(player, state)) {
            return 0.6F;
        }
        return friction;
    }
}

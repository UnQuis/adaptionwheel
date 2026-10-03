package ru.adaptionwheel.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import ru.adaptionwheel.SurfaceAdaptations;

@Mixin(Entity.class)
public abstract class SlimeBounceMixin {

    @WrapOperation(
            method = "restituteMovementAfterCollisions(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;ZZLnet/minecraft/world/phys/Vec3;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;getBlockBounciness(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)D"))
    private double adaptionwheel$noSlimeBounce(Entity self, BlockPos pos, BlockState state, Operation<Double> original) {
        if (self instanceof Player player && state.is(Blocks.SLIME_BLOCK)
                && SurfaceAdaptations.ignoresSlimePenalty(player)) {
            return 0.0;
        }
        return original.call(self, pos, state);
    }
}

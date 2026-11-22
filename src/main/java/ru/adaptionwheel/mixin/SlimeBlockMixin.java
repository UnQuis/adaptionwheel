package ru.adaptionwheel.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SlimeBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.adaptionwheel.SurfaceAdaptations;

@Mixin(SlimeBlock.class)
public abstract class SlimeBlockMixin {

    @Inject(method = "stepOn", at = @At("HEAD"), cancellable = true)
    private void adaptionwheel$noSlowdown(Level level, BlockPos pos, BlockState state, Entity entity, CallbackInfo ci) {
        if (entity instanceof LivingEntity living
                && !living.isSteppingCarefully()
                && SurfaceAdaptations.ignoresSlimePenalty(living)) {
            ci.cancel();
        }
    }

    @Inject(method = "updateEntityAfterFallOn", at = @At("HEAD"), cancellable = true)
    private void adaptionwheel$noBounce(BlockGetter level, Entity entity, CallbackInfo ci) {
        if (entity instanceof LivingEntity living && SurfaceAdaptations.ignoresSlimePenalty(living)) {
            ci.cancel();
        }
    }
}

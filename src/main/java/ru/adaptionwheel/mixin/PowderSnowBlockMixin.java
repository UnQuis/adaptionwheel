package ru.adaptionwheel.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.PowderSnowBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.adaptionwheel.SurfaceAdaptations;

/**
 * Move_PowderSnow: adapted players stand ON powder snow like leather-boots
 * wearers ({@code canEntityWalkOnPowderSnow}) and sinking into it no longer
 * drags them down or accumulates freezing (entityInside is skipped entirely,
 * which also prevents {@code setIsInPowderSnow(true)} from ever applying).
 */
@Mixin(PowderSnowBlock.class)
public abstract class PowderSnowBlockMixin {

    @Inject(method = "entityInside", at = @At("HEAD"), cancellable = true)
    private void adaptionwheel$noSwallow(net.minecraft.world.level.block.state.BlockState state,
                                         net.minecraft.world.level.Level level,
                                         net.minecraft.core.BlockPos pos,
                                         Entity entity,
                                         net.minecraft.world.entity.InsideBlockEffectApplier effectApplier,
                                         boolean isPrecise, CallbackInfo ci) {
        if (entity instanceof Player player && SurfaceAdaptations.walksOnPowderSnow(player)) {
            ci.cancel();
        }
    }

    @Inject(method = "canEntityWalkOnPowderSnow", at = @At("HEAD"), cancellable = true)
    private static void adaptionwheel$walkOnTop(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (entity instanceof Player player && SurfaceAdaptations.walksOnPowderSnow(player)) {
            cir.setReturnValue(true);
        }
    }
}

package ru.adaptionwheel.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.adaptionwheel.SurfaceAdaptations;

@Mixin(SweetBerryBushBlock.class)
public abstract class BerryBushMixin {

    @Inject(method = "entityInside", at = @At("HEAD"), cancellable = true)
    private void adaptionwheel$noSnag(net.minecraft.world.level.block.state.BlockState state,
                                      net.minecraft.world.level.Level level,
                                      net.minecraft.core.BlockPos pos,
                                      Entity entity, CallbackInfo ci) {
        if (entity instanceof LivingEntity living && SurfaceAdaptations.movesThroughBerryBush(living)) {
            ci.cancel();
        }
    }
}

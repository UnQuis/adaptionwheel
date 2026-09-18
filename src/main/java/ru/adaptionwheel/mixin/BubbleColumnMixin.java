package ru.adaptionwheel.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.BubbleColumnBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.adaptionwheel.SurfaceAdaptations;

/**
 * Move_BubbleColumn: vanilla applies drag/launch via {@code onAboveBubbleCol} /
 * {@code onInsideBubbleCol} from entityInside; adapted players keep full
 * control of their vertical motion inside columns.
 */
@Mixin(BubbleColumnBlock.class)
public abstract class BubbleColumnMixin {

    @Inject(method = "entityInside", at = @At("HEAD"), cancellable = true)
    private void adaptionwheel$noDrag(net.minecraft.world.level.block.state.BlockState state,
                                      net.minecraft.world.level.Level level,
                                      net.minecraft.core.BlockPos pos,
                                      Entity entity,
                                      net.minecraft.world.entity.InsideBlockEffectApplier effectApplier,
                                      boolean isPrecise, CallbackInfo ci) {
        if (entity instanceof Player player && SurfaceAdaptations.controlsBubbleColumns(player)) {
            ci.cancel();
        }
    }
}

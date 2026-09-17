package ru.adaptionwheel.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.WebBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.adaptionwheel.SurfaceAdaptations;

/**
 * Adapted players still stick to cobwebs, but half as much as vanilla:
 * motion multiplier (0.5, 0.1, 0.5) instead of (0.25, 0.05, 0.25).
 */
@Mixin(WebBlock.class)
public abstract class WebBlockMixin {

    @Inject(method = "entityInside", at = @At("HEAD"), cancellable = true)
    private void adaptionwheel$gentlerWeb(BlockState state, Level level, BlockPos pos, Entity entity, CallbackInfo ci) {
        if (entity instanceof Player player && SurfaceAdaptations.movesThroughWebs(player)) {
            entity.makeStuckInBlock(state, new Vec3(0.5, 0.1, 0.5));
            ci.cancel();
        }
    }
}

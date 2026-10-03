package ru.adaptionwheel.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import ru.adaptionwheel.SurfaceAdaptations;

@Mixin(Entity.class)
public abstract class SpeedFactorMixin {

    @WrapOperation(
            method = "getBlockSpeedFactor",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/Block;getSpeedFactor()F"))
    private float adaptionwheel$speedFactor(Block block, Operation<Float> original) {
        float factor = original.call(block);
        if (factor >= 1.0F) {
            return factor;
        }
        Entity self = (Entity) (Object) this;
        if (self instanceof Player player) {
            Float neutralized = SurfaceAdaptations.neutralizedSpeedFactor(player, block);
            if (neutralized != null) {
                return neutralized;
            }
        }
        return factor;
    }

    @WrapOperation(
            method = "getBlockJumpFactor",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/Block;getJumpFactor()F"))
    private float adaptionwheel$jumpFactor(Block block, Operation<Float> original) {
        float factor = original.call(block);
        if (factor >= 1.0F) {
            return factor;
        }
        Entity self = (Entity) (Object) this;
        if (self instanceof Player player && SurfaceAdaptations.keepsFullJump(player, block)) {
            return 1.0F;
        }
        return factor;
    }
}

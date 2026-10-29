package ru.adaptionwheel.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForgeMod;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.adaptionwheel.LavaSwimming;

/**
 * Lava swimming, by making the water branch of {@code travel} answer for lava.
 *
 * <p>Vanilla's lava branch is not water with a different colour: it multiplies motion by 0.5 and
 * then subtracts a quarter of gravity every tick, so a player holding jump reaches roughly zero
 * net rise. That is why no amount of tuning the old post-tick velocity hack could make lava feel
 * swimmable — the hack was fighting a branch that actively cancels vertical motion.
 *
 * <p>Forcing the two {@code isInWater()} checks in the patched branch is the whole trick: the
 * water branch already carries the drag, the acceleration, the slow sink and the collision
 * handling, and NeoForge's own {@code jumpInFluid} call supplies the same 0.04 jump impulse in
 * lava that it does in water. The two additions are deliberately separate:
 *
 * <ul>
 *   <li>the {@code isInWater} rewrite is side-agnostic. In practice a player's {@code travel} only
 *       runs on the client ({@code isControlledByLocalInstance} is true for {@code LocalPlayer}
 *       alone), so the client owns the physics and the server adopts the position it reports --
 *       the mixin does not try to run the branch twice;</li>
 *   <li>the sneak sink is gated on the client for the same reason vanilla applies
 *       {@code goDownInWater} there, and only when the player is not riding, not a spectator and
 *       not floating under {@code isNoGravity}.</li>
 * </ul>
 *
 * <p>The horizontal scale is applied at the tail rather than around one inner call, because the
 * water branch has several exits (the collision bump in particular) and a single scale point is
 * easier to keep correct than three. Vertical is intentionally untouched: lava had no usable rise,
 * so "slower than water" must not slow the one axis that was already missing.
 */
@Mixin(LivingEntity.class)
public abstract class LavaSwimMixin {

    @ModifyExpressionValue(
            method = "travel",
            at = @At(value = "INVOKE", target = "isInWater()Z"))
    private boolean adaptionwheel$lavaCountsAsWater(boolean original) {
        return original || LavaSwimming.active((LivingEntity) (Object) this);
    }

    @Inject(method = "travel", at = @At("HEAD"))
    private void adaptionwheel$sinkInLava(Vec3 input, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!LavaSwimming.active(self) || !self.level().isClientSide
                || !self.isShiftKeyDown() || self.isPassenger()
                || self.isSpectator() || self.isNoGravity()) {
            return;
        }
        // Exactly vanilla's goDownInWater: -0.04 scaled by SWIM_SPEED. The water branch below
        // applies the same 0.8 drag to it that it applies to the water version.
        Vec3 motion = self.getDeltaMovement();
        double swimSpeed = self.getAttributeValue(NeoForgeMod.SWIM_SPEED);
        self.setDeltaMovement(motion.x, motion.y - 0.04 * swimSpeed, motion.z);
    }

    @Inject(method = "travel", at = @At("TAIL"))
    private void adaptionwheel$slowLavaSwim(Vec3 input, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!LavaSwimming.active(self)) {
            return;
        }
        Vec3 motion = self.getDeltaMovement();
        double scale = LavaSwimming.horizontalScale();
        self.setDeltaMovement(motion.x * scale, motion.y, motion.z * scale);
    }
}

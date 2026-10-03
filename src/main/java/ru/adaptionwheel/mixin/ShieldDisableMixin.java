package ru.adaptionwheel.mixin;

import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.adaptionwheel.SurfaceAdaptations;

@Mixin(Player.class)
public abstract class ShieldDisableMixin {

    @Inject(method = "blockUsingItem", at = @At("HEAD"), cancellable = true)
    private void adaptionwheel$keepShield(net.minecraft.server.level.ServerLevel level,
                                          net.minecraft.world.entity.LivingEntity attacker,
                                          net.minecraft.world.damagesource.DamageSource source,
                                          float damage, boolean fullyBlocked, CallbackInfo ci) {
        Player self = (Player) (Object) this;
        if (SurfaceAdaptations.keepsShieldUp(self)) {

            if (!fullyBlocked) {
                attacker.knockback(0.5, attacker.getX() - self.getX(), attacker.getZ() - self.getZ(), source, damage);
            }
            ci.cancel();
        }
    }
}

package ru.adaptionwheel.mixin;

import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.adaptionwheel.SurfaceAdaptations;

/**
 * Combat_ShieldLock: since 26.x the shield disable lives in
 * {@code Player.blockUsingItem(ServerLevel, LivingEntity, DamageSource, float)}
 * (the {@code BlocksAttacks#disable} call after the super knockback). Adapted
 * players skip the override entirely and only run the {@code LivingEntity}
 * part (attacker knockback), so their shield is never put on cooldown.
 */
@Mixin(Player.class)
public abstract class ShieldDisableMixin {

    @Inject(method = "blockUsingItem", at = @At("HEAD"), cancellable = true)
    private void adaptionwheel$keepShield(net.minecraft.server.level.ServerLevel level,
                                          net.minecraft.world.entity.LivingEntity attacker,
                                          net.minecraft.world.damagesource.DamageSource source,
                                          float damage, CallbackInfo ci) {
        Player self = (Player) (Object) this;
        if (SurfaceAdaptations.keepsShieldUp(self)) {
            // Preserve the vanilla LivingEntity behaviour (attacker gets pushed back).
            attacker.knockback(0.5, attacker.getX() - self.getX(), attacker.getZ() - self.getZ(), source, damage);
            ci.cancel();
        }
    }
}

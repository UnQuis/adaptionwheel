package ru.adaptionwheel.mixin;

import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.adaptionwheel.SurfaceAdaptations;

/**
 * Combat_ShieldLock: {@code Player.disableShield()} is the single choke point
 * vanilla uses for every shield disable (axe hits included) — adapted players
 * simply never enter it, on both sides.
 */
@Mixin(Player.class)
public abstract class ShieldDisableMixin {

    @Inject(method = "disableShield", at = @At("HEAD"), cancellable = true)
    private void adaptionwheel$keepShield(CallbackInfo ci) {
        if (SurfaceAdaptations.keepsShieldUp((Player) (Object) this)) {
            ci.cancel();
        }
    }
}

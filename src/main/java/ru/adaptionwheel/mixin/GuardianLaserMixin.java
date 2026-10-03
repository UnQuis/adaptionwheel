package ru.adaptionwheel.mixin;

import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import ru.adaptionwheel.server.GuardianDirectDamage;

@Mixin(targets = "com.brandon3055.draconicevolution.entity.guardian.control.LaserBeamPhase", remap = false)
public class GuardianLaserMixin {

    @Redirect(
            method = {"serverTick()V"},
            at = @At(value = "INVOKE", target = "setHealth(F)V", remap = false),
            remap = false,
            require = 1
    )
    private void adaptionwheel$guardianDirectHealth(Player target, float newHealth) {
        GuardianDirectDamage.apply(target, newHealth);
    }
}

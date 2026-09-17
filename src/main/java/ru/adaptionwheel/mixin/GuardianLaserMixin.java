package ru.adaptionwheel.mixin;

import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import ru.adaptionwheel.server.GuardianDirectDamage;

/**
 * Draconic Evolution's fully charged Chaos Guardian twin laser applies its damage by
 * calling {@code setHealth} directly on the target (after a fake CombatTracker record),
 * bypassing every NeoForge damage event — no adaptation could ever react to it.
 *
 * This redirect routes that call through {@link GuardianDirectDamage}, where wheel
 * adaptations (existence immunity, damage-type/contact reductions, Adversity survival)
 * are applied before the health is actually written.
 *
 * The mixin is applied only when Draconic Evolution is loaded
 * (see {@link AdaptionMixinPlugin}); the target string intentionally omits the owner so
 * the single {@code setHealth(F)V} callsite matches regardless of the declared receiver.
 * The handler parameter must be the exact receiver type used by Draconic Evolution's
 * bytecode ({@code Player}) or Mixin rejects the redirect at apply time.
 */
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
